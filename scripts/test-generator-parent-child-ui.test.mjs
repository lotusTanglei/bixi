import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import test from 'node:test';

const generatorPath = new URL('../bixi-ui/src/views/gen/table/generator.vue', import.meta.url);
const require = createRequire(new URL('../bixi-ui/package.json', import.meta.url));
const ts = require('typescript');
const vue = require('vue');
const { parse } = require('@vue/compiler-sfc');
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};

function setup(apis = {}) {
	const { descriptor, errors } = parse(readFileSync(generatorPath, 'utf8'), { filename: fileURLToPath(generatorPath) });
	assert.deepEqual(errors, []);
	const source = ts.createSourceFile('generator.vue.ts', descriptor.scriptSetup.content,
		ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
	const names = source.statements.flatMap(statement => ts.isVariableStatement(statement)
		? statement.declarationList.declarations.flatMap(item => ts.isIdentifier(item.name) ? [item.name.text] : [])
		: ts.isFunctionDeclaration(statement) && statement.name ? [statement.name.text] : []);
	const setupSource = source.statements.filter(item => !ts.isImportDeclaration(item))
		.map(item => item.getFullText(source)).join('\n');
	const script = ts.transpileModule(setupSource, {
		compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
	}).outputText;
	let bindings;
	const scope = vue.effectScope();
	const context = vm.createContext({
		...vue,
		defineProps: () => ({ tableName: 'purchase_order', dsName: 'master' }),
		defineEmits: () => () => {},
		defineExpose() {},
		onMounted() {},
		rule: { overLength: () => true },
		useMessage: () => ({ success() {}, error() {} }),
		useMessageBox: () => ({ confirm: async () => true }),
		putObj: async () => ({}),
		useListTableApi: async () => ({ data: [] }),
		groupList: async () => ({ data: [] }),
		checkVersion: async () => ({ data: true }),
		online: async () => ({}),
		...apis,
		capture: value => { bindings = value; },
	});
	scope.run(() => vm.runInContext(`${script}\ncapture({${names.join(',')}});`, context,
		{ filename: fileURLToPath(generatorPath) }));
	return { bindings, destroy: () => scope.stop() };
}

test('generator configuration submits complete parent-child relationship metadata', async () => {
	const source = readFileSync(generatorPath, 'utf8');

	assert.match(source, /v-model="dataForm\.childTableName"/);
	assert.match(source, /v-model="dataForm\.mainField"/);
	assert.match(source, /v-model="dataForm\.childField"/);
	assert.match(source, /childTableName:\s*dataForm\.childTableName/);
	assert.match(source, /mainField:\s*dataForm\.mainField/);
	assert.match(source, /childField:\s*dataForm\.childField/);
	assert.match(source, /validateRelationship/);
});

test('generator configuration excludes child primary keys from relationship fields', async () => {
	const source = readFileSync(generatorPath, 'utf8');

	assert.match(source, /field\.primaryPk\s*!==\s*'1'/);
});

test('relationship selectors expose only supported Long id and unmanaged Long child keys', t => {
	const ui = setup();
	t.after(ui.destroy);
	const state = ui.bindings;
	state.parentFields.value = [
		{ fieldName: 'id', attrType: 'Long', primaryPk: '1' },
		{ fieldName: 'legacy_id', attrType: 'Long', primaryPk: '1' },
		{ fieldName: 'ID', attrType: 'String', primaryPk: '1' },
	];
	state.dataForm.mainField = 'id';
	state.childFields.value = [
		{ fieldName: 'order_id', attrType: 'Long', primaryPk: '0' },
		{ fieldName: 'tenant_id', attrType: 'Long', primaryPk: '0' },
		{ fieldName: 'create_by', attrType: 'Long', primaryPk: '0' },
		{ fieldName: 'quantity', attrType: 'Integer', primaryPk: '0' },
		{ fieldName: 'id', attrType: 'Long', primaryPk: '1' },
	];

	assert.deepEqual([...state.mainFieldOptions.value].map(field => field.fieldName), ['id']);
	assert.deepEqual([...state.childFieldOptions.value].map(field => field.fieldName), ['order_id']);
});

test('stale child-field requests cannot replace newer fields or clear newer loading state', async t => {
	const requests = new Map();
	const ui = setup({
		useTableApi: async (_dsName, tableName) => {
			const request = deferred();
			requests.set(tableName, request);
			return request.promise;
		},
	});
	t.after(ui.destroy);
	const state = ui.bindings;

	const first = state.handleChildTableChange('A');
	const second = state.handleChildTableChange('B');
	requests.get('B').resolve({ data: { fieldList: [{ fieldName: 'b_order_id' }] } });
	await second;
	assert.deepEqual([...state.childFields.value].map(field => field.fieldName), ['b_order_id']);
	requests.get('A').resolve({ data: { fieldList: [{ fieldName: 'a_order_id' }] } });
	await first;
	assert.deepEqual([...state.childFields.value].map(field => field.fieldName), ['b_order_id']);

	const staleFirst = state.handleChildTableChange('C');
	const currentSecond = state.handleChildTableChange('D');
	requests.get('C').resolve({ data: { fieldList: [{ fieldName: 'c_order_id' }] } });
	await staleFirst;
	assert.equal(state.childLoading.value, true, 'stale finally must not clear the current request loading state');
	requests.get('D').resolve({ data: { fieldList: [{ fieldName: 'd_order_id' }] } });
	await currentSecond;
	assert.equal(state.childLoading.value, false);
	assert.deepEqual([...state.childFields.value].map(field => field.fieldName), ['d_order_id']);
});
