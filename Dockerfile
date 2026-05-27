# Build the whole reactor, then run one service's shaded jar.
# Pick the service with --build-arg MODULE=edd-demo-customer-svc | edd-demo-greeter-svc
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
# MODULE_DIR is the module path under the repo root, e.g. modules/demo/edd-demo-customer-svc
ARG MODULE_DIR
COPY --from=build /build/${MODULE_DIR}/target/*-app.jar /app/app.jar
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
