# Bixi Agent Guide

## Start Here

1. If `.codegraph/` exists, run `codegraph explore "<question or symbols>"` before text search or broad file reads.
2. Read `.docs/1_ARCHITECTURE.md` before changing module boundaries.
3. Read `.docs/5_AI_DEVELOPMENT.md` for task templates and the definition of done.
4. Do not edit `.docs/.chiwen.state.json`; it is user-maintained generated state.

## Project Shape

Bixi is a Java 17 and Vue 3 enterprise development platform with two deployment modes:

- `cloud`: Gateway, Auth, and UPMS run as separate applications with Nacos and Feign.
- `single`: Auth, UPMS, Generator, and Quartz run in one `bixi-single` process.

Both modes must share the same Controller, Service, Mapper, entity, SQL contract, and frontend implementation. Deployment entry points compose business modules; they must not contain copied business code.

## Module Map

| Path | Responsibility |
| --- | --- |
| `bixi-common/*` | Transport-neutral infrastructure. Never depend on a `*-biz` module. |
| `bixi-module/*-api` | Cross-module contracts, DTOs, and Feign adapters. Never depend on the corresponding `*-biz`. |
| `bixi-module/*-biz` | The unique business implementation used by both modes. |
| `bixi-auth` | Authorization server and token endpoints. |
| `bixi-gateway` | Cloud-only routing, filtering, and rate limiting. |
| `bixi-single` | Single-process composition root. No copied business implementation. |
| `bixi-ui` | Shared Vue 3 frontend; backend menus provide dynamic routes. |
| `bixi-project-documents/sql` | Ordered schema, data, constraint, and index initialization. |

## Dependency Rules

- Keep the direction `deployment entry -> business implementation -> API contract/common infrastructure`.
- Consumers depend on transport-neutral interfaces under `com.lotus.bixi.*.api.service`, never directly on `Remote*Service` Feign types.
- Cloud mode selects Feign adapters. Single mode selects local adapters. Do not add localhost Feign loopback.
- A new business domain belongs in one business module and must run unchanged in both modes.
- Run `make architecture-check` after any module, API, Feign, or dependency change.

## Standard Business Pattern

Use `bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo` and `bixi-ui/src/views/demo/task` as the first-stage reference implementation.

A complete CRUD change includes:

- Entity validation, Mapper, Service, Controller, pagination, and details.
- `@HasPermission` on reads and writes; `@SysLog` on every write.
- Table definition in `01_init_all_tables.sql`, menu/role data in `04_init_data.sql`, and query indexes in `03_add_indexes.sql`.
- Frontend API, page, form validation, button permissions, loading/error/empty states, and responsive layout.
- Focused backend tests plus the shared runtime flow in `scripts/acceptance.mjs` when behavior changes.

Permission names use snake case: `<domain>_<resource>_view|add|edit|del`. Frontend buttons use the same values through `v-auth`.

## Commands

```bash
make init-env
make doctor
make start-cloud
make verify-cloud
make start-single
make verify-single
make diagnose
make stop

make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
```

`make init-env` creates random local credentials in ignored `.env`. Do not commit `.env`, secrets, generated passwords, local indexes, build output, or personal absolute paths.

## Definition Of Done

- The change compiles and focused tests pass.
- Cloud and single use one business implementation.
- Permissions and audit logging are exercised, not only declared.
- Both `make verify-cloud` and `make verify-single` pass for shared behavior.
- Documentation and examples describe current commands and current limitations.
- `codegraph sync .` and `git diff --check` pass before handoff.
