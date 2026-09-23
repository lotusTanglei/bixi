#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
if [[ "$(uname -s)" == "Darwin" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

rabbit_test_mysql_container="bixi-reliable-rabbit-mysql-$$"
rabbit_test_broker_container="bixi-reliable-rabbit-broker-$$"
rabbit_test_mysql_id=""
rabbit_test_broker_id=""
rabbit_ack_worker_pid=""
rabbit_ack_worker_log=""
rabbit_test_mysql_password="$(openssl rand -hex 24)"
rabbit_test_mysql_image="${RELIABLE_RABBIT_MYSQL_IMAGE:-mysql:8.0.45}"
rabbit_test_broker_image="${RELIABLE_RABBIT_IMAGE:-rabbitmq:4.0.5-management-alpine}"

cleanup() {
  if [[ -n "$rabbit_ack_worker_pid" ]] && kill -0 "$rabbit_ack_worker_pid" 2>/dev/null; then
    kill -KILL "$rabbit_ack_worker_pid" 2>/dev/null || true
    wait "$rabbit_ack_worker_pid" 2>/dev/null || true
  fi
  if [[ -n "$rabbit_ack_worker_log" ]]; then
    rm -f "$rabbit_ack_worker_log"
  fi
  if [[ -n "$rabbit_test_broker_id" ]]; then
    docker rm --force --volumes "$rabbit_test_broker_id" >/dev/null 2>&1 || true
  fi
  if [[ -n "$rabbit_test_mysql_id" ]]; then
    docker rm --force --volumes "$rabbit_test_mysql_id" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

rabbit_test_mysql_id="$(docker run --detach --name "$rabbit_test_mysql_container" \
  --env MYSQL_ROOT_PASSWORD="$rabbit_test_mysql_password" \
  --env MYSQL_DATABASE=reliable_test \
  --publish 127.0.0.1::3306 "$rabbit_test_mysql_image")"
rabbit_test_broker_id="$(docker run --detach --name "$rabbit_test_broker_container" \
  --env RABBITMQ_DEFAULT_USER=test \
  --env RABBITMQ_DEFAULT_PASS=test \
  --publish 127.0.0.1::5672 "$rabbit_test_broker_image")"

rabbit_test_mysql_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if docker exec "$rabbit_test_mysql_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    rabbit_test_mysql_ready=true
    break
  fi
  sleep 2
done
if [[ "$rabbit_test_mysql_ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi

rabbit_test_broker_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  if docker exec "$rabbit_test_broker_container" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
    rabbit_test_broker_ready=true
    break
  fi
  sleep 1
done
if [[ "$rabbit_test_broker_ready" != true ]]; then
  echo 'Disposable RabbitMQ did not become ready within 60 seconds.' >&2
  exit 1
fi

rabbit_test_mysql_port="$(docker port "$rabbit_test_mysql_container" 3306/tcp)"
rabbit_test_mysql_port="${rabbit_test_mysql_port##*:}"
rabbit_test_broker_port="$(docker port "$rabbit_test_broker_container" 5672/tcp)"
rabbit_test_broker_port="${rabbit_test_broker_port##*:}"
export OUTBOX_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${rabbit_test_mysql_port}/reliable_test?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
export MYSQL_ROOT_PASSWORD="$rabbit_test_mysql_password"
export WORKFLOW_TEST_JDBC_URL="$OUTBOX_TEST_JDBC_URL"
export WORKFLOW_TEST_DB_USER=root
export WORKFLOW_TEST_DB_PASSWORD="$MYSQL_ROOT_PASSWORD"
export RELIABLE_RABBIT_TEST_HOST=127.0.0.1
export RELIABLE_RABBIT_TEST_PORT="$rabbit_test_broker_port"
export RELIABLE_RABBIT_TEST_USERNAME=test
export RELIABLE_RABBIT_TEST_PASSWORD=test
export RELIABLE_RABBIT_TEST_BROKER_CONTAINER="$rabbit_test_broker_container"

docker exec "$rabbit_test_mysql_container" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT VERSION() AS mysql_version, @@transaction_isolation AS transaction_isolation"'

# Compile the selected module without touching unrelated dirty test sources in the reactor.
mvn -Pcloud -pl bixi-common/bixi-common-mq -am -Dmaven.test.skip=true package
mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitOwnerEndpointIntegrationTest,RabbitInboxListenerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# Keep the broker-restart assertion across separate JVMs: seed a durable queue,
# restart the actual broker container, then start a fresh consumer process.
mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitBrokerRestartIntegrationTest#seedDurableMessageBeforeExternalBrokerRestart \
  -Dsurefire.failIfNoSpecifiedTests=false test
rabbit_test_broker_port_before_restart="$rabbit_test_broker_port"
docker restart "$rabbit_test_broker_container" >/dev/null
rabbit_test_broker_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  rabbit_test_broker_port="$(docker port "$rabbit_test_broker_container" 5672/tcp | tail -n 1)"
  rabbit_test_broker_port="${rabbit_test_broker_port##*:}"
  if docker exec "$rabbit_test_broker_container" rabbitmq-diagnostics -q ping >/dev/null 2>&1 \
      && nc -z -w 1 127.0.0.1 "$rabbit_test_broker_port" >/dev/null 2>&1; then
    rabbit_test_broker_ready=true
    break
  fi
  sleep 1
done
if [[ "$rabbit_test_broker_ready" != true ]]; then
  echo 'RabbitMQ did not become reachable after broker restart.' >&2
  exit 1
fi
export RELIABLE_RABBIT_TEST_PORT="$rabbit_test_broker_port"
echo "RabbitMQ broker restart ready: ${rabbit_test_broker_port_before_restart} -> ${rabbit_test_broker_port}"
mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitBrokerRestartIntegrationTest#consumeDurableMessageAfterExternalBrokerRestart \
  -Dsurefire.failIfNoSpecifiedTests=false test

# The common-module tests prove the transport primitive. This workflow test proves
# the same durable transport carries the real Flowable/UPMS automatic-task business loop.
mvn -Pcloud -pl bixi-module/bixi-workflow-biz -am \
  -Dtest=WorkflowUpmsAutomaticTaskRabbitIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# Commit the target business transaction, stop the consumer JVM before manual ack,
# then prove a fresh JVM acknowledges the broker redelivery without rerunning the handler.
RELIABLE_RABBIT_ACK_PHASE=seed mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitAckCrashIntegrationTest#seedPersistentDelivery \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test

rabbit_ack_worker_log="$(mktemp "${TMPDIR:-/tmp}/bixi-rabbit-ack-crash.XXXXXX.log")"
RELIABLE_RABBIT_ACK_PHASE=hold mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitAckCrashIntegrationTest#processWaitsAfterCommitBeforeManualAck \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test >"$rabbit_ack_worker_log" 2>&1 &
rabbit_ack_worker_pid=$!

rabbit_ack_marker_ready=false
for ((attempt = 0; attempt < 60; attempt++)); do
  marker_count="$(docker exec "$rabbit_test_mysql_container" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -B -h127.0.0.1 -uroot reliable_test -e "SELECT COUNT(*) FROM ack_crash_marker WHERE stage=0x434f4d4d49545445445f4245464f52455f41434b"' \
    2>/dev/null || true)"
  if [[ "$marker_count" == 1 ]]; then
    rabbit_ack_marker_ready=true
    break
  fi
  if ! kill -0 "$rabbit_ack_worker_pid" 2>/dev/null; then
    cat "$rabbit_ack_worker_log" >&2 || true
    echo 'Rabbit ack-crash worker exited before reaching the committed-before-ack marker.' >&2
    exit 1
  fi
  sleep 1
done
if [[ "$rabbit_ack_marker_ready" != true ]]; then
  cat "$rabbit_ack_worker_log" >&2 || true
  echo 'Rabbit ack-crash worker did not reach the kill marker within 60 seconds.' >&2
  exit 1
fi

echo "Killing isolated Rabbit consumer JVM ${rabbit_ack_worker_pid} after commit and before manual ack."
kill -KILL "$rabbit_ack_worker_pid" 2>/dev/null || true
wait "$rabbit_ack_worker_pid" 2>/dev/null || true
rabbit_ack_worker_pid=""

RELIABLE_RABBIT_ACK_PHASE=resume mvn -pl bixi-common/bixi-common-mq \
  -Dtest=RabbitAckCrashIntegrationTest#freshConsumerAcknowledgesRedeliveryWithoutRepeatingBusinessEffect \
  -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=0 test

echo 'Rabbit commit-before-ack crash recovery passed.'
