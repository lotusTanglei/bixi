#!/usr/bin/env node

import { createCipheriv, createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import process from 'node:process';

const root = new URL('..', import.meta.url);
const args = parseArgs(process.argv.slice(2));
const envFile = args.envFile || process.env.BIXI_ENV_FILE || new URL('../.env', import.meta.url).pathname;
const env = { ...await readEnv(envFile), ...process.env };
const apiBase = stripTrailingSlash(args.apiBase || env.BIXI_API_BASE_URL || `http://127.0.0.1:${env.SINGLE_PORT || 9998}`);
const output = args.output || new URL('../target/phase2-runtime/EVIDENCE-SINGLE-GENERATOR-PARENT-CHILD-20260927.json', import.meta.url).pathname;

const state = {
	mode: 'single',
	apiBase,
	checks: [],
	startedAt: new Date().toISOString(),
};

function check(name, condition, details = undefined) {
	if (!condition) {
		const error = new Error(`${name} failed${details === undefined ? '' : `: ${JSON.stringify(details)}`}`);
		error.check = name;
		throw error;
	}
	state.checks.push({ name, status: 'passed', ...(details === undefined ? {} : { details }) });
}

function apiError(response, action) {
	throw new Error(`${action} returned HTTP ${response.status}: ${JSON.stringify(response.body)}`);
}

function assertApi(response, action) {
	if (response.status !== 200 || response.body?.code !== 0) apiError(response, action);
	return response.body.data;
}

function assertDenied(response, action) {
	const rejected = response.status >= 400 && response.status < 500
		|| (response.status === 200 && Number.isInteger(response.body?.code) && response.body.code !== 0);
	check(action, response.status !== 0 && rejected, { status: response.status, body: response.body });
}

const token = await login();
const auditBefore = new Set((await loadAuditLogs(token)).map(record => String(record.id)));

const anonymous = await request('/admin/dictAggregate/page?current=1&size=1');
assertDenied(anonymous, 'anonymous parent-child access rejected');

const marker = `generator-runtime-${Date.now()}`;
const createPayload = {
	type: marker,
	name: '生成父表运行验收',
	description: 'single generated parent-child runtime',
	sn: 1,
	systemFlag: 'N',
	children: [
		{ value: `${marker}-one`, label: 'One', dictType: marker, description: 'first child', sn: 1 },
		{ value: `${marker}-two`, label: 'Two', dictType: marker, description: 'second child', sn: 2 },
	],
};

const created = assertApi(await authorized(token, '/admin/dictAggregate', { method: 'POST', body: createPayload }), 'create parent-child record');
check('parent create response', created === true);
const parent = await findParent(token, marker);
check('created parent located', Boolean(parent?.id), parent);
const initialDetails = assertApi(await authorized(token, `/admin/dictAggregate/details/${parent.id}`), 'load created parent details');
check('created parent contains two children', Array.isArray(initialDetails.children) && initialDetails.children.length === 2, initialDetails);
check('created children point to created parent', initialDetails.children.every(child => String(child.dictId) === String(parent.id)), initialDetails.children);

const replacement = {
	id: parent.id,
	type: marker,
	name: '生成父表运行验收-已替换',
	description: 'replacement',
	sn: 2,
	systemFlag: 'N',
	children: [
		{ id: initialDetails.children[0].id, dictId: parent.id, value: `${marker}-one-replaced`, label: 'One replaced', dictType: marker, description: 'replacement child', sn: 10 },
		{ value: `${marker}-three`, label: 'Three', dictType: marker, description: 'new child', sn: 20 },
	],
};
assertApi(await authorized(token, '/admin/dictAggregate', { method: 'PUT', body: replacement }), 'replace parent-child children');
const replacedDetails = assertApi(await authorized(token, `/admin/dictAggregate/details/${parent.id}`), 'load replaced parent details');
check('child replacement is atomic and complete', replacedDetails.name === replacement.name
	&& replacedDetails.children.length === 2
	&& replacedDetails.children.some(child => child.value === `${marker}-one-replaced`)
	&& replacedDetails.children.some(child => child.value === `${marker}-three`), replacedDetails);
check('replacement assigns fresh child identities', replacedDetails.children.every(child => child.id)
	&& !replacedDetails.children.some(child => String(child.id) === String(initialDetails.children[1].id)), replacedDetails.children);

const invalidUpdate = {
	...replacement,
	name: 'must rollback',
	children: [{ ...replacedDetails.children[0], dictId: '999999999999999999' }],
};
const invalidResponse = await authorized(token, '/admin/dictAggregate', { method: 'PUT', body: invalidUpdate });
check('invalid child relationship rejected', invalidResponse.status >= 400
	|| (invalidResponse.status === 200 && invalidResponse.body?.code !== 0), { status: invalidResponse.status, body: invalidResponse.body });
const afterInvalid = assertApi(await authorized(token, `/admin/dictAggregate/details/${parent.id}`), 'verify failed update rollback');
check('failed child update leaves parent and children unchanged', afterInvalid.name === replacedDetails.name
	&& JSON.stringify(afterInvalid.children) === JSON.stringify(replacedDetails.children), afterInvalid);

assertApi(await authorized(token, '/admin/dictAggregate', { method: 'DELETE', body: [parent.id] }), 'delete parent-child record');
const deletedDetails = await authorized(token, `/admin/dictAggregate/details/${parent.id}`);
check('deleted parent is no longer readable', deletedDetails.status >= 400
	|| (deletedDetails.status === 200 && deletedDetails.body?.code !== 0), { status: deletedDetails.status, body: deletedDetails.body });
const remaining = await findParent(token, marker);
check('deleted parent is absent from page', !remaining, { marker });

const auditLogs = await waitForAuditLogs(token, auditBefore, marker);
state.checks.push({ name: 'parent-child write audit logs', status: 'passed', details: auditLogs });
state.finishedAt = new Date().toISOString();
state.result = 'passed';
await writeFile(output, `${JSON.stringify(state, null, 2)}\n`, { flag: 'w' });
console.log(JSON.stringify(state, null, 2));

async function login() {
	const password = encryptPassword(required('ADMIN_PASSWORD'), required('BIXI_ENCODE_KEY'));
	const response = await fetchResponse(`${apiBase}/admin/oauth2/token`, {
		method: 'POST',
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${required('OAUTH_PASSWORD_CLIENT_SECRET')}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: new URLSearchParams({ username: required('ADMIN_USERNAME'), password, grant_type: 'password', scope: 'server' }).toString(),
	});
	if (response.status !== 200 || !response.body?.access_token) apiError(response, 'login');
	return response.body.access_token;
}

async function findParent(tokenValue, type) {
	const data = assertApi(await authorized(tokenValue, `/admin/dictAggregate/page?current=1&size=100&type=${encodeURIComponent(type)}`), `find parent ${type}`);
	return (data.records || []).find(record => record.type === type);
}

async function loadAuditLogs(tokenValue) {
	return assertApi(await authorized(tokenValue, '/admin/log/page?current=1&size=500&descs=id'), 'load audit logs')?.records || [];
}

async function waitForAuditLogs(tokenValue, baseline, type) {
	for (let attempt = 0; attempt < 30; attempt += 1) {
		const records = (await loadAuditLogs(tokenValue)).filter(record => !baseline.has(String(record.id)));
		const writes = records.filter(record => record.requestUri === '/admin/dictAggregate'
			&& ['新增字典表', '修改字典表', '删除字典表'].includes(record.title));
		if (new Set(writes.map(record => record.title)).size === 3
			&& writes.some(record => typeof record.params === 'string' && record.params.includes(type))) {
			return writes.map(({ id, title, method, requestUri }) => ({ id: String(id), title, method, requestUri }));
		}
		await sleep(500);
	}
	throw new Error('parent-child write audit logs were not persisted');
}

async function authorized(tokenValue, path, options = {}) {
	return request(path, { ...options, headers: { Authorization: `Bearer ${tokenValue}`, ...(options.headers || {}) } });
}

async function request(path, options = {}) {
	const headers = { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) };
	const body = options.body && typeof options.body !== 'string' ? JSON.stringify(options.body) : options.body;
	return fetchResponse(`${apiBase}${path}`, { ...options, headers, body });
}

async function fetchResponse(url, options = {}) {
	try {
		const response = await fetch(url, { ...options, signal: AbortSignal.timeout(30_000) });
		const text = await response.text();
		let body = text;
		try { body = text ? JSON.parse(text) : null; } catch { /* keep response text */ }
		return { status: response.status, headers: response.headers, body };
	} catch (error) {
		return { status: 0, headers: new Headers(), body: String(error) };
	}
}

function encryptPassword(value, keyword) {
	const key = createHash('sha256').update(keyword, 'utf8').digest();
	const iv = createHash('md5').update(`${keyword}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]).toString('base64');
}

async function readEnv(path) {
	const result = {};
	for (const line of await readFile(path, 'utf8').then(value => value.split(/\r?\n/))) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith('#')) continue;
		const separator = trimmed.indexOf('=');
		if (separator < 1) continue;
		let value = trimmed.slice(separator + 1).trim();
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) value = value.slice(1, -1);
		result[trimmed.slice(0, separator).trim()] = value;
	}
	return result;
}

function required(name) {
	if (!env[name] || env[name] === 'GENERATE_ON_FIRST_RUN') throw new Error(`${name} is required in ${envFile}`);
	return env[name];
}

function parseArgs(values) {
	const result = {};
	for (let index = 0; index < values.length; index += 1) {
		const option = values[index];
		if (option === '--api-base' || option === '--env-file' || option === '--output') {
			if (!values[index + 1]) throw new Error(`${option} requires a value`);
			result[option.slice(2).replaceAll('-', '') === 'apibase' ? 'apiBase' : option.slice(2).replaceAll('-', '') === 'envfile' ? 'envFile' : 'output'] = values[++index];
		} else throw new Error(`unknown option: ${option}`);
	}
	return result;
}

function stripTrailingSlash(value) {
	return String(value).replace(/\/$/, '');
}

function sleep(milliseconds) {
	return new Promise(resolvePromise => setTimeout(resolvePromise, milliseconds));
}
