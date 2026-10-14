#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawn, execFile as execFileCallback } from 'node:child_process';
import { createCipheriv, createHash, randomBytes } from 'node:crypto';
import { access, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { extractWorkbookText } from './tenant-isolation-support.mjs';

const execFile = promisify(execFileCallback);
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const suffix = randomBytes(5).toString('hex');
const prefix = `bb_${suffix}`;
const tenantId = 900000000000000000n + BigInt(parseInt(suffix.slice(0, 8), 16));
const lowRoleId = tenantId + 1n;
const tenantRoleId = tenantId + 2n;
const lowUserId = tenantId + 3n;
const tenantUserId = tenantId + 4n;
const permissions = [
	'acceptance_dict_aggregate_view', 'acceptance_dict_aggregate_add',
	'acceptance_dict_aggregate_edit', 'acceptance_dict_aggregate_del',
	'acceptance_dict_aggregate_import', 'acceptance_dict_aggregate_export',
	'acceptance_sys_dict_item_add', 'acceptance_sys_dict_item_edit',
	'acceptance_sys_dict_item_del', 'acceptance_public_param_view',
	'acceptance_public_param_add', 'acceptance_public_param_edit',
	'acceptance_public_param_del', 'acceptance_public_param_import',
	'acceptance_public_param_export',
	'sys_role_perm', 'sys_role_del', 'sys_user_del',
];
const resources = [
	{
		name: 'dictAggregate', path: '/admin/dictAggregate', field: 'type', table: 'sys_dict',
		headers: ['字典类型', '字典名称', '字典描述', '排序号', '系统标志'],
		payload: (key, label) => ({ type: key, name: label, description: 'BB acceptance', sn: 1,
			systemFlag: '0', children: [] }),
		row: (key, label) => [key, label, 'BB acceptance', '1', '0'],
		change: (detail, value) => ({ ...detail, name: value, children: detail.children || [] }),
		changed: (detail) => detail.name,
	},
	{
		name: 'publicParam', path: '/admin/publicParam', field: 'key', table: 'sys_public_param',
		headers: ['名称', '键', '值', '校验码', '类型，0未知，1系统，2业务', '系统标识，0非系统，1系统', '排序'],
		payload: (key, label) => ({ name: label, key, value: label, validateCode: 'bb', type: '2',
			systemFlag: '0', sn: 1 }),
		row: (key, label) => [label, key, label, 'bb', '2', '0', '1'],
		change: (detail, value) => ({ ...detail, value }),
		changed: (detail) => detail.value,
	},
];

function singleDirectBase(env, fixtureDriver) {
	const port = fixtureDriver === 'native' ? '9999' : env.SINGLE_PORT || '9998';
	return (env.BIXI_SINGLE_DIRECT_BASE_URL
		|| `http://127.0.0.1:${port}`).replace(/\/$/, '');
}

if (process.argv.includes('--self-test')) {
	assert.equal(singleDirectBase({ SINGLE_PORT: '9998' }, 'native'), 'http://127.0.0.1:9999');
	assert.equal(singleDirectBase({}, 'docker'), 'http://127.0.0.1:9998');
	assert.equal(singleDirectBase({ SINGLE_PORT: '19998' }, 'docker'), 'http://127.0.0.1:19998');
	assert.equal(singleDirectBase({ BIXI_SINGLE_DIRECT_BASE_URL: 'http://localhost:19999/' }, 'native'),
		'http://localhost:19999');
	for (const resource of resources) {
		const workbook = await makeXlsx(resource.headers, [resource.row(`${prefix}_test`, 'self-test')]);
		const xml = await extractWorkbookText(workbook);
		assert(xml.includes(`${prefix}_test`) && xml.includes(resource.headers[0]));
	}
	console.log(JSON.stringify({ status: 'passed', check: 'XLSX archives and single direct base' }));
} else {
	await main();
}

async function main() {
	const fileEnv = Object.fromEntries((await readFile(process.env.BIXI_ENV_FILE || resolve(root, '.env'), 'utf8'))
		.split(/\r?\n/).filter(line => line.trim() && !line.trim().startsWith('#')).map(line => {
			const separator = line.indexOf('=');
			return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
		}));
	const env = { ...fileEnv, ...process.env };
	const mode = env.BIXI_MODE || 'single';
	assert(['cloud', 'single'].includes(mode), 'BIXI_MODE must be cloud or single');
	const fixtureDriver = env.BIXI_FIXTURE_DRIVER || 'docker';
	assert(['docker', 'native'].includes(fixtureDriver), 'BIXI_FIXTURE_DRIVER must be docker or native');
	for (const name of ['ADMIN_USERNAME', 'ADMIN_PASSWORD', 'OAUTH_PASSWORD_CLIENT_SECRET', 'BIXI_ENCODE_KEY']) {
		assert(env[name] && env[name] !== 'GENERATE_ON_FIRST_RUN', `Missing ${name}`);
	}
	if (fixtureDriver === 'native') {
		for (const name of ['MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_ROOT_PASSWORD']) {
			assert(env[name], `Native fixtures require ${name}`);
		}
	}
	const remoteMysql = fixtureDriver === 'native' && !['localhost', '127.0.0.1'].includes(env.MYSQL_HOST);
	if (remoteMysql) {
		assert(env.MYSQL_SSL_CA, 'Remote MySQL fixtures require MYSQL_SSL_CA');
		await access(env.MYSQL_SSL_CA);
	}
	const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
	const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');
	const checked = [];
	const evidence = [];
	const failures = [];
	const tokens = [];
	let mysqlContainer;
	let adminToken;
	let seeded = false;
	let cleaned = false;
	let failure;

	async function call(path, { token, method = 'GET', body, headers = {} } = {}) {
		const response = await fetch(`${apiBase}${path}`, {
			method, signal: AbortSignal.timeout(30_000),
			headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers,
				...(body !== undefined && !(body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}) },
			body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
		});
		const contentType = response.headers.get('content-type') || '';
		if (!contentType.toLowerCase().includes('json')) {
			return { status: response.status, contentType, binary: Buffer.from(await response.arrayBuffer()) };
		}
		const text = await response.text();
		let parsed;
		try { parsed = JSON.parse(text); } catch { parsed = null; }
		return { status: response.status, body: parsed, contentType };
	}

	function api(result, label) {
		assert(result.status === 200 && result.body?.code === 0 && result.body?.data !== false,
			`${label}: ${JSON.stringify({ status: result.status, code: result.body?.code })}`);
		return result.body.data;
	}

	function denied(result, label) {
		assert((result.status >= 400 && result.status < 500)
			|| (result.status === 200 && Number.isInteger(result.body?.code) && result.body.code !== 0),
			`${label}: expected a client rejection, got ${JSON.stringify({ status: result.status,
				code: result.body?.code })}`);
	}

	async function rejectForgedInnerClientLookup(base, label) {
		const response = await fetch(`${base}/admin/client/getClientDetailsById/bixi-browser`, {
			headers: { from: 'Y' }, signal: AbortSignal.timeout(30_000),
		});
		const body = await response.text();
		assert(!/client[_-]?secret/i.test(body)
			&& !(env.OAUTH_BROWSER_CLIENT_SECRET && body.includes(env.OAUTH_BROWSER_CLIENT_SECRET)),
			`${label}: client secret exposed (HTTP ${response.status})`);
		let parsed;
		try { parsed = JSON.parse(body); } catch { parsed = null; }
		assert((response.status >= 400 && response.status < 500 && response.status !== 404)
			|| (response.status === 200 && Number.isInteger(parsed?.code) && parsed.code !== 0),
			`${label}: forged internal request was not rejected (HTTP ${response.status})`);
		checked.push(`${label}: forged internal client lookup rejected`);
	}

	async function login(username, password, tenant) {
		const key = createHash('sha256').update(env.BIXI_ENCODE_KEY, 'utf8').digest();
		const iv = createHash('md5').update(`${env.BIXI_ENCODE_KEY}bixi-iv-salt-2025`, 'utf8').digest();
		const cipher = createCipheriv('aes-256-cbc', key, iv);
		const encrypted = Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
		const response = await fetch(`${apiBase}/auth/oauth2/token`, {
			method: 'POST', signal: AbortSignal.timeout(30_000),
			headers: { Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
				'Content-Type': 'application/x-www-form-urlencoded', ...(tenant ? { 'X-Tenant-Id': tenant } : {}) },
			body: new URLSearchParams({ username, password: encrypted, grant_type: 'password', scope: 'server' }),
		});
		const result = await response.json();
		assert(response.status === 200 && result.access_token, `Login failed (${response.status})`);
		tokens.push(result.access_token);
		return result.access_token;
	}

	async function mysqlSql(sql) {
		return new Promise((done, reject) => {
			const native = fixtureDriver === 'native';
			const command = native ? 'mysql' : 'docker';
			const args = native
				? ['--batch', '--silent', '--protocol=TCP',
					...(remoteMysql ? ['--ssl-mode=VERIFY_IDENTITY', `--ssl-ca=${env.MYSQL_SSL_CA}`] : []),
					`--host=${env.MYSQL_HOST}`,
					`--port=${env.MYSQL_PORT}`, `--user=${env.MYSQL_ROOT_USERNAME || 'root'}`, env.MYSQL_DATABASE]
				: ['exec', '-i', mysqlContainer, 'sh', '-c',
					'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --silent --protocol=TCP -uroot "$MYSQL_DATABASE"'];
			const child = spawn(command, args, {
				stdio: ['pipe', 'pipe', 'pipe'],
				env: native ? { ...process.env, MYSQL_PWD: env.MYSQL_ROOT_PASSWORD } : process.env,
			});
			let stdout = '';
			let stderr = '';
			child.stdout.on('data', chunk => { stdout += chunk.toString(); });
			child.stderr.on('data', chunk => { stderr += chunk.toString(); });
			child.on('error', reject);
			child.on('close', code => code === 0 ? done(stdout.trim()) : reject(new Error(`fixture SQL failed (${code}): ${stderr.replace(/.*password[^\n]*\n/ig, '').trim()}`)));
			child.stdin.end(sql);
		});
	}

	async function activeCount(resource, tenant) {
		const value = await mysqlSql(`SELECT COUNT(*) FROM ${resource.table} WHERE tenant_id=${tenant} AND del_flag='0';`);
		assert.match(value, /^\d+$/, `${resource.name} active row count is not numeric: ${value}`);
		return Number(value);
	}

	async function seed(adminId, harmlessMenuId) {
		const menuValues = permissions.map((permission, index) =>
			`(${tenantId + BigInt(index + 10)},'BB ${index}','${permission}',0,'0',${index},'1','0','0',${tenantId},'0','0','0')`).join(',');
		await mysqlSql(`
INSERT INTO sys_tenant (id,name,code,status,max_user_count,del_flag)
VALUES (${tenantId},'BB ${suffix}','${prefix}','0',-1,'0');
SET @bb_hash=(SELECT password FROM sys_user WHERE id=${adminId});
INSERT INTO sys_user (id,username,password,name,tenant_id,del_flag,status,data_status,lock_flag)
VALUES (${lowUserId},'${prefix}_low',@bb_hash,'BB low permission',1,'0','0','0','0'),
       (${tenantUserId},'${prefix}_tenant',@bb_hash,'BB tenant user',${tenantId},'0','0','0','0');
INSERT INTO sys_role (id,name,code,sn,del_flag,status,data_status,tenant_id,data_scope)
VALUES (${lowRoleId},'BB low role','${prefix}_low',1,'0','0','0',1,'1'),
       (${tenantRoleId},'BB tenant role','${prefix}_tenant',1,'0','0','0',${tenantId},'1');
INSERT INTO sys_menu (id,name,permission,parent_id,visible,sn,type,keep_alive,embedded,tenant_id,del_flag,status,data_status)
VALUES ${menuValues};
INSERT INTO sys_user_role (user_id,role_id,create_time)
VALUES (${lowUserId},${lowRoleId},CURRENT_TIMESTAMP),(${tenantUserId},${tenantRoleId},CURRENT_TIMESTAMP);
INSERT INTO sys_role_menu (role_id,menu_id,create_time)
VALUES (${lowRoleId},${harmlessMenuId},CURRENT_TIMESTAMP),
${permissions.map((_, index) => `(${tenantRoleId},${tenantId + BigInt(index + 10)},CURRENT_TIMESTAMP)`).join(',\n')};
`);
	}

	async function cleanup() {
		if (!seeded) return;
		await mysqlSql(`
DELETE FROM sys_dict_item WHERE dict_id IN
  (SELECT id FROM sys_dict WHERE tenant_id IN (1,${tenantId}) AND type LIKE '${prefix}%');
DELETE FROM sys_dict WHERE tenant_id IN (1,${tenantId}) AND (type LIKE '${prefix}%' OR name LIKE '${prefix}%');
DELETE FROM sys_public_param WHERE tenant_id IN (1,${tenantId}) AND (\`key\` LIKE '${prefix}%' OR name LIKE '${prefix}%');
DELETE FROM sys_user_role WHERE user_id IN (${lowUserId},${tenantUserId});
DELETE FROM sys_role_menu WHERE role_id IN (${lowRoleId},${tenantRoleId});
DELETE FROM sys_user WHERE id IN (${lowUserId},${tenantUserId});
DELETE FROM sys_role WHERE id IN (${lowRoleId},${tenantRoleId});
DELETE FROM sys_menu WHERE id BETWEEN ${tenantId + 10n} AND ${tenantId + BigInt(permissions.length + 9)};
DELETE FROM sys_tenant WHERE id=${tenantId};
`);
	}

	async function page(resource, token, key) {
		return api(await call(`${resource.path}/page?current=1&size=20&${resource.field}=${encodeURIComponent(key)}`,
			{ token }), `${resource.name} page`);
	}

	async function create(resource, token, key, label) {
		api(await call(resource.path, { token, method: 'POST', body: resource.payload(key, label) }),
			`${resource.name} create`);
		const result = await page(resource, token, key);
		const row = result.records?.find(value => value[resource.field] === key);
		assert(row?.id, `${resource.name} created row missing: ${key}`);
		return String(row.id);
	}

	async function detail(resource, token, id) {
		return api(await call(`${resource.path}/details/${id}`, { token }), `${resource.name} details`);
	}

	async function upload(resource, token, bytes, filename, headers = {}) {
		const form = new FormData();
		form.append('file', new Blob([bytes]), filename);
		return call(`${resource.path}/import`, { token, method: 'POST', body: form, headers });
	}

	async function importFailure(resource, token, bytes, filename, code, key) {
		const before = (await page(resource, token, key)).total;
		const beforeDb = await activeCount(resource, 1);
		const response = await upload(resource, token, bytes, filename);
		const rejectedAtGateway = code === 'FILE_TOO_LARGE' && response.status === 413;
		const result = rejectedAtGateway ? null : api(response, `${resource.name} ${code} upload`);
		if (!rejectedAtGateway) {
			assert(result.success === false && result.code === code && result.importedRows === 0,
				`${resource.name} expected ${code}: ${JSON.stringify(result)}`);
		}
		const after = (await page(resource, token, key)).total;
		const afterDb = await activeCount(resource, 1);
		assert.equal(after, before, `${resource.name} ${code} changed the filtered row count`);
		assert.equal(afterDb, beforeDb, `${resource.name} ${code} changed the active tenant row count`);
		evidence.push({ resource: resource.name, case: code, status: response.status,
			before, after, beforeDb, afterDb, errors: result?.errors });
		return result;
	}

	async function checkResource(resource, lowToken, tenantToken) {
		const keyA = `${prefix}_${resource.name}_owner`;
		const keyB = `${prefix}_${resource.name}_other`;
		const idA = await create(resource, adminToken, keyA, 'owner tenant');
		const idB = await create(resource, tenantToken, keyB, 'other tenant');
		const originalA = await detail(resource, adminToken, idA);
		const originalB = await detail(resource, tenantToken, idB);
		const validWorkbook = await makeXlsx(resource.headers, [resource.row(`${prefix}_${resource.name}_low`, 'denied')]);

		for (const [label, path, method, body] of [
			['view page', `${resource.path}/page?current=1&size=1`, 'GET'],
			['view detail', `${resource.path}/details/${idA}`, 'GET'],
			['add', resource.path, 'POST', resource.payload(`${prefix}_${resource.name}_low`, 'denied')],
			['edit', resource.path, 'PUT', resource.change(originalA, 'denied')],
			['delete', resource.path, 'DELETE', [idA]],
			['export', `${resource.path}/export`, 'GET'],
		]) {
			const result = await call(path, { token: lowToken, method, body });
			assert.equal(result.status, 403, `${resource.name} low permission ${label}: ${JSON.stringify(result.body)}`);
		}
		assert.equal((await upload(resource, lowToken, validWorkbook, 'valid.xlsx')).status, 403,
			`${resource.name} low permission import`);
		assert.deepEqual(await detail(resource, adminToken, idA), originalA,
			`${resource.name} low permission request changed owner data`);
		checked.push(`BB-09 ${resource.name}: view/add/edit/del/import/export denied`);

		const otherPage = await page(resource, tenantToken, keyA);
		assert.equal(otherPage.total, 0, `${resource.name} page leaked owner row`);
		denied(await call(`${resource.path}/details/${idA}`, { token: tenantToken }), `${resource.name} cross-tenant detail`);
		denied(await call(resource.path, { token: tenantToken, method: 'PUT',
			body: resource.change(originalA, 'cross-tenant update') }), `${resource.name} cross-tenant update`);
		denied(await call(resource.path, { token: tenantToken, method: 'DELETE', body: [idA] }),
			`${resource.name} cross-tenant delete`);
		denied(await call(`${resource.path}/details/${idB}`, { token: adminToken }),
			`${resource.name} reverse cross-tenant detail`);
		assert.equal((await page(resource, adminToken, keyB)).total, 0,
			`${resource.name} reverse cross-tenant page`);
		denied(await call(resource.path, { token: adminToken, method: 'PUT',
			body: resource.change(originalB, 'reverse cross-tenant update') }),
			`${resource.name} reverse cross-tenant update`);
		denied(await call(resource.path, { token: adminToken, method: 'DELETE', body: [idB] }),
			`${resource.name} reverse cross-tenant delete`);
		assert.deepEqual(await detail(resource, adminToken, idA), originalA,
			`${resource.name} cross-tenant write changed owner row`);
		const exportResult = await call(`${resource.path}/export?${resource.field}=${encodeURIComponent(keyA)}`,
			{ token: tenantToken });
		assert(exportResult.status === 200 && exportResult.body === undefined,
			`${resource.name} tenant export failed: ${JSON.stringify({ status: exportResult.status,
				contentType: exportResult.contentType, code: exportResult.body?.code })}`);
		const exportText = exportResult.binary?.length ? await extractWorkbookText(exportResult.binary) : '';
		assert(!exportText.includes(keyA), `${resource.name} tenant export leaked owner row`);
		const ownExport = await call(`${resource.path}/export?${resource.field}=${encodeURIComponent(keyB)}`,
			{ token: tenantToken });
		assert(ownExport.status === 200 && ownExport.binary?.length > 0,
			`${resource.name} own export failed: ${JSON.stringify({ status: ownExport.status,
				contentType: ownExport.contentType, code: ownExport.body?.code,
				message: String(ownExport.body?.msg || '').slice(0, 200) })}`);
		assert((await extractWorkbookText(ownExport.binary)).includes(keyB),
			`${resource.name} tenant export omitted own row`);
		const reverseExport = await call(`${resource.path}/export?${resource.field}=${encodeURIComponent(keyB)}`,
			{ token: adminToken });
		assert(reverseExport.status === 200 && reverseExport.body === undefined,
			`${resource.name} reverse tenant export failed: ${JSON.stringify({ status: reverseExport.status,
				contentType: reverseExport.contentType, code: reverseExport.body?.code })}`);
		if (reverseExport.binary?.length) {
			assert(!(await extractWorkbookText(reverseExport.binary)).includes(keyB),
				`${resource.name} reverse tenant export leaked row`);
		}
		const forgedKey = `${prefix}_${resource.name}_forged`;
		const forgedWorkbook = await makeXlsx(resource.headers, [resource.row(forgedKey, 'forged tenant')]);
		assert.equal((await upload(resource, tenantToken, forgedWorkbook, 'valid.xlsx',
			{ 'X-Tenant-Id': '1' })).status, 403, `${resource.name} forged tenant import`);
		assert.equal((await page(resource, adminToken, forgedKey)).total, 0,
			`${resource.name} forged tenant import created owner row`);
		const localImport = api(await upload(resource, tenantToken,
			await makeXlsx(resource.headers, [resource.row(keyA, 'tenant-local import')]), 'valid.xlsx'),
			`${resource.name} tenant-local import`);
		assert(localImport.success && localImport.code === 'SUCCESS' && localImport.importedRows === 1,
			`${resource.name} tenant-local import failed: ${JSON.stringify(localImport)}`);
		assert.equal((await page(resource, tenantToken, keyA)).total, 1,
			`${resource.name} tenant-local import missing`);
		assert.deepEqual(await detail(resource, adminToken, idA), originalA,
			`${resource.name} tenant-local import changed owner row`);
		checked.push(`BB-10 ${resource.name}: page/detail/write/export/import tenant isolation`);

		const importPrefix = `${prefix}_${resource.name}_boundary`;
		await importFailure(resource, adminToken, Buffer.alloc(0), 'empty.xlsx', 'EMPTY_FILE', importPrefix);
		await importFailure(resource, adminToken, Buffer.from('not an Excel archive'), 'wrong.xlsx',
			'INVALID_FILE', importPrefix);
		await importFailure(resource, adminToken, Buffer.alloc(5 * 1024 * 1024 + 1, 0x41),
			'oversize.xlsx', 'FILE_TOO_LARGE', importPrefix);
		const tooManyRows = Array.from({ length: 1001 }, (_, index) =>
			resource.row(`${importPrefix}_${index}`, `row ${index}`));
		await importFailure(resource, adminToken, await makeXlsx(resource.headers, tooManyRows),
			'rows.xlsx', 'ROW_LIMIT_EXCEEDED', importPrefix);
		const invalidRow = resource.row(`${importPrefix}_invalid`, 'invalid');
		invalidRow[resource.name === 'dictAggregate' ? 0 : 1] = '';
		invalidRow[resource.name === 'dictAggregate' ? 1 : 0] = `${importPrefix}_invalid`;
		const rowFailure = await importFailure(resource, adminToken,
			await makeXlsx(resource.headers, [resource.row(`${importPrefix}_valid`, 'valid'), invalidRow]),
			'validation.xlsx', 'VALIDATION_FAILED', importPrefix);
		assert(rowFailure.errors?.some(error => Number(error.rowNumber) === 3),
			`${resource.name} failed row number missing: ${JSON.stringify(rowFailure.errors)}`);
		checked.push(`BB-11 ${resource.name}: empty/invalid/oversize/row-limit/validation rollback`);

		const duplicateBeforeDb = await activeCount(resource, 1);
		const duplicate = await call(resource.path, { token: adminToken, method: 'POST',
			body: resource.payload(keyA, 'duplicate') });
		const duplicateAfter = (await page(resource, adminToken, keyA)).total;
		const duplicateAfterDb = await activeCount(resource, 1);
		evidence.push({ resource: resource.name, case: 'DUPLICATE_CREATE', status: duplicate.status,
			code: duplicate.body?.code, before: 1, after: duplicateAfter,
			beforeDb: duplicateBeforeDb, afterDb: duplicateAfterDb });
		try {
			assert.equal(duplicateAfter, 1, `${resource.name} duplicate ${resource.field} persisted`);
			assert.equal(duplicateAfterDb, duplicateBeforeDb, `${resource.name} duplicate changed the active tenant row count`);
			denied(duplicate, `${resource.name} duplicate ${resource.field}`);
			checked.push(`BB-12 ${resource.name}: duplicate create rejected`);
		} catch (error) {
			failures.push({ resource: resource.name, case: 'DUPLICATE_CREATE', error: error.message });
		}
		try {
			await importFailure(resource, adminToken,
				await makeXlsx(resource.headers, [resource.row(`${importPrefix}_new`, 'new'),
					resource.row(keyA, 'existing duplicate')]),
				'duplicate.xlsx', 'DUPLICATE_KEY', importPrefix);
			checked.push(`BB-12 ${resource.name}: duplicate import rolled back`);
		} catch (error) {
			failures.push({ resource: resource.name, case: 'DUPLICATE_IMPORT', error: error.message });
			evidence.push({ resource: resource.name, case: 'DUPLICATE_IMPORT',
				after: (await page(resource, adminToken, importPrefix)).total });
		}
		try {
			const edits = ['concurrent first', 'concurrent second'];
			const responses = await Promise.all(edits.map(value => call(resource.path, { token: tenantToken,
				method: 'PUT', body: resource.change(originalB, value) })));
			responses.forEach((result, index) => api(result, `${resource.name} concurrent update ${index + 1}`));
			assert(edits.includes(resource.changed(await detail(resource, tenantToken, idB))),
				`${resource.name} concurrent update left an unexpected value`);
			assert.equal((await page(resource, tenantToken, keyB)).total, 1,
				`${resource.name} concurrent update changed row count`);
			api(await call(resource.path, { token: tenantToken, method: 'DELETE', body: [idB] }),
				`${resource.name} delete`);
			denied(await call(`${resource.path}/details/${idB}`, { token: tenantToken }),
				`${resource.name} deleted detail`);
			checked.push(`BB-12 ${resource.name}: two updates and deleted detail`);
			const replacementId = await create(resource, tenantToken, keyB, 'recreated after delete');
			assert.notEqual(replacementId, idB, `${resource.name} reused a deleted row`);
			assert.equal((await page(resource, tenantToken, keyB)).total, 1,
				`${resource.name} deleted row blocked a replacement`);
			checked.push(`BB-12 ${resource.name}: soft-deleted key can be reused`);
		} catch (error) {
			failures.push({ resource: resource.name, case: 'UPDATE_DELETE_OR_RECREATE', error: error.message });
		}
	}

	try {
		await rejectForgedInnerClientLookup(apiBase, `${mode} public API`);
		if (mode === 'single') {
			const directBase = singleDirectBase(env, fixtureDriver);
			await rejectForgedInnerClientLookup(directBase, 'single direct backend');
		}
		if (fixtureDriver === 'docker') {
			mysqlContainer = env.BIXI_MYSQL_CONTAINER;
			if (!mysqlContainer) {
				const { stdout } = await execFile('docker', ['ps', '--filter', 'label=com.docker.compose.service=mysql',
					'--format', '{{.Names}}']);
				const names = stdout.trim().split(/\s+/).filter(Boolean);
				const matching = names.filter(name => name.includes(`-${mode}-mysql-`) || name.includes(`${mode}-mysql`));
				const candidates = matching.length ? matching : names;
				assert.equal(candidates.length, 1, `Expected one running ${mode} MySQL container; set BIXI_MYSQL_CONTAINER`);
				mysqlContainer = candidates[0];
			}
		}
		adminToken = await login(env.ADMIN_USERNAME, env.ADMIN_PASSWORD);
		const adminInfo = api(await call('/admin/user/info', { token: adminToken }), 'administrator identity');
		assert.equal(String(adminInfo.sysUser.tenantId || 1), '1', 'Administrator must belong to tenant 1');
		const adminId = String(adminInfo.sysUser.id);
		assert(/^\d+$/.test(adminId), 'Administrator ID must be decimal');
		const menuTree = api(await call('/admin/menu/tree', { token: adminToken }), 'administrator menu tree');
		let harmlessMenuId;
		function visit(nodes) {
			for (const node of nodes || []) {
				if (node.permission === 'demo_task_view') harmlessMenuId = String(node.id);
				visit(node.children);
			}
		}
		visit(menuTree);
		assert(/^\d+$/.test(harmlessMenuId || ''), 'Demo view permission is missing');
		seeded = true;
		await seed(adminId, harmlessMenuId);
		const lowToken = await login(`${prefix}_low`, env.ADMIN_PASSWORD);
		const tenantToken = await login(`${prefix}_tenant`, env.ADMIN_PASSWORD, String(tenantId));
		const lowInfo = api(await call('/admin/user/info', { token: lowToken }), 'low permission identity');
		const tenantInfo = api(await call('/admin/user/info', { token: tenantToken }), 'tenant identity');
		assert.equal(String(tenantInfo.sysUser.tenantId), String(tenantId));
		assert(permissions.every(permission => tenantInfo.permissions.includes(permission)),
			'Tenant user is missing Acceptance permissions');
		assert(permissions.every(permission => !lowInfo.permissions.includes(permission)),
			'Low permission user gained an Acceptance permission');
		assert(lowInfo.permissions.includes('demo_task_view'), 'Low permission role lost its granted demo permission');
		for (const [label, path, method, body] of [
			['role grants', '/admin/role/menu', 'PUT', { id: String(lowRoleId), menuIds: '' }],
			['role deletion', '/admin/role', 'DELETE', [String(lowRoleId)]],
			['user deletion', '/admin/user', 'DELETE', [String(lowUserId)]],
		]) {
			denied(await call(path, { token: tenantToken, method, body }), `cross-tenant ${label}`);
			const unchanged = api(await call('/admin/user/info', { token: lowToken }),
				`cross-tenant ${label} victim identity`);
			assert(unchanged.permissions.includes('demo_task_view'),
				`cross-tenant ${label} removed victim permissions`);
		}
		checked.push('Cross-tenant role grants, role deletion and user deletion rejected without side effects');
		api(await call('/admin/param/page?current=1&size=1', { token: adminToken }),
			'administrator legacy parameters');
		for (const path of ['/admin/param/page?current=1&size=1', '/admin/param/details/1',
			'/admin/param/details?key=USER_DEFAULT_ROLE',
			'/admin/param/publicValue/TENANT_DEFAULT_PASSWORD']) {
			denied(await call(path, { token: lowToken }), `low permission legacy parameter ${path}`);
		}
		checked.push('Legacy parameters: low permission reads and internal lookup denied');
		const menu = api(await call('/admin/menu', { token: lowToken }), 'low permission navigation');
		const menuText = JSON.stringify(menu);
		assert(!menuText.includes('/acceptance/dictAggregate/index')
			&& !menuText.includes('/acceptance/publicParam/index'), 'Low permission menu exposed Acceptance resources');
		checked.push('BB-09: Acceptance menus hidden from low permission role');
		for (const resource of resources) {
			try { await checkResource(resource, lowToken, tenantToken); }
			catch (error) { failures.push({ resource: resource.name, case: 'RESOURCE_CHECK', error: error.message }); }
		}
		if (failures.length) throw new Error(`${failures.length} black-box assertions failed`);
	} catch (error) {
		failure = error;
	} finally {
		for (const token of tokens.filter(value => value !== adminToken)) {
			try { api(await call('/auth/token/logout', { token, method: 'DELETE' }), 'cleanup login'); }
			catch (error) { failure ||= error; }
		}
		try { await cleanup(); cleaned = true; } catch (error) { failure ||= error; }
		if (adminToken) {
			try { api(await call('/auth/token/logout', { token: adminToken, method: 'DELETE' }),
				'cleanup administrator login'); }
			catch (error) { failure ||= error; }
		}
	}
	console.log(JSON.stringify({ mode, status: failure ? 'failed' : 'passed', checked, evidence, failures,
		fixture: prefix, cleanup: cleaned ? 'completed' : 'incomplete', error: failure?.message }, null, 2));
	if (failure) process.exitCode = 1;
}

async function makeXlsx(headers, rows) {
	const folder = await mkdtemp(join(tmpdir(), 'bixi-acceptance-negative-'));
	const contents = join(folder, 'contents');
	const archive = join(folder, 'sample.xlsx');
	const xml = value => String(value).replace(/[&<>"']/g, character =>
		({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[character]);
	const cells = (values, rowNumber) => values.map((value, column) =>
		`<c r="${String.fromCharCode(65 + column)}${rowNumber}" t="inlineStr"><is><t>${xml(value)}</t></is></c>`).join('');
	try {
		await mkdir(join(contents, '_rels'), { recursive: true });
		await mkdir(join(contents, 'xl', '_rels'), { recursive: true });
		await mkdir(join(contents, 'xl', 'worksheets'), { recursive: true });
		await Promise.all([
			writeFile(join(contents, '[Content_Types].xml'),
				'<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>'),
			writeFile(join(contents, '_rels', '.rels'),
				'<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'),
			writeFile(join(contents, 'xl', 'workbook.xml'),
				'<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Acceptance" sheetId="1" r:id="rId1"/></sheets></workbook>'),
			writeFile(join(contents, 'xl', '_rels', 'workbook.xml.rels'),
				'<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'),
		]);
		const sheetRows = [headers, ...rows].map((values, index) =>
			`<row r="${index + 1}">${cells(values, index + 1)}</row>`).join('');
		await writeFile(join(contents, 'xl', 'worksheets', 'sheet1.xml'),
			`<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${sheetRows}</sheetData></worksheet>`);
		await execFile('zip', ['-q', '-X', '-r', archive, '.'], { cwd: contents });
		return await readFile(archive);
	} finally {
		await rm(folder, { recursive: true, force: true });
	}
}
