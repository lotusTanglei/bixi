ARG REGISTRY_MIRROR
FROM ${REGISTRY_MIRROR}library/maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx1024m"

ARG MODULE
ARG MAVEN_PROFILE=cloud

COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -P"${MAVEN_PROFILE}" -pl "${MODULE}" -am package -Dmaven.test.skip=true \
    && JAR_FILE="$(find "${MODULE}/target" -maxdepth 1 -type f -name '*.jar' \
        ! -name 'original-*' ! -name '*-sources.jar' | head -n 1)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /app.jar

FROM ${REGISTRY_MIRROR}library/eclipse-temurin:17.0.13_11-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home bixi \
    && install -d -o bixi -g bixi /app/logs /data/files

WORKDIR /app
COPY --from=build --chown=bixi:bixi /app.jar /app/app.jar

USER bixi

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    TZ=Asia/Shanghai

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]
