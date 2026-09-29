#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
ROOT="$PWD"

if [[ "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
	export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

if ! command -v mvn >/dev/null 2>&1; then
	echo 'Environment blocked: Maven is unavailable.' >&2
	exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
	echo 'Environment blocked: python3 is required for bounded Docker probes.' >&2
	exit 2
fi

run_id="$(date -u +%Y%m%d%H%M%S)-$$"
report_dir="${ROOT}/target/phase2-runtime"
report_file="${report_dir}/quartz-jdbc-failover-${run_id}.json"
log_file="${report_dir}/quartz-jdbc-failover-${run_id}.log"

docker_timeout="${QUARTZ_HA_DOCKER_COMMAND_SECONDS:-30}"
docker_preflight_timeout="${QUARTZ_HA_DOCKER_PREFLIGHT_SECONDS:-8}"
mysql_image="${QUARTZ_HA_TEST_MYSQL_IMAGE:-mysql:8.4.3}"
container="bixi-quartz-ha-${run_id//[^A-Za-z0-9]/-}"
container_id=""
owns_container=false
external_jdbc_url="${BIXI_QUARTZ_HA_JDBC_URL:-}"
mysql_password="${QUARTZ_HA_MYSQL_PASSWORD:-$(openssl rand -hex 24)}"

case "${docker_timeout}" in
	''|*[!0-9]*) echo 'QUARTZ_HA_DOCKER_COMMAND_SECONDS must be an integer.' >&2; exit 2 ;;
esac
case "${docker_preflight_timeout}" in
	''|*[!0-9]*) echo 'QUARTZ_HA_DOCKER_PREFLIGHT_SECONDS must be an integer.' >&2; exit 2 ;;
esac
if (( docker_timeout < 1 || docker_preflight_timeout < 1 )); then
	echo 'Quartz Docker timeouts must be at least one second.' >&2
	exit 2
fi

quartz_docker() {
	local timeout_seconds="$1"
	shift
	QUARTZ_DOCKER_INPUT_FILE="${QUARTZ_DOCKER_INPUT_FILE:-}" \
	python3 -c '
import os
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
timeout=timeout_seconds
arguments = ["docker", *sys.argv[2:]]
input_file = os.environ.get("QUARTZ_DOCKER_INPUT_FILE")
payload = None
if input_file:
    with open(input_file, "rb") as handle:
        payload = handle.read()
try:
    result = subprocess.run(arguments, input=payload, capture_output=True, timeout=timeout)
except FileNotFoundError:
    print("Docker CLI is unavailable.", file=sys.stderr)
    raise SystemExit(127)
except subprocess.TimeoutExpired:
    print(f"Docker command timed out after {timeout} seconds.", file=sys.stderr)
    raise SystemExit(124)
if result.stdout:
    sys.stdout.buffer.write(result.stdout)
if result.stderr:
    sys.stderr.buffer.write(result.stderr)
raise SystemExit(result.returncode)
' "${timeout_seconds}" "$@"
}

run_local_test_preflight() {
	if [[ "${BIXI_LOCAL_PREFLIGHT_SKIP:-false}" == "true" ]]; then
		echo 'WARNING: BIXI_LOCAL_PREFLIGHT_SKIP=true; local resource preflight was explicitly bypassed.' >&2
		return 0
	fi
	if BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT=true \
		BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE="${BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE:-true}" \
		bash "${ROOT}/scripts/local-test-preflight.sh" single; then
		return 0
	else
		local status=$?
		echo "Environment blocked: local resource preflight failed (status=${status}); no Docker resources were created." >&2
		return "${status}"
	fi
}

cleanup() {
	local status=$?
    if [[ "${owns_container}" == true && ( -n "${container_id}" || -n "${container}" ) ]]; then
		quartz_docker 5 rm --force --volumes "${container_id:-${container}}" >/dev/null 2>&1 || true
	fi
	if (( status != 0 )); then
		echo "Quartz JDBC failover did not complete; log: ${log_file}" >&2
		if [[ -s "${log_file}" ]]; then
			tail -n 80 "${log_file}" >&2 || true
		fi
	fi
	exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -z "${external_jdbc_url}" ]]; then
	run_local_test_preflight single
	if ! command -v docker >/dev/null 2>&1; then
		echo 'Environment blocked: Docker CLI is unavailable.' >&2
		exit 2
	fi
	if ! quartz_docker "${docker_preflight_timeout}" version --format '{{.Server.Version}}' >/dev/null; then
		echo 'Environment blocked: Docker daemon did not answer the bounded server probe.' >&2
		exit 2
	fi
	echo "Starting disposable MySQL ${mysql_image}."
	if ! container_id="$(quartz_docker "${docker_timeout}" run --detach --rm --name "${container}" \
		--env MYSQL_ROOT_PASSWORD="${mysql_password}" \
		--env MYSQL_DATABASE=bixi_quartz_ha \
		--publish 127.0.0.1::3306 "${mysql_image}")"; then
		echo 'Environment blocked: disposable MySQL could not be started.' >&2
		exit 2
	fi
	owns_container=true
	ready=false
	for _ in $(seq 1 60); do
		if quartz_docker "${docker_timeout}" exec "${container_id}" sh -c \
			'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
			ready=true
			break
		fi
		sleep 1
	done
	if [[ "${ready}" != true ]]; then
		echo 'Environment blocked: disposable MySQL did not become ready within 60 seconds.' >&2
		exit 2
	fi
	mysql_port="$(quartz_docker "${docker_timeout}" port "${container_id}" 3306/tcp | sed -E 's/.*:([0-9]+)$/\1/' | tail -n 1)"
	case "${mysql_port}" in
		''|*[!0-9]*) echo 'Environment blocked: MySQL port could not be resolved.' >&2; exit 2 ;;
	esac
	export BIXI_QUARTZ_HA_JDBC_URL="jdbc:mysql://127.0.0.1:${mysql_port}/bixi_quartz_ha?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
	export QUARTZ_HA_JDBC_USERNAME=root
	export QUARTZ_HA_JDBC_PASSWORD="${mysql_password}"
	QUARTZ_DOCKER_INPUT_FILE="${ROOT}/scripts/quartz-ha-schema.sql" \
		quartz_docker "${docker_timeout}" exec -i "${container_id}" sh -c \
		'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot bixi_quartz_ha'
	else
	: "${QUARTZ_HA_JDBC_USERNAME:=root}"
	: "${QUARTZ_HA_JDBC_PASSWORD:=}"
	export QUARTZ_HA_JDBC_USERNAME QUARTZ_HA_JDBC_PASSWORD
	echo 'Using caller-provided BIXI_QUARTZ_HA_JDBC_URL; no database lifecycle is owned by this run.'
fi

mkdir -p "${report_dir}"

echo 'Running the two-node Quartz JDBC kill-owner/recovery exercise.'
if ! BIXI_QUARTZ_HA_JDBC_URL="${BIXI_QUARTZ_HA_JDBC_URL}" \
	QUARTZ_HA_JDBC_USERNAME="${QUARTZ_HA_JDBC_USERNAME}" \
	QUARTZ_HA_JDBC_PASSWORD="${QUARTZ_HA_JDBC_PASSWORD}" \
	mvn -Pcloud -pl bixi-module/bixi-quartz -am \
	-Dtest=QuartzJdbcFailoverIntegrationTest \
	-Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test >"${log_file}" 2>&1; then
	cat "${log_file}"
	exit 1
fi
cat "${log_file}"

cat >"${report_file}" <<EOF
{
  "suite": "quartz-jdbc-failover",
  "observedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "result": "passed",
  "environment": "$(if [[ -n "${external_jdbc_url}" ]]; then echo external-jdbc; else echo disposable-mysql; fi)",
  "applicationLevel": false,
  "checks": {
    "jdbcClusteredJobStore": true,
    "longTaskOwnerKilled": true,
    "survivorRecovery": true,
    "nonConcurrentRecovery": true,
    "failureHistory": true
  },
  "scope": "Quartz scheduler semantics only; this does not claim Bixi HTTP or full Single/Cloud application HA."
}
EOF
echo "Quartz JDBC failover evidence: ${report_file}"
