#!/usr/bin/env node

import { createCipheriv, createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { dirname, isAbsolute, join, normalize, relative, resolve, sep } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { importOwnedGeneratorTable } from './generator-acceptance-ownership.mjs';
import {
	authTokenPathForMode,
	functionNameForTable,
	healthChecksForMode,
	matchesGeneratorAudit,
	matchesOperationAudit,
	readResponseBodyLimited,
	runBoundedProcess,
	safeArtifactFilename,
} from './generator-acceptance-support.mjs';

const MAX_ZIP_RESPONSE_BYTES = 32 * 1024 * 1024;
const MAX_XLSX_RESPONSE_BYTES = 16 * 1024 * 1024;
const MAX_PROCESS_STDERR_BYTES = 64 * 1024;
const PROCESS_TIMEOUT_MILLIS = 30_000;

const HELP = `Usage: node scripts/generator-acceptance.mjs [options]

Runs the Generator HTTP acceptance flow without printing credentials.

Options:
  --mode <cloud|single>       Deployment mode (default: BIXI_MODE or cloud)
  --env-file <path>           Runtime env file (default: BIXI_ENV_FILE or .env)
  --api-base <url>            Direct backend base URL (default: gateway/single port)
  --output-dir <path>         Artifact directory (default: target/generator-acceptance/*)
  --ds-name <name>            Generator datasource name (default: master)
  --standalone-table <name>   Standalone table (default: sys_public_param)
  --parent-table <name>       Parent table (default: sys_dict)
  --child-table <name>        Child table (default: sys_dict_item)
  --skip-sync                 Diagnostic only: skip the destructive sync assertion
  --skip-server-generate      Skip POST /generator/code; ZIP generation still runs
  --help                      Show this help

The script leaves imported Generator metadata in place for repeatability, refuses
to overwrite unowned configuration, and preserves downloaded artifacts.
`;

const args = parseArgs(process.argv.slice(2));
if (args.help) {
	process.stdout.write(HELP);
	process.exit(0);
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mode = args.mode || process.env.BIXI_MODE || 'cloud';
assert(['cloud', 'single'].includes(mode), `mode must be cloud or single, received: ${mode}`);
const envFile = resolve(args.envFile || process.env.BIXI_ENV_FILE || resolve(root, '.env'));
const fileEnv = await readEnv(envFile);
const env = { ...fileEnv, ...process.env };
const defaultPort = mode === 'cloud' ? required('GATEWAY_PORT') : required('SINGLE_PORT');
const apiBase = stripTrailingSlash(args.apiBase || env.BIXI_API_BASE_URL || `http://127.0.0.1:${defaultPort}`);
const generatorPrefix = mode === 'cloud' ? '/gen' : '/admin';
const auditContextPath = mode === 'single' ? '/admin' : '';
const dsName = args.dsName || env.BIXI_GENERATOR_DS_NAME || 'master';
const standaloneTableName = args.standaloneTable || 'sys_public_param';
const parentTableName = args.parentTable || 'sys_dict';
const childTableName = args.childTable || 'sys_dict_item';
const builtInStyle = '1872559165395542017';
const ownershipMarker = 'generator-acceptance';

let token;
const ownedTables = new Set();
const importedTables = new Set();
let artifactRoot;

await runAcceptance();

async function runAcceptance() {
	await verifyHealth();
	await verifyAnonymousDenied();
	token = await login();

	const userInfo = assertApi(await authorized('/admin/user/info'), 'load user info');
	const currentUser = userInfo.sysUser || userInfo;
	assert(currentUser.username === required('ADMIN_USERNAME'), 'user info returned an unexpected username');
	const permissions = Array.isArray(userInfo.permissions) ? userInfo.permissions : [];
	const expectedPermissions = [
		'codegen_table_view',
		'codegen_table_edit',
		'codegen_table_sync',
		'codegen_table_generate',
		'codegen_table_export',
	];
	for (const permission of expectedPermissions) {
		assert(permissions.includes(permission), `missing permission: ${permission}`, permissions);
	}
	const menu = assertApi(await authorized('/admin/menu'), 'load menu');
	assert(containsMenuPath(menu, '/gen/table/index'), 'Generator table menu is not visible');

	const auditBaseline = new Set((await loadAuditLogs()).map((record) => String(record.id)));
	await importTable(standaloneTableName);
	if (!args.skipSync) {
		assert(
			ownedTables.has(standaloneTableName),
			`refusing to sync unowned Generator metadata for ${dsName}.${standaloneTableName}; use a clean database`
		);
		await assertApi(
			await authorized(`${generatorPrefix}/table/sync/${encodeURIComponent(dsName)}/${encodeURIComponent(standaloneTableName)}`, {
				method: 'POST',
			}),
			'sync standalone table'
		);
	}
	const standalone = await configureTable(standaloneTableName, {
		author: ownershipMarker,
		style: builtInStyle,
		moduleName: 'acceptance',
		functionName: functionNameForTable(standaloneTableName),
		childTableName: null,
		mainField: null,
		childField: null,
	});

	artifactRoot = await prepareArtifactRoot(args.outputDir);
	const standaloneResult = await exerciseGeneration('standalone', standalone);

	await importTable(childTableName);
	const parent = await configureTable(parentTableName, {
		author: ownershipMarker,
		style: builtInStyle,
		moduleName: 'acceptance',
		functionName: 'dictAggregate',
		childTableName,
		mainField: 'id',
		childField: 'dict_id',
	});
	const parentChildResult = await exerciseGeneration('parent-child', parent);

	const summary = {
		mode,
		apiBase,
		dsName,
		username: currentUser.username,
		permissions: expectedPermissions,
		menu: '/gen/table/index',
		unauthorized: 'rejected',
		standalone: standaloneResult,
		parentChild: parentChildResult,
		sync: args.skipSync ? 'skipped by diagnostic option' : 'passed',
		artifacts: artifactRoot,
		auditLogs: await waitForAuditLogs(auditBaseline, [standaloneResult, parentChildResult]),
		limitations: [
			'Generator metadata imported by this run is marked with author=generator-acceptance and retained because the public API has no metadata delete endpoint.',
			...(args.skipSync ? ['Sync was skipped for diagnostic continuation; a default run still fails on any sync API error.'] : []),
			...(args.skipServerGenerate ? ['Server-side project generation was skipped for diagnostic continuation; ZIP generation was still verified.'] : []),
		],
	};
	console.log(JSON.stringify(summary, null, 2));
}

async function verifyHealth() {
	for (const check of healthChecksForMode(mode)) {
		await waitForHealth(`${apiBase}${check.path}`, check.name);
	}
}

async function verifyAnonymousDenied() {
	const path = `${generatorPrefix}/table/config/${encodeURIComponent(dsName)}/${encodeURIComponent(standaloneTableName)}`;
	const response = await request(path);
	assertDenied(response, 'anonymous Generator metadata access');
}

async function login() {
	const loginBody = new URLSearchParams({
		username: required('ADMIN_USERNAME'),
		password: encryptPassword(required('ADMIN_PASSWORD'), required('BIXI_ENCODE_KEY')),
		grant_type: 'password',
		scope: 'server',
	});
	const response = await request(authTokenPathForMode(mode), {
		method: 'POST',
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${required('OAUTH_PASSWORD_CLIENT_SECRET')}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: loginBody.toString(),
	});
	assert(response.status === 200 && response.body?.access_token, `login failed (${response.status})`);
	return response.body.access_token;
}

async function importTable(tableName) {
	const result = await importOwnedGeneratorTable({
		request: authorized,
		prefix: generatorPrefix,
		dsName,
		tableName,
		marker: ownershipMarker,
	});
	const table = result.table;
	assert(table?.id && table.tableName === tableName && table.dsName === dsName, `invalid imported table: ${tableName}`, table);
	assert(Array.isArray(table.fieldList) && table.fieldList.length > 0, `imported table has no fields: ${tableName}`);
	if (result.created) importedTables.add(tableName);
	ownedTables.add(tableName);
	return table;
}

async function configureTable(tableName, changes) {
	const table = await importTable(tableName);
	assert(ownedTables.has(tableName), `refusing to configure unowned Generator configuration ${dsName}.${tableName}`);
	return updateTableConfiguration(tableName, table, changes);
}

async function updateTableConfiguration(tableName, table, changes) {
	const payload = { ...table, ...changes };
	delete payload.fieldList;
	delete payload.childFieldList;
	delete payload.groupList;
	assertApi(await authorized(`${generatorPrefix}/table`, { method: 'PUT', body: payload }), `configure ${tableName}`);
	const configured = assertApi(
		await authorized(`${generatorPrefix}/table/config/${encodeURIComponent(dsName)}/${encodeURIComponent(tableName)}`),
		`reload ${tableName}`
	);
	for (const [key, expected] of Object.entries(changes)) {
		assert(String(configured[key] ?? '') === String(expected ?? ''), `${tableName}.${key} was not persisted`, configured);
	}
	return configured;
}

async function exerciseGeneration(label, table) {
	const preview = await authorized(`${generatorPrefix}/generator/preview?tableId=${encodeURIComponent(table.id)}`);
	assert(preview.status === 200 && Array.isArray(preview.body) && preview.body.length > 0, `${label} preview failed`, preview.body);
	const versions = new Set(preview.body.map((artifact) => artifact.templateVersion));
	assert(versions.size === 1 && !versions.has(undefined) && !versions.has(''), `${label} preview versions differ`, [...versions]);
	const templateVersion = [...versions][0];
	const previewPaths = preview.body.map((artifact) => validateArtifactPath(artifact.codePath));
	assert(new Set(previewPaths).size === previewPaths.length, `${label} preview contains duplicate paths`, previewPaths);
	assert(preview.body.every((artifact) => typeof artifact.code === 'string' && artifact.code.length > 0), `${label} preview contains empty code`);

	const stale = await authorized(`${generatorPrefix}/generator/code`, {
		method: 'POST',
		body: { tableIds: [table.id], templateVersion: `${templateVersion}-stale`, overwrite: true },
	});
	assertStaleVersionRejected(stale, label);

	if (!args.skipServerGenerate) {
		assertApi(await authorized(`${generatorPrefix}/generator/code`, {
			method: 'POST',
			body: { tableIds: [table.id], templateVersion, overwrite: true },
		}), `${label} server generation`);
	}

	const download = await authorizedBinary(
		`${generatorPrefix}/generator/download?tableIds=${encodeURIComponent(table.id)}&templateVersion=${encodeURIComponent(templateVersion)}`,
		MAX_ZIP_RESPONSE_BYTES
	);
	assert(download.status === 200, `${label} ZIP download returned HTTP ${download.status}`);
	assert(download.body.length > 4 && download.body.subarray(0, 2).toString('ascii') === 'PK', `${label} download is not a ZIP`);
	const zipPath = resolve(artifactRoot, safeArtifactFilename(label, table.tableName, 'zip'));
	assert(isWithin(artifactRoot, zipPath), `${label} ZIP path escapes the artifact directory`);
	await writeFile(zipPath, download.body, { flag: 'wx' });
	const extractRoot = resolve(artifactRoot, label);
	const extractedPaths = await safeExtractZip(zipPath, extractRoot);
	assertSamePaths(extractedPaths, previewPaths, `${label} ZIP differs from preview`);

	const exported = await authorizedBinary(
		`${generatorPrefix}/table/export?dsName=${encodeURIComponent(dsName)}&tableName=${encodeURIComponent(table.tableName)}`,
		MAX_XLSX_RESPONSE_BYTES
	);
	assert(exported.status === 200 && exported.body.length > 0, `${label} metadata export failed`);
	assert(
		String(exported.headers.get('content-disposition') || '').toLowerCase().includes('attachment'),
		`${label} metadata export is missing attachment disposition`
	);
	const exportPath = resolve(artifactRoot, safeArtifactFilename(label, table.tableName, 'xlsx'));
	assert(isWithin(artifactRoot, exportPath), `${label} XLSX path escapes the artifact directory`);
	await writeFile(exportPath, exported.body, { flag: 'wx' });
	const xlsxEntryCount = await assertXlsxArchive(exportPath, exported.body, label);

	return {
		table: table.tableName,
		tableId: String(table.id),
		templateVersion,
		previewCount: previewPaths.length,
		zipEntryCount: extractedPaths.length,
		zipPath,
		extractedPath: extractRoot,
		exportPath,
		xlsxEntryCount,
		staleVersion: 'rejected',
		serverGeneration: args.skipServerGenerate ? 'skipped by option' : 'passed',
	};
}

async function assertXlsxArchive(exportPath, content, label) {
	assert(
		content.length > 4 && content.subarray(0, 4).equals(Buffer.from([0x50, 0x4b, 0x03, 0x04])),
		`${label} metadata export is not an OOXML ZIP archive`
	);
	const listed = await run('unzip', ['-Z1', exportPath], { maxBytes: 1024 * 1024 });
	const entries = listed.toString('utf8').split(/\r?\n/).filter(Boolean).map(validateArtifactPath);
	assert(entries.length > 0 && entries.length <= 256, `${label} XLSX entry count is outside the acceptance limit`, entries.length);
	for (const requiredEntry of ['[Content_Types].xml', 'xl/workbook.xml']) {
		assert(entries.includes(requiredEntry), `${label} XLSX is missing ${requiredEntry}`, entries);
		const unzipPattern = requiredEntry.replaceAll('[', '\\[').replaceAll(']', '\\]');
		const xml = await run('unzip', ['-p', exportPath, unzipPattern], { maxBytes: 4 * 1024 * 1024 });
		assert(xml.length > 0 && xml.subarray(0, 256).toString('utf8').includes('<?xml'),
			`${label} XLSX entry is not XML: ${requiredEntry}`);
	}
	return entries.length;
}

async function safeExtractZip(zipPath, outputRoot) {
	await mkdir(outputRoot, { recursive: false });
	const listed = await run('unzip', ['-Z1', zipPath], { maxBytes: 1024 * 1024 });
	const entries = listed.toString('utf8').split(/\r?\n/).filter(Boolean);
	assert(entries.length > 0 && entries.length <= 256, 'ZIP entry count is outside the acceptance limit', entries.length);
	const destinations = new Set();
	const extracted = [];
	let totalBytes = 0;
	for (const entry of entries) {
		const safePath = validateArtifactPath(entry);
		assert(!/[?*\[\]]/.test(safePath), `ZIP entry contains wildcard syntax: ${entry}`);
		const destination = resolve(outputRoot, safePath);
		assert(isWithin(outputRoot, destination), `ZIP entry escapes the output directory: ${entry}`);
		assert(!destinations.has(destination), `ZIP contains duplicate destination: ${entry}`);
		destinations.add(destination);
		if (entry.endsWith('/')) {
			await mkdir(destination, { recursive: true });
			continue;
		}
		const content = await run('unzip', ['-p', zipPath, entry], { maxBytes: 16 * 1024 * 1024 });
		totalBytes += content.length;
		assert(totalBytes <= 64 * 1024 * 1024, 'ZIP uncompressed content exceeds 64 MiB');
		await mkdir(dirname(destination), { recursive: true });
		await writeFile(destination, content, { flag: 'wx' });
		extracted.push(safePath);
	}
	return extracted.sort();
}

function validateArtifactPath(value) {
	assert(typeof value === 'string' && value.length > 0, 'artifact path is empty');
	assert(!value.includes('\\') && !value.includes('\0'), `artifact path has forbidden characters: ${value}`);
	assert(!isAbsolute(value) && !/^[A-Za-z]:/.test(value), `artifact path is absolute: ${value}`);
	const normalized = normalize(value).split(sep).join('/');
	assert(normalized !== '..' && !normalized.startsWith('../'), `artifact path traverses its root: ${value}`);
	return normalized.replace(/\/$/, '');
}

function assertSamePaths(actual, expected, message) {
	const left = [...actual].sort();
	const right = [...expected].sort();
	assert(JSON.stringify(left) === JSON.stringify(right), message, { actual: left, expected: right });
}

async function prepareArtifactRoot(configuredPath) {
	if (configuredPath) {
		const path = resolve(configuredPath);
		await mkdir(path, { recursive: false });
		return path;
	}
	const parent = resolve(root, 'target/generator-acceptance');
	await mkdir(parent, { recursive: true });
	return mkdtemp(join(parent, `${mode}-`));
}

async function waitForAuditLogs(baselineIds, generationResults) {
	const expected = [...importedTables].map((tableName) => ({
		kind: 'operation',
		title: '导入代码生成表',
		method: 'POST',
		requestUri: `${auditContextPath}/table/import/${encodeURIComponent(dsName)}/${encodeURIComponent(tableName)}`,
		marker: ownershipMarker,
	}));
	expected.push(...generationResults.map((result) => ({
		kind: 'configuration',
		title: '修改列属性',
		method: 'PUT',
		requestUri: `${auditContextPath}/table`,
		marker: result.table,
	})));
	if (!args.skipServerGenerate) {
			expected.push(...generationResults.map((result) => ({
			kind: 'generation',
			title: '生成代码到项目目录',
			tableId: result.tableId,
			templateVersion: result.templateVersion,
			requestUri: `${auditContextPath}/generator/code`,
		})));
	}
	if (!args.skipSync) {
		expected.push({
			kind: 'sync',
			title: '同步代码生成表结构',
			method: 'POST',
			requestUri: `${auditContextPath}/table/sync/${encodeURIComponent(dsName)}/${encodeURIComponent(standaloneTableName)}`,
			marker: standaloneTableName,
		});
	}
	for (let attempt = 0; attempt < 30; attempt += 1) {
		const records = (await loadAuditLogs()).filter((record) => !baselineIds.has(String(record.id)));
		const usedIds = new Set();
		const matches = expected.map((item) => {
			const match = records.find((record) => !usedIds.has(String(record.id))
				&& (item.kind === 'generation'
					? matchesGeneratorAudit(record, item)
					: matchesOperationAudit(record, item)));
			if (match) usedIds.add(String(match.id));
			return match;
		});
		if (matches.every(Boolean)) {
			return matches.map(({ id, title, method, requestUri }) => ({ id: String(id), title, method, requestUri }));
		}
		await sleep(500);
	}
	throw new Error(`Generator operation logs were not persisted: ${expected.map((item) => item.title).join(', ')}`);
}

async function loadAuditLogs() {
	const response = await authorized('/admin/log/page?current=1&size=500&descs=id');
	return assertApi(response, 'load operation logs')?.records || [];
}

async function waitForHealth(url, name) {
	for (let attempt = 0; attempt < 60; attempt += 1) {
		const response = await fetchResponse(url);
		if (response.status === 200 && response.body?.status === 'UP') return;
		await sleep(1000);
	}
	throw new Error(`${name} health check did not pass: ${url}`);
}

async function authorized(path, options = {}) {
	return request(path, { ...options, headers: { Authorization: `Bearer ${token}`, ...options.headers } });
}

async function authorizedBinary(path, maximumBytes, options = {}) {
	return requestBinary(path, maximumBytes, {
		...options,
		headers: { Authorization: `Bearer ${token}`, ...options.headers },
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

async function requestBinary(path, maximumBytes, options = {}) {
	try {
		const response = await fetch(`${apiBase}${path}`, { ...options, signal: AbortSignal.timeout(30_000) });
		return {
			status: response.status,
			headers: response.headers,
			body: await readResponseBodyLimited(response, maximumBytes),
		};
	} catch (error) {
		return { status: 0, headers: new Headers(), body: Buffer.alloc(0), error: String(error) };
	}
}

async function fetchResponse(url, options = {}) {
	try {
		const response = await fetch(url, { ...options, signal: AbortSignal.timeout(30_000) });
		const text = await response.text();
		let body = text;
		try { body = text ? JSON.parse(text) : null; } catch { /* keep text diagnostics */ }
		return { status: response.status, headers: response.headers, body };
	} catch (error) {
		return { status: 0, headers: new Headers(), body: String(error) };
	}
}

function assertApi(response, action) {
	assert(response.status === 200, `${action} returned HTTP ${response.status}`, response.body);
	assert(response.body?.code === 0, `${action} returned an API error`, response.body);
	return response.body.data;
}

function assertDenied(response, action) {
	const clientDenied = response.status >= 400 && response.status < 500;
	const apiDenied = response.status === 200 && Number.isInteger(response.body?.code) && response.body.code !== 0;
	assert(response.status !== 0 && (clientDenied || apiDenied), `${action} was not rejected`, response.body);
}

function assertStaleVersionRejected(response, label) {
	assert(
		response.status >= 400 && response.status < 600
			&& Number.isInteger(response.body?.code) && response.body.code !== 0
			&& String(response.body?.msg || '').includes('模板已变化'),
		`${label} stale templateVersion was not rejected with the expected domain error`,
		response.body
	);
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

async function run(command, commandArgs, { maxBytes }) {
	return runBoundedProcess(command, commandArgs, {
		maxStdoutBytes: maxBytes,
		maxStderrBytes: MAX_PROCESS_STDERR_BYTES,
		timeoutMillis: PROCESS_TIMEOUT_MILLIS,
	});
}

function parseArgs(values) {
	const result = {};
	const valueOptions = new Map([
		['--mode', 'mode'], ['--env-file', 'envFile'], ['--api-base', 'apiBase'], ['--output-dir', 'outputDir'],
		['--ds-name', 'dsName'], ['--standalone-table', 'standaloneTable'], ['--parent-table', 'parentTable'],
		['--child-table', 'childTable'],
	]);
	for (let index = 0; index < values.length; index += 1) {
		const option = values[index];
		if (option === '--help' || option === '-h') result.help = true;
		else if (option === '--skip-sync') result.skipSync = true;
		else if (option === '--skip-server-generate') result.skipServerGenerate = true;
		else if (valueOptions.has(option)) {
			assert(index + 1 < values.length, `${option} requires a value`);
			result[valueOptions.get(option)] = values[++index];
		} else throw new Error(`unknown option: ${option}`);
	}
	return result;
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
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) value = value.slice(1, -1);
		result[key] = value;
	}
	return result;
}

function required(name) {
	const value = env[name];
	if (!value || value === 'GENERATE_ON_FIRST_RUN') throw new Error(`${name} is required in ${envFile}`);
	return value;
}

function stripTrailingSlash(value) {
	return String(value).replace(/\/$/, '');
}

function isWithin(rootPath, candidate) {
	const path = relative(resolve(rootPath), resolve(candidate));
	return path !== '..' && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function sleep(milliseconds) {
	return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}

function assert(condition, message, details) {
	if (condition) return;
	const suffix = details === undefined ? '' : `\n${JSON.stringify(details, null, 2)}`;
	throw new Error(`${message}${suffix}`);
}
