#!/usr/bin/env node

import assert from 'node:assert/strict';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { extractWorkbookText } from './tenant-isolation-support.mjs';

const exec = promisify(execFile);
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const envFile = process.env.BIXI_ENV_FILE || resolve(root, '.env');
const fileEnv = Object.fromEntries((await readFile(envFile, 'utf8')).split(/\r?\n/)
	.filter(line => line.trim() && !line.trim().startsWith('#')).map(line => {
		const separator = line.indexOf('=');
		return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
	}));
const env = { ...fileEnv, ...process.env };
const mode = env.BIXI_MODE || 'single';
assert(['cloud', 'single'].includes(mode), 'BIXI_MODE must be cloud or single');
const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');
for (const key of ['ADMIN_USERNAME', 'ADMIN_PASSWORD', 'OAUTH_PASSWORD_CLIENT_SECRET', 'BIXI_ENCODE_KEY']) {
	assert(env[key], `Missing ${key}`);
}

const suffix = randomBytes(5).toString('hex');
const tenantId = 900000000000000000n + BigInt(parseInt(suffix.slice(0, 8), 16));
const userId = tenantId + 1n;
const tenantOneUserId = tenantId + 2n;
const roleId = tenantId + 3n;
const menuIds = [tenantId + 10n, tenantId + 11n, tenantId + 12n, tenantId + 13n, tenantId + 14n];
const username = `tenant_matrix_${suffix}`;
const roleCode = `tenant_matrix_role_${suffix}`;
const tenantCode = `tenant_matrix_${suffix}`;
const tenantName = `Tenant matrix ${suffix}`;
const tenantOneUserName = 'Tenant one same-name user';
const tenantTwoUserName = 'Tenant two same-name user';
const checked = [];
const tokens = [];
let adminToken;
let tenantToken;
let tenantRefresh;
let tenantTaskId;
let defaultTaskId;
let mysqlContainer;
let redisContainer;

try {
	mysqlContainer = await resolveMysqlContainer();
	redisContainer = await resolveRedisContainer();
	await seedFixtures();
	adminToken = (await login(env.ADMIN_USERNAME, env.ADMIN_PASSWORD)).accessToken;
	const adminInfo = api(await call('/admin/user/info', { token: adminToken }), 'administrator identity');
	assert.equal(String(adminInfo.sysUser.tenantId || 1), '1');

	const defaultTask = api(await call('/admin/demo/task', { token: adminToken, method: 'POST', body: {
		 title: `tenant-one-${suffix}`, assignee: 'tenant-one', priority: 'HIGH', taskStatus: 'TODO',
		 dueDate: '2026-12-31', remark: 'tenant matrix fixture',
	} }), 'create tenant one task');
	defaultTaskId = String((await findTask(adminToken, `tenant-one-${suffix}`)).id);

	const tenantLogin = await login(username, env.ADMIN_PASSWORD, tenantId.toString());
	tenantToken = tenantLogin.accessToken;
	tenantRefresh = tenantLogin.refreshToken;
	const tenantInfo = api(await call('/admin/user/info', { token: tenantToken }), 'tenant two identity');
	assert.equal(String(tenantInfo.sysUser.tenantId), tenantId.toString());
	assert(tenantInfo.permissions.includes('demo_task_view'), 'tenant two task permission missing');
	checked.push('two tenants contain same username/role code while authentication selects the tenant context');

	const tenantTaskResponse = api(await call('/admin/demo/task', { token: tenantToken, method: 'POST', body: {
		title: `tenant-two-${suffix}`, assignee: 'tenant-two', priority: 'MEDIUM', taskStatus: 'TODO',
		dueDate: '2026-12-31', remark: 'forged tenant_id must be ignored', tenantId: 1,
	} }), 'create tenant two task with forged tenantId');
	tenantTaskId = String((await findTask(tenantToken, `tenant-two-${suffix}`)).id);
	const tenantDetails = api(await call(`/admin/demo/task/details/${tenantTaskId}`, { token: tenantToken }), 'tenant two details');
	assert.equal(String(tenantDetails.tenantId), tenantId.toString(), 'INSERT accepted a forged tenant_id');

	const tenantPage = api(await call('/admin/demo/task/page?current=1&size=100', { token: tenantToken }), 'tenant two list');
	assert(tenantPage.records.some(row => String(row.id) === tenantTaskId));
	assert(!tenantPage.records.some(row => String(row.id) === defaultTaskId), 'tenant two list leaked tenant one data');
	const tenantExport = await call('/admin/user/export', { token: tenantToken });
	const tenantExportText = await exportWorkbookText(tenantExport, 'tenant two export');
	assert(tenantExportText.includes(tenantTwoUserName), 'tenant two export omitted the tenant-local user');
	assert(!tenantExportText.includes(tenantOneUserName), 'tenant two export crossed tenant boundary');
	checked.push('tenant-local list, detail, export and INSERT binding');

	const crossDetails = api(await call(`/admin/demo/task/details/${defaultTaskId}`, { token: tenantToken }), 'cross-tenant details');
	assert.equal(crossDetails, null, 'cross-tenant detail returned a row');
	const crossUpdate = await call('/admin/demo/task', { token: tenantToken, method: 'PUT', body: {
		...defaultTask, id: defaultTaskId, title: `cross-update-${suffix}`,
	} });
	assert(!isSuccessfulWrite(crossUpdate), 'cross-tenant update was accepted');
	const crossDelete = await call('/admin/demo/task', { token: tenantToken, method: 'DELETE', body: [defaultTaskId] });
	assert(!isSuccessfulWrite(crossDelete), 'cross-tenant delete was accepted');
	const unchanged = api(await call(`/admin/demo/task/details/${defaultTaskId}`, { token: adminToken }), 'cross-tenant write postcondition');
	assert.equal(unchanged.title, `tenant-one-${suffix}`);
	checked.push('cross-tenant detail/update/delete denied without mutating the owner row');

	assert.equal((await call('/admin/demo/task/page?current=1&size=100', {
		token: tenantToken, headers: { 'X-Tenant-Id': '1' },
	})).status, 403, 'tenant user forged X-Tenant-Id');
	assert.equal((await call('/admin/demo/task/page?current=1&size=100', {
		token: tenantToken, headers: { 'X-Tenant-Scope': 'ALL' },
	})).status, 403, 'ordinary user requested ALL_TENANTS');
	checked.push('forged tenant header and ordinary ALL_TENANTS request denied');

	const allHeaders = { 'X-Tenant-Scope': 'ALL' };
	const allTasks = api(await call('/admin/demo/task/page?current=1&size=100', { token: adminToken, headers: allHeaders }), 'platform all-tenant list');
	assert(allTasks.records.some(row => String(row.id) === defaultTaskId));
	assert(allTasks.records.some(row => String(row.id) === tenantTaskId));
	const allDetails = api(await call(`/admin/demo/task/details/${tenantTaskId}`, { token: adminToken, headers: allHeaders }), 'platform all-tenant detail');
	assert.equal(String(allDetails.tenantId), tenantId.toString());
	const allExport = await call('/admin/user/export', { token: adminToken, headers: allHeaders });
	const allExportText = await exportWorkbookText(allExport, 'platform all-tenant export');
	assert(allExportText.includes(tenantTwoUserName));
	assert(allExportText.includes(tenantOneUserName));
	const allWrite = await call('/admin/demo/task', { token: adminToken, method: 'POST', headers: allHeaders, body: {
		title: `all-write-${suffix}`, assignee: 'must-fail', priority: 'LOW', taskStatus: 'TODO', dueDate: '2026-12-31',
	} });
	assert(!isSuccessfulWrite(allWrite), 'ALL_TENANTS write was accepted');
	const switchedWrite = await call('/admin/demo/task', { token: adminToken, method: 'POST', headers: { 'X-Tenant-Id': tenantId.toString() }, body: {
		title: `switched-write-${suffix}`, assignee: 'must-fail', priority: 'LOW', taskStatus: 'TODO', dueDate: '2026-12-31',
	} });
	assert(!isSuccessfulWrite(switchedWrite), 'read-only tenant switch accepted a write');
	checked.push('platform ALL_TENANTS list/detail/export allowed; ALL_TENANTS and switched writes rejected');

	await setTenantStatusFixture('1');
	assert.equal((await call('/admin/demo/task/page?current=1&size=10', { token: tenantToken })).status, 403,
		'disabled tenant access was not rejected');
	const refreshResponse = await refresh(tenantRefresh, tenantId.toString());
	assert(refreshResponse.status >= 400, 'disabled tenant refresh was accepted');
	await setTenantStatusFixture('0');
	checked.push('fixture-disabled tenant blocks existing access and refresh over HTTP; status mutation is isolated SQL setup');

	console.log(JSON.stringify({ mode, status: 'passed', checked, tenantId: tenantId.toString(), mysqlContainer,
		lifecycleStatusMutation: 'fixture SQL only; tenant access and refresh denial verified over HTTP' }, null, 2));
} catch (error) {
	console.error(JSON.stringify({ mode, status: 'failed', checked, error: error.message }));
	process.exitCode = 1;
} finally {
	if (adminToken) await call('/auth/token/logout', { token: adminToken, method: 'DELETE' }).catch(() => undefined);
	if (tenantToken) await call('/auth/token/logout', { token: tenantToken, method: 'DELETE' }).catch(() => undefined);
	await cleanupFixtures().catch(() => undefined);
}

async function resolveMysqlContainer() {
	if (env.BIXI_MYSQL_CONTAINER) return env.BIXI_MYSQL_CONTAINER;
	const { stdout } = await exec('docker', ['ps', '--filter', 'label=com.docker.compose.service=mysql', '--format', '{{.Names}}']);
	const names = stdout.trim().split(/\s+/).filter(Boolean);
	const preferred = names.filter(name => name.includes(`-${mode}-mysql-`) || name.includes(`${mode}-mysql`));
	const candidates = preferred.length ? preferred : names;
	assert.equal(candidates.length, 1, `Expected one running ${mode} MySQL container; set BIXI_MYSQL_CONTAINER explicitly`);
	return candidates[0];
}

async function resolveRedisContainer() {
	if (env.BIXI_REDIS_CONTAINER) return env.BIXI_REDIS_CONTAINER;
	const { stdout } = await exec('docker', ['ps', '--filter', 'label=com.docker.compose.service=redis', '--format', '{{.Names}}']);
	const names = stdout.trim().split(/\s+/).filter(Boolean);
	const preferred = names.filter(name => name.includes(`-${mode}-redis-`) || name.includes(`${mode}-redis`));
	const candidates = preferred.length ? preferred : names;
	assert.equal(candidates.length, 1, `Expected one running ${mode} Redis container; set BIXI_REDIS_CONTAINER explicitly`);
	return candidates[0];
}

async function seedFixtures() {
	const menuValues = ['demo_task_view', 'demo_task_add', 'demo_task_edit', 'demo_task_del', 'sys_user_export'];
	const menuSql = menuValues.map((permission, index) => `(${menuIds[index]},'tenant matrix ${permission}','${permission}',0,0,${index},'2','0','0',${tenantId},'0','0','0')`).join(',');
	await mysqlSql(`
INSERT INTO sys_tenant (id,name,code,status,max_user_count,del_flag) VALUES (${tenantId},'${tenantName}','${tenantCode}','0',-1,'0');
SET @matrix_hash=(SELECT password FROM sys_user WHERE id=1);
INSERT INTO sys_user (id,username,password,name,tenant_id,del_flag,status,data_status,lock_flag)
VALUES (${tenantOneUserId},'${username}',@matrix_hash,'${tenantOneUserName}',1,'0','0','0','0');
INSERT INTO sys_user (id,username,password,name,tenant_id,del_flag,status,data_status,lock_flag)
VALUES (${userId},'${username}',@matrix_hash,'${tenantTwoUserName}',${tenantId},'0','0','0','0');
INSERT INTO sys_role (id,name,code,sn,del_flag,status,data_status,tenant_id,data_scope)
VALUES (${roleId},'Tenant matrix role','${roleCode}',1,'0','0','0',${tenantId},'1');
	INSERT INTO sys_menu (id,name,permission,parent_id,visible,sn,type,keep_alive,embedded,tenant_id,del_flag,status,data_status)
	VALUES ${menuSql};
	INSERT INTO sys_user_role (user_id,role_id,create_time) VALUES (${userId},${roleId},CURRENT_TIMESTAMP);
	INSERT INTO sys_role_menu (role_id,menu_id,create_time) VALUES ${menuIds.map(id => `(${roleId},${id},CURRENT_TIMESTAMP)`).join(',')};
	`);
}

async function cleanupFixtures() {
	if (!mysqlContainer) return;
	await mysqlSql(`
DELETE FROM sys_user_role WHERE user_id IN (${userId},${tenantOneUserId}) OR role_id=${roleId};
DELETE FROM sys_role_menu WHERE role_id=${roleId} OR menu_id IN (${menuIds.join(',')});
DELETE FROM biz_demo_task WHERE title IN ('tenant-one-${suffix}','tenant-two-${suffix}','all-write-${suffix}','switched-write-${suffix}');
DELETE FROM sys_user WHERE id IN (${userId},${tenantOneUserId});
DELETE FROM sys_role WHERE id=${roleId};
DELETE FROM sys_menu WHERE id IN (${menuIds.join(',')});
DELETE FROM sys_tenant WHERE id=${tenantId};
`);
	await evictTenantStatusCache().catch(() => undefined);
}

async function setTenantStatusFixture(status) {
	assert(status === '0' || status === '1', `invalid fixture tenant status: ${status}`);
	await mysqlSql(`UPDATE sys_tenant SET status='${status}' WHERE id=${tenantId};`);
	await evictTenantStatusCache();
}

async function evictTenantStatusCache() {
	if (!redisContainer) return;
	await exec('docker', ['exec', redisContainer, 'sh', '-c',
		`redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL "tenant_status::${tenantId}"`]);
}

async function mysqlSql(sql) {
	await new Promise((resolvePromise, reject) => {
		const child = spawn('docker', ['exec', '-i', mysqlContainer, 'sh', '-c', 'mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'], {
			stdio: ['pipe', 'pipe', 'pipe'],
		});
		let stderr = '';
		child.stderr.on('data', data => { stderr += data.toString(); });
		child.on('error', reject);
		child.on('close', code => code === 0 ? resolvePromise() : reject(new Error(`fixture SQL failed (${code}): ${stderr.replace(/.*password[^\n]*\n/ig, '').trim()}`)));
		child.stdin.end(sql);
	});
}

async function login(usernameValue, password, tenantHeader) {
	const key = createHash('sha256').update(env.BIXI_ENCODE_KEY, 'utf8').digest();
	const iv = createHash('md5').update(`${env.BIXI_ENCODE_KEY}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	const encrypted = Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
	const response = await fetch(`${apiBase}/auth/oauth2/token`, {
		method: 'POST', signal: AbortSignal.timeout(30000),
		headers: { Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded', ...(tenantHeader ? { 'X-Tenant-Id': tenantHeader } : {}) },
		body: new URLSearchParams({ username: usernameValue, password: encrypted, grant_type: 'password', scope: 'server' }),
	});
	const body = await response.json();
	assert(response.status === 200 && body.access_token, `Login failed (${response.status})`);
	tokens.push(body.access_token);
	return { accessToken: body.access_token, refreshToken: body.refresh_token };
}

async function refresh(refreshToken, tenantHeader) {
	const response = await fetch(`${apiBase}/auth/oauth2/token`, {
		method: 'POST', signal: AbortSignal.timeout(30000),
		headers: { Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded', 'X-Tenant-Id': tenantHeader },
		body: new URLSearchParams({ refresh_token: refreshToken, grant_type: 'refresh_token', scope: 'server' }),
	});
	return { status: response.status, body: await response.json() };
}

async function call(path, { token, method = 'GET', body, headers = {} } = {}) {
	const response = await fetch(`${apiBase}${path}`, {
		method, signal: AbortSignal.timeout(30000),
		headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
			...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const contentType = response.headers.get('content-type') || '';
	if (!contentType.toLowerCase().includes('json')) {
		return {
			status: response.status,
			body: null,
			binary: Buffer.from(await response.arrayBuffer()),
			contentType,
		};
	}
	const text = await response.text();
	let parsed;
	try { parsed = JSON.parse(text); } catch { parsed = null; }
	return { status: response.status, body: parsed, contentType };
}

function api(result, label) {
	assert(result.status === 200 && result.body?.code === 0,
		`${label} failed (${result.status}): ${JSON.stringify(result.body)}`);
	return result.body.data;
}

function isSuccessfulWrite(result) {
	return result.status === 200 && result.body?.code === 0 && result.body?.data !== false;
}

async function exportWorkbookText(result, label) {
	assert(result.status === 200 && result.binary?.length > 0, `${label} failed (${result.status})`);
	return extractWorkbookText(result.binary);
}

async function findTask(token, title) {
	const page = api(await call(`/admin/demo/task/page?current=1&size=20&title=${encodeURIComponent(title)}`, { token }), 'find fixture task');
	const task = page.records?.find(row => row.title === title);
	assert(task?.id, `fixture task ${title} not found`);
	return task;
}
