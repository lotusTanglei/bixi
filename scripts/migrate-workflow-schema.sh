#!/usr/bin/env bash
set -euo pipefail
set +x

cd "$(dirname "$0")/.."
ROOT="$PWD"
ENV_FILE="${BIXI_ENV_FILE:-${ROOT}/.env}"

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
if [[ "${WORKFLOW_MAINTENANCE:-false}" != "true" ]]; then
  echo 'Set WORKFLOW_MAINTENANCE=true after stopping Workflow traffic and consumers.' >&2
  exit 1
fi
if [[ "${WORKFLOW_MIGRATION_CONFIRM:-}" != 'APPLY_WORKFLOW_MIGRATIONS' ]]; then
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

compose --profile cloud --profile workflow --profile workflow-cluster config --quiet
running="$(compose --profile cloud --profile workflow --profile workflow-cluster ps -q \
  workflow workflow-a workflow-b 2>/dev/null || true)"
if [[ -n "$running" ]]; then
  echo 'Stop workflow/workflow-a/workflow-b before applying a schema migration.' >&2
  printf '%s\n' "$running" >&2
  exit 1
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

if [[ "$#" -gt 0 ]]; then
  migration_files=("$@")
else
  migration_files=()
  while IFS= read -r file; do
    migration_files+=("$file")
  done < <(find bixi-project-documents/sql/migrations -maxdepth 1 -type f \
    \( -name '*workflow*.sql' -o -name '*flowable*.sql' -o -name '*reliable*.sql' -o -name '*demo_leave*.sql' -o -name '*idempotency.sql' \) \
    -print | sort)
fi
if [[ "${#migration_files[@]}" -eq 0 ]]; then
  echo 'No workflow migration files were selected.' >&2
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
  echo "Applying controlled Workflow migration: $id"
  mysql_exec < "$file"
  printf "INSERT INTO bixi_schema_migration(migration_id) VALUES ('%s');\n" "$id" | mysql_exec
done

echo 'Controlled Workflow schema migration passed.'
