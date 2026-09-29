#!/bin/sh
set -eu

: "${RABBITMQ_ADMIN_USERNAME:?Rabbit administrator username is required}"
: "${RABBITMQ_ADMIN_PASSWORD:?Rabbit administrator password is required}"
: "${RABBITMQ_APP_USERNAME:?Rabbit application username is required}"
: "${RABBITMQ_APP_PASSWORD:?Rabbit application password is required}"
: "${RABBITMQ_UPMS_USERNAME:?UPMS Rabbit username is required}"
: "${RABBITMQ_UPMS_PASSWORD:?UPMS Rabbit password is required}"
: "${RABBITMQ_WORKFLOW_USERNAME:?Workflow Rabbit username is required}"
: "${RABBITMQ_WORKFLOW_PASSWORD:?Workflow Rabbit password is required}"
: "${RABBITMQ_WORKFLOW_VHOST:?Workflow Rabbit virtual host is required}"

UPMS_CONFIGURE_PATTERN='^(bixi\.workflow|bixi\.upms\.inbox)$'
UPMS_WRITE_PATTERN='^(bixi\.upms|bixi\.upms\.inbox)$'
UPMS_READ_PATTERN='^(bixi\.workflow|bixi\.upms\.inbox)$'
WORKFLOW_CONFIGURE_PATTERN='^(bixi\.upms|bixi\.workflow\.inbox)$'
WORKFLOW_WRITE_PATTERN='^(bixi\.workflow|bixi\.workflow\.inbox)$'
WORKFLOW_READ_PATTERN='^(bixi\.upms|bixi\.workflow\.inbox)$'

rabbit_admin() {
    rabbitmqadmin --host rabbitmq --port 15672 \
        --username "${RABBITMQ_ADMIN_USERNAME}" \
        --password "${RABBITMQ_ADMIN_PASSWORD}" "$@"
}

attempt=1
until rabbit_admin show overview >/dev/null 2>&1; do
    if [ "${attempt}" -ge 30 ]; then
        echo 'Rabbit management API did not become ready' >&2
        exit 1
    fi
    attempt=$((attempt + 1))
    sleep 2
done

rabbit_admin declare vhost name="${RABBITMQ_WORKFLOW_VHOST}" tracing=false

if [ "${RABBITMQ_APP_USERNAME}" = "${RABBITMQ_ADMIN_USERNAME}" ]; then
    if [ "${RABBITMQ_APP_PASSWORD}" != "${RABBITMQ_ADMIN_PASSWORD}" ]; then
        echo 'Rabbit administrator and application passwords must match when usernames are reused' >&2
        exit 1
    fi
    # Do not redeclare the shared user with empty tags: that would demote the
    # management account before the remaining permission calls complete.
else
    rabbit_admin declare user name="${RABBITMQ_APP_USERNAME}" password="${RABBITMQ_APP_PASSWORD}" tags=
fi
rabbit_admin declare permission vhost=/ user="${RABBITMQ_APP_USERNAME}" configure='.*' write='.*' read='.*'

rabbit_admin declare user name="${RABBITMQ_UPMS_USERNAME}" password="${RABBITMQ_UPMS_PASSWORD}" tags=
rabbit_admin declare permission vhost="${RABBITMQ_WORKFLOW_VHOST}" user="${RABBITMQ_UPMS_USERNAME}" \
    configure="${UPMS_CONFIGURE_PATTERN}" write="${UPMS_WRITE_PATTERN}" read="${UPMS_READ_PATTERN}"

rabbit_admin declare user name="${RABBITMQ_WORKFLOW_USERNAME}" password="${RABBITMQ_WORKFLOW_PASSWORD}" tags=
rabbit_admin declare permission vhost="${RABBITMQ_WORKFLOW_VHOST}" user="${RABBITMQ_WORKFLOW_USERNAME}" \
    configure="${WORKFLOW_CONFIGURE_PATTERN}" write="${WORKFLOW_WRITE_PATTERN}" read="${WORKFLOW_READ_PATTERN}"

echo 'Rabbit application and workflow trust boundaries configured.'
