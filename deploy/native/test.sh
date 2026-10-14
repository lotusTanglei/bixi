#!/bin/bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unset NACOS_PUBLISH_USERNAME NACOS_PUBLISH_PASSWORD
mkdir -p "$tmp/sql"
for number in 01 02 03 04; do
    printf 'SELECT 1;\n' > "$tmp/sql/${number}_fixture.sql"
done

export BIXI_SQL_DIR="$tmp/sql" BIXI_TEST_LOG="$tmp/mysql.log" BIXI_TEST_MYSQL_ARGS="$tmp/mysql-argv.log"
export MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=bixi
export MYSQL_ROOT_USERNAME=root MYSQL_ROOT_PASSWORD=fixture
export ADMIN_PASSWORD_BCRYPT_B64
ADMIN_PASSWORD_BCRYPT_B64="$(printf '%s' '$2y$12$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' | base64 | tr -d '\n')"
export OAUTH_INTERNAL_SEED=fixture OAUTH_PASSWORD_CLIENT_SECRET=fixture
export OAUTH_MOBILE_CLIENT_SECRET=fixture
export OAUTH_BROWSER_CLIENT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa

mysql() {
    printf '%s\n' "$*" >> "$BIXI_TEST_MYSQL_ARGS"
    case "$*" in
        *information_schema.schemata*) printf '1\n' ;;
        *information_schema.tables*) printf '%s\n' "$BIXI_TEST_TABLE_COUNT" ;;
        *) printf 'write\n' >> "$BIXI_TEST_LOG"; cat >/dev/null ;;
    esac
}
export -f mysql

export BIXI_TEST_TABLE_COUNT=1
if bash "$root/deploy/native/init-db.sh" > "$tmp/rejected.out" 2>&1; then
    echo 'nonempty database was accepted' >&2
    exit 1
fi
grep -F 'is not empty; no SQL was applied' "$tmp/rejected.out" >/dev/null
[[ ! -e "$BIXI_TEST_LOG" ]] || { echo 'nonempty database was modified' >&2; exit 1; }

export BIXI_TEST_TABLE_COUNT=0
bash "$root/deploy/native/init-db.sh" > "$tmp/accepted.out"
[[ "$(wc -l < "$BIXI_TEST_LOG" | tr -d ' ')" == 5 ]] \
    || { echo 'fresh database did not import four files and seed secrets' >&2; exit 1; }
grep -F 'Fresh database initialized' "$tmp/accepted.out" >/dev/null

export MYSQL_HOST=db.example.test BIXI_TEST_TABLE_COUNT=1
unset MYSQL_SSL_CA
if bash "$root/deploy/native/init-db.sh" > "$tmp/remote-db.out" 2>&1; then
    echo 'remote MySQL accepted without a trusted CA' >&2
    exit 1
fi
grep -F 'remote MySQL requires MYSQL_SSL_CA' "$tmp/remote-db.out" >/dev/null
printf 'fixture CA\n' > "$tmp/mysql-ca.pem"
export MYSQL_SSL_CA="$tmp/mysql-ca.pem"
if bash "$root/deploy/native/init-db.sh" > "$tmp/remote-db.out" 2>&1; then
    echo 'nonempty remote database was accepted' >&2
    exit 1
fi
grep -F -- '--ssl-mode=VERIFY_IDENTITY' "$tmp/mysql-argv.log" >/dev/null
grep -F -- "--ssl-ca=$MYSQL_SSL_CA" "$tmp/mysql-argv.log" >/dev/null
export MYSQL_HOST=127.0.0.1
unset MYSQL_SSL_CA

if sh "$root/deploy/native/preflight.sh" wrong-service > "$tmp/preflight.out" 2>&1; then
    echo 'unknown service was accepted' >&2
    exit 1
fi
grep -F 'unknown service' "$tmp/preflight.out" >/dev/null

native_env=(BIXI_DEPLOYMENT_MODE=single SERVER_ADDRESS=127.0.0.1 SPRING_PROFILES_ACTIVE=prod
    MYSQL_PORT=3306 MYSQL_DATABASE=bixi MYSQL_USERNAME=bixi MYSQL_PASSWORD=fixture
    REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD=fixture
    BIXI_ENCODE_KEY=fixture OAUTH_BROWSER_CLIENT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
    JASYPT_ENCRYPTOR_PASSWORD=fixture GENERATOR_PROJECT_ROOT=/var/lib/bixi/generator-output)
if env "${native_env[@]}" MYSQL_HOST=db.example.test \
    MYSQL_JDBC_URL='jdbc:mysql://db.example.test:3306/bixi?sslMode=REQUIRED' \
    sh "$root/deploy/native/preflight.sh" single > "$tmp/preflight.out" 2>&1; then
    echo 'remote MySQL without verified identity was accepted' >&2
    exit 1
fi
grep -F 'remote MySQL requires sslMode=VERIFY_IDENTITY' "$tmp/preflight.out" >/dev/null
if env "${native_env[@]}" MYSQL_HOST=db.example.test \
    MYSQL_JDBC_URL='jdbc:mysql://db.example.test:3306/bixi?sslMode=VERIFY_IDENTITY' \
    sh "$root/deploy/native/preflight.sh" single > "$tmp/preflight.out" 2>&1; then
    echo 'preflight unexpectedly found an installed release JAR' >&2
    exit 1
fi
grep -F 'missing release JAR: single.jar' "$tmp/preflight.out" >/dev/null
if env "${native_env[@]}" MYSQL_HOST=127.0.0.1 \
    MYSQL_JDBC_URL='jdbc:mysql://127.0.0.1:3306/bixi?sslMode=REQUIRED' \
    NACOS_PUBLISH_PASSWORD=fixture \
    sh "$root/deploy/native/preflight.sh" single > "$tmp/preflight.out" 2>&1; then
    echo 'publisher credentials reached a service process' >&2
    exit 1
fi
grep -F 'publisher credentials must not be present in service environment' "$tmp/preflight.out" >/dev/null \
    || { echo 'preflight did not reject publisher credentials' >&2; exit 1; }

if command -v nginx >/dev/null 2>&1 && command -v openssl >/dev/null 2>&1; then
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost \
        -keyout "$tmp/key.pem" -out "$tmp/cert.pem" >/dev/null 2>&1
    for mode in cloud single; do
        sed -e "s|/etc/bixi/tls/fullchain.pem|$tmp/cert.pem|g" \
            -e "s|/etc/bixi/tls/privkey.pem|$tmp/key.pem|g" \
            "$root/deploy/native/nginx/$mode.conf" > "$tmp/site.conf"
        printf 'events {}\nhttp { include %s; }\n' "$tmp/site.conf" > "$tmp/nginx.conf"
        nginx -t -c "$tmp/nginx.conf" -p "$tmp" > "$tmp/nginx.out" 2>&1 \
            || { cat "$tmp/nginx.out" >&2; exit 1; }
    done
    python3 "$root/scripts/test-sms-ingress-rate-limit.py"
fi
for mode in cloud single; do
    conf="$root/deploy/native/nginx/$mode.conf"
    grep -F 'client_max_body_size 100m;' "$conf" >/dev/null
    grep -F 'map $http_upgrade $bixi_connection_upgrade' "$conf" >/dev/null
    sed -n '1,/location = \/healthz/p' "$conf" | grep -F 'add_header Content-Security-Policy' >/dev/null
    sed -n '/location \/api\/ {/,/^    }/p' "$conf" | grep -F 'proxy_set_header Upgrade $http_upgrade;' >/dev/null
    sed -n '/location \/api\/ {/,/^    }/p' "$conf" | grep -F 'proxy_set_header Connection $bixi_connection_upgrade;' >/dev/null
    if grep -A 20 'location /api/' "$conf" | grep -F 'add_header ' >/dev/null; then
        echo 'API location overrides inherited security headers' >&2
        exit 1
    fi
done

release_guide="$root/deploy/native/README.md"
if grep -F 'systemctl enable --now bixi@' "$release_guide" >/dev/null; then
    echo 'native redeploy would leave an active JAR running after the release switch' >&2
    exit 1
fi
grep -F 'sudo systemctl restart "bixi@$service"' "$release_guide" >/dev/null
grep -F 'sudo systemctl restart bixi@single' "$release_guide" >/dev/null
grep -F 'sudo systemctl is-active --quiet "bixi@$service"' "$release_guide" >/dev/null
grep -F 'sudo systemctl stop bixi@single' "$release_guide" >/dev/null
grep -F 'REL="$OLD"' "$release_guide" >/dev/null
grep -F 'curl -fsS --retry 60 --retry-connrefused' "$release_guide" >/dev/null

export NACOS_HOST=127.0.0.1 NACOS_PORT=8848 NACOS_NAMESPACE=bixi-prod
export NACOS_USERNAME=bixi-prod-client NACOS_PASSWORD=fixture-runtime-password
export NACOS_PUBLISH_USERNAME=operator NACOS_PUBLISH_PASSWORD=fixture-publish-password
mkdir -p "$tmp/nacos"
printf 'spring: {}\n' > "$tmp/nacos/fixture-prod.yml"
if NACOS_HOST=nacos.example.test sh "$root/deploy/native/publish-nacos.sh" "$tmp/nacos" \
    > "$tmp/nacos-remote.out" 2>&1; then
    echo 'remote Nacos HTTP credential submission was accepted' >&2
    exit 1
fi
grep -F 'NACOS_HOST must be loopback' "$tmp/nacos-remote.out" >/dev/null
curl() {
    case "$*" in
        *'/v1/auth/users/login'*)
            [[ "$*" == *"username=$NACOS_PUBLISH_USERNAME"* ]] || { echo 'publisher username was not used' >&2; return 1; }
            [[ "$*" != *"$NACOS_PUBLISH_PASSWORD"* ]] || { echo 'publisher password was exposed in curl arguments' >&2; return 1; }
            [[ "$*" != *"$NACOS_PASSWORD"* ]] || { echo 'runtime password was exposed in curl arguments' >&2; return 1; }
            [[ "$*" == *'password@-'* ]] || { echo 'Nacos password was not read from stdin' >&2; return 1; }
            [[ "$(cat)" == "$NACOS_PUBLISH_PASSWORD" ]] || { echo 'publisher password was not used' >&2; return 1; }
            printf '{"accessToken":"fixture-access-token"}\n'
            ;;
        *'/v1/cs/configs'*)
            [[ "$*" != *'fixture-access-token'* ]] || { echo 'Nacos token was exposed in curl arguments' >&2; return 1; }
            [[ "$*" == *'accessToken@-'* ]] || { echo 'Nacos token was not read from stdin' >&2; return 1; }
            [[ "$(cat)" == fixture-access-token ]] || return 1
            printf 'true\n'
            ;;
        *) echo 'unexpected Nacos request' >&2; return 1 ;;
    esac
}
export -f curl
if NACOS_PUBLISH_USERNAME= bash "$root/deploy/native/publish-nacos.sh" "$tmp/nacos" \
    > "$tmp/nacos-missing-publisher.out" 2>&1; then
    echo 'publisher accepted missing dedicated username' >&2
    exit 1
fi
grep -F 'NACOS_PUBLISH_USERNAME is required' "$tmp/nacos-missing-publisher.out" >/dev/null \
    || { echo 'publisher did not require separate credentials' >&2; exit 1; }
if NACOS_PUBLISH_PASSWORD= bash "$root/deploy/native/publish-nacos.sh" "$tmp/nacos" \
    > "$tmp/nacos-missing-password.out" 2>&1; then
    echo 'publisher accepted missing dedicated password' >&2
    exit 1
fi
grep -F 'NACOS_PUBLISH_PASSWORD is required' "$tmp/nacos-missing-password.out" >/dev/null \
    || { echo 'publisher did not require separate password' >&2; exit 1; }
if NACOS_PUBLISH_USERNAME="$NACOS_USERNAME" bash "$root/deploy/native/publish-nacos.sh" "$tmp/nacos" \
    > "$tmp/nacos-shared-user.out" 2>&1; then
    echo 'publisher accepted the runtime username' >&2
    exit 1
fi
grep -F 'publisher username must differ from runtime username' "$tmp/nacos-shared-user.out" >/dev/null \
    || { echo 'publisher did not reject shared username' >&2; exit 1; }
bash "$root/deploy/native/publish-nacos.sh" "$tmp/nacos" > "$tmp/nacos.out"
grep -F 'Published fixture-prod.yml' "$tmp/nacos.out" >/dev/null

mkdir -p "$tmp/package-root/deploy/native" "$tmp/package-root/bixi-ui" "$tmp/package-bin"
cp "$root/deploy/native/package.sh" "$tmp/package-root/deploy/native/package.sh"
git -C "$tmp/package-root" init -q
git -C "$tmp/package-root" add deploy/native/package.sh
git -C "$tmp/package-root" -c user.name=Test -c user.email=test@example.invalid commit -qm fixture
cat > "$tmp/package-bin/mvn" <<'MVN'
#!/bin/sh
case "$*" in
    *help:evaluate*) printf '1.0.0\n' ;;
    *) printf '%s\n' "$*" > "$BIXI_TEST_BUILD_LOG"; exit 17 ;;
esac
MVN
export BIXI_TEST_REAL_NODE="$(command -v node)"
cat > "$tmp/package-bin/node" <<'NODE'
#!/bin/sh
case "$1" in
    -e) [ "${BIXI_TEST_NODE_UNSUPPORTED:-false}" = false ] || exit 1
        exec "$BIXI_TEST_REAL_NODE" "$@" ;;
    -p) printf '1.0.0\n' ;;
    --version) printf 'v20.20.2\n' ;;
esac
NODE
printf '%s\n' '#!/bin/sh' 'exit 0' > "$tmp/package-bin/npm"
chmod +x "$tmp/package-bin/mvn" "$tmp/package-bin/node" "$tmp/package-bin/npm"
export BIXI_TEST_BUILD_LOG="$tmp/unsupported-node-build.log"
if BIXI_TEST_NODE_UNSUPPORTED=true PATH="$tmp/package-bin:$PATH" \
    sh "$tmp/package-root/deploy/native/package.sh" cloud "$tmp/unsupported-node-package" \
    > "$tmp/unsupported-node-package.out" 2>&1; then
    echo 'package accepted an unsupported Node version' >&2
    exit 1
fi
grep -F 'Node.js 22.18+ is required' "$tmp/unsupported-node-package.out" >/dev/null
[[ ! -e "$BIXI_TEST_BUILD_LOG" ]] || { echo 'package built with unsupported Node' >&2; exit 1; }
for mode in cloud single; do
    export BIXI_TEST_BUILD_LOG="$tmp/$mode-build.log"
    if PATH="$tmp/package-bin:$PATH" sh "$tmp/package-root/deploy/native/package.sh" \
        "$mode" "$tmp/$mode-package" > "$tmp/$mode-package.out" 2>&1; then
        echo "$mode package accepted a failed fresh build" >&2
        exit 1
    fi
    [[ -f "$BIXI_TEST_BUILD_LOG" ]] || { cat "$tmp/$mode-package.out" >&2; echo "$mode package did not rebuild from source" >&2; exit 1; }
    grep -F -- "-P$mode" "$BIXI_TEST_BUILD_LOG" >/dev/null \
        || { echo "$mode package did not rebuild from source" >&2; exit 1; }
    grep -F -- '-Dprofiles.active=prod' "$BIXI_TEST_BUILD_LOG" >/dev/null
    grep -F -- 'clean package' "$BIXI_TEST_BUILD_LOG" >/dev/null
    if [ "$mode" = cloud ]; then
        grep -F -- '-Dnacos.namespace=bixi-prod' "$BIXI_TEST_BUILD_LOG" >/dev/null
    else
        grep -F -- '-pl bixi-single -am' "$BIXI_TEST_BUILD_LOG" >/dev/null
    fi
done
echo 'Native deployment static checks passed'
