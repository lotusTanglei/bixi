import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

import * as support from './workflow-performance-metrics.mjs';

const {
	mapWithConcurrency,
	parsePerformanceConfig,
	summarizeLatency,
	summarizePerformance,
} = support;

test('central sanitizer redacts registered secrets, tokens, and local paths', () => {
	assert.equal(typeof support.createSecretSanitizer, 'function');
	const sanitizer = support.createSecretSanitizer([
		'super-secret-password',
		'access-token-value',
		'/Users/example/private/repo',
		'/Users/example/private/repo/target/fault.env',
	]);
	sanitizer.register('late-actor-token');

	const sanitized = sanitizer.sanitize(
		'password=super-secret-password token=access-token-value actor=late-actor-token '
		+ 'root=/Users/example/private/repo env=/Users/example/private/repo/target/fault.env'
	);
	for (const forbidden of [
		'super-secret-password', 'access-token-value', 'late-actor-token', '/Users/example/private/repo',
	]) assert.equal(sanitized.includes(forbidden), false, `sanitizer leaked ${forbidden}`);
	assert.match(sanitized, /<redacted>/);
});

test('central sanitizer recursively redacts secrets containing JSON escape characters', () => {
	const secret = 'quote"slash\\line\nnext';
	const sanitizer = support.createSecretSanitizer([secret]);
	assert.equal(typeof sanitizer.sanitizeValue, 'function');
	const sanitized = sanitizer.sanitizeValue({
		plain: secret,
		nested: [{ message: `before ${secret} after` }],
		untouched: 42,
	});
	assert.deepEqual(sanitized, {
		plain: '<redacted>',
		nested: [{ message: 'before <redacted> after' }],
		untouched: 42,
	});
	assert.equal(JSON.stringify(sanitized).includes('quote\\"slash'), false);
});

test('sensitive environment names include access keys without treating process keys as secrets', () => {
	assert.equal(typeof support.isSensitiveEnvironmentName, 'function');
	for (const name of [
		'ADMIN_PASSWORD', 'OAUTH_CLIENT_SECRET', 'ACCESS_TOKEN', 'MINIO_ACCESS_KEY',
		'SSH_PRIVATE_KEY', 'SERVICE_CREDENTIAL',
	]) assert.equal(support.isSensitiveEnvironmentName(name), true, name);
	for (const name of ['WORKFLOW_PROCESS_KEY', 'BUSINESS_KEY', 'MONKEY_MODE', 'NACOS_NAMESPACE']) {
		assert.equal(support.isSensitiveEnvironmentName(name), false, name);
	}
});

test('stdin sanitizer redacts environment secrets and private paths without argv exposure', () => {
	const secret = 'access"key\\with\nnewline';
	const repositoryPath = process.cwd();
	const envFile = `${repositoryPath}/target/private/fault.env`;
	const args = ['scripts/workflow-performance-metrics.mjs', 'sanitize-stdin'];
	for (const forbidden of [secret, repositoryPath, envFile]) {
		assert.equal(args.some(argument => argument.includes(forbidden)), false);
	}
	const result = spawnSync(process.execPath, args, {
		cwd: repositoryPath,
		env: {
			...process.env,
			MINIO_ACCESS_KEY: secret,
			WORKFLOW_PROCESS_KEY: 'demo_leave_approval',
			BIXI_SANITIZER_ROOT: repositoryPath,
			BIXI_SANITIZER_ENV_FILE: envFile,
		},
		input: `secret=${secret} root=${repositoryPath} env=${envFile} process=demo_leave_approval`,
		encoding: 'utf8',
	});
	assert.equal(result.status, 0, result.stderr);
	assert.equal(result.stdout.includes(secret), false);
	assert.equal(result.stdout.includes(repositoryPath), false);
	assert.equal(result.stdout.includes(envFile), false);
	assert.match(result.stdout, /secret=<redacted>/);
	assert.match(result.stdout, /process=demo_leave_approval/);
});

test('bounded process runner resolves normal exits without a timeout race', async () => {
	assert.equal(typeof support.runBoundedProcess, 'function');
	const result = await support.runBoundedProcess(
		process.execPath,
		['-e', "process.stdout.write('ok')"],
		{ timeoutMillis: 1_000, killGraceMillis: 50 }
	);
	assert.equal(result.exitCode, 0);
	assert.equal(result.stdout, 'ok');
	assert.equal(result.timedOut, false);
});

test('bounded process runner terminates then kills a child that ignores TERM', async () => {
	assert.equal(typeof support.runBoundedProcess, 'function');
	const startedAt = Date.now();
	await assert.rejects(
		support.runBoundedProcess(
			process.execPath,
			['-e', "process.on('SIGTERM', () => {}); setInterval(() => {}, 1000)"],
			{ timeoutMillis: 100, killGraceMillis: 100 }
		),
		error => error.code === 'PROCESS_TIMEOUT' && error.result?.timedOut === true
	);
	assert.ok(Date.now() - startedAt < 2_000, 'timed out child was not reaped promptly');
});

test('bounded process runner reaps descendants that retain stdio after their parent exits', {
	skip: process.platform === 'win32',
}, async () => {
	const descendantScript = "process.on('SIGTERM', () => {}); setTimeout(() => {}, 3000)";
	const parentScript = [
		"const { spawn } = require('node:child_process')",
		`const child = spawn(process.execPath, ['-e', ${JSON.stringify(descendantScript)}], { stdio: ['ignore', process.stdout, process.stderr] })`,
		"require('node:fs').writeSync(1, `${child.pid}\\n`)",
		'process.exit(0)',
	].join(';');
	const startedAt = Date.now();
	const result = await support.runBoundedProcess(process.execPath, ['-e', parentScript], {
		timeoutMillis: 500,
		killGraceMillis: 100,
	});
	const elapsed = Date.now() - startedAt;
	const descendantPid = Number(result.stdout.trim());
	assert.equal(result.exitCode, 0);
	assert.ok(Number.isInteger(descendantPid) && descendantPid > 0, result.stdout);
	assert.ok(elapsed < 1_500, `runner waited ${elapsed}ms for inherited stdio`);
	assert.throws(() => process.kill(descendantPid, 0), error => error?.code === 'ESRCH');
});

test('performance config uses bounded documented defaults', () => {
	assert.deepEqual(parsePerformanceConfig({}), {
		sampleSize: 12,
		concurrency: 4,
		warmup: 2,
		backlogSampleIntervalMillis: 250,
	});
});

test('performance config accepts positive integers within local-run limits', () => {
	assert.deepEqual(parsePerformanceConfig({
		WORKFLOW_PERF_SAMPLE_SIZE: '24',
		WORKFLOW_PERF_CONCURRENCY: '6',
		WORKFLOW_PERF_WARMUP: '3',
		WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS: '500',
	}), {
		sampleSize: 24,
		concurrency: 6,
		warmup: 3,
		backlogSampleIntervalMillis: 500,
	});
});

test('performance config rejects zero, fractions, and excessive local load', () => {
	assert.throws(() => parsePerformanceConfig({ WORKFLOW_PERF_SAMPLE_SIZE: '0' }), /WORKFLOW_PERF_SAMPLE_SIZE.*1.*100/);
	assert.throws(() => parsePerformanceConfig({ WORKFLOW_PERF_CONCURRENCY: '1.5' }), /WORKFLOW_PERF_CONCURRENCY.*integer/);
	assert.throws(() => parsePerformanceConfig({ WORKFLOW_PERF_WARMUP: '21' }), /WORKFLOW_PERF_WARMUP.*1.*20/);
	assert.throws(() => parsePerformanceConfig({ WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS: '100' }), /WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS.*250.*500/);
});

test('performance backlog interval accepts 500ms and rejects 501ms', () => {
	assert.equal(parsePerformanceConfig({ WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS: '500' }).backlogSampleIntervalMillis, 500);
	assert.throws(() => parsePerformanceConfig({ WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS: '501' }),
		/WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS.*250.*500/);
});

test('Rabbit queue parser accepts strict structured queue counters', () => {
	assert.equal(typeof support.parseRabbitQueueJson, 'function');
	assert.deepEqual(support.parseRabbitQueueJson(JSON.stringify([
		{ name: 'bixi.upms.inbox', messages_ready: 2, messages_unacknowledged: 1 },
		{ name: 'bixi.workflow.inbox', messages_ready: 0, messages_unacknowledged: 0 },
	])), [
		{ name: 'bixi.upms.inbox', ready: 2, unacknowledged: 1 },
		{ name: 'bixi.workflow.inbox', ready: 0, unacknowledged: 0 },
	]);
});

test('Rabbit queue parser rejects headers, NaN-like counters, and malformed rows', () => {
	assert.equal(typeof support.parseRabbitQueueJson, 'function');
	assert.throws(() => support.parseRabbitQueueJson('name\tmessages_ready\tmessages_unacknowledged'), /valid JSON/);
	assert.throws(() => support.parseRabbitQueueJson('[{"name":"queue","messages_ready":"NaN","messages_unacknowledged":0}]'), /non-negative integer/);
	assert.throws(() => support.parseRabbitQueueJson('[{"name":"","messages_ready":0,"messages_unacknowledged":0}]'), /non-empty name/);
	assert.throws(() => support.parseRabbitQueueJson('{"name":"queue"}'), /JSON array/);
});

test('latency summary uses deterministic nearest-rank percentiles', () => {
	assert.deepEqual(summarizeLatency([1, 2, 3, 4, 100]), {
		count: 5,
		minMillis: 1,
		maxMillis: 100,
		meanMillis: 22,
		p50Millis: 3,
		p95Millis: 100,
		p99Millis: 100,
		percentileRule: 'nearest-rank: sorted[ceil(p * count) - 1]',
	});
});

test('performance summary uses only successful flows for latency and reports failed observations separately', () => {
	const flows = [
		{ success: true, durations: { createRequestMillis: 4, submitRequestMillis: 6, createSubmitMillis: 10, submitToReviewMillis: 20, completeRequestMillis: 30, completionToFinalMillis: 40, endToEndMillis: 100 } },
		{ success: false, index: 7, failureStage: 'final-state', error: 'timed out', durations: { createRequestMillis: 8, submitRequestMillis: 12, createSubmitMillis: 20, submitToReviewMillis: 40, completeRequestMillis: 60, endToEndMillis: 200 } },
	];
	const summary = summarizePerformance(flows, 400);

	assert.equal(summary.count, 2);
	assert.equal(summary.successCount, 1);
	assert.equal(summary.failureCount, 1);
	assert.equal(summary.successRate, 0.5);
	assert.equal(summary.failureRate, 0.5);
	assert.equal(summary.completedFlowsPerSecond, 2.5);
	assert.equal(summary.latencies.createRequestMillis.meanMillis, 4);
	assert.equal(summary.latencies.submitRequestMillis.meanMillis, 6);
	assert.equal(summary.latencies.createSubmitMillis.meanMillis, 10);
	assert.equal(summary.latencies.submitToReviewMillis.p50Millis, 20);
	assert.equal(summary.latencies.completeRequestMillis.p99Millis, 30);
	assert.equal(summary.latencies.completionToFinalMillis.maxMillis, 40);
	assert.equal(summary.latencies.endToEndMillis.minMillis, 100);
	for (const latency of Object.values(summary.latencies)) {
		assert.equal(latency.population, 'successful-flows');
		assert.equal(latency.eligibleFlowCount, 1);
		assert.equal(latency.sampleCount, 1);
	}
	assert.deepEqual(summary.failureObservations, [{
		kind: null,
		index: 7,
		failureStage: 'final-state',
		error: 'timed out',
		durations: flows[1].durations,
	}]);
});

function backlogSnapshot(observedAt, executableCount, executableWait, rabbitQueues) {
	const empty = () => ({ count: 0, oldestWaitMillis: 0 });
	return {
		flowable: {
			executableJobs: { count: executableCount, oldestWaitMillis: executableWait },
			timerJobs: empty(),
			deadLetterJobs: empty(),
		},
		reliableOutbox: { pending: empty(), inFlight: empty(), failed: empty() },
		reliableInbox: { pending: empty(), inFlight: empty(), failed: empty() },
		rabbitQueues,
		observedAt,
	};
}

test('backlog peaks retain transient and dynamically appearing Rabbit queues with per-metric timestamps', () => {
	assert.equal(typeof support.mergeBacklogPeak, 'function');
	let peak = support.mergeBacklogPeak(null, backlogSnapshot(
		'2026-09-22T00:00:00.000Z', 5, 10,
		[{ name: 'alpha', ready: 1, unacknowledged: 4 }]
	));
	peak = support.mergeBacklogPeak(peak, backlogSnapshot(
		'2026-09-22T00:00:01.000Z', 3, 20,
		[{ name: 'alpha', ready: 9, unacknowledged: 2 }, { name: 'beta', ready: 7, unacknowledged: 1 }]
	));
	peak = support.mergeBacklogPeak(peak, backlogSnapshot(
		'2026-09-22T00:00:02.000Z', 1, 5,
		[{ name: 'beta', ready: 0, unacknowledged: 8 }]
	));

	assert.equal(peak.firstSampledAt, '2026-09-22T00:00:00.000Z');
	assert.equal(peak.lastSampledAt, '2026-09-22T00:00:02.000Z');
	assert.deepEqual(peak.flowable.executableJobs, {
		count: 5,
		countObservedAt: '2026-09-22T00:00:00.000Z',
		oldestWaitMillis: 20,
		oldestWaitObservedAt: '2026-09-22T00:00:01.000Z',
	});
	assert.deepEqual(peak.rabbitQueues, [
		{
			name: 'alpha',
			ready: 9,
			readyObservedAt: '2026-09-22T00:00:01.000Z',
			unacknowledged: 4,
			unacknowledgedObservedAt: '2026-09-22T00:00:00.000Z',
		},
		{
			name: 'beta',
			ready: 7,
			readyObservedAt: '2026-09-22T00:00:01.000Z',
			unacknowledged: 8,
			unacknowledgedObservedAt: '2026-09-22T00:00:02.000Z',
		},
	]);
});

function replicaSample(observedAt, overrides = {}) {
	return {
		observedAt,
		nacosHealthyInstances: 2,
		workflowOwners: { workflowA: 'workflow-a', workflowB: 'workflow-b' },
		workflowDiagnostics: { workflowA: { asyncExecutorActive: true }, workflowB: { asyncExecutorActive: true } },
		containers: {
			workflowA: { containerId: 'container-a', startedAt: 'start-a', status: 'running', running: true },
			workflowB: { containerId: 'container-b', startedAt: 'start-b', status: 'running', running: true },
		},
		...overrides,
	};
}

test('replica continuity evaluation rejects a sampled registration outage', () => {
	assert.equal(typeof support.evaluateReplicaContinuity, 'function');
	const result = support.evaluateReplicaContinuity([
		replicaSample('2026-09-22T00:00:00.000Z'),
		replicaSample('2026-09-22T00:00:01.000Z', { nacosHealthyInstances: 1 }),
		replicaSample('2026-09-22T00:00:02.000Z'),
	]);
	assert.equal(result.stable, false);
	assert.equal(result.sampleCount, 3);
	assert.match(result.observation, /sampled intervals/i);
	assert.ok(result.violations.some(item => item.reason === 'nacos-registration-count'));
});

test('replica continuity evaluation rejects a container restart between samples', () => {
	const restarted = replicaSample('2026-09-22T00:00:01.000Z');
	restarted.containers.workflowB = {
		...restarted.containers.workflowB,
		containerId: 'container-b-restarted',
		startedAt: 'start-b-restarted',
	};
	const result = support.evaluateReplicaContinuity([
		replicaSample('2026-09-22T00:00:00.000Z'),
		restarted,
	]);
	assert.equal(result.stable, false);
	assert.ok(result.violations.some(item => item.reason === 'container-identity-changed'));
});

test('bounded concurrency never exceeds the configured worker count and preserves result order', async () => {
	let inFlight = 0;
	let peak = 0;
	const results = await mapWithConcurrency([40, 5, 20, 1], 2, async (delay, index) => {
		inFlight += 1;
		peak = Math.max(peak, inFlight);
		await new Promise(resolve => setTimeout(resolve, delay));
		inFlight -= 1;
		return index;
	});

	assert.equal(peak, 2);
	assert.deepEqual(results, [0, 1, 2, 3]);
});

test('shared concurrency limiter caps tasks submitted by independent callers', async () => {
	assert.equal(typeof support.createConcurrencyLimiter, 'function');
	const schedule = support.createConcurrencyLimiter(2);
	let inFlight = 0;
	let peak = 0;
	const results = await Promise.all([40, 5, 20, 1].map((delay, index) => schedule(async () => {
		inFlight += 1;
		peak = Math.max(peak, inFlight);
		await new Promise(resolve => setTimeout(resolve, delay));
		inFlight -= 1;
		return index;
	})));

	assert.equal(peak, 2);
	assert.deepEqual(results, [0, 1, 2, 3]);
});

test('Docker command descriptions identify operations without exposing arguments', () => {
	assert.equal(typeof support.describeDockerCommand, 'function');
	assert.equal(support.describeDockerCommand(['inspect', '--format', '{{json .State}}', 'secret-id']), 'docker inspect');
	assert.equal(support.describeDockerCommand([
		'compose', '--env-file', '/private/fault.env', '--project-name', 'private-project',
		'-f', '/private/compose.yaml', '--profile', 'cloud', 'exec', '-T', 'mysql', 'env',
	]), 'docker compose exec');
	assert.equal(support.describeDockerCommand([]), 'docker command');
});

test('bulk Workflow container inspection maps both owners and rejects foreign or duplicate containers', () => {
	assert.equal(typeof support.parseWorkflowOwnerContainers, 'function');
	const container = (service, id, project = 'fault-project') => ({
		Id: id,
		Config: { Labels: {
			'com.docker.compose.project': project,
			'com.docker.compose.service': service,
		} },
		State: {
			Status: 'running', Running: true, ExitCode: 0, OOMKilled: false,
			StartedAt: `${id}-started`, FinishedAt: '0001-01-01T00:00:00Z',
		},
	});
	const workflowA = container('workflow-a', 'id-a');
	const workflowB = container('workflow-b', 'id-b');
	const states = support.parseWorkflowOwnerContainers(
		`${JSON.stringify(workflowB)}\n${JSON.stringify(workflowA)}\n`,
		'fault-project'
	);
	assert.equal(states['workflow-a'].containerId, 'id-a');
	assert.equal(states['workflow-b'].containerId, 'id-b');
	assert.equal(states['workflow-a'].running, true);

	assert.throws(
		() => support.parseWorkflowOwnerContainers(JSON.stringify(container('workflow-a', 'foreign', 'other')), 'fault-project'),
		/another project/
	);
	assert.throws(
		() => support.parseWorkflowOwnerContainers(
			`${JSON.stringify(workflowA)}\n${JSON.stringify(container('workflow-a', 'id-a-duplicate'))}`,
			'fault-project'
		),
		/one container for each Workflow replica/
	);
});
