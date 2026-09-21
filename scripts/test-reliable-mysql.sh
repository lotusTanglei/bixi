#!/usr/bin/env bash
set -euo pipefail
set +x

# The suite resets its tables; always create a dedicated, disposable database.
reliable_test_selection="${1:-both}"
case "$reliable_test_selection" in
  cloud|single) reliable_test_profiles=("$reliable_test_selection") ;;
  both) reliable_test_profiles=(cloud single) ;;
  *) echo "Usage: $0 [cloud|single|both]" >&2; exit 2 ;;
esac
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" == "Darwin" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

reliable_test_env="$(mktemp)"
reliable_test_container="bixi-reliable-test-${reliable_test_selection}-$$"
reliable_test_container_id=""
reliable_test_image="${RELIABLE_TEST_MYSQL_IMAGE:-mysql:8.0.45}"
cleanup() {
  if [[ -n "$reliable_test_container_id" ]]; then
    docker rm --force --volumes "$reliable_test_container_id" >/dev/null 2>&1 || true
  fi
  rm -f "$reliable_test_env"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chmod 600 "$reliable_test_env"
reliable_test_password="$(openssl rand -hex 24)"
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=reliable_test\n' "$reliable_test_password" > "$reliable_test_env"
reliable_test_container_id="$(docker run --detach --name "$reliable_test_container" --env-file "$reliable_test_env" \
  --publish 127.0.0.1::3306 "$reliable_test_image")"

reliable_test_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if docker exec "$reliable_test_container" sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    reliable_test_ready=true
    break
  fi
  sleep 2
done
if [[ "$reliable_test_ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi
reliable_test_port="$(docker port "$reliable_test_container" 3306/tcp)"
reliable_test_port="${reliable_test_port##*:}"
export OUTBOX_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${reliable_test_port}/reliable_test?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8"
export MYSQL_ROOT_PASSWORD="$reliable_test_password"
docker exec "$reliable_test_container" sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -e "SELECT VERSION() AS mysql_version, @@transaction_isolation AS transaction_isolation"'
for reliable_test_profile in "${reliable_test_profiles[@]}"; do
  echo "Running reliable messaging tests against disposable ${reliable_test_image} (${reliable_test_profile})."
  mvn -P"$reliable_test_profile" -pl bixi-common/bixi-common-mq -am \
    -Dtest='DurableMessageTest,ReliableDeliveryPropertiesTest,JdbcOutboxStoreTest,OutboxDispatcherTest,JdbcInboxStoreTest,InboxExecutorTest,LocalDurableTransportTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
done
