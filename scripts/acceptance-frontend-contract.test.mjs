import { readFile } from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';

const root = new URL('../bixi-ui/', import.meta.url);

async function source(path) {
	return readFile(new URL(path, root), 'utf8');
}

test('Acceptance API clients use the shared admin HTTP prefix and preserve public parameter key', async () => {
	const dictApi = await source('src/api/acceptance/dictAggregate.ts');
	const paramApi = await source('src/api/acceptance/publicParam.ts');
	assert.match(dictApi, /const baseUrl = ['"]\/admin\/dictAggregate['"]/);
	assert.match(paramApi, /const baseUrl = ['"]\/admin\/publicParam['"]/);
	assert.match(paramApi, /key\?: string/);
	assert.match(paramApi, /responseType: ['"]blob['"]/);
});

test('Acceptance pages expose permissions and explicit loading, error, and empty states', async () => {
	const dictPage = await source('src/views/acceptance/dictAggregate/index.vue');
	const dictForm = await source('src/views/acceptance/dictAggregate/form.vue');
	const paramPage = await source('src/views/acceptance/publicParam/index.vue');
	const paramForm = await source('src/views/acceptance/publicParam/form.vue');
	for (const permission of [
		'acceptance_dict_aggregate_add',
		'acceptance_dict_aggregate_edit', 'acceptance_dict_aggregate_del',
		'acceptance_dict_aggregate_import', 'acceptance_dict_aggregate_export',
		'acceptance_sys_dict_item_add', 'acceptance_sys_dict_item_edit',
		'acceptance_sys_dict_item_del', 'acceptance_public_param_add',
		'acceptance_public_param_edit', 'acceptance_public_param_del',
		'acceptance_public_param_import', 'acceptance_public_param_export',
	]) {
		assert.match(`${dictPage}\n${dictForm}\n${paramPage}\n${paramForm}`, new RegExp(permission));
	}
	for (const page of [dictPage, paramPage]) {
		assert.match(page, /v-loading/);
		assert.match(page, /loadError/);
		assert.match(page, /empty-text=/);
		assert.match(page, /importResult/);
	}
	assert.match(dictForm, /required: true/);
	assert.match(paramForm, /required: true/);
	for (const field of ['type', 'name', 'systemFlag']) {
		assert.match(dictPage, new RegExp(`v-model=["']state\\.queryForm\\.${field}["']`), `dictionary filter ${field} is missing`);
	}
	for (const field of ['name', 'key', 'type', 'systemFlag']) {
		assert.match(paramPage, new RegExp(`v-model=["']state\\.queryForm\\.${field}["']`), `public parameter filter ${field} is missing`);
	}
});
