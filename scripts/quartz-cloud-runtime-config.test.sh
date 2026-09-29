#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

fail() {
	printf 'Quartz cloud runtime contract failed: %s\n' "$*" >&2
	exit 1
}

require_text() {
	file="$1"
	text="$2"
	message="$3"
	grep -F -- "$text" "$ROOT/$file" >/dev/null 2>&1 || fail "$message"
}

service_block() {
	service="$1"
	awk -v marker="  ${service}:" '
		$0 == marker { inside = 1 }
		inside && /^  [a-zA-Z0-9_-]+:$/ && $0 != marker { exit }
		inside { print }
	' "$ROOT/compose.yaml"
}

quartz_service="$(service_block quartz)"
[ -n "$quartz_service" ] || fail 'compose.yaml must define the Quartz cloud service'
for contract in \
	'profiles: [cloud]' \
	'MODULE: bixi-module/bixi-quartz' \
	'MAVEN_PROFILE: cloud' \
	'BIXI_DEPLOYMENT_MODE: cloud' \
	'BIXI_MANAGEMENT_ENDPOINTS: ${BIXI_MANAGEMENT_ENDPOINTS:-health,info,metrics}' \
	'http://127.0.0.1:5007/actuator/health' \
	'mysql:' \
	'redis:' \
	'nacos-config:'; do
	printf '%s\n' "$quartz_service" | grep -F -- "$contract" >/dev/null 2>&1 \
		|| fail "Quartz Compose service is missing: $contract"
done

require_text deploy/nacos/bixi-quartz-dev.yml \
	'url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:bixi}' \
	'Quartz Nacos config must use runtime MySQL coordinates'
require_text deploy/nacos/bixi-quartz-dev.yml '- /actuator/health/**' \
	'Quartz health probe must be permitted without a token'
require_text deploy/nacos/bixi-quartz-dev.yml 'mapper-locations: classpath*:/mapper/*Mapper.xml' \
	'Quartz Nacos config must include MyBatis mapper locations'

gateway='deploy/nacos/bixi-gateway-dev.yml'
for route in 'id: bixi-quartz' 'uri: lb://bixi-quartz' 'Path=/job/**'; do
	require_text "$gateway" "$route" "Gateway must expose Quartz route: $route"
done
route_count="$(grep -cF 'id: bixi-quartz' "$ROOT/$gateway")"
[ "$route_count" -ge 2 ] || fail 'AI profile must retain the Quartz route when it replaces the route list'
require_text bixi-gateway/src/main/java/com/lotus/bixi/gateway/security/GatewaySecurityConfiguration.java \
	'"/job/actuator/health", "/job/actuator/health/**"' \
	'Gateway must permit the Quartz health probe'

require_text bixi-module/bixi-quartz/src/main/java/com/lotus/bixi/quartz/BixiQuartzApplication.java \
	'havingValue = "cloud", matchIfMissing = true' \
	'Quartz application must be available as the Cloud deployment entry point'
require_text bixi-module/bixi-quartz/src/main/resources/quartz-config.yml \
	'isClustered: true' \
	'Quartz must use the clustered JDBC scheduler configuration'
require_text bixi-module/bixi-quartz/src/main/resources/quartz-config.yml \
	'job-store-type: jdbc' \
	'Quartz must persist scheduler state in JDBC'

start_cloud="$(awk '
	/^start_cloud\(\) \{$/ { inside = 1 }
	inside { print }
	inside && /^\}$/ { exit }
' "$ROOT/scripts/bixi.sh")"
for contract in \
	'gateway upms auth generator quartz frontend-cloud' \
	'mysql redis rabbitmq nacos nacos-config monitor gateway upms auth generator quartz frontend-cloud' \
	'wait_for_url quartz "http://localhost:${GATEWAY_PORT}/job/actuator/health"'; do
	printf '%s\n' "$start_cloud" | grep -F -- "$contract" >/dev/null 2>&1 \
		|| fail "start_cloud is missing Quartz orchestration: $contract"
done

start_single="$(awk '
	/^start_single\(\) \{$/ { inside = 1 }
	inside { print }
	inside && /^\}$/ { exit }
' "$ROOT/scripts/bixi.sh")"
printf '%s\n' "$start_single" | grep -F -- 'stop frontend-cloud monitor auth upms generator quartz' >/dev/null 2>&1 \
	|| fail 'start_single must stop the Cloud Quartz service before starting Single'

printf 'Quartz cloud runtime configuration contract tests passed.\n'
