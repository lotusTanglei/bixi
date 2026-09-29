#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
ROOT="$PWD"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"

migration_scope="${BIXI_MIGRATION_SCOPE:-workflow}"
list_only=false
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --list)
      list_only=true
      shift
      ;;
    --scope)
      [[ "$#" -ge 2 ]] || { echo '--scope requires a value' >&2; exit 1; }
      migration_scope="$2"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

case "$migration_scope" in
  workflow|phase2) ;;
  *)
    echo "Unsupported migration scope: $migration_scope (expected workflow or phase2)." >&2
    exit 1
    ;;
esac

select_migration_files() {
  local scope="$1"
  if [[ "$#" -gt 1 ]]; then
    shift
    printf '%s\n' "$@"
    return
  fi
  if [[ "$scope" == phase2 ]]; then
    find bixi-project-documents/sql/migrations -maxdepth 1 -type f -name '*.sql' -print | sort
    return
  fi
  find bixi-project-documents/sql/migrations -maxdepth 1 -type f \
    \( -name '*workflow*.sql' -o -name '*flowable*.sql' -o -name '*reliable*.sql' -o -name '*demo_leave*.sql' -o -name '*idempotency.sql' \) \
    -print | sort
}

if [[ "$list_only" == true ]]; then
  select_migration_files "$migration_scope" "$@"
  exit 0
fi

if [[ ! -s "$ENV_FILE" ]]; then
  echo "Missing environment file: $ENV_FILE" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ "${WORKFLOW_SCHEMA_UPDATE:-false}" == "true" ]]; then
  echo 'WORKFLOW_SCHEMA_UPDATE must remain false during controlled migration.' >&2
  exit 1
fi
if [[ "$migration_scope" == phase2 ]]; then
  if [[ "${BIXI_SCHEMA_MAINTENANCE:-false}" != true ]]; then
    echo 'Set BIXI_SCHEMA_MAINTENANCE=true after stopping all application traffic and consumers.' >&2
    exit 1
  fi
  if [[ "${BIXI_MIGRATION_CONFIRM:-}" != 'APPLY_PHASE2_MIGRATIONS' ]]; then
    echo 'Set BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS for this one migration window.' >&2
    exit 1
  fi
elif [[ "${WORKFLOW_MAINTENANCE:-false}" != "true" ]]; then
  echo 'Set WORKFLOW_MAINTENANCE=true after stopping Workflow traffic and consumers.' >&2
  exit 1
elif [[ "${WORKFLOW_MIGRATION_CONFIRM:-}" != 'APPLY_WORKFLOW_MIGRATIONS' ]]; then
  echo 'Set WORKFLOW_MIGRATION_CONFIRM=APPLY_WORKFLOW_MIGRATIONS for this one migration window.' >&2
  exit 1
fi

compose() {
  if [[ "${WORKFLOW_CLUSTER_ENABLED:-false}" == 'true' ]]; then
    docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.workflow-cluster.yaml "$@"
  else
    docker compose --env-file "$ENV_FILE" -f compose.yaml "$@"
  fi
}

compose_with_workflow_cluster() {
  docker compose --env-file "$ENV_FILE" -f compose.yaml -f compose.workflow-cluster.yaml "$@"
}

if [[ "$migration_scope" == phase2 ]]; then
  related_services=(gateway auth upms generator quartz ai workflow workflow-a workflow-b single)
  compose_with_workflow_cluster --profile cloud --profile ai --profile workflow \
    --profile workflow-cluster --profile single config --quiet
  running="$(compose_with_workflow_cluster --profile cloud --profile ai --profile workflow \
    --profile workflow-cluster --profile single ps -q "${related_services[@]}" 2>/dev/null || true)"
  if [[ -n "$running" ]]; then
    echo 'Stop gateway/auth/upms/generator/quartz/ai/workflow/workflow-a/workflow-b/single before applying a schema migration.' >&2
    printf '%s\n' "$running" >&2
    exit 1
  fi
else
  compose_with_workflow_cluster --profile cloud --profile workflow --profile workflow-cluster config --quiet
  running="$(compose_with_workflow_cluster --profile cloud --profile workflow --profile workflow-cluster \
    ps -q workflow workflow-a workflow-b 2>/dev/null || true)"
  if [[ -n "$running" ]]; then
    echo 'Stop workflow/workflow-a/workflow-b before applying a schema migration.' >&2
    printf '%s\n' "$running" >&2
    exit 1
  fi
fi
if ! compose ps -q mysql | grep -q .; then
  echo 'MySQL must already be running and healthy for a controlled migration.' >&2
  exit 1
fi

mysql_exec() {
  compose exec -T mysql sh -c \
    'exec mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" "$@"' sh "$@"
}

mysql_exec <<'SQL'
CREATE TABLE IF NOT EXISTS bixi_schema_migration (
    migration_id VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    applied_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (migration_id)
) ENGINE=InnoDB DEFAULT CHARSET=ascii COLLATE=ascii_bin;
SQL

 migration_files=()
while IFS= read -r file; do
  migration_files+=("$file")
done < <(select_migration_files "$migration_scope" "$@")
if [[ "${#migration_files[@]}" -eq 0 ]]; then
  echo "No $migration_scope migration files were selected." >&2
  exit 1
fi

for file in "${migration_files[@]}"; do
  case "$file" in
    bixi-project-documents/sql/migrations/*.sql|sql/migrations/*.sql) ;;
    *) echo "Migration must be under bixi-project-documents/sql/migrations: $file" >&2; exit 1 ;;
  esac
  [[ -f "$file" ]] || { echo "Missing migration: $file" >&2; exit 1; }
  id="$(basename "$file")"
  applied="$(mysql_exec --batch --skip-column-names -e \
    "SELECT COUNT(*) FROM bixi_schema_migration WHERE migration_id='${id}'")"
  if [[ "$applied" == '1' ]]; then
    echo "Skipping already applied migration: $id"
    continue
  fi
  echo "Applying controlled ${migration_scope} migration: $id"
  mysql_exec < "$file"
  printf "INSERT INTO bixi_schema_migration(migration_id) VALUES ('%s');\n" "$id" | mysql_exec
done

echo "Controlled ${migration_scope} schema migration passed."
