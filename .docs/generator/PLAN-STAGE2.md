# Generator Stage 2 Implementation Plan

> **For agentic workers:** Execute this plan inline with test-driven development. Do not commit or push automatically.

**Goal:** Deliver a clean-database generator that produces compileable single-table and parent-child CRUD for the shared cloud/single implementation, with safe fixed templates, import/export behavior, and executable acceptance evidence.

**Architecture:** Keep `GeneratorServiceImpl` as the single preview/ZIP/disk rendering path. Add a versioned classpath template catalog as the immutable default source, use database templates only for explicitly selected custom groups, and keep all disk publication behind `GeneratorOutputPolicy` and `AtomicGeneratorWriter`. Generated API contracts go to the configured `*-api` path; Mapper, Service, and Controller implementations go to the configured `*-biz` path; Vue and SQL remain shared.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Velocity, Vue 3/TypeScript, JUnit 5, AssertJ, Node.js acceptance scripts.

---

### Task 1: Restore A Fixed Default Catalog

**Files:**
- Create: `bixi-module/bixi-generator/src/main/resources/generator/default-v1/catalog.json`
- Create: `bixi-module/bixi-generator/src/main/resources/generator/default-v1/*`
- Create: `bixi-module/bixi-generator/src/main/java/com/lotus/bixi/generator/template/BuiltInTemplateCatalog.java`
- Test: `bixi-module/bixi-generator/src/test/java/com/lotus/bixi/generator/template/BuiltInTemplateCatalogTest.java`

- [x] Write a failing contract test that requires a fixed version and all backend/API/frontend/SQL artifacts.
- [x] Run the focused test and verify it fails because the catalog is absent.
- [x] Add the classpath catalog and strict loader; reject missing, duplicate, absolute, and traversing paths.
- [x] Run the test and verify it passes.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#tasks-1-3-fixed-catalog-shared-snapshot-and-single-table-output).

### Task 2: Make Preview And Generation Use The Same Catalog Snapshot

**Files:**
- Modify: `bixi-module/bixi-generator/src/main/java/com/lotus/bixi/generator/service/impl/GeneratorServiceImpl.java`
- Modify: `bixi-module/bixi-generator/src/main/java/com/lotus/bixi/generator/service/impl/GeneratorTemplateSnapshot.java`
- Test: `bixi-module/bixi-generator/src/test/java/com/lotus/bixi/generator/service/impl/GeneratorServiceImplTest.java`

- [x] Write failing tests for clean-database fallback, deterministic version, full artifact set, stale-version rejection, and duplicate output rejection.
- [x] Run the tests and verify each required behavior fails for the expected reason.
- [x] Add the minimal fallback and render validation without creating a second render path.
- [x] Run generator tests and keep output policy/writer tests green.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#tasks-1-3-fixed-catalog-shared-snapshot-and-single-table-output).

### Task 3: Validate Generated Single-Table Output

**Files:**
- Modify: default backend/API/Vue/SQL templates from Task 1.
- Create: `scripts/test-generator-output.mjs`
- Test: `bixi-module/bixi-generator/src/test/java/com/lotus/bixi/generator/template/GeneratedSingleTableContractTest.java`

- [x] Write failing assertions for DTO/VO validation, pagination/details, read/write permissions, write audit, tenant-aware entity, frontend loading/error/empty states, and import/export permissions.
- [x] Render a representative table and compile generated Java against the project classpath in an isolated temporary directory.
- [x] Fix only template defects exposed by the contract and compile checks.
- [x] Verify the generated Vue sources with the repository TypeScript and Vue SFC compiler dependencies.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#tasks-1-3-fixed-catalog-shared-snapshot-and-single-table-output).

### Task 4: Parent-Child Transaction And Isolation Contract

**Files:**
- Create: parent-child variants under `generator/default-v1/`.
- Modify: `GeneratorServiceImpl.java` data-model validation.
- Test: `GeneratedParentChildContractTest.java`.

- [x] Write failing tests for required relation metadata, child ownership checks, atomic create/update/delete, rollback, and child write permissions.
- [x] Reject incomplete or self-referential relation metadata before rendering.
- [x] Add transactional service templates that replace the complete child set only after validating ownership and relation keys.
- [x] Compile and run focused generated-service tests for rollback and cross-tenant/cross-parent denial.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#task-4-parent-child-transaction-and-isolation-contract).

### Task 5: Import/Export Delivery Contract

**Files:**
- Modify: generated DTO/service/controller templates.
- Create: generated import result contract template.
- Test: `GeneratedImportExportContractTest.java`.

- [x] Write failing tests for independent import/export permissions, Bean Validation error rows, duplicate-key handling, all-or-nothing writes, bounded upload size, and masked exports.
- [x] Add a transactional import service that validates all rows before saving and returns bounded row errors.
- [x] Add export projection annotations so sensitive fields are never serialized to the workbook.
- [x] Verify failure leaves the database unchanged and all writes emit audit records.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#task-5-importexport-delivery-contract).

### Task 6: Fixed-Source Template Updates

**Files:**
- Modify: `GenTemplateServiceImpl.java`, `GenTemplateController.java`, `BixiGeneratorDefaultProperties.java`.
- Create: a manifest DTO and downloader abstraction.
- Test: `GenTemplateServiceImplTest.java`, `GenTemplateControllerSecurityTest.java`.

- [x] Write failing tests for POST-only mutation, edit permission plus audit, HTTPS allowlist, pinned revision, manifest SHA-256 verification, bounded files, and transaction rollback.
- [x] Download every candidate into memory, validate the complete manifest, then insert group/templates/relations in one transaction.
- [x] Preserve the active group when any fetch, hash, parse, path, or database write fails.
- [x] Disable remote checks by default; expose source revision and manifest digest in the result.

Evidence: [EVIDENCE-STAGE2.md](EVIDENCE-STAGE2.md#task-6-fixed-source-template-updates).

### Task 7: Clean-Database And Four-Mode Acceptance

**Files:**
- Modify: `bixi-project-documents/sql/04_init_data.sql` and migrations only for stable catalog metadata/permissions.
- Modify: `scripts/acceptance.mjs`.
- Create: `scripts/generator-acceptance.mjs` and `.docs/generator/EVIDENCE-STAGE2.md`.

- [ ] Initialize an empty MySQL database with ordered SQL and assert the built-in catalog is usable without manual reseeding.
- [ ] Generate one table and one parent-child pair into a temporary worktree, compile cloud/single, build the shared frontend, and run CRUD/permission/audit/import/export assertions.
- [ ] Verify enabled and disabled behavior in single/cloud; disabled mode must expose no stale route, menu, task, or dependency.
- [ ] Run `make architecture-check`, `make runtime-config-check`, backend cloud/single CI, frontend CI, cloud/single verification, `codegraph sync .`, and `git diff --check`.
- [ ] Record exact commands, counts, failures, limitations, and artifact paths in the evidence document.

Completed prerequisite slice:

- [x] Make metadata synchronization reconcile an existing configuration and its columns in one transaction while
  preserving generator and per-column user settings; expose exact read-only inspection plus an audited atomic import
  ownership contract; replace timestamp-based ownership inference with a read-first ownership check whose conditional
  import result rejects a foreign concurrent winner without any recovery write.
