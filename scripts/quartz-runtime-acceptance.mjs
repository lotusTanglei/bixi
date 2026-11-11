#!/usr/bin/env node

import assert from 'node:assert/strict';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = parseArgs(process.argv.slice(2));

if (args.help) {
	console.log(`Usage: node scripts/quartz-runtime-acceptance.mjs [options]

  --mode <cloud|single>       Default: BIXI_MODE or cloud
  --env-file <path>           Default: BIXI_ENV_FILE or .env
  --api-base <url>            Default: BIXI_API_BASE_URL or direct backend port

Temporary jobs are paused and deleted in finally; execution and audit records remain.
`);
	process.exit(0);
}

const envFile = resolve(args.envFile || process.env.BIXI_ENV_FILE || resolve(root, '.env'));
const fileEnv = await readEnv(envFile);
const env = { ...fileEnv, ...process.env };
const mode = args.mode || env.BIXI_MODE || 'cloud';
assert(['cloud', 'single'].includes(mode), 'mode must be cloud or single');
const apiBase = (args.apiBase || env.BIXI_API_BASE_URL
	|| `http://127.0.0.1:${required(mode === 'cloud' ? 'GATEWAY_PORT' : 'SINGLE_PORT')}`).replace(/\/$/, '');
const prefix = mode === 'cloud' ? '/job' : '/admin';
const authPrefix = mode === 'cloud' ? '/auth' : '/admin';
const healthPath = `${prefix}/actuator/health`;
const marker = randomBytes(6).toString('hex');
const group = `quartz-acceptance-${marker}`;
const jobs = [];
const evidence = { mode, apiBase, status: 'failed', checks: [], cleanup: [] };
let token;
let failure;

try {
	await waitForHealth(120_000);
	token = await login();
	const info = api(await call('/admin/user/info'), 'administrator identity');
	for (const permission of ['job_sys_job_view', 'job_sys_job_add', 'job_sys_job_del',
		'job_sys_job_start_job', 'job_sys_job_run_job', 'job_sys_job_shutdown_job',
		'job_sys_job_record_view']) {
		assert(info.permissions?.includes(permission), `missing permission: ${permission}`);
	}

	const successId = await createJob({
		name: `quartz-success-${marker}`, group, type: '2', className: 'demo',
		methodName: 'demoMethod', methodParamsValue: marker,
		cronExpression: '0/30 * * * * ?', retryCount: 0, retryIntervalSeconds: 1,
	});
	api(await call(`${prefix}/sys-job/start-job/${successId}`, { method: 'POST' }), 'start success job');
	const success = await waitForRecord(successId,
		(record) => String(record.status) === '0' && record.triggerType === 'CRON', 75_000);
	assert.equal(Number(success.attempt), 1);
	assert.equal(Number(success.maxAttempts), 1);
	evidence.checks.push({ name: 'scheduled success', jobId: successId, recordId: String(success.id) });

	const failureId = await createJob({
		name: `quartz-failure-${marker}`, group, type: '2', className: 'demo',
		methodName: 'missingMethod', methodParamsValue: marker,
		cronExpression: '0 0 0 1 1 ? 2099', retryCount: 0, retryIntervalSeconds: 1,
	});
	api(await call(`${prefix}/sys-job/run-job/${failureId}`, { method: 'POST' }), 'trigger failure job');
	const failed = await waitForRecord(failureId,
		(record) => String(record.status) === '1' && record.triggerType === 'MANUAL', 20_000);
	assert.equal(Number(failed.attempt), 1);
	assert.equal(Number(failed.maxAttempts), 1);
	assert(String(failed.exceptionInfo || '').includes('方法未找到'), 'failure history lacks the invocation error');
	evidence.checks.push({ name: 'persisted failure', jobId: failureId, recordId: String(failed.id) });
	evidence.status = 'passed';
} catch (error) {
	failure = error;
} finally {
	if (token && jobs.length) {
		await waitForHealth(90_000).catch(() => undefined);
		for (const job of jobs.reverse()) {
			let jobId = job.id;
			try {
				jobId ||= await findJob(job.name);
				if (!jobId) {
					evidence.cleanup.push({ name: job.name, status: 'not created' });
					continue;
				}
				let pauseError;
				try {
					api(await call(`${prefix}/sys-job/shutdown-job/${jobId}`, { method: 'POST' }),
						`pause ${job.name}`);
				} catch (error) {
					pauseError = error;
				}
				api(await call(`${prefix}/sys-job/${jobId}`, { method: 'DELETE' }), `delete ${job.name}`);
				if (pauseError) throw pauseError;
				evidence.cleanup.push({ jobId, status: 'deleted' });
			} catch (error) {
				evidence.cleanup.push({ name: job.name, jobId, status: 'failed', error: error.message });
				failure ||= error;
			}
		}
	}
	if (token) await call(`${authPrefix}/token/logout`, { method: 'DELETE' }).catch(() => undefined);
	if (failure) {
		evidence.status = 'failed';
		evidence.error = failure.message;
		process.exitCode = 1;
	}
	console.log(JSON.stringify(evidence, null, 2));
}

async function createJob(payload) {
	const job = { name: payload.name, id: null };
	jobs.push(job);
	api(await call(`${prefix}/sys-job`, { method: 'POST', body: payload }), `create ${payload.name}`);
	job.id = await findJob(payload.name);
	assert(job.id, `created job was not found: ${payload.name}`);
	return job.id;
}

async function findJob(name) {
	const page = api(await call(`${prefix}/sys-job/page?current=1&size=10&name=${encodeURIComponent(name)}`),
		`find ${name}`);
	const found = page.records?.find((item) => item.name === name && item.group === group);
	return found?.id ? String(found.id) : null;
}

async function records(jobId) {
	const page = api(await call(`${prefix}/sys-job-record/page?current=1&size=100&descs=id&jobId=${jobId}`),
		`load records for ${jobId}`);
	return (page.records || []).filter((record) => String(record.jobId) === jobId);
}

async function waitForRecord(jobId, matches, timeoutMillis) {
	const deadline = Date.now() + timeoutMillis;
	while (Date.now() < deadline) {
		const found = (await records(jobId)).find(matches);
		if (found) return found;
		await sleep(1000);
	}
	throw new Error(`timed out waiting for Quartz record for job ${jobId}`);
}

async function waitForHealth(timeoutMillis) {
	const deadline = Date.now() + timeoutMillis;
	while (Date.now() < deadline) {
		const result = await call(healthPath).catch(() => null);
		if (result?.status === 200 && result.body?.status === 'UP') return;
		await sleep(1000);
	}
	throw new Error(`Quartz health did not become UP: ${healthPath}`);
}

async function login() {
	const body = new URLSearchParams({
		username: required('ADMIN_USERNAME'), password: encryptPassword(required('ADMIN_PASSWORD'),
			required('BIXI_ENCODE_KEY')),
		grant_type: 'password', scope: 'server',
	});
	const secret = required('OAUTH_PASSWORD_CLIENT_SECRET');
	const response = await call(`${authPrefix}/oauth2/token`, { method: 'POST',
		headers: { Authorization: `Basic ${Buffer.from(`bixi:${secret}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString() });
	assert(response.status === 200 && response.body?.access_token, `administrator login failed (${response.status})`);
	return response.body.access_token;
}

async function call(path, { method = 'GET', body, headers = {} } = {}) {
	const response = await fetch(`${apiBase}${path}`, { method, signal: AbortSignal.timeout(20_000),
		headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
			...(body !== undefined && typeof body !== 'string' ? { 'Content-Type': 'application/json' } : {}),
			...headers },
		body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body) });
	const text = await response.text();
	let parsed;
	try { parsed = JSON.parse(text); } catch { parsed = null; }
	return { status: response.status, body: parsed };
}

function api(response, label) {
	assert(response.status === 200 && response.body?.code === 0 && response.body?.data !== false,
		`${label} failed (${response.status}, code ${response.body?.code})`);
	return response.body.data;
}

function encryptPassword(value, keyValue) {
	const key = createHash('sha256').update(keyValue, 'utf8').digest();
	const iv = createHash('md5').update(`${keyValue}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]).toString('base64');
}

function required(name) {
	assert(env[name] && env[name] !== 'GENERATE_ON_FIRST_RUN', `missing ${name} in ${envFile}`);
	return env[name];
}

function parseArgs(values) {
	const result = {};
	const options = new Map([['--mode', 'mode'], ['--env-file', 'envFile'],
		['--api-base', 'apiBase']]);
	for (let index = 0; index < values.length; index += 1) {
		const option = values[index];
		if (option === '--help') result.help = true;
		else if (options.has(option)) {
			assert(values[index + 1], `${option} requires a value`);
			result[options.get(option)] = values[++index];
		} else throw new Error(`unknown option: ${option}`);
	}
	return result;
}

async function readEnv(path) {
	const result = {};
	for (const line of (await readFile(path, 'utf8')).split(/\r?\n/)) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith('#')) continue;
		const separator = trimmed.indexOf('=');
		if (separator < 1) continue;
		let value = trimmed.slice(separator + 1).trim();
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) {
			value = value.slice(1, -1);
		}
		result[trimmed.slice(0, separator).trim()] = value;
	}
	return result;
}

function sleep(milliseconds) {
	return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}
