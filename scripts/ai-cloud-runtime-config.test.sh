#!/bin/sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
compose="${ROOT}/compose.yaml"
gateway="${ROOT}/deploy/nacos/bixi-gateway-dev.yml"
ai_config="${ROOT}/deploy/nacos/bixi-ai-biz-dev.yml"
env_example="${ROOT}/.env.example"
runtime="${ROOT}/scripts/bixi.sh"

require_text() {
    file="$1"
    text="$2"
    message="$3"
    grep -F -- "$text" "$file" >/dev/null || {
        printf 'AI cloud runtime contract failed: %s (%s)\n' "$message" "$file" >&2
        exit 1
    }
}

require_text "$compose" '  ai:' 'Compose must define an AI service'
require_text "$compose" '  AI_ENABLED: ${AI_ENABLED:-false}' 'AI feature flag must reach shared cloud services for menu gating'
require_text "$compose" 'profiles: [ai]' 'AI must be behind the opt-in ai profile'
require_text "$compose" 'MODULE: bixi-module/bixi-ai-biz' 'AI service must build the shared AI business module'
require_text "$compose" 'AI_ENABLED: ${AI_ENABLED:-false}' 'AI feature flag must reach the service'
require_text "$compose" 'DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY:-}' 'AI provider key must be injected without a source value'
require_text "$compose" '127.0.0.1:${AI_PORT:-5000}:5000' 'AI health port must be isolated on loopback'
require_text "$compose" 'http://127.0.0.1:5000/actuator/health' 'AI service must have a health probe'

require_text "$gateway" 'on-profile: ai' 'AI gateway route must be profile-gated'
require_text "$gateway" 'id: bixi-ai' 'Gateway must expose the AI route when enabled'
require_text "$gateway" 'uri: lb://bixi-ai-biz' 'Gateway AI route must use the registered service name'
require_text "$gateway" 'Path=/ai/**' 'Gateway AI route must preserve the AI API prefix'

# Spring replaces list-valued properties when an active profile document
# defines `spring.cloud.gateway.routes`. The enabled document must therefore
# retain every base route as well as the opt-in AI route.
profile_gateway="$(awk '
    /^---[[:space:]]*$/ { active = 1; next }
    active { print }
' "$gateway")"
require_profile_text() {
    text="$1"
    message="$2"
    printf '%s\n' "$profile_gateway" | grep -F -- "$text" >/dev/null || {
        printf 'AI cloud runtime contract failed: %s (%s)\n' "$message" "$gateway" >&2
        exit 1
    }
}
require_profile_text 'id: bixi-auth' 'AI profile must retain the Auth route'
require_profile_text 'Path=/auth/**' 'AI profile must retain the Auth path'
require_profile_text 'id: bixi-upms' 'AI profile must retain the UPMS route'
require_profile_text 'Path=/admin/**' 'AI profile must retain the UPMS path'
require_profile_text 'id: bixi-generator' 'AI profile must retain the Generator route'
require_profile_text 'Path=/gen/**' 'AI profile must retain the Generator path'
require_profile_text 'id: bixi-ai' 'AI profile must add the AI route'
require_profile_text 'Path=/ai/**' 'AI profile must add the AI path'
require_profile_text 'PrefixPath=/ai' 'AI profile must restore the controller prefix after the global strip filter'
require_text "${ROOT}/bixi-module/bixi-ai-api/src/main/java/com/lotus/bixi/ai/api/constant/AiConstants.java" \
    'String AI_SERVICE = "bixi-ai-biz"' 'Feign clients must use the registered AI service name'

require_text "$ai_config" 'api-key: ${DASHSCOPE_API_KEY:}' 'Nacos must resolve the provider key from the environment'
require_text "$ai_config" 'enabled: ${AI_ENABLED:false}' 'Nacos must share the AI feature flag'
require_text "$ai_config" 'datasource:' 'AI Nacos config must define its database'
require_text "$ai_config" 'data:' 'AI Nacos config must define Redis settings'
require_text "$ai_config" 'security:' 'AI Nacos config must define the resource security boundary'
require_text "$ai_config" 'micro: true' 'AI cloud service must use the microservice RBAC mode'
require_text "$ai_config" '- /actuator/health' 'AI health must be the only anonymous operational probe'

require_text "$env_example" 'AI_ENABLED=false' 'AI must remain disabled by default'
require_text "$env_example" 'DASHSCOPE_API_KEY=' 'Provider keys must not be committed'
if grep -E 'DASHSCOPE_API_KEY=(sk-|dashscope|[A-Za-z0-9]{20,})' "$env_example" >/dev/null; then
    echo 'AI cloud runtime contract failed: .env.example appears to contain a provider secret' >&2
    exit 1
fi

require_text "$runtime" 'validate_ai_contract' 'start script must validate AI configuration before build'
require_text "$runtime" 'AI_ENABLED=true' 'start script must reject enabled AI without a key'
require_text "$runtime" 'ai_services="ai"' 'enabled cloud starts the AI service'
require_text "$runtime" 'ai_profiles="--profile ai"' 'enabled cloud activates the AI Compose profile'
require_text "$runtime" 'stop ai' 'disabled cloud stops a stale AI container'
require_text "$runtime" 'wait_for_url ai' 'enabled cloud waits for AI health'

if grep -E 'DASHSCOPE_API_KEY:[[:space:]]*(sk-|dashscope|[A-Za-z0-9]{20,})' "$compose" "$ai_config" >/dev/null; then
    echo 'AI cloud runtime contract failed: provider key is hard-coded in runtime resources' >&2
    exit 1
fi

echo 'AI cloud runtime configuration checks passed.'
