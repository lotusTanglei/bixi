# Quartz Stage 2 Evidence

Date: 2026-09-27 (initial slice recorded 2026-09-25)

This document records the accepted static and focused-test slice for Phase 2 C Quartz delivery. It does not claim that the full Phase 2 C roadmap or the cloud/single runtime matrix is complete.

## Delivered contract

- Task mutations use `SysJobMutationDTO`; callers cannot write scheduler status, execution status, tenant identity, timestamps, or other persistence-managed fields.
- Cron expressions, invocation targets, field lengths, task types, retry counts and retry intervals are validated before persistence.
- Java, Spring Bean, REST and JAR tasks execute synchronously inside the Quartz job. REST non-2xx responses, JAR non-zero exits and invocation exceptions are failures rather than early asynchronous success.
- `@DisallowConcurrentExecution` covers the real invocation duration. A two-thread Quartz scheduler regression proves a long-running task does not overlap.
- Misfire policies map to their documented Quartz instructions.
- A trigger supports zero to five retries with a one-to-300-second interval. All attempts share an `executionId`; history stores the attempt number, maximum attempts, trigger source and recovery marker. Final failure is rethrown to Quartz.
- Missing jobs fail query, run and delete requests explicitly; running jobs cannot be deleted.
- The Vue form exposes bounded retry controls, validates the active invocation target and submits only writable fields. The history dialog displays retry and node-recovery evidence and uses the backend permission names.
- Clean schemas and the guarded additive migration persist the retry/history fields and add task-timeline and execution-attempt indexes.

## Fresh verification

Executed with Java 17 on 2026-09-25:

```text
mvn -pl bixi-module/bixi-quartz -am test -DskipTests=false
  reactor: 12/12 modules SUCCESS
  bixi-quartz: 33 tests, 0 failures, 0 errors, 0 skipped

node scripts/test-quartz-ui.mjs
  6 tests, 0 failures

node scripts/test-quartz-schema.mjs
  2 tests, 0 failures

make frontend-ci
  Quartz UI: 6/6
  Quartz schema: 2/2
  ESLint: passed
  production build: passed

git diff --check
  passed
```

The frontend build emitted only the existing third-party `vform3-builds` eval warnings.

## Unverified runtime evidence

The original 2026-09-25 run could not reach the local Docker/OrbStack daemon. That historical
limitation is superseded for the migration checks below, but the following runtime items remain
unverified:

- cloud and single HTTP creation, pause, resume, immediate execution and history queries against the migrated schema;
- killing a scheduler node during a durable job and observing recovery from another node.

These remain Phase 2 C acceptance work. Static schema tests and H2/unit execution tests do not substitute for them.

## Additional verification (2026-09-27)

The following focused checks were run against the source-aligned Single process on
`127.0.0.1:29992` with a real administrator token. Temporary jobs were paused and deleted in
`finally` cleanup; no `phase2-audit-*` task remained after the run.

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home \
  mvn -pl bixi-module/bixi-quartz -am test -DskipTests=false
  bixi-quartz: 39 tests, 0 failures, 0 errors, 0 skipped

python3 scripts/test-quartz-migration.py
  MySQL 8.4.3; 3 tests, 0 failures
```

The real HTTP probe created a REST task whose local endpoint returned HTTP 500, started it,
manually triggered it, and read the persisted execution record. The observed record had
`status=1`, `triggerType=MANUAL`, and an exception containing `REST调用返回HTTP 500`; pause and
delete both returned HTTP 200/code 0. This confirms that a manually accepted trigger does not
turn an unsuccessful REST invocation into a successful execution record.

The startup reconciliation fix now scans `sys_job` with the explicit
`TenantContextHolder.isAllTenantsReadOnly()` mode while retaining the default tenant as the SQL
fallback. This prevents jobs belonging to tenants other than tenant 1 from being omitted during
startup and restores the prior tenant/read-only context afterward. `BixiInitQuartzJobTest` covers
the scan mode and context restoration; the focused test passes 3/3.

Long-running process recovery, retry backoff over a real scheduler, and a multi-node kill/recovery
run still require a dedicated enabled runtime matrix.

The 2026-09-27 architecture audit confirmed that the source has recovery-aware Quartz job
semantics (`requestRecovery=true`, recovery fire-instance propagation, and `RECOVERY` records),
but the current deployment shape cannot provide a Cloud Quartz node test: the Cloud Compose
profile has no `quartz` service and the Gateway has no Quartz job route; Single composes one
scheduler process. The existing SBA two-instance probe is for UPMS replicas and must not be
read as Quartz failover evidence. A real multi-node kill/recovery result therefore remains
unverified.
