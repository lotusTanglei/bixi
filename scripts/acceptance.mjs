#!/usr/bin/env node

import { createCipheriv, createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mode = process.env.BIXI_MODE || 'cloud';

if (!['cloud', 'single'].includes(mode)) {
	throw new Error(`BIXI_MODE must be cloud or single, received: ${mode}`);
}

const envFile = process.env.BIXI_ENV_FILE ? resolve(process.env.BIXI_ENV_FILE) : resolve(root, '.env');
const fileEnv = await readEnv(envFile);
const env = { ...fileEnv, ...process.env };
const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');
const username = required('ADMIN_USERNAME');
const password = required('ADMIN_PASSWORD');
const clientSecret = required('OAUTH_PASSWORD_CLIENT_SECRET');
const encodeKey = required('BIXI_ENCODE_KEY');
const createdTitles = ['新增示例任务', '修改示例任务', '删除示例任务'];

let token;
let taskId;
let deleted = false;

try {
	await waitForHealth(`${origin}/healthz`, (response) => response.status === 200, 'frontend');
	await waitForHealth(
		`${apiBase}/admin/actuator/health`,
		(response) => response.status === 200 && response.body?.status === 'UP',
		'backend'
	);

	const loginBody = new URLSearchParams({
		username,
		password: encryptPassword(password, encodeKey),
		grant_type: 'password',
		scope: 'server',
	});
	const login = await request('/auth/oauth2/token', {
		method: 'POST',
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${clientSecret}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: loginBody.toString(),
	});
	assert(login.status === 200 && login.body?.access_token, `login failed (${login.status})`, login.body);
	token = login.body.access_token;

	const userInfo = assertApi(await authorized('/admin/user/info'), 'load user info');
	const currentUser = userInfo.sysUser || userInfo;
	assert(currentUser.username === username, 'user info returned an unexpected username', userInfo);
	assert(!Object.hasOwn(currentUser, 'password'), 'user info exposed a password field', currentUser);
	const permissions = Array.isArray(userInfo.permissions) ? userInfo.permissions : [];
	const roles = Array.isArray(userInfo.roles) ? userInfo.roles.map(String) : [];
	assert(roles.includes('1'), 'administrator role was not returned', userInfo.roles);
	for (const permission of ['demo_task_view', 'demo_task_add', 'demo_task_edit', 'demo_task_del']) {
		assert(permissions.includes(permission), `missing permission: ${permission}`, permissions);
	}

	const menu = assertApi(await authorized('/admin/menu'), 'load menu');
	assert(containsMenuPath(menu, '/demo/task/index'), 'sample task menu is not visible', menu);
	const auditLogBaseline = new Set((await loadAuditLogs()).map((record) => String(record.id)));

	const suffix = `${mode}-${Date.now()}`;
	const title = `双模验收任务-${suffix}`;
	const createPayload = {
		title,
		assignee: '验收负责人',
		priority: 'HIGH',
		taskStatus: 'TODO',
		dueDate: formatDate(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
		remark: `created by ${mode} acceptance`,
	};
	assertApi(await authorized('/admin/demo/task', { method: 'POST', body: createPayload }), 'create task');

	const page = assertApi(
		await authorized(`/admin/demo/task/page?current=1&size=10&title=${encodeURIComponent(title)}`),
		'query task page'
	);
	const createdTask = page.records?.find((record) => record.title === title);
	assert(createdTask?.id, 'created task was not returned by the page query', page);
	taskId = String(createdTask.id);

	const details = assertApi(await authorized(`/admin/demo/task/details/${taskId}`), 'load task details');
	assert(details.title === title && details.priority === 'HIGH', 'task details do not match the created task', details);

	const updatePayload = {
		...details,
		assignee: '双模验收负责人',
		priority: 'MEDIUM',
		taskStatus: 'IN_PROGRESS',
	};
	assertApi(await authorized('/admin/demo/task', { method: 'PUT', body: updatePayload }), 'update task');
	const updated = assertApi(await authorized(`/admin/demo/task/details/${taskId}`), 'reload updated task');
	assert(
		updated.assignee === '双模验收负责人' && updated.taskStatus === 'IN_PROGRESS',
		'updated task values were not persisted',
		updated
	);

	const invalid = await authorized('/admin/demo/task', {
		method: 'POST',
		body: { ...createPayload, title: '' },
	});
	const invalidIsApiError = invalid.status === 200 && Number.isInteger(invalid.body?.code) && invalid.body.code !== 0;
	const invalidIsClientError = invalid.status >= 400 && invalid.status < 500;
	assert(invalid.status !== 0, 'invalid task request did not receive a response', invalid.body);
	assert(invalidIsApiError || invalidIsClientError, 'invalid task request was accepted', invalid.body);
	assert(
		invalid.body && typeof invalid.body === 'object' && typeof invalid.body.msg === 'string' && invalid.body.msg.length > 0,
		'invalid task response did not contain a structured error message',
		invalid.body
	);

	assertApi(await authorized('/admin/demo/task', { method: 'DELETE', body: [taskId] }), 'delete task');
	deleted = true;
	const deletedPage = assertApi(
		await authorized(`/admin/demo/task/page?current=1&size=10&title=${encodeURIComponent(title)}`),
		'query deleted task'
	);
	assert(!deletedPage.records?.some((record) => String(record.id) === taskId), 'deleted task is still visible', deletedPage);

	const auditLogs = await waitForAuditLogs(auditLogBaseline, title, taskId);
	console.log(
		JSON.stringify(
			{
				mode,
				origin,
				login: login.status,
				username: currentUser.username,
				roles,
				permissions: permissions.filter((value) => value.startsWith('demo_task_')).sort(),
				menu: '/demo/task/index',
				crud: ['create', 'page', 'details', 'update', 'invalid-request', 'delete'],
				auditLogs,
			},
			null,
			2
		)
	);
} finally {
	if (token && taskId && !deleted) {
		await authorized('/admin/demo/task', { method: 'DELETE', body: [taskId] }).catch(() => undefined);
	}
}

async function waitForAuditLogs(baselineIds, taskTitle, deletedTaskId) {
	const expected = [
		{ title: '新增示例任务', method: 'POST', marker: taskTitle },
		{ title: '修改示例任务', method: 'PUT', marker: taskTitle },
		{ title: '删除示例任务', method: 'DELETE', marker: deletedTaskId },
	];
	for (let attempt = 1; attempt <= 30; attempt += 1) {
		const records = await loadAuditLogs();
		const currentRunRecords = records.filter((record) => !baselineIds.has(String(record.id)));
		const matches = expected.map((item) =>
			currentRunRecords.find(
				(record) =>
					record.title === item.title &&
					record.method === item.method &&
					String(record.requestUri || '').endsWith('/demo/task') &&
					String(record.params || '').includes(item.marker)
			)
		);
		if (matches.every(Boolean)) {
			return matches.map(({ id, title, method, requestUri }) => ({ id: String(id), title, method, requestUri }));
		}
		await sleep(1000);
	}
	throw new Error(`operation logs were not persisted: ${createdTitles.join(', ')}`);
}

async function loadAuditLogs() {
	const response = await authorized('/admin/log/page?current=1&size=500');
	assert(response.status === 200, `load operation logs returned HTTP ${response.status}`, response.body);
	assert(response.body?.code === 0, 'load operation logs returned an API error', response.body);
	return response.body.data?.records || [];
}

async function waitForHealth(url, predicate, name) {
	for (let attempt = 1; attempt <= 60; attempt += 1) {
		const response = await fetchResponse(url);
		if (predicate(response)) return;
		await sleep(1000);
	}
	throw new Error(`${name} health check did not pass: ${url}`);
}

async function authorized(path, options = {}) {
	return request(path, {
		...options,
		headers: {
			Authorization: `Bearer ${token}`,
			...options.headers,
		},
	});
}

async function request(path, options = {}) {
	const headers = { Accept: 'application/json', ...options.headers };
	let body = options.body;
	if (body && typeof body !== 'string') {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(body);
	}
	return fetchResponse(`${apiBase}${path}`, { ...options, headers, body });
}

async function fetchResponse(url, options = {}) {
	try {
		const response = await fetch(url, { ...options, signal: AbortSignal.timeout(10_000) });
		const text = await response.text();
		let body = text;
		if (text) {
			try {
				body = JSON.parse(text);
			} catch {
				// Health endpoints may intentionally return plain text.
			}
		}
		return { status: response.status, body };
	} catch (error) {
		return { status: 0, body: String(error) };
	}
}

function assertApi(response, action) {
	assert(response.status === 200, `${action} returned HTTP ${response.status}`, response.body);
	assert(response.body?.code === 0, `${action} returned an API error`, response.body);
	return response.body.data;
}

function containsMenuPath(value, expectedPath) {
	if (Array.isArray(value)) return value.some((item) => containsMenuPath(item, expectedPath));
	if (!value || typeof value !== 'object') return false;
	if (value.path === expectedPath) return true;
	return Object.values(value).some((item) => containsMenuPath(item, expectedPath));
}

function encryptPassword(value, keyword) {
	const key = createHash('sha256').update(keyword, 'utf8').digest();
	const iv = createHash('md5').update(`${keyword}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]).toString('base64');
}

function formatDate(value) {
	const year = value.getFullYear();
	const month = String(value.getMonth() + 1).padStart(2, '0');
	const day = String(value.getDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
}

function required(name) {
	const value = env[name];
	if (!value || value === 'GENERATE_ON_FIRST_RUN') throw new Error(`missing generated runtime value: ${name}`);
	return value;
}

function assert(condition, message, details) {
	if (condition) return;
	const suffix = details === undefined ? '' : `\n${JSON.stringify(details, null, 2)}`;
	throw new Error(`${message}${suffix}`);
}

async function readEnv(path) {
	const result = {};
	const content = await readFile(path, 'utf8');
	for (const line of content.split(/\r?\n/)) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith('#')) continue;
		const separator = trimmed.indexOf('=');
		if (separator < 1) continue;
		const key = trimmed.slice(0, separator).trim();
		let value = trimmed.slice(separator + 1).trim();
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) {
			value = value.slice(1, -1);
		}
		result[key] = value;
	}
	return result;
}

function sleep(milliseconds) {
	return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}
