#!/usr/bin/env node

import { createCipheriv, createHash, randomBytes, randomUUID } from 'node:crypto';
import { mkdir, readFile, realpath, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, relative, resolve } from 'node:path';
import { arch, cpus, platform, release, totalmem } from 'node:os';
import { spawn } from 'node:child_process';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { generateStrongPassword } from './acceptance-password.mjs';
import {
	createConcurrencyLimiter,
	createSecretSanitizer,
	describeDockerCommand,
	evaluateReplicaContinuity,
	isSensitiveEnvironmentName,
	mapWithConcurrency,
	mergeBacklogPeak,
	parsePerformanceConfig,
	parseRabbitQueueJson,
	parseWorkflowOwnerContainers,
	runBoundedProcess,
	summarizePerformance,
} from './workflow-performance-metrics.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const envFile = requiredEnv('BIXI_FAULT_ENV_FILE');
const project = requiredEnv('BIXI_FAULT_PROJECT');
const reportFile = requiredEnv('BIXI_FAULT_REPORT_FILE');
const deferredMigration = await resolveDeferredMigration(requiredEnv('BIXI_FAULT_DEFERRED_MIGRATION'));
const namespace = requiredEnv('NACOS_NAMESPACE');
const gatewayPort = requiredEnv('BIXI_FAULT_GATEWAY_PORT');
const fileEnv = await readEnv(envFile);
const env = { ...fileEnv, ...process.env };
const runtimeSecretValues = Object.entries(env)
	.filter(([name, value]) => isSensitiveEnvironmentName(name)
		&& value && value !== 'GENERATE_ON_FIRST_RUN')
	.map(([, value]) => value);
const secretSanitizer = createSecretSanitizer([...runtimeSecretValues, root, envFile]);
const performanceConfig = parsePerformanceConfig(env);
const dockerCommandLimiter = createConcurrencyLimiter(2);
const apiBase = `http://127.0.0.1:${gatewayPort}`;
const composeBase = [
	'compose', '--env-file', envFile, '--project-name', project,
	'-f', resolve(root, 'compose.yaml'),
	'-f', resolve(root, 'compose.workflow-cluster.yaml'),
	'-f', resolve(root, 'compose.workflow-fault-test.yaml'),
];
const profiles = ['--profile', 'cloud', '--profile', 'workflow-cluster'];
const report = {
	project,
	namespace,
	startedAt: new Date().toISOString(),
	lockConfiguration: {
		asyncJobLockTime: env.WORKFLOW_ASYNC_JOB_LOCK_TIME,
		timerLockTime: env.WORKFLOW_TIMER_LOCK_TIME,
		resetExpiredJobsInterval: env.WORKFLOW_RESET_EXPIRED_JOBS_INTERVAL,
	},
	checks: {},
};
const activeLocks = new Set();
let token;
let adminToken;
let actorId;

class LockSession {
	constructor(description, acquireStatement, releaseStatement) {
		this.description = description;
		this.acquireStatement = acquireStatement || `LOCK TABLES ${description} WRITE`;
		this.releaseStatement = releaseStatement || 'UNLOCK TABLES';
		this.child = null;
		this.buffer = '';
	}

	async start() {
		this.child = spawn('docker', [...composeBase, ...profiles, 'exec', '-T', 'mysql', 'sh', '-c', 'exec mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" --batch --skip-column-names --raw --unbuffered'], { cwd: root, stdio: ['pipe', 'pipe', 'pipe'] });
		this.child.stdout.on('data', chunk => { this.buffer += chunk.toString(); });
		this.child.stderr.on('data', chunk => { this.buffer += chunk.toString(); });
		const marker = `LOCK_ACQUIRED_${randomUUID()}`;
		this.child.stdin.write(`SET SESSION lock_wait_timeout=60; ${this.acquireStatement}; SELECT '${marker}';\n`);
		await this.waitMarker(marker, 70_000);
	}

	async release() {
		if (!this.child || this.child.exitCode !== null) return;
		const marker = `LOCK_RELEASED_${randomUUID()}`;
		this.child.stdin.write(`${this.releaseStatement}; SELECT '${marker}';\n`);
		await this.waitMarker(marker, 20_000).catch(() => undefined);
		this.child.stdin.end();
		try {
			await onceExit(this.child, 20_000);
		} catch {
			this.child.kill('SIGKILL');
			await onceExit(this.child, 5_000).catch(() => undefined);
		}
		this.child = null;
	}

	waitMarker(marker, timeoutMillis) {
		if (this.buffer.includes(marker)) return Promise.resolve();
		return new Promise((resolvePromise, reject) => {
			const started = Date.now();
			const timer = setInterval(() => {
				if (this.buffer.includes(marker)) {
					clearInterval(timer);
					resolvePromise();
				} else if (this.child?.exitCode !== null) {
					clearInterval(timer);
					reject(new Error(`MySQL lock session for ${this.description} exited: ${this.buffer.slice(-500)}`));
				} else if (Date.now() - started > timeoutMillis) {
					clearInterval(timer);
					reject(new Error(`timed out acquiring/releasing MySQL lock for ${this.description}`));
				}
			}, 100);
		});
	}
}

try {
	await waitForServices();
	const admin = await login(required('ADMIN_USERNAME'), required('ADMIN_PASSWORD'));
	adminToken = admin.token;
	token = adminToken;
	report.checks.initialCluster = await clusterSnapshot();
	assert(report.checks.initialCluster.workflowOwners.workflowA === 'workflow-a', 'workflow-a lock owner mismatch');
	assert(report.checks.initialCluster.workflowOwners.workflowB === 'workflow-b', 'workflow-b lock owner mismatch');
	assert(report.checks.initialCluster.nacosHealthyInstances === 2, 'Nacos must report two healthy Workflow instances');

	const adminUserInfo = await api('/admin/user/info');
	const adminUser = adminUserInfo.sysUser || adminUserInfo;
	assert(adminUser.id, 'administrator has no user ID for the graceful-stop probe', adminUserInfo);
	assert(adminUser.deptId, 'administrator has no department for the fault-test actor', adminUserInfo);
	const actor = await createActor(
		`wf-fault-${Date.now()}-${randomBytes(2).toString('hex')}`,
		adminUser.deptId
	);
	actorId = actor.id;

	const maintenanceStartedAt = Date.now();
	const v2Deployment = await api('/admin/workflow/definition/deploy-demo', { method: 'POST' });
	assert(v2Deployment?.processDefinitionId, 'v2 deployment did not return a process definition ID', v2Deployment);
	const maintenanceDraft = await api('/admin/demo/leave', {
		method: 'POST',
		body: {
			approverId: actor.id,
			startDate: '2026-09-28',
			endDate: '2026-09-29',
			reason: `stage3-migration-${project}`,
		},
	});
	const maintenanceLeaveId = String(maintenanceDraft.id);
	const maintenanceSubmitted = await api(`/admin/demo/leave/${maintenanceLeaveId}/submit`, { method: 'POST' });
	const maintenanceBound = await pollApi(
		() => api(`/admin/demo/leave/details/${maintenanceLeaveId}`),
		value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
		'v2 leave workflow binding',
		60_000
	);
	const maintenanceProcessInstanceId = String(maintenanceBound.processInstanceId);
	const maintenanceBusinessKey = String(maintenanceBound.businessKey);
	assert(String(maintenanceBound.applicantId) === String(adminUser.id)
		&& String(maintenanceBound.approverId) === actor.id,
		'maintenance leave ownership does not match the administrator and separate actor', maintenanceBound);
	assert(maintenanceBound.reason === `stage3-migration-${project}`,
		'maintenance leave reason changed before the review window', maintenanceBound);
	const v2BindingBeforeMigration = await definitionBinding(maintenanceProcessInstanceId);
	assert(v2BindingBeforeMigration.definitionId === String(v2Deployment.processDefinitionId),
		'old process did not bind to the explicitly deployed v2 definition', {
			deployment: v2Deployment,
			binding: v2BindingBeforeMigration,
		});
	assert(v2BindingBeforeMigration.processKey === 'demo_leave_approval'
		&& v2BindingBeforeMigration.version === Number(v2Deployment.version),
		'v2 engine definition key/version evidence does not match its deployment', {
			deployment: v2Deployment,
			binding: v2BindingBeforeMigration,
		});
	assert(v2BindingBeforeMigration.runtimeDefinitionId === v2BindingBeforeMigration.definitionId,
		'Flowable runtime execution is not bound to the recorded v2 definition', v2BindingBeforeMigration);
	assert(v2BindingBeforeMigration.resourceName?.endsWith('demo_leave_approval_v2.bpmn20.xml'),
		'old process definition does not use the v2 BPMN resource', v2BindingBeforeMigration);
	assert(v2Deployment.xmlResourceName === v2BindingBeforeMigration.resourceName,
		'v2 deployment and engine binding disagree on the BPMN resource', {
			deployment: v2Deployment,
			binding: v2BindingBeforeMigration,
		});

	token = actor.token;
	const maintenanceTodo = await poll(
		() => api('/admin/workflow/task/todo/page?current=1&size=100'),
		value => value.records?.some(item => String(item.processInstanceId) === maintenanceProcessInstanceId),
		'v2 human review task assignment',
		60_000
	);
	const maintenanceReviewTask = maintenanceTodo.records?.find(
		item => String(item.processInstanceId) === maintenanceProcessInstanceId
	);
	assert(maintenanceReviewTask?.taskId, 'v2 review task was not assigned to the separate actor', maintenanceTodo);

	token = adminToken;
	const migrationRejection = await expectControlledMigrationRejected();
	const workflowAStop = await stopOwner('workflow-a');
	const afterWorkflowAStop = await poll(
		() => nacosCount(),
		count => count === 1,
		'one healthy Workflow replica after stopping workflow-a',
		60_000
	);
	const workflowBStop = await stopOwner('workflow-b');
	const afterAllOwnersStopped = await poll(
		() => nacosCount(),
		count => count === 0,
		'zero healthy Workflow replicas for the maintenance window',
		60_000
	);
	const migrationApplied = await runControlledMigration();
	const ownersRestarted = await restartOwners();

	const v2BindingAfterRestart = await definitionBinding(maintenanceProcessInstanceId);
	assertSameDefinition(v2BindingBeforeMigration, v2BindingAfterRestart,
		'old process changed definition across the maintenance restart');
	const v3Deployment = await api('/admin/workflow/definition/deploy-demo/v3', { method: 'POST' });
	assert(v3Deployment?.processDefinitionId, 'v3 deployment did not return a process definition ID', v3Deployment);
	const definitions = await mysqlRows(
		"SELECT ID_, VERSION_, RESOURCE_NAME_ FROM ACT_RE_PROCDEF WHERE KEY_='demo_leave_approval' ORDER BY VERSION_"
	);
	const latestDefinition = definitions.at(-1);
	assert(latestDefinition?.[0] === String(v3Deployment.processDefinitionId),
		'v3 deployment is not the latest definition ID', { definitions, v3Deployment });
	assert(latestDefinition?.[2]?.endsWith('demo_leave_approval_v3.bpmn20.xml'),
		'v3 is not the latest deployed definition', definitions);
	assert(Number(latestDefinition?.[1]) > v2BindingBeforeMigration.version,
		'v3 definition version did not advance beyond v2', { definitions, v2BindingBeforeMigration });
	assert(definitions.some(row => row[0] === v2BindingBeforeMigration.definitionId),
		'the v2 definition disappeared after v3 deployment', definitions);
	const v2BindingAfterV3 = await definitionBinding(maintenanceProcessInstanceId);
	assertSameDefinition(v2BindingBeforeMigration, v2BindingAfterV3,
		'old process changed definition after v3 deployment');

	token = actor.token;
	const maintenanceCompletionStartedAt = Date.now();
	const maintenanceCompletionRequestId = randomUUID();
	await api('/admin/workflow/task/complete', {
		method: 'POST',
		body: {
			requestId: maintenanceCompletionRequestId,
			taskId: maintenanceReviewTask.taskId,
			approvalComment: 'stage3 v2 migration completion',
		},
	});
	const maintenanceFinal = await poll(
		() => finalSnapshot(maintenanceProcessInstanceId, maintenanceLeaveId, maintenanceBusinessKey),
		value => value.processStatus === 'completed'
			&& value.leaveStatus === 'APPROVED'
			&& value.bookingState === 'BOOKED'
			&& value.deadLetterCount === 0,
		'v2 process completion after v3 deployment',
		90_000
	);
	const v2BindingAfterCompletion = await definitionBinding(maintenanceProcessInstanceId);
	assertSameDefinition(v2BindingBeforeMigration, v2BindingAfterCompletion,
		'completed old process did not retain its original v2 definition');
	report.checks.maintenanceMigration = {
		startedAt: new Date(maintenanceStartedAt).toISOString(),
		finishedAt: new Date().toISOString(),
		durationMillis: Date.now() - maintenanceStartedAt,
		deferredMigration: deferredMigration.relativePath,
		v2Deployment: pickDefinitionDeployment(v2Deployment),
		oldInstance: {
			applicantId: String(adminUser.id),
			approverId: actor.id,
			leaveId: maintenanceLeaveId,
			processInstanceId: maintenanceProcessInstanceId,
			businessKey: maintenanceBusinessKey,
			reviewTaskId: String(maintenanceReviewTask.taskId),
			submitStatus: maintenanceSubmitted.leaveStatus,
			bindingBeforeMigration: v2BindingBeforeMigration,
			bindingAfterRestart: v2BindingAfterRestart,
			bindingAfterV3Deployment: v2BindingAfterV3,
			bindingAfterCompletion: v2BindingAfterCompletion,
		},
		runningReplicaRejection: migrationRejection,
		ownerStops: {
			workflowA: workflowAStop,
			nacosHealthyAfterWorkflowA: afterWorkflowAStop,
			workflowB: workflowBStop,
			nacosHealthyAfterAllStopped: afterAllOwnersStopped,
		},
		controlledMigration: migrationApplied,
		restart: ownersRestarted,
		v3Deployment: pickDefinitionDeployment(v3Deployment),
		definitionsAfterV3: definitions.map(row => ({
			definitionId: row[0],
			version: Number(row[1]),
			resourceName: row[2],
		})),
		completion: {
			requestId: maintenanceCompletionRequestId,
			comment: 'stage3 v2 migration completion',
			startedAt: new Date(maintenanceCompletionStartedAt).toISOString(),
			finishedAt: new Date().toISOString(),
			durationMillis: Date.now() - maintenanceCompletionStartedAt,
			finalBusinessState: maintenanceFinal,
		},
	};
	report.checks.definitions = {
		count: definitions.length,
		latestDefinitionId: latestDefinition?.[0],
		latestVersion: Number(latestDefinition?.[1]),
		latestResourceName: latestDefinition?.[2],
	};
	token = adminToken;
	await runPerformancePhase({
		adminToken,
		actorToken: actor.token,
		actorId: actor.id,
		adminUserId: String(adminUser.id),
		v3Deployment,
	});

	token = adminToken;
	const draft = await api('/admin/demo/leave', {
		method: 'POST',
		body: {
			approverId: actor.id,
			startDate: '2026-10-01',
			endDate: '2026-10-02',
			reason: `stage3-failover-${project}`,
		},
	});
	const leaveId = String(draft.id);
	const submitted = await api(`/admin/demo/leave/${leaveId}/submit`, { method: 'POST' });
	const bound = await pollApi(
		() => api(`/admin/demo/leave/details/${leaveId}`),
		value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
		'confirmed workflow binding',
		60_000
	);
	const processInstanceId = String(bound.processInstanceId);
	const businessKey = String(bound.businessKey);
	report.checks.leave = { leaveId, processInstanceId, businessKey, submitStatus: submitted.leaveStatus };
	await waitFor(
		async () => Number((await mysqlRows(`SELECT COUNT(*) FROM reliable_outbox WHERE aggregate_key=${sql(businessKey)} AND status <> 'DELIVERED'`))[0]?.[0] || 0) === 0,
		'reliable start command delivery',
		60_000
	);

	token = actor.token;
	const todo = await poll(
		() => api('/admin/workflow/task/todo/page?current=1&size=100'),
		value => value.records?.some(item => String(item.processInstanceId) === processInstanceId),
		'review task assignment',
		60_000
	);
	const reviewTask = todo.records?.find(item => String(item.processInstanceId) === processInstanceId);
	assert(reviewTask?.taskId, 'review task was not assigned to the created actor', todo);

	const outboxLock = await lockTable('reliable_outbox');
	let bookingLock;
	try {
		await api('/admin/workflow/task/complete', {
			method: 'POST',
			body: { requestId: randomUUID(), taskId: reviewTask.taskId, approvalComment: 'stage3 async failover' },
		});
		const asyncJob = await poll(
			() => findJob(processInstanceId, 'requestBusiness'),
			value => value && ['workflow-a', 'workflow-b'].includes(value.owner),
			'locked requestBusiness job',
			45_000
		);
		const asyncHolder = asyncJob.owner;
		const asyncSurvivor = otherOwner(asyncHolder);
		const asyncKillAt = Date.now();
		const killedAsyncContainer = await killVerifiedOwner(asyncHolder);
		await waitForContainerExit(killedAsyncContainer, asyncHolder);
		const asyncTakeover = await poll(
			() => findJobById(asyncJob.id),
			value => value?.owner === asyncSurvivor,
			'async job lock takeover',
			45_000
		);
		report.checks.asyncJobFailover = {
			jobId: asyncJob.id,
			killedOwner: asyncHolder,
			survivor: asyncSurvivor,
			originalLockExpiry: asyncJob.lockExpiry,
			takeoverLockExpiry: asyncTakeover.lockExpiry,
			takeoverMillis: Date.now() - asyncKillAt,
		};

		// Hold the booking aggregate before releasing the first barrier. The UPMS
		// handler then persists an in-flight receipt while the process remains at
		// waitBusinessResult, making the timer window deterministic.
		bookingLock = await lockTable('demo_leave_booking');
		await outboxLock.release();
		activeLocks.delete(outboxLock);
		const pendingTimerJob = await poll(
			() => findTimerJob(processInstanceId, 'businessTimeout'),
			value => value !== null,
			'businessTimeout timer job',
			45_000
		);

		await restartOwner(asyncHolder);
		const restoredAfterAsync = await poll(clusterSnapshot, snapshot =>
			snapshot.nacosHealthyInstances === 2
				&& snapshot.workflowOwners.workflowA === 'workflow-a'
				&& snapshot.workflowOwners.workflowB === 'workflow-b',
			'restarted async owner registration',
			90_000
		);
		report.checks.afterAsyncOwnerRestart = restoredAfterAsync;

		const jobInsertLock = await lockJobInsert(pendingTimerJob.id);
		try {
			const affected = await mysql(
			`UPDATE ACT_RU_TIMER_JOB SET DUEDATE_=UTC_TIMESTAMP(3) WHERE PROCESS_INSTANCE_ID_=${sql(processInstanceId)} AND ELEMENT_ID_='businessTimeout' AND LOCK_OWNER_ IS NULL; SELECT ROW_COUNT()`
		);
		assert(Number(affected) === 1, `expected one timer row to accelerate, affected ${affected}`);
			const timerJob = await poll(
				() => findTimerJob(processInstanceId, 'businessTimeout'),
				value => value && ['workflow-a', 'workflow-b'].includes(value.owner),
				'locked businessTimeout timer job',
				45_000
			);
			const timerHolder = timerJob.owner;
			const timerSurvivor = otherOwner(timerHolder);
			const timerKillAt = Date.now();
			const killedTimerContainer = await killVerifiedOwner(timerHolder);
			await waitForContainerExit(killedTimerContainer, timerHolder);
			const timerTakeover = await poll(
				() => findTimerJob(processInstanceId, 'businessTimeout'),
				value => value?.owner === timerSurvivor,
				'timer job lock takeover',
				45_000
			);
			report.checks.timerJobFailover = {
				jobId: timerJob.id,
				killedOwner: timerHolder,
				survivor: timerSurvivor,
				originalLockExpiry: timerJob.lockExpiry,
				takeoverLockExpiry: timerTakeover.lockExpiry,
				takeoverMillis: Date.now() - timerKillAt,
			};
		} finally {
			await jobInsertLock.release();
			activeLocks.delete(jobInsertLock);
		}

		await poll(
			() => findExecution(processInstanceId, 'waitCompensationResult'),
			value => value > 0,
			'compensation wait after timer takeover',
			60_000
		);
		await restartOwner(report.checks.timerJobFailover.killedOwner);
		await poll(clusterSnapshot, snapshot => snapshot.nacosHealthyInstances === 2, 'second owner registration', 90_000);
	} finally {
		if (bookingLock) {
			await bookingLock.release();
			activeLocks.delete(bookingLock);
		}
	}

	const final = await poll(
		() => finalSnapshot(processInstanceId, leaveId, businessKey),
		value => value.processStatus === 'terminated' && value.leaveStatus === 'CANCELED' && value.bookingState === 'CANCELED',
		'final cancellation convergence',
		90_000
	);
	report.checks.final = final;
	token = adminToken;
	report.checks.gracefulShutdown = await runGracefulDrainProbe({
		adminToken,
		actorToken: actor.token,
		actorId: actor.id,
		v3DefinitionId: String(v3Deployment.processDefinitionId),
	});
	report.checks.afterGracefulRestart = report.checks.gracefulShutdown.afterRestart;
	report.checks.finalCluster = report.checks.afterGracefulRestart;
	report.status = 'PASSED';
} catch (error) {
	report.status = 'FAILED';
	report.error = sanitizeError(error);
	process.exitCode = 1;
} finally {
	for (const lock of [...activeLocks].reverse()) {
		await lock.release().catch(() => undefined);
	}
	if (adminToken && actorId) {
		token = adminToken;
		await api('/admin/user', { method: 'DELETE', body: [actorId] }).catch(() => undefined);
	}
	report.finishedAt = new Date().toISOString();
	await mkdir(dirname(reportFile), { recursive: true });
	const safeReport = secretSanitizer.sanitize(JSON.stringify(secretSanitizer.sanitizeValue(report), null, 2));
	await writeFile(reportFile, `${safeReport}\n`, 'utf8');
	console.log(safeReport);
}

async function runPerformancePhase({ adminToken: flowAdminToken, actorToken, actorId: flowActorId, adminUserId, v3Deployment }) {
	const evidence = {
		status: 'RUNNING',
		config: { ...performanceConfig },
		startedAt: new Date().toISOString(),
		environment: null,
		warmup: null,
		measured: null,
		backlog: null,
		replicaContinuity: null,
	};
	report.checks.performance = evidence;
	let sampler;
	let phaseError;
	try {
		evidence.environment = await captureEnvironmentEvidence(v3Deployment);
		const warmupFlows = await mapWithConcurrency(
			Array.from({ length: performanceConfig.warmup }, (_, index) => index),
			Math.min(performanceConfig.concurrency, performanceConfig.warmup),
			(_, index) => runPerformanceFlow({
				kind: 'warmup',
				index,
				adminToken: flowAdminToken,
				actorToken,
				actorId: flowActorId,
				adminUserId,
				v3DefinitionId: String(v3Deployment.processDefinitionId),
			})
		);
		evidence.warmup = {
			count: warmupFlows.length,
			successCount: warmupFlows.filter(flow => flow.success).length,
			failureCount: warmupFlows.filter(flow => !flow.success).length,
			flows: warmupFlows,
			excludedFromLatencyStatistics: true,
		};
		assert(evidence.warmup.failureCount === 0, 'performance warmup flow failed', evidence.warmup);

		const replicaBefore = await performanceReplicaSnapshot();
		assertTwoReplicaCluster(replicaBefore, 'pre-performance cluster');
		evidence.replicaContinuity = {
			before: replicaBefore,
			samples: [],
			after: null,
			observation: 'Replica health and identity are evaluated only at the recorded sampled intervals.',
		};
		const before = await captureBacklogSnapshot({ includeRabbitQueues: true });
		sampler = startPerformanceSampler(before, replicaBefore, performanceConfig.backlogSampleIntervalMillis);
		evidence.backlog = { before, peak: null, after: null, sampleCount: 1 };
		const batchStartedAt = Date.now();
		const measuredFlows = await mapWithConcurrency(
			Array.from({ length: performanceConfig.sampleSize }, (_, index) => index),
			performanceConfig.concurrency,
			(_, index) => runPerformanceFlow({
				kind: 'measured',
				index,
				adminToken: flowAdminToken,
				actorToken,
				actorId: flowActorId,
				adminUserId,
				v3DefinitionId: String(v3Deployment.processDefinitionId),
			})
		);
		const batchWallMillis = Date.now() - batchStartedAt;
		evidence.measured = {
			...summarizePerformance(measuredFlows, batchWallMillis),
			startedAt: new Date(batchStartedAt).toISOString(),
			finishedAt: new Date().toISOString(),
			flows: measuredFlows,
		};
		const businessKeys = measuredFlows.map(flow => flow.businessKey).filter(Boolean);
		const processInstanceIds = measuredFlows.map(flow => flow.processInstanceId).filter(Boolean);
		evidence.measured.finalReliableBacklog = await captureMeasuredReliableBacklog(businessKeys, processInstanceIds);
		assert(evidence.measured.failureCount === 0,
			'one or more measured performance flows failed', evidence.measured.flows.filter(flow => !flow.success));
		assert(evidence.measured.finalReliableBacklog.unresolved === 0,
			'measured flows retained unresolved reliable backlog', evidence.measured.finalReliableBacklog);
	} catch (error) {
		evidence.status = 'FAILED';
		evidence.error = sanitizeError(error);
		phaseError = error;
	} finally {
		if (sampler) {
			try {
				const sampled = await sampler.stop();
				evidence.backlog.peak = sampled.peak;
				evidence.backlog.sampleCount = sampled.sampleCount;
				const samplingFailed = sampled.errors.length > 0;
				if (sampled.errors.length > 0) {
					evidence.backlog.samplingErrors = sampled.errors;
				}
				evidence.backlog.after = await captureBacklogSnapshot({ includeRabbitQueues: true });
				const replicaAfter = await performanceReplicaSnapshot();
				const replicaSamples = [...sampled.replicaSamples, replicaAfter];
				const continuity = evaluateReplicaContinuity(replicaSamples);
				evidence.replicaContinuity = {
					...continuity,
					before: replicaSamples[0],
					samples: replicaSamples.slice(1, -1),
					after: replicaAfter,
				};
				if (samplingFailed) throw new Error('one or more performance backlog samples failed');
				assert(continuity.stable,
					'one or more sampled replica observations lost registration, health, or container identity', continuity);
			} catch (error) {
				evidence.backlog.captureError = sanitizeError(error);
				if (!phaseError) phaseError = error;
				evidence.status = 'FAILED';
				evidence.error ||= sanitizeError(error);
			}
		}
		evidence.finishedAt = new Date().toISOString();
		if (evidence.status === 'RUNNING') evidence.status = 'PASSED';
	}
	if (phaseError) throw phaseError;
}

async function runPerformanceFlow({ kind, index, adminToken: flowAdminToken, actorToken, actorId: flowActorId, adminUserId, v3DefinitionId }) {
	const startedAt = Date.now();
	let failureStage = 'create-request';
	const result = {
		kind,
		index,
		success: false,
		startedAt: new Date(startedAt).toISOString(),
		durations: {},
	};
	try {
		const createStartedAt = Date.now();
		const draft = await apiWithToken(flowAdminToken, '/admin/demo/leave', {
			method: 'POST',
			body: {
				approverId: flowActorId,
				startDate: '2026-10-10',
				endDate: '2026-10-11',
				reason: `stage3-performance-${kind}-${project}-${index}`,
			},
		});
		const createFinishedAt = Date.now();
		result.durations.createRequestMillis = createFinishedAt - createStartedAt;
		result.leaveId = String(draft.id);
		failureStage = 'submit-request';
		const submitted = await apiWithToken(flowAdminToken,
			`/admin/demo/leave/${result.leaveId}/submit`, { method: 'POST' });
		const submitFinishedAt = Date.now();
		result.durations.submitRequestMillis = submitFinishedAt - createFinishedAt;
		result.durations.createSubmitMillis = submitFinishedAt - createStartedAt;
		result.submitStatus = submitted.leaveStatus;

		failureStage = 'workflow-binding';
		const bound = await pollApi(
			() => apiWithToken(flowAdminToken, `/admin/demo/leave/details/${result.leaveId}`),
			value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
			`${kind} performance workflow binding ${index}`,
			60_000
		);
		result.processInstanceId = String(bound.processInstanceId);
		result.businessKey = String(bound.businessKey);
		assert(String(bound.applicantId) === adminUserId && String(bound.approverId) === flowActorId,
			'performance flow did not retain distinct applicant and approver', bound);

		failureStage = 'human-review';
		const todo = await poll(
			() => apiWithToken(actorToken, '/admin/workflow/task/todo/page?current=1&size=100'),
			value => value.records?.some(item => String(item.processInstanceId) === result.processInstanceId),
			`${kind} performance human review ${index}`,
			60_000
		);
		const reviewTask = todo.records.find(item => String(item.processInstanceId) === result.processInstanceId);
		result.reviewTaskId = String(reviewTask.taskId);
		result.durations.submitToReviewMillis = Date.now() - submitFinishedAt;

		failureStage = 'task-completion';
		const completeStartedAt = Date.now();
		await apiWithToken(actorToken, '/admin/workflow/task/complete', {
			method: 'POST',
			body: {
				requestId: randomUUID(),
				taskId: result.reviewTaskId,
				approvalComment: `stage3 performance ${kind} ${index}`,
			},
		});
		const completeFinishedAt = Date.now();
		result.durations.completeRequestMillis = completeFinishedAt - completeStartedAt;
		failureStage = 'final-business-state';
		result.finalState = await poll(
			() => performanceFinalSnapshot(result.processInstanceId, result.leaveId, result.businessKey),
			value => value.processStatus === 'completed'
				&& value.leaveStatus === 'APPROVED'
				&& value.bookingState === 'BOOKED'
				&& value.deadLetterCount === 0
				&& value.reliableUnresolved === 0
				&& value.businessResultOutboxDelivered > 0
				&& value.businessResultInboxProcessed > 0,
			`${kind} performance final business state ${index}`,
			90_000
		);
		result.durations.completionToFinalMillis = Date.now() - completeFinishedAt;
		failureStage = 'definition-binding';
		const binding = await definitionBinding(result.processInstanceId);
		assert(binding.definitionId === v3DefinitionId && binding.modelVersion === 'v3',
			'performance flow did not use the explicitly deployed v3 definition', binding);
		result.definition = binding;
		result.success = true;
	} catch (error) {
		result.failureStage = failureStage;
		result.error = sanitizeError(error);
	} finally {
		result.finishedAt = new Date().toISOString();
		result.durations.endToEndMillis = Date.now() - startedAt;
	}
	return result;
}

async function performanceFinalSnapshot(processInstanceId, leaveId, businessKey) {
	const rows = await mysqlRows(`
		SELECT
			(SELECT status FROM wf_process_instance WHERE process_instance_id=${sql(processInstanceId)} LIMIT 1),
			(SELECT leave_status FROM demo_leave_request WHERE id=${sql(leaveId)} LIMIT 1),
			(SELECT MAX(booking_state) FROM demo_leave_booking WHERE leave_id=${sql(leaveId)}),
			(SELECT COUNT(*) FROM ACT_RU_DEADLETTER_JOB WHERE PROCESS_INSTANCE_ID_=${sql(processInstanceId)}),
			(SELECT COUNT(*) FROM reliable_outbox WHERE aggregate_key=${sql(businessKey)} AND status IN ('PENDING','IN_FLIGHT','FAILED')),
			(SELECT COUNT(*) FROM reliable_inbox WHERE payload_json LIKE ${sql(`%${processInstanceId}%`)} AND status IN ('RECEIVED','IN_FLIGHT','FAILED')),
			(SELECT COUNT(*) FROM reliable_outbox WHERE type='WORKFLOW_BUSINESS_TASK_RESULT' AND aggregate_key=${sql(businessKey)} AND status='DELIVERED'),
			(SELECT COUNT(*) FROM reliable_inbox WHERE target_owner='workflow' AND type='WORKFLOW_BUSINESS_TASK_RESULT'
				AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.processInstanceId'))=${sql(processInstanceId)}
				AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessKey'))=${sql(businessKey)} AND status='PROCESSED')
	`);
	return {
		processStatus: rows[0]?.[0] || null,
		leaveStatus: rows[0]?.[1] || null,
		bookingState: rows[0]?.[2] || null,
		deadLetterCount: Number(rows[0]?.[3] || 0),
		reliableUnresolved: Number(rows[0]?.[4] || 0) + Number(rows[0]?.[5] || 0),
		businessResultOutboxDelivered: Number(rows[0]?.[6] || 0),
		businessResultInboxProcessed: Number(rows[0]?.[7] || 0),
		observedAt: new Date().toISOString(),
	};
}

async function captureBacklogSnapshot({ includeRabbitQueues = false } = {}) {
	const rows = await mysqlRows(`
		SELECT 'executableJobs', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(CREATE_TIME_), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM ACT_RU_JOB
		UNION ALL SELECT 'timerJobs', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(CREATE_TIME_), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM ACT_RU_TIMER_JOB
		UNION ALL SELECT 'deadLetterJobs', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(CREATE_TIME_), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM ACT_RU_DEADLETTER_JOB
		UNION ALL SELECT 'outboxPending', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(created_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_outbox WHERE status='PENDING'
		UNION ALL SELECT 'outboxInFlight', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(created_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_outbox WHERE status='IN_FLIGHT'
		UNION ALL SELECT 'outboxFailed', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(created_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_outbox WHERE status='FAILED'
		UNION ALL SELECT 'inboxPending', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(received_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_inbox WHERE status='RECEIVED'
		UNION ALL SELECT 'inboxInFlight', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(received_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_inbox WHERE status='IN_FLIGHT'
		UNION ALL SELECT 'inboxFailed', COUNT(*), COALESCE(TIMESTAMPDIFF(MICROSECOND, MIN(received_at), UTC_TIMESTAMP(6)) DIV 1000, 0) FROM reliable_inbox WHERE status='FAILED'
	`);
	const values = Object.fromEntries(rows.map(row => [row[0], {
		count: Number(row[1]),
		oldestWaitMillis: Number(row[2]),
	}]));
	return {
		flowable: {
			executableJobs: values.executableJobs,
			timerJobs: values.timerJobs,
			deadLetterJobs: values.deadLetterJobs,
		},
		reliableOutbox: {
			pending: values.outboxPending,
			inFlight: values.outboxInFlight,
			failed: values.outboxFailed,
		},
		reliableInbox: {
			pending: values.inboxPending,
			inFlight: values.inboxInFlight,
			failed: values.inboxFailed,
		},
		rabbitQueues: includeRabbitQueues ? await rabbitQueueSnapshot() : undefined,
		observedAt: new Date().toISOString(),
	};
}

function startPerformanceSampler(initialBacklog, initialReplica, intervalMillis) {
	let stopping = false;
	let peak = mergeBacklogPeak(null, initialBacklog);
	let sampleCount = 1;
	const replicaSamples = [initialReplica];
	const errors = [];
	const task = (async () => {
		while (!stopping) {
			await sleep(intervalMillis);
			if (stopping) break;
			try {
				const [backlog, replica] = await Promise.all([
					captureBacklogSnapshot({ includeRabbitQueues: true }),
					performanceReplicaSnapshot(),
				]);
				peak = mergeBacklogPeak(peak, backlog);
				replicaSamples.push(replica);
				sampleCount += 1;
			} catch (error) {
				errors.push(sanitizeError(error));
			}
		}
	})();
	return {
		async stop() {
			stopping = true;
			await task;
			return { peak, sampleCount, replicaSamples, errors };
		},
	};
}

async function captureMeasuredReliableBacklog(businessKeys, processInstanceIds) {
	if (businessKeys.length === 0 || processInstanceIds.length === 0) {
		return { outboxUnresolved: 0, inboxUnresolved: 0, unresolved: 0, observedAt: new Date().toISOString() };
	}
	const outboxFilter = businessKeys.map(sql).join(',');
	const inboxFilter = processInstanceIds.map(id => `payload_json LIKE ${sql(`%${id}%`)}`).join(' OR ');
	const rows = await mysqlRows(`
		SELECT
			(SELECT COUNT(*) FROM reliable_outbox WHERE aggregate_key IN (${outboxFilter}) AND status IN ('PENDING','IN_FLIGHT','FAILED')),
			(SELECT COUNT(*) FROM reliable_inbox WHERE (${inboxFilter}) AND status IN ('RECEIVED','IN_FLIGHT','FAILED'))
	`);
	const outboxUnresolved = Number(rows[0]?.[0] || 0);
	const inboxUnresolved = Number(rows[0]?.[1] || 0);
	return {
		outboxUnresolved,
		inboxUnresolved,
		unresolved: outboxUnresolved + inboxUnresolved,
		observedAt: new Date().toISOString(),
	};
}

async function rabbitQueueSnapshot() {
	const output = await composeExec('rabbitmq', [
		'rabbitmqctl', '-q', '--formatter', 'json', 'list_queues',
		'name', 'messages_ready', 'messages_unacknowledged',
	]);
	return parseRabbitQueueJson(output);
}

async function captureEnvironmentEvidence(v3Deployment) {
	const probes = [
		['dockerServer', async () => JSON.parse(await docker('version', '--format', '{{json .Server}}'))],
		['dockerInfo', async () => JSON.parse(await docker('info', '--format', '{{json .}}'))],
		['workflowA', () => containerEvidence('workflow-a')],
		['workflowB', () => containerEvidence('workflow-b')],
		['gateway', () => containerEvidence('gateway')],
		['upms', () => containerEvidence('upms')],
		['mysqlContainer', () => containerEvidence('mysql')],
		['rabbitContainer', () => containerEvidence('rabbitmq')],
		['jvmVersion', () => composeExec('workflow-a', ['sh', '-c', 'java -version 2>&1 | sed -n "1p"'])],
		['mysqlVersion', () => composeExec('mysql', ['mysql', '--version'])],
		['rabbitVersion', () => composeExec('rabbitmq', ['rabbitmq-diagnostics', '-q', 'version'])],
	];
	const captured = Object.fromEntries(await mapWithConcurrency(probes, 2,
		async ([name, read]) => [name, await captureEnvironmentProbe(name, read)]));
	const actualCluster = await captureEnvironmentProbe('actualCluster', clusterSnapshot);
	const {
		dockerServer, dockerInfo, workflowA, workflowB, gateway, upms,
		mysqlContainer, rabbitContainer, jvmVersion, mysqlVersion, rabbitVersion,
	} = captured;
	return {
		host: {
			os: platform(),
			release: release(),
			arch: arch(),
			cpuModel: cpus()[0]?.model || null,
			logicalCpuCount: cpus().length,
			totalMemoryBytes: totalmem(),
		},
		docker: {
			serverVersion: dockerServer.Version || dockerInfo.ServerVersion,
			operatingSystem: dockerInfo.OperatingSystem,
			arch: dockerInfo.Architecture,
			logicalCpuCount: dockerInfo.NCPU,
			totalMemoryBytes: dockerInfo.MemTotal,
		},
		containers: {
			workflowA,
			workflowB,
			gateway,
			upms,
			mysql: mysqlContainer,
			rabbit: rabbitContainer,
		},
		jvm: {
			version: jvmVersion,
			configuredHeap: parseHeapOptions(env.WORKFLOW_JAVA_OPTS),
		},
		mysql: { version: mysqlVersion },
		rabbit: { version: rabbitVersion },
		model: pickDefinitionDeployment(v3Deployment),
		actualWorkflowSettings: actualCluster.workflowDiagnostics,
		caveat: 'This is a single-host local measurement with two Workflow replicas sharing one MySQL; it is not a production capacity, database HA, cross-region HA, or cross-region failover claim.',
		observedAt: new Date().toISOString(),
	};
}

async function captureEnvironmentProbe(name, read) {
	try {
		return await read();
	} catch (error) {
		throw new Error(`environment probe ${name} failed: ${sanitizeError(error)}`);
	}
}

async function containerEvidence(service) {
	const id = await serviceContainer(service);
	const inspected = JSON.parse(await docker('inspect', id))[0];
	const nanoCpus = Number(inspected.HostConfig?.NanoCpus || 0);
	const memoryBytes = Number(inspected.HostConfig?.Memory || 0);
	return {
		image: inspected.Config?.Image || null,
		cpuLimitCores: nanoCpus > 0 ? nanoCpus / 1_000_000_000 : null,
		memoryLimitBytes: memoryBytes > 0 ? memoryBytes : null,
	};
}

function parseHeapOptions(javaOptions) {
	return {
		xms: String(javaOptions || '').match(/(?:^|\s)-Xms([^\s]+)/)?.[1] || null,
		xmx: String(javaOptions || '').match(/(?:^|\s)-Xmx([^\s]+)/)?.[1] || null,
	};
}

async function runGracefulDrainProbe({ adminToken: flowAdminToken, actorToken, actorId: flowActorId, v3DefinitionId }) {
	const startedAt = Date.now();
	const before = await clusterSnapshot();
	assert(before.nacosHealthyInstances === 2, 'pre-drain Nacos healthy instance count must be two');
	const draft = await apiWithToken(flowAdminToken, '/admin/demo/leave', {
		method: 'POST',
		body: {
			approverId: flowActorId,
			startDate: '2026-10-20',
			endDate: '2026-10-21',
			reason: `stage3-graceful-drain-${project}`,
		},
	});
	const leaveId = String(draft.id);
	await apiWithToken(flowAdminToken, `/admin/demo/leave/${leaveId}/submit`, { method: 'POST' });
	const bound = await pollApi(
		() => apiWithToken(flowAdminToken, `/admin/demo/leave/details/${leaveId}`),
		value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
		'graceful-drain workflow binding',
		60_000
	);
	const processInstanceId = String(bound.processInstanceId);
	const businessKey = String(bound.businessKey);
	const binding = await definitionBinding(processInstanceId);
	assert(binding.definitionId === v3DefinitionId && binding.modelVersion === 'v3',
		'graceful-drain process did not use the explicitly deployed v3 definition', binding);
	const todo = await poll(
		() => apiWithToken(actorToken, '/admin/workflow/task/todo/page?current=1&size=100'),
		value => value.records?.some(item => String(item.processInstanceId) === processInstanceId),
		'graceful-drain review task assignment',
		60_000
	);
	const task = todo.records.find(item => String(item.processInstanceId) === processInstanceId);
	const outboxLock = await lockTable('reliable_outbox');
	let stop;
	let stopEvidence;
	let stopObservation;
	let job;
	let barrierReleasedAt;
	try {
		await apiWithToken(actorToken, '/admin/workflow/task/complete', {
			method: 'POST',
			body: { requestId: randomUUID(), taskId: task.taskId, approvalComment: 'stage3 graceful drain' },
		});
		job = await poll(
			() => findJob(processInstanceId, 'requestBusiness'),
			value => value && ['workflow-a', 'workflow-b'].includes(value.owner),
			'persistently owned graceful-drain requestBusiness job',
			45_000
		);
		stop = await startOwnerStop(job.owner);
		stopObservation = await observeStopStillRunning(stop, job.id, job.owner, 1_500);
		barrierReleasedAt = Date.now();
		await outboxLock.release();
		activeLocks.delete(outboxLock);
		stopEvidence = await stop.result;
	} finally {
		await outboxLock.release().catch(() => undefined);
		activeLocks.delete(outboxLock);
		if (stop && !stopEvidence) stopEvidence = await stop.result.catch(() => undefined);
	}
	assert(stopEvidence?.graceful === true, 'graceful-drain owner did not exit cleanly', stopEvidence);
	const owner = job.owner;
	const survivor = otherOwner(owner);
	const singleReplica = await poll(
		async () => ({
			nacosHealthyInstances: await nacosCount(),
			survivorDiagnostics: await diagnosticsWithToken(survivor, flowAdminToken),
		}),
		snapshot => snapshot.nacosHealthyInstances === 1
			&& snapshot.survivorDiagnostics.lockOwner === survivor,
		'single healthy Workflow replica after graceful drain',
		60_000
	);
	const final = await poll(
		() => finalSnapshot(processInstanceId, leaveId, businessKey),
		value => value.processStatus === 'completed'
			&& value.leaveStatus === 'APPROVED'
			&& value.bookingState === 'BOOKED'
			&& value.deadLetterCount === 0,
		'graceful-drain final business state',
		90_000
	);

	const survivorDraft = await apiWithToken(flowAdminToken, '/admin/demo/leave', {
		method: 'POST',
		body: {
			approverId: flowActorId,
			startDate: '2026-10-22',
			endDate: '2026-10-23',
			reason: `stage3-graceful-survivor-${project}`,
		},
	});
	const survivorLeaveId = String(survivorDraft.id);
	await apiWithToken(flowAdminToken, `/admin/demo/leave/${survivorLeaveId}/submit`, { method: 'POST' });
	const survivorBound = await pollApi(
		() => apiWithToken(flowAdminToken, `/admin/demo/leave/details/${survivorLeaveId}`),
		value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
		'workflow start through graceful-drain survivor',
		60_000
	);
	await restartOwner(owner);
	const afterRestart = await poll(clusterSnapshot, snapshot =>
		snapshot.nacosHealthyInstances === 2
			&& snapshot.workflowOwners.workflowA === 'workflow-a'
			&& snapshot.workflowOwners.workflowB === 'workflow-b',
		'restarted graceful-drain owner registration',
		90_000
	);
	const probeFinishedAt = Date.now();
	return {
		stoppedOwner: owner,
		survivor,
		containerId: stopEvidence.containerId,
		startedAt: stopEvidence.startedAt,
		finishedAt: stopEvidence.finishedAt,
		durationMillis: stopEvidence.durationMillis,
		status: stopEvidence.status,
		exitCode: stopEvidence.exitCode,
		oomKilled: stopEvidence.oomKilled,
		graceful: stopEvidence.graceful,
		nacosHealthyInstances: singleReplica.nacosHealthyInstances,
		probeLeaveId: survivorLeaveId,
		probeProcessInstanceId: String(survivorBound.processInstanceId),
		observedAt: new Date().toISOString(),
		probeStartedAt: new Date(startedAt).toISOString(),
		probeFinishedAt: new Date(probeFinishedAt).toISOString(),
		probeDurationMillis: probeFinishedAt - startedAt,
		before,
		jobId: job.id,
		owner,
		definition: binding,
		originalLockExpiry: job.lockExpiry,
		barrier: {
			table: 'reliable_outbox',
			releasedAt: new Date(barrierReleasedAt).toISOString(),
		},
		stopObservation,
		stop: stopEvidence,
		drainWaitAfterBarrierMillis: Math.max(0, new Date(stopEvidence.finishedAt).getTime() - barrierReleasedAt),
		finalBusinessState: final,
		afterRestart,
		drain: {
			leaveId,
			processInstanceId,
			businessKey,
			definition: binding,
			jobId: job.id,
			owner,
			survivor,
			originalLockExpiry: job.lockExpiry,
			barrierReleasedAt: new Date(barrierReleasedAt).toISOString(),
			stopObservation,
			drainWaitAfterBarrierMillis: Math.max(0, new Date(stopEvidence.finishedAt).getTime() - barrierReleasedAt),
			finalBusinessState: final,
		},
	};
}

async function expectControlledMigrationRejected() {
	const startedAt = Date.now();
	const ownersBefore = await workflowOwnerStates();
	assertOwnersInState(ownersBefore, 'running', 'controlled migration guard precondition');
	const schemaBefore = await deferredMigrationSchemaState();
	assertDeferredMigrationSchemaState(schemaBefore, 0, 'running-replica guard precondition');
	const result = await controlledMigrationCommand();
	const schemaAfter = await deferredMigrationSchemaState();
	assertDeferredMigrationSchemaState(schemaAfter, 0, 'rejected controlled migration result');
	const diagnostic = 'Stop workflow/workflow-a/workflow-b before applying a schema migration.';
	const output = `${result.stdout}\n${result.stderr}`;
	assert(result.exitCode !== 0, 'controlled migration unexpectedly succeeded while Workflow replicas were running');
	assert(output.includes(diagnostic), 'controlled migration failed without the running-replica guard diagnostic', {
		exitCode: result.exitCode,
		output: sanitizeOutput(output),
	});
	const ownersAfter = await workflowOwnerStates();
	assertOwnersInState(ownersAfter, 'running', 'controlled migration guard result');
	return {
		startedAt: new Date(startedAt).toISOString(),
		finishedAt: new Date().toISOString(),
		durationMillis: Date.now() - startedAt,
		exitCode: result.exitCode,
		expectedDiagnostic: diagnostic,
		ownersBefore,
		ownersAfter,
		schemaBefore,
		schemaAfter,
	};
}

async function runControlledMigration() {
	const startedAt = Date.now();
	const ownersBefore = await workflowOwnerStates();
	assertOwnersInState(ownersBefore, 'exited', 'controlled migration precondition');
	assert(await nacosCount() === 0, 'Nacos still reports a healthy Workflow replica before migration');
	const result = await controlledMigrationCommand();
	assert(result.exitCode === 0, 'controlled migration entrypoint failed', {
		exitCode: result.exitCode,
		output: sanitizeOutput(`${result.stdout}\n${result.stderr}`),
	});
	const schemaAfter = await deferredMigrationSchemaState();
	assertDeferredMigrationSchemaState(schemaAfter, 1, 'controlled migration result');
	const ownersAfter = await workflowOwnerStates();
	assertOwnersInState(ownersAfter, 'exited', 'controlled migration verification');
	const nacosHealthyInstances = await nacosCount();
	assert(nacosHealthyInstances === 0, 'Workflow replica rejoined Nacos during migration verification', {
		nacosHealthyInstances,
	});
	return {
		startedAt: new Date(startedAt).toISOString(),
		finishedAt: new Date().toISOString(),
		durationMillis: Date.now() - startedAt,
		exitCode: result.exitCode,
		migrationId: schemaAfter.migrationId,
		ledgerCount: schemaAfter.ledgerCount,
		recoveryAuditTableCount: schemaAfter.recoveryAuditTableCount,
		schemaAfter,
		nacosHealthyInstances,
		ownersBefore,
		ownersAfter,
	};
}

async function deferredMigrationSchemaState() {
	const migrationId = basename(deferredMigration.relativePath);
	const [ledgerCount, recoveryAuditTableCount] = await Promise.all([
		mysql(`SELECT COUNT(*) FROM bixi_schema_migration WHERE migration_id=${sql(migrationId)}`),
		mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='wf_recovery_audit'"),
	]);
	return {
		migrationId,
		ledgerCount: Number(ledgerCount),
		recoveryAuditTableCount: Number(recoveryAuditTableCount),
		observedAt: new Date().toISOString(),
	};
}

function assertDeferredMigrationSchemaState(state, expectedCount, description) {
	assert(state.ledgerCount === expectedCount && state.recoveryAuditTableCount === expectedCount,
		`${description}: expected deferred migration ledger/table counts to equal ${expectedCount}`,
		state);
}

async function restartOwners() {
	const startedAt = Date.now();
	const ownersBefore = await workflowOwnerStates();
	assertOwnersInState(ownersBefore, 'exited', 'Workflow owner restart precondition');
	await restartOwner('workflow-a');
	const workflowAStartedAt = new Date().toISOString();
	await restartOwner('workflow-b');
	const workflowBStartedAt = new Date().toISOString();
	const cluster = await poll(clusterSnapshot, snapshot =>
		snapshot.nacosHealthyInstances === 2
			&& snapshot.workflowOwners.workflowA === 'workflow-a'
			&& snapshot.workflowOwners.workflowB === 'workflow-b',
		'restarted maintenance-window Workflow replicas',
		90_000
	);
	const ownersAfter = await workflowOwnerStates();
	assertOwnersInState(ownersAfter, 'running', 'Workflow owner restart result');
	return {
		startedAt: new Date(startedAt).toISOString(),
		finishedAt: new Date().toISOString(),
		durationMillis: Date.now() - startedAt,
		ownerStartedAt: {
			workflowA: workflowAStartedAt,
			workflowB: workflowBStartedAt,
		},
		ownersBefore,
		ownersAfter,
		cluster,
	};
}

async function controlledMigrationCommand() {
	return runForStatus('env', [
		`BIXI_ENV_FILE=${envFile}`,
		'bash',
		'scripts/migrate-workflow-schema.sh',
		deferredMigration.relativePath,
	]);
}

async function waitForServices() {
	for (const service of ['mysql', 'redis', 'rabbitmq', 'nacos', 'upms', 'auth', 'gateway', 'workflow-a', 'workflow-b']) {
		await poll(
			async () => (await docker('inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', await serviceContainer(service))).trim(),
			value => value === 'healthy' || (service === 'nacos' && value === 'healthy'),
			`${service} health`,
			120_000
		);
	}
}

async function clusterSnapshot() {
	const [workflowA, workflowB, nacosHealthyInstances] = await Promise.all([
		diagnostics('workflow-a'),
		diagnostics('workflow-b'),
		nacosCount(),
	]);
	return {
		workflowOwners: { workflowA: workflowA.lockOwner, workflowB: workflowB.lockOwner },
		workflowDiagnostics: {
			workflowA: pickDiagnostics(workflowA),
			workflowB: pickDiagnostics(workflowB),
		},
		nacosHealthyInstances,
		observedAt: new Date().toISOString(),
	};
}

async function performanceReplicaSnapshot() {
	const [cluster, states] = await Promise.all([clusterSnapshot(), workflowOwnerStates()]);
	return {
		...cluster,
		containers: {
			workflowA: states['workflow-a'],
			workflowB: states['workflow-b'],
		},
	};
}

function assertTwoReplicaCluster(snapshot, description) {
	assert(snapshot.nacosHealthyInstances === 2
		&& snapshot.workflowOwners.workflowA === 'workflow-a'
		&& snapshot.workflowOwners.workflowB === 'workflow-b',
	`${description} must contain both configured Workflow owners and two healthy Nacos instances`, snapshot);
}

function pickDiagnostics(value) {
	return {
		asyncJobLockTimeMillis: value.asyncJobLockTimeMillis,
		timerJobLockTimeMillis: value.timerJobLockTimeMillis,
		resetExpiredJobsIntervalMillis: value.resetExpiredJobsIntervalMillis,
		defaultAsyncJobAcquireWaitTimeMillis: value.defaultAsyncJobAcquireWaitTimeMillis,
		defaultTimerJobAcquireWaitTimeMillis: value.defaultTimerJobAcquireWaitTimeMillis,
		maxAsyncJobsDuePerAcquisition: value.maxAsyncJobsDuePerAcquisition,
		maxTimerJobsPerAcquisition: value.maxTimerJobsPerAcquisition,
		resetExpiredJobsPageSize: value.resetExpiredJobsPageSize,
		resetExpiredJobsEnabled: value.resetExpiredJobsEnabled,
		unlockOwnedJobs: value.unlockOwnedJobs,
		asyncExecutorActivate: value.asyncExecutorActivate,
		asyncExecutorAutoActivate: value.asyncExecutorAutoActivate,
		asyncExecutorActive: value.asyncExecutorActive,
	};
}

async function diagnostics(service) {
	return diagnosticsWithToken(service, token);
}

async function diagnosticsWithToken(service, authToken) {
	let output;
	try {
		output = await composeExecWithInput(service, [
			'sh', '-c', 'IFS= read -r bearer; curl -sS -w "\\n%{http_code}" -H "Authorization: Bearer ${bearer}" http://127.0.0.1:5008/workflow/recovery/diagnostics',
		], `${authToken}\n`);
	} catch (error) {
		throw new Error(`${service} diagnostics request failed: ${sanitizeError(error)}`);
	}
	const separator = output.lastIndexOf('\n');
	assert(separator >= 0, `${service} diagnostics response is missing its HTTP status`, sanitizeOutput(output));
	const responseBody = output.slice(0, separator).trim();
	const httpStatus = Number(output.slice(separator + 1).trim());
	assert(Number.isInteger(httpStatus) && httpStatus >= 200 && httpStatus < 300,
		`${service} diagnostics HTTP ${httpStatus}`, sanitizeOutput(responseBody));
	let body;
	try {
		body = JSON.parse(responseBody);
	} catch {
		throw new Error(`${service} diagnostics returned invalid JSON: ${sanitizeOutput(responseBody)}`);
	}
	assert(body.code === 0 && body.data?.lockOwner, `${service} diagnostics failed`, body);
	return body.data;
}

async function nacosCount() {
	const output = await composeExec('nacos', [
		'sh', '-c', 'curl -fsS --get http://127.0.0.1:8848/nacos/v1/ns/instance/list --data-urlencode "serviceName=bixi-workflow-biz" --data-urlencode "groupName=DEFAULT_GROUP" --data-urlencode "namespaceId=$1" --data-urlencode "healthyOnly=true"', 'sh', namespace,
	]);
	const body = JSON.parse(output);
	return Array.isArray(body.hosts) ? body.hosts.length : 0;
}

async function finalSnapshot(processInstanceId, leaveId, businessKey) {
	const [process, leave, booking, approvals, deadLetters, outbox, inbox] = await Promise.all([
		mysqlRows(`SELECT status FROM wf_process_instance WHERE process_instance_id=${sql(processInstanceId)}`),
		mysqlRows(`SELECT leave_status FROM demo_leave_request WHERE id=${sql(leaveId)}`),
		mysqlRows(`SELECT COUNT(*), MIN(booking_state), MAX(booking_state) FROM demo_leave_booking WHERE leave_id=${sql(leaveId)}`),
		mysqlRows(`SELECT COUNT(*) FROM wf_approval_record WHERE process_instance_id=${sql(processInstanceId)}`),
		mysqlRows(`SELECT COUNT(*) FROM ACT_RU_DEADLETTER_JOB WHERE PROCESS_INSTANCE_ID_=${sql(processInstanceId)}`),
		mysqlRows(`SELECT type, status, COUNT(*) FROM reliable_outbox WHERE aggregate_key=${sql(businessKey)} GROUP BY type, status ORDER BY type, status`),
		mysqlRows(`SELECT target_owner, type, status, COUNT(*) FROM reliable_inbox WHERE payload_json LIKE ${sql(`%${processInstanceId}%`)} GROUP BY target_owner, type, status ORDER BY target_owner, type, status`),
	]);
	return {
		processStatus: process[0]?.[0] || null,
		leaveStatus: leave[0]?.[0] || null,
		bookingCount: Number(booking[0]?.[0] || 0),
		bookingState: booking[0]?.[2] || null,
		approvalRecordCount: Number(approvals[0]?.[0] || 0),
		deadLetterCount: Number(deadLetters[0]?.[0] || 0),
		outbox,
		inbox,
		observedAt: new Date().toISOString(),
	};
}

async function definitionBinding(processInstanceId) {
	const rows = await mysqlRows(`
		SELECT w.process_definition_id, d.VERSION_, d.RESOURCE_NAME_, w.status, d.KEY_, d.NAME_,
			(SELECT MIN(e.PROC_DEF_ID_) FROM ACT_RU_EXECUTION e
			 WHERE e.PROC_INST_ID_=w.process_instance_id),
			(SELECT h.PROC_DEF_ID_ FROM ACT_HI_PROCINST h
			 WHERE h.PROC_INST_ID_=w.process_instance_id LIMIT 1)
		FROM wf_process_instance w
		JOIN ACT_RE_PROCDEF d ON d.ID_=w.process_definition_id
		WHERE w.process_instance_id=${sql(processInstanceId)}
	`);
	assert(rows.length === 1, `expected one definition binding for process ${processInstanceId}`, rows);
	const binding = {
		definitionId: rows[0][0],
		version: Number(rows[0][1]),
		resourceName: rows[0][2],
		processStatus: rows[0][3],
		processKey: rows[0][4],
		processName: rows[0][5],
		modelVersion: rows[0][2]?.endsWith('demo_leave_approval_v2.bpmn20.xml') ? 'v2'
			: rows[0][2]?.endsWith('demo_leave_approval_v3.bpmn20.xml') ? 'v3' : null,
		runtimeDefinitionId: nullableDefinitionId(rows[0][6]),
		historyDefinitionId: nullableDefinitionId(rows[0][7]),
	};
	const engineDefinitionIds = [binding.runtimeDefinitionId, binding.historyDefinitionId].filter(Boolean);
	assert(engineDefinitionIds.length > 0 && engineDefinitionIds.every(id => id === binding.definitionId),
		'Flowable engine and Workflow business records disagree on the process definition', binding);
	return binding;
}

function nullableDefinitionId(value) {
	return value === 'NULL' ? null : value;
}

function assertSameDefinition(expected, actual, message) {
	assert(actual.definitionId === expected.definitionId
		&& actual.version === expected.version
		&& actual.resourceName === expected.resourceName
		&& actual.processKey === expected.processKey,
	message,
	{ expected, actual });
}

function pickDefinitionDeployment(deployment) {
	return {
		processDefinitionId: String(deployment.processDefinitionId),
		processKey: deployment.processKey,
		processName: deployment.processName,
		version: Number(deployment.version),
		deploymentId: deployment.deploymentId,
		xmlResourceName: deployment.xmlResourceName,
	};
}

async function findJob(processInstanceId, elementId) {
	const rows = await mysqlRows(`SELECT ID_, COALESCE(LOCK_OWNER_, ''), DATE_FORMAT(LOCK_EXP_TIME_, '%Y-%m-%d %H:%i:%s.%f'), RETRIES_ FROM ACT_RU_JOB WHERE PROCESS_INSTANCE_ID_=${sql(processInstanceId)} AND ELEMENT_ID_=${sql(elementId)} ORDER BY ID_ LIMIT 1`);
	return rows[0] ? { id: rows[0][0], owner: rows[0][1] || null, lockExpiry: rows[0][2] || null, retries: Number(rows[0][3]) } : null;
}

async function findJobById(id) {
	const rows = await mysqlRows(`SELECT ID_, COALESCE(LOCK_OWNER_, ''), DATE_FORMAT(LOCK_EXP_TIME_, '%Y-%m-%d %H:%i:%s.%f'), RETRIES_ FROM ACT_RU_JOB WHERE ID_=${sql(id)}`);
	return rows[0] ? { id: rows[0][0], owner: rows[0][1] || null, lockExpiry: rows[0][2] || null, retries: Number(rows[0][3]) } : null;
}

async function findTimerJob(processInstanceId, elementId) {
	const rows = await mysqlRows(`SELECT ID_, COALESCE(LOCK_OWNER_, ''), DATE_FORMAT(LOCK_EXP_TIME_, '%Y-%m-%d %H:%i:%s.%f'), DATE_FORMAT(DUEDATE_, '%Y-%m-%d %H:%i:%s.%f') FROM ACT_RU_TIMER_JOB WHERE PROCESS_INSTANCE_ID_=${sql(processInstanceId)} AND ELEMENT_ID_=${sql(elementId)} ORDER BY ID_ LIMIT 1`);
	return rows[0] ? { id: rows[0][0], owner: rows[0][1] || null, lockExpiry: rows[0][2] || null, dueDate: rows[0][3] || null } : null;
}

async function findExecution(processInstanceId, activityId) {
	const rows = await mysqlRows(`SELECT COUNT(*) FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_=${sql(processInstanceId)} AND ACT_ID_=${sql(activityId)}`);
	return Number(rows[0]?.[0] || 0);
}

async function lockTable(table) {
	const session = new LockSession(table);
	activeLocks.add(session);
	try {
		await session.start();
		return session;
	} catch (error) {
		await session.release().catch(() => undefined);
		activeLocks.delete(session);
		throw error;
	}
}

async function lockJobInsert(jobId) {
	const session = new LockSession(
		`ACT_RU_JOB insert for ${jobId}`,
		`SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION; SELECT ID_ FROM ACT_RU_JOB WHERE ID_=${sql(jobId)} FOR UPDATE`,
		'ROLLBACK'
	);
	activeLocks.add(session);
	try {
		await session.start();
		return session;
	} catch (error) {
		await session.release().catch(() => undefined);
		activeLocks.delete(session);
		throw error;
	}
}

async function killVerifiedOwner(owner) {
	const container = await serviceContainer(owner);
	const labels = JSON.parse(await docker('inspect', '--format', '{{json .Config.Labels}}', container));
	assert(labels['com.docker.compose.project'] === project, `refusing to kill container from another project: ${container}`);
	assert(labels['com.docker.compose.service'] === owner, `refusing to kill unexpected service: ${container}`);
	await docker('kill', '--signal', 'KILL', container);
	return container;
}

async function stopOwner(owner) {
	const stop = await startOwnerStop(owner);
	return stop.result;
}

async function startOwnerStop(owner) {
	const startedAt = Date.now();
	const container = await serviceContainer(owner);
	const labels = JSON.parse(await docker('inspect', '--format', '{{json .Config.Labels}}', container));
	assert(labels['com.docker.compose.project'] === project, `refusing to stop container from another project: ${container}`);
	assert(labels['com.docker.compose.service'] === owner, `refusing to stop unexpected service: ${container}`);
	let settled = false;
	const result = (async () => {
		await compose('stop', '--timeout', '30', owner);
		await waitForContainerExit(container, owner);
		return stoppedOwnerEvidence(owner, container, startedAt);
	})();
	result.then(() => { settled = true; }, () => { settled = true; });
	return {
		owner,
		containerId: container,
		startedAt: new Date(startedAt).toISOString(),
		isSettled: () => settled,
		result,
	};
}

async function stoppedOwnerEvidence(owner, container, startedAt) {
	const state = JSON.parse(await docker('inspect', '--format', '{{json .State}}', container));
	const gracefulExit = state.ExitCode === 0 || state.ExitCode === 143;
	assert(state.Status === 'exited' && state.OOMKilled === false && !state.Error && gracefulExit,
		`${owner} did not stop cleanly`, state);
	return {
		containerId: container,
		startedAt: new Date(startedAt).toISOString(),
		finishedAt: state.FinishedAt,
		durationMillis: Date.now() - startedAt,
		status: state.Status,
		exitCode: state.ExitCode,
		oomKilled: state.OOMKilled,
		graceful: true,
	};
}

async function observeStopStillRunning(stop, jobId, owner, observationMillis) {
	const startedAt = Date.now();
	let lastJob;
	while (Date.now() - startedAt < observationMillis) {
		assert(!stop.isSettled(), `${owner} stopped before the in-flight drain barrier was released`);
		const state = JSON.parse(await docker('inspect', '--format', '{{json .State}}', stop.containerId));
		lastJob = await findJobById(jobId);
		assert(state.Running === true && lastJob?.owner === owner,
			`${owner} did not retain the persistently owned in-flight job during graceful stop`, { state, lastJob });
		await sleep(100);
	}
	return {
		observationMillis: Date.now() - startedAt,
		containerStillRunning: true,
		jobStillOwned: true,
		job: lastJob,
		observedAt: new Date().toISOString(),
	};
}

async function waitForContainerExit(container, service) {
	await poll(
		async () => (await docker('inspect', '--format', '{{.State.Status}}', container)).trim(),
		value => value === 'exited' || value === 'dead',
		`${service} container exit`,
		45_000
	);
}

async function restartOwner(service) {
	await composeWithOptions({ timeoutMillis: 90_000 },
		'up', '-d', '--no-build', '--wait', '--wait-timeout', '75', service);
}

async function workflowOwnerStates() {
	const result = await compose('ps', '-a', '-q', 'workflow-a', 'workflow-b');
	const containerIds = result.stdout.trim().split(/\s+/).filter(Boolean);
	assert(containerIds.length === 2, 'Compose must return both Workflow replica containers', containerIds);
	const output = await docker('inspect', '--format', '{{json .}}', ...containerIds);
	return parseWorkflowOwnerContainers(output, project);
}

function assertOwnersInState(states, expected, description) {
	for (const owner of ['workflow-a', 'workflow-b']) {
		assert(states[owner]?.status === expected,
			`${description}: ${owner} must be ${expected}`,
			states[owner]);
	}
}

async function serviceContainer(service) {
	const result = await compose('ps', '-q', service);
	const id = result.stdout.trim().split(/\s+/)[0];
	assert(id, `Compose did not return a container for ${service}`);
	return id;
}

async function createActor(username, deptId) {
	const password = generateStrongPassword();
	await api('/admin/user', { method: 'POST', body: { username, name: username, password, role: [1], post: [], deptId, lockFlag: '0' } });
	const users = await api(`/admin/user/page?current=1&size=20&username=${encodeURIComponent(username)}`);
	const user = users.records?.find(item => item.username === username);
	assert(user?.id, 'fault actor was not created', users);
	const logged = await login(username, password);
	return { id: String(user.id), token: logged.token };
}

async function login(username, password) {
	const response = await request('/auth/oauth2/token', {
		method: 'POST',
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${required('OAUTH_PASSWORD_CLIENT_SECRET')}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: new URLSearchParams({ username, password: encryptPassword(password, required('BIXI_ENCODE_KEY')), grant_type: 'password', scope: 'server' }).toString(),
	});
	assert(response.status === 200 && response.body?.access_token, 'login failed', { status: response.status });
	secretSanitizer.register(response.body.access_token);
	return { token: response.body.access_token };
}

async function api(path, options = {}) {
	return apiWithToken(token, path, options);
}

async function apiWithToken(authToken, path, options = {}) {
	const response = await request(path, {
		...options,
		headers: { Authorization: `Bearer ${authToken}`, ...options.headers },
	});
	assert(response.status === 200 && response.body?.code === 0, `${options.method || 'GET'} ${path} failed`, response.body);
	return response.body.data;
}

async function request(path, options = {}) {
	const headers = { Accept: 'application/json', ...options.headers };
	let body = options.body;
	if (body && typeof body !== 'string') {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(body);
	}
	try {
		const response = await fetch(`${apiBase}${path}`, { ...options, headers, body, signal: AbortSignal.timeout(15_000) });
		const text = await response.text();
		let parsed = text;
		try { parsed = text ? JSON.parse(text) : null; } catch { /* retain non-JSON diagnostics */ }
		return { status: response.status, body: parsed };
	} catch (error) {
		return { status: 0, body: String(error) };
	}
}

async function composeExec(service, args) {
	return (await compose('exec', '-T', service, ...args)).stdout.trim();
}

async function composeExecWithInput(service, args, input) {
	return (await run('docker', [...composeBase, ...profiles, 'exec', '-T', service, ...args], { input })).stdout.trim();
}

async function compose(...args) {
	return run('docker', [...composeBase, ...profiles, ...args]);
}

async function composeWithOptions(options, ...args) {
	return run('docker', [...composeBase, ...profiles, ...args], options);
}

async function docker(...args) {
	return (await run('docker', args)).stdout.trim();
}

async function run(command, args, options = {}) {
	const execute = () => runBoundedProcess(command, args, {
		cwd: root,
		env: process.env,
		input: options.input,
		timeoutMillis: options.timeoutMillis ?? 120_000,
		killGraceMillis: 1_000,
	});
	const description = command === 'docker' ? describeDockerCommand(args) : command;
	let result;
	try {
		result = command === 'docker'
			? await dockerCommandLimiter(execute)
			: await execute();
	} catch (error) {
		throw new Error(`${description} failed: ${sanitizeError(error)}`);
	}
	if (result.exitCode === 0) return result;
	throw new Error(`${description} exited ${result.exitCode}: ${sanitizeOutput(result.stderr || result.stdout)}`);
}

function runForStatus(command, args, options = {}) {
	return runBoundedProcess(command, args, {
		cwd: root,
		env: process.env,
		input: options.input,
		timeoutMillis: options.timeoutMillis ?? 120_000,
		killGraceMillis: 1_000,
	});
}

async function mysql(sqlText) {
	const result = await compose('exec', '-T', 'mysql', 'sh', '-c', 'exec mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" --batch --skip-column-names --raw -e "$1"', 'sh', sqlText);
	return result.stdout.trim();
}

async function mysqlRows(sqlText) {
	const output = await mysql(sqlText);
	if (!output) return [];
	return output.split(/\r?\n/).filter(Boolean).map(line => line.split('\t').map(value => value === '\\N' ? null : value));
}

async function poll(read, ready, description, timeoutMillis = 30_000) {
	const started = Date.now();
	let lastError;
	while (Date.now() - started < timeoutMillis) {
		try {
			const value = await read();
			if (ready(value)) return value;
		} catch (error) {
			lastError = error;
		}
		await sleep(250);
	}
	throw new Error(`timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

async function pollApi(read, ready, description, timeoutMillis = 30_000) {
	return poll(read, ready, description, timeoutMillis);
}

async function waitFor(read, description, timeoutMillis) {
	return poll(read, value => Boolean(value), description, timeoutMillis);
}

function otherOwner(owner) {
	assert(owner === 'workflow-a' || owner === 'workflow-b', `unexpected Workflow lock owner: ${owner}`);
	return owner === 'workflow-a' ? 'workflow-b' : 'workflow-a';
}

function sql(value) {
	return `'${String(value).replaceAll("'", "''")}'`;
}

function encryptPassword(value, keyword) {
	const key = createHash('sha256').update(keyword, 'utf8').digest();
	const iv = createHash('md5').update(`${keyword}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]).toString('base64');
}

function required(name) {
	const value = env[name];
	if (!value || value === 'GENERATE_ON_FIRST_RUN') throw new Error(`missing runtime value: ${name}`);
	return value;
}

function requiredEnv(name) {
	const value = process.env[name];
	if (!value) throw new Error(`missing environment variable: ${name}`);
	return value;
}

async function resolveDeferredMigration(value) {
	assert(value.endsWith('.sql'), 'BIXI_FAULT_DEFERRED_MIGRATION must name a .sql file');
	const candidate = resolve(root, value);
	assertPathWithinRoot(candidate, 'deferred migration path');
	let canonical;
	try {
		canonical = await realpath(candidate);
	} catch {
		throw new Error(`deferred migration does not exist: ${value}`);
	}
	assertPathWithinRoot(canonical, 'resolved deferred migration path');
	const relativePath = relative(root, canonical);
	assert(relativePath.startsWith('bixi-project-documents/sql/migrations/') && relativePath.endsWith('.sql'),
		'deferred migration must be a SQL file under bixi-project-documents/sql/migrations');
	const metadata = await stat(canonical);
	assert(metadata.isFile(), 'deferred migration must be a regular file');
	return { absolutePath: canonical, relativePath };
}

function assertPathWithinRoot(path, description) {
	const pathFromRoot = relative(root, path);
	assert(pathFromRoot && pathFromRoot !== '..' && !pathFromRoot.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`),
		`${description} must stay under the repository root`);
}

function sanitizeError(error) {
	return secretSanitizer.sanitize(error?.message || error);
}

function sanitizeOutput(output) {
	return secretSanitizer.sanitize(output).trim().slice(-2_000);
}

async function readEnv(path) {
	const result = {};
	const content = await readFile(path, 'utf8');
	for (const line of content.split(/\r?\n/)) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith('#')) continue;
		const separator = trimmed.indexOf('=');
		if (separator < 1) continue;
		let value = trimmed.slice(separator + 1).trim();
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) value = value.slice(1, -1);
		result[trimmed.slice(0, separator)] = value;
	}
	return result;
}

function onceExit(child, timeoutMillis) {
	if (child.exitCode !== null) return Promise.resolve();
	return new Promise((resolvePromise, reject) => {
		const onClose = () => { clearTimeout(timer); resolvePromise(); };
		const timer = setTimeout(() => {
			child.off('close', onClose);
			reject(new Error('timed out waiting for child process exit'));
		}, timeoutMillis);
		child.once('close', onClose);
	});
}

function sleep(milliseconds) {
	return new Promise(resolvePromise => setTimeout(resolvePromise, milliseconds));
}

function assert(condition, message, details) {
	if (condition) return;
	const suffix = details === undefined ? '' : `\n${JSON.stringify(details)}`;
	throw new Error(`${message}${suffix}`);
}
