ARG REGISTRY_MIRROR
FROM ${REGISTRY_MIRROR}library/node:20.18.1-alpine3.20 AS build

WORKDIR /workspace

ARG PUBLIC_MICRO_MODE=true
ARG PUBLIC_WEB_CLIENT
ARG PUBLIC_MOBILE_CLIENT
ARG PUBLIC_CIPHER_CONFIG

COPY bixi-ui/package.json bixi-ui/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY bixi-ui/ .
RUN sh ./docker-build.sh

FROM ${REGISTRY_MIRROR}library/nginx:1.27.3-alpine3.20

ARG NGINX_CONFIG=cloud.conf

COPY deploy/nginx/${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
