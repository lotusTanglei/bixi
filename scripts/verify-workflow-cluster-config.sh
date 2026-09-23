#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"

if [ ! -s "${ENV_FILE}" ]; then
    echo "Missing environment file: ${ENV_FILE}" >&2
    exit 1
fi

compose() {
    docker compose --env-file "${ENV_FILE}" \
        -f "${ROOT}/compose.yaml" -f "${ROOT}/compose.workflow-cluster.yaml" "$@"
}

compose --profile cloud --profile workflow-cluster config --quiet
services="$(compose --profile cloud --profile workflow-cluster config --services)"
printf '%s\n' "${services}" | grep -Fx workflow-a >/dev/null
printf '%s\n' "${services}" | grep -Fx workflow-b >/dev/null
if printf '%s\n' "${services}" | grep -Fx workflow >/dev/null; then
    echo 'Single Workflow service must not be active in the cluster profile' >&2
    exit 1
fi

config="$(compose --profile cloud --profile workflow-cluster config)"
printf '%s\n' "${config}" | grep -F 'WORKFLOW_LOCK_OWNER: workflow-a' >/dev/null
printf '%s\n' "${config}" | grep -F 'WORKFLOW_LOCK_OWNER: workflow-b' >/dev/null

for service in workflow-a workflow-b; do
    block="$(printf '%s\n' "${config}" | awk -v service="${service}:" '
        $0 == "  " service { inside=1; next }
        inside && $0 ~ /^  [^ ]/ { exit }
        inside { print }
    ')"
    printf '%s\n' "${block}" | grep -F 'MODULE: bixi-module/bixi-workflow-biz' >/dev/null
    if printf '%s\n' "${block}" | grep -E '^    ports:' >/dev/null; then
        echo "${service} must not publish a duplicate host port" >&2
        exit 1
    fi
done

echo 'Workflow cluster Compose configuration passed.'
