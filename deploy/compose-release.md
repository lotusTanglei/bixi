# Bixi Compose release installation

This is the immutable-image entry for the candidate `v1.0.0` release on an
Ubuntu 24.04 x86_64 host. It is a fresh-install procedure, not evidence that
the release gate has passed. Use Docker 24+ and Docker Compose 2.24+; the
release override needs Compose's `!reset` tag to remove development builds.

## Freeze and publish the images

Start from one clean, committed candidate with Maven and frontend versions
both set to `1.0.0`. Run the source gates before building images. From the
repository root, use Docker Buildx to build and push the Ubuntu x86_64 images
from that commit (replace `REGISTRY` with your registry and log in first). The explicit
`CONFIG_PROFILE=prod` build argument packages `*-prod.yml` Nacos imports;
the backend Dockerfile otherwise defaults to `dev`.

```sh
REGISTRY=registry.example/bixi
TAG=1.0.0
build_backend() {
  docker buildx build --platform linux/amd64 --file deploy/docker/backend.Dockerfile \
    --build-arg MODULE="$2" --build-arg MAVEN_PROFILE="$3" \
    --build-arg CONFIG_PROFILE=prod --tag "$REGISTRY/$1:$TAG" --push .
}
build_backend monitor bixi-module/bixi-monitor cloud
build_backend gateway bixi-gateway cloud
build_backend auth bixi-auth cloud
build_backend upms bixi-module/bixi-upms-biz cloud
build_backend generator bixi-module/bixi-generator cloud
build_backend quartz bixi-module/bixi-quartz cloud
build_backend ai bixi-module/bixi-ai-biz cloud
build_backend workflow bixi-module/bixi-workflow-biz cloud
build_backend single bixi-single single

build_frontend() {
  docker buildx build --platform linux/amd64 --file deploy/docker/frontend.Dockerfile \
    --build-arg PUBLIC_MICRO_MODE="$2" --build-arg NGINX_CONFIG="$3" \
    --tag "$REGISTRY/$1:$TAG" --push .
}
build_frontend frontend-cloud true cloud.conf
build_frontend frontend-single false single.conf
```

Inspect each pushed image in the registry, for example with
`docker buildx imagetools inspect "$REGISTRY/auth:$TAG"`, and record its actual
registry `name@sha256:<digest>` reference. Record the exact MySQL, Redis,
RabbitMQ, Nacos, and Nginx registry digests used as well. Archive the build
logs, candidate commit, frontend artifacts, SQL files, and image digest list
together. Do not build from source on the target host.

Copy [.env.release.example](../.env.release.example) to the ignored
`.env.release`, fill its `commit` with the frozen 40-character Git commit and
replace every image placeholder with a registry `name@sha256:<64 hex>`
reference. All image entries are required, including optional AI and Workflow
images. A tag such as `:1.0.0` is insufficient because it can be moved.

## Prepare runtime configuration

Use `make init-env` once to create the ignored, mode-600 `.env`. Set a trusted
TLS certificate directory containing readable `fullchain.pem` and
`privkey.pem`, then set `BIXI_HTTPS_ENABLED=true` and `BIXI_TLS_DIR` in `.env`.
For cloud set `NACOS_NAMESPACE=bixi-prod`. `make init-env` creates separate
`NACOS_PASSWORD` and `NACOS_CLIENT_PASSWORD` values: the first is used only by
the one-time Nacos config publisher, while business containers receive the
`bixi-prod-client` account. Keep both private and distinct. The publisher
creates that account with `bixi-prod:*:config/*` read permission and
`bixi-prod:*:naming/*` read/write permission for service registration. Supply
provider credentials before enabling AI, object storage, or external delivery
channels. Run `make doctor`
to prepare `ADMIN_PASSWORD_BCRYPT_B64`, or provide a precomputed bcrypt hash
encoded with base64. `make doctor` checks cloud Workflow requirements, so for
single Workflow with Rabbit disabled, prepare the hash before enabling Workflow
or supply it precomputed. The release commands reject placeholders and never
generate or rewrite credentials. After preparing the hash, remove
`ADMIN_PASSWORD` from `.env` and keep the matching password in a separate
password manager. The release starter only needs the hash; the acceptance
commands below need the password at run time. Restrict access to `.env`,
`.env.release`, and TLS private keys.

The cloud release mounts `deploy/native/nacos/*-prod.yml` and publishes it to
`bixi-prod`; single release sets `SPRING_PROFILES_ACTIVE=prod`. The Nacos
container remains bound to host loopback, as in the base Compose file. Do not
forward its port publicly. On the reference host, confirm that the runtime
account can read a published Data ID and register a service, but cannot publish
a disposable config Data ID or administer Nacos. Record the ACL and negative
results without credentials or access tokens.

## Start and verify

On the host, check out the exact frozen commit with a clean worktree and keep
the two ignored environment files beside it. Start one mode:

```sh
make start-cloud-release
# or, on a separate fresh installation:
make start-single-release
```

Use the certificate's actual hostname for acceptance. In a Bash shell, read
the matching administrator password from the password manager without writing
it to the release runtime `.env`, then run the three commands for the installed
mode:

```bash
read -rsp 'Administrator password: ' ADMIN_PASSWORD
printf '\n'
export ADMIN_PASSWORD
export BIXI_ORIGIN=https://bixi.example.com

# Cloud installation
make verify-cloud
make verify-security-cloud
make verify-negative-cloud

# Or, on the separate single installation
make verify-single
make verify-security-single
make verify-negative-single

unset ADMIN_PASSWORD BIXI_ORIGIN
```

The release command validates the manifest commit against clean `HEAD`, image
digest syntax, runtime secrets, HTTPS, the administrator hash, Compose version,
and merged Compose configuration before stopping or starting containers. It
also rejects an existing MySQL volume unless Docker records this frozen commit
on the volume. A development volume cannot be reused; a volume created by the
same frozen release can be redeployed without changing its data. Rejection does
not stop services or alter the volume. The command
applies `compose.release.yaml` last and uses `up --no-build`. It must not be
replaced by `make start-cloud` or `make start-single`, which build development
images. The localhost startup probes only establish local readiness; record
browser checks, effective image digests, and `docker compose ps` output with
the release evidence.

The first MySQL startup executes `01` through `04` SQL and then seeds runtime
credentials. MySQL becomes healthy only after the completion marker exists.
These SQL files are for a new, empty volume only. If first initialization
fails, investigate the SQL and container logs. Remove `bixi_mysql-data` only
after confirming it is a disposable volume from that failed new installation.
Never use `make reset` as a production recovery or redeploy step. Keep existing
volumes during same-version redeploy, and use a tested backup/restore decision
for any failed deployment involving real data.

This procedure has not yet been certified on the Ubuntu reference host. The
release ledger records the required install, browser, restore, and rollback
evidence before a `v1.0.0` tag or GitHub Release is allowed.
