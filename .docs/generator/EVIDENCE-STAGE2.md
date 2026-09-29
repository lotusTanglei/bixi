# Generator Stage 2 Evidence

Date: 2026-09-26

This document records evidence for individually accepted generator slices. It does not declare Phase 2 A, or the complete Phase 2 roadmap, finished.

## Tasks 1-3: Fixed Catalog, Shared Snapshot, And Single-Table Output

### Delivered contract

- A versioned `bixi-default-v1` classpath catalog provides 17 single-table API, biz, SQL and Vue artifacts without requiring seeded template rows. Parent-child rendering reuses the same catalog with explicit variant overrides.
- The strict loader rejects missing resources, duplicate output paths, absolute output paths and traversing resource paths. Catalog contents are immutable after startup.
- Preview, ZIP download and directory generation all use `GeneratorServiceImpl.renderBundle`. A deterministic SHA-256 template version covers the selected group, ordered template IDs, output paths and template source. Directory generation rejects stale preview versions.
- `GeneratorOutputPolicy` confines output to configured project-relative roots and rejects absolute paths, traversal, prefix collisions, symbolic links and normalized duplicate targets.
- `AtomicGeneratorWriter` preflights overwrite conflicts, stages the complete result, and restores overwritten files plus removes new files and temporary directories after an injected publish failure.
- The generated single-table contract includes a tenant-aware `BaseEntity`, separate create/update/query DTOs and VO, Bean Validation, pagination, details, CRUD transactions, independent view/add/edit/del/import/export permissions, write/export audit annotations, menu SQL, and Vue loading/error/empty/validation states.

### Executable evidence

- `BuiltInTemplateCatalogTest` loads the fixed snapshot and drives invalid catalogs through the same parser and path/resource validation used by the classpath source.
- `GeneratorServiceImplTest` proves clean-database fallback, exact preview publication, stale-version and duplicate-output rejection before disk writes, custom-group fail-closed behavior, and compiles every rendered Java source with the Java 17 compiler in an isolated temporary directory.
- `GeneratedSingleTableTemplateTest` asserts the full contract and produces the actual rendered API/list/form fixture. `scripts/test-generator-output.mjs` parses and compiles its TypeScript and Vue SFC script/template blocks.
- `GeneratorTemplateSnapshotTest`, `GeneratorOutputPolicyTest` and `AtomicGeneratorWriterTest` cover determinism, boundary validation, overwrite confirmation and rollback.

The invalid catalog and generated frontend tests were observed RED before the injectable loader and fixture producer existed, then GREEN after the minimal changes.

## Task 4: Parent-Child Transaction And Isolation Contract

### Delivered contract

- Parent-child rendering requires one complete relation and rejects missing metadata, self-reference, duplicate fields, conflicting class/output names, unsupported key types, parent keys other than the trusted `Long id`, child primary keys, and child framework-managed fields.
- The generated parent service is the only aggregate write boundary. It requires both parent and child write permissions, validates tenant/parent/child ownership before writes, and performs parent plus complete-child-set create, replace, and delete in one transaction.
- Replacement validates submitted child identities before mutation, performs logical deletion, clears the old child ID before reinsertion, and assigns the trusted parent relation and tenant values on the server.
- Generated SQL and UI include independent child add/edit/delete permissions. The parent-child form submits child rows and uses all-of authorization for aggregate write buttons.
- The generator configuration page exposes child table and relation fields, filters unsupported relationship fields, and prevents stale asynchronous field responses from overwriting a newer selection.
- `generator-ci` runs the Java fixture producer before the UI behavior and generated SFC/TypeScript consumers, and is part of `ci-gate`.

### TDD evidence

The tests were observed failing before implementation for these behaviors:

- only single-table artifacts rendered; incomplete and self-referential metadata was accepted;
- non-unique or client-forged relationship metadata was trusted;
- parent-only permission allowed child writes;
- cross-parent child identities were accepted;
- generated parent-child UI did not submit children;
- logical-delete replacement reused tombstoned child IDs;
- unsupported keys, managed child fields, duplicate metadata, and class-name collisions were accepted;
- a stale child-table response could overwrite the latest UI selection;
- generated Vue/API fixtures were absent from the Node syntax test;
- the specialist Node tests were absent from the standard CI graph.

### Verification

The final source was verified with Java 17:

```text
make generator-ci
  bixi-generator: 39 tests, 0 failures, 0 errors
  parent-child UI behavior: 4 tests, 0 failures
  generated Vue SFC/API syntax: 1 test, 0 failures

make frontend-ci
make architecture-check
make runtime-config-check
codegraph sync .
git diff --check
```

The executable generated-service test dynamically compiles the rendered API/biz sources, invokes the generated service through Spring transaction advice, and covers successful create/update/delete, complete replacement, permission all-of behavior, cross-tenant and cross-parent denial before writes, and rollback after parent or child failures. Its in-memory store models active rows and logical-delete tombstones; a mutation that removes `child.setId(null)` fails the replacement scenario.

Independent specification and code-quality reviews both approved the final Task 4 implementation. No commit or push was performed, and `.docs/.chiwen.state.json` was not changed.

## Task 5: Import/Export Delivery Contract

### Delivered contract

- The generated controller gives import and export independent permissions and stable write audit titles. Uploads are multipart-only and reject empty or oversized files before workbook parsing.
- The generated import service validates every row before mutation, reports bounded row errors, rejects duplicate keys both within the workbook and against existing data, and writes all accepted rows in one transaction.
- Export uses a dedicated projection. Sensitive fields are masked by generated mapping code and are not exposed through the persistence entity.
- The generated Vue API and list page expose import/export only through their matching permissions and handle file selection, upload errors, result feedback, loading, and download cleanup.
- Operation logging now sanitizes multipart, servlet, stream, binary, path, header, validation, cyclic, deep, and oversized argument structures before asynchronous JSON persistence. Multipart logging records bounded metadata only and never reads file content. Unsafe map keys cannot reintroduce absolute paths.

### TDD and review evidence

The generator tests were observed failing before the import/export templates and executable runtime fixture were implemented. A real aspect-to-listener audit regression then reproduced Jackson failing on `MockMultipartFile.inputStream`; the sanitizer made that path persist successfully without accessing multipart bytes or streams.

The final executable tests cover:

- independent controller permissions and real `@SysLog` publication/persistence;
- Bean Validation failures, bounded row errors, duplicate workbook/database keys, maximum row count, and transaction rollback;
- generated Java compilation and execution of the import service;
- masked export projection and generated Vue/TypeScript syntax;
- map-key filtering, path removal, cycle/depth/item bounds, and the exact 128-code-point filename-extension boundary.

### Verification

The final source was freshly verified with Java 17:

```text
make generator-ci
  bixi-generator: 43 tests, 0 failures, 0 errors
  bixi-common-log: 22 tests, 0 failures, 0 errors
  parent-child UI behavior: 4 tests, 0 failures
  generated parent-child frontend: 1 test, 0 failures
  generated import/export frontend: 1 test, 0 failures

make architecture-check
make runtime-config-check
make frontend-ci
git diff --check
```

All commands passed. The production frontend build emitted only the existing third-party `vform3-builds` eval warnings.

## Task 6: Fixed-Source Template Updates

### Delivered contract

- Remote checks and updates are disabled by default. Enabling requires an HTTPS standard-port base URL, an exact hostname allowlist, a full 40-character lowercase Git revision, and a configured lowercase SHA-256 of the manifest.
- The loader uses strict duplicate/unknown/trailing-token JSON checks, validates the full manifest before file downloads, caps packages at 64 files, the manifest at 64 KiB and each UTF-8 template at 1 MiB, verifies declared byte sizes and file digests, and rejects paths outside the pinned revision root.
- The HTTP downloader rejects redirects and non-200 responses and enforces limits both from `Content-Length` and while streaming a body with no declared length.
- `POST /template/online` requires `codegen_template_edit` and emits the stable “在线更新模板” audit event. The frontend uses the same edit permission and displays verified revision/digest prefixes rather than coercing the typed response to `[object Object]`.
- All files are verified before database mutation. Group, template and relation inserts share one transaction; the real H2 constraint-failure test proves rollback preserves the prior group. A generated-column unique constraint prevents two concurrent installs from creating the same active revision, while logical deletion still permits a later reinstall.
- [FIXED-SOURCE-UPDATES.md](FIXED-SOURCE-UPDATES.md) documents immutable publication, configuration, limits, failure recovery and migration. The example manifest deliberately contains placeholders rather than a fake enabled pin.

### TDD and verification evidence

Observed RED failures covered the previous GET/view/no-audit endpoint, enabled-by-default checks, unpinned downloader absence, missing transaction rollback dependency, frontend add permission/object rendering, oversized hard limits, the 512-vs-255 generator path mismatch, null host allowlists, and concurrent duplicate installation.

Fresh Java 17 verification after Tasks 1-6:

```text
make generator-ci
  bixi-generator: 61 tests, 0 failures, 0 errors
  parent-child UI behavior: 4 tests, 0 failures
  generated parent-child frontend: 1 test, 0 failures
  generated import/export frontend: 1 test, 0 failures
  generator mutation/fixed-source UI and SQL contracts: 5 tests, 0 failures
  generated single-table frontend: 1 test, 0 failures

make architecture-check
make runtime-config-check
make frontend-ci
```

All listed commands passed. The production frontend build emitted only the existing third-party `vform3-builds` eval warnings.

The first MySQL 8.4 runtime check for `20260924_generator_template_group_uniqueness.sql` was attempted but not
executed because the local Docker/OrbStack daemon socket was unavailable. The clean schema and migration shape were
covered by the Node contract at that point; the later real migration run is recorded below under Task 7.

The migration was subsequently run in a fresh disposable MySQL 8.4.3 container against a legacy `gen_group`
schema, rather than the clean initialization schema. The harness applies the migration twice and compares the full
column/index snapshot, then verifies that duplicate active names and an incompatible pre-existing generated column
fail before changing the schema or data:

```text
make generator-migration-test
  MySQL: 8.4.3
  Ran 3 tests in 11.261s
  OK
```

This closes the real pre-existing-schema migration item. The earlier unavailable-daemon note above is historical
context for the first attempt; it is superseded by this later run.

## Task 7 Prerequisite: Metadata Synchronization And Acceptance Ownership

### Delivered contract

- `POST /table/sync/{dsName}/{tableName}` delegates to one public transactional service boundary. It rejects a
  missing active configuration, updates only physical table comment/database type on the existing row, preserves its
  identity and generator/relation/audit configuration, and reconciles columns by normalized physical name. Matching
  columns retain their row/audit identity and user form/grid/query/dictionary/sort configuration while physical and
  derived type metadata is refreshed through explicit update assignments; nullable physical comments and derived
  package names can therefore clear stale values to SQL `NULL`. New columns are initialized and inserted, and only
  dropped column IDs are deleted. Initialization and false table/column update, delete, or insert results propagate
  through the same rollback boundary.
- `GET /table/config/{dsName}/{tableName}` performs an exact active-row lookup without importing metadata. It uses
  `codegen_table_view` and has no write audit annotation.
- `POST /table/import/{dsName}/{tableName}` accepts a validated ownership marker under `codegen_table_edit` with a
  write audit. Its public transactional service returns `{created, table}`, writes the marker only on a newly created
  row, returns an existing row without changing it, and rejects a concurrent unique-key collision so a foreign winner
  is never re-marked.
- Physical metadata lookup restores a caller's nested dynamic datasource selection with `poll()`.
- Generator acceptance first reads the exact configuration. An existing row marked `author=generator-acceptance` is
  accepted without mutation, while a pre-existing foreign row is rejected immediately. Only an absent row triggers
  the atomic import POST. Its returned row is checked again, so a foreign winner between GET and POST is rejected
  without a follow-up ownership `PUT`. Creation-time inference is absent, and the unavoidable read/import race cannot
  transfer ownership.

### TDD and executable evidence

The initial Java 17 focused run failed test compilation on the absent `findConfiguredTable`, `syncTable`, and metadata
snapshot contract. The reconciliation review RED run then produced four intended failures: persisted column identity
and user configuration were lost, false update/delete results were ignored, and inserts still used the wholesale
replacement failure contract. The explicit-import RED run failed compilation on the absent request/result/API
contract. The first behavioral Node suite failed because the import ownership helper did not exist. The follow-up
review RED run failed all four ownership sequence tests because the helper posted without the exact GET, while the
Java suite failed test compilation because no explicit nullable physical-metadata update contract existed.

Two mutation checks proved the critical regressions are observable:

- removing `@Transactional` made the H2 test retain `current physical comment` after the injected column failure;
- the extended H2 proof executes a matching-column update and dropped-column logical delete before an injected new
  column insert failure, then observes the table metadata, update, and delete all rolled back;
- replacing `DynamicDataSourceContextHolder.poll()` with `clear()` erased the test's outer datasource context.
- the H2 physical-metadata reload starts with non-null `field_comment` and `package_name` values, applies null metadata
  through the production update wrapper, and reloads both columns as SQL `NULL`;
- forcing the real table metadata update to return false raises the synchronization error before any column
  initialization or update/delete/insert call.

Initial prerequisite verification:

```text
make generator-ci
  bixi-generator: 79 tests, 0 failures, 0 errors
  Generator Node contracts: 16 tests, 0 failures
```

The prerequisite synchronization and ownership slice was subsequently exercised by the cloud runtime acceptance below.

## Task 7 Partial Runtime Evidence: Cloud Enabled And Generated Project Compilation

### Review corrections

The first acceptance-quality review found four important and two minor gaps. The implementation and executable
acceptance were corrected before recording runtime evidence:

- configuration initialization is a `POST` write operation under `codegen_table_edit` with `@SysLog`, rather than a
  mutating `GET` available to view-only users;
- exact configuration reload returns the complete field and group detail needed for repeatable marker-owned runs;
- audit URI matching distinguishes cloud paths from the `/admin` single context path;
- synchronization locks the configured table row before reading the column snapshot, preventing two transactions from
  inserting the same newly discovered column;
- the runtime acceptance checks the import audit generated by the table it created in the current run;
- the output-volume contract requires an exact writable mount and rejects a read-only `:ro` variant.

The concurrency regression uses two real H2 transactions: the second transaction waits for the configured-table row
lock and, after both transactions complete, the new physical field has one active configuration and `saveBatch` ran
once. The final focused and standard verification passed:

```text
focused Generator Java: 21 tests, 0 failures, 0 errors
focused Generator Node/frontend: 21 tests, 0 failures
H2 synchronization transaction/concurrency: 3 tests, 0 failures, 0 errors

make generator-ci
  bixi-generator: 83 tests, 0 failures, 0 errors
  all Generator Node and shell contracts passed

make architecture-check
make runtime-config-check
node --check scripts/generator-acceptance.mjs
git diff --check
```

### Cloud enabled runtime

The clean Compose project `bixi-phase2-clean` ran the current Generator image with MySQL, Redis, RabbitMQ, Nacos,
Gateway, Auth, UPMS and the shared frontend healthy. The default acceptance ran with no skip flags:

```text
node scripts/generator-acceptance.mjs \
  --mode cloud \
  --env-file target/phase2-runtime/clean.env

PASS
single table sys_public_param: 17 preview and ZIP artifacts
parent-child sys_dict/sys_dict_item: 21 preview and ZIP artifacts
directory publication, synchronization, XLSX export, anonymous denial,
permissions, menus, fixed template version and operation audits: PASS
artifact directory: target/generator-acceptance/cloud-oUjHnu
```

A second run used the previously unconfigured `biz_demo_task` table so the import path and its exact asynchronous audit
were exercised rather than skipped because of prior ownership:

```text
node scripts/generator-acceptance.mjs \
  --mode cloud \
  --env-file target/phase2-runtime/clean.env \
  --standalone-table biz_demo_task

PASS
audit title: 导入代码生成表
audit method: POST
audit request URI: /table/import/master/biz_demo_task
artifact directory: target/generator-acceptance/cloud-bTc74i
```

The Generator container ran as UID 10001 and wrote API, biz, SQL and Vue artifacts to the writable
`/data/generator-output` volume.

### Generated project compilation

The actual standalone and parent-child directory-publication artifacts from `cloud-bTc74i` were overlaid on an
isolated detached worktree containing the current uncommitted source snapshot. No generated source was edited. The
following fresh checks passed:

```text
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn -Pcloud -pl bixi-module/bixi-upms-biz -am -DskipTests package
  16 reactor modules: BUILD SUCCESS

JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  mvn -Psingle -pl bixi-module/bixi-upms-biz -am -DskipTests clean package
  16 reactor modules, full recompilation: BUILD SUCCESS

cd bixi-ui
npm ci
npm run lint:eslint
npm run build:prod
```

`npm ci` installed from the committed lock file, ESLint exited successfully without changing either generated bundle,
and the production build succeeded twice after transforming 2747 modules. The generated `publicParam` and
`dictAggregate` API/page chunks were present. Build output contained only the existing third-party
`vform3-builds` eval warnings and the existing large-chunk warning.

### Single enabled runtime

The same clean Compose project was switched through the standard `start-single` entry point. Only MySQL, Redis, the
single application and the shared frontend were required; Gateway, Auth, UPMS, Generator, Workflow and Nacos ran in
the single process rather than as separate services. The acceptance client now follows that topology explicitly:

- single health readiness checks only `/admin/actuator/health`;
- direct single authentication uses `/admin/oauth2/token`;
- cloud readiness and authentication paths remain unchanged.

The helper behavior is covered by `scripts/generator-acceptance-support.test.mjs`. Its 14 tests include the single
health/authentication paths, exact single audit URIs, bounded response/process handling and safe artifact names.

Three no-skip runtime runs passed:

```text
node scripts/generator-acceptance.mjs \
  --mode single \
  --env-file target/phase2-runtime/clean.env

PASS
artifact directory: target/generator-acceptance/single-9z4eFx

node scripts/generator-acceptance.mjs \
  --mode single \
  --env-file target/phase2-runtime/clean.env \
  --standalone-table sys_post

PASS
audit title: 导入代码生成表
audit method: POST
audit request URI: /admin/table/import/master/sys_post
artifact directory: target/generator-acceptance/single-NORxwD

# RabbitMQ was then stopped before this run.
node scripts/generator-acceptance.mjs \
  --mode single \
  --env-file target/phase2-runtime/clean.env

PASS
artifact directory: target/generator-acceptance/single-GolzHw
```

Every run verified 17 single-table and 21 parent-child preview/ZIP artifacts, server-side directory publication,
synchronization, XLSX export, anonymous rejection, permissions and menus, fixed template versions, stale-version
rejection and operation audits. The final run proves the single Generator path does not depend on RabbitMQ, Nacos,
Gateway or a separate Generator service.

Fresh verification after the single topology correction passed:

```text
make generator-ci
  bixi-generator: 83 tests, 0 failures, 0 errors
  Generator Node contracts: 31 tests, 0 failures

make architecture-check
make runtime-config-check
git diff --check
```

### Path-safety regression and isolated single JAR

The ZIP writer now canonicalizes the configured project root with `toRealPath()` before it computes entry names.
This keeps a project configured through a macOS `/tmp` symbolic-link spelling inside the project-relative namespace;
the resulting ZIP entries no longer contain host path components such as `../../../private/tmp`. The regression is
the `zipDownloadUsesProjectRelativeEntriesWhenProjectRootIsASymbolicLink` case in
`GeneratorServiceImplTest`.

Fresh Java 17 focused verification:

```text
mvn -pl bixi-module/bixi-generator -am \
  -Dtest=GeneratorServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
  GeneratorServiceImplTest: 11 tests, 0 failures, 0 errors
```

An isolated `-Psingle` JAR was also built from the current source snapshot and run with the same clean runtime
configuration. Its server-side generation path completed for both standalone and parent-child metadata, including
the 17/21 preview and ZIP artifact sets, directory publication, XLSX export, permission and audit assertions, and
anonymous denial. The reproducible artifact and acceptance output were:

```text
mvn -Psingle -pl bixi-single -am -DskipTests package
  22 modules: BUILD SUCCESS

JAR: /tmp/bixi-generator-runtime-036OMM/bixi-single/target/bixi-single.jar
acceptance: /tmp/bixi-generator-runtime-036OMM/acceptance-fixed-2
```

The JAR run required `SECURITY_ENCODE_KEY` to be supplied alongside `BIXI_ENCODE_KEY`; this is a runtime
configuration detail, not a code-path bypass. This evidence covers the single enabled Generator path only. It does
not cover generated-project CRUD execution, the cloud JAR topology, or either disabled-mode runtime matrix.

### Remaining Phase 2 A work

Task 7 is not complete. Remaining work is starting the generated project in cloud/single and exercising its
generated CRUD/permission/audit/import/export runtime assertions, plus the single-disabled/cloud-disabled runtime
matrices. The passing
cloud/single Generator acceptance and generated-source compilation above must not be used as evidence for those
still-open behaviors.

### Cloud enabled rerun (2026-09-26 22:42 +08:00)

After the Cloud Workflow profile was enabled and its controlled Flowable 7.1.0 schema migration completed, the
Generator acceptance was rerun against the same isolated runtime:

```text
BIXI_MODE=cloud BIXI_ENV_FILE=target/phase2-runtime/cloud-enabled.env \
  node scripts/generator-acceptance.mjs
```

The process exited with code 0. The direct Generator API was `http://127.0.0.1:29997`; the shared frontend remained
on `http://localhost:28080`. The run verified administrator permissions and anonymous rejection, standalone
`sys_public_param` metadata import/synchronization, 17 preview and ZIP entries, 9 XLSX entries, server-side
generation, stale-template-version rejection, and operation audit records. Parent-child `sys_dict` metadata produced
21 preview and ZIP entries and 9 XLSX entries with the same synchronization, generation, stale-version and audit
checks. The captured artifacts are under
`target/generator-acceptance/cloud-StNHXL`.

This rerun confirms Generator behavior while the enabled Workflow service is healthy; it does not close the remaining
generated-project CRUD runtime, disabled-mode matrices, AI provider/RAG runtime, or broader Phase 2 acceptance work.

### Generated-project import transaction rollback (2026-09-27 00:35 +08:00)

The isolated generated `SysPost` project was started from the single JAR on port `29994` with a separate
`SECURITY_ENCODE_KEY` (the same value as `BIXI_ENCODE_KEY`) and the isolated `bixi_gen_test` database. The multipart
fixture is preserved at `/private/tmp/generated-crud-inputs-29996/post-rollback-overlong.xlsx`; row 2 is valid and
row 3 has a 65-character `code`, exceeding the database column's 64-character limit while remaining inside the DTO's
512-character validation bound.

```text
POST http://127.0.0.1:29994/admin/publicParam/import
file: post-rollback-overlong.xlsx

HTTP 200
{"code":0,"data":{"success":false,"code":"WRITE_FAILED","totalRows":2,"importedRows":0,
 "errors":[{"rowNumber":3,"errors":["数据写入失败"]}]}}

SELECT COUNT(*) FROM sys_post WHERE code = 'RT-ROLLBACK-20260927';
0
```

The first row was absent before the request and remained absent after the second-row write failed, proving the
generated import service marks the transaction rollback-only. The corresponding audit record was persisted with title
`导入岗位信息表`, method `POST`, and URI `/admin/publicParam/import` (id `2103887020033294338`).

### Generated project single CRUD runtime (2026-09-27 04:12 +08:00)

The fixed generated project was rebuilt and started as an isolated single JAR on port `29995`, using database
`bixi_gen_fix_20260927`, Redis database `7`, context path `/admin`, and Generator enabled with Workflow, AI and
reliable Rabbit disabled. The generated menu SQL was applied to this disposable database and its rows were assigned to
the default tenant and administrator role so the generated page could be exercised through the same tenant and
permission filters as a deployed project. No repository schema or seed file was changed by this setup.

The real generated Controller path was `/admin/sysPost`; the earlier conflicting `/admin/publicParam` path was not
used. A black-box HTTP run exited with code `0` and verified:

- login, generated menu `/acceptance/sysPost/index`, all six generated permissions, and anonymous rejection;
- create, page, details, update, invalid update rejection, and delete;
- two-row XLSX import and duplicate-workbook rejection;
- XLSX export (3,779-byte OOXML response);
- persisted operation audits for create, update, import, export and delete.

The captured result was:

```text
route: /admin/sysPost
anonymous: rejected
crud: create, page, details, update, invalid-update-rejected, delete
import: 2 rows passed; duplicate workbook rejected
export: XLSX, 3779 bytes
audits:
  新增岗位信息表 POST /admin/sysPost
  修改岗位信息表 PUT /admin/sysPost
  导入岗位信息表 POST /admin/sysPost/import
  导出岗位信息表 GET /admin/sysPost/export
  删除岗位信息表 DELETE /admin/sysPost
```

This closes the single generated-project CRUD/permission/audit/import/export runtime slice. The generated project was
compiled from the repaired source before this run; a separate cloud generated-project runtime still remains to be
executed.

### Generated project Cloud CRUD runtime (2026-09-27 05:25 +08:00)

The repaired generated UPMS image `bixi-generated-cloud-upms-final:20260927` was started with the original Cloud
Compose application environment and registered through Nacos as `bixi-upms-biz`. The generated project was exercised
through the real Gateway at `http://127.0.0.1:29997`; no generated source was edited for this check. The standalone
route is `/admin/sysPost` and the parent-child route is `/admin/dictAggregate`.

The standalone black-box run verified login, anonymous rejection (HTTP 401), menu visibility, all six generated
permissions, create/page/details/update, missing-ID update rejection (HTTP 400), two-row XLSX import, duplicate
workbook rejection, XLSX export (3,764 bytes), delete, and five persisted operation audits. The captured result is:

```text
node /private/tmp/cloud-generated-crud.mjs
status: passed
route: /admin/sysPost
anonymous: HTTP 401 rejected
invalid update: HTTP 400 rejected
import: 2 rows imported; duplicate workbook VALIDATION_FAILED
export: XLSX, 3764 bytes
audits: 新增岗位信息表 / 修改岗位信息表 / 导入岗位信息表 / 导出岗位信息表 / 删除岗位信息表
audit request URIs: /sysPost, /sysPost/import (Gateway strips /admin before UPMS)
evidence: target/phase2-runtime/generated-project-cloud-crud-20260927.json
```

The parent-child black-box run temporarily applied the generated menu SQL to the disposable runtime data, assigned its
rows to tenant `1` and administrator role `1`, and removed those rows and cache entries after the run. It verified
anonymous rejection, the generated menu, all six parent permissions and three child permissions, creation of two
parents with 2/1 children, page/details, complete child replacement without reusing old child IDs, cross-parent child
ID rejection, invalid relationship-key rejection, aggregate deletion of parents and children, and three persisted
operation audits. The captured result is:

```text
node /private/tmp/cloud-generated-parent-child.mjs
status: passed
route: /admin/dictAggregate
children: create 2/1; replacement passed; old child IDs not reused
negative cases: cross-parent and invalid dictId both rejected (HTTP 200, API code 1)
audits: 新增字典表 / 修改字典表 / 删除字典表
audit request URI: /dictAggregate (Gateway strips /admin before UPMS)
evidence: target/phase2-runtime/generated-project-cloud-parent-child-crud-20260927.json
```

The historical note above that a separate Cloud generated-project runtime remained is superseded by this section. Both
Cloud generated-project CRUD slices now have real Gateway HTTP evidence; remaining Phase 2 work is tracked separately
for disabled-mode matrices, provider-specific AI runtime, and other roadmap exit conditions.

## Cross-Cutting Quartz Runtime Evidence (2026-09-27 06:03 +08:00)

The fresh Single runtime was rebuilt from the current source and exercised through the real `/admin/sys-job` HTTP
endpoints. Two concurrent `run-job` requests for a job that was absent from Quartz both returned HTTP 200/code 0;
the JDBC duplicate-key race is handled after Quartz wraps it as `JobPersistenceException`. A slow REST job received
two manual triggers and the fixture reported `maxActive=1`. An HTTP 500 REST job recorded attempts `1/3`, `2/3`
and `3/3`, with one shared `executionId`, `maxAttempts=3`, and the HTTP exception persisted on each record.

The repeatable runtime result is captured at
`target/phase2-runtime/quartz-runtime-20260927.json`. Focused Java 17 coverage is
`TaskUtilSchedulingSemanticsTest` with 7 tests, 0 failures and 0 errors, including the wrapped JDBC duplicate race
and delayed transaction-visibility case. Temporary fixture jobs and the fixture process were removed after capture.
