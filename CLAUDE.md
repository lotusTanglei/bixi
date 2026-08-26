# CLAUDE.md

Claude Code must follow [AGENTS.md](AGENTS.md), which is the canonical AI development contract for this repository.

## Bixi Context

- Java 17, Spring Boot 3.4.1, MyBatis-Plus, Vue 3.5, TypeScript 5.6.
- `-Pcloud` is the default microservice build; `-Psingle` builds the aggregate application.
- Business code exists once under `bixi-module/*-biz` and is composed by both deployment modes.
- Cross-module consumers use transport-neutral `*-api/service` contracts. Feign and local classes are adapters selected by deployment mode.
- The standard full-stack sample is `demo/task`; preserve its database, CRUD, permission, operation-log, frontend, test, and dual-mode acceptance coverage when extending it.

## Required Checks

```bash
make architecture-check
make runtime-config-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
make verify-cloud
make verify-single
```

Use `codegraph explore` first when `.codegraph/` exists. Never edit `.docs/.chiwen.state.json` or commit `.env` and generated credentials.
