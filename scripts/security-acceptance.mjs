#!/usr/bin/env node

import assert from 'node:assert/strict';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fileEnv = Object.fromEntries((await readFile(process.env.BIXI_ENV_FILE || resolve(root, '.env'), 'utf8'))
	.split(/\r?\n/).filter(line => line.trim() && !line.trim().startsWith('#')).map(line => {
		const separator = line.indexOf('=');
		return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
	}));
const env = { ...fileEnv, ...process.env };
const mode = env.BIXI_MODE || 'cloud';
assert(['cloud', 'single'].includes(mode), 'BIXI_MODE must be cloud or single');
const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');
for (const key of ['ADMIN_USERNAME', 'ADMIN_PASSWORD', 'OAUTH_PASSWORD_CLIENT_SECRET', 'BIXI_ENCODE_KEY']) {
	assert(env[key], `Missing ${key}`);
}

const checked = [];
const tokens = [];
let adminToken;
let roleId;
let userId;
let noticeId;
let failure;
const suffix = randomBytes(6).toString('hex');
const username = `sec${suffix}`;
const roleName = `security-${suffix}`;

try {
	adminToken = await login(env.ADMIN_USERNAME, env.ADMIN_PASSWORD);
	const adminInfo = api(await call('/admin/user/info', { token: adminToken }), 'administrator identity');
	const adminUser = adminInfo.sysUser;
	const requiredPermissions = ['sys_user_view', 'sys_user_import', 'sys_role_view', 'sys_role_import',
		'sys_menu_view', 'sys_dept_view', 'sys_dept_export', 'sys_dept_import', 'sys_log_view',
		'sys_client_view', 'sys_client_export', 'sys_token_view', 'sys_system_view'];
	requiredPermissions.push('sys_notice_view', 'sys_notice_add', 'sys_notice_edit', 'sys_notice_del', 'sys_notice_send');
	for (const permission of requiredPermissions) {
		assert(adminInfo.permissions.includes(permission), `Initialization is missing permission ${permission}`);
	}
	checked.push('administrator permission seeds');
	const menuTree = api(await call('/admin/menu/tree', { token: adminToken }), 'administrator menu tree');
	const menuIds = new Map();
	function visit(nodes) {
		for (const node of nodes || []) {
			if (node.permission) menuIds.set(node.permission, String(node.id));
			visit(node.children);
		}
	}
	visit(menuTree);
	for (const permission of ['demo_task_view', 'sys_log_view', 'sys_notice_add']) {
		assert(menuIds.has(permission), `Missing menu for ${permission}`);
	}

	api(await call('/admin/role', { token: adminToken, method: 'POST', body: {
		name: roleName, code: roleName, sn: 999, description: 'Disposable security acceptance role',
	} }), 'create isolated role');
	const roles = api(await call(`/admin/role/page?name=${roleName}&current=1&size=10`, { token: adminToken }), 'find role');
	roleId = roles.records.find(role => role.name === roleName)?.id;
	assert(roleId, 'Created role was not found');
	await setMenus([menuIds.get('demo_task_view')]);
	const password = `Sec!${randomBytes(12).toString('base64url')}`;
	api(await call('/admin/user', { token: adminToken, method: 'POST', body: {
		username, name: username, password, deptId: adminUser.deptId, role: [roleId], post: [], lockFlag: '0',
	} }), 'create isolated user');
	const users = api(await call(`/admin/user/page?username=${username}&current=1&size=10`, { token: adminToken }), 'find user');
	userId = users.records.find(user => user.username === username)?.id;
	assert(userId, 'Created user was not found');
	let creationAudit;
	for (let attempt = 0; attempt < 20 && !creationAudit; attempt++) {
		const logs = api(await call('/admin/log/page?current=1&size=100&descs=id', { token: adminToken }), 'audit persistence');
		creationAudit = logs.records.find(log => log.title === '添加用户' && log.params?.includes(username));
		if (!creationAudit) await delay(300);
	}
	assert(creationAudit, 'User creation must persist an audit record');
	assert(String(creationAudit.createBy) === String(adminUser.id), 'Audit must retain the authenticated administrator');
	assert(!creationAudit.params.includes(password) && !/"password"\s*:/i.test(creationAudit.params),
		'User creation audit must exclude password fields and values');
	checked.push('persisted audit retains actor and excludes the supplied password');
	const userToken = await login(username, password);
	api(await call('/admin/user/info', { token: userToken }), 'ordinary user identity');
	for (const path of ['/admin/user/me', `/admin/user/me?id=${adminUser.id}&userId=${adminUser.id}`]) {
		const profile = api(await call(path, { token: userToken }), 'ordinary user personal profile');
		assert.equal(String(profile.id), String(userId), 'Personal profile must use the authenticated user ID');
		assert.equal(profile.username, username, 'Personal profile must belong to the authenticated user');
		assert(!Object.hasOwn(profile, 'password') && !Object.hasOwn(profile, 'salt'),
			'Personal profile must exclude password and salt');
	}
	assert.equal((await call(`/admin/user/details/${userId}`, { token: userToken })).status, 403,
		'Personal profile access must not grant management details access, even for the same user');
	checked.push('ordinary user personal profile ignores requested IDs, omits credentials and preserves management denial');
	api(await call('/admin/menu', { token: userToken }), 'ordinary user navigation');
	api(await call('/admin/demo/task/page?current=1&size=1', { token: userToken }), 'initial granted permission');
	checked.push('ordinary user identity, navigation and granted business read');
	// A fresh principal guarantees a fresh consent prompt on every run.
	const formResult = await promisify(execFile)('python3', [resolve(root, 'scripts/auth-form-acceptance.py')], {
		env: { ...env, BIXI_AUTH_TEST_USERNAME: username, BIXI_AUTH_TEST_PASSWORD: password },
		timeout: 120000,
	}).then(({ stdout }) => JSON.parse(stdout), error => {
		let result;
		try { result = JSON.parse(error.stdout); } catch { /* Only report a sanitized summary. */ }
		throw new Error(result?.error ? `Browser authentication acceptance: ${result.error}` : 'Browser authentication acceptance failed');
	});
	assert(formResult.status === 'passed', 'Browser authentication acceptance failed');
	checked.push(...formResult.checked);

	const denied = [
		['/admin/user/page?current=1&size=1'], ['/admin/user/details/1'], ['/admin/user/options'],
		['/admin/role/page?current=1&size=1'], ['/admin/role/details/1'], ['/admin/role/list'],
		['/admin/role/getRoleList', 'POST', [1]], ['/admin/menu/tree'], ['/admin/menu/tree/1'], ['/admin/menu/1100'],
		['/admin/dept/list'], ['/admin/dept/tree'], ['/admin/dept/export'],
		['/admin/log/page?current=1&size=1'], ['/admin/client/page?current=1&size=1'], ['/admin/client/bixi'],
		['/admin/client/export'], ['/admin/client/sync', 'PUT'],
		['/admin/token/page', 'POST', { current: 1, size: 10 }], ['/admin/system/cache'],
		['/admin/notice/page?current=1&size=1'], ['/admin/notice/-1'],
		['/admin/notice', 'POST', { title: 'unauthorized draft' }],
		['/admin/notice', 'PUT', { id: -1, title: 'unauthorized change' }],
		['/admin/notice/-1', 'DELETE'], ['/admin/notice/send/-1', 'POST'],
		['/admin/user-notice/record/page?current=1&size=1'],
	];
	for (const [path, method = 'GET', body] of denied) {
		const result = await call(path, { token: userToken, method, body });
		assert.equal(result.status, 403, `Ordinary user must receive 403 for ${method} ${path}; got ${result.status}`);
	}
	checked.push(`${denied.length} management HTTP operations denied`);
	assert.equal((await call('/admin/user-notice', { token: userToken, method: 'POST',
		body: { noticeId: -1, userId } })).status, 405, 'Clients must not manufacture notice recipient records');
	api(await call('/admin/user-notice/page?current=1&size=1&userId=1', { token: userToken }), 'personal inbox');

	await setMenus([menuIds.get('demo_task_view'), menuIds.get('sys_log_view')]);
	api(await call('/admin/log/page?current=1&size=1', { token: userToken }), 'new permission on existing token');
	await setMenus([menuIds.get('demo_task_view')]);
	assert.equal((await call('/admin/log/page?current=1&size=1', { token: userToken })).status, 403,
		'Partial revocation must affect the existing token');
	api(await call('/admin/demo/task/page?current=1&size=1', { token: userToken }), 'retained permission');
	await setMenus([menuIds.get('demo_task_view'), menuIds.get('sys_notice_add')]);
	const options = api(await call('/admin/user/options', { token: userToken }), 'notice recipient options');
	assert(options.some(user => String(user.id) === String(userId)), 'New user is missing from recipient options');
	for (const user of options) {
		assert.deepEqual(Object.keys(user).sort(), ['id', 'name', 'username'], 'Recipient options must contain only selection fields');
	}
	api(await call('/admin/role/list', { token: userToken }), 'notice role options');
	api(await call('/admin/dept/tree', { token: userToken }), 'notice department options');
	assert.equal((await call('/admin/user/page?current=1&size=1', { token: userToken })).status, 403,
		'Notice permissions must not grant user management read access');
	checked.push('notice recipient options use minimal fields without user management access');
	await setMenus(['demo_task_view', 'sys_notice_add', 'sys_notice_edit'].map(permission => menuIds.get(permission)));
	const noticeTitle = `security-draft-${suffix}`;
	const draft = { title: noticeTitle, content: 'private draft', type: '0', priority: '0', status: '1',
		targetType: '3', targetIds: String(userId), senderId: adminUser.id };
	api(await call('/admin/notice', { token: userToken, method: 'POST', body: draft }), 'create notice with forged publication status');
	const notices = api(await call(`/admin/notice/page?title=${noticeTitle}&current=1&size=10`, { token: adminToken }), 'find draft');
	noticeId = notices.records.find(notice => notice.title === noticeTitle)?.id;
	assert(noticeId, 'Created notice was not found');
	const savedDraft = api(await call(`/admin/notice/${noticeId}`, { token: adminToken }), 'draft publication state');
	assert.equal(String(savedDraft.senderId), String(userId), 'Creation must use the authenticated sender');
	assert.equal(savedDraft.status, '0',
		'Creation must ignore a forged publication status');
	api(await call('/admin/notice', { token: userToken, method: 'PUT', body: { ...draft, id: noticeId, content: 'revised private draft' } }),
		'edit draft with forged publication status');
	const editedDraft = api(await call(`/admin/notice/${noticeId}`, { token: adminToken }), 'edited draft publication state');
	assert.equal(String(editedDraft.senderId), String(userId), 'Editing must preserve the authenticated sender');
	assert.equal(editedDraft.status, '0',
		'Editing must not publish a draft');
	assert.equal((await call(`/admin/notice/send/${noticeId}`, { token: userToken, method: 'POST' })).status, 403,
		'Creating and editing permissions must not allow publishing');
	const records = api(await call(`/admin/user-notice/record/page?noticeId=${noticeId}&current=1&size=10`, { token: adminToken }), 'draft recipient records');
	const receiptId = records.records.find(record => String(record.userId) === String(userId))?.id;
	assert(receiptId, 'Draft must preserve the selected recipient for management');
	const inboxPath = `/admin/user-notice/page?noticeId=${noticeId}&current=1&size=10`;
	assert.equal(api(await call(inboxPath, { token: userToken }), 'draft inbox visibility').records.length, 0);
	assert.equal((await call(`/admin/user-notice/${receiptId}`, { token: userToken })).body?.code, 1,
		'A recipient must not read a draft by guessing their receipt ID');
	await setMenus(['demo_task_view', 'sys_notice_add', 'sys_notice_edit', 'sys_notice_send'].map(permission => menuIds.get(permission)));
	api(await call(`/admin/notice/send/${noticeId}`, { token: userToken, method: 'POST' }), 'authorized publication');
	const received = api(await call(inboxPath, { token: userToken }), 'published inbox visibility');
	assert.equal(received.records.length, 1, 'Published notice must reach its selected recipient');
	assert.equal(received.records[0].content, 'revised private draft');
	api(await call(`/admin/user-notice/${receiptId}`, { token: userToken }), 'published receipt details');
	api(await call('/admin/user-notice', { token: userToken, method: 'PUT', body: { id: receiptId, isRead: '1' } }), 'mark published notice read');
	api(await call(`/admin/notice/send/${noticeId}`, { token: userToken, method: 'POST' }), 'resend published reminder');
	const resent = api(await call(inboxPath, { token: userToken }), 'resent inbox visibility');
	assert.equal(resent.records.length, 1, 'Resending must not duplicate recipients');
	assert.equal(String(resent.records[0].id), String(receiptId), 'Resending must preserve the receipt ID');
	assert.equal(resent.records[0].isRead, '1', 'Resending must preserve read state');
	assert.equal(api(await call(`${inboxPath}&userId=${userId}`, { token: adminToken }), 'outsider inbox isolation').records.length, 0,
		'An inbox request cannot impersonate another recipient');
	assert.equal((await call('/admin/notice', { token: userToken, method: 'PUT', body: { ...draft, id: noticeId, content: 'overwrite published' } })).body?.code, 1,
		'Even an editor cannot replace published content');
	api(await call(`/admin/notice/${noticeId}`, { token: adminToken, method: 'DELETE' }), 'delete isolated notice');
	noticeId = undefined;
	assert.equal(api(await call(inboxPath, { token: userToken }), 'deleted notice inbox visibility').records.length, 0);
	assert.equal((await call(`/admin/user-notice/${receiptId}`, { token: userToken })).body?.code, 1);
	checked.push('draft publication and sender cannot be forged; published recipients stay isolated; resend preserves receipts and read state');
	await setMenus([]);
	assert.equal((await call('/admin/demo/task/page?current=1&size=1', { token: userToken })).status, 403,
		'Full revocation must affect the existing token');
	checked.push('existing token observes grant, partial revocation and full revocation');
	const auditBeforeLogout = api(await call('/admin/log/page?current=1&size=100&descs=id', { token: adminToken }), 'logout audit baseline');
	const existingAuditIds = new Set(auditBeforeLogout.records.map(log => String(log.id)));
	api(await call('/auth/token/logout', { token: userToken, method: 'DELETE' }), 'ordinary user logout');
	tokens.splice(tokens.indexOf(userToken), 1);
	let logoutAudit;
	for (let attempt = 0; attempt < 20 && !logoutAudit; attempt++) {
		const logs = api(await call('/admin/log/page?current=1&size=100&descs=id', { token: adminToken }), 'logout audit persistence');
		assert(!logs.records.some(log => log.params?.includes(userToken)), 'Logout audit must not persist the Bearer token');
		logoutAudit = logs.records.find(log => !existingAuditIds.has(String(log.id))
			&& log.title === '退出成功' && String(log.createBy) === String(userId));
		if (!logoutAudit) await delay(300);
	}
	assert(logoutAudit, 'Logout must retain the authenticated actor in its audit record');
	checked.push('logout audit retains actor without the Bearer token');
} catch (error) {
	failure = error;
} finally {
	// A user's identity must still exist when their token is logged out.
	for (const token of tokens.filter(value => value !== adminToken)) {
		try {
			api(await call('/auth/token/logout', { token, method: 'DELETE' }), 'cleanup login');
		} catch (error) {
			failure ||= error;
		}
	}
	// Only delete the records created by this run; never reset shared data or caches.
	if (noticeId) {
		try {
			api(await call(`/admin/notice/${noticeId}`, { token: adminToken, method: 'DELETE' }), 'cleanup notice');
		} catch (error) {
			failure ||= error;
		}
	}
	for (const [path, id] of [['/admin/user', userId], ['/admin/role', roleId]]) {
		if (!id) continue;
		try {
			api(await call(path, { token: adminToken, method: 'DELETE', body: [id] }), `cleanup ${path}`);
		} catch (error) {
			failure ||= error;
		}
	}
	if (adminToken) {
		try {
			api(await call('/auth/token/logout', { token: adminToken, method: 'DELETE' }), 'cleanup administrator login');
		} catch (error) {
			failure ||= error;
		}
	}
}

if (failure) {
	console.error(JSON.stringify({ mode, status: 'failed', checked, error: failure.message }));
	process.exitCode = 1;
} else {
	console.log(JSON.stringify({ mode, status: 'passed', checked, cleanup: 'completed' }, null, 2));
}

async function setMenus(ids) {
	api(await call('/admin/role/menu', { token: adminToken, method: 'PUT', body: { id: roleId, menuIds: ids.join(',') } }),
		'update isolated role permissions');
}

async function login(name, password) {
	const key = createHash('sha256').update(env.BIXI_ENCODE_KEY, 'utf8').digest();
	const iv = createHash('md5').update(`${env.BIXI_ENCODE_KEY}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	const encrypted = Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
	const response = await fetch(`${apiBase}/auth/oauth2/token`, {
		method: 'POST', signal: AbortSignal.timeout(30000),
		headers: { Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded' },
		body: new URLSearchParams({ username: name, password: encrypted, grant_type: 'password', scope: 'server' }),
	});
	const body = await response.json();
	assert(response.status === 200 && body.access_token, `Login failed (${response.status})`);
	tokens.push(body.access_token);
	return body.access_token;
}

async function call(path, { token, method = 'GET', body } = {}) {
	const response = await fetch(`${apiBase}${path}`, {
		method, signal: AbortSignal.timeout(30000),
		headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	let parsed;
	try { parsed = JSON.parse(text); } catch { parsed = null; }
	return { status: response.status, body: parsed };
}

function api(result, label) {
	assert(result.status === 200 && result.body?.code === 0, `${label} failed (${result.status})`);
	assert(result.body.data !== false, `${label} returned false`);
	return result.body.data;
}
