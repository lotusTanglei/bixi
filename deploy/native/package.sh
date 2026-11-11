#!/bin/sh
set -eu

fail() { echo "bixi package: $*" >&2; exit 1; }
mode="${1:-}"
output="${2:-}"
case "$mode" in cloud|single) ;; *) fail 'usage: package.sh cloud|single /absolute/output-directory' ;; esac
case "$output" in /*) ;; *) fail 'output directory must be absolute' ;; esac
[ ! -e "$output" ] || fail "output already exists: $output"
root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
case "$output" in "$root"/*) fail 'output must be outside the source tree' ;; esac
[ -z "$(git -C "$root" status --porcelain --untracked-files=normal)" ] \
    || fail 'source tree is not frozen; commit all release changes first'
command -v unzip >/dev/null 2>&1 || fail 'unzip is required to inspect JAR profiles'
command -v npm >/dev/null 2>&1 || fail 'npm is required to build the matching frontend'
command -v node >/dev/null 2>&1 || fail 'Node.js 22.18+ is required to build the matching frontend'
node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit(major > 22 || (major === 22 && minor >= 18) ? 0 : 1)' \
    || fail "Node.js 22.18+ is required; found $(node --version)"
version="$(cd "$root" && mvn -q -B -N -f bixi-common/bixi-common-bom/pom.xml \
    -Dstyle.color=never -DforceStdout \
    -Dexpression=project.version help:evaluate)" || fail 'unable to resolve Maven version'
ui_version="$(cd "$root/bixi-ui" && node -p 'require("./package.json").version')" \
    || fail 'unable to resolve frontend version'
[ "$version" = 1.0.0 ] && [ "$ui_version" = "$version" ] \
    || fail "release version mismatch: Maven=$version frontend=$ui_version"

if [ "$mode" = cloud ]; then
    (cd "$root" && mvn -B -ntp -Pcloud -Dprofiles.active=prod -Dnacos.namespace=bixi-prod \
        -Dmaven.test.skip=true clean package) || fail 'cloud build failed'
else
    (cd "$root" && mvn -B -ntp -Psingle -pl bixi-single -am -Dprofiles.active=prod \
        -Dmaven.test.skip=true clean package) || fail 'single build failed'
fi

if [ "$mode" = cloud ]; then
    set -- \
        bixi-gateway/target/bixi-gateway.jar:gateway \
        bixi-auth/target/bixi-auth.jar:auth \
        bixi-module/bixi-upms-biz/target/bixi-upms-biz-exec.jar:upms \
        bixi-module/bixi-generator/target/bixi-generator.jar:generator \
        bixi-module/bixi-quartz/target/bixi-quartz.jar:quartz \
        bixi-module/bixi-monitor/target/bixi-monitor.jar:monitor \
        bixi-module/bixi-ai-biz/target/bixi-ai-biz.jar:ai \
        bixi-module/bixi-workflow-biz/target/bixi-workflow-biz.jar:workflow
else
    set -- bixi-single/target/bixi-single.jar:single
fi
for pair do
    source=${pair%:*}
    [ -s "$root/$source" ] || fail "missing built JAR: $source"
    if [ "$mode" = cloud ]; then
        unzip -p "$root/$source" BOOT-INF/classes/application.yml | grep -F -- '-prod.yml' >/dev/null \
            || fail "$source was not built with -Dprofiles.active=prod"
    else
        unzip -p "$root/$source" BOOT-INF/classes/application-prod.yml | grep -F 'MYSQL_JDBC_URL' >/dev/null \
            || fail "$source does not contain application-prod.yml"
    fi
done

if [ "$mode" = cloud ]; then micro=true; else micro=false; fi
(cd "$root/bixi-ui" && VITE_IS_MICRO="$micro" npm run build:prod)
[ -f "$root/bixi-ui/dist/index.html" ] || fail 'frontend build produced no index.html'

mkdir -p "$output/jars" "$output/ui" "$output/ops" "$output/sql/migrations"
for pair do
    cp "$root/${pair%:*}" "$output/jars/${pair#*:}.jar"
done
if [ "$mode" = cloud ]; then
    cp -R "$root/deploy/native/nacos" "$output/ops/nacos"
fi

cp -R "$root/bixi-ui/dist/." "$output/ui/"
cp "$root/deploy/native/nginx/$mode.conf" "$output/ops/nginx.conf"
cp "$root/deploy/native/systemd/bixi@.service" "$output/ops/bixi@.service"
if [ "$mode" = cloud ]; then cp -R "$root/deploy/native/service-env" "$output/ops/service-env"; fi
cp "$root/deploy/native/preflight.sh" "$root/deploy/native/init-db.sh" \
    "$root/deploy/native/publish-nacos.sh" "$root/deploy/native/bixi.env.example" \
    "$root/deploy/native/README.md" "$output/ops/"
cp "$root"/bixi-project-documents/sql/0[1-4]_*.sql "$output/sql/"
for migration in common_7_1_0 engine_7_1_0 history_7_1_0 runtime_properties; do
    cp "$root/bixi-project-documents/sql/migrations/20260922_flowable_$migration.sql" \
        "$output/sql/migrations/"
done
printf 'commit=%s\nversion=%s\nmode=%s\nnamespace=bixi-prod\n' \
    "$(git -C "$root" rev-parse HEAD)" "$version" "$mode" > "$output/BUILD_INFO"

(
    cd "$output"
    find . -type f ! -name SHA256SUMS -print | LC_ALL=C sort | while IFS= read -r file; do
        if command -v sha256sum >/dev/null 2>&1; then
            sha256sum "$file"
        else
            shasum -a 256 "$file"
        fi
    done > SHA256SUMS
)
echo "Packaged $mode at $output"
