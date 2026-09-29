#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM

write_env() {
    workflow_enabled="$1"
    reliable_enabled="$2"
    rabbit_enabled="$3"
    env_file="${TEST_DIR}/runtime.env"
    sed "s/^WORKFLOW_ENABLED=.*/WORKFLOW_ENABLED=${workflow_enabled}/" \
        "${ROOT}/.env.example" >"${env_file}"
    printf '\nBIXI_RELIABLE_ENABLED=%s\nBIXI_RELIABLE_RABBIT_ENABLED=%s\n' \
        "${reliable_enabled}" "${rabbit_enabled}" >>"${env_file}"
}

assert_valid() {
    mode="$1"
    if ! output="$(BIXI_ENV_FILE="${TEST_DIR}/runtime.env" \
            "${ROOT}/scripts/bixi.sh" validate-config "${mode}" 2>&1)"; then
        printf 'Expected valid %s configuration, got:\n%s\n' "${mode}" "${output}" >&2
        exit 1
    fi
}

assert_invalid() {
    mode="$1"
    expected="$2"
    if output="$(BIXI_ENV_FILE="${TEST_DIR}/runtime.env" \
            "${ROOT}/scripts/bixi.sh" validate-config "${mode}" 2>&1)"; then
        printf 'Expected invalid %s configuration to fail\n' "${mode}" >&2
        exit 1
    fi
    case "${output}" in
        *"${expected}"*) ;;
        *)
            printf 'Expected error containing %s, got:\n%s\n' "${expected}" "${output}" >&2
            exit 1
            ;;
    esac
}

write_env false false false
assert_valid single
assert_valid cloud

write_env true true false
assert_valid single
assert_invalid cloud 'BIXI_RELIABLE_RABBIT_ENABLED=true'

write_env true true true
assert_valid cloud

write_env true false false
assert_invalid single 'BIXI_RELIABLE_ENABLED=true'

write_env true false true
assert_invalid cloud 'BIXI_RELIABLE_ENABLED=true'

printf 'Bixi runtime configuration contract tests passed.\n'
