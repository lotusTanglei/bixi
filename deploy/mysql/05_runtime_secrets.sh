#!/bin/sh
set -eu

admin_hash="$(printf '%s' "${ADMIN_PASSWORD_BCRYPT_B64}" | base64 -d)"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" <<SQL
UPDATE sys_user
SET password = '${admin_hash}'
WHERE username = 'admin';

UPDATE sys_oauth_client_details
SET client_secret = SHA2(CONCAT('${OAUTH_INTERNAL_SEED}', ':', client_id), 256);

UPDATE sys_oauth_client_details
SET client_secret = '${OAUTH_PASSWORD_CLIENT_SECRET}'
WHERE client_id = 'bixi';

UPDATE sys_oauth_client_details
SET client_secret = '${OAUTH_MOBILE_CLIENT_SECRET}'
WHERE client_id = 'app';

UPDATE sys_public_param
SET value = '${TENANT_DEFAULT_PASSWORD}'
WHERE \`key\` = 'TENANT_DEFAULT_PASSWORD';

SQL
