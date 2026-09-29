#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

fail() {
	printf 'Generator cloud runtime contract failed: %s\n' "$*" >&2
	exit 1
}

require_text() {
	file="$1"
	text="$2"
	message="$3"
	grep -F -- "${text}" "${ROOT}/${file}" >/dev/null 2>&1 || fail "${message}"
}

require_writable_mount() {
	service="$1"
	printf '%s\n' "${service}" | grep -E \
		'^[[:space:]]*-[[:space:]]*\$\{GENERATOR_OUTPUT_PATH:-generator-output\}:/data/generator-output[[:space:]]*$' \
		>/dev/null 2>&1 || fail 'Generator output mount must be present and writable'
}

service_block() {
	service="$1"
	awk -v marker="  ${service}:" '
		$0 == marker { inside = 1 }
		inside && /^  [a-zA-Z0-9_-]+:$/ && $0 != marker { exit }
		inside { print }
	' "${ROOT}/compose.yaml"
}

nacos_config='deploy/nacos/bixi-generator-dev.yml'
[ -s "${ROOT}/${nacos_config}" ] || fail "missing ${nacos_config}"

generator_service="$(service_block generator)"
[ -n "${generator_service}" ] || fail 'compose.yaml must define the generator service'
for contract in \
	'profiles: [cloud]' \
	'MODULE: bixi-module/bixi-generator' \
	'MAVEN_PROFILE: cloud' \
	'BIXI_DEPLOYMENT_MODE: cloud' \
	'GENERATOR_ENABLED: ${GENERATOR_ENABLED:-true}' \
	'GENERATOR_PROJECT_ROOT: /data/generator-output' \
	'http://127.0.0.1:5002/actuator/health'; do
	printf '%s\n' "${generator_service}" | grep -F -- "${contract}" >/dev/null 2>&1 \
		|| fail "generator Compose service is missing: ${contract}"
done
require_writable_mount "${generator_service}"

single_service="$(service_block single)"
[ -n "${single_service}" ] || fail 'compose.yaml must define the single service'
for contract in \
	'BIXI_DEPLOYMENT_MODE: single' \
	'GENERATOR_ENABLED: ${GENERATOR_ENABLED:-true}' \
	'GENERATOR_PROJECT_ROOT: /data/generator-output'; do
	printf '%s\n' "${single_service}" | grep -F -- "${contract}" >/dev/null 2>&1 \
		|| fail "single Compose service is missing Generator output contract: ${contract}"
done
require_writable_mount "${single_service}"

upms_service="$(service_block upms)"
[ -n "${upms_service}" ] || fail 'compose.yaml must define the upms service'
printf '%s\n' "${upms_service}" | grep -F -- \
	'GENERATOR_ENABLED: ${GENERATOR_ENABLED:-true}' >/dev/null 2>&1 \
	|| fail 'upms Compose service must receive the Generator enabled switch'

require_text deploy/docker/backend.Dockerfile '/data/generator-output' \
	'Backend image must create the Generator output directory for the non-root runtime user'
require_text compose.yaml '  generator-output:' \
	'Compose must declare the default persistent Generator output volume'
require_text .env.example 'GENERATOR_OUTPUT_PATH=generator-output' \
	'Runtime template must expose the optional Generator output mount source'
for dependency in mysql redis nacos-config; do
	printf '%s\n' "${generator_service}" | grep -F -- "${dependency}:" >/dev/null 2>&1 \
		|| fail "generator Compose service must depend on ${dependency}"
done

require_text deploy/nacos/bixi-gateway-dev.yml 'id: bixi-generator' \
	'Gateway must declare a named Generator route'
require_text deploy/nacos/bixi-gateway-dev.yml 'uri: lb://bixi-generator' \
	'Gateway must route Generator traffic to bixi-generator'
require_text deploy/nacos/bixi-gateway-dev.yml 'Path=/gen/**' \
	'Gateway must expose Generator at /gen/**'

if grep -F 'context-path:' "${ROOT}/${nacos_config}" >/dev/null 2>&1; then
	fail 'Generator must receive the path after the Gateway global StripPrefix filter'
fi
require_text "${nacos_config}" 'driver-class-name: com.mysql.cj.jdbc.Driver' \
	'Generator Nacos config must declare the MySQL datasource'
require_text "${nacos_config}" 'username: ${MYSQL_USERNAME}' \
	'Generator datasource username must come from the runtime environment'
require_text "${nacos_config}" 'password: ${MYSQL_PASSWORD}' \
	'Generator datasource password must come from the runtime environment'
require_text "${nacos_config}" 'url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:bixi}' \
	'Generator datasource URL must use runtime database coordinates'
require_text "${nacos_config}" '- /actuator/health' \
	'Generator health probe must be permitted without a token'
require_text "${nacos_config}" '- /actuator/health/**' \
	'Generator component health probes must be permitted without a token'
require_text "${nacos_config}" 'enabled: ${GENERATOR_ENABLED:true}' \
	'Generator cloud assembly must have an explicit enabled switch'
require_text deploy/nacos/bixi-upms-biz-dev.yml \
	'enabled: ${GENERATOR_ENABLED:true}' \
	'UPMS cloud assembly must receive the Generator enabled switch'
require_text bixi-single/src/main/resources/application.yml \
	'enabled: ${GENERATOR_ENABLED:true}' \
	'Single assembly must have the same explicit Generator enabled switch'

require_text bixi-module/bixi-generator/src/main/resources/application.yml \
	'namespace: ${NACOS_NAMESPACE:@nacos.namespace@}' \
	'Generator discovery and config must honor the runtime Nacos namespace'
require_text bixi-module/bixi-generator/src/main/resources/application.yml \
	'mode: ${BIXI_DEPLOYMENT_MODE:cloud}' \
	'Generator must select cloud adapters explicitly'

start_cloud="$(awk '
	/^start_cloud\(\) \{$/ { inside = 1 }
	inside { print }
	inside && /^}$/ { exit }
' "${ROOT}/scripts/bixi.sh")"
for contract in \
	'gateway upms auth generator quartz frontend-cloud' \
	'mysql redis rabbitmq nacos nacos-config monitor gateway upms auth generator quartz frontend-cloud' \
	'wait_for_url generator "http://localhost:${GATEWAY_PORT}/gen/actuator/health"'; do
	printf '%s\n' "${start_cloud}" | grep -F -- "${contract}" >/dev/null 2>&1 \
		|| fail "start_cloud is missing Generator orchestration: ${contract}"
done

diagnose="$(awk '
	/^diagnose\(\) \{$/ { inside = 1 }
	inside { print }
	inside && /^}$/ { exit }
' "${ROOT}/scripts/bixi.sh")"
printf '%s\n' "${diagnose}" | grep -F -- \
	'generator|http://localhost:${GATEWAY_PORT}/gen/actuator/health' >/dev/null 2>&1 \
	|| fail 'diagnose must check Generator through Gateway'

printf 'Generator cloud runtime configuration contract tests passed.\n'
