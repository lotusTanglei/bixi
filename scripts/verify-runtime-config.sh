#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

required_files="
compose.yaml
compose.workflow-cluster.yaml
compose.workflow-fault-test.yaml
.env.example
deploy/docker/backend.Dockerfile
deploy/docker/frontend.Dockerfile
deploy/rabbitmq/configure-access.sh
deploy/nacos/publish.sh
deploy/nacos/application-dev.yml
deploy/nacos/bixi-gateway-dev.yml
deploy/nacos/bixi-auth-dev.yml
deploy/nacos/bixi-generator-dev.yml
deploy/nacos/bixi-quartz-dev.yml
deploy/nacos/bixi-ai-biz-dev.yml
deploy/nacos/bixi-upms-biz-dev.yml
deploy/nacos/bixi-monitor-dev.yml
deploy/nacos/bixi-common-oss-dev.yml
deploy/nacos/bixi-common-mq-dev.yml
deploy/mysql/05_runtime_secrets.sh
scripts/acceptance.mjs
scripts/bixi-runtime-config.test.sh
scripts/generator-cloud-runtime-config.test.sh
scripts/ai-cloud-runtime-config.test.sh
scripts/verify-workflow-cluster-config.sh
scripts/test-workflow-process-restart.sh
scripts/test-workflow-process-restart.test.mjs
scripts/test-reliable-rabbit.sh
scripts/test-reliable-rabbit.test.mjs
scripts/test-workflow-cluster-failover.sh
scripts/workflow-cluster-failover.mjs
scripts/workflow-cluster-failover.test.mjs
scripts/workflow-performance-metrics.mjs
scripts/workflow-performance-metrics.test.mjs
scripts/test-quartz-jdbc-failover.sh
scripts/test-quartz-jdbc-failover.test.mjs
scripts/quartz-ha-schema.sql
scripts/quartz-cloud-runtime-config.test.sh
scripts/migrate-workflow-schema.sh
scripts/test-phase2-migration-runner.test.mjs
scripts/test-phase2-delivery-config.test.mjs
AGENTS.md
.docs/1_ARCHITECTURE.md
.docs/5_AI_DEVELOPMENT.md
.docs/6_RELEASE_BASELINE.md
.gitlab-ci.yml
"

for file in ${required_files}; do
    if [ ! -s "${ROOT}/${file}" ]; then
        echo "Missing runtime asset: ${file}" >&2
        exit 1
    fi
done

for data_id in application-dev.yml bixi-gateway-dev.yml bixi-auth-dev.yml \
    bixi-generator-dev.yml bixi-quartz-dev.yml bixi-ai-biz-dev.yml bixi-upms-biz-dev.yml bixi-monitor-dev.yml \
    bixi-common-oss-dev.yml bixi-common-mq-dev.yml; do
    grep -F "${data_id}" "${ROOT}/deploy/nacos/publish.sh" >/dev/null 2>&1 \
        || grep -F '/config/*.yml' "${ROOT}/deploy/nacos/publish.sh" >/dev/null
done

grep -F 'BIXI_SBA_CLIENT_ENABLED' "${ROOT}/deploy/nacos/application-dev.yml" >/dev/null \
    || { echo 'Nacos global config must expose the SBA client feature flag' >&2; exit 1; }
grep -F 'user.name:' "${ROOT}/deploy/nacos/application-dev.yml" >/dev/null \
    || { echo 'Nacos global config must pass protected Actuator credentials to SBA' >&2; exit 1; }
grep -F 'include: health,info,metrics' "${ROOT}/deploy/nacos/bixi-monitor-dev.yml" >/dev/null \
    || { echo 'Monitor must expose only health, info and metrics endpoints' >&2; exit 1; }
grep -F 'BIXI_SBA_CLIENT_ENABLED: "false"' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Monitor and single services must disable recursive SBA registration' >&2; exit 1; }
grep -F 'MODULE: bixi-module/bixi-monitor' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Compose must define the monitor service' >&2; exit 1; }
grep -F 'AI_ENABLED=false' "${ROOT}/.env.example" >/dev/null \
    || { echo 'AI must be opt-in by default' >&2; exit 1; }
grep -F 'DASHSCOPE_API_KEY=' "${ROOT}/.env.example" >/dev/null \
    || { echo 'AI provider key must be supplied through the environment' >&2; exit 1; }

grep -F 'GENERATE_ON_FIRST_RUN' "${ROOT}/.env.example" >/dev/null
grep -F 'BIXI_SBA_CLIENT_ENABLED=true' "${ROOT}/.env.example" >/dev/null \
    || { echo 'Cloud runtime must enable SBA registration by default' >&2; exit 1; }
grep -F 'BIXI_SBA_CLIENT_PASSWORD=GENERATE_ON_FIRST_RUN' "${ROOT}/.env.example" >/dev/null \
    || { echo 'SBA credentials must be generated and kept out of source control' >&2; exit 1; }
grep -F 'monitor gateway upms auth generator quartz frontend-cloud' "${ROOT}/scripts/bixi.sh" >/dev/null \
    || { echo 'Cloud start must build and start the monitor service' >&2; exit 1; }
grep -F '127.0.0.1:${BIXI_HTTP_PORT:-8080}:8080' "${ROOT}/compose.yaml" >/dev/null
grep -F 'service_completed_successfully' "${ROOT}/compose.yaml" >/dev/null
grep -F 'WORKFLOW_CLUSTER_ENABLED=false' "${ROOT}/.env.example" >/dev/null
grep -F 'workflow-a:' "${ROOT}/compose.workflow-cluster.yaml" >/dev/null
grep -F 'workflow-b:' "${ROOT}/compose.workflow-cluster.yaml" >/dev/null
grep -F 'restart: "no"' "${ROOT}/compose.workflow-fault-test.yaml" >/dev/null
grep -F 'logs --no-color --tail=160 workflow-a workflow-b upms auth gateway' "${ROOT}/scripts/test-workflow-cluster-failover.sh" >/dev/null
grep -F 'const apiBase = `http://127.0.0.1:${gatewayPort}`;' "${ROOT}/scripts/workflow-cluster-failover.mjs" >/dev/null

for performance_default in \
    'WORKFLOW_PERF_SAMPLE_SIZE:-12' \
    'WORKFLOW_PERF_CONCURRENCY:-4' \
    'WORKFLOW_PERF_WARMUP:-2' \
    'WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS:-250'; do
    grep -F "${performance_default}" "${ROOT}/scripts/test-workflow-cluster-failover.sh" >/dev/null \
        || { echo "Missing Workflow performance default: ${performance_default}" >&2; exit 1; }
done

for workflow_config in \
    bixi-module/bixi-workflow-biz/src/main/resources/application.yml \
    bixi-single/src/main/resources/application.yml; do
    grep -F 'check-process-definitions: false' "${ROOT}/${workflow_config}" >/dev/null \
        || { echo "Workflow runtime must disable classpath auto-deployment: ${workflow_config}" >&2; exit 1; }
    grep -F 'public-start-models: ${WORKFLOW_PUBLIC_START_MODELS:}' "${ROOT}/${workflow_config}" >/dev/null \
        || { echo "Workflow public start allowlist must be empty by default: ${workflow_config}" >&2; exit 1; }
done

grep -F 'WORKFLOW_PUBLIC_START_MODELS: ${WORKFLOW_PUBLIC_START_MODELS:-}' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Compose must pass the empty-by-default Workflow public start allowlist' >&2; exit 1; }

for rabbit_exclusion in \
    org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration \
    org.springframework.boot.actuate.autoconfigure.amqp.RabbitHealthContributorAutoConfiguration; do
    grep -F -- "- ${rabbit_exclusion}" "${ROOT}/bixi-single/src/main/resources/application.yml" >/dev/null \
        || { echo "Single runtime must exclude Rabbit auto-configuration: ${rabbit_exclusion}" >&2; exit 1; }
done

single_service_block="$(awk '
    /^  single:$/ { inside = 1 }
    inside && /^  [a-zA-Z0-9_-]+:$/ && $0 != "  single:" { exit }
    inside { print }
' "${ROOT}/compose.yaml")"
if printf '%s\n' "${single_service_block}" | grep -E '^[[:space:]]+rabbitmq(-config)?:' >/dev/null; then
    echo 'Single Compose service must not depend on RabbitMQ' >&2
    exit 1
fi

start_single_block="$(awk '
    /^start_single\(\) \{$/ { inside = 1 }
    inside { print }
    inside && /^}$/ { exit }
' "${ROOT}/scripts/bixi.sh")"
printf '%s\n' "${start_single_block}" \
    | grep -F 'compose --profile single up --detach --no-build mysql redis single frontend-single' >/dev/null \
    || { echo 'start_single must start only MySQL, Redis, single, and frontend-single' >&2; exit 1; }
if printf '%s\n' "${start_single_block}" | grep -F 'rabbitmq' >/dev/null; then
    echo 'start_single must not start RabbitMQ' >&2
    exit 1
fi

awk '
    /^prepare_images\(\) \{$/ { inside = 1 }
    inside && /if \[ "\$\{mode\}" = .cloud. \]; then/ { cloud = 1 }
    inside && /rabbitmq:4\.0\.5-management-alpine/ && !cloud { exit 1 }
    inside && /^}$/ { exit }
' "${ROOT}/scripts/bixi.sh" \
    || { echo 'Single image preparation must not require a RabbitMQ image' >&2; exit 1; }

for contract in biz_demo_task demo_task_view demo_task_add demo_task_edit demo_task_del; do
    grep -F "${contract}" "${ROOT}/bixi-project-documents/sql/01_schema.sql" \
        "${ROOT}/bixi-project-documents/sql/02_data.sql" >/dev/null 2>&1 \
        || { echo "Missing sample business contract: ${contract}" >&2; exit 1; }
done

for secret_key in TENANT_DEFAULT_PASSWORD DRUID_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY; do
    grep -F "${secret_key}=GENERATE_ON_FIRST_RUN" "${ROOT}/.env.example" >/dev/null \
        || { echo "Missing generated secret template: ${secret_key}" >&2; exit 1; }
done

for rabbit_secret in RABBITMQ_APP_PASSWORD RABBITMQ_UPMS_PASSWORD RABBITMQ_WORKFLOW_PASSWORD; do
    grep -F "${rabbit_secret}=GENERATE_ON_FIRST_RUN" "${ROOT}/.env.example" >/dev/null \
        || { echo "Missing isolated Rabbit credential template: ${rabbit_secret}" >&2; exit 1; }
done

grep -F 'RABBITMQ_USERNAME: ${RABBITMQ_APP_USERNAME}' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Backend containers must receive the non-admin Rabbit application user' >&2; exit 1; }
grep -F 'BIXI_RELIABLE_RABBIT_USERNAME: ${RABBITMQ_UPMS_USERNAME}' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'UPMS must receive its dedicated reliable Rabbit user' >&2; exit 1; }
grep -F 'BIXI_RELIABLE_RABBIT_USERNAME: ${RABBITMQ_WORKFLOW_USERNAME}' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Workflow must receive its dedicated reliable Rabbit user' >&2; exit 1; }
grep -F 'BIXI_RELIABLE_RABBIT_VIRTUAL_HOST: ${RABBITMQ_WORKFLOW_VHOST}' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Reliable Rabbit endpoints must use the isolated workflow vhost' >&2; exit 1; }
grep -F 'rabbitmq-config:' "${ROOT}/compose.yaml" >/dev/null \
    || { echo 'Compose must provision Rabbit users, vhost, and permissions before applications start' >&2; exit 1; }

for permission_contract in \
    "UPMS_CONFIGURE_PATTERN='^(bixi\.workflow|bixi\.upms\.inbox)$'" \
    "UPMS_WRITE_PATTERN='^(bixi\.upms|bixi\.upms\.inbox)$'" \
    "UPMS_READ_PATTERN='^(bixi\.workflow|bixi\.upms\.inbox)$'" \
    "WORKFLOW_CONFIGURE_PATTERN='^(bixi\.upms|bixi\.workflow\.inbox)$'" \
    "WORKFLOW_WRITE_PATTERN='^(bixi\.workflow|bixi\.workflow\.inbox)$'" \
    "WORKFLOW_READ_PATTERN='^(bixi\.upms|bixi\.workflow\.inbox)$'"; do
    grep -F "${permission_contract}" "${ROOT}/deploy/rabbitmq/configure-access.sh" >/dev/null \
        || { echo "Missing directional Rabbit permission: ${permission_contract}" >&2; exit 1; }
done

grep -F "SET value = '\${TENANT_DEFAULT_PASSWORD}'" "${ROOT}/deploy/mysql/05_runtime_secrets.sh" >/dev/null

if grep -F 'gen_datasource_config' "${ROOT}/deploy/mysql/05_runtime_secrets.sh" \
    "${ROOT}/bixi-project-documents/sql/02_data.sql" >/dev/null; then
    echo 'Default dynamic datasource seed must not bypass Jasypt encryption' >&2
    exit 1
fi

if grep -E 'RABBITMQ_PASSWORD:guest|MYSQL_PASSWORD:(bixi|123456)|MINIO_SECRET_KEY:minioadmin|login-password: 123456|^[[:space:]]+password: bixi$' \
    "${ROOT}/bixi-single/src/main/resources/application-dev.yml" >/dev/null; then
    echo 'Single runtime config contains a known default password' >&2
    exit 1
fi

if grep -F "'TENANT_DEFAULT_PASSWORD', '123456'" "${ROOT}/bixi-project-documents/sql/02_data.sql" >/dev/null \
    || grep -F "'root', '123456', 'mysql'" "${ROOT}/bixi-project-documents/sql/02_data.sql" >/dev/null; then
    echo 'Database seed contains a known default password' >&2
    exit 1
fi

if find "${ROOT}/bixi-auth" "${ROOT}/bixi-gateway" "${ROOT}/bixi-module" \
    -path '*/src/main/resources/application.yml' -type f -exec grep -H '@nacos.password@' {} + | grep . >/dev/null; then
    echo 'Application config contains a build-time Nacos password' >&2
    exit 1
fi

if grep -F '/Users/' "${ROOT}/scripts/bixi.sh" "${ROOT}/compose.yaml" >/dev/null; then
    echo 'Runtime entry points must not contain personal absolute paths' >&2
    exit 1
fi

for runtime_security_config in \
    bixi-single/src/main/resources/application-dev.yml \
    deploy/nacos/bixi-auth-dev.yml \
    deploy/nacos/bixi-generator-dev.yml \
    deploy/nacos/bixi-quartz-dev.yml \
    deploy/nacos/bixi-ai-biz-dev.yml \
    deploy/nacos/bixi-upms-biz-dev.yml \
    deploy/nacos/bixi-workflow-biz-dev.yml; do
    if grep -E '^[[:space:]]*-[[:space:]]+/(v3/api-docs|swagger-ui|doc\.html|swagger-resources|druid|actuator/\*\*)' \
        "${ROOT}/${runtime_security_config}" >/dev/null; then
        echo "Runtime config exposes documentation or operational data anonymously: ${runtime_security_config}" >&2
        exit 1
    fi
    grep -F -- '- /actuator/health' "${ROOT}/${runtime_security_config}" >/dev/null \
        || { echo "Runtime config must keep only the health probe anonymous: ${runtime_security_config}" >&2; exit 1; }
done

grep -F 'enabled: ${BIXI_GATEWAY_DOCS_ENABLED:false}' "${ROOT}/deploy/nacos/bixi-gateway-dev.yml" >/dev/null \
    || { echo 'Gateway API documentation must be disabled by default until its reactive auth chain is enabled' >&2; exit 1; }
grep -F 'spring-boot-starter-oauth2-resource-server' "${ROOT}/bixi-gateway/pom.xml" >/dev/null \
    || { echo 'Gateway must include the reactive OAuth2 resource-server dependency' >&2; exit 1; }
for gateway_security_config in \
    bixi-gateway/src/main/resources/application.yml \
    deploy/nacos/bixi-gateway-dev.yml; do
    grep -F 'introspection-uri: ${BIXI_GATEWAY_SECURITY_INTROSPECTION_URI:' "${ROOT}/${gateway_security_config}" >/dev/null \
        || { echo "Gateway must configure an Auth introspection URI: ${gateway_security_config}" >&2; exit 1; }
    grep -F 'request-timeout: ${BIXI_GATEWAY_SECURITY_REQUEST_TIMEOUT:3s}' "${ROOT}/${gateway_security_config}" >/dev/null \
        || { echo "Gateway must bound introspection request time: ${gateway_security_config}" >&2; exit 1; }
done
grep -F 'BIXI_GATEWAY_SECURITY_INTROSPECTION_URI=' "${ROOT}/.env.example" >/dev/null \
    || { echo 'Missing Gateway introspection URI environment template' >&2; exit 1; }
grep -F 'BIXI_GATEWAY_SECURITY_REQUEST_TIMEOUT=3s' "${ROOT}/.env.example" >/dev/null \
    || { echo 'Missing Gateway introspection timeout environment template' >&2; exit 1; }
gateway_service_block="$(awk '
    /^  gateway:$/ { inside = 1 }
    inside && /^  [a-zA-Z0-9_-]+:$/ && $0 != "  gateway:" { exit }
    inside { print }
' "${ROOT}/compose.yaml")"
printf '%s\n' "${gateway_service_block}" \
    | grep -F 'BIXI_GATEWAY_SECURITY_INTROSPECTION_URI: ${BIXI_GATEWAY_SECURITY_INTROSPECTION_URI:-http://auth:3000/token/check_token}' >/dev/null \
    || { echo 'Compose Gateway must use the internal Auth introspection endpoint by default' >&2; exit 1; }
printf '%s\n' "${gateway_service_block}" \
    | grep -F 'BIXI_GATEWAY_SECURITY_REQUEST_TIMEOUT: ${BIXI_GATEWAY_SECURITY_REQUEST_TIMEOUT:-3s}' >/dev/null \
    || { echo 'Compose Gateway must pass the bounded introspection timeout' >&2; exit 1; }
printf '%s\n' "${gateway_service_block}" \
    | grep -F 'BIXI_SBA_CLIENT_ENABLED: "false"' >/dev/null \
    || { echo 'Gateway must not register with SBA until its reactive Actuator chain supports Basic Auth' >&2; exit 1; }
printf '%s\n' "${gateway_service_block}" \
    | grep -F 'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health' >/dev/null \
    || { echo 'Gateway must expose only the health Actuator endpoint by default' >&2; exit 1; }
grep -F 'enabled: false' "${ROOT}/deploy/nacos/bixi-auth-dev.yml" >/dev/null \
    || { echo 'Auth service API documentation must be disabled by default' >&2; exit 1; }
for config in "${ROOT}/deploy/nacos/application-dev.yml" "${ROOT}/bixi-single/src/main/resources/application-dev.yml"; do
    grep -F 'show-details: never' "${config}" >/dev/null \
        || { echo "Health endpoint details must not be exposed: ${config}" >&2; exit 1; }
done
grep -F 'include: health,info,metrics,loggers,logfile,dynamictp' "${ROOT}/deploy/nacos/application-dev.yml" >/dev/null \
    || { echo 'Shared business services must expose protected SBA operational endpoints' >&2; exit 1; }
for health_only_config in \
    deploy/nacos/bixi-gateway-dev.yml \
    deploy/nacos/bixi-auth-dev.yml \
    bixi-single/src/main/resources/application-dev.yml; do
    grep -Eq '^[[:space:]]*include:[[:space:]]*health[[:space:]]*$' "${ROOT}/${health_only_config}" \
        || { echo "Runtime must retain its health-only Actuator override: ${health_only_config}" >&2; exit 1; }
done

personal_paths="$(find "${ROOT}" \
    \( -path "${ROOT}/.git" -o -path "${ROOT}/.codegraph" -o -path '*/target' -o -path '*/node_modules' -o -path '*/dist' \) -prune \
    -o -type f \( -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' -o -name '*.sql' \) \
    -exec grep -Hn '/Users/' {} + 2>/dev/null || true)"
if [ -n "${personal_paths}" ]; then
    printf '%s\n%s\n' 'Runtime resources must not contain personal absolute paths' "${personal_paths}" >&2
    exit 1
fi

legacy_docker="$(find "${ROOT}" -name pom.xml -type f -exec \
    grep -EHn 'docker-maven-plugin|<docker\.(host|registry|namespace|username|password)>' {} + 2>/dev/null || true)"
if [ -n "${legacy_docker}" ]; then
    printf '%s\n%s\n' 'Maven configuration contains the retired Fabric8 Docker publishing path' "${legacy_docker}" >&2
    exit 1
fi

if grep -En "https?://(127\\.0\\.0\\.1|localhost)" "${ROOT}/bixi-project-documents/sql/02_data.sql" >/dev/null; then
    echo 'Database seed contains a hard-coded local HTTP address' >&2
    exit 1
fi

if grep -R -En 'jasypt\.encryptor\.password"[[:space:]]*,[[:space:]]*"[^$]' \
    "${ROOT}/bixi-common" --include='*.java' >/dev/null; then
    echo 'Java sources contain a hard-coded Jasypt password' >&2
    exit 1
fi

grep -F 'BIXI_ENV_FILE' "${ROOT}/scripts/bixi.sh" "${ROOT}/scripts/acceptance.mjs" >/dev/null
"${ROOT}/scripts/bixi-runtime-config.test.sh"
"${ROOT}/scripts/generator-cloud-runtime-config.test.sh"
"${ROOT}/scripts/ai-cloud-runtime-config.test.sh"
"${ROOT}/scripts/test-bixi-workflow-lifecycle.test.sh"
node --test "${ROOT}/scripts/test-workflow-process-restart.test.mjs"
node --test "${ROOT}/scripts/test-local-process-restart.test.mjs"
node --test "${ROOT}/scripts/test-reliable-rabbit.test.mjs"
node --test "${ROOT}/scripts/test-phase2-delivery-config.test.mjs" \
    "${ROOT}/scripts/test-phase2-migration-runner.test.mjs"
node --test "${ROOT}/scripts/test-quartz-jdbc-failover.test.mjs"
node --test "${ROOT}/scripts/test-sba-multi-instance-acceptance.test.mjs"
node --test "${ROOT}/scripts/ci-artifact-contract.test.mjs"
bash -n "${ROOT}/scripts/test-quartz-jdbc-failover.sh"
"${ROOT}/scripts/quartz-cloud-runtime-config.test.sh"
grep -F 'QuartzJdbcFailoverIntegrationTest' "${ROOT}/scripts/test-quartz-jdbc-failover.sh" >/dev/null

for section in '架构地图与职责' '编码规范' '常用命令' '新增业务' '修复缺陷' '拆分服务'; do
    grep -F "${section}" "${ROOT}/.docs/5_AI_DEVELOPMENT.md" >/dev/null \
        || { echo "Missing AI development context section: ${section}" >&2; exit 1; }
done

for section in '安全基线' '启动与验收' '升级与回滚' '已知限制'; do
    grep -F "${section}" "${ROOT}/.docs/6_RELEASE_BASELINE.md" >/dev/null \
        || { echo "Missing release baseline section: ${section}" >&2; exit 1; }
done

for command in 'make start-cloud' 'make verify-cloud' 'make start-single' 'make verify-single'; do
    grep -F "${command}" "${ROOT}/.gitlab-ci.yml" >/dev/null \
        || { echo "Missing dual-mode CI command: ${command}" >&2; exit 1; }
done

echo 'Runtime configuration checks passed.'
