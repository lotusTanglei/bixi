#!/bin/bash
set -euo pipefail

fail() { echo "bixi database init: $*" >&2; exit 1; }
for key in MYSQL_HOST MYSQL_PORT MYSQL_DATABASE MYSQL_ROOT_USERNAME MYSQL_ROOT_PASSWORD \
    ADMIN_PASSWORD_BCRYPT_B64 OAUTH_INTERNAL_SEED OAUTH_PASSWORD_CLIENT_SECRET \
    OAUTH_MOBILE_CLIENT_SECRET OAUTH_BROWSER_CLIENT_SECRET; do
    [[ -n "${!key:-}" && "${!key}" != CHANGE_ME && "${!key}" != GENERATE_ON_FIRST_RUN ]] \
        || fail "$key is required"
done
[[ "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]] || fail 'MYSQL_DATABASE contains invalid characters'
[[ "$OAUTH_BROWSER_CLIENT_SECRET" =~ ^[0-9a-fA-F]{48}$ ]] \
    || fail 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters'
sql_dir="${BIXI_SQL_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/../sql" && pwd)}"
for number in 01 02 03 04; do
    files=("$sql_dir"/"$number"_*.sql)
    [[ -f "${files[0]}" ]] || fail "missing $number SQL file in $sql_dir"
done
admin_hash="$(printf '%s' "$ADMIN_PASSWORD_BCRYPT_B64" | base64 -d)" \
    || fail 'ADMIN_PASSWORD_BCRYPT_B64 is invalid base64'
[[ "$admin_hash" =~ ^\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{53}$ ]] \
    || fail 'ADMIN_PASSWORD_BCRYPT_B64 is not a bcrypt hash'
mysql_tls=(--protocol=TCP)
case "$MYSQL_HOST" in
    localhost|127.0.0.1) ;;
    *)
        [[ -r "${MYSQL_SSL_CA:-}" ]] || fail 'remote MySQL requires MYSQL_SSL_CA (readable CA certificate)'
        mysql_tls+=(--ssl-mode=VERIFY_IDENTITY "--ssl-ca=$MYSQL_SSL_CA")
        ;;
esac

db() {
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --skip-column-names \
        "${mysql_tls[@]}" --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
        --user="$MYSQL_ROOT_USERNAME" "$@"
}
schema_count="$(db -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${MYSQL_DATABASE}'")"
[[ "$schema_count" == 1 ]] || fail "target database $MYSQL_DATABASE must already exist"
object_count="$(db -e "SELECT
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}') +
    (SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema='${MYSQL_DATABASE}') +
    (SELECT COUNT(*) FROM information_schema.events WHERE event_schema='${MYSQL_DATABASE}') +
    (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='${MYSQL_DATABASE}')")"
[[ "$object_count" == 0 ]] || fail "target database $MYSQL_DATABASE is not empty; no SQL was applied"

for number in 01 02 03 04; do
    for file in "$sql_dir"/"$number"_*.sql; do
        db "$MYSQL_DATABASE" < "$file"
        echo "Imported $(basename "$file")"
    done
done

hex() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n'; }
admin_hex="$(hex "$admin_hash")"
seed_hex="$(hex "$OAUTH_INTERNAL_SEED")"
password_hex="$(hex "$OAUTH_PASSWORD_CLIENT_SECRET")"
mobile_hex="$(hex "$OAUTH_MOBILE_CLIENT_SECRET")"
browser_hex="$(hex "$OAUTH_BROWSER_CLIENT_SECRET")"
db "$MYSQL_DATABASE" <<SQL
UPDATE sys_user SET password = CONVERT(UNHEX('$admin_hex') USING utf8mb4) WHERE username = 'admin';
UPDATE sys_oauth_client_details SET client_secret = SHA2(CONCAT(CONVERT(UNHEX('$seed_hex') USING utf8mb4), ':', client_id), 256);
UPDATE sys_oauth_client_details SET client_secret = CONVERT(UNHEX('$password_hex') USING utf8mb4) WHERE client_id = 'bixi';
UPDATE sys_oauth_client_details SET client_secret = CONVERT(UNHEX('$mobile_hex') USING utf8mb4) WHERE client_id = 'app';
UPDATE sys_oauth_client_details SET client_secret = CONVERT(UNHEX('$browser_hex') USING utf8mb4) WHERE client_id = 'bixi-browser';
SQL
echo 'Fresh database initialized'
