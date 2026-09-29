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

restart_mysql_container="bixi-workflow-process-restart-mysql-$$-$(openssl rand -hex 6)"
restart_mysql_id=""
restart_mysql_password="$(openssl rand -hex 24)"
restart_mysql_image="${WORKFLOW_RESTART_MYSQL_IMAGE:-mysql:8.0.45}"
restart_docker_preflight_seconds="${WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS:-5}"
restart_docker_command_seconds="${WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS:-10}"

case "$restart_docker_preflight_seconds" in
  ''|*[!0-9]*)
    echo 'WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS must be a positive integer.' >&2
    exit 2
    ;;
esac
if (( restart_docker_preflight_seconds < 1 )); then
  echo 'WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS must be at least 1 second.' >&2
  exit 2
fi
case "$restart_docker_command_seconds" in
  ''|*[!0-9]*)
    echo 'WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS must be a positive integer.' >&2
    exit 2
    ;;
esac
if (( restart_docker_command_seconds < 1 )); then
  echo 'WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS must be at least 1 second.' >&2
  exit 2
fi

# Docker CLI calls can block forever when the desktop VM/socket is unhealthy. Do
# one bounded server probe before creating anything, so an unavailable runtime
# is reported as an environment block instead of leaving a test process behind.
if ! command -v docker >/dev/null 2>&1; then
  echo 'Environment blocked: Docker CLI is unavailable.' >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo 'Environment blocked: python3 is required for the bounded Docker probe.' >&2
  exit 2
fi
if ! restart_docker_version="$(python3 - "$restart_docker_preflight_seconds" <<'PY'
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
try:
    result = subprocess.run(
        ["docker", "version", "--format", "{{.Server.Version}}"],
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout_seconds,
    )
except subprocess.TimeoutExpired:
    print("Docker server probe timed out", file=sys.stderr)
    raise SystemExit(124)
if result.returncode != 0:
    detail = (result.stderr or result.stdout).strip()
    if detail:
        print(detail[-500:], file=sys.stderr)
    raise SystemExit(result.returncode or 1)
print(result.stdout.strip() or "unknown")
PY
)"; then
  echo 'Environment blocked: Docker daemon did not answer the bounded server probe.' >&2
  exit 2
fi
echo "Docker server ready: ${restart_docker_version}"

restart_now_millis() {
  python3 -c 'import time; print(int(time.monotonic() * 1000))'
}

restart_docker() {
  python3 - "$restart_docker_command_seconds" "$@" <<'PY'
import subprocess
import sys

timeout_seconds = int(sys.argv[1])
arguments = ["docker", *sys.argv[2:]]
try:
    result = subprocess.run(
        arguments,
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout_seconds,
    )
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

cleanup() {
  if [[ -n "$restart_mysql_id" ]]; then
    restart_docker rm --force --volumes "$restart_mysql_id" >/dev/null 2>&1 || true
  else
    # docker run can create the named container before its CLI call times out;
    # use the invocation-specific name as a bounded cleanup fallback.
    restart_docker rm --force --volumes "$restart_mysql_container" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! restart_mysql_id="$(restart_docker run --detach --name "$restart_mysql_container" \
  --env MYSQL_ROOT_PASSWORD="$restart_mysql_password" \
  --env MYSQL_DATABASE=workflow_process_restart \
  --publish 127.0.0.1::3306 "$restart_mysql_image")"; then
  echo 'Environment blocked: Docker could not create the disposable MySQL container.' >&2
  exit 2
fi

ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if restart_docker exec "$restart_mysql_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  else
    restart_docker_status=$?
    if (( restart_docker_status == 124 )); then
      echo 'Environment blocked: Docker exec timed out while waiting for MySQL.' >&2
      exit 2
    fi
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi

if ! restart_mysql_port="$(restart_docker port "$restart_mysql_container" 3306/tcp)"; then
  echo 'Environment blocked: Docker did not return the disposable MySQL port.' >&2
  exit 2
fi
restart_mysql_port="${restart_mysql_port##*:}"
export OUTBOX_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${restart_mysql_port}/workflow_process_restart?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
export MYSQL_ROOT_PASSWORD="$restart_mysql_password"

mvn -pl bixi-common/bixi-common-mq -am -Dmaven.test.skip=true package

echo 'Starting an isolated worker JVM and waiting for a persisted kill marker.'
RELIABLE_RABBIT_PROCESS_PHASE=hold mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitProcessRestartIntegrationTest#workerProcessWaitsAfterBusinessWriteBeforeCommit \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test >"${TMPDIR:-/tmp}/bixi-workflow-process-restart-worker.log" 2>&1 &
worker_pid=$!

marker_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if marker_count="$(restart_docker exec "$restart_mysql_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -h127.0.0.1 -uroot workflow_process_restart -e "SELECT COUNT(*) FROM process_restart_marker WHERE stage=0x48414e444c45525f454e5445524544"' \
      2>/dev/null)"; then
    :
  else
    restart_docker_status=$?
    if (( restart_docker_status == 124 )); then
      echo 'Environment blocked: Docker exec timed out while polling the kill marker.' >&2
      exit 2
    fi
    marker_count=''
  fi
  if [[ "$marker_count" == 1 ]]; then
    marker_ready=true
    break
  fi
  if ! kill -0 "$worker_pid" 2>/dev/null; then
    cat "${TMPDIR:-/tmp}/bixi-workflow-process-restart-worker.log" >&2 || true
    echo 'Worker JVM exited before the persisted kill marker appeared.' >&2
    exit 1
  fi
  sleep 1
done
if [[ "$marker_ready" != true ]]; then
  echo 'Worker JVM did not reach the persisted kill marker within 60 seconds.' >&2
  exit 1
fi

echo "Killing isolated worker JVM ${worker_pid} after handler entry."
restart_recovery_started_ms="$(restart_now_millis)"
kill -KILL "$worker_pid" 2>/dev/null || true
wait "$worker_pid" 2>/dev/null || true

RELIABLE_RABBIT_PROCESS_PHASE=resume mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitProcessRestartIntegrationTest#freshWorkerRecoversTheRolledBackBusinessTransaction \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test

restart_recovery_finished_ms="$(restart_now_millis)"
restart_recovery_elapsed_ms=$((restart_recovery_finished_ms - restart_recovery_started_ms))
echo "Workflow process restart recovery passed (observed elapsed_ms=${restart_recovery_elapsed_ms}; marker-to-resume script wall clock, not an SLO)."
echo 'Workflow owner process restart recovery passed.'
