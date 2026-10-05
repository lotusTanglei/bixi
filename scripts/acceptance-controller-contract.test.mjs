import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contracts = [
	{
		name: 'dictAggregate',
		path: '../bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/acceptance/controller/SysDictController.java',
		basePath: '/dictAggregate',
		permissions: {
			view: 'acceptance_dict_aggregate_view',
			add: 'acceptance_dict_aggregate_add',
			edit: 'acceptance_dict_aggregate_edit',
			del: 'acceptance_dict_aggregate_del',
			import: 'acceptance_dict_aggregate_import',
			export: 'acceptance_dict_aggregate_export',
		},
	},
	{
		name: 'publicParam',
		path: '../bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/acceptance/controller/SysPublicParamController.java',
		basePath: '/publicParam',
		permissions: {
			view: 'acceptance_public_param_view',
			add: 'acceptance_public_param_add',
			edit: 'acceptance_public_param_edit',
			del: 'acceptance_public_param_del',
			import: 'acceptance_public_param_import',
			export: 'acceptance_public_param_export',
		},
	},
];

const sources = await Promise.all(contracts.map(async (contract) => ({
	...contract,
	source: await readFile(new URL(contract.path, import.meta.url), 'utf8'),
})));

const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function methodAnnotations(source, methodName) {
	const lines = source.split(/\r?\n/);
	const signature = new RegExp(`\\b(?:public|protected|private)\\b[^\\n]*\\b${escapeRegExp(methodName)}\\s*\\(`);
	const signatureIndex = lines.findIndex((line) => signature.test(line));
	assert.notEqual(signatureIndex, -1, `missing method ${methodName}`);
	return lines.slice(Math.max(0, signatureIndex - 8), signatureIndex + 1).join('\n');
}

function assertEndpoint(source, { method, mapping, permission, audited = false }) {
	const annotations = methodAnnotations(source, method);
	assert.match(annotations, mapping, `${method} is missing its HTTP mapping`);
	assert.match(annotations, new RegExp(`@HasPermission\\("${escapeRegExp(permission)}"\\)`),
		`${method} is missing permission ${permission}`);
	if (audited) {
		assert.match(annotations, /@SysLog\("[^"]+"\)/, `${method} is missing @SysLog`);
	}
}

for (const contract of sources) {
	test(`${contract.name} controller exposes the frozen HTTP contract`, () => {
		const { source, basePath, permissions } = contract;
		assert.match(source, new RegExp(`@RequestMapping\\("${escapeRegExp(basePath)}"\\)`),
			`controller path must be ${basePath}`);

		assertEndpoint(source, {
			method: 'page',
			mapping: /@GetMapping\("\/page"\)/,
			permission: permissions.view,
		});
		assertEndpoint(source, {
			method: 'details',
			mapping: /@GetMapping\("\/details\/\{id\}"\)/,
			permission: permissions.view,
		});
		assertEndpoint(source, {
			method: 'create',
			mapping: /@PostMapping\s*$/m,
			permission: permissions.add,
			audited: true,
		});
		assertEndpoint(source, {
			method: 'update',
			mapping: /@PutMapping\s*$/m,
			permission: permissions.edit,
			audited: true,
		});
		assertEndpoint(source, {
			method: 'delete',
			mapping: /@DeleteMapping\s*$/m,
			permission: permissions.del,
			audited: true,
		});
		assertEndpoint(source, {
			method: 'importData',
			mapping: /@PostMapping\(value = "\/import", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
			permission: permissions.import,
			audited: true,
		});
		assertEndpoint(source, {
			method: 'export',
			mapping: /@GetMapping\("\/export"\)/,
			permission: permissions.export,
			audited: true,
		});
	});
}
