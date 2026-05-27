#!/bin/bash
#
# End-to-end test for edd-java. Builds the ping/pong service uberjars and the generic router uberjar,
# deploys self-contained AWS infra (API Gateway + S3 + DynamoDB + SQS + Lambda) as CloudFormation
# stacks under an isolated env name, then runs the Clojure-parity integration suite (e2e/it) against
# the live API:
#   - ping --effect--> router --> pong --effect--> router --> ping ... (hop-guarded)
#   - S3 import-bucket filter, cross-service remote query, identity, validation, idempotency,
#     optimistic concurrency, multi-service fan-out.
#
# Idempotent: every resource is a CloudFormation stack, deployed (created or updated) in place.
# Set BUILD=0 to skip the Maven build on a re-run. Override the env name with E2E_ENV.

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; BLUE='\033[0;34m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${BLUE}[E2E]${NC} $1"; }
ok()   { echo -e "${GREEN}[E2E OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[E2E WARN]${NC} $1"; }
err()  { echo -e "${RED}[E2E ERR]${NC} $1"; }

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${E2E_DIR}/.." && pwd)"
CF="${E2E_DIR}/cloudformation"

ENV="${E2E_ENV:-cqrs-e2e}"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-1}}"
ACCOUNT="${TARGET_ACCOUNT_ID:-}"
BUILD_TAG="${BUILD_ID:-local}"
REALM="test"
PING_HANDLER="com.alphaprosoft.edd.e2e.ping.PingLambda::handleRequest"
PONG_HANDLER="com.alphaprosoft.edd.e2e.pong.PongLambda::handleRequest"

if [ -z "${ACCOUNT}" ]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
fi

log "env=${ENV} region=${REGION} account=${ACCOUNT} build=${BUILD_TAG}"

# Fail fast on a concurrent run against the shared ${ENV}-* stacks (a mid-deploy collision is what
# produces stale/corrupt deploys). Serialize the stage or set a distinct E2E_ENV per executor.
in_progress="$(aws cloudformation list-stacks --region "${REGION}" \
  --stack-status-filter CREATE_IN_PROGRESS UPDATE_IN_PROGRESS \
                        UPDATE_ROLLBACK_IN_PROGRESS ROLLBACK_IN_PROGRESS REVIEW_IN_PROGRESS \
  --query "StackSummaries[?starts_with(StackName, '${ENV}-e2e')].StackName" \
  --output text 2>/dev/null || true)"
if [ -n "${in_progress}" ]; then
  err "Another E2E run appears to be in progress (stacks: ${in_progress}). Aborting."
  exit 1
fi

deploy() {
  local stack="$1"; local template="$2"; shift 2
  log "Deploying stack ${stack}"
  aws cloudformation deploy \
    --region "${REGION}" \
    --stack-name "${stack}" \
    --template-file "${template}" \
    --capabilities CAPABILITY_NAMED_IAM \
    --no-fail-on-empty-changeset \
    --parameter-overrides "$@"
}

output() {
  local stack="$1"; local key="$2"
  aws cloudformation describe-stacks --region "${REGION}" --stack-name "${stack}" \
    --query "Stacks[0].Outputs[?OutputKey=='${key}'].OutputValue" --output text
}

# Prove the deployed BUILD alias runs EXACTLY the jar we just built (catches stale/cached deploys).
verify_deployed_code() {
  local fn="$1"; local jar="$2"
  local expected actual
  expected="$(openssl dgst -sha256 -binary "${jar}" | base64)"
  actual="$(aws lambda get-function-configuration --region "${REGION}" \
            --function-name "${fn}:BUILD" --query CodeSha256 --output text)"
  if [ "${expected}" != "${actual}" ]; then
    err "Deployed code for ${fn} does NOT match the built jar (expected ${expected}, got ${actual})."
    exit 1
  fi
  ok "Verified ${fn}:BUILD runs the freshly built jar"
}

uuid() { cat /proc/sys/kernel/random/uuid; }

queue_url() { echo "https://sqs.${REGION}.amazonaws.com/${ACCOUNT}/$1"; }

# 1. Build the uberjars (router from the main reactor, ping/pong from the e2e reactor).
if [ "${BUILD:-1}" = "1" ]; then
  log "Building edd-java (install) + e2e service jars"
  ( cd "${ROOT_DIR}" && mvn -q -DskipTests install )
  ( cd "${E2E_DIR}" && mvn -q -DskipTests package )
fi

ROUTER_JAR="$(ls "${ROOT_DIR}"/modules/runtime/edd-java-router/target/*-app.jar | head -1)"
PING_JAR="$(ls "${E2E_DIR}"/ping-svc/target/*-app.jar | head -1)"
PONG_JAR="$(ls "${E2E_DIR}"/pong-svc/target/*-app.jar | head -1)"
log "router=${ROUTER_JAR##*/} ping=${PING_JAR##*/} pong=${PONG_JAR##*/}"

# 2. Deterministic queue URLs break the router<->service circular dependency.
ROUTER_Q="$(queue_url "${ENV}-router-response")"
PING_Q="$(queue_url "${ENV}-ping-svc-commands")"
PONG_Q="$(queue_url "${ENV}-pong-svc-commands")"
ROUTES="{\"ping\":\"${PING_Q}\",\"ping-set-value\":\"${PING_Q}\",\"ping-broadcast\":\"${PING_Q}\",\"ping-claim-name\":\"${PING_Q}\",\"ping-set-score\":\"${PING_Q}\",\"ping-object-uploaded\":\"${PING_Q}\",\"pong\":\"${PONG_Q}\",\"pong-set-value\":\"${PONG_Q}\",\"pong-combine\":\"${PONG_Q}\"}"

# 3. Shared infra (buckets + import queue).
deploy "${ENV}-e2e-infra" "${CF}/infra.yaml" \
  "EnvironmentNameLower=${ENV}" "ImportResourceName=ping-svc"

DEPLOY_BUCKET="$(output "${ENV}-e2e-infra" DeploymentBucketName)"
IMPORT_QUEUE_ARN="$(output "${ENV}-e2e-infra" ImportQueueArn)"
IMPORT_BUCKET="$(output "${ENV}-e2e-infra" ImportBucketName)"
log "deployment-bucket=${DEPLOY_BUCKET} import-bucket=${IMPORT_BUCKET}"

# 4. Upload artifacts under a unique key per run, so CloudFormation always republishes the BUILD alias.
RUN_TAG="${BUILD_TAG}-$(date +%Y%m%d%H%M%S)"
ROUTER_KEY="router-${RUN_TAG}.jar"
PING_KEY="ping-svc-${RUN_TAG}.jar"
PONG_KEY="pong-svc-${RUN_TAG}.jar"
log "Uploading jars to s3://${DEPLOY_BUCKET}"
aws s3 cp "${ROUTER_JAR}" "s3://${DEPLOY_BUCKET}/${ROUTER_KEY}" --region "${REGION}"
aws s3 cp "${PING_JAR}"   "s3://${DEPLOY_BUCKET}/${PING_KEY}"   --region "${REGION}"
aws s3 cp "${PONG_JAR}"   "s3://${DEPLOY_BUCKET}/${PONG_KEY}"   --region "${REGION}"

# 5. API Gateway first, so its URL/id can be injected into the service lambdas (cross-service deps +
#    the apigateway invoke grant). The integration targets the service aliases by deterministic name.
deploy "${ENV}-e2e-api" "${CF}/api.yaml" \
  "EnvironmentNameLower=${ENV}" "Region=${REGION}"
API_URL="$(output "${ENV}-e2e-api" ApiUrl)"
API_ID="$(output "${ENV}-e2e-api" ApiId)"
ok "API at ${API_URL} (id ${API_ID})"

# 6. Router (creates {env}-router-response + mapping). Routes table addresses the service queues.
deploy "${ENV}-e2e-router" "${CF}/lambda-router.yaml" \
  "EnvironmentNameLower=${ENV}" "Region=${REGION}" \
  "DeploymentBucketName=${DEPLOY_BUCKET}" "S3Key=${ROUTER_KEY}" "Routes=${ROUTES}"
verify_deployed_code "${ENV}-router" "${ROUTER_JAR}"

# 7. ping (consumes the import queue) + pong. Both get the router queue + the ApiUrl + the API id.
deploy "${ENV}-e2e-ping" "${CF}/lambda-svc.yaml" \
  "EnvironmentNameLower=${ENV}" "ResourceName=ping-svc" "Handler=${PING_HANDLER}" "Region=${REGION}" \
  "DeploymentBucketName=${DEPLOY_BUCKET}" "S3Key=${PING_KEY}" \
  "RouterQueueUrl=${ROUTER_Q}" "ApiUrl=${API_URL}" "CQRSApiId=${API_ID}" \
  "EnableImport=true" "ImportQueueArn=${IMPORT_QUEUE_ARN}"
verify_deployed_code "${ENV}-ping-svc" "${PING_JAR}"

deploy "${ENV}-e2e-pong" "${CF}/lambda-svc.yaml" \
  "EnvironmentNameLower=${ENV}" "ResourceName=pong-svc" "Handler=${PONG_HANDLER}" "Region=${REGION}" \
  "DeploymentBucketName=${DEPLOY_BUCKET}" "S3Key=${PONG_KEY}" \
  "RouterQueueUrl=${ROUTER_Q}" "ApiUrl=${API_URL}" "CQRSApiId=${API_ID}" \
  "EnableImport=false"
verify_deployed_code "${ENV}-pong-svc" "${PONG_JAR}"

# 8. AWS::ApiGateway::Deployment does not redeploy when the RestApi body changes; force a fresh one.
aws apigateway create-deployment --region "${REGION}" \
  --rest-api-id "${API_ID}" --stage-name e2e >/dev/null
ok "API stage redeployed"

# 9. Upload a file to the import bucket; its aggregate id is the UUID filename.
OBJ_ID="$(uuid)"
KEY="${REALM}/$(date +%F)/$(uuid)/${OBJ_ID}.json"
log "Uploading import object s3://${IMPORT_BUCKET}/${KEY}"
echo '{"e2e":"object-upload"}' >/tmp/e2e-object.json
aws s3 cp /tmp/e2e-object.json "s3://${IMPORT_BUCKET}/${KEY}" --region "${REGION}"

# 10. Run the integration suite against the deployed stack.
log "Running integration tests (e2e/it) against ${API_URL}"
( cd "${E2E_DIR}/it" \
  && ApiUrl="${API_URL}" \
     E2E_IMPORT_OBJECT_ID="${OBJ_ID}" \
     mvn -q test )

ok "All E2E tests passed."
