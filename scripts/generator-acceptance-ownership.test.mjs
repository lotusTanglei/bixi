import assert from 'node:assert/strict';
import test from 'node:test';

import { importOwnedGeneratorTable } from './generator-acceptance-ownership.mjs';

const base = {
	prefix: '/gen',
	dsName: 'master',
	tableName: 'purchase_order',
	marker: 'generator-acceptance',
};

function ok(data) {
	return { status: 200, body: { code: 0, data } };
}

test('missing configuration is read first and then created with one atomic POST', async () => {
	const calls = [];
	const result = await importOwnedGeneratorTable({
		...base,
		request: async (path, options) => {
			calls.push({ path, options });
			if (options.method === 'GET') return ok(null);
			return ok({ created: true, table: { id: 41, dsName: 'master', tableName: 'purchase_order',
				author: 'generator-acceptance', fieldList: [{ id: 101 }] } });
		},
	});

	assert.equal(result.created, true);
	assert.equal(result.table.author, 'generator-acceptance');
	assert.deepEqual(calls, [
		{
			path: '/gen/table/config/master/purchase_order',
			options: { method: 'GET' },
		},
		{
			path: '/gen/table/import/master/purchase_order',
			options: { method: 'POST', body: { author: 'generator-acceptance' } },
		},
	]);
});

test('marker-owned rerun is accepted after the exact GET without any mutation', async () => {
	const calls = [];
	const result = await importOwnedGeneratorTable({
		...base,
		request: async (path, options) => {
			calls.push({ path, options });
			return ok({ id: 41, dsName: 'master', tableName: 'purchase_order',
				author: 'generator-acceptance', fieldList: [{ id: 101 }] });
		},
	});

	assert.equal(result.created, false);
	assert.deepEqual(calls, [{
		path: '/gen/table/config/master/purchase_order',
		options: { method: 'GET' },
	}]);
});

test('marker-owned rerun rejects a lightweight lookup without persisted fields', async () => {
	await assert.rejects(() => importOwnedGeneratorTable({
		...base,
		request: async () => ok({
			id: 41,
			dsName: 'master',
			tableName: 'purchase_order',
			author: 'generator-acceptance',
		}),
	}), /configuration has no fields/);
});

test('foreign existing configuration is refused immediately after the exact GET', async () => {
	const calls = [];
	await assert.rejects(() => importOwnedGeneratorTable({
		...base,
		request: async (path, options) => {
			calls.push({ path, options });
			return ok({ id: 41, dsName: 'master', tableName: 'purchase_order',
				author: 'foreign-owner', fieldList: [{ id: 101 }] });
		},
	}), /refusing to use foreign Generator configuration/);

	assert.deepEqual(calls, [{
		path: '/gen/table/config/master/purchase_order',
		options: { method: 'GET' },
	}]);
});

test('foreign winner between GET and POST is refused without an ownership recovery PUT', async () => {
	const calls = [];
	await assert.rejects(() => importOwnedGeneratorTable({
		...base,
		request: async (path, options) => {
			calls.push({ path, options });
			if (options.method === 'GET') return ok(null);
			return ok({ created: false, table: { id: 42, dsName: 'master', tableName: 'purchase_order',
				author: 'foreign-winner', fieldList: [{ id: 102 }] } });
		},
	}), /refusing to use foreign Generator configuration/);

	assert.deepEqual(calls, [
		{
			path: '/gen/table/config/master/purchase_order',
			options: { method: 'GET' },
		},
		{
			path: '/gen/table/import/master/purchase_order',
			options: { method: 'POST', body: { author: 'generator-acceptance' } },
		},
	]);
});
