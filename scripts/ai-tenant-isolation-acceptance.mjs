#!/usr/bin/env node

import assert from 'node:assert/strict';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';

const exec = promisify(execFile);
const root = new URL('..', import.meta.url).pathname.replace(/\/$/, '');
const envFile = process.env.BIXI_ENV_FILE || `${root}/.env`;
const fileEnv = Object.fromEntries((await readFile(envFile, 'utf8')).split(/\r?\n/)
	.filter(line => line.trim() && !line.trim().startsWith('#')).map(line => {
		const separator = line.indexOf('=');
		return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
	}));
const env = { ...fileEnv, ...process.env };
const apiBase = (env.BIXI_API_BASE_URL || 'http://127.0.0.1:29997').replace(/\/$/, '');
const mysqlContainer = env.BIXI_MYSQL_CONTAINER || 'bixi-phase2-cloud-mysql-1';
const redisContainer = env.BIXI_REDIS_CONTAINER || 'bixi-phase2-cloud-redis-1';
const evidenceFile = env.BIXI_EVIDENCE_FILE;
for (const key of ['ADMIN_USERNAME', 'ADMIN_PASSWORD', 'OAUTH_PASSWORD_CLIENT_SECRET', 'BIXI_ENCODE_KEY']) {
	assert(env[key], `Missing ${key}`);
}

const suffix = randomBytes(5).toString('hex');
const tenantId = 900000000000000000n + BigInt(parseInt(suffix.slice(0, 8), 16));
const userA = tenantId + 1n;
const userB = tenantId + 2n;
const roleA = tenantId + 3n;
const roleB = tenantId + 4n;
const permissions = [
	'ai_chat_add', 'ai_rag_add', 'ai_session_view', 'ai_session_add', 'ai_session_edit', 'ai_session_del',
	'ai_document_view', 'ai_document_add', 'ai_document_del',
];
const tenantBMenuIds = permissions.map((_, index) => tenantId + 30n + BigInt(index));
const username = `ai_tenant_matrix_${suffix}`;
const roleCodeA = `ai_tenant_role_a_${suffix}`;
const roleCodeB = `ai_tenant_role_b_${suffix}`;
const titleA = `ai-tenant-a-${suffix}`;
const titleB = `ai-tenant-b-${suffix}`;
const markerA = `AI_TENANT_A_MARKER_${suffix}`;
const markerB = `AI_TENANT_B_MARKER_${suffix}`;
const tokens = [];
const checked = [];
let documentA;
let documentB;
let cleanupResult = { attempted: false, mysqlRowsRemaining: null, tenantStatusCacheKeysRemaining: null };

function sql(value) {
	return `'${String(value).replaceAll("'", "''")}'`;
}

async function mysqlSql(statement) {
	await new Promise((resolve, reject) => {
		const child = spawn('docker', ['exec', '-i', mysqlContainer, 'sh', '-c',
			'mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'], { stdio: ['pipe', 'pipe', 'pipe'] });
		let stderr = '';
		child.stderr.on('data', data => { stderr += data.toString(); });
		child.on('error', reject);
		child.on('close', code => code === 0
			? resolve()
			: reject(new Error(`fixture SQL failed (${code}): ${stderr.replace(/.*password[^\n]*\n/ig, '').trim()}`)));
		child.stdin.end(statement);
	});
}

async function mysqlQuery(statement) {
	const { stdout } = await exec('docker', ['exec', mysqlContainer, 'sh', '-c',
		'mysql --protocol=TCP -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -NBe "$1"', 'mysql-query', statement]);
	return stdout.trim();
}

async function deleteRedisKeys(pattern) {
	try {
		const { stdout } = await exec('docker', ['exec', redisContainer, 'sh', '-c',
			`redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern '${pattern}'`]);
		const keys = stdout.split(/\r?\n/).filter(Boolean);
		if (keys.length > 0) {
			await exec('docker', ['exec', redisContainer, 'sh', '-c',
				`redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL ${keys.map(key => `'${key.replaceAll("'", "'\\''")}'`).join(' ')}`]);
		}
	} catch {
		// Redis cache cleanup is best effort; SQL cleanup remains authoritative.
	}
}

function encryptedPassword(password) {
	const key = createHash('sha256').update(env.BIXI_ENCODE_KEY, 'utf8').digest();
	const iv = createHash('md5').update(`${env.BIXI_ENCODE_KEY}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
}

async function login(tenantHeader) {
	const response = await fetch(`${apiBase}/auth/oauth2/token`, {
		method: 'POST', signal: AbortSignal.timeout(30000),
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded', 'X-Tenant-Id': String(tenantHeader),
		},
		body: new URLSearchParams({ username, password: encryptedPassword(env.ADMIN_PASSWORD), grant_type: 'password', scope: 'server' }),
	});
	const body = await response.json();
	assert(response.status === 200 && body.access_token, `Login failed (${response.status})`);
	tokens.push(body.access_token);
	return body.access_token;
}

async function request(path, { token, method = 'GET', body, headers = {} } = {}) {
	const response = await fetch(`${apiBase}${path}`, {
		method, signal: AbortSignal.timeout(60000),
		headers: {
			...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
			...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
		},
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	let bodyJson = null;
	try { bodyJson = JSON.parse(text); } catch { /* non-JSON response */ }
	return { status: response.status, body: bodyJson };
}

function api(result, label) {
	assert(result.status === 200 && result.body?.code === 0,
		`${label} failed (${result.status}): ${JSON.stringify(result.body)}`);
	return result.body.data;
}

function successfulWrite(result) {
	return result.status === 200 && result.body?.code === 0 && result.body?.data !== false;
}

async function seedFixtures() {
	const tenantBMenus = tenantBMenuIds.map((id, index) =>
		`(${id},${sql(`AI tenant ${permissions[index]}`)},${sql(permissions[index])},0,0,${index},'2','0','0',${tenantId},'0','0','0')`).join(',');
	await mysqlSql(`
INSERT INTO sys_tenant (id,name,code,status,max_user_count,del_flag)
VALUES (${tenantId},${sql(`AI tenant ${suffix}`)},${sql(`ai_tenant_${suffix}`)},'0',-1,'0');
SET @matrix_hash=(SELECT password FROM sys_user WHERE id=1);
INSERT INTO sys_user (id,username,password,name,tenant_id,del_flag,status,data_status,lock_flag)
VALUES (${userA},${sql(username)},@matrix_hash,${sql(`AI tenant A ${suffix}`)},1,'0','0','0','0');
INSERT INTO sys_user (id,username,password,name,tenant_id,del_flag,status,data_status,lock_flag)
VALUES (${userB},${sql(username)},@matrix_hash,${sql(`AI tenant B ${suffix}`)},${tenantId},'0','0','0','0');
INSERT INTO sys_role (id,name,code,sn,del_flag,status,data_status,tenant_id,data_scope)
VALUES (${roleA},'AI tenant A role',${sql(roleCodeA)},1,'0','0','0',1,'4'),
       (${roleB},'AI tenant B role',${sql(roleCodeB)},1,'0','0','0',${tenantId},'4');
INSERT INTO sys_menu (id,name,permission,parent_id,visible,sn,type,keep_alive,embedded,tenant_id,del_flag,status,data_status)
VALUES ${tenantBMenus};
INSERT INTO sys_user_role (user_id,role_id,create_time)
VALUES (${userA},${roleA},CURRENT_TIMESTAMP),(${userB},${roleB},CURRENT_TIMESTAMP);
INSERT INTO sys_role_menu (role_id,menu_id,create_time)
SELECT ${roleA},id,CURRENT_TIMESTAMP FROM sys_menu
WHERE tenant_id=1 AND permission IN (${permissions.map(sql).join(',')});
INSERT INTO sys_role_menu (role_id,menu_id,create_time)
VALUES ${tenantBMenuIds.map(id => `(${roleB},${id},CURRENT_TIMESTAMP)`).join(',')};
`);
}

async function cleanupFixtures() {
	cleanupResult.attempted = true;
	await mysqlSql(`
DELETE FROM ai_embedding WHERE tenant_id=${tenantId};
DELETE FROM ai_message WHERE tenant_id=${tenantId};
DELETE FROM ai_conversation WHERE user_id IN (${userA},${userB}) OR tenant_id=${tenantId};
DELETE FROM ai_session WHERE user_id IN (${userA},${userB}) OR tenant_id=${tenantId};
DELETE FROM ai_document WHERE user_id IN (${userA},${userB}) OR tenant_id=${tenantId};
DELETE FROM sys_user_role WHERE user_id IN (${userA},${userB}) OR role_id IN (${roleA},${roleB});
DELETE FROM sys_role_menu WHERE role_id IN (${roleA},${roleB}) OR menu_id IN (${tenantBMenuIds.join(',')});
DELETE FROM sys_user WHERE id IN (${userA},${userB});
DELETE FROM sys_role WHERE id IN (${roleA},${roleB});
DELETE FROM sys_menu WHERE id IN (${tenantBMenuIds.join(',')});
DELETE FROM sys_tenant WHERE id=${tenantId};
`);
	await deleteRedisKeys(`*${username}*`);
	await deleteRedisKeys(`*${tenantId}*`);
	const counts = await mysqlQuery(`SELECT
 (SELECT COUNT(*) FROM sys_tenant WHERE id=${tenantId})+
 (SELECT COUNT(*) FROM sys_user WHERE id IN (${userA},${userB}))+
 (SELECT COUNT(*) FROM sys_role WHERE id IN (${roleA},${roleB}))+
 (SELECT COUNT(*) FROM sys_menu WHERE id IN (${tenantBMenuIds.join(',')}))+
 (SELECT COUNT(*) FROM ai_document WHERE tenant_id=${tenantId})+
 (SELECT COUNT(*) FROM ai_embedding WHERE tenant_id=${tenantId})+
 (SELECT COUNT(*) FROM ai_session WHERE tenant_id=${tenantId})+
 (SELECT COUNT(*) FROM ai_conversation WHERE tenant_id=${tenantId})+
 (SELECT COUNT(*) FROM ai_message WHERE tenant_id=${tenantId})`);
	cleanupResult.mysqlRowsRemaining = Number.parseInt(counts || '0', 10);
	try {
		const { stdout } = await exec('docker', ['exec', redisContainer, 'sh', '-c',
			`redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern 'tenant_status::${tenantId}'`]);
		cleanupResult.tenantStatusCacheKeysRemaining = stdout.split(/\r?\n/).filter(Boolean).length;
	} catch {
		cleanupResult.tenantStatusCacheKeysRemaining = null;
	}
}

let result;
try {
	await seedFixtures();
	const tokenA = await login(1);
	const tokenB = await login(tenantId);
	const identityA = api(await request('/admin/user/info', { token: tokenA }), 'tenant A identity');
	const identityB = api(await request('/admin/user/info', { token: tokenB }), 'tenant B identity');
	assert.equal(String(identityA.sysUser.id), String(userA));
	assert.equal(String(identityA.sysUser.tenantId), '1');
	assert.equal(String(identityB.sysUser.id), String(userB));
	assert.equal(String(identityB.sysUser.tenantId), String(tenantId));
	for (const permission of ['ai_document_view', 'ai_document_add', 'ai_document_del', 'ai_chat_add', 'ai_rag_add']) {
		assert(identityA.permissions.includes(permission), `tenant A missing ${permission}`);
		assert(identityB.permissions.includes(permission), `tenant B missing ${permission}`);
	}
	checked.push('same username selected requested tenant and both principals received AI permissions');

	api(await request('/ai/documents', { token: tokenA, method: 'POST', body: {
		title: titleA, content: `private ${markerA}`, source: 'tenant-a', docType: 'txt',
	} }), 'tenant A document create');
	api(await request('/ai/documents', { token: tokenB, method: 'POST', body: {
		title: titleB, content: `private ${markerB}`, source: 'tenant-b', docType: 'txt',
	} }), 'tenant B document create');
	const listA = api(await request('/ai/documents/list', { token: tokenA }), 'tenant A document list');
	const listB = api(await request('/ai/documents/list', { token: tokenB }), 'tenant B document list');
	const rowA = listA.find(row => row.title === titleA);
	const rowB = listB.find(row => row.title === titleB);
	assert(rowA?.id && rowB?.id, 'created AI documents were not listed');
	documentA = String(rowA.id);
	documentB = String(rowB.id);
	assert(!listA.some(row => row.title === titleB), 'tenant A document list leaked tenant B');
	assert(!listB.some(row => row.title === titleA), 'tenant B document list leaked tenant A');
	checked.push('tenant-local document create/list and ownership binding');

	const searchA = api(await request('/ai/search', { token: tokenA, method: 'POST', body: {
		query: markerA, topK: 5, documentIds: [documentA],
	} }), 'tenant A vector search');
	const searchB = api(await request('/ai/search', { token: tokenB, method: 'POST', body: {
		query: markerB, topK: 5, documentIds: [documentB],
	} }), 'tenant B vector search');
	assert(searchA.some(row => String(row.id) === documentA), 'tenant A search missed own document');
	assert(searchB.some(row => String(row.id) === documentB), 'tenant B search missed own document');
	const crossSearch = api(await request('/ai/search', { token: tokenA, method: 'POST', body: {
		query: markerB, topK: 5, documentIds: [documentB],
	} }), 'cross-tenant vector search');
	assert.equal(crossSearch.length, 0, 'cross-tenant vector search returned a document');
	checked.push('vector search returns only the authenticated user and tenant document');

	const ragA = api(await request('/ai/rag', { token: tokenA, method: 'POST', body: {
		message: `summarize ${markerA}`, documentIds: [documentA],
	} }), 'tenant A RAG');
	const ragB = api(await request('/ai/rag', { token: tokenB, method: 'POST', body: {
		message: `summarize ${markerB}`, documentIds: [documentB],
	} }), 'tenant B RAG');
	assert(ragA.sources?.some(source => String(source.documentId) === documentA), 'tenant A RAG source missing');
	assert(ragB.sources?.some(source => String(source.documentId) === documentB), 'tenant B RAG source missing');
	const crossRag = api(await request('/ai/rag', { token: tokenA, method: 'POST', body: {
		message: `summarize ${markerB}`, documentIds: [documentB],
	} }), 'cross-tenant RAG');
	assert(!crossRag.sources?.some(source => String(source.documentId) === documentB), 'cross-tenant RAG returned the other tenant source');
	checked.push('RAG returns an owned source and no cross-tenant source');

	const forgedHeader = await request('/ai/documents/list', { token: tokenA, headers: { 'X-Tenant-Id': String(tenantId) } });
	assert.equal(forgedHeader.status, 403, 'forged X-Tenant-Id was accepted by AI route');
	checked.push('forged X-Tenant-Id rejected before AI service access');

	const crossDelete = await request(`/ai/documents/${documentB}`, { token: tokenA, method: 'DELETE' });
	assert(!successfulWrite(crossDelete), 'cross-tenant AI delete was accepted');
	const bStillThere = api(await request('/ai/documents/list', { token: tokenB }), 'tenant B post-cross-delete list');
	assert(bStillThere.some(row => String(row.id) === documentB), 'cross-tenant delete mutated tenant B document');
	checked.push('cross-tenant delete rejected without mutation');

	api(await request(`/ai/documents/${documentA}`, { token: tokenA, method: 'DELETE' }), 'tenant A document delete');
	api(await request(`/ai/documents/${documentB}`, { token: tokenB, method: 'DELETE' }), 'tenant B document delete');
	const afterA = api(await request('/ai/search', { token: tokenA, method: 'POST', body: {
		query: markerA, topK: 5, documentIds: [documentA],
	} }), 'tenant A search after delete');
	const afterB = api(await request('/ai/search', { token: tokenB, method: 'POST', body: {
		query: markerB, topK: 5, documentIds: [documentB],
	} }), 'tenant B search after delete');
	assert.equal(afterA.length, 0);
	assert.equal(afterB.length, 0);
	checked.push('owner deletes remove document and vector search visibility');
	result = { observedAt: new Date().toISOString(), composeProject: 'bixi-phase2-cloud', mode: 'cloud', provider: 'local-deterministic', apiBase, status: 'passed', checked, secretsOmitted: true };
} catch (error) {
	result = { observedAt: new Date().toISOString(), composeProject: 'bixi-phase2-cloud', mode: 'cloud', apiBase, status: 'failed', checked, error: error.message, secretsOmitted: true };
	process.exitCode = 1;
} finally {
	for (const token of tokens) await request('/auth/token/logout', { token, method: 'DELETE' }).catch(() => undefined);
	try {
		await cleanupFixtures();
	} catch (error) {
		cleanupResult.error = error.message;
		if (result?.status === 'passed') {
			result.status = 'failed';
			result.error = `cleanup failed: ${error.message}`;
			process.exitCode = 1;
		}
	}
	result.cleanup = cleanupResult;
	if (evidenceFile) await writeFile(evidenceFile, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
	console.log(JSON.stringify(result, null, 2));
}
