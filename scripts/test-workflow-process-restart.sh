#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
if [[ "$(uname -s)" == "Darwin" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

restart_mysql_container="bixi-workflow-process-restart-mysql-$$"
restart_mysql_id=""
restart_mysql_password="$(openssl rand -hex 24)"
restart_mysql_image="${WORKFLOW_RESTART_MYSQL_IMAGE:-mysql:8.0.45}"

cleanup() {
  if [[ -n "$restart_mysql_id" ]]; then
    docker rm --force --volumes "$restart_mysql_id" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

restart_mysql_id="$(docker run --detach --name "$restart_mysql_container" \
  --env MYSQL_ROOT_PASSWORD="$restart_mysql_password" \
  --env MYSQL_DATABASE=workflow_process_restart \
  --publish 127.0.0.1::3306 "$restart_mysql_image")"

ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if docker exec "$restart_mysql_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi

restart_mysql_port="$(docker port "$restart_mysql_container" 3306/tcp)"
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
  marker_count="$(docker exec "$restart_mysql_container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -h127.0.0.1 -uroot workflow_process_restart -e "SELECT COUNT(*) FROM process_restart_marker WHERE stage=0x48414e444c45525f454e5445524544"' \
    2>/dev/null || true)"
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
kill -KILL "$worker_pid" 2>/dev/null || true
wait "$worker_pid" 2>/dev/null || true

RELIABLE_RABBIT_PROCESS_PHASE=resume mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitProcessRestartIntegrationTest#freshWorkerRecoversTheRolledBackBusinessTransaction \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test

echo 'Workflow owner process restart recovery passed.'
