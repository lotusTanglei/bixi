#!/bin/sh
set -eu

fail() { echo "bixi Nacos publish: $*" >&2; exit 1; }
config_dir="${1:-}"
[ -d "$config_dir" ] || fail 'usage: publish-nacos.sh /path/to/release/ops/nacos'
for key in NACOS_HOST NACOS_PORT NACOS_USERNAME NACOS_PUBLISH_USERNAME NACOS_PUBLISH_PASSWORD; do
    value="$(printenv "$key" 2>/dev/null || true)"
    [ -n "$value" ] || fail "$key is required"
done
[ "$NACOS_PUBLISH_USERNAME" != "$NACOS_USERNAME" ] \
    || fail 'publisher username must differ from runtime username'
[ "${NACOS_NAMESPACE:-}" = bixi-prod ] || fail 'NACOS_NAMESPACE must be bixi-prod'
case "$NACOS_HOST" in
    localhost|127.0.0.1) ;;
    *) fail 'NACOS_HOST must be loopback; use a verified tunnel for remote Nacos' ;;
esac
command -v jq >/dev/null 2>&1 || fail 'jq is required'
base="http://${NACOS_HOST}:${NACOS_PORT}/nacos"
token="$(printf '%s' "$NACOS_PUBLISH_PASSWORD" | curl -fsS -X POST "$base/v1/auth/users/login" \
    --data-urlencode "username=$NACOS_PUBLISH_USERNAME" \
    --data-urlencode 'password@-' | jq -er '.accessToken')" \
    || fail 'Nacos authentication failed'

for file in "$config_dir"/*.yml; do
    [ -f "$file" ] || fail 'no production Nacos YAML files found'
    data_id="$(basename "$file")"
    case "$data_id" in *-prod.yml|application-prod.yml) ;; *) fail "unexpected Data ID: $data_id" ;; esac
    response="$(printf '%s' "$token" | curl -fsS -X POST "$base/v1/cs/configs" \
        --data-urlencode 'accessToken@-' \
        --data-urlencode "dataId=$data_id" \
        --data-urlencode 'group=DEFAULT_GROUP' \
        --data-urlencode "tenant=$NACOS_NAMESPACE" \
        --data-urlencode 'type=yaml' \
        --data-urlencode "content@$file")" || fail "failed to publish $data_id"
    [ "$response" = true ] || fail "Nacos rejected $data_id"
    echo "Published $data_id"
done
