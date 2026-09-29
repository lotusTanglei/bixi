import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import test from 'node:test';

import * as generatorSupport from './generator-acceptance-support.mjs';

import {
	functionNameForTable,
	matchesGeneratorAudit,
	readResponseBodyLimited,
	runBoundedProcess,
	safeArtifactFilename,
} from './generator-acceptance-support.mjs';

const successfulAudit = {
	type: '0',
	title: '生成代码到项目目录',
	method: 'POST',
	requestUri: '/generator/code',
	params: '[{"tableIds":[1872559165395542017],"templateVersion":"bixi-default-v1:abc","overwrite":true}]',
};

const acceptanceSource = readFileSync(new URL('./generator-acceptance.mjs', import.meta.url), 'utf8');

test('standalone function names preserve the default compatibility name', () => {
	assert.equal(functionNameForTable('sys_public_param'), 'publicParam');
	assert.equal(functionNameForTable('sys_public_param'), functionNameForTable('sys_public_param'));
});

test('standalone function names are stable legal identifiers for custom tables', () => {
	const postName = functionNameForTable('sys_post');
	const roleName = functionNameForTable('sys_role');
	assert.equal(postName, 'sysPost');
	assert.equal(postName, functionNameForTable('sys_post'));
	assert.notEqual(postName, roleName);
	assert.match(postName, /^[A-Za-z_$][A-Za-z0-9_$]*$/);
	assert.equal(functionNameForTable('123_custom-table'), 'table123CustomTable');
});

test('standalone function names avoid the fixed parent-child acceptance name', () => {
	assert.equal(functionNameForTable('dict_aggregate', { reservedNames: ['dictAggregate'] }), 'dictAggregateTable');
});

test('generator acceptance derives standalone function names from the selected table', () => {
	assert.match(acceptanceSource, /functionName:\s*functionNameForTable\(standaloneTableName\)/);
});

test('generation audit matching requires success, exact URI, table ID, and template version', () => {
	const expected = {
		tableId: '1872559165395542017',
		templateVersion: 'bixi-default-v1:abc',
		requestUri: '/generator/code',
	};
	assert.equal(matchesGeneratorAudit(successfulAudit, expected), true);
	for (const changed of [
		{ type: '9' },
		{ requestUri: '/admin/generator/code' },
		{ params: successfulAudit.params.replace('1872559165395542017', '1872559165395542018') },
		{ params: successfulAudit.params.replace('bixi-default-v1:abc', 'bixi-default-v1:abc-stale') },
	]) {
		assert.equal(matchesGeneratorAudit({ ...successfulAudit, ...changed }, expected), false);
	}
});

test('generation audit matching accepts the exact single context-path URI', () => {
	assert.equal(matchesGeneratorAudit({
		...successfulAudit,
		requestUri: '/admin/generator/code',
	}, {
		tableId: '1872559165395542017',
		templateVersion: 'bixi-default-v1:abc',
		requestUri: '/admin/generator/code',
	}), true);
});

test('operation audit matching requires success and the exact single import URI', () => {
	assert.equal(typeof generatorSupport.matchesOperationAudit, 'function');
	const expected = {
		title: '导入代码生成表',
		method: 'POST',
		requestUri: '/admin/table/import/master/purchase_order',
		marker: 'generator-acceptance',
	};
	const record = {
		type: '0',
		...expected,
		params: '[{"author":"generator-acceptance"}]',
	};
	assert.equal(generatorSupport.matchesOperationAudit(record, expected), true);
	assert.equal(generatorSupport.matchesOperationAudit({ ...record, type: '9' }, expected), false);
	assert.equal(generatorSupport.matchesOperationAudit({
		...record,
		requestUri: '/table/import/master/purchase_order',
	}, expected), false);
});

test('single health checks use only the composed application actuator', () => {
	assert.equal(typeof generatorSupport.healthChecksForMode, 'function');
	assert.deepEqual(generatorSupport.healthChecksForMode('single'), [
		{ name: 'single', path: '/admin/actuator/health' },
	]);
});

test('single direct-backend authentication uses the application context path', () => {
	assert.equal(typeof generatorSupport.authTokenPathForMode, 'function');
	assert.equal(generatorSupport.authTokenPathForMode('single'), '/admin/oauth2/token');
});

test('configuration reload uses the exact read-only endpoint instead of the mutation endpoint', () => {
	const start = acceptanceSource.indexOf('async function updateTableConfiguration');
	const end = acceptanceSource.indexOf('\nasync function ', start + 1);
	const implementation = acceptanceSource.slice(start, end);
	assert.match(implementation, /\/table\/config\/\$\{encodeURIComponent\(dsName\)\}\/\$\{encodeURIComponent\(tableName\)\}/);
});

test('generation audit matching accepts string IDs without losing Snowflake precision', () => {
	const record = {
		...successfulAudit,
		params: '[{"tableIds":["1872559165395542017"],"templateVersion":"bixi-default-v1:abc","overwrite":true}]',
	};
	assert.equal(matchesGeneratorAudit(record, {
		tableId: '1872559165395542017',
		templateVersion: 'bixi-default-v1:abc',
		requestUri: '/generator/code',
	}), true);
});

test('declared oversized binary response is rejected before its body is read', async () => {
	let readerRequested = false;
	const response = {
		headers: new Headers({ 'content-length': '16' }),
		body: {
			getReader() {
				readerRequested = true;
				throw new Error('body must not be read');
			},
		},
	};

	await assert.rejects(readResponseBodyLimited(response, 8), error => error.code === 'BINARY_BODY_LIMIT');
	assert.equal(readerRequested, false);
});

test('chunked binary response is cancelled as soon as it exceeds the limit', async () => {
	let cancelled = false;
	const chunks = [new Uint8Array(4), new Uint8Array(5)];
	const response = new Response(new ReadableStream({
		pull(controller) {
			const chunk = chunks.shift();
			if (chunk) controller.enqueue(chunk);
			else controller.close();
		},
		cancel() {
			cancelled = true;
		},
	}));

	await assert.rejects(readResponseBodyLimited(response, 8), error => error.code === 'BINARY_BODY_LIMIT');
	assert.equal(cancelled, true);
});

test('binary response at the exact limit is returned intact', async () => {
	const body = await readResponseBodyLimited(new Response(new Uint8Array([1, 2, 3, 4])), 4);
	assert.deepEqual(body, Buffer.from([1, 2, 3, 4]));
});

test('bounded process runner caps stderr and terminates the process', async () => {
	await assert.rejects(
		runBoundedProcess(process.execPath, ['-e', "process.stderr.write('x'.repeat(1024)); setInterval(() => {}, 1000)"], {
			maxStdoutBytes: 64,
			maxStderrBytes: 64,
			timeoutMillis: 2_000,
			killGraceMillis: 50,
		}),
		error => error.code === 'PROCESS_STDERR_LIMIT'
			&& Buffer.byteLength(error.result?.stderr || '') <= 64
	);
});

test('bounded process runner times out and reaps a hanging process', async () => {
	const startedAt = Date.now();
	await assert.rejects(
		runBoundedProcess(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], {
			maxStdoutBytes: 64,
			maxStderrBytes: 64,
			timeoutMillis: 100,
			killGraceMillis: 50,
		}),
		error => error.code === 'PROCESS_TIMEOUT'
	);
	assert.ok(Date.now() - startedAt < 2_000);
});

test('bounded process runner returns normal stdout', async () => {
	const output = await runBoundedProcess(process.execPath, ['-e', "process.stdout.write('ok')"], {
		maxStdoutBytes: 64,
		maxStderrBytes: 64,
		timeoutMillis: 1_000,
	});
	assert.equal(output.toString('utf8'), 'ok');
});

test('artifact filename encodes untrusted table names into one safe component', () => {
	const root = resolve('/tmp/generator-acceptance-artifacts');
	const filename = safeArtifactFilename('standalone', '../../../../outside/name', 'zip');
	const target = resolve(root, filename);
	const path = relative(root, target);
	assert.equal(dirname(filename), '.');
	assert.equal(isAbsolute(filename), false);
	assert.equal(path === '..' || path.startsWith(`..${sep}`), false);
	assert.match(filename, /^standalone-[A-Za-z0-9_-]+\.zip$/);
});
