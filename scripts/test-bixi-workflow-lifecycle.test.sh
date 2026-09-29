#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TEST_DIR="$(mktemp -d)"
DOCKER_LOG="${TEST_DIR}/docker.log"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM

docker() {
    printf '%s\n' "$*" >>"${DOCKER_LOG}"
}

assert_contains() {
    value="$1"
    expected="$2"
    case "${value}" in
        *"${expected}"*) ;;
        *)
            printf 'Expected [%s] to contain [%s]\n' "${value}" "${expected}" >&2
            exit 1
            ;;
    esac
}

assert_stop_command() {
    workflow_cluster_enabled="$1"
    : >"${DOCKER_LOG}"
    WORKFLOW_CLUSTER_ENABLED="${workflow_cluster_enabled}"
    stop_workflow_services
    command="$(sed -n '1p' "${DOCKER_LOG}")"
    assert_contains "${command}" "-f ${ROOT}/compose.yaml"
    assert_contains "${command}" "-f ${ROOT}/compose.workflow-cluster.yaml"
    assert_contains "${command}" '--profile workflow --profile workflow-cluster stop workflow workflow-a workflow-b'
}

# Source the command functions without invoking a command. The fake docker
# function records the exact lifecycle command selected by the script.
BIXI_ENV_FILE="${TEST_DIR}/runtime.env"
printf 'WORKFLOW_CLUSTER_ENABLED=false\n' >"${BIXI_ENV_FILE}"
# shellcheck disable=SC1090
. "${ROOT}/scripts/bixi.sh" >/dev/null

assert_stop_command false
assert_stop_command true

doctor() { :; }
load_env() { :; }
validate_ai_contract() { :; }
select_registry() { :; }
prepare_images() { :; }
wait_for_url() { :; }
show_access() { :; }
compose() {
    printf 'compose %s\n' "$*" >>"${DOCKER_LOG}"
}

ENV_FILE="${BIXI_ENV_FILE}"
WORKFLOW_ENABLED=true
AI_ENABLED=false
BIXI_HTTP_PORT=8080
MONITOR_PORT=5001
GATEWAY_PORT=8081
for workflow_cluster_enabled in false true; do
    : >"${DOCKER_LOG}"
    WORKFLOW_CLUSTER_ENABLED="${workflow_cluster_enabled}"
    start_cloud
    first_command="$(sed -n '1p' "${DOCKER_LOG}")"
    assert_contains "${first_command}" 'stop workflow workflow-a workflow-b'
    if [ "${workflow_cluster_enabled}" = 'true' ]; then
        assert_contains "$(sed -n '/up --detach --no-build --wait/p' "${DOCKER_LOG}")" 'workflow-a workflow-b'
    else
        assert_contains "$(sed -n '/up --detach --no-build --wait/p' "${DOCKER_LOG}")" 'workflow'
        if sed -n '/up --detach --no-build --wait/p' "${DOCKER_LOG}" | grep -F 'workflow-a' >/dev/null; then
            printf 'Cluster Workflow service started while cluster mode was disabled\n' >&2
            exit 1
        fi
    fi
done

: >"${DOCKER_LOG}"
SINGLE_PORT=8082
start_single
assert_contains "$(sed -n '1p' "${DOCKER_LOG}")" 'stop workflow workflow-a workflow-b'

for function in start_cloud start_single; do
    block="$(awk -v name="${function}" '
        $0 == name "() {" { inside = 1 }
        inside { print }
        inside && /^}$/ { exit }
    ' "${ROOT}/scripts/bixi.sh")"
    assert_contains "${block}" 'stop_workflow_services'
done

for command in stop reset; do
    block="$(awk -v name="${command}" '
        $0 ~ "^[[:space:]]*" name ") load_env;" { print }
    ' "${ROOT}/scripts/bixi.sh")"
    assert_contains "${block}" 'stop_workflow_services'
done

printf 'Bixi Workflow lifecycle contract tests passed.\n'
