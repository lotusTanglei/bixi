import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

const require = createRequire(import.meta.url);
const ts = require('typescript');
const source = readFileSync(resolve(import.meta.dirname, '../src/hooks/table.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;

function tableWith(pageList) {
	const exports = {};
	runInNewContext(compiled, {
		exports,
		require: (name) => name === 'element-plus'
			? { ElMessage: { error() {} } }
			: { default: { toUnderline: (value) => value } },
		onMounted() {},
	});
	const state = { createdIsNeed: false, pageList };
	return { state, ...exports.useTable(state) };
}

test('an older page response cannot replace the latest page', async () => {
	const requests = [];
	const table = tableWith(() => new Promise((resolve) => requests.push(resolve)));
	const first = table.getDataList();
	const second = table.getDataList();
	assert.equal(requests.length, 2);
	requests[1]({ data: { records: [{ id: 'new' }], total: 1 } });
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal(table.state.dataList[0].id, 'new');
	requests[0]({ data: { records: [{ id: 'old' }], total: 1 } });
	await Promise.all([first, second]);
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal(table.state.dataList[0].id, 'new');
});

test('a failed page request leaves a persistent error state', async () => {
	const table = tableWith(() => Promise.reject(new Error('network offline')));
	table.getDataList();
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal(table.state.loadError, 'network offline');
	assert.equal(table.state.loading, false);
});
