#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_DIR}"' EXIT HUP INT TERM

sed 's/^OAUTH_BROWSER_CLIENT_SECRET=.*/OAUTH_BROWSER_CLIENT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef/' \
    "${ROOT}/.env.example" >"${TEST_DIR}/runtime.env"
printf '\nBIXI_HTTPS_ENABLED=true\nBIXI_TLS_DIR=\n' >>"${TEST_DIR}/runtime.env"
if output="$(BIXI_ENV_FILE="${TEST_DIR}/runtime.env" \
        "${ROOT}/scripts/bixi.sh" validate-config cloud 2>&1)"; then
    echo 'HTTPS without a certificate directory must fail validation' >&2
    exit 1
fi
case "${output}" in
    *BIXI_TLS_DIR*) ;;
    *) printf 'Expected BIXI_TLS_DIR error, got: %s\n' "${output}" >&2; exit 1 ;;
esac

mkdir "${TEST_DIR}/bin"
cat >"${TEST_DIR}/bin/docker" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >>"${BIXI_DOCKER_LOG}"
case "$*" in
    *compose.https.yaml*' down'*) [ -n "${BIXI_TLS_DIR:-}" ] || exit 1 ;;
esac
EOF
chmod +x "${TEST_DIR}/bin/docker"

printf '\nBIXI_HTTPS_ENABLED=false\n' >>"${TEST_DIR}/runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/runtime.env" BIXI_DOCKER_LOG="${TEST_DIR}/docker.log" \
    PATH="${TEST_DIR}/bin:${PATH}" "${ROOT}/scripts/bixi.sh" stop >/dev/null
grep -F 'compose.https.yaml' "${TEST_DIR}/docker.log" \
    | grep -F 'stop https-cloud https-single' >/dev/null \
    || { echo 'stop must stop HTTPS services even after HTTPS is disabled' >&2; exit 1; }

printf '\nBIXI_HTTPS_ENABLED=true\nBIXI_TLS_DIR=/tmp\n' >>"${TEST_DIR}/runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/runtime.env" BIXI_DOCKER_LOG="${TEST_DIR}/docker.log" \
    PATH="${TEST_DIR}/bin:${PATH}" "${ROOT}/scripts/bixi.sh" status >/dev/null
grep -F 'compose.https.yaml' "${TEST_DIR}/docker.log" | grep -F ' ps' >/dev/null \
    || { echo 'enabled HTTPS must load the Compose overlay' >&2; exit 1; }

printf '\nBIXI_TLS_DIR=\n' >>"${TEST_DIR}/runtime.env"
BIXI_ENV_FILE="${TEST_DIR}/runtime.env" BIXI_DOCKER_LOG="${TEST_DIR}/docker.log" \
    PATH="${TEST_DIR}/bin:${PATH}" "${ROOT}/scripts/bixi.sh" stop >/dev/null \
    || { echo 'stop must work after HTTPS certificate configuration is removed' >&2; exit 1; }

grep -F 'up --detach --no-build https-cloud' "${ROOT}/scripts/bixi.sh" >/dev/null \
    || { echo 'cloud start must start the HTTPS edge' >&2; exit 1; }
grep -F 'up --detach --no-build https-single' "${ROOT}/scripts/bixi.sh" >/dev/null \
    || { echo 'single start must start the HTTPS edge' >&2; exit 1; }

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    BIXI_TLS_DIR=/tmp docker compose --env-file "${TEST_DIR}/runtime.env" \
        -f "${ROOT}/compose.yaml" -f "${ROOT}/compose.https.yaml" \
        --profile cloud config --quiet
    BIXI_TLS_DIR=/tmp docker compose --env-file "${TEST_DIR}/runtime.env" \
        -f "${ROOT}/compose.yaml" -f "${ROOT}/compose.https.yaml" \
        --profile single config --quiet
fi

echo 'Bixi HTTPS lifecycle checks passed.'
