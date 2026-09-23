#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
ROOT="$PWD"

if [[ ! -s "${ROOT}/.env" ]]; then
  echo 'Missing .env. Run make init-env first.' >&2
  exit 1
fi
command -v docker >/dev/null 2>&1 || { echo 'Docker is required.' >&2; exit 1; }

perf_sample_size="${WORKFLOW_PERF_SAMPLE_SIZE:-12}"
perf_concurrency="${WORKFLOW_PERF_CONCURRENCY:-4}"
perf_warmup="${WORKFLOW_PERF_WARMUP:-2}"
perf_backlog_interval="${WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS:-250}"
WORKFLOW_PERF_SAMPLE_SIZE="${perf_sample_size}" \
WORKFLOW_PERF_CONCURRENCY="${perf_concurrency}" \
WORKFLOW_PERF_WARMUP="${perf_warmup}" \
WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS="${perf_backlog_interval}" \
node --input-type=module -e \
  "import { parsePerformanceConfig } from './scripts/workflow-performance-metrics.mjs'; parsePerformanceConfig(process.env);"

run_id="$(date -u +%Y%m%d%H%M%S)-$$"
project="bixi-wf-fault-${run_id}"
report_dir="${ROOT}/target/workflow-cluster-failover/${run_id}"
env_file="${report_dir}/fault.env"
override_file="${report_dir}/overrides.env"
mkdir -p "${report_dir}"

compose() {
  docker compose --env-file "${env_file}" --project-name "${project}" \
    -f "${ROOT}/compose.yaml" \
    -f "${ROOT}/compose.workflow-cluster.yaml" \
    -f "${ROOT}/compose.workflow-fault-test.yaml" "$@"
}

sanitize_diagnostics() {
  BIXI_SANITIZER_ROOT="${ROOT}" \
  BIXI_SANITIZER_ENV_FILE="${env_file}" \
  node scripts/workflow-performance-metrics.mjs sanitize-stdin
}

verify_flowable_runtime_properties() {
  actual="$(compose exec -T mysql sh -c \
    'exec mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" --batch --skip-column-names' <<'SQL'
SELECT CONCAT(NAME_, '=', VALUE_)
FROM ACT_GE_PROPERTY
WHERE NAME_ IN (
  'cfg.execution-related-entities-count',
  'cfg.task-related-entities-count'
)
ORDER BY NAME_;
SQL
)"
  expected="$(printf '%s\n%s' \
    'cfg.execution-related-entities-count=true' \
    'cfg.task-related-entities-count=true')"
  if [[ "${actual}" != "${expected}" ]]; then
    echo 'Controlled migration did not initialize the Flowable relationship-count settings.' >&2
    printf '%s\n' "${actual}" >&2
    exit 1
  fi
}

cleanup() {
  status=$?
  if [[ "${project}" == bixi-wf-fault-* && -s "${env_file}" ]]; then
    if [[ "${status}" -ne 0 ]]; then
      echo "Fault-test project ${project} failed; preserving a short diagnostic before cleanup." >&2
      compose --profile cloud --profile workflow-cluster ps 2>&1 | sanitize_diagnostics >&2 || true
      compose --profile cloud --profile workflow-cluster logs --no-color --tail=160 workflow-a workflow-b upms auth gateway 2>&1 | sanitize_diagnostics >&2 || true
      compose --profile cloud --profile workflow-cluster logs --no-color --tail=120 mysql nacos nacos-config 2>&1 | sanitize_diagnostics >&2 || true
    fi
    compose --profile cloud --profile workflow-cluster down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  rm -f "${env_file}" "${override_file}" "${env_file}.tmp"
  exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cat >"${override_file}" <<EOF
COMPOSE_PROJECT_NAME=${project}
MYSQL_DATABASE=bixi_wf_fault_${run_id//[^A-Za-z0-9]/_}
NACOS_NAMESPACE=bixi-wf-fault-${run_id}
WORKFLOW_ENABLED=true
WORKFLOW_CLUSTER_ENABLED=true
WORKFLOW_SCHEMA_UPDATE=false
WORKFLOW_ASYNC_EXECUTOR_ACTIVATE=true
BIXI_RELIABLE_ENABLED=true
BIXI_RELIABLE_RABBIT_ENABLED=true
WORKFLOW_ASYNC_JOB_LOCK_TIME=PT8S
WORKFLOW_TIMER_LOCK_TIME=PT8S
WORKFLOW_RESET_EXPIRED_JOBS_INTERVAL=PT1S
WORKFLOW_DEFAULT_ASYNC_JOB_ACQUIRE_WAIT_TIME=PT1S
WORKFLOW_DEFAULT_TIMER_JOB_ACQUIRE_WAIT_TIME=PT1S
WORKFLOW_MAX_ASYNC_JOBS_DUE_PER_ACQUISITION=1
WORKFLOW_MAX_TIMER_JOBS_PER_ACQUISITION=1
WORKFLOW_RESET_EXPIRED_JOBS_PAGE_SIZE=100
WORKFLOW_RESET_EXPIRED_JOB_ENABLED=true
WORKFLOW_UNLOCK_OWNED_JOBS=true
WORKFLOW_PERF_SAMPLE_SIZE=${perf_sample_size}
WORKFLOW_PERF_CONCURRENCY=${perf_concurrency}
WORKFLOW_PERF_WARMUP=${perf_warmup}
WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS=${perf_backlog_interval}
WORKFLOW_MAINTENANCE=true
WORKFLOW_MIGRATION_CONFIRM=APPLY_WORKFLOW_MIGRATIONS
WORKFLOW_JAVA_OPTS='-Xms96m -Xmx320m'
UPMS_JAVA_OPTS='-Xms128m -Xmx384m'
AUTH_JAVA_OPTS='-Xms96m -Xmx256m'
GATEWAY_JAVA_OPTS='-Xms96m -Xmx256m'
MYSQL_PORT=0
REDIS_PORT=0
RABBITMQ_PORT=0
RABBITMQ_MANAGEMENT_PORT=0
NACOS_PORT=0
GATEWAY_PORT=0
BIXI_HTTP_PORT=0
EOF

cp "${ROOT}/.env" "${env_file}"
awk -F= '
  NR == FNR {
    if ($0 ~ /^[A-Za-z_][A-Za-z0-9_]*=/) {
      key = substr($0, 1, index($0, "=") - 1)
      if (!(key in override)) order[++count] = key
      override[key] = $0
    }
    next
  }
  {
    key = ($0 ~ /^[A-Za-z_][A-Za-z0-9_]*=/) ? substr($0, 1, index($0, "=") - 1) : ""
    if (key in override) {
      print override[key]
      delete override[key]
    } else {
      print
    }
  }
  END {
    for (i = 1; i <= count; i++) {
      key = order[i]
      if (key in override) print override[key]
    }
  }
' "${override_file}" "${env_file}" >"${env_file}.tmp"
mv "${env_file}.tmp" "${env_file}"

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

echo "Starting isolated Workflow failover project ${project}."
compose --profile cloud --profile workflow-cluster up -d --wait mysql redis rabbitmq nacos
compose --profile cloud --profile workflow-cluster up -d nacos-config
nacos_config_id="$(compose --profile cloud --profile workflow-cluster ps -q nacos-config)"
for attempt in $(seq 1 60); do
  nacos_config_state="$(docker inspect --format '{{.State.Status}}' "${nacos_config_id}" 2>/dev/null || true)"
  if [[ "${nacos_config_state}" == exited || "${nacos_config_state}" == dead ]]; then
    nacos_config_exit="$(docker inspect --format '{{.State.ExitCode}}' "${nacos_config_id}")"
    if [[ "${nacos_config_exit}" != 0 ]]; then
      echo "nacos-config exited with code ${nacos_config_exit}." >&2
      exit 1
    fi
    break
  fi
  if [[ "${attempt}" == 60 ]]; then
    echo 'nacos-config did not finish within 60 seconds.' >&2
    exit 1
  fi
  sleep 1
done

echo 'Applying Flowable 7.1.0 and reliable Workflow migrations in the isolated database.'
BIXI_ENV_FILE="${env_file}" bash "${ROOT}/scripts/migrate-workflow-schema.sh"
verify_flowable_runtime_properties

echo 'Deferring the recovery-audit migration for the controlled maintenance-window exercise.'
compose exec -T mysql sh -c \
  'exec mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' <<'SQL'
DROP TABLE IF EXISTS wf_recovery_audit;
DELETE FROM bixi_schema_migration WHERE migration_id='20260922_workflow_recovery_audit.sql';
SQL

echo 'Building and starting the two fault-test Workflow replicas.'
compose --profile cloud --profile workflow-cluster up -d --build --wait upms auth gateway workflow-a workflow-b

gateway_port="$(compose --profile cloud --profile workflow-cluster port gateway 9999 | sed -E 's/.*:([0-9]+)$/\1/' | tail -n 1)"
case "${gateway_port}" in
  ''|*[!0-9]*) echo "Could not resolve the temporary Gateway port: ${gateway_port}" >&2; exit 1 ;;
esac

echo "Running lock-owner failover checks through temporary Gateway port ${gateway_port}."
BIXI_FAULT_ENV_FILE="${env_file}" \
BIXI_FAULT_PROJECT="${project}" \
BIXI_FAULT_REPORT_FILE="${report_dir}/report.json" \
BIXI_FAULT_GATEWAY_PORT="${gateway_port}" \
BIXI_FAULT_DEFERRED_MIGRATION=bixi-project-documents/sql/migrations/20260922_workflow_recovery_audit.sql \
NACOS_NAMESPACE="${NACOS_NAMESPACE}" \
node "${ROOT}/scripts/workflow-cluster-failover.mjs"

echo "Workflow cluster failover report: ${report_dir}/report.json"
