#!/bin/sh
set -eu

case "${OAUTH_BROWSER_CLIENT_SECRET:-}" in
    *[!0-9a-fA-F]*|'') echo 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters' >&2; exit 1 ;;
esac
[ "${#OAUTH_BROWSER_CLIENT_SECRET}" -eq 48 ] \
    || { echo 'OAUTH_BROWSER_CLIENT_SECRET must be 48 hexadecimal characters' >&2; exit 1; }

admin_hash="$(printf '%s' "${ADMIN_PASSWORD_BCRYPT_B64}" | base64 -d)"
hex() { printf '%s' "$1" | od -An -tx1 | tr -d ' \n'; }
admin_hex="$(hex "${admin_hash}")"
seed_hex="$(hex "${OAUTH_INTERNAL_SEED}")"
password_hex="$(hex "${OAUTH_PASSWORD_CLIENT_SECRET}")"
mobile_hex="$(hex "${OAUTH_MOBILE_CLIENT_SECRET}")"
browser_hex="$(hex "${OAUTH_BROWSER_CLIENT_SECRET}")"

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --protocol=socket -uroot "${MYSQL_DATABASE}" <<SQL
UPDATE sys_user
SET password = CONVERT(UNHEX('${admin_hex}') USING utf8mb4)
WHERE username = 'admin';

UPDATE sys_oauth_client_details
SET client_secret = SHA2(CONCAT(CONVERT(UNHEX('${seed_hex}') USING utf8mb4), ':', client_id), 256);

UPDATE sys_oauth_client_details
SET client_secret = CONVERT(UNHEX('${password_hex}') USING utf8mb4)
WHERE client_id = 'bixi';

UPDATE sys_oauth_client_details
SET client_secret = CONVERT(UNHEX('${mobile_hex}') USING utf8mb4)
WHERE client_id = 'app';

UPDATE sys_oauth_client_details
SET client_secret = CONVERT(UNHEX('${browser_hex}') USING utf8mb4)
WHERE client_id = 'bixi-browser';

SQL

touch /var/lib/mysql/.bixi-init-complete
