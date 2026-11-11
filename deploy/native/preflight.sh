#!/bin/sh
set -eu

fail() { echo "bixi preflight: $*" >&2; exit 1; }
require() {
    value="$(printenv "$1" 2>/dev/null || true)"
    [ -n "$value" ] && [ "$value" != CHANGE_ME ] && [ "$value" != GENERATE_ON_FIRST_RUN ] \
        || fail "$1 is required"
}

service="${1:-}"
case "$service" in
    single) mode=single ;;
    gateway|auth|upms|generator|quartz|monitor|ai|workflow) mode=cloud ;;
    *) fail "unknown service: $service" ;;
esac

[ "${BIXI_DEPLOYMENT_MODE:-}" = "$mode" ] || fail "service $service requires BIXI_DEPLOYMENT_MODE=$mode"
[ -z "${NACOS_PUBLISH_USERNAME:-}" ] && [ -z "${NACOS_PUBLISH_PASSWORD:-}" ] \
    || fail 'publisher credentials must not be present in service environment'
[ "${SERVER_ADDRESS:-}" = 127.0.0.1 ] || fail 'SERVER_ADDRESS must stay on loopback behind Nginx'
case "${SPRING_PROFILES_ACTIVE:-}" in
    prod|prod,ai) ;;
    *) fail 'SPRING_PROFILES_ACTIVE must be prod or prod,ai' ;;
esac
for key in MYSQL_HOST MYSQL_PORT MYSQL_DATABASE MYSQL_USERNAME MYSQL_PASSWORD MYSQL_JDBC_URL \
    REDIS_HOST REDIS_PORT REDIS_PASSWORD BIXI_ENCODE_KEY OAUTH_BROWSER_CLIENT_SECRET \
    JASYPT_ENCRYPTOR_PASSWORD; do
    require "$key"
done
case "$MYSQL_JDBC_URL" in
    "jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"\?*) ;;
    *) fail 'MYSQL_JDBC_URL must target the configured MySQL host, port and database' ;;
esac
case "$MYSQL_HOST" in
    localhost|127.0.0.1) ;;
    *) printf '%s' "$MYSQL_JDBC_URL" | grep -Eq '(^|[?&])sslMode=VERIFY_IDENTITY(&|$)' \
        || fail 'remote MySQL requires sslMode=VERIFY_IDENTITY' ;;
esac
case "$OAUTH_BROWSER_CLIENT_SECRET" in *[!0-9a-fA-F]*|'') fail 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters' ;; esac
[ "${#OAUTH_BROWSER_CLIENT_SECRET}" -eq 48 ] || fail 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters'

if [ "$mode" = cloud ]; then
    for key in NACOS_HOST NACOS_PORT NACOS_USERNAME NACOS_PASSWORD NACOS_NAMESPACE \
        SPRING_CLOUD_NACOS_DISCOVERY_IP \
        RABBITMQ_HOST RABBITMQ_PORT RABBITMQ_USERNAME RABBITMQ_PASSWORD RABBITMQ_VIRTUAL_HOST \
        BIXI_SBA_CLIENT_URL BIXI_SBA_CLIENT_USERNAME BIXI_SBA_CLIENT_PASSWORD; do
        require "$key"
    done
    [ "$NACOS_NAMESPACE" = bixi-prod ] || fail 'NACOS_NAMESPACE must match the bixi-prod build'
    case "$NACOS_HOST" in
        localhost|127.0.0.1) ;;
        *) fail 'NACOS_HOST must be loopback; use a verified tunnel for remote Nacos' ;;
    esac
    [ "$SPRING_CLOUD_NACOS_DISCOVERY_IP" = 127.0.0.1 ] || fail 'Nacos must register loopback-only services'
fi
if [ "${WORKFLOW_ENABLED:-false}" = true ]; then
    [ "${WORKFLOW_SCHEMA_UPDATE:-false}" = false ] || fail 'Workflow schema update must remain disabled'
    [ "${BIXI_RELIABLE_ENABLED:-false}" = true ] || fail 'Workflow requires BIXI_RELIABLE_ENABLED=true'
    if [ "$mode" = cloud ]; then
        [ "${BIXI_RELIABLE_RABBIT_ENABLED:-false}" = true ] || fail 'Cloud Workflow requires the Rabbit adapter'
        case "$service" in
            upms|workflow)
                for key in BIXI_RELIABLE_RABBIT_USERNAME BIXI_RELIABLE_RABBIT_PASSWORD BIXI_RELIABLE_RABBIT_VIRTUAL_HOST; do
                    require "$key"
                done
                ;;
        esac
    fi
fi

case "$service" in
    single|generator) require GENERATOR_PROJECT_ROOT ;;
esac
case "$service" in
    gateway)
        require BIXI_GATEWAY_SECURITY_INTROSPECTION_URI
        [ "${SPRING_CONFIG_IMPORT:-}" = 'nacos:application-prod.yml,nacos:bixi-gateway-prod.yml' ] \
            || fail 'Gateway must require production Nacos config'
        if [ "${AI_ENABLED:-false}" = true ]; then
            [ "$SPRING_PROFILES_ACTIVE" = prod,ai ] || fail 'AI_ENABLED=true requires the gateway ai profile'
        fi
        ;;
    ai)
        [ "${AI_ENABLED:-false}" = true ] || fail 'AI service requires AI_ENABLED=true'
        require DASHSCOPE_API_KEY
        ;;
    workflow)
        [ "${WORKFLOW_ENABLED:-false}" = true ] || fail 'Workflow service requires WORKFLOW_ENABLED=true'
        ;;
esac

if [ "${AI_ENABLED:-false}" = true ]; then require DASHSCOPE_API_KEY; fi
if [ "${MINIO_ENABLED:-false}" = true ]; then
    for key in MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY; do require "$key"; done
fi
[ -r "/opt/bixi/current/jars/$service.jar" ] || fail "missing release JAR: $service.jar"
