#!/usr/bin/env bash
set -euo pipefail
set +x

# Read-only guard for local verification. It never creates, stops, or removes
# containers; runtime scripts remain responsible for their own scoped cleanup.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-single}"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"
MIN_FREE_GIB="${BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB:-10}"
MIN_DOCKER_GIB="${BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB:-}"
MAX_RECLAIMABLE_GIB="${BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB:-20}"
DOCKER_TIMEOUT_SECONDS="${BIXI_LOCAL_PREFLIGHT_DOCKER_TIMEOUT_SECONDS:-5}"
FAIL_ON_CONFLICT="${BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT:-false}"
FAIL_ON_STORAGE_PRESSURE="${BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE:-false}"

case "${MODE}" in
  static|single|cloud|cluster) ;;
  *)
    echo "Usage: $0 [static|single|cloud|cluster]" >&2
    exit 2
    ;;
esac

is_positive_integer() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
  esac
  (( "$1" > 0 ))
}

if ! is_positive_integer "${MIN_FREE_GIB}"; then
  echo "BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB must be a positive integer." >&2
  exit 2
fi
if [[ -n "${MIN_DOCKER_GIB}" ]] && ! is_positive_integer "${MIN_DOCKER_GIB}"; then
  echo "BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB must be a positive integer when set." >&2
  exit 2
fi
if ! is_positive_integer "${MAX_RECLAIMABLE_GIB}"; then
  echo "BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB must be a positive integer." >&2
  exit 2
fi
if ! is_positive_integer "${DOCKER_TIMEOUT_SECONDS}"; then
  echo "BIXI_LOCAL_PREFLIGHT_DOCKER_TIMEOUT_SECONDS must be a positive integer." >&2
  exit 2
fi

exit_status=0
blocked() {
  printf '[local-preflight] BLOCKED: %s\n' "$*" >&2
  (( exit_status < 1 )) && exit_status=1
}

environment_blocked() {
  printf '[local-preflight] BLOCKED (environment): %s\n' "$*" >&2
  exit_status=2
}

warn() {
  printf '[local-preflight] WARN: %s\n' "$*" >&2
}

ok() {
  printf '[local-preflight] OK: %s\n' "$*"
}

env_value() {
  local name="$1"
  local value=''
  if [[ -n "${!name+x}" ]]; then
    printf '%s' "${!name}"
    return
  fi
  if [[ -r "${ENV_FILE}" ]]; then
    value="$(awk -v key="${name}" '
      index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }
    ' "${ENV_FILE}")"
    value="${value#\"}"
    value="${value%\"}"
    value="${value#\'}"
    value="${value%\'}"
  fi
  printf '%s' "${value}"
}

is_true() {
  case "$(env_value "$1" | tr '[:upper:]' '[:lower:]')" in
    true|1|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

conflict() {
  if is_true BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT; then
    blocked "$*"
  else
    warn "$*"
  fi
}

check_host_disk() {
  local available_kib
  available_kib="$(df -Pk "${ROOT}" 2>/dev/null | awk 'NR == 2 { print $4; exit }')" || true
  case "${available_kib}" in
    ''|*[!0-9]*)
      environment_blocked "unable to read free space for ${ROOT}"
      return 1
      ;;
  esac
  local required_kib=$((MIN_FREE_GIB * 1024 * 1024))
  if (( available_kib < required_kib )); then
    blocked "${ROOT} has ${available_kib} KiB free; at least ${MIN_FREE_GIB} GiB is required before Docker tests"
    return 1
  fi
  local available_gib=$((available_kib / 1024 / 1024))
  ok "host free space ${available_gib} GiB (minimum ${MIN_FREE_GIB} GiB)"
}

required_docker_gib() {
  case "${MODE}" in
    single) printf '4' ;;
    cloud) printf '6' ;;
    cluster) printf '8' ;;
    static) printf '0' ;;
  esac
}

docker_probe() {
  local output
  if ! command -v docker >/dev/null 2>&1; then
    environment_blocked 'Docker CLI is unavailable'
    return 1
  fi
  if ! command -v python3 >/dev/null 2>&1; then
    environment_blocked 'python3 is required for the bounded Docker probe'
    return 1
  fi
  if ! output="$(python3 - "${DOCKER_TIMEOUT_SECONDS}" <<'PY'
import subprocess
import sys
import json
import re

timeout = int(sys.argv[1])

def run(arguments):
    try:
        result = subprocess.run(arguments, check=False, capture_output=True,
                                text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise SystemExit(1)
    if result.returncode != 0:
        raise SystemExit(1)
    return result.stdout.strip()

def optional_run(arguments):
    try:
        result = subprocess.run(arguments, check=False, capture_output=True,
                                text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return ''
    if result.returncode != 0:
        return ''
    return result.stdout.strip()

def size_bytes(value):
    match = re.search(r'([0-9]+(?:\.[0-9]+)?)\s*([KMGTPE]?i?B)', value or '', re.I)
    if not match:
        return 0
    number = float(match.group(1))
    unit = match.group(2).upper()
    factors = {
        'B': 1,
        'KB': 1000,
        'MB': 1000 ** 2,
        'GB': 1000 ** 3,
        'TB': 1000 ** 4,
        'PB': 1000 ** 5,
        'KIB': 1024,
        'MIB': 1024 ** 2,
        'GIB': 1024 ** 3,
        'TIB': 1024 ** 4,
        'PIB': 1024 ** 5,
    }
    return int(number * factors.get(unit, 0))

memory = run(['docker', 'info', '--format', '{{.MemTotal}}'])
if not memory.isdigit() or int(memory) <= 0:
    raise SystemExit(1)
compose_version = run(['docker', 'compose', 'version'])
# Include stopped containers. Disposable harnesses use names such as
# bixi-* without Compose labels, while Compose dependencies can retain a
# generic name (mysql/redis/rabbitmq); the project label keeps those generic
# names scoped to Bixi. This remains read-only and avoids false positives for
# unrelated projects that happen to run the same dependency images.
containers = run([
    'docker', 'ps', '--all', '--format',
    '{{.Names}}\\t{{.Image}}\\t{{.Label "com.docker.compose.project"}}\\t{{.Label "com.docker.compose.service"}}\\t{{.State}}',
])
storage = optional_run(['docker', 'system', 'df', '--format', '{{json .}}'])
print('MEMORY=' + memory)
print('COMPOSE=' + compose_version.splitlines()[0])
for line in containers.splitlines():
    if not line.strip():
        continue
    fields = line.split('\\t')
    # A small compatibility path helps local fake Docker CLIs and older
    # wrappers that returned project|service while the full query was added.
    if len(fields) >= 5:
        name, image, project, service, state = (field.strip() for field in fields[:5])
    elif '|' in line:
        project, service = (field.strip() for field in line.split('|', 1))
        name, image, state = '', '', 'running'
    else:
        continue
    normalized_name = name.lstrip('/').lower()
    normalized_project = project.lower()
    normalized_service = service.lower()
    normalized_image = image.lower()
    normalized_state = state.lower()
    bixi_name = normalized_name == 'bixi' or normalized_name.startswith(('bixi-', 'bixi_'))
    bixi_project = normalized_project == 'bixi' or normalized_project.startswith('bixi-')
    bixi_container = bixi_name or bixi_project
    if bixi_container:
        print('BIXI_CONTAINER=' + '|'.join([name or '(unnamed)', image or '(unknown)',
                                             project or '(no-compose-project)',
                                             service or '(no-compose-service)',
                                             state or '(unknown)']))
    # SERVICE is intentionally emitted only for active containers. Stopped
    # Bixi resources are reported via BIXI_CONTAINER but do not look like a
    # currently running cloud/single deployment.
    if project and service and (not state or normalized_state in {
        'running', 'created', 'restarting', 'paused'
    }):
        print('SERVICE=' + project + '|' + service)
for line in storage.splitlines():
    try:
        row = json.loads(line)
    except (TypeError, ValueError):
        continue
    kind = str(row.get('Type', '')).strip()
    if not kind:
        continue
    size = str(row.get('Size', '')).strip()
    reclaimable = str(row.get('Reclaimable', '')).strip()
    print('STORAGE=' + '|'.join([
        kind,
        str(size_bytes(size)),
        str(size_bytes(reclaimable)),
        size,
        reclaimable,
    ]))
PY
  )"; then
    environment_blocked "Docker daemon did not answer within ${DOCKER_TIMEOUT_SECONDS} seconds"
    return 1
  fi

  local memory_bytes="$(printf '%s\n' "${output}" | sed -n 's/^MEMORY=//p' | head -n 1)"
  local compose_version="$(printf '%s\n' "${output}" | sed -n 's/^COMPOSE=//p' | head -n 1)"
  if [[ ! "${memory_bytes}" =~ ^[0-9]+$ ]] || (( memory_bytes <= 0 )); then
    environment_blocked 'Docker reported no usable memory limit'
    return 1
  fi
  local required_gib
  required_gib="$(required_docker_gib)"
  if is_true AI_ENABLED; then
    required_gib=$((required_gib + 1))
    warn 'AI_ENABLED=true adds a memory-heavy service; keep this run single-mode and sequential'
  fi
  if is_true WORKFLOW_CLUSTER_ENABLED; then
    if [[ "${MODE}" != 'cluster' ]]; then
      warn 'WORKFLOW_CLUSTER_ENABLED=true requests two Workflow owners; use mode cluster on a dedicated machine'
    fi
    (( required_gib < 8 )) && required_gib=8
  fi
  if [[ -n "${MIN_DOCKER_GIB}" ]]; then
    required_gib="${MIN_DOCKER_GIB}"
    warn "using explicit Docker memory threshold override: ${MIN_DOCKER_GIB} GiB"
  fi
  local required_bytes=$((required_gib * 1024 * 1024 * 1024))
  local memory_gib_tenths=$((memory_bytes * 10 / 1024 / 1024 / 1024))
  local memory_gib="$((memory_gib_tenths / 10)).$((memory_gib_tenths % 10))"
  if (( memory_bytes < required_bytes )); then
    blocked "Docker VM memory is ${memory_gib} GiB; mode ${MODE} needs at least ${required_gib} GiB (set BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB only to document a deliberate override)"
  else
    ok "Docker VM memory ${memory_gib} GiB (minimum ${required_gib} GiB for ${MODE})"
  fi
  [[ -n "${compose_version}" ]] && ok "Docker Compose is available (${compose_version})"

  local storage_reclaimable_bytes=0
  local storage_summary=''
  local storage_line storage_type storage_size_bytes storage_reclaimable storage_size_raw storage_reclaimable_raw
  local storage_report=false
  while IFS= read -r storage_line; do
    [[ "${storage_line}" == STORAGE=* ]] || continue
    storage_line="${storage_line#STORAGE=}"
    IFS='|' read -r storage_type storage_size_bytes storage_reclaimable storage_size_raw storage_reclaimable_raw <<< "${storage_line}"
    [[ "${storage_reclaimable}" =~ ^[0-9]+$ ]] || continue
    storage_report=true
    storage_reclaimable_bytes=$((storage_reclaimable_bytes + storage_reclaimable))
    storage_summary="${storage_summary}${storage_type}: ${storage_size_raw:-unknown} total, ${storage_reclaimable_raw:-unknown} reclaimable; "
  done < <(printf '%s\n' "${output}")
  if [[ "${storage_report}" == true ]]; then
    local max_reclaimable_bytes=$((MAX_RECLAIMABLE_GIB * 1024 * 1024 * 1024))
    local reclaimable_gib_tenths=$((storage_reclaimable_bytes * 10 / 1024 / 1024 / 1024))
    local reclaimable_gib="$((reclaimable_gib_tenths / 10)).$((reclaimable_gib_tenths % 10))"
    if (( storage_reclaimable_bytes >= max_reclaimable_bytes )); then
      if is_true BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE; then
        blocked "Docker storage reclaimable ${reclaimable_gib} GiB exceeds ${MAX_RECLAIMABLE_GIB} GiB threshold (${storage_summary%'; '}); clean up Docker storage before adding tests"
      else
        warn "Docker storage reclaimable ${reclaimable_gib} GiB exceeds ${MAX_RECLAIMABLE_GIB} GiB threshold (${storage_summary%'; '}); inspect docker system df before adding tests"
      fi
    else
      ok "Docker storage reclaimable ${reclaimable_gib} GiB (${storage_summary%'; '})"
    fi
  else
    warn 'Docker storage report is unavailable; host disk and Docker VM gates still apply'
  fi

  local running_single=false
  local running_cloud=false
  local bixi_container_count=0
  local bixi_container_summary=''
  local service_line project_name service_name container_line container_summary
  while IFS= read -r service_line; do
    [[ "${service_line}" == SERVICE=* ]] || continue
    service_line="${service_line#SERVICE=}"
    project_name="${service_line%%|*}"
    case "${project_name}" in
      bixi|bixi-*) ;;
      *) continue ;;
    esac
    service_name="${service_line#*|}"
    case "${service_name}" in
      single|frontend-single) running_single=true ;;
      monitor|gateway|upms|auth|generator|quartz|ai|frontend-cloud|workflow|workflow-a|workflow-b|nacos|nacos-config)
        running_cloud=true
        ;;
    esac
  done < <(printf '%s\n' "${output}")

  while IFS= read -r container_line; do
    [[ "${container_line}" == BIXI_CONTAINER=* ]] || continue
    container_summary="${container_line#BIXI_CONTAINER=}"
    ((bixi_container_count += 1))
    if (( bixi_container_count <= 5 )); then
      bixi_container_summary="${bixi_container_summary}${container_summary}; "
    fi
  done < <(printf '%s\n' "${output}")

  if is_true BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT && [[ "${running_single}" == true || "${running_cloud}" == true ]]; then
    conflict 'Bixi containers are already running; resource-creating local tests require an exclusive deployment mode'
  elif [[ "${running_single}" == true && "${running_cloud}" == true ]]; then
    conflict 'both single and cloud containers are running; stop one mode before adding more tests'
  elif [[ "${MODE}" == 'single' && "${running_cloud}" == true ]]; then
    conflict 'cloud containers are already running; single and cloud should not run concurrently on a personal Docker VM'
  elif [[ "${MODE}" != 'single' && "${running_single}" == true ]]; then
    conflict 'single containers are already running; single and cloud should not run concurrently on a personal Docker VM'
  elif (( bixi_container_count > 0 )); then
    local suffix=''
    if (( bixi_container_count > 5 )); then
      suffix=" (showing first 5 of ${bixi_container_count})"
    fi
    conflict "Bixi containers remain, including stopped or disposable resources${suffix}: ${bixi_container_summary%'; '}; remove them before creating more local test resources"
  else
    ok 'no conflicting Bixi deployment mode was detected'
  fi
  return 0
}

disk_ok=true
if ! check_host_disk; then
  disk_ok=false
fi
if [[ "${MODE}" == 'static' ]]; then
  ok 'static mode does not contact Docker or start a Compose project'
elif [[ "${disk_ok}" != true ]]; then
  warn 'skipping Docker probe because the host disk gate failed'
else
  docker_probe || true
fi

if (( exit_status == 0 )); then
  ok "local test preflight passed for ${MODE}"
else
  printf '[local-preflight] See the messages above before starting tests (mode=%s).\n' "${MODE}" >&2
fi
exit "${exit_status}"
