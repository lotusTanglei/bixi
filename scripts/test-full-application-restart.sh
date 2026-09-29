#!/usr/bin/env bash
set -euo pipefail
set +x

# Run a complete outage/restart rehearsal against a disposable Compose project.
# Existing Bixi projects are never addressed: every Compose call carries the
# invocation-specific project name and every cleanup operation is scoped to it.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN_ID="$(date -u +%Y%m%dt%H%M%sz)-$$"
PROJECT="bixi-full-restart-${RUN_ID}"
OUTPUT_ROOT="${FULL_RESTART_OUTPUT_DIR:-${ROOT}/target/phase2-runtime}"
RUN_DIR="${OUTPUT_ROOT}/full-application-restart-${RUN_ID}"
ENV_FILE="${RUN_DIR}/runtime.env"
COMPOSE_OVERRIDE="${ROOT}/scripts/full-application-restart.compose.yaml"
SOURCE_ENV="${FULL_RESTART_SOURCE_ENV:-${ROOT}/.env}"
# A full cloud plus single rehearsal is intentionally opt-in. Both stacks
# contain several JVMs and their dependencies; running them back-to-back on a
# personal Docker VM can exhaust memory and disk before cleanup runs. CI or a
# dedicated host may still request the matrix explicitly (for example,
# `FULL_RESTART_MODES=cloud,single`).
REQUESTED_MODES="${FULL_RESTART_MODES:-single}"
PREFLIGHT_BYPASSED=false
PREFLIGHT_BYPASS_VARIABLE=''

run_local_test_preflight() {
  local requested_mode="${1:-}"
  local preflight_status
  case "${requested_mode}" in
    single|cloud) ;;
    *)
      echo "Environment blocked: unsupported full restart mode '${requested_mode}'." >&2
      return 2
      ;;
  esac
  if [[ "${BIXI_LOCAL_PREFLIGHT_SKIP:-false}" == "true" ]]; then
    PREFLIGHT_BYPASSED=true
    PREFLIGHT_BYPASS_VARIABLE='BIXI_LOCAL_PREFLIGHT_SKIP'
    echo 'WARNING: BIXI_LOCAL_PREFLIGHT_SKIP=true; local resource preflight was explicitly bypassed.' >&2
    echo 'PREFLIGHT_BYPASS=true variable=BIXI_LOCAL_PREFLIGHT_SKIP' >&2
    return 0
  fi
  if BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT=true \
      bash "${ROOT}/scripts/local-test-preflight.sh" "${requested_mode}"; then
    return 0
  else
    preflight_status=$?
    echo "Environment blocked: local resource preflight failed for ${requested_mode} (status=${preflight_status}); no Docker resources were created." >&2
    return "${preflight_status}"
  fi
}

IFS=',' read -r -a requested_modes <<< "${REQUESTED_MODES}"
for mode in "${requested_modes[@]}"; do
  run_local_test_preflight "${mode}"
done
mkdir -p "${RUN_DIR}"
umask 077
cp "${SOURCE_ENV}" "${ENV_FILE}"

compose() {
  docker compose --project-name "${PROJECT}" --env-file "${ENV_FILE}" \
    -f "${ROOT}/compose.yaml" -f "${COMPOSE_OVERRIDE}" "$@"
}

set_env() {
  local key="$1"
  local value="$2"
  local temporary="${ENV_FILE}.tmp"
  awk -v key="${key}" -v value="${value}" '
    BEGIN { replaced = 0 }
    index($0, key "=") == 1 {
      if (!replaced) print key "=" value
      replaced = 1
      next
    }
    { print }
    END { if (!replaced) print key "=" value }
  ' "${ENV_FILE}" > "${temporary}"
  mv "${temporary}" "${ENV_FILE}"
}

free_port() {
  python3 - <<'PY'
import socket
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

now_epoch_ms() {
  python3 -c 'import time; print(int(time.time() * 1000))'
}

require_runtime() {
  command -v docker >/dev/null 2>&1 || { echo 'Environment blocked: Docker CLI is unavailable.' >&2; exit 2; }
  command -v python3 >/dev/null 2>&1 || { echo 'Environment blocked: python3 is unavailable.' >&2; exit 2; }
  docker version --format '{{.Server.Version}}' >/dev/null 2>&1 \
    || { echo 'Environment blocked: Docker daemon did not answer.' >&2; exit 2; }
  [[ -s "${SOURCE_ENV}" ]] || { echo "Environment blocked: source env does not exist: ${SOURCE_ENV}" >&2; exit 2; }
  [[ -s "${COMPOSE_OVERRIDE}" ]] || { echo "Environment blocked: missing Compose override: ${COMPOSE_OVERRIDE}" >&2; exit 2; }
}

configure_ports() {
  set_env COMPOSE_PROJECT_NAME "${PROJECT}"
  set_env BIXI_HTTP_PORT "$(free_port)"
  set_env MYSQL_PORT "$(free_port)"
  set_env REDIS_PORT "$(free_port)"
  set_env SINGLE_PORT "$(free_port)"
  set_env GATEWAY_PORT "$(free_port)"
  set_env MONITOR_PORT "$(free_port)"
  set_env RABBITMQ_PORT "$(free_port)"
  set_env RABBITMQ_MANAGEMENT_PORT "$(free_port)"
  set_env NACOS_PORT "$(free_port)"
  # Keep the rehearsal below the memory footprint of a full developer stack.
  set_env GATEWAY_JAVA_OPTS "-Xms64m -Xmx160m"
  set_env AUTH_JAVA_OPTS "-Xms64m -Xmx160m"
  set_env UPMS_JAVA_OPTS "-Xms96m -Xmx256m"
  set_env GENERATOR_JAVA_OPTS "-Xms64m -Xmx160m"
  set_env QUARTZ_JAVA_OPTS "-Xms64m -Xmx160m"
  set_env MONITOR_JAVA_OPTS "-Xms64m -Xmx160m"
  set_env WORKFLOW_JAVA_OPTS "-Xms96m -Xmx256m"
  set_env SINGLE_JAVA_OPTS "-Xms64m -Xmx192m"
  set_env WORKFLOW_ENABLED true
  set_env WORKFLOW_SCHEMA_UPDATE true
  set_env BIXI_RELIABLE_ENABLED true
  set_env BIXI_RELIABLE_RABBIT_ENABLED true
  set_env WORKFLOW_CLUSTER_ENABLED false
  set_env AI_ENABLED false
  set_env GENERATOR_ENABLED true
  set_env BIXI_MANAGEMENT_ENDPOINTS health
  set_env WORKFLOW_PUBLIC_START_MODELS stage2_form_acceptance
}

mode_services() {
  case "$1" in
    cloud) printf '%s\n' mysql redis rabbitmq rabbitmq-config nacos nacos-config monitor gateway upms auth generator quartz workflow frontend-cloud ;;
    single) printf '%s\n' mysql redis single frontend-single ;;
    *) echo "Unsupported mode: $1" >&2; return 2 ;;
  esac
}

compose_mode() {
  case "$1" in
    cloud) shift; compose --profile cloud --profile workflow "$@" ;;
    single) shift; compose --profile single "$@" ;;
    *) return 2 ;;
  esac
}

snapshot() {
  local mode="$1"
  local label="$2"
  local output="${RUN_DIR}/${mode}.${label}.jsonl"
  compose_mode "${mode}" ps --all --format json $(mode_services "${mode}") > "${output}" 2>&1 || true
}

compose_logs() {
  local mode="$1"
  local label="$2"
  compose_mode "${mode}" logs --no-color --tail=120 $(mode_services "${mode}") \
    > "${RUN_DIR}/${mode}.${label}.log" 2>&1 || true
}

services_ready() {
  local mode="$1"
  local label="$2"
  shift 2
  local snapshot_file="${RUN_DIR}/${mode}.readiness-${label}.jsonl"
  compose_mode "${mode}" ps --all --format json "$@" \
    > "${snapshot_file}" 2>&1 || return 1
  python3 - "${snapshot_file}" "$@" <<'PY'
import json
import sys

path, *expected = sys.argv[1:]
rows = []
for line in open(path, errors='replace'):
    line = line.strip()
    if not line:
        continue
    try:
        rows.append(json.loads(line))
    except json.JSONDecodeError:
        pass

by_service = {row.get('Service'): row for row in rows}
if any(service not in by_service for service in expected):
    raise SystemExit(1)
for service in expected:
    row = by_service[service]
    if row.get('State') != 'running' or (row.get('Health') or '') != 'healthy':
        raise SystemExit(1)
raise SystemExit(0)
PY
}

wait_services_ready() {
  local mode="$1"
  local label="$2"
  shift 2
  local timeout_seconds="${FULL_RESTART_HEALTH_TIMEOUT_SECONDS:-600}"
  local deadline="$(($(date +%s) + timeout_seconds))"
  while (( $(date +%s) < deadline )); do
    if services_ready "${mode}" "${label}" "$@"; then
      return 0
    fi
    sleep 5
  done
  return 1
}

wait_config_completed() {
  local mode="$1"
  local label="$2"
  shift 2
  local snapshot_file="${RUN_DIR}/${mode}.completion-${label}.jsonl"
  local timeout_seconds="${FULL_RESTART_HEALTH_TIMEOUT_SECONDS:-600}"
  local deadline="$(($(date +%s) + timeout_seconds))"
  while (( $(date +%s) < deadline )); do
    set +e
    compose_mode "${mode}" ps --all --format json "$@" > "${snapshot_file}" 2>&1
    local ps_status=$?
    set -e
    if [[ "${ps_status}" == 0 ]] && python3 - "${snapshot_file}" "$@" <<'PY'
import json
import sys

path, *expected = sys.argv[1:]
rows = []
for line in open(path, errors='replace'):
    line = line.strip()
    if not line:
        continue
    try:
        rows.append(json.loads(line))
    except json.JSONDecodeError:
        pass

by_service = {row.get('Service'): row for row in rows}
if any(service not in by_service for service in expected):
    raise SystemExit(1)
for service in expected:
    row = by_service[service]
    state = (row.get('State') or '').lower()
    exit_code = str(row.get('ExitCode', ''))
    if state not in {'exited', 'dead'} or exit_code != '0':
        raise SystemExit(1)
raise SystemExit(0)
PY
    then
      return 0
    fi
    sleep 5
  done
  return 1
}

start_mode_staged() {
  local mode="$1"
  local label="$2"
  local log="${RUN_DIR}/${mode}.${label}.start.log"
  : > "${log}"
  if [[ "${mode}" == single ]]; then
    echo "[${mode}] ${label}: starting mysql and redis" | tee -a "${log}"
    if ! compose_mode "${mode}" up --detach --no-build --no-deps mysql redis >> "${log}" 2>&1; then
      return 1
    fi
    if ! wait_services_ready "${mode}" "${label}-dependencies" mysql redis; then
      return 1
    fi
    echo "[${mode}] ${label}: starting single" | tee -a "${log}"
    if ! compose_mode "${mode}" up --detach --no-build --no-deps single >> "${log}" 2>&1; then
      return 1
    fi
    if ! wait_services_ready "${mode}" "${label}-application" single; then
      return 1
    fi
    echo "[${mode}] ${label}: starting frontend-single" | tee -a "${log}"
    if ! compose_mode "${mode}" up --detach --no-build --no-deps frontend-single >> "${log}" 2>&1; then
      return 1
    fi
    if ! wait_services_ready "${mode}" "${label}-frontend" frontend-single; then
      return 1
    fi
    return 0
  fi

  echo "[${mode}] ${label}: starting mysql, redis, rabbitmq and nacos" | tee -a "${log}"
  if ! compose_mode "${mode}" up --detach --no-build --no-deps mysql redis rabbitmq nacos >> "${log}" 2>&1; then
    return 1
  fi
  if ! wait_services_ready "${mode}" "${label}-dependencies" mysql redis rabbitmq nacos; then
    return 1
  fi
  echo "[${mode}] ${label}: publishing rabbitmq and nacos configuration" | tee -a "${log}"
  if ! compose_mode "${mode}" up --detach --no-build --no-deps rabbitmq-config nacos-config >> "${log}" 2>&1; then
    return 1
  fi
  if ! wait_config_completed "${mode}" "${label}-configuration" rabbitmq-config nacos-config; then
    return 1
  fi
  echo "[${mode}] ${label}: starting monitor, gateway, upms, generator, quartz and workflow" | tee -a "${log}"
  if ! compose_mode "${mode}" up --detach --no-build --no-deps monitor gateway upms generator quartz workflow >> "${log}" 2>&1; then
    return 1
  fi
  if ! wait_services_ready "${mode}" "${label}-backend" monitor gateway upms generator quartz workflow; then
    return 1
  fi
  echo "[${mode}] ${label}: starting auth" | tee -a "${log}"
  if ! compose_mode "${mode}" up --detach --no-build --no-deps auth >> "${log}" 2>&1; then
    return 1
  fi
  if ! wait_services_ready "${mode}" "${label}-auth" auth; then
    return 1
  fi
  echo "[${mode}] ${label}: starting frontend-cloud" | tee -a "${log}"
  if ! compose_mode "${mode}" up --detach --no-build --no-deps frontend-cloud >> "${log}" 2>&1; then
    return 1
  fi
  if ! wait_services_ready "${mode}" "${label}-frontend" frontend-cloud; then
    return 1
  fi
}

stack_ready() {
  local mode="$1"
  local snapshot_file="${RUN_DIR}/${mode}.readiness.jsonl"
  compose_mode "${mode}" ps --all --format json $(mode_services "${mode}") \
    > "${snapshot_file}" 2>&1 || return 1
  python3 - "${snapshot_file}" "${mode}" <<'PY'
import json
import sys

path, mode = sys.argv[1:]
rows = []
for line in open(path, errors='replace'):
    line = line.strip()
    if not line:
        continue
    try:
        rows.append(json.loads(line))
    except json.JSONDecodeError:
        pass

expected = {
    'cloud': {'mysql', 'redis', 'rabbitmq', 'nacos', 'monitor', 'gateway', 'upms', 'auth', 'generator', 'quartz', 'workflow', 'frontend-cloud'},
    'single': {'mysql', 'redis', 'single', 'frontend-single'},
}[mode]
seen = {row.get('Service') for row in rows}
if expected - seen:
    raise SystemExit(1)
for row in rows:
    service = row.get('Service')
    if service not in expected:
        continue
    state = row.get('State')
    health = row.get('Health') or ''
    if state != 'running' or health != 'healthy':
        raise SystemExit(1)
raise SystemExit(0)
PY
}

wait_stack_ready() {
  local mode="$1"
  local timeout_seconds="${FULL_RESTART_HEALTH_TIMEOUT_SECONDS:-600}"
  local deadline="$(($(date +%s) + timeout_seconds))"
  while (( $(date +%s) < deadline )); do
    if stack_ready "${mode}"; then
      return 0
    fi
    sleep 5
  done
  return 1
}

run_acceptance() {
  local mode="$1"
  local label="$2"
  local origin="http://127.0.0.1:$(awk -F= '$1 == "BIXI_HTTP_PORT" {print $2}' "${ENV_FILE}")"
  local output="${RUN_DIR}/${mode}.${label}.acceptance.log"
  set +e
  BIXI_MODE="${mode}" BIXI_ENV_FILE="${ENV_FILE}" BIXI_ORIGIN="${origin}" \
    node "${ROOT}/scripts/acceptance.mjs" > "${output}" 2>&1
  local status=$?
  set -e
  printf '%s\n' "${status}"
}

restart_mode() {
  # The caller may invoke this function from an `if` conditional to preserve a
  # per-mode result. Re-enable errexit here so a failed Compose operation never
  # falls through to an acceptance run against a partially created stack.
  set -e
  local mode="$1"
  local mode_started mode_stopped mode_healthy mode_finished
  local baseline_status post_status
  local evidence="${RUN_DIR}/${mode}.result"

  echo "[${mode}] starting isolated stack (${PROJECT})"
  mode_started="$(now_epoch_ms)"
  if [[ "${mode}" == cloud ]]; then
    set_env BIXI_RELIABLE_RABBIT_ENABLED true
  else
    set_env BIXI_RELIABLE_RABBIT_ENABLED false
  fi
  set +e
  start_mode_staged "${mode}" initial
  local start_status=$?
  set -e
  if ! wait_stack_ready "${mode}"; then
    compose_logs "${mode}" start-failed
    printf '%s\n' "start_status=${start_status}" "baseline_status=not-run" > "${evidence}"
    echo "[${mode}] isolated stack did not become healthy (status=${start_status})" >&2
    return 1
  fi
  snapshot "${mode}" baseline
  baseline_status="$(run_acceptance "${mode}" baseline)"
  mode_stopped=""
  mode_healthy=""
  mode_finished=""
  if [[ "${baseline_status}" != 0 ]]; then
    compose_logs "${mode}" baseline
    printf '%s\n' "baseline_status=${baseline_status}" > "${evidence}"
    echo "[${mode}] baseline acceptance failed (status=${baseline_status})" >&2
    return 1
  fi

  echo "[${mode}] stopping every application and dependency service"
  mode_stopped="$(now_epoch_ms)"
  set +e
  compose_mode "${mode}" stop --timeout 30 $(mode_services "${mode}") \
    > "${RUN_DIR}/${mode}.stop.log" 2>&1
  local stop_status=$?
  set -e
  if [[ "${stop_status}" != 0 ]]; then
    printf '%s\n' "baseline_status=${baseline_status}" "stop_status=${stop_status}" > "${evidence}"
    echo "[${mode}] isolated stack stop failed (status=${stop_status})" >&2
    return 1
  fi
  snapshot "${mode}" stopped

  echo "[${mode}] starting the complete stack after the outage"
  set +e
  start_mode_staged "${mode}" restart
  local restart_status=$?
  set -e
  if ! wait_stack_ready "${mode}"; then
    compose_logs "${mode}" restart-failed
    printf '%s\n' "baseline_status=${baseline_status}" "restart_status=${restart_status}" > "${evidence}"
    echo "[${mode}] isolated stack did not recover healthy (status=${restart_status})" >&2
    return 1
  fi
  mode_healthy="$(now_epoch_ms)"
  snapshot "${mode}" healthy
  post_status="$(run_acceptance "${mode}" post-restart)"
  mode_finished="$(now_epoch_ms)"
  if [[ "${post_status}" != 0 ]]; then
    compose_logs "${mode}" post-restart
    printf '%s\n' "baseline_status=${baseline_status}" "post_status=${post_status}" > "${evidence}"
    echo "[${mode}] post-restart acceptance failed (status=${post_status})" >&2
    return 1
  fi

  printf '%s\n' \
    "baseline_status=${baseline_status}" \
    "post_status=${post_status}" \
    "started_ms=${mode_started}" \
    "stop_requested_ms=${mode_stopped}" \
    "healthy_ms=${mode_healthy}" \
    "finished_ms=${mode_finished}" \
    "recovery_ms=$((mode_healthy - mode_stopped))" > "${evidence}"
  echo "[${mode}] complete restart evidence passed (recovery_ms=$((mode_healthy - mode_stopped)))"
}

reset_project_between_modes() {
  local from_mode="$1"
  local to_mode="$2"
  local result_file="${RUN_DIR}/${from_mode}-to-${to_mode}.cleanup.result"
  local log_file="${RUN_DIR}/${from_mode}-to-${to_mode}.cleanup.log"
  local cleanup_status

  echo "[${from_mode}] stopping the isolated Compose project before ${to_mode}"
  set +e
  compose --profile cloud --profile workflow --profile single down --volumes --remove-orphans \
    >"${log_file}" 2>&1
  cleanup_status=$?
  set -e
  printf '%s\n' \
    "from=${from_mode}" \
    "to=${to_mode}" \
    "status=${cleanup_status}" \
    "completed_at=$(now_epoch_ms)" >"${result_file}"
  if [[ "${cleanup_status}" != 0 ]]; then
    echo "[${from_mode}] isolated Compose cleanup before ${to_mode} failed (status=${cleanup_status})" >&2
    return 1
  fi
}

write_evidence() {
  local final_status="$1"
  local preflight_bypassed="${2:-${PREFLIGHT_BYPASSED}}"
  local preflight_bypass_variable="${3:-${PREFLIGHT_BYPASS_VARIABLE}}"
  python3 - "${RUN_DIR}" "${PROJECT}" "${REQUESTED_MODES}" "${final_status}" \
    "${preflight_bypassed}" "${preflight_bypass_variable}" <<'PY'
import json
import pathlib
import sys
from datetime import datetime, timezone

run_dir = pathlib.Path(sys.argv[1])
project = sys.argv[2]
modes = [value for value in sys.argv[3].split(',') if value]
final_status = sys.argv[4]
preflight_bypassed = sys.argv[5].lower() == 'true'
preflight_bypass_variable = sys.argv[6] or None

def read_jsonl(path):
    rows = []
    if not path.exists():
        return rows
    for line in path.read_text(errors='replace').splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            rows.append(json.loads(line))
        except json.JSONDecodeError:
            rows.append({'raw': line})
    return rows

def result(path):
    values = {}
    if path.exists():
        for line in path.read_text(errors='replace').splitlines():
            if '=' in line:
                key, value = line.split('=', 1)
                values[key] = int(value) if value.isdigit() else value
    return values

mode_records = []
for mode in modes:
    before = read_jsonl(run_dir / f'{mode}.baseline.jsonl')
    stopped = read_jsonl(run_dir / f'{mode}.stopped.jsonl')
    healthy = read_jsonl(run_dir / f'{mode}.healthy.jsonl')
    mode_records.append({
        'mode': mode,
        'result': result(run_dir / f'{mode}.result'),
        'services': {
            'baseline': before,
            'stopped': stopped,
            'healthy': healthy,
        },
        'acceptanceLogs': {
            'baseline': f'{mode}.baseline.acceptance.log',
            'postRestart': f'{mode}.post-restart.acceptance.log',
        },
    })

transitions = []
for index in range(max(0, len(modes) - 1)):
    from_mode = modes[index]
    to_mode = modes[index + 1]
    transition_path = run_dir / f'{from_mode}-to-{to_mode}.cleanup.result'
    transitions.append({
        'from': from_mode,
        'to': to_mode,
        'result': result(transition_path),
        'log': f'{from_mode}-to-{to_mode}.cleanup.log',
    })

payload = {
    'observedAt': datetime.now(timezone.utc).isoformat(),
    'project': project,
    'modesRequested': modes,
    'status': final_status,
    'preflight': {
        'bypassed': preflight_bypassed,
        'bypassVariable': preflight_bypass_variable,
        'evidenceEligibleForPersonalComputer': not preflight_bypassed,
    },
    'restartSemantics': 'all listed services stopped first, then restarted in dependency-ordered Compose stages with explicit health and one-shot configuration gates',
    'modeIsolation': {
        'sequential': len(modes) <= 1 or len(transitions) == len(modes) - 1,
        'transitions': transitions,
        'cleanupBetweenModes': len(modes) <= 1 or len(transitions) == len(modes) - 1,
    },
    'startupStages': {
        'single': ['mysql+redis', 'single', 'frontend-single'],
        'cloud': ['mysql+redis+rabbitmq+nacos', 'rabbitmq-config+nacos-config', 'monitor+gateway+upms+generator+quartz+workflow', 'auth', 'frontend-cloud'],
    },
    'scope': 'isolated project and volumes only',
    'modes': mode_records,
    'limitations': [
        'Acceptance proves the shared CRUD/workflow path after restart; it is not a load, HA, or third-party delivery test.',
        'The rehearsal reuses prebuilt application images and does not prove a clean image build.',
    ],
}
(run_dir / 'evidence.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n')
print(run_dir / 'evidence.json')
PY
}

overall_status=2
cleanup_done=false

cleanup_project() {
  if [[ "${cleanup_done}" == true ]]; then
    return
  fi
  cleanup_done=true
  set +e
  compose --profile cloud --profile workflow --profile single down --volumes --remove-orphans \
    > "${RUN_DIR}/cleanup.log" 2>&1
  rm -f "${ENV_FILE}"
  set -e
}

on_exit() {
  local status=$?
  set +e
  if [[ ! -s "${RUN_DIR}/evidence.json" ]]; then
    write_evidence failed >/dev/null 2>&1 || true
  fi
  cleanup_project
  exit "${status}"
}

trap on_exit EXIT

require_runtime
configure_ports

overall_status=0
IFS=',' read -r -a modes <<< "${REQUESTED_MODES}"
for ((index = 0; index < ${#modes[@]}; index++)); do
  mode="${modes[index]}"
  if ! restart_mode "${mode}"; then
    overall_status=1
    break
  fi
  if (( index + 1 < ${#modes[@]} )); then
    next_mode="${modes[index + 1]}"
    if ! reset_project_between_modes "${mode}" "${next_mode}"; then
      overall_status=1
      break
    fi
    if ! run_local_test_preflight "${next_mode}"; then
      overall_status=1
      break
    fi
  fi
done

write_evidence "$(if (( overall_status == 0 )); then printf passed; else printf failed; fi)" \
  "${PREFLIGHT_BYPASSED}" "${PREFLIGHT_BYPASS_VARIABLE}"

if (( overall_status != 0 )); then
  echo "Full application restart rehearsal failed; evidence: ${RUN_DIR}/evidence.json" >&2
  exit 1
fi
echo "Full application restart rehearsal passed; evidence: ${RUN_DIR}/evidence.json"
