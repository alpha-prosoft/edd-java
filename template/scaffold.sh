#!/bin/bash
#
# Scaffold a new edd-java service from the working template at template/sample-svc.
#
#   template/scaffold.sh --group com.acme --name order [--db postgres|aws] \
#       [--out DIR] [--edd-version 0.1.0-SNAPSHOT]
#
# It copies the shared domain module plus ONE backend module, then string-replaces the template's
# dummy names. No code generation: template/sample-svc is a real, compilable project you can open,
# build and edit directly — this script only renames it.
#
#   --db postgres : Postgres event + view stores, Undertow HTTP/2 server  -> target/<name>-server.jar
#   --db aws      : DynamoDB event store + S3 view store, AWS Lambda       -> target/<name>-lambda.jar
#
# The generated project depends on com.alphaprosoft:edd-java-*:<edd-version> from your local Maven
# repo, so run `mvn install` in this repo once before scaffolding.

set -euo pipefail

TEMPLATE="$(cd "$(dirname "$0")/sample-svc" && pwd)"

GROUP="" NAME="" DB="postgres" OUT="." EDD_VERSION="0.1.0-SNAPSHOT"
usage() { sed -n '2,16p' "$0"; exit "${1:-0}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --group) GROUP="$2"; shift 2;;
    --name) NAME="$2"; shift 2;;
    --db) DB="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --edd-version) EDD_VERSION="$2"; shift 2;;
    -h|--help) usage 0;;
    *) echo "Unknown arg: $1" >&2; usage 1;;
  esac
done

[ -n "$GROUP" ] || { echo "ERROR: --group is required (e.g. com.acme)" >&2; usage 1; }
[ -n "$NAME" ]  || { echo "ERROR: --name is required (e.g. order)" >&2; usage 1; }
case "$DB" in postgres|aws) ;; *) echo "ERROR: --db must be postgres|aws" >&2; exit 1;; esac

NAME="$(echo "$NAME" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')"
[ -n "$NAME" ] || { echo "ERROR: --name must contain letters/digits" >&2; exit 1; }
CLASS="$(echo "${NAME:0:1}" | tr '[:lower:]' '[:upper:]')${NAME:1}"
GROUPPATH="$(echo "$GROUP" | tr '.' '/')"
OTHER_DB="$([ "$DB" = postgres ] && echo aws || echo postgres)"
PROJ="${OUT%/}/${NAME}-svc"

echo "Scaffolding ${NAME}-svc  (group ${GROUP}, classes ${GROUP}.${NAME}, db ${DB}) -> ${PROJ}"
[ -e "$PROJ" ] && { echo "ERROR: ${PROJ} already exists" >&2; exit 1; }

# 1. copy: parent pom + shared domain module + the chosen backend module only.
mkdir -p "$PROJ"
cp "$TEMPLATE/pom.xml" "$PROJ/pom.xml"
cp -r "$TEMPLATE/sample-domain" "$PROJ/sample-domain"
cp -r "$TEMPLATE/sample-$DB" "$PROJ/sample-$DB"

# 2. drop the backend we did not copy from the parent's <modules>.
sed -i "/<module>sample-${OTHER_DB}<\/module>/d" "$PROJ/pom.xml"

# 3. move dummy package path com/example/sample -> <group path>/<name>.
find "$PROJ" -depth -type d -path '*/com/example/sample' | while read -r d; do
  base="${d%/com/example/sample}"
  mkdir -p "${base}/${GROUPPATH}/${NAME}"
  mv "$d"/* "${base}/${GROUPPATH}/${NAME}/"
  rmdir "$d" "${base}/com/example" "${base}/com" 2>/dev/null || true
done

# 4. rename dummy-named files and module directories.
find "$PROJ" -depth -name '*Sample*' | while read -r f; do
  mv "$f" "$(dirname "$f")/$(basename "$f" | sed "s/Sample/${CLASS}/")"
done
find "$PROJ" -depth -type d -name 'sample-*' | while read -r d; do
  mv "$d" "$(dirname "$d")/$(basename "$d" | sed "s/^sample-/${NAME}-/")"
done

# 5. string-replace the dummy tokens. Order matters: group before name (package overlap), and the
#    edd dependency version (0.1.0-SNAPSHOT) is distinct from the project version (1.0.0-SNAPSHOT).
find "$PROJ" -type f -print0 | xargs -0 sed -i \
  -e "s/com\.example/${GROUP}/g" \
  -e "s/Sample/${CLASS}/g" \
  -e "s/sample/${NAME}/g" \
  -e "s/0\.1\.0-SNAPSHOT/${EDD_VERSION}/g"

echo "Done. Next:"
echo "  cd ${PROJ}"
echo "  mvn package        # builds all modules and runs the in-memory tests"
if [ "$DB" = postgres ]; then
  echo "  java -jar ${NAME}-postgres/target/${NAME}-server.jar"
else
  echo "  # deploy ${NAME}-aws/target/${NAME}-lambda.jar  (handler ${GROUP}.${NAME}.${CLASS}Lambda::handleRequest)"
fi
