import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const script = await readFile(new URL('./workflow-cluster-failover.mjs', import.meta.url), 'utf8');
const harness = await readFile(new URL('./test-workflow-cluster-failover.sh', import.meta.url), 'utf8');
const runtimeVerifier = await readFile(new URL('./verify-runtime-config.sh', import.meta.url), 'utf8');
const makefile = await readFile(new URL('../Makefile', import.meta.url), 'utf8');
const flowableRuntimePropertiesMigration = await readFile(
	new URL('../bixi-project-documents/sql/migrations/20260922_flowable_runtime_properties.sql', import.meta.url),
	'utf8'
).catch(() => '');

test('initializes LockSession before the top-level failover workflow can use it', () => {
	const classDeclaration = script.indexOf('class LockSession');
	const workflowStart = script.indexOf('await waitForServices()');

	assert.notEqual(classDeclaration, -1, 'LockSession declaration is missing');
	assert.notEqual(workflowStart, -1, 'failover workflow entry point is missing');
	assert.ok(
		classDeclaration < workflowStart,
		'LockSession remains in the temporal dead zone when lockTable first runs'
	);
});

test('runtime verifier registers static harness tests and performance defaults', () => {
	assert.match(runtimeVerifier, /scripts\/workflow-cluster-failover\.test\.mjs/);
	assert.match(runtimeVerifier, /scripts\/workflow-performance-metrics\.test\.mjs/);
	for (const configuredDefault of [
		'WORKFLOW_PERF_SAMPLE_SIZE:-12',
		'WORKFLOW_PERF_CONCURRENCY:-4',
		'WORKFLOW_PERF_WARMUP:-2',
		'WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS:-250',
	]) assert.match(runtimeVerifier, new RegExp(configuredDefault));
	assert.match(runtimeVerifier, /grep -F "\$\{performance_default\}" "\$\{ROOT\}\/scripts\/test-workflow-cluster-failover\.sh"/);
});

test('Makefile provides a static cluster gate before the Docker failover target', () => {
	assert.match(makefile, /\.PHONY:[^\n]*workflow-cluster-static-test/);
	const staticTarget = makefile.match(/workflow-cluster-static-test:\n([\s\S]*?)(?=\n[^\t\n][^\n]*:|$)/)?.[1] || '';
	assert.match(staticTarget, /node --test scripts\/workflow-cluster-failover\.test\.mjs scripts\/workflow-performance-metrics\.test\.mjs/);
	assert.match(staticTarget, /node --check scripts\/workflow-cluster-failover\.mjs/);
	assert.match(staticTarget, /node --check scripts\/workflow-performance-metrics\.mjs/);
	assert.match(staticTarget, /bash -n scripts\/test-workflow-cluster-failover\.sh scripts\/migrate-workflow-schema\.sh/);
	assert.match(makefile, /workflow-cluster-failover-test:\s+workflow-cluster-static-test/);

	const runtimeTarget = makefile.match(/runtime-config-check:\n([\s\S]*?)(?=\n[^\t\n][^\n]*:|$)/)?.[1] || '';
	assert.doesNotMatch(runtimeTarget, /\bnode\b/);
});

test('uses unbuffered MySQL output so lock markers are observable immediately', () => {
	assert.ok(
		/exec mysql[^\n]+--unbuffered/.test(script),
		'long-lived mysql client can buffer the marker after the table lock is already held'
	);
});

test('diagnostics sends bearer tokens over stdin and never includes authToken in argv', () => {
	const diagnostics = script.match(/async function diagnosticsWithToken\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(diagnostics, /composeExecWithInput\(/, 'diagnostics must use the stdin-capable compose helper');
	assert.match(diagnostics, /authToken/, 'diagnostics must provide the requested bearer token');
	const argv = diagnostics.match(/composeExecWithInput\(service,\s*(\[[\s\S]*?\]),/)?.[1] || '';
	assert.ok(argv, 'diagnostics compose argv is missing');
	assert.equal(argv.includes('authToken'), false,
		'bearer token must not be present in Docker or shell argv');
});

test('diagnostics failures identify the replica, HTTP status, and sanitized response body', () => {
	const diagnostics = script.match(/async function diagnosticsWithToken\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(diagnostics, /%\{http_code\}/, 'diagnostics must capture the HTTP status without discarding the body');
	assert.match(diagnostics, /service[^\n]+diagnostics HTTP/, 'the failure must identify the replica');
	assert.match(diagnostics, /sanitizeOutput\(responseBody\)/,
		'the response body must pass through the central secret sanitizer');
});

test('all login tokens are registered with the central sanitizer', () => {
	const login = script.match(/async function login\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(login, /secretSanitizer\.register\(response\.body\.access_token\)/);
	assert.match(script, /runtimeSecretValues/);
	assert.match(script, /secretSanitizer\.sanitize/);
});

test('cleanup diagnostics pass through the stdin sanitizer without secret paths in argv', () => {
	const sanitizer = harness.match(/sanitize_diagnostics\(\) \{[\s\S]*?\n\}/)?.[0] || '';
	const cleanup = harness.match(/cleanup\(\) \{[\s\S]*?\n\}/)?.[0] || '';
	assert.match(sanitizer, /BIXI_SANITIZER_ROOT="\$\{ROOT\}"/);
	assert.match(sanitizer, /BIXI_SANITIZER_ENV_FILE="\$\{env_file\}"/);
	assert.match(sanitizer, /node scripts\/workflow-performance-metrics\.mjs sanitize-stdin/);
	assert.doesNotMatch(sanitizer, /node[^\n]*(?:\$\{ROOT\}|\$\{env_file\})/);
	const diagnostics = cleanup.split('\n').filter(line => /compose .*\b(?:ps|logs)\b/.test(line));
	assert.equal(diagnostics.length, 3);
	assert.equal(diagnostics.filter(line => line.includes('| sanitize_diagnostics')).length, 3,
		'ps and both log captures must be sanitized');
});

test('registers and releases a lock session when startup fails', () => {
	const lockTable = script.match(/async function lockTable\(table\) \{([\s\S]*?)\n\}/)?.[1] || '';
	const registered = lockTable.indexOf('activeLocks.add(session)');
	const started = lockTable.indexOf('await session.start()');

	assert.ok(registered >= 0 && registered < started, 'session must be registered before startup can fail');
	assert.match(lockTable, /catch[\s\S]+await session\.release\(\)/, 'failed startup must release the child session');
});

test('rejects the child exit wait timeout so release can force-kill the process', () => {
	const onceExit = script.match(/function onceExit\(child, timeoutMillis\) \{([\s\S]*?)\n\}/)?.[1] || '';
	assert.match(onceExit, /setTimeout\(\(\) => \{[\s\S]*?reject\(/, 'timeout must reject instead of looking like a clean exit');
});

test('command helpers use bounded child processes and Compose wait has a shorter timeout', () => {
	const run = script.match(/function run\(command, args[\s\S]*?\n\}/)?.[0] || '';
	const runForStatus = script.match(/function runForStatus\(command, args[\s\S]*?\n\}/)?.[0] || '';
	const restartOwner = script.match(/async function restartOwner\(service\) \{([\s\S]*?)\n\}/)?.[1] || '';

	assert.match(run, /runBoundedProcess\(/);
	assert.match(runForStatus, /runBoundedProcess\(/);
	assert.match(restartOwner, /'--wait-timeout',\s*'75'/);
	assert.match(restartOwner, /timeoutMillis:\s*90_000/);
});

test('timer failover blocks only the target executable-job insert', () => {
	assert.doesNotMatch(
		script,
		/lockTable\('ACT_RU_JOB'\)/,
		'an ACT_RU_JOB table lock also blocks Flowable before it can reset expired timer locks'
	);
	assert.match(
		script,
		/lockJobInsert\(pendingTimerJob\.id\)/,
		'timer failover must block the executable-job insert by the timer job ID'
	);
	assert.match(
		script,
		/SELECT ID_ FROM ACT_RU_JOB WHERE ID_=\$\{sql\(jobId\)\} FOR UPDATE/,
		'the targeted lock must use the ACT_RU_JOB primary-key gap instead of the whole table'
	);
});

test('graceful shutdown leaves one replica serving new workflow starts', () => {
	const graceful = script.match(/async function runGracefulDrainProbe\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(graceful, /startOwnerStop\(job\.owner\)/,
		'the persisted in-flight owner must receive a graceful stop');
	assert.match(
		graceful,
		/snapshot => snapshot\.nacosHealthyInstances === 1/,
		'the stopped replica must disappear from Nacos before the probe'
	);
	assert.match(graceful, /reason: `stage3-graceful-survivor-\$\{project\}`/,
		'the survivor must process a fresh business start');
	assert.match(script, /report\.checks\.gracefulShutdown =/, 'the graceful-stop evidence must be persisted');
	assert.match(script, /report\.checks\.afterGracefulRestart =/, 'the stopped replica must rejoin the cluster');
});

test('graceful shutdown probe assigns an approver different from its administrator applicant', () => {
	const gracefulProbe = script.match(/async function runGracefulDrainProbe\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(gracefulProbe, /approverId: flowActorId/,
		'the administrator applicant must use the separate fault actor as approver');
	assert.doesNotMatch(gracefulProbe, /approverId:.*admin/,
		'the applicant cannot approve their own leave');
});

test('performance phase runs after v3 maintenance completion and before destructive failover', () => {
	const maintenanceComplete = script.indexOf('report.checks.maintenanceMigration =');
	const performance = script.indexOf('await runPerformancePhase(');
	const destructiveFailover = script.indexOf('reason: `stage3-failover-${project}`');

	assert.notEqual(performance, -1, 'performance phase is missing');
	assert.ok(maintenanceComplete < performance && performance < destructiveFailover,
		'performance must run on v3 after maintenance and before SIGKILL fault phases');
});

test('performance flows use request-scoped tokens and persist partial results on failure', () => {
	const phase = script.match(/async function runPerformancePhase\([\s\S]*?\n\}/)?.[0] || '';
	const flow = script.match(/async function runPerformanceFlow\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(flow, /apiWithToken\(flowAdminToken/, 'admin calls need an explicit request-scoped token');
	assert.match(flow, /apiWithToken\(actorToken/, 'actor calls need an explicit request-scoped token');
	assert.doesNotMatch(flow, /token\s*=/, 'concurrent flows cannot mutate the global token');
	assert.match(phase, /report\.checks\.performance\s*=/, 'partial performance evidence must be written before assertion');
	assert.match(phase, /failureCount === 0/, 'any measured flow failure must fail acceptance');
});

test('performance report captures environment, backlog samples, queues, and model binding', () => {
	assert.match(script, /async function captureEnvironmentEvidence\(/);
	assert.match(script, /host:\s*\{/);
	assert.match(script, /docker:\s*\{/);
	assert.match(script, /containers:/);
	assert.match(script, /jvm:/);
	assert.match(script, /mysql:/);
	assert.match(script, /rabbit:/);
	assert.match(script, /model:/);
	assert.match(script, /single-host local measurement/);
	assert.match(script, /async function captureBacklogSnapshot\(/);
	assert.match(script, /executableJobs/);
	assert.match(script, /timerJobs/);
	assert.match(script, /deadLetterJobs/);
	assert.match(script, /reliableOutbox/);
	assert.match(script, /reliableInbox/);
	assert.match(script, /rabbitQueues/);
	assert.match(script, /'--formatter',\s*'json',\s*'list_queues'/);
	assert.match(script, /parseRabbitQueueJson\(output\)/);
});

test('performance environment captures Gateway and UPMS container resources', () => {
	const capture = script.match(/async function captureEnvironmentEvidence\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(capture, /containerEvidence\('gateway'\)/);
	assert.match(capture, /containerEvidence\('upms'\)/);
	assert.match(capture, /containers:\s*\{[\s\S]*?gateway[\s\S]*?upms/);
});

test('performance environment bounds Docker probe concurrency and names probe failures', () => {
	const capture = script.match(/async function captureEnvironmentEvidence\([\s\S]*?\n\}/)?.[0] || '';
	const probe = script.match(/async function captureEnvironmentProbe\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(capture, /mapWithConcurrency\(probes,\s*2,/,
		'environment collection must not fan out every Docker command at once');
	assert.match(capture, /captureEnvironmentProbe\(name, read\)/);
	assert.match(probe, /environment probe \$\{name\} failed/,
		'timeouts must identify the exact environment probe');
});

test('short-lived Node-side Docker commands share the low-load concurrency limiter', () => {
	assert.match(script, /createConcurrencyLimiter/);
	assert.match(script, /const dockerCommandLimiter = createConcurrencyLimiter\(2\)/);
	const run = script.match(/async function run\(command, args, options = \{\}\) \{[\s\S]*?\n\}/)?.[0] || '';
	assert.match(run, /command === 'docker'/);
	assert.match(run, /dockerCommandLimiter/);
	assert.match(run, /describeDockerCommand\(args\)/,
		'timeouts must identify the Docker operation without serializing its arguments');
});

test('replica state sampling uses one bulk Compose lookup and one bulk inspect', () => {
	const states = script.match(/async function workflowOwnerStates\(\) \{[\s\S]*?\n\}/)?.[0] || '';
	assert.match(states, /compose\('ps', '-a', '-q', 'workflow-a', 'workflow-b'\)/);
	assert.match(states, /docker\('inspect', '--format', '\{\{json \.\}\}', \.\.\.containerIds\)/);
	assert.doesNotMatch(states, /Promise\.all\(\['workflow-a', 'workflow-b'\]/,
		'replica sampling must not fan each owner out into separate Docker processes');
});

test('performance completion proves the Rabbit business-result outbox and inbox path', () => {
	const snapshot = script.match(/async function performanceFinalSnapshot\([\s\S]*?\n\}/)?.[0] || '';
	const flow = script.match(/async function runPerformanceFlow\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(snapshot, /WORKFLOW_BUSINESS_TASK_RESULT/);
	assert.match(snapshot, /businessResultOutboxDelivered/);
	assert.match(snapshot, /businessResultInboxProcessed/);
	assert.match(flow, /value\.businessResultOutboxDelivered > 0/);
	assert.match(flow, /value\.businessResultInboxProcessed > 0/);
});

test('performance phase records periodic replica observations without claiming unobserved continuity', () => {
	const phase = script.match(/async function runPerformancePhase\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(phase, /replicaContinuity/);
	assert.match(phase, /performanceReplicaSnapshot\(\)/);
	assert.match(phase, /evaluateReplicaContinuity\(/);
	assert.match(phase, /replicaSamples/);
	assert.doesNotMatch(phase, /throughout/);
});

test('graceful shutdown drains a persistently owned in-flight requestBusiness job', () => {
	const graceful = script.match(/async function runGracefulDrainProbe\([\s\S]*?\n\}/)?.[0] || '';

	assert.match(graceful, /lockTable\('reliable_outbox'\)/, 'drain probe needs a deterministic DB barrier');
	assert.match(graceful, /findJob\([^\n]+requestBusiness/, 'drain probe must identify the persisted requestBusiness job');
	assert.match(graceful, /startOwnerStop\(job\.owner\)/, 'the actual persistent lock owner must be stopped');
	assert.match(graceful, /observeStopStillRunning\(/, 'the stop must be observed blocked before barrier release');
	assert.match(graceful, /await outboxLock\.release\(\)/, 'the DB barrier must be released for transaction drain');
	assert.match(graceful, /await stop\.result/, 'the asynchronous stop result must be awaited');
	assert.match(graceful, /processStatus === 'completed'/);
	assert.match(graceful, /leaveStatus === 'APPROVED'/);
	assert.match(graceful, /bookingState === 'BOOKED'/);
	assert.match(graceful, /deadLetterCount === 0/);
});

test('graceful drain retains a fresh-start survivor probe before owner restart', () => {
	const graceful = script.match(/async function runGracefulDrainProbe\([\s\S]*?\n\}/)?.[0] || '';
	const stopped = graceful.indexOf('nacosHealthyInstances === 1');
	const survivorProbe = graceful.indexOf('stage3-graceful-survivor-${project}');
	const restart = graceful.indexOf('await restartOwner(owner)');

	assert.ok(stopped >= 0 && stopped < survivorProbe && survivorProbe < restart,
		'the survivor must accept a fresh start while the drained owner remains down');
	assert.match(graceful, /nacosHealthyInstances === 2/);
	assert.match(graceful, /workflowOwners\.workflowA === 'workflow-a'/);
	assert.match(graceful, /workflowOwners\.workflowB === 'workflow-b'/);
});

test('graceful drain is bound to v3 and preserves the historical top-level report contract', () => {
	const call = script.match(/runGracefulDrainProbe\(\{[\s\S]*?\}\)/)?.[0] || '';
	const graceful = script.match(/async function runGracefulDrainProbe\([\s\S]*?\n\}/)?.[0] || '';
	assert.match(call, /v3DefinitionId/);
	assert.match(graceful, /definitionBinding\(processInstanceId\)/);
	assert.match(graceful, /binding\.definitionId === v3DefinitionId/);
	assert.match(graceful, /definition:\s*binding/);
	for (const field of [
		'stoppedOwner', 'survivor', 'containerId', 'startedAt', 'finishedAt', 'durationMillis',
		'status', 'exitCode', 'oomKilled', 'graceful', 'nacosHealthyInstances', 'probeLeaveId',
		'probeProcessInstanceId', 'observedAt',
	]) assert.match(graceful, new RegExp(`\\b${field}(?:\\s*:|\\s*,)`), `missing gracefulShutdown.${field}`);
	assert.match(graceful, /probeStartedAt:/);
	assert.match(graceful, /probeFinishedAt:/);
	assert.match(graceful, /probeDurationMillis:/);
	assert.match(graceful, /drain:\s*\{/);
});

test('fault harness prepares one genuinely unapplied additive migration', () => {
	assert.match(harness, /DROP TABLE IF EXISTS wf_recovery_audit/, 'the disposable database must emulate the pre-audit schema');
	assert.match(
		harness,
		/DELETE FROM bixi_schema_migration WHERE migration_id='20260922_workflow_recovery_audit\.sql'/,
		'the deferred migration must be absent from the persistent migration ledger'
	);
	assert.match(
		harness,
		/BIXI_FAULT_DEFERRED_MIGRATION=.*20260922_workflow_recovery_audit\.sql/,
		'the runtime driver must receive the exact deferred migration path'
	);
});

test('controlled schema migration initializes Flowable relationship settings before replicas start', () => {
	assert.match(
		flowableRuntimePropertiesMigration,
		/cfg\.execution-related-entities-count[\s\S]*?'true'/i,
		'the controlled schema must initialize the default execution relationship-count setting'
	);
	assert.match(
		flowableRuntimePropertiesMigration,
		/cfg\.task-related-entities-count[\s\S]*?'true'/i,
		'the controlled schema must initialize the default task relationship-count setting'
	);
	assert.equal(
		(flowableRuntimePropertiesMigration.match(/where\s+not\s+exists/gi) || []).length,
		2,
		'the two Flowable settings must be safe to migrate after an earlier single-replica startup'
	);

	const migration = harness.indexOf('bash "${ROOT}/scripts/migrate-workflow-schema.sh"');
	const verification = harness.indexOf('\nverify_flowable_runtime_properties\n', migration);
	const replicaStartup = harness.indexOf("echo 'Building and starting the two fault-test Workflow replicas.'");
	assert.ok(
		migration >= 0 && verification > migration && replicaStartup > verification,
		'the harness must verify both stored settings after migration and before concurrent replica startup'
	);
	assert.match(harness, /cfg\.execution-related-entities-count/);
	assert.match(harness, /cfg\.task-related-entities-count/);
});

test('maintenance window preserves and completes a running v2 instance before v3 traffic', () => {
	const deployV2 = script.indexOf("await api('/admin/workflow/definition/deploy-demo'");
	const createOldInstance = script.indexOf('reason: `stage3-migration-${project}`');
	const rejectWhileRunning = script.indexOf('await expectControlledMigrationRejected()', createOldInstance);
	const stopFirstOwner = script.indexOf("await stopOwner('workflow-a')", rejectWhileRunning);
	const stopSecondOwner = script.indexOf("await stopOwner('workflow-b')", stopFirstOwner);
	const migrate = script.indexOf('await runControlledMigration()', stopSecondOwner);
	const restart = script.indexOf('await restartOwners()', migrate);
	const deployV3 = script.indexOf("await api('/admin/workflow/definition/deploy-demo/v3'", restart);
	const completeOldInstance = script.indexOf('stage3 v2 migration completion', deployV3);

	for (const [label, position] of Object.entries({
		deployV2, createOldInstance, rejectWhileRunning, stopFirstOwner, stopSecondOwner,
		migrate, restart, deployV3, completeOldInstance,
	})) assert.notEqual(position, -1, `${label} step is missing`);
	assert.ok(
		deployV2 < createOldInstance
			&& createOldInstance < rejectWhileRunning
			&& rejectWhileRunning < stopFirstOwner
			&& stopFirstOwner < stopSecondOwner
			&& stopSecondOwner < migrate
			&& migrate < restart
			&& restart < deployV3
			&& deployV3 < completeOldInstance,
		'the maintenance exercise must cross a stopped-cluster migration before the old v2 instance completes'
	);
	assert.match(script, /report\.checks\.maintenanceMigration =/, 'the maintenance evidence must be persisted');
});

test('rejected controlled migration proves the deferred schema remains unapplied', () => {
	const schemaState = script.match(
		/async function deferredMigrationSchemaState\(\) \{([\s\S]*?)\n\}/
	)?.[1] || '';
	const rejection = script.match(
		/async function expectControlledMigrationRejected\(\) \{([\s\S]*?)\n\}/
	)?.[1] || '';
	const before = rejection.indexOf('const schemaBefore = await deferredMigrationSchemaState()');
	const command = rejection.indexOf('await controlledMigrationCommand()');
	const after = rejection.indexOf('const schemaAfter = await deferredMigrationSchemaState()');

	assert.match(schemaState, /bixi_schema_migration/, 'schema state must query the migration ledger');
	assert.match(schemaState, /wf_recovery_audit/, 'schema state must query the deferred table');
	assert.ok(before >= 0 && before < command && command < after,
		'the rejected command must be bracketed by deferred schema snapshots');
	assert.match(rejection, /assertDeferredMigrationSchemaState\(schemaBefore, 0/,
		'the pre-command ledger and table counts must both be zero');
	assert.match(rejection, /assertDeferredMigrationSchemaState\(schemaAfter, 0/,
		'the post-command ledger and table counts must both be zero');
	assert.match(rejection, /schemaBefore,[\s\S]*schemaAfter,/,
		'both rejected-command schema snapshots must be persisted');
});

test('graceful owner stop rejects forced SIGKILL exit codes', () => {
	const stopOwner = script.match(/async function stoppedOwnerEvidence\(owner, container, startedAt\) \{([\s\S]*?)\n\}/)?.[1] || '';

	assert.match(stopOwner, /state\.ExitCode === 0\s*\|\|\s*state\.ExitCode === 143/,
		'graceful stop must allow only normal or SIGTERM Java exits');
	assert.match(stopOwner, /graceful:\s*true/,
		'stop evidence must explicitly record that the graceful-exit assertion passed');
	assert.doesNotMatch(stopOwner, /state\.ExitCode === 137/,
		'forced SIGKILL must never be accepted as graceful');
});

test('completed definition binding treats a literal MySQL NULL runtime ID as absent', () => {
	const definitionBinding = script.match(
		/async function definitionBinding\(processInstanceId\) \{([\s\S]*?)\n\}/
	)?.[1] || '';
	const nullableSource = script.match(/function nullableDefinitionId\(value\) \{[\s\S]*?\n\}/)?.[0];
	assert.ok(nullableSource, 'definition binding lacks a nullable MySQL ID normalizer');
	const nullableDefinitionId = Function(`"use strict"; return (${nullableSource});`)();
	const v2DefinitionId = 'demo_leave_approval:1:v2-id';

	assert.equal(nullableDefinitionId('NULL'), null, 'MySQL literal NULL must mean no runtime definition');
	assert.equal(nullableDefinitionId(null), null, 'already-normalized SQL null must remain null');
	assert.equal(nullableDefinitionId(v2DefinitionId), v2DefinitionId, 'a real definition ID must remain unchanged');
	assert.match(definitionBinding, /runtimeDefinitionId:\s*nullableDefinitionId\(rows\[0\]\[6\]\)/,
		'the nullable runtime definition field must use the normalizer');
	assert.match(definitionBinding, /historyDefinitionId:\s*nullableDefinitionId\(rows\[0\]\[7\]\)/,
		'the nullable history definition field must use the normalizer');
	assert.match(definitionBinding,
		/engineDefinitionIds\.length > 0 && engineDefinitionIds\.every\(id => id === binding\.definitionId\)/,
		'definition binding must retain the non-null engine/business consistency check');

	const completedEngineIds = [nullableDefinitionId('NULL'), nullableDefinitionId(v2DefinitionId)].filter(Boolean);
	assert.deepEqual(completedEngineIds, [v2DefinitionId]);
	assert.ok(completedEngineIds.every(id => id === v2DefinitionId),
		'a completed process must rely on its matching historic definition ID');
	const mismatchedEngineIds = [nullableDefinitionId('NULL'), nullableDefinitionId('demo_leave_approval:2:v3-id')].filter(Boolean);
	assert.equal(mismatchedEngineIds.every(id => id === v2DefinitionId), false,
		'a real historic/business definition mismatch must not be masked');
});
