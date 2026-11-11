# Bixi native release (Ubuntu)

This bundle targets Ubuntu 22.04/24.04 LTS x86_64 with Java 17, systemd and
Nginx. Both `cloud` and `single` run on one host without Docker. It is an
installation recipe, not evidence that either Ubuntu release has passed the
release gate. A native install must use the exact candidate package whose
checksums and commit are recorded in `BUILD_INFO` and `SHA256SUMS`.

## Prerequisites

- Install `openjdk-17-jre-headless`, `nginx`, `mysql-client`, `curl`, `jq`, and
  `apache2-utils`; install a trusted certificate for the public hostname.
- Provide a private MySQL 8.0.16+ database and Redis. Cloud also needs an
  authenticated Nacos 2.4.3 namespace with ID `bixi-prod` and RabbitMQ.
  Nacos must be reachable on loopback; use a verified TLS tunnel when it runs
  remotely, because its publisher sends credentials over HTTP to the
  configured host. The reference Compose versions are MySQL 8.4.3, Redis
  7.4.2, RabbitMQ 4.0.5. Record the versions actually used; other versions
  are not automatically certified.
- Keep MySQL, Redis, Nacos, RabbitMQ and application ports off public
  interfaces. The systemd unit binds every Bixi JAR to `127.0.0.1`, and cloud
  services register that address in Nacos. Only Nginx should accept public
  traffic. For a remote MySQL host, set `MYSQL_JDBC_URL` to the same configured
  host, port and database with `sslMode=VERIFY_IDENTITY`, trust its CA in Java,
  and set `MYSQL_SSL_CA` to a readable CA certificate for SQL initialization.
- Create a new empty database. `01_schema.sql` contains `DROP TABLE`; never
  apply it to an existing database. Back up MySQL and `/var/lib/bixi/files`
  together after real use begins.

## Build immutable packages

Build on a clean checkout of the frozen candidate commit, with Java 17,
Node 22.18+, `unzip` and installed frontend dependencies. Run the existing CI
gates first. The package command refuses a dirty checkout or a reused output
directory, then rebuilds the JARs and matching frontend from that checkout.
Package each mode separately:

```sh
(cd bixi-ui && npm ci)
sh deploy/native/package.sh cloud /tmp/bixi-v1.0.0-cloud

sh deploy/native/package.sh single /tmp/bixi-v1.0.0-single
```

The output contains JARs, the matching frontend build, the four base SQL files,
four pinned Flowable 7.1.0 SQL files under `sql/migrations`, native operating
files, commit/mode metadata and SHA-256 checksums. Transfer each directory
unchanged. On Ubuntu, verify before staging:

```sh
cd /path/to/bixi-v1.0.0-single   # or cloud
sha256sum -c SHA256SUMS
cat BUILD_INFO
```

## Stage the chosen mode

Use a distinct release directory for every package, such as
`/opt/bixi/releases/v1.0.0-single`. The commands below use `PKG` for the
verified package and `REL` for its destination. Do not place secrets inside a
release directory.

```sh
PKG=/path/to/bixi-v1.0.0-single
REL=/opt/bixi/releases/v1.0.0-single
id -u bixi >/dev/null 2>&1 || sudo useradd --system --home-dir /var/lib/bixi --shell /usr/sbin/nologin bixi
sudo install -d -m 0755 /opt/bixi/releases /usr/local/libexec/bixi
sudo install -d -m 0700 /etc/bixi /etc/bixi/tls
sudo install -d -o bixi -g bixi -m 0750 /var/lib/bixi /var/lib/bixi/files /var/lib/bixi/generator-output /var/lib/bixi/logs
sudo cp -a "$PKG" "$REL"
sudo chown -R root:root "$REL"
sudo chmod -R a+rX,go-w "$REL"
sudo install -m 0755 "$REL/ops/preflight.sh" /usr/local/libexec/bixi/preflight.sh
sudo install -m 0644 "$REL/ops/bixi@.service" /etc/systemd/system/bixi@.service
sudo test -e /etc/bixi/bixi.env || sudo install -m 0600 "$REL/ops/bixi.env.example" /etc/bixi/bixi.env
sudo systemctl daemon-reload
```

The protected environment file is created only on first installation; a
same-version redeployment preserves its credentials. Edit
`/etc/bixi/bixi.env` with `sudoedit`: select `BIXI_DEPLOYMENT_MODE`, replace
every `CHANGE_ME`, and set real connection addresses. The same 48-hex
`OAUTH_BROWSER_CLIENT_SECRET` must be written to this file and the fresh
database. Generate unique values with `openssl rand -hex 24` or stronger;
never put them in the frontend build, Nacos YAML or a release package. Keep
the environment file root-owned and mode `0600`. Store administrator and
database credentials in the operator's secret store.

Cloud installs the supplied per-service environment files so Monitor can
reach loopback actuator URLs. Only the restricted Nacos runtime credentials
and RabbitMQ credentials belong in the root-owned main environment file.
Never put `NACOS_PUBLISH_USERNAME` or `NACOS_PUBLISH_PASSWORD` in that file
or any systemd service environment. Optional overrides go in
`/etc/bixi/<service>.env`, also mode `0600`.

```sh
# cloud only
for file in "$REL"/ops/service-env/*.env; do
  target="/etc/bixi/$(basename "$file")"
  sudo test -e "$target" || sudo install -m 0600 "$file" "$target"
done
```

Configure TLS paths `/etc/bixi/tls/fullchain.pem` and
`/etc/bixi/tls/privkey.pem` as files or symlinks to your certificate manager.
Nginx must be able to read the certificate key as root. Replace the default
Nginx site and install this mode's proxy configuration:

```sh
PUBLIC_HOST=bixi.example.com   # replace with the hostname on the TLS certificate
sudo install -m 0644 "$REL/ops/nginx.conf" /etc/nginx/sites-available/bixi
sudo sed -i "s/BIXI_PUBLIC_HOST/$PUBLIC_HOST/g" /etc/nginx/sites-available/bixi
sudo ln -sfn /etc/nginx/sites-available/bixi /etc/nginx/sites-enabled/bixi
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
```

The Nginx config serves HTTPS on 443, redirects HTTP 80 to HTTPS, proxies
`/api` to loopback port 9999, and preserves the mode-specific auth redirects.
Use a DNS hostname containing only letters, digits, dots and hyphens for
`PUBLIC_HOST`; the certificate must match it. Check `/healthz` through that
hostname after start. On a shared Nginx host, review other enabled sites
before disabling the default site.

## Fresh database only

Create the empty database with `utf8mb4` first and grant the application user
access. Run `init-db.sh` before any service is started. It verifies the
database exists and has zero tables **before** reading any initialization
SQL. A failed partial import leaves a nonempty database and will be rejected
on retry; only a disposable new database may be dropped and recreated.

In a root shell, source the protected runtime environment and supply the
one-time database administrator credentials and seed values. The bcrypt hash
command prompts for the administrator password without putting it in process
arguments. Keep the chosen password in your secret store. Generate and store
a unique 64-hex `OAUTH_PASSWORD_CLIENT_SECRET` there before initialization;
the native HTTP acceptance scripts need the same value later. Import the four
Flowable 7.1.0 scripts after the base SQL and before starting any service,
even if Workflow will initially be disabled; `WORKFLOW_SCHEMA_UPDATE` remains
`false` in both modes.

```sh
sudo -i
set -e
REL=/opt/bixi/releases/v1.0.0-single   # use the staged cloud release for cloud
set -a; . /etc/bixi/bixi.env; set +a
export MYSQL_ROOT_USERNAME=root
read -r -s -p 'MySQL administrator password: ' MYSQL_ROOT_PASSWORD; echo
export MYSQL_ROOT_PASSWORD
export ADMIN_PASSWORD_BCRYPT_B64="$(htpasswd -nBC 12 admin | cut -d: -f2 | base64 -w0)"
export OAUTH_INTERNAL_SEED="$(openssl rand -hex 32)"
read -r -s -p 'Password-client secret from secret store: ' OAUTH_PASSWORD_CLIENT_SECRET; echo
export OAUTH_PASSWORD_CLIENT_SECRET
export OAUTH_MOBILE_CLIENT_SECRET="$(openssl rand -hex 32)"
bash "$REL/ops/init-db.sh"
mysql_tls=(--protocol=TCP)
case "$MYSQL_HOST" in
  localhost|127.0.0.1) ;;
  *) mysql_tls+=(--ssl-mode=VERIFY_IDENTITY "--ssl-ca=$MYSQL_SSL_CA") ;;
esac
for migration in \
  20260922_flowable_common_7_1_0.sql \
  20260922_flowable_engine_7_1_0.sql \
  20260922_flowable_history_7_1_0.sql \
  20260922_flowable_runtime_properties.sql; do
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 \
    "${mysql_tls[@]}" --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
    --user="$MYSQL_ROOT_USERNAME" "$MYSQL_DATABASE" < "$REL/sql/migrations/$migration"
done
unset MYSQL_ROOT_PASSWORD ADMIN_PASSWORD_BCRYPT_B64 OAUTH_INTERNAL_SEED \
  OAUTH_PASSWORD_CLIENT_SECRET OAUTH_MOBILE_CLIENT_SECRET
exit
```

The base SQL, secret seeding and Flowable schema run in order. Any import error
stops the root shell before service startup. These scripts are one-time empty
database initialization: after a partial failure, inspect the database and
restore a pre-install backup or recreate only the disposable new database;
never rerun `init-db.sh` or replay the non-idempotent Flowable schema on a
nonempty database. Check the initialization log and first login before
accepting the installation. This path is not an upgrade path for an existing
database.

## Cloud config and start

For cloud, create the authenticated Nacos namespace with ID `bixi-prod` and
a separate `bixi-prod-client` runtime user. Grant that user only resource
`bixi-prod:*:config/*` with action `r` and resource
`bixi-prod:*:naming/*` with action `rw`; it must not be able to publish
configuration or administer Nacos. Put this user's credentials in
`NACOS_USERNAME` and `NACOS_PASSWORD` in `/etc/bixi/bixi.env`. Keep a
different Nacos administrator or publisher identity in the operator's
root-only secret store.

Publish this package's `*-prod.yml` Data IDs while cloud services are stopped;
republishing can refresh live configuration. They contain environment
references, never secret values. The publisher requires `jq` and reads the
runtime host and namespace from the root-owned environment file. Enter the
separate publisher credentials from the secret store into the temporary root
shell; do not write them into a systemd environment file, shell command line,
release package or log:

```sh
sudo -i
set -e
set -a; . /etc/bixi/bixi.env; set +a
read -r -p 'Nacos publisher username from secret store: ' NACOS_PUBLISH_USERNAME
read -r -s -p 'Nacos publisher password from secret store: ' NACOS_PUBLISH_PASSWORD; echo
export NACOS_PUBLISH_USERNAME NACOS_PUBLISH_PASSWORD
sh /opt/bixi/releases/v1.0.0-cloud/ops/publish-nacos.sh /opt/bixi/releases/v1.0.0-cloud/ops/nacos
unset NACOS_PUBLISH_USERNAME NACOS_PUBLISH_PASSWORD
exit
```

Before starting services, verify every expected `*-prod.yml` Data ID exists
under `bixi-prod` and `DEFAULT_GROUP`. In the dedicated acceptance environment,
confirm `bixi-prod-client` can read one published Data ID but cannot write a
disposable config Data ID or administer Nacos; check that the denied write
created no config. After startup, confirm the same runtime user registers and
discovers the cloud services in `bixi-prod`, then check each service health URL
in the start sequence below. Record the ACL, result, timestamp and candidate
package in release evidence without recording credentials or access tokens.

When `AI_ENABLED=true`, set `SPRING_PROFILES_ACTIVE=prod,ai` in
`/etc/bixi/gateway.env` and configure `DASHSCOPE_API_KEY` in the protected
main environment file. When Workflow is enabled, enable the reliable Rabbit
adapter, provision distinct UPMS/Workflow RabbitMQ users and virtual host,
and put their `BIXI_RELIABLE_RABBIT_USERNAME`, `_PASSWORD` and
`_VIRTUAL_HOST` values in `/etc/bixi/upms.env` and
`/etc/bixi/workflow.env`. Leave `WORKFLOW_SCHEMA_UPDATE=false`; verify the
Flowable import above and complete recovery checks before enabling Workflow.

Activate the staged release only after database and Nacos prerequisites pass.
Record the current release path with `readlink -e /opt/bixi/current` before
switching an existing installation. The symlink replacement is atomic on Ubuntu
and can be retried after an interrupted attempt. Run this in a maintenance
window: Nginx serves the new UI as soon as the symlink changes. Restart each
cloud service, including units already running, and check its local health URL
before continuing. If any command fails, stop the rollout and use the rollback
procedure below:

```sh
set -e
sudo ln -sfnT "$REL" /opt/bixi/.current.new
sudo mv -Tf /opt/bixi/.current.new /opt/bixi/current
for entry in monitor:5001 upms:4000 generator:5002 quartz:5007 auth:3000 gateway:9999; do
  service=${entry%%:*}
  port=${entry#*:}
  sudo systemctl enable "bixi@$service"
  sudo systemctl restart "bixi@$service"
  curl -fsS --retry 60 --retry-connrefused --retry-delay 2 --max-time 5 \
    "http://127.0.0.1:$port/actuator/health" >/dev/null
done
for entry in ai:5000 workflow:5008; do
  service=${entry%%:*}
  port=${entry#*:}
  if sudo systemctl is-active --quiet "bixi@$service"; then
    sudo systemctl restart "bixi@$service"
    curl -fsS --retry 60 --retry-connrefused --retry-delay 2 --max-time 5 \
      "http://127.0.0.1:$port/actuator/health" >/dev/null
  fi
done
sudo systemctl reload nginx
```

Start `bixi@ai` and `bixi@workflow` only when their prerequisites and
feature checks are complete. An active optional unit is restarted and checked
by the commands above. The Gateway `ai` profile must already be active before
testing AI routes.

## Single start

Single uses no Nacos or Gateway. It reads the bundled
`application-prod.yml`, with Redis/MySQL credentials and local file storage
supplied at runtime. Switch its release symlink, then restart and check the unit
even when it is already running:

```sh
set -e
sudo ln -sfnT "$REL" /opt/bixi/.current.new
sudo mv -Tf /opt/bixi/.current.new /opt/bixi/current
sudo systemctl enable bixi@single
sudo systemctl restart bixi@single
curl -fsS --retry 60 --retry-connrefused --retry-delay 2 --max-time 5 \
  http://127.0.0.1:9999/admin/actuator/health >/dev/null
sudo systemctl reload nginx
```

Do not run cloud and single together: both own loopback port 9999. Validate
HTTPS, browser login/refresh/logout, API permissions, tenant isolation, audit
and optional features against the selected mode. On the target Ubuntu host
with the same pinned source commit, install Node 22.18+, `mysql-client`, `redis-tools`,
`zip` and `unzip`. The runner needs a trusted CA for the public HTTPS hostname
and private access to MySQL and Redis. The native fixture scripts write and
remove test rows, so use a dedicated acceptance database without business data.

Create a separate acceptance file outside the source tree and release package.
Enter the values from the operator's secret store in an editor, not in shell
arguments or a logged command. The password-client secret must be the value
seeded during fresh database initialization; the browser-client secret and
encode key must match `/etc/bixi/bixi.env`. Use the selected mode's actual
database, Redis address and certificate hostname:

```sh
umask 077
install -d -m 0700 "$HOME/.config/bixi"
ACCEPT_ENV="$HOME/.config/bixi/acceptance.env"
touch "$ACCEPT_ENV"
chmod 0600 "$ACCEPT_ENV"
vi "$ACCEPT_ENV"
```

```dotenv
BIXI_ORIGIN=https://bixi.example.com
BIXI_API_BASE_URL=https://bixi.example.com/api
ADMIN_USERNAME=admin
ADMIN_PASSWORD=REPLACE_FROM_SECRET_STORE
OAUTH_PASSWORD_CLIENT_SECRET=REPLACE_FROM_SECRET_STORE
OAUTH_BROWSER_CLIENT_SECRET=REPLACE_FROM_SECRET_STORE
BIXI_ENCODE_KEY=REPLACE_FROM_SECRET_STORE
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_PASSWORD=REPLACE_FROM_SECRET_STORE
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=bixi
MYSQL_ROOT_USERNAME=root
MYSQL_ROOT_PASSWORD=REPLACE_FROM_SECRET_STORE
WORKFLOW_ENABLED=false
```

Replace every placeholder and set `WORKFLOW_ENABLED` and its reliable-adapter
flags to the deployed values when Workflow is enabled. The baseline script
also needs direct `redis-cli` access to seed a one-time captcha; the negative
and tenant scripts use the MySQL credentials for isolated fixtures. Run from
the pinned source tree on that host, without sourcing or printing the acceptance
file. The negative script probes that host's loopback gateway or single process:

```sh
set -e
export BIXI_ENV_FILE="$ACCEPT_ENV" BIXI_FIXTURE_DRIVER=native BIXI_MODE=single
node scripts/acceptance.mjs
node scripts/acceptance-negative.mjs
node scripts/tenant-isolation-acceptance.mjs
```

Set `BIXI_MODE=cloud` for the separate cloud installation and repeat the same
commands. Keep the acceptance file mode `0600`; never commit it or include it
in the release package or evidence logs. Read process logs with
`journalctl -u bixi@single -f` or the matching cloud service name. Verify
`systemctl is-enabled` and a stop/start cycle.

## Backup and restore rehearsal

Use a dedicated acceptance installation with real test records and at least one
uploaded file. Record the last successful write time, then block new traffic
and stop every active Bixi unit before taking a consistent backup. Keep writers
stopped until both the MySQL dump and file/object snapshots finish. In a root
shell, source `/etc/bixi/bixi.env`, set `MYSQL_ROOT_USERNAME` to a backup-capable
database user, and write the dump and local file archive to a private directory
outside `/opt/bixi` and `/var/lib/bixi`:

```sh
set -e
umask 077
BACKUP_DIR=/secure/bixi-backup/rehearsal-1  # isolated, access-controlled storage
install -d -m 0700 "$BACKUP_DIR"
mysqldump --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_ROOT_USERNAME" --password --single-transaction \
  --routines --events --triggers "$MYSQL_DATABASE" > "$BACKUP_DIR/database.sql"
tar -C /var/lib/bixi -cpf "$BACKUP_DIR/files.tar" files
test -s "$BACKUP_DIR/database.sql"
test -s "$BACKUP_DIR/files.tar"
(cd "$BACKUP_DIR" && sha256sum database.sql files.tar > SHA256SUMS)
```

For remote MySQL, add `--ssl-mode=VERIFY_IDENTITY --ssl-ca="$MYSQL_SSL_CA"`
to the `mysqldump` and restore `mysql` commands; verify the server certificate
hostname. When `MINIO_ENABLED=true`, also export or snapshot the configured
bucket using the storage provider's supported procedure. Record its object-key
list, count and available checksums with the same backup time. Protect backup
files and object exports as production data; do not put them in the release
package or Git. An application package rollback does not restore data.

Restore only into a separate MySQL instance and empty file/object destination.
Confirm the target host and database differ from the live source before
importing. Create the target database and grants, then verify it has no tables,
routines, events or triggers, as checked by `init-db.sh`. In a root shell on
the target, source its `/etc/bixi/bixi.env`, set `MYSQL_ROOT_USERNAME` to a
restore-capable database user, and make the verified backup available. Then:

```sh
set -e
BACKUP_DIR=/secure/bixi-backup/rehearsal-1  # location on the target
(cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS)
mysql --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_ROOT_USERNAME" --password "$MYSQL_DATABASE" < "$BACKUP_DIR/database.sql"
tar -C /var/lib/bixi -xpf "$BACKUP_DIR/files.tar"
chown -R bixi:bixi /var/lib/bixi/files
```

Add the verified-identity MySQL TLS flags above when the target is remote.
Restore the object export into an empty `bixi` bucket at a separate storage
endpoint when enabled. Do **not** run `init-db.sh` or replay the Flowable SQL
on the restored database. Stage the exact same package, verify `SHA256SUMS`,
and set the target's protected environment to its new connection addresses
while preserving the original browser-client secret,
encode key and other data-encryption secrets. For cloud, publish this package's
production Nacos configuration to the target namespace before starting units.
Use the matching start block above, then verify HTTPS login, roles/permissions,
test records, local downloads and (when enabled) object downloads on the
restored instance. Run write-capable acceptance scripts only against this
isolated target, never against a live business database.

Record the candidate commit and package SHA-256, source/target host and mode,
MySQL and storage versions, backup and restore start/end times, interruption
time, last successful pre-backup write, first successful post-restore read,
table/record counts, file/object counts and sampled checksums, and any failed
checks. Calculate RPO from the last recoverable write to the interruption and
RTO from interruption to restored service; report measured values, not an
untested guarantee. After the rehearsal, redeploy the same package without
reinitializing the target
database and recheck the same records and files.

## Return to a previous package

If a restart or health check fails after switching packages, stop all Bixi
units and restore the release path recorded before the switch. On a fresh
installation with no previous release, stop the failed units and investigate
the new database without rerunning initialization SQL. Restore the old
package's Nginx config and (for cloud) its Nacos Data IDs while services are stopped. Then atomically
replace `current`, run `nginx -t`, restart the old mode in the order above, and
repeat its health and functional checks before reloading Nginx. Keep
`/etc/bixi/bixi.env`,
`/var/lib/bixi/files` and the database intact during an application rollback.
Do not restore an old empty-database snapshot over live business data. A
release that changed schema or data contracts needs a tested database restore
decision; this first stable release has no supported older version to roll
back to.

```sh
set -e
OLD=/opt/bixi/releases/previous-verified-release
REL="$OLD"
PUBLIC_HOST=bixi.example.com
sudo systemctl stop bixi@single bixi@gateway bixi@auth bixi@upms bixi@generator bixi@quartz bixi@monitor bixi@ai bixi@workflow
sudo ln -sfnT "$OLD" /opt/bixi/.current.new
sudo mv -Tf /opt/bixi/.current.new /opt/bixi/current
sudo install -m 0644 "$OLD/ops/nginx.conf" /etc/nginx/sites-available/bixi
sudo sed -i "s/BIXI_PUBLIC_HOST/$PUBLIC_HOST/g" /etc/nginx/sites-available/bixi
sudo nginx -t
# Rerun the matching start block above with REL=$OLD; it restarts and checks each unit.
```
