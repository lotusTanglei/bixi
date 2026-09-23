import { spawn } from 'node:child_process';
import process from 'node:process';

const LIMITS = {
	WORKFLOW_PERF_SAMPLE_SIZE: { defaultValue: 12, min: 1, max: 100 },
	WORKFLOW_PERF_CONCURRENCY: { defaultValue: 4, min: 1, max: 16 },
	WORKFLOW_PERF_WARMUP: { defaultValue: 2, min: 1, max: 20 },
	WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS: { defaultValue: 250, min: 250, max: 500 },
};

export function runBoundedProcess(command, args, options = {}) {
	const timeoutMillis = options.timeoutMillis ?? 120_000;
	const killGraceMillis = options.killGraceMillis ?? 1_000;
	if (!Number.isInteger(timeoutMillis) || timeoutMillis < 1) {
		throw new RangeError('timeoutMillis must be a positive integer');
	}
	if (!Number.isInteger(killGraceMillis) || killGraceMillis < 1) {
		throw new RangeError('killGraceMillis must be a positive integer');
	}
	return new Promise((resolvePromise, reject) => {
		const ownsProcessGroup = process.platform !== 'win32';
		const child = spawn(command, args, {
			cwd: options.cwd,
			env: options.env,
			stdio: ['pipe', 'pipe', 'pipe'],
			detached: ownsProcessGroup,
		});
		let stdout = '';
		let stderr = '';
		let settled = false;
		let timedOut = false;
		let shutdownStarted = false;
		let exitCode = null;
		let exitSignal = null;
		let killTimer;
		let hardStopTimer;
		const result = () => ({
			exitCode: exitCode ?? child.exitCode,
			signal: exitSignal ?? child.signalCode,
			stdout,
			stderr,
			timedOut,
		});
		const finish = (error, result) => {
			if (settled) return;
			settled = true;
			clearTimeout(timeout);
			clearTimeout(killTimer);
			clearTimeout(hardStopTimer);
			child.stdin.destroy();
			child.stdout.destroy();
			child.stderr.destroy();
			child.unref();
			if (error) reject(error);
			else resolvePromise(result);
		};
		const finishAfterShutdown = () => {
			const processResult = result();
			if (timedOut) {
				const error = new Error(`${command} timed out after ${timeoutMillis}ms`);
				error.code = 'PROCESS_TIMEOUT';
				error.result = processResult;
				finish(error);
			} else {
				finish(null, processResult);
			}
		};
		const signalTree = signal => {
			let groupSignalled = false;
			if (ownsProcessGroup && child.pid) {
				try {
					process.kill(-child.pid, signal);
					groupSignalled = true;
				} catch (error) {
					if (error.code !== 'ESRCH' && error.code !== 'EPERM') throw error;
				}
			}
			if (!groupSignalled && child.exitCode === null) {
				try { child.kill(signal); } catch (error) {
					if (error.code !== 'ESRCH') throw error;
				}
			}
		};
		const beginShutdown = dueToTimeout => {
			if (settled || shutdownStarted) return;
			shutdownStarted = true;
			timedOut = dueToTimeout;
			try {
				signalTree('SIGTERM');
			} catch (error) {
				finish(error);
				return;
			}
			killTimer = setTimeout(() => {
				if (settled) return;
				try {
					signalTree('SIGKILL');
				} catch (error) {
					finish(error);
					return;
				}
				hardStopTimer = setTimeout(finishAfterShutdown, 50);
			}, killGraceMillis);
		};
		const timeout = setTimeout(() => beginShutdown(true), timeoutMillis);
		child.stdout.on('data', chunk => { stdout += chunk.toString(); });
		child.stderr.on('data', chunk => { stderr += chunk.toString(); });
		child.stdin.on('error', () => undefined);
		child.on('error', error => finish(error));
		child.on('exit', (code, signal) => {
			exitCode = code;
			exitSignal = signal;
			if (!shutdownStarted) beginShutdown(false);
		});
		child.on('close', finishAfterShutdown);
		child.stdin.end(options.input);
	});
}

export function isSensitiveEnvironmentName(name) {
	const normalized = String(name || '').toUpperCase();
	if (/(?:^|_)(?:PASSWORD|SECRET|TOKEN|CREDENTIAL)(?:_|$)/.test(normalized)) return true;
	if (!/(?:^|_)KEY(?:_|$)/.test(normalized)) return false;
	return !/(?:^|_)(?:PROCESS|BUSINESS|AGGREGATE|ROUTE|CACHE|LOOKUP|SORT)_KEY(?:_|$)/.test(normalized);
}

export function createSecretSanitizer(initialValues = []) {
	const sensitiveValues = new Set();
	const register = value => {
		if (value !== undefined && value !== null && String(value).length > 0) {
			sensitiveValues.add(String(value));
		}
	};
	for (const value of initialValues) register(value);
	return {
		register,
		sanitize(value) {
			let sanitized = String(value ?? '');
			for (const secret of [...sensitiveValues].sort((left, right) => right.length - left.length)) {
				sanitized = sanitized.replaceAll(secret, '<redacted>');
			}
			return sanitized;
		},
		sanitizeValue(value) {
			return sanitizeStructure(value, this.sanitize);
		},
	};
}

function sanitizeStructure(value, sanitize, seen = new WeakMap()) {
	if (typeof value === 'string') return sanitize(value);
	if (!value || typeof value !== 'object') return value;
	if (seen.has(value)) return seen.get(value);
	if (Array.isArray(value)) {
		const output = [];
		seen.set(value, output);
		for (const item of value) output.push(sanitizeStructure(item, sanitize, seen));
		return output;
	}
	if (Object.getPrototypeOf(value) !== Object.prototype && Object.getPrototypeOf(value) !== null) return value;
	const output = {};
	seen.set(value, output);
	for (const [key, item] of Object.entries(value)) {
		output[key] = sanitizeStructure(item, sanitize, seen);
	}
	return output;
}

export function parsePerformanceConfig(env) {
	return {
		sampleSize: boundedInteger(env, 'WORKFLOW_PERF_SAMPLE_SIZE'),
		concurrency: boundedInteger(env, 'WORKFLOW_PERF_CONCURRENCY'),
		warmup: boundedInteger(env, 'WORKFLOW_PERF_WARMUP'),
		backlogSampleIntervalMillis: boundedInteger(env, 'WORKFLOW_PERF_BACKLOG_SAMPLE_INTERVAL_MS'),
	};
}

export function parseRabbitQueueJson(output) {
	let rows;
	try {
		rows = JSON.parse(output);
	} catch (error) {
		throw new TypeError(`Rabbit queue output must be valid JSON: ${error.message}`);
	}
	if (!Array.isArray(rows)) {
		throw new TypeError('Rabbit queue output must be a JSON array');
	}
	return rows.map((row, index) => {
		if (!row || typeof row !== 'object' || Array.isArray(row)) {
			throw new TypeError(`Rabbit queue row ${index} must be an object`);
		}
		if (typeof row.name !== 'string' || row.name.trim() === '') {
			throw new TypeError(`Rabbit queue row ${index} must have a non-empty name`);
		}
		for (const field of ['messages_ready', 'messages_unacknowledged']) {
			if (!Number.isInteger(row[field]) || row[field] < 0) {
				throw new TypeError(`Rabbit queue row ${index} ${field} must be a non-negative integer`);
			}
		}
		return {
			name: row.name,
			ready: row.messages_ready,
			unacknowledged: row.messages_unacknowledged,
		};
	});
}

export function summarizeLatency(values) {
	const sorted = values.filter(Number.isFinite).sort((left, right) => left - right);
	if (sorted.length === 0) {
		return {
			count: 0,
			minMillis: null,
			maxMillis: null,
			meanMillis: null,
			p50Millis: null,
			p95Millis: null,
			p99Millis: null,
			percentileRule: 'nearest-rank: sorted[ceil(p * count) - 1]',
		};
	}
	return {
		count: sorted.length,
		minMillis: sorted[0],
		maxMillis: sorted.at(-1),
		meanMillis: round(sorted.reduce((sum, value) => sum + value, 0) / sorted.length),
		p50Millis: nearestRank(sorted, 0.50),
		p95Millis: nearestRank(sorted, 0.95),
		p99Millis: nearestRank(sorted, 0.99),
		percentileRule: 'nearest-rank: sorted[ceil(p * count) - 1]',
	};
}

export function summarizePerformance(flows, batchWallMillis) {
	const successfulFlows = flows.filter(flow => flow.success);
	const failedFlows = flows.filter(flow => !flow.success);
	const successCount = successfulFlows.length;
	const failureCount = flows.length - successCount;
	const durationNames = [
		'createRequestMillis',
		'submitRequestMillis',
		'createSubmitMillis',
		'submitToReviewMillis',
		'completeRequestMillis',
		'completionToFinalMillis',
		'endToEndMillis',
	];
	return {
		count: flows.length,
		batchWallMillis,
		completedFlowsPerSecond: batchWallMillis > 0 ? round(successCount * 1_000 / batchWallMillis) : 0,
		successCount,
		successRate: flows.length > 0 ? round(successCount / flows.length) : 0,
		failureCount,
		failureRate: flows.length > 0 ? round(failureCount / flows.length) : 0,
		latencies: Object.fromEntries(durationNames.map(name => {
			const latency = summarizeLatency(successfulFlows.map(flow => flow.durations?.[name]));
			return [name, {
				...latency,
				sampleCount: latency.count,
				population: 'successful-flows',
				eligibleFlowCount: successCount,
			}];
		})),
		failureObservations: failedFlows.map(flow => ({
			kind: flow.kind ?? null,
			index: flow.index ?? null,
			failureStage: flow.failureStage ?? 'unknown',
			error: flow.error ?? null,
			durations: { ...(flow.durations || {}) },
		})),
	};
}

export function mergeBacklogPeak(peak, snapshot) {
	if (!snapshot?.observedAt) throw new TypeError('backlog snapshot must include observedAt');
	const next = peak ? structuredClone(peak) : {
		flowable: {},
		reliableOutbox: {},
		reliableInbox: {},
		rabbitQueues: [],
		firstSampledAt: snapshot.observedAt,
		lastSampledAt: snapshot.observedAt,
	};
	for (const category of ['flowable', 'reliableOutbox', 'reliableInbox']) {
		for (const [name, value] of Object.entries(snapshot[category] || {})) {
			const existing = next[category][name];
			if (!existing) {
				next[category][name] = {
					count: value.count,
					countObservedAt: snapshot.observedAt,
					oldestWaitMillis: value.oldestWaitMillis,
					oldestWaitObservedAt: snapshot.observedAt,
				};
				continue;
			}
			if (value.count > existing.count) {
				existing.count = value.count;
				existing.countObservedAt = snapshot.observedAt;
			}
			if (value.oldestWaitMillis > existing.oldestWaitMillis) {
				existing.oldestWaitMillis = value.oldestWaitMillis;
				existing.oldestWaitObservedAt = snapshot.observedAt;
			}
		}
	}
	const queues = new Map(next.rabbitQueues.map(queue => [queue.name, queue]));
	for (const queue of snapshot.rabbitQueues || []) {
		const existing = queues.get(queue.name);
		if (!existing) {
			queues.set(queue.name, {
				name: queue.name,
				ready: queue.ready,
				readyObservedAt: snapshot.observedAt,
				unacknowledged: queue.unacknowledged,
				unacknowledgedObservedAt: snapshot.observedAt,
			});
			continue;
		}
		if (queue.ready > existing.ready) {
			existing.ready = queue.ready;
			existing.readyObservedAt = snapshot.observedAt;
		}
		if (queue.unacknowledged > existing.unacknowledged) {
			existing.unacknowledged = queue.unacknowledged;
			existing.unacknowledgedObservedAt = snapshot.observedAt;
		}
	}
	next.rabbitQueues = [...queues.values()].sort((left, right) => left.name.localeCompare(right.name));
	next.lastSampledAt = snapshot.observedAt;
	return next;
}

export function evaluateReplicaContinuity(samples) {
	if (!Array.isArray(samples) || samples.length === 0) {
		return {
			stable: false,
			sampleCount: 0,
			firstSampledAt: null,
			lastSampledAt: null,
			observation: 'No replica samples were collected.',
			violations: [{ observedAt: null, reason: 'no-samples' }],
		};
	}
	const baseline = samples[0];
	const violations = [];
	for (const sample of samples) {
		if (sample.nacosHealthyInstances !== 2) {
			violations.push({ observedAt: sample.observedAt, reason: 'nacos-registration-count' });
		}
		if (Array.isArray(sample.nacosInstances)
			&& (sample.nacosInstances.length !== 2 || sample.nacosInstances.some(instance => instance.healthy !== true))) {
			violations.push({ observedAt: sample.observedAt, reason: 'nacos-registration-details' });
		}
		for (const [key, owner] of [['workflowA', 'workflow-a'], ['workflowB', 'workflow-b']]) {
			if (sample.workflowOwners?.[key] !== owner || !sample.workflowDiagnostics?.[key]) {
				violations.push({ observedAt: sample.observedAt, owner, reason: 'diagnostics-registration' });
			}
			const container = sample.containers?.[key];
			const baselineContainer = baseline.containers?.[key];
			if (!container || container.running !== true || container.status !== 'running') {
				violations.push({ observedAt: sample.observedAt, owner, reason: 'container-not-running' });
			} else if (container.containerId !== baselineContainer?.containerId
				|| container.startedAt !== baselineContainer?.startedAt) {
				violations.push({ observedAt: sample.observedAt, owner, reason: 'container-identity-changed' });
			}
		}
	}
	return {
		stable: violations.length === 0,
		sampleCount: samples.length,
		firstSampledAt: samples[0].observedAt,
		lastSampledAt: samples.at(-1).observedAt,
		observation: 'All sampled intervals retained both original running containers and two healthy registrations; unobserved time between samples is not asserted.',
		violations,
	};
}

export async function mapWithConcurrency(items, concurrency, worker) {
	if (!Number.isInteger(concurrency) || concurrency < 1) {
		throw new TypeError('concurrency must be a positive integer');
	}
	const results = new Array(items.length);
	let nextIndex = 0;
	async function runWorker() {
		while (nextIndex < items.length) {
			const index = nextIndex;
			nextIndex += 1;
			results[index] = await worker(items[index], index);
		}
	}
	await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, runWorker));
	return results;
}

export function createConcurrencyLimiter(concurrency) {
	if (!Number.isInteger(concurrency) || concurrency < 1) {
		throw new TypeError('concurrency must be a positive integer');
	}
	let active = 0;
	const waiting = [];
	const acquire = () => new Promise(resolvePromise => {
		if (active < concurrency) {
			active += 1;
			resolvePromise();
		} else {
			waiting.push(resolvePromise);
		}
	});
	const release = () => {
		active -= 1;
		const next = waiting.shift();
		if (next) {
			active += 1;
			next();
		}
	};
	return async task => {
		if (typeof task !== 'function') throw new TypeError('limited task must be a function');
		await acquire();
		try {
			return await task();
		} finally {
			release();
		}
	};
}

const COMPOSE_OPERATIONS = new Set([
	'config', 'down', 'exec', 'kill', 'logs', 'port', 'ps', 'restart', 'rm', 'start', 'stop', 'up',
]);

export function describeDockerCommand(args) {
	if (!Array.isArray(args) || args.length === 0) return 'docker command';
	if (args[0] !== 'compose') return `docker ${args[0]}`;
	const operation = args.slice(1).find(argument => COMPOSE_OPERATIONS.has(argument));
	return operation ? `docker compose ${operation}` : 'docker compose command';
}

export function parseWorkflowOwnerContainers(output, expectedProject) {
	const containers = String(output || '').split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
	const entries = containers.map(container => {
		const labels = container.Config?.Labels || {};
		const service = labels['com.docker.compose.service'];
		if (labels['com.docker.compose.project'] !== expectedProject) {
			throw new Error(`refusing to inspect container from another project: ${container.Id || 'unknown'}`);
		}
		if (service !== 'workflow-a' && service !== 'workflow-b') {
			throw new Error(`refusing to inspect unexpected service: ${container.Id || 'unknown'}`);
		}
		const state = container.State || {};
		return [service, {
			containerId: container.Id,
			status: state.Status,
			running: state.Running,
			exitCode: state.ExitCode,
			oomKilled: state.OOMKilled,
			startedAt: state.StartedAt,
			finishedAt: state.FinishedAt,
		}];
	});
	const states = Object.fromEntries(entries);
	if (entries.length !== 2 || !states['workflow-a'] || !states['workflow-b']) {
		throw new Error('Docker inspect must return one container for each Workflow replica');
	}
	return states;
}

function boundedInteger(env, name) {
	const { defaultValue, min, max } = LIMITS[name];
	const raw = env[name];
	if (raw === undefined || raw === '') return defaultValue;
	if (!/^[0-9]+$/.test(String(raw))) {
		throw new TypeError(`${name} must be an integer between ${min} and ${max}`);
	}
	const value = Number(raw);
	if (!Number.isSafeInteger(value) || value < min || value > max) {
		throw new RangeError(`${name} must be between ${min} and ${max}`);
	}
	return value;
}

function nearestRank(sorted, percentile) {
	return sorted[Math.max(0, Math.ceil(percentile * sorted.length) - 1)];
}

function round(value) {
	return Math.round(value * 1_000) / 1_000;
}

if (process.argv[2] === 'sanitize-stdin') {
	const sensitiveValues = Object.entries(process.env)
		.filter(([name, value]) => isSensitiveEnvironmentName(name)
			&& value && value !== 'GENERATE_ON_FIRST_RUN')
		.map(([, value]) => value);
	const sanitizer = createSecretSanitizer([
		...sensitiveValues,
		process.env.BIXI_SANITIZER_ROOT,
		process.env.BIXI_SANITIZER_ENV_FILE,
	]);
	let input = '';
	process.stdin.setEncoding('utf8');
	for await (const chunk of process.stdin) input += chunk;
	process.stdout.write(sanitizer.sanitize(input));
}
