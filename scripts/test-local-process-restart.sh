#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
ROOT="$PWD"
if [[ "$(uname -s)" == "Darwin" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

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

run_local_test_preflight single

local_restart_mysql_container="bixi-local-process-restart-mysql-$$-$(openssl rand -hex 6)"
local_restart_mysql_id=""
local_restart_mysql_password="$(openssl rand -hex 24)"
local_restart_mysql_image="${LOCAL_PROCESS_RESTART_MYSQL_IMAGE:-mysql:8.0.45}"
local_restart_preflight_seconds="${LOCAL_PROCESS_RESTART_DOCKER_PREFLIGHT_SECONDS:-5}"
local_restart_command_seconds="${LOCAL_PROCESS_RESTART_DOCKER_COMMAND_SECONDS:-20}"

for timeout_name in local_restart_preflight_seconds local_restart_command_seconds; do
  timeout_value="${!timeout_name}"
  case "$timeout_value" in
    ''|*[!0-9]*) echo "${timeout_name} must be a positive integer." >&2; exit 2 ;;
  esac
  (( timeout_value > 0 )) || { echo "${timeout_name} must be at least 1 second." >&2; exit 2; }
done

if ! command -v docker >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
  echo 'Environment blocked: Docker CLI and python3 are required.' >&2
  exit 2
fi

local_restart_docker() {
  python3 - "$local_restart_command_seconds" "$@" <<'PY'
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
try:
    result = subprocess.run(["docker", *sys.argv[2:]], check=False,
                            capture_output=True, text=True, timeout=timeout_seconds)
except subprocess.TimeoutExpired:
    print("Docker command timed out before completion.", file=sys.stderr)
    raise SystemExit(124)
if result.stdout:
    sys.stdout.write(result.stdout)
if result.stderr:
    sys.stderr.write(result.stderr)
raise SystemExit(result.returncode)
PY
}

if ! local_restart_docker_version="$(python3 - "$local_restart_preflight_seconds" <<'PY'
import subprocess
import sys

try:
    result = subprocess.run(["docker", "version", "--format", "{{.Server.Version}}"],
                            check=False, capture_output=True, text=True,
                            timeout=int(sys.argv[1]))
except subprocess.TimeoutExpired:
    raise SystemExit(124)
if result.returncode:
    raise SystemExit(result.returncode)
print(result.stdout.strip() or "unknown")
PY
)"; then
  echo 'Environment blocked: Docker daemon did not answer the bounded server probe.' >&2
  exit 2
fi
echo "Docker server ready: ${local_restart_docker_version}"

cleanup() {
  if [[ -n "$local_restart_mysql_id" ]]; then
    local_restart_docker rm --force --volumes "$local_restart_mysql_id" >/dev/null 2>&1 || true
  else
    local_restart_docker rm --force --volumes "$local_restart_mysql_container" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

local_restart_mysql_id="$(local_restart_docker run --detach --name "$local_restart_mysql_container" \
  --env MYSQL_ROOT_PASSWORD="$local_restart_mysql_password" \
  --env MYSQL_DATABASE=local_process_restart \
  --publish 127.0.0.1::3306 "$local_restart_mysql_image")"

local_restart_mysql_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if local_restart_docker exec "$local_restart_mysql_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    local_restart_mysql_ready=true
    break
  fi
  sleep 2
done
if [[ "$local_restart_mysql_ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi

local_restart_mysql_port="$(local_restart_docker port "$local_restart_mysql_container" 3306/tcp)"
local_restart_mysql_port="${local_restart_mysql_port##*:}"
export OUTBOX_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${local_restart_mysql_port}/local_process_restart?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
export MYSQL_ROOT_PASSWORD="$local_restart_mysql_password"

mvn -pl bixi-common/bixi-common-mq -am -Dmaven.test.skip=true package

local_restart_worker_log="$(mktemp "${TMPDIR:-/tmp}/bixi-local-process-restart.XXXXXX.log")"
local_restart_worker_pid=""
cleanup_worker() {
  if [[ -n "$local_restart_worker_pid" ]] && kill -0 "$local_restart_worker_pid" 2>/dev/null; then
    kill -KILL "$local_restart_worker_pid" 2>/dev/null || true
    wait "$local_restart_worker_pid" 2>/dev/null || true
  fi
  rm -f "$local_restart_worker_log"
}
trap cleanup_worker EXIT

echo 'Starting an isolated local transport JVM and waiting for a persisted kill marker.'
RELIABLE_LOCAL_PROCESS_PHASE=hold mvn -pl bixi-common/bixi-common-mq \
  -Dtest=LocalProcessRestartIntegrationTest#localTransportRollsBackHandlerAndLeavesLeasesRecoverableWhenOwnerProcessDies \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test >"$local_restart_worker_log" 2>&1 &
local_restart_worker_pid=$!

local_restart_marker_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  marker_count="$(local_restart_docker exec "$local_restart_mysql_container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -h127.0.0.1 -uroot local_process_restart -e "SELECT COUNT(*) FROM local_process_restart_marker WHERE stage=0x48414e444c45525f454e5445524544"' \
    2>/dev/null || true)"
  if [[ "$marker_count" == 1 ]]; then
    local_restart_marker_ready=true
    break
  fi
  if ! kill -0 "$local_restart_worker_pid" 2>/dev/null; then
    cat "$local_restart_worker_log" >&2 || true
    echo 'Local transport worker exited before reaching the persisted kill marker.' >&2
    exit 1
  fi
  sleep 1
done
if [[ "$local_restart_marker_ready" != true ]]; then
  cat "$local_restart_worker_log" >&2 || true
  echo 'Local transport worker did not reach the persisted kill marker within 60 seconds.' >&2
  exit 1
fi

echo "Killing isolated local transport JVM ${local_restart_worker_pid} after handler entry."
local_restart_started_ms="$(python3 -c 'import time; print(int(time.monotonic() * 1000))')"
kill -KILL "$local_restart_worker_pid" 2>/dev/null || true
wait "$local_restart_worker_pid" 2>/dev/null || true
local_restart_worker_pid=""

RELIABLE_LOCAL_PROCESS_PHASE=resume mvn -pl bixi-common/bixi-common-mq \
  -Dtest=LocalProcessRestartIntegrationTest#newLocalOwnerReclaimsBothLeasesAndCommitsBusinessExactlyOnce \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test

local_restart_finished_ms="$(python3 -c 'import time; print(int(time.monotonic() * 1000))')"
local_restart_elapsed_ms=$((local_restart_finished_ms - local_restart_started_ms))
echo "Local process restart recovery passed (observed elapsed_ms=${local_restart_elapsed_ms}; marker-to-resume script wall clock, not an SLO)."
