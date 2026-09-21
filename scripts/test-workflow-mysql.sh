#!/usr/bin/env bash
set -euo pipefail
set +x

# Use an isolated database: the integration suite recreates its business tables.
workflow_test_selection="${1:-both}"
read -r -a workflow_test_classes <<< "${WORKFLOW_TEST_CLASSES:-WorkflowApprovalIntegrationTest WorkflowStartIdempotencyTest WorkflowResultWithoutHistoryTest LeaveRequestIntegrationTest}"
case "$workflow_test_selection" in
  cloud|single) workflow_test_profiles=("$workflow_test_selection") ;;
  both) workflow_test_profiles=(cloud single) ;;
  *) echo "Usage: $0 [cloud|single|both]" >&2; exit 2 ;;
esac
cd "$(dirname "$0")/.."
if [[ "$(uname -s)" == "Darwin" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi

workflow_test_env="$(mktemp)"
workflow_test_container="bixi-workflow-test-${workflow_test_selection}-$$"
workflow_test_container_id=""
workflow_test_image="${WORKFLOW_TEST_MYSQL_IMAGE:-mysql:8.0.45}"
cleanup() {
  if [[ -n "$workflow_test_container_id" ]]; then
    docker rm --force --volumes "$workflow_test_container_id" >/dev/null 2>&1 || true
  fi
  rm -f "$workflow_test_env"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
chmod 600 "$workflow_test_env"
workflow_test_password="$(openssl rand -hex 24)"
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=workflow_approval_test\n' "$workflow_test_password" > "$workflow_test_env"
workflow_test_container_id="$(docker run --detach --name "$workflow_test_container" --env-file "$workflow_test_env" \
  --publish 127.0.0.1::3306 "$workflow_test_image")"

workflow_test_ready=false
for ((attempt = 0; attempt < 90; attempt++)); do
  if docker exec "$workflow_test_container" sh -c \
      'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -h127.0.0.1 -uroot -e "SELECT 1"' >/dev/null 2>&1; then
    workflow_test_ready=true
    break
  fi
  sleep 2
done
if [[ "$workflow_test_ready" != true ]]; then
  echo 'Disposable MySQL did not become ready within 180 seconds.' >&2
  exit 1
fi
workflow_test_port="$(docker port "$workflow_test_container" 3306/tcp)"
workflow_test_port="${workflow_test_port##*:}"
export WORKFLOW_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${workflow_test_port}/workflow_approval_test?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai"
export WORKFLOW_TEST_DB_USER=root
export WORKFLOW_TEST_DB_PASSWORD="$workflow_test_password"
docker exec "$workflow_test_container" sh -c \
  'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -h127.0.0.1 -uroot -e "SELECT VERSION() AS mysql_version, @@transaction_isolation AS transaction_isolation"'
for workflow_test_profile in "${workflow_test_profiles[@]}"; do
  for workflow_test_class in "${workflow_test_classes[@]}"; do
    # Flowable persists engine history configuration. Reset only this disposable schema
    # between suites so history=none and normal history execute against fresh engine tables.
    docker exec "$workflow_test_container" sh -c \
      'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; mysql -h127.0.0.1 -uroot -e "DROP DATABASE IF EXISTS workflow_approval_test; CREATE DATABASE workflow_approval_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"'
    workflow_test_module=bixi-module/bixi-workflow-biz
    if [[ "$workflow_test_class" == LeaveRequestIntegrationTest ]]; then
      workflow_test_module=bixi-module/bixi-upms-biz
    fi
    echo "Running ${workflow_test_class} against disposable ${workflow_test_image} (${workflow_test_profile})."
    mvn -P"$workflow_test_profile" -pl "$workflow_test_module" -am \
      -Dtest="$workflow_test_class" -Dsurefire.failIfNoSpecifiedTests=false test
  done
done
