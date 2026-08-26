#!/bin/sh
set -eu

nacos_url="http://${NACOS_HOST:-nacos}:${NACOS_PORT:-8848}/nacos"
namespace="${NACOS_NAMESPACE:-bixi}"
group="${NACOS_GROUP:-DEFAULT_GROUP}"

curl -fsS -X POST "${nacos_url}/v1/console/namespaces" \
    --data-urlencode "customNamespaceId=${namespace}" \
    --data-urlencode "namespaceName=${namespace}" >/dev/null 2>&1 || true

for file in /config/*.yml; do
    data_id="$(basename "${file}")"
    response="$(curl -fsS -X POST "${nacos_url}/v1/cs/configs" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=${group}" \
        --data-urlencode "tenant=${namespace}" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${file}")"
    if [ "${response}" != "true" ]; then
        echo "Failed to publish Nacos config: ${data_id}" >&2
        exit 1
    fi
    echo "Published ${data_id}"
done
