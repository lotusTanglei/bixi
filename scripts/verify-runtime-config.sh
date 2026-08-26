#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

required_files="
compose.yaml
.env.example
deploy/docker/backend.Dockerfile
deploy/docker/frontend.Dockerfile
deploy/nacos/publish.sh
deploy/nacos/application-dev.yml
deploy/nacos/bixi-gateway-dev.yml
deploy/nacos/bixi-auth-dev.yml
deploy/nacos/bixi-upms-biz-dev.yml
deploy/nacos/bixi-common-oss-dev.yml
deploy/nacos/bixi-common-mq-dev.yml
deploy/mysql/05_runtime_secrets.sh
scripts/acceptance.mjs
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
    bixi-upms-biz-dev.yml bixi-common-oss-dev.yml bixi-common-mq-dev.yml; do
    grep -F "${data_id}" "${ROOT}/deploy/nacos/publish.sh" >/dev/null 2>&1 \
        || grep -F '/config/*.yml' "${ROOT}/deploy/nacos/publish.sh" >/dev/null
done

grep -F 'GENERATE_ON_FIRST_RUN' "${ROOT}/.env.example" >/dev/null
grep -F '127.0.0.1:${BIXI_HTTP_PORT:-8080}:8080' "${ROOT}/compose.yaml" >/dev/null
grep -F 'service_completed_successfully' "${ROOT}/compose.yaml" >/dev/null

for contract in biz_demo_task demo_task_view demo_task_add demo_task_edit demo_task_del; do
    grep -F "${contract}" "${ROOT}/bixi-project-documents/sql/01_init_all_tables.sql" \
        "${ROOT}/bixi-project-documents/sql/04_init_data.sql" >/dev/null 2>&1 \
        || { echo "Missing sample business contract: ${contract}" >&2; exit 1; }
done

for secret_key in TENANT_DEFAULT_PASSWORD DRUID_PASSWORD MINIO_ACCESS_KEY MINIO_SECRET_KEY; do
    grep -F "${secret_key}=GENERATE_ON_FIRST_RUN" "${ROOT}/.env.example" >/dev/null \
        || { echo "Missing generated secret template: ${secret_key}" >&2; exit 1; }
done

grep -F "SET value = '\${TENANT_DEFAULT_PASSWORD}'" "${ROOT}/deploy/mysql/05_runtime_secrets.sh" >/dev/null

if grep -F 'gen_datasource_config' "${ROOT}/deploy/mysql/05_runtime_secrets.sh" \
    "${ROOT}/bixi-project-documents/sql/04_init_data.sql" >/dev/null; then
    echo 'Default dynamic datasource seed must not bypass Jasypt encryption' >&2
    exit 1
fi

if grep -E 'RABBITMQ_PASSWORD:guest|MYSQL_PASSWORD:(bixi|123456)|MINIO_SECRET_KEY:minioadmin|login-password: 123456|^[[:space:]]+password: bixi$' \
    "${ROOT}/bixi-single/src/main/resources/application-dev.yml" >/dev/null; then
    echo 'Single runtime config contains a known default password' >&2
    exit 1
fi

if grep -F "'TENANT_DEFAULT_PASSWORD', '123456'" "${ROOT}/bixi-project-documents/sql/04_init_data.sql" >/dev/null \
    || grep -F "'root', '123456', 'mysql'" "${ROOT}/bixi-project-documents/sql/04_init_data.sql" >/dev/null; then
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

if grep -En "https?://(127\\.0\\.0\\.1|localhost)" "${ROOT}/bixi-project-documents/sql/04_init_data.sql" >/dev/null; then
    echo 'Database seed contains a hard-coded local HTTP address' >&2
    exit 1
fi

if grep -R -En 'jasypt\.encryptor\.password"[[:space:]]*,[[:space:]]*"[^$]' \
    "${ROOT}/bixi-common" --include='*.java' >/dev/null; then
    echo 'Java sources contain a hard-coded Jasypt password' >&2
    exit 1
fi

grep -F 'BIXI_ENV_FILE' "${ROOT}/scripts/bixi.sh" "${ROOT}/scripts/acceptance.mjs" >/dev/null

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
