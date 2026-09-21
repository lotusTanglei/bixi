#!/usr/bin/env node
// Execute the real Vue setup scripts with Vue reactivity and controlled API responses.
// Run after installing bixi-ui dependencies: node scripts/test-workflow-ui.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import test from 'node:test';
import { webcrypto } from 'node:crypto';

const require = createRequire(new URL('../bixi-ui/package.json', import.meta.url));
const ts = require('typescript');
const vue = require('vue');
const { parse } = require('@vue/compiler-sfc');
const root = new URL('../bixi-ui/src/views/', import.meta.url);
const settle = () => new Promise(setImmediate);
const deferred = () => {
	let resolve, reject;
	const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
	return { promise, resolve, reject };
};
const memoryStorage = () => {
	const values = new Map();
	return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key), clear: () => values.clear(), key: index => [...values.keys()][index] ?? null, get length() { return values.size; } };
};

function setup(path, apis = {}) {
	const { descriptor } = parse(readFileSync(new URL(path, root), 'utf8'));
	const source = ts.createSourceFile(path, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
	const names = source.statements.flatMap(statement => ts.isVariableStatement(statement)
		? statement.declarationList.declarations.flatMap(item => ts.isIdentifier(item.name) ? [item.name.text] : ts.isObjectBindingPattern(item.name) ? item.name.elements.map(binding => binding.name.text) : [])
		: ts.isFunctionDeclaration(statement) ? [statement.name.text] : []);
	const setupSource = source.statements.filter(item => !ts.isImportDeclaration(item)).map(item => item.getFullText(source)).join('\n');
	const script = ts.transpileModule(setupSource, {
		compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
	}).outputText;
	const events = [], messages = [], unmount = [];
	let bindings;
	const scope = vue.effectScope();
	const context = vm.createContext({
		...vue, defineExpose() {}, defineProps: () => ({}), defineEmits: () => (...event) => events.push(event),
		onBeforeUnmount: callback => unmount.push(callback),
		useMessage: () => ({ success: message => messages.push(message), error: message => messages.push(message) }),
		auth: () => true, approvers: async () => ({ data: { records: [] } }),
		crypto: webcrypto, sessionStorage: memoryStorage(),
		useUserInfo: () => ({ userInfos: { user: { id: '11' } } }),
		useMessageBox: () => ({ confirm: async () => true }),
		...apis, capture: value => { bindings = value; },
	});
	const moduleCache = new Map();
	const loadModule = modulePath => {
		if (moduleCache.has(modulePath)) return moduleCache.get(modulePath);
		const exports = {};
		moduleCache.set(modulePath, exports);
		const content = ts.transpileModule(readFileSync(new URL(`../bixi-ui/src/${modulePath}.ts`, import.meta.url), 'utf8'), {
			compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
		}).outputText;
		const localRequire = name => {
			if (name === 'js-cookie') {
				const module = { exports: {} };
				vm.runInContext(`(function(module, exports) {${readFileSync(require.resolve('js-cookie'), 'utf8')}\n})`, context)(module, module.exports);
				return { default: module.exports };
			}
			if (name === 'vue') return { ...vue, onBeforeUnmount: context.onBeforeUnmount };
			if (name === '/@/stores/userInfo') return { useUserInfo: context.useUserInfo };
			if (name === '/@/hooks/message') return { useMessage: context.useMessage, useMessageBox: context.useMessageBox };
			if (name === './workflow-intent') return loadModule('utils/workflow-intent');
			if (name === '/@/utils/authFunction') return { auth: context.auth };
			if (name.startsWith('/@/api/')) return new Proxy({}, { get: (_, key) => context[key] });
			if (name.startsWith('/@/utils/workflow-')) return loadModule(name.slice(3));
			throw new Error(`Unexpected real helper import: ${name}`);
		};
		vm.runInContext(`(function(require, exports) {${content}\n})`, context)(localRequire, exports);
		return exports;
	};
	Object.assign(context, loadModule('utils/workflow-intent'), loadModule('utils/workflow-intent-dialog'));
	scope.run(() => vm.runInContext(`${script}\ncapture({${names.join(',')}});`, context, { filename: fileURLToPath(new URL(path, root)) }));
	return {
		bindings, events, messages, context, loadModule, template: descriptor.template.content,
		close() {
			const beforeClose = descriptor.template.content.match(/:before-close="([^"]+)"/)?.[1];
			const done = () => { bindings.visible.value = false; };
			if (beforeClose) vm.runInContext(beforeClose, context)(done);
			else done();
		},
		destroy() { unmount.forEach(callback => callback()); scope.stop(); },
	};
}

const startPath = 'workflow/process/start-dialog.vue';
const startDefinition = { id: 'approval:1', key: 'approval', name: '审批' };
const validStartForm = state => { state.dataFormRef.value = { validate: async () => true, resetFields() {}, clearValidate() {} }; };

test('start preserves the original request and payload across a lost response and page reload', async t => {
	const storage = memoryStorage(), submitted = [];
	const first = setup(startPath, {
		sessionStorage: storage,
		start: async data => { submitted.push(JSON.parse(JSON.stringify(data))); throw { msg: 'network timeout' }; },
	});
	t.after(first.destroy);
	await first.bindings.openDialog(startDefinition);
	validStartForm(first.bindings);
	first.bindings.dataForm.title = 'original title';
	first.bindings.dataForm.variables = { nested: { value: 'original' } };
	await first.bindings.onSubmit();
	assert.match(submitted[0].requestId ?? '', /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
	assert.equal(submitted[0].processKey, startDefinition.key, 'ProcessStartDTO requires processKey');
	assert.equal(Object.hasOwn(submitted[0], 'processDefinitionId'), false, 'ProcessStartDTO does not accept a version ID');
	assert.equal(storage.length, 1);
	first.close();
	const restored = setup(startPath, {
		sessionStorage: storage,
		start: async data => { submitted.push(JSON.parse(JSON.stringify(data))); return { code: 0 }; },
	});
	t.after(restored.destroy);
	await restored.bindings.openDialog({ ...startDefinition, id: 'approval:2' });
	validStartForm(restored.bindings);
	assert.equal(restored.bindings.dataForm.title, 'original title');
	// Retrying always uses the persisted payload, even if mutable form state changes.
	restored.bindings.dataForm.title = 'accidentally changed';
	restored.bindings.dataForm.variables.nested.value = 'accidentally changed';
	await restored.bindings.onSubmit();
	assert.deepEqual(submitted[1], submitted[0]);
	assert.equal(storage.length, 0);
	assert.equal(restored.events.filter(([event]) => event === 'refresh').length, 1);
});

test('start result lookup preserves a missing command and clears only a completed success', async t => {
	const storage = memoryStorage(), submitted = [], queried = [];
	let found = false;
	const ui = setup(startPath, {
		sessionStorage: storage,
		start: async data => { submitted.push(data.requestId); throw { msg: 'network timeout' }; },
		getCommand: async requestId => { queried.push(requestId); if (!found) throw { code: 1, data: { errorCode: 'WORKFLOW_COMMAND_NOT_FOUND', requestId } }; return { code: 0, data: { requestId, operation: 'START', resultCode: 'SUCCESS', completedAt: '2026-09-21T12:00:00', response: { processInstanceId: 'p1' } } }; },
	});
	t.after(ui.destroy);
	await ui.bindings.openDialog(startDefinition);
	validStartForm(ui.bindings);
	ui.bindings.dataForm.title = 'original';
	await ui.bindings.onSubmit();
	assert.equal(typeof ui.bindings.queryResult, 'function');
	await ui.bindings.queryResult();
	assert.equal(storage.length, 1);
	assert.equal(ui.bindings.error.value, '尚未查询到本次结果，可重试本次操作');
	assert.equal(ui.bindings.visible.value, true);
	found = true;
	await ui.bindings.queryResult();
	assert.deepEqual(queried, [submitted[0], submitted[0]]);
	assert.equal(submitted.length, 1);
	assert.equal(storage.length, 0);
	assert.equal(ui.events.filter(([event]) => event === 'refresh').length, 1);
});

test('start pending requests are isolated by user and process, and a new intent needs an explicit reset', async t => {
	const storage = memoryStorage(), submitted = [];
	let userId = '11', confirmations = 0;
	const ui = setup(startPath, {
		sessionStorage: storage,
		useUserInfo: () => ({ userInfos: { user: { id: userId } } }),
		useMessageBox: () => ({ confirm: async () => { confirmations++; } }),
		start: async data => { submitted.push(JSON.parse(JSON.stringify(data))); throw { msg: 'network timeout' }; },
	});
	t.after(ui.destroy);
	const state = ui.bindings;
	await state.openDialog(startDefinition);
	validStartForm(state);
	state.dataForm.title = 'user 11';
	await state.onSubmit();
	assert.equal(typeof state.newIntent, 'function');
	ui.close();
	userId = '22';
	await state.openDialog(startDefinition);
	assert.equal(state.dataForm.title, '');
	state.dataForm.title = 'user 22';
	await state.onSubmit();
	assert.notEqual(submitted[0].requestId, submitted[1].requestId);
	ui.close();
	userId = '11';
	await state.openDialog({ id: 'other:1', key: 'other', name: 'other' });
	assert.equal(state.dataForm.title, '');
	ui.close();
	await state.openDialog(startDefinition);
	assert.equal(state.dataForm.title, 'user 11');
	await state.newIntent();
	state.dataForm.title = 'new intent';
	await state.onSubmit();
	assert.equal(confirmations, 1);
	assert.notEqual(submitted[0].requestId, submitted[2].requestId);
});

test('start validation and submission lock close, reopen and duplicate requests', async t => {
	const validation = deferred(), saving = deferred(), submitted = [];
	const ui = setup(startPath, { start: async data => { submitted.push(data); await saving.promise; return { code: 0 }; } });
	t.after(ui.destroy);
	const state = ui.bindings;
	await state.openDialog(startDefinition);
	state.dataForm.title = 'original';
	state.dataFormRef.value = { validate: () => validation.promise, resetFields() {}, clearValidate() {} };
	const first = state.onSubmit();
	ui.close();
	await state.openDialog({ id: 'other:1', key: 'other' });
	assert.equal(state.visible.value, true);
	assert.equal(state.dataForm.processKey, startDefinition.key);
	const duplicate = state.onSubmit();
	validation.resolve(true);
	await settle();
	ui.close();
	assert.equal(state.visible.value, true);
	saving.resolve();
	await Promise.all([first, duplicate]);
	assert.equal(submitted.length, 1);
});

test('start does not send without durable session storage or after the current user changes', async t => {
	let sent = 0, actorId = '11';
	const ui = setup(startPath, {
		sessionStorage: { getItem: () => null, setItem() { throw new Error('storage unavailable'); } },
		useUserInfo: () => ({ userInfos: { user: { id: actorId } } }),
		start: async () => { sent++; return { code: 0 }; },
	});
	t.after(ui.destroy);
	await ui.bindings.openDialog(startDefinition);
	validStartForm(ui.bindings);
	ui.bindings.dataForm.title = 'original';
	await ui.bindings.onSubmit();
	assert.equal(sent, 0);
	assert.ok(ui.bindings.error.value);
	actorId = '22';
	await ui.bindings.onSubmit();
	assert.equal(sent, 0);
	assert.equal(ui.bindings.error.value, '当前用户已变更，请关闭并重新打开窗口');
});

test('start rejects definitions and saved requests that lack the required process key', async t => {
	let sent = 0;
	const ui = setup(startPath, { start: async () => { sent++; return { code: 0 }; } });
	t.after(ui.destroy);
	await ui.bindings.openDialog({ id: 'approval:1', name: '审批' });
	validStartForm(ui.bindings);
	ui.bindings.dataForm.title = 'original';
	await ui.bindings.onSubmit();
	assert.equal(sent, 0);
	assert.equal(ui.bindings.storageReady.value, false);
	const storage = memoryStorage();
	storage.setItem('workflow:START:11:approval', JSON.stringify({ requestId: webcrypto.randomUUID(), processDefinitionId: 'approval:1', title: 'old payload', variables: {} }));
	const restored = setup(startPath, { sessionStorage: storage, start: async () => { sent++; return { code: 0 }; } });
	t.after(restored.destroy);
	await restored.bindings.openDialog(startDefinition);
	await restored.bindings.onSubmit();
	assert.equal(sent, 0);
	assert.equal(restored.bindings.storageReady.value, false);
	assert.equal(storage.length, 1);
});

const leave = id => ({ id, approverId: `reviewer-${id}`, startDate: '2026-10-01', endDate: '2026-10-02', reason: `reason-${id}`, processInstanceId: id });
const formPath = 'demo/leave/form.vue';

test('a late draft response cannot overwrite the next draft being saved', async t => {
	const a = deferred(), saved = [];
	const ui = setup(formPath, {
		getObj: id => id === 'A' ? a.promise : Promise.resolve({ data: leave(id) }),
		save: async (data, id) => saved.push({ id, reason: data.reason }),
	});
	t.after(ui.destroy);
	const state = ui.bindings;
	const first = state.openDialog('A');
	await settle();
	ui.close();
	await state.openDialog('B');
	a.resolve({ data: leave('A') });
	await first;
	state.formRef.value = { validate: async () => true };
	await state.saveDraft();
	assert.deepEqual(saved, [{ id: 'B', reason: 'reason-B' }]);
});

test('draft validation and saving lock close, reopen and duplicate submissions', async t => {
	const validation = deferred(), saving = deferred(), saved = [];
	const ui = setup(formPath, {
		getObj: async id => ({ data: leave(id) }),
		save: async (data, id) => { saved.push({ id, reason: data.reason }); await saving.promise; },
	});
	t.after(ui.destroy);
	const state = ui.bindings;
	await state.openDialog('A');
	state.formRef.value = { validate: () => validation.promise, clearValidate() {} };
	const submission = state.saveDraft();
	ui.close();
	await state.openDialog('B');
	assert.equal(state.visible.value, true);
	assert.equal(state.id.value, 'A');
	const duplicate = state.saveDraft();
	validation.resolve(true);
	await settle();
	ui.close();
	await state.openDialog('C');
	assert.equal(state.visible.value, true);
	assert.equal(state.id.value, 'A');
	saving.resolve();
	await Promise.all([submission, duplicate]);
	assert.deepEqual(saved, [{ id: 'A', reason: 'reason-A' }]);
	assert.equal(ui.events.filter(([event]) => event === 'refresh').length, 1);
});

const readers = [
	{ path: formPath, api: 'getObj', argument: id => id, result: leave, value: state => state.form.reason, expected: 'reason-B' },
	{ path: 'demo/leave/detail.vue', api: 'getObj', argument: id => id, result: leave, value: state => state.request.value?.id, expected: 'B' },
	{ path: 'workflow/process/detail-dialog.vue', api: 'getObj', argument: id => ({ processInstanceId: id }), result: id => ({ processInstanceId: id }), value: state => state.processData.value.processInstanceId, expected: 'B' },
	{ path: 'workflow/process/process-dialog.vue', api: 'getDiagram', argument: id => id, result: id => id, value: state => state.diagramUrl.value, expected: 'data:image/png;base64,B' },
];
for (const reader of readers) {
	test(`${reader.path}: latest dialog keeps its response when an old request finishes last`, async t => {
		const a = deferred();
		const ui = setup(reader.path, {
			[reader.api]: id => id === 'A' ? a.promise : Promise.resolve({ data: reader.result(id) }),
			history: async id => ({ data: [{ id }] }), getHistory: async id => ({ data: [{ id }] }),
		});
		t.after(ui.destroy);
		const state = ui.bindings;
		const first = state.openDialog(reader.argument('A'));
		await settle();
		ui.close();
		await state.openDialog(reader.argument('B'));
		a.resolve({ data: reader.result('A') });
		await first;
		assert.equal(reader.value(state), reader.expected);
		if (state.records) assert.equal(state.records.value[0].id, 'B');
	});

	test(`${reader.path}: an old error/finalizer cannot change a new loading dialog`, async t => {
		const a = deferred(), b = deferred();
		const ui = setup(reader.path, {
			[reader.api]: id => id === 'A' ? a.promise : b.promise,
			history: async () => ({ data: [] }), getHistory: async () => ({ data: [] }),
		});
		t.after(ui.destroy);
		const state = ui.bindings;
		const first = state.openDialog(reader.argument('A'));
		await settle();
		ui.close();
		const second = state.openDialog(reader.argument('B'));
		await settle();
		a.reject({ msg: 'old request failed' });
		await first;
		assert.equal(state.loading.value, true);
		assert.equal(state.error.value, '');
		b.resolve({ data: reader.result('B') });
		await second;
		assert.equal(state.loading.value, false);
	});
}

test('a stale task lookup cannot reopen an earlier process dialog', async t => {
	const a = deferred(), opened = [];
	const ui = setup('workflow/task/detail-dialog.vue', {
		getObj: id => id === 'A' ? a.promise : Promise.resolve({ data: { processInstanceId: id } }),
	});
	t.after(ui.destroy);
	ui.bindings.detailRef.value = { openDialog: async row => opened.push(row.processInstanceId) };
	const first = ui.bindings.openDialog({ taskId: 'A' });
	await ui.bindings.openDialog({ taskId: 'B' });
	a.resolve({ data: { processInstanceId: 'A' } });
	await first;
	assert.deepEqual(opened, ['B']);
});

test('process details remain usable without permission to read leave data', async t => {
	let leaveReads = 0;
	const ui = setup('workflow/process/detail-dialog.vue', {
		auth: () => false,
		getObj: async () => ({ data: { processInstanceId: 'B', businessTable: 'demo_leave_request', businessId: 'B' } }),
		getHistory: async () => ({ data: [] }),
		getLeave: async () => { leaveReads++; throw { msg: 'permission denied' }; },
	});
	t.after(ui.destroy);
	await ui.bindings.openDialog({ processInstanceId: 'B' });
	assert.equal(leaveReads, 0);
	assert.equal(ui.bindings.processData.value.processInstanceId, 'B');
	assert.equal(ui.bindings.error.value, '');
});

for (const dialog of ['approve', 'transfer']) {
	test(`${dialog}: validation and submission lock close, reopen and duplicate requests`, async t => {
		const validation = deferred(), saving = deferred(), submitted = [];
		const write = async data => { submitted.push(data.taskId); await saving.promise; return { code: 0 }; };
		const ui = setup(`workflow/task/${dialog}-dialog.vue`, {
			complete: write, reject: write, transfer: write, delegate: write,
			userListApi: async () => ({ code: 0, data: { records: [] } }),
		});
		t.after(ui.destroy);
		const state = ui.bindings;
		await state.openDialog({ taskId: 'A' });
		state.dataFormRef.value = { validate: () => validation.promise, clearValidate() {} };
		const first = state.onSubmit();
		ui.close();
		await state.openDialog({ taskId: 'B' });
		assert.equal(state.visible.value, true);
		assert.equal(state.dataForm.taskId, 'A');
		const duplicate = state.onSubmit();
		validation.resolve(true);
		await settle();
		ui.close();
		assert.equal(state.visible.value, true);
		saving.resolve();
		await Promise.all([first, duplicate]);
		assert.deepEqual(submitted, ['A']);
		assert.equal(ui.events.filter(([event]) => event === 'refresh').length, 1);
	});
}

for (const lookup of [
	{ path: formPath, api: 'approvers', search: 'searchUsers', records: 'users' },
	{ path: 'workflow/task/transfer-dialog.vue', api: 'userListApi', search: 'getUserList', records: 'userList' },
]) {
	test(`${lookup.path}: slow user search cannot replace newer search results`, async t => {
		const a = deferred();
		const ui = setup(lookup.path, {
			[lookup.api]: name => name === 'A' ? a.promise : Promise.resolve({ code: 0, data: { records: [{ id: name }] } }),
		});
		t.after(ui.destroy);
		const state = ui.bindings;
		const first = state[lookup.search]('A');
		await state[lookup.search]('B');
		a.resolve({ code: 0, data: { records: [{ id: 'A' }] } });
		await first;
		assert.equal(state[lookup.records].value[0].id, 'B');
	});
}


const validTaskForm = state => { state.dataFormRef.value = { validate: async () => true, clearValidate() {} }; };
for (const dialog of ['approve', 'transfer']) {
	test(`${dialog}: an ambiguous command restores its original content and request after reopen and reload`, async t => {
		const storage = memoryStorage(), sent = [];
		const write = async data => { sent.push(JSON.parse(JSON.stringify(data))); throw { msg: 'timeout' }; };
		const apis = { sessionStorage: storage, complete: write, reject: write, transfer: write, delegate: write };
		const ui = setup(`workflow/task/${dialog}-dialog.vue`, apis); t.after(ui.destroy);
		const state = ui.bindings;
		await state.openDialog({ taskId: 'A', taskName: '审批任务' }); validTaskForm(state);
		state.dataForm.comment = 'original'; state.dataForm.userId = '22';
		await state.onSubmit();
		assert.equal(sent.length, 1);
		assert.match(sent[0].requestId, /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/);
		state.dataForm.comment = 'changed'; state.dataForm.userId = '33'; state.dataForm.result = '拒绝';
		ui.close(); await state.openDialog({ taskId: 'A' }, true);
		assert.equal(state.dataForm.comment, 'original');
		if (dialog === 'transfer') { assert.equal(state.dataForm.userId, '22'); assert.equal(state.delegating.value, false); }
		else assert.equal(state.dataForm.result, '通过');
		assert.ok(state.pendingIntent.value);
		assert.match(ui.template, /:disabled="loading \|\| !!pendingIntent/);
		const restored = setup(`workflow/task/${dialog}-dialog.vue`, apis); t.after(restored.destroy);
		await restored.bindings.openDialog({ taskId: 'A' }); validTaskForm(restored.bindings);
		assert.equal(restored.bindings.dataForm.comment, 'original');
		await restored.bindings.onSubmit();
		assert.deepEqual(sent[1], sent[0]);
	});
}

const taskApis = overrides => ({ complete: async () => { throw { msg: 'timeout' }; }, userListApi: async () => ({ code: 0, data: { records: [] } }), ...overrides });
test('approval requires an acknowledged new intent, preserving the earlier command for query', async t => {
	const sent = [], storage = memoryStorage(); let confirmed = false;
	const ui = setup('workflow/task/approve-dialog.vue', taskApis({ sessionStorage: storage,
		complete: async data => { sent.push(JSON.parse(JSON.stringify(data))); throw { msg: 'timeout' }; },
		reject: async data => { sent.push(JSON.parse(JSON.stringify(data))); throw { msg: 'timeout' }; },
		useMessageBox: () => ({ confirm: async () => { if (!confirmed) throw 'cancel'; } }),
	})); t.after(ui.destroy);
	await ui.bindings.openDialog({ taskId: 'A' }); validTaskForm(ui.bindings);
	ui.bindings.dataForm.comment = 'original'; await ui.bindings.onSubmit();
	await ui.bindings.newIntent(); assert.ok(ui.bindings.pendingIntent.value);
	confirmed = true; await ui.bindings.newIntent(); assert.equal(ui.bindings.pendingIntent.value, null);
	ui.bindings.dataForm.result = '拒绝'; ui.bindings.dataForm.comment = 'new rejection'; await ui.bindings.onSubmit();
	assert.notEqual(sent[0].requestId, sent[1].requestId);
	assert.equal(sent[1].rejectReason, 'new rejection');
	assert.equal(storage.length, 2);
	const entries = ui.context.listWorkflowIntents('task');
	assert.equal(entries.filter(entry => entry.acknowledged).length, 1);
});

test('approval snapshots the payload before async validation and ignores actor changes and late results', async t => {
	const validation = deferred(), saving = deferred(), sent = [], user = vue.reactive({ id: '11' });
	const ui = setup('workflow/task/approve-dialog.vue', taskApis({
		useUserInfo: () => ({ userInfos: { user } }),
		complete: async data => { sent.push(JSON.parse(JSON.stringify(data))); await saving.promise; return { code: 0 }; },
	})); t.after(ui.destroy);
	await ui.bindings.openDialog({ taskId: 'A' });
	ui.bindings.dataForm.comment = 'original'; ui.bindings.dataFormRef.value = { validate: () => validation.promise, clearValidate() {} };
	const action = ui.bindings.onSubmit();
	ui.bindings.dataForm.comment = 'changed'; ui.bindings.dataForm.taskId = 'B'; ui.bindings.dataForm.result = '拒绝';
	validation.resolve(true); await settle();
	assert.equal(sent[0].taskId, 'A'); assert.equal(sent[0].approvalComment, 'original');
	user.id = '22'; saving.resolve(); await action;
	assert.equal(ui.bindings.visible.value, false); assert.equal(ui.bindings.dataForm.comment, '');
	assert.equal(ui.events.length, 0); assert.equal(ui.context.sessionStorage.length, 1);
	user.id = '11'; await ui.bindings.openDialog({ taskId: 'A' });
	assert.equal(ui.bindings.pendingIntent.value.request.requestId, sent[0].requestId);
});

test('corrupted stored commands and unavailable storage never generate a replacement send', async t => {
	let sends = 0;
	for (const malformed of ['{broken', JSON.stringify({ requestId: 'bad', taskId: 'A', approvalComment: 'original' }), JSON.stringify({ requestId: webcrypto.randomUUID(), taskId: 'B', approvalComment: 'original' })]) {
		const storage = memoryStorage(); storage.setItem('workflow:COMPLETE:11:A', malformed);
		const ui = setup('workflow/task/approve-dialog.vue', { sessionStorage: storage, complete: async () => { sends++; } }); t.after(ui.destroy);
		await ui.bindings.openDialog({ taskId: 'A' }); validTaskForm(ui.bindings); ui.bindings.dataForm.comment = 'new';
		await ui.bindings.onSubmit();
		assert.equal(ui.bindings.storageReady.value, false); assert.ok(ui.bindings.error.value); assert.equal(storage.length, 1);
	}
	const unavailable = setup('workflow/task/approve-dialog.vue', { sessionStorage: { length: 0, setItem() { throw new Error('disabled'); } }, complete: async () => { sends++; } }); t.after(unavailable.destroy);
	await unavailable.bindings.openDialog({ taskId: 'A' }); validTaskForm(unavailable.bindings); unavailable.bindings.dataForm.comment = 'new'; await unavailable.bindings.onSubmit();
	assert.equal(sends, 0); assert.ok(unavailable.bindings.error.value);
});

test('task lookup recognizes null successful response, preserves not-found and rejects mismatched results', async t => {
	let result;
	const ui = setup('workflow/task/approve-dialog.vue', taskApis({ getCommand: async requestId => {
		if (!result) throw { data: { errorCode: 'WORKFLOW_COMMAND_NOT_FOUND' } };
		return { code: 0, data: { requestId, operation: 'COMPLETE', resourceId: 'A', resultCode: 'SUCCESS', completedAt: '2026-09-21', response: null, ...result } };
	} })); t.after(ui.destroy);
	await ui.bindings.openDialog({ taskId: 'A' }); validTaskForm(ui.bindings); ui.bindings.dataForm.comment = 'original'; await ui.bindings.onSubmit();
	await ui.bindings.queryResult(); assert.match(ui.bindings.error.value, /尚未查询/); assert.ok(ui.bindings.pendingIntent.value);
	result = { resourceId: 'B' }; await ui.bindings.queryResult(); assert.ok(ui.bindings.pendingIntent.value);
	result = {}; await ui.bindings.queryResult(); assert.equal(ui.bindings.pendingIntent.value, null); assert.equal(ui.context.sessionStorage.length, 0);
	assert.equal(ui.events.filter(([event]) => event === 'refresh').length, 1);
});

test('pending commands remain queryable and retryable with original payload when todo rows disappear', async t => {
	const storage = memoryStorage(), sent = [];
	const creator = setup('workflow/task/approve-dialog.vue', taskApis({ sessionStorage: storage })); t.after(creator.destroy);
	await creator.bindings.openDialog({ taskId: 'A', taskName: '原审批任务' }); validTaskForm(creator.bindings); creator.bindings.dataForm.comment = 'original'; await creator.bindings.onSubmit();
	const panel = setup('workflow/components/pending-commands.vue', { sessionStorage: storage, defineProps: () => ({ kind: 'task' }), complete: async data => { sent.push(data); return { code: 0, data: null }; } }); t.after(panel.destroy);
	await settle();
	assert.equal(panel.bindings.entries.value.length, 1);
	assert.match(panel.template, /原内容|原操作/);
	await panel.bindings.retryIntent(panel.bindings.entries.value[0]);
	assert.equal(sent[0].taskId, 'A'); assert.equal(sent[0].approvalComment, 'original'); assert.equal(storage.length, 0);
});

const tableApis = {
	todoPageList: async () => ({}), donePageList: async () => ({}), myProcessPageList: async () => ({}), useI18n: () => ({ t: value => value }),
	useTable: () => ({ getDataList() {}, currentChangeHandle() {}, sizeChangeHandle() {}, tableStyle: {} }),
};
test('task list locks the resolve prompt and keeps unresolved original comment visible in the pending panel', async t => {
	const prompt = deferred(), sent = []; let prompts = 0;
	const ui = setup('workflow/task/list.vue', { ...tableApis,
		ElMessageBox: { prompt: () => { prompts++; return prompt.promise; } },
		resolve: async data => { sent.push(JSON.parse(JSON.stringify(data))); throw { msg: 'timeout' }; },
	}); t.after(ui.destroy);
	const first = ui.bindings.resolveTask({ taskId: 'A' });
	const duplicate = ui.bindings.resolveTask({ taskId: 'B' });
	assert.equal(prompts, 1);
	prompt.resolve({ value: 'original assistance' }); await Promise.all([first, duplicate]);
	await ui.bindings.resolveTask({ taskId: 'A' });
	assert.equal(prompts, 1); assert.equal(sent.length, 1);
	assert.match(ui.template, /pending-commands/);
	assert.equal(ui.context.listWorkflowIntents('task')[0].request.comment, 'original assistance');
});

test('cancel freezes resource and actor before confirmation, blocks duplicates and preserves lost results', async t => {
	const confirmation = deferred(), sent = [], user = vue.reactive({ id: '11' }); let confirmations = 0;
	const ui = setup('workflow/process/instance.vue', { ...tableApis, useUserInfo: () => ({ userInfos: { user } }),
		useMessageBox: () => ({ confirm: () => { confirmations++; return confirmation.promise; } }),
		cancel: async (...args) => { sent.push(args); throw { msg: 'timeout' }; },
	}); t.after(ui.destroy);
	const row = { processInstanceId: 'p1' }, first = ui.bindings.handleCancel(row);
	const duplicate = ui.bindings.handleCancel({ processInstanceId: 'p2' });
	assert.equal(confirmations, 1);
	row.processInstanceId = 'changed'; confirmation.resolve(); await Promise.all([first, duplicate]);
	assert.equal(sent[0][0], 'p1'); assert.equal(sent[0][2], '用户取消');
	await ui.bindings.handleCancel({ processInstanceId: 'p1' });
	assert.equal(confirmations, 1); assert.equal(sent.length, 1);
	assert.match(ui.template, /pending-commands/);
});

test('pending panel hides other actors, honors view/edit permissions, and ignores a late lookup', async t => {
	const storage = memoryStorage(), user = vue.reactive({ id: '11' }), permissions = vue.reactive({ view: true, edit: true }), query = deferred();
	const creator = setup('workflow/task/approve-dialog.vue', taskApis({ sessionStorage: storage })); t.after(creator.destroy);
	await creator.bindings.openDialog({ taskId: 'A' }); validTaskForm(creator.bindings); creator.bindings.dataForm.comment = 'private'; await creator.bindings.onSubmit();
	let writes = 0;
	const panel = setup('workflow/components/pending-commands.vue', { sessionStorage: storage, defineProps: () => ({ kind: 'task' }),
		useUserInfo: () => ({ userInfos: { user } }), auth: permission => permission.endsWith('_view') ? permissions.view : permissions.edit,
		getCommand: () => query.promise, complete: async () => { writes++; return { code: 0 }; },
	}); t.after(panel.destroy);
	const intent = panel.bindings.entries.value[0];
	permissions.edit = false; await panel.bindings.retryIntent(intent); assert.equal(writes, 0); assert.equal(storage.length, 1);
	permissions.view = false; await settle(); assert.equal(panel.bindings.entries.value.length, 0);
	permissions.view = true; await settle(); assert.equal(panel.bindings.entries.value.length, 1);
	const lookup = panel.bindings.queryIntent(intent);
	user.id = '22'; assert.equal(panel.bindings.entries.value.length, 0);
	query.resolve({ code: 0, data: { requestId: intent.request.requestId, operation: 'COMPLETE', resourceId: 'A', resultCode: 'SUCCESS', completedAt: 'today', response: null } });
	await lookup; assert.equal(storage.length, 1); assert.equal(panel.events.length, 0); assert.equal(panel.bindings.error.value, '');
});

test('lifecycle lookup requires a matching successful Boolean and retry carries the persisted reason', async t => {
	let found = false; const sent = [];
	const ui = setup('workflow/components/pending-commands.vue', { defineProps: () => ({ kind: 'process' }),
		getCommand: async requestId => ({ code: 0, data: { requestId, operation: 'TERMINATE', resourceId: 'p1', resultCode: 'SUCCESS', completedAt: 'today', response: found } }),
		cancel: async (...args) => { sent.push(args); return { code: 0, data: true }; },
	}); t.after(ui.destroy);
	const intent = ui.context.pendingWorkflowIntent('TERMINATE', 'p1', { reason: 'original reason' });
	await ui.bindings.queryIntent(intent); assert.equal(ui.context.sessionStorage.length, 1);
	await ui.bindings.retryIntent(intent); assert.equal(sent[0][2], 'original reason'); assert.equal(ui.context.sessionStorage.length, 0);
	const second = ui.context.pendingWorkflowIntent('TERMINATE', 'p1', { reason: 'second reason' });
	found = true; await ui.bindings.queryIntent(second); assert.equal(ui.context.sessionStorage.length, 0);
});

test('successful old responses cannot clear a replacement request stored under the same legacy key', async t => {
	const storage = memoryStorage(), query = deferred(), originalId = webcrypto.randomUUID();
	storage.setItem('workflow:COMPLETE:11:A', JSON.stringify({ requestId: originalId, taskId: 'A', approvalComment: 'original' }));
	const ui = setup('workflow/task/approve-dialog.vue', { sessionStorage: storage, getCommand: () => query.promise }); t.after(ui.destroy);
	await ui.bindings.openDialog({ taskId: 'A' }); const lookup = ui.bindings.queryResult();
	const replacement = { requestId: webcrypto.randomUUID(), taskId: 'A', approvalComment: 'newer' };
	storage.setItem('workflow:COMPLETE:11:A', JSON.stringify(replacement));
	query.resolve({ code: 0, data: { requestId: originalId, operation: 'COMPLETE', resourceId: 'A', resultCode: 'SUCCESS', completedAt: 'today', response: null } });
	await lookup;
	assert.equal(JSON.parse(storage.getItem('workflow:COMPLETE:11:A')).requestId, replacement.requestId);
	assert.equal(ui.events.length, 0);
});

test('actor changes during a resolve prompt or cancellation confirmation never send under the new account', async t => {
	for (const process of [false, true]) {
		const user = vue.reactive({ id: '11' }), confirmation = deferred(); let sent = 0;
		const ui = setup(process ? 'workflow/process/instance.vue' : 'workflow/task/list.vue', { ...tableApis,
			useUserInfo: () => ({ userInfos: { user } }),
			ElMessageBox: { prompt: () => confirmation.promise }, useMessageBox: () => ({ confirm: () => confirmation.promise }),
			resolve: async () => { sent++; }, cancel: async () => { sent++; },
		}); t.after(ui.destroy);
		const action = process ? ui.bindings.handleCancel({ processInstanceId: 'p1' }) : ui.bindings.resolveTask({ taskId: 'A' });
		user.id = '22'; confirmation.resolve({ value: 'original' }); await action;
		assert.equal(sent, 0); assert.equal(ui.context.sessionStorage.length, 0);
	}
});

test('commands are scoped by actor and resource and cross-operation reuse requires acknowledgement', async t => {
	const user = vue.reactive({ id: '11' });
	const ui = setup('workflow/task/approve-dialog.vue', { useUserInfo: () => ({ userInfos: { user } }) }); t.after(ui.destroy);
	const first = ui.context.pendingWorkflowIntent('TRANSFER', 'A', { taskId: 'A', transferUserId: '22', transferReason: 'original' });
	assert.throws(() => ui.context.pendingWorkflowIntent('REJECT', 'A', { taskId: 'A', rejectReason: 'new' }), /待确认/);
	const second = ui.context.pendingWorkflowIntent('REJECT', 'B', { taskId: 'B', rejectReason: 'new' });
	assert.notEqual(first.request.requestId, second.request.requestId);
	await ui.bindings.openDialog({ taskId: 'A' }); assert.equal(ui.bindings.storageReady.value, false);
	user.id = '33'; assert.equal(ui.context.listWorkflowIntents('task').length, 0);
	const otherActor = ui.context.pendingWorkflowIntent('TRANSFER', 'A', { taskId: 'A', transferUserId: '44', transferReason: 'other actor' });
	assert.notEqual(first.request.requestId, otherActor.request.requestId);
	assert.throws(() => ui.context.clearWorkflowIntent(first), /当前用户已变更/);
	assert.equal(ui.context.sessionStorage.length, 3);
});

test('pending panel prevents duplicate retries while sending', async t => {
	const saving = deferred(); let sends = 0;
	const ui = setup('workflow/components/pending-commands.vue', { defineProps: () => ({ kind: 'task' }),
		claim: async () => { sends++; await saving.promise; return { code: 0, data: null }; },
	}); t.after(ui.destroy);
	const intent = ui.context.pendingWorkflowIntent('CLAIM', 'A', { taskId: 'A' });
	const first = ui.bindings.retryIntent(intent), second = ui.bindings.retryIntent(intent);
	assert.equal(sends, 1); saving.resolve(); await Promise.all([first, second]);
	assert.equal(ui.context.sessionStorage.length, 0);
});

test('task and lifecycle HTTP adapters preserve supplied request IDs and cancel reasons', async () => {
	const requests = [];
	const loadApi = path => {
		const script = ts.transpileModule(readFileSync(new URL(`../bixi-ui/src/api/workflow/${path}.ts`, import.meta.url), 'utf8'), {
			compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
		}).outputText;
		const exports = {};
		vm.runInNewContext(`(function(require, exports) { ${script} })`)(() => ({ default: config => { requests.push(JSON.parse(JSON.stringify(config))); return Promise.resolve({ code: 0 }); } }), exports);
		return exports;
	};
	const task = loadApi('task'), process = loadApi('process'), id = webcrypto.randomUUID();
	for (const name of ['complete', 'reject', 'transfer', 'delegate', 'resolve', 'addComment']) {
		await task[name]({ taskId: 'A', requestId: id });
		await task[name]({ taskId: 'A', requestId: id });
		assert.equal(requests.at(-1).data.requestId, id); assert.deepEqual(requests.at(-1), requests.at(-2));
	}
	await task.claim('A', id); assert.deepEqual(requests.at(-1).params, { taskId: 'A', requestId: id });
	await task.unclaim('A', id); assert.deepEqual(requests.at(-1).params, { requestId: id });
	await process.cancel('p1', id, 'original reason'); assert.deepEqual(requests.at(-1).params, { requestId: id, reason: 'original reason' });
	for (const name of ['suspend', 'activate']) { await process[name]('p1', id); assert.deepEqual(requests.at(-1).params, { requestId: id }); }
	await process.getCommand(id); assert.equal(requests.at(-1).url, `/admin/workflow/command/${id}`);
});

test('malformed acknowledgement metadata cannot unlock a second command for the same task', async t => {
	const ui = setup('workflow/task/approve-dialog.vue'); t.after(ui.destroy);
	const original = ui.context.pendingWorkflowIntent('COMPLETE', 'A', { taskId: 'A', approvalComment: 'original' });
	ui.context.sessionStorage.setItem(original.storageKey, JSON.stringify({ ...original, acknowledged: 'false' }));
	assert.throws(() => ui.context.pendingWorkflowIntent('REJECT', 'A', { taskId: 'A', rejectReason: 'new' }), /无法恢复/);
	assert.equal(ui.context.sessionStorage.length, 1);
});

for (const kind of ['task', 'process']) {
	test(`pending ${kind} cards distinguish identical names and original contents with persisted resource numbers`, t => {
		const ui = setup('workflow/components/pending-commands.vue', { defineProps: () => ({ kind }) }); t.after(ui.destroy);
		for (const resourceId of ['resource-A', 'resource-B']) {
			ui.context.pendingWorkflowIntent(kind === 'task' ? 'COMPLETE' : 'TERMINATE', resourceId,
				kind === 'task' ? { taskId: resourceId, approvalComment: 'same original content' } : { reason: 'same original content' },
				{ taskName: kind === 'task' ? '同名审批' : undefined, processName: '同名流程' });
		}
		// Compile the actual SFC template and inspect its rendered cards, including slot content.
		const { compile } = require('@vue/compiler-dom');
		const { code } = compile(ui.template, { mode: 'function', prefixIdentifiers: true });
		const render = new Function('Vue', code)({ ...vue, resolveComponent: name => name, resolveDirective: () => ({}), withDirectives: node => node });
		const context = vue.proxyRefs({ ...ui.bindings, kind,
			workflowOperationLabels: ui.context.workflowOperationLabels,
			canQueryWorkflowIntent: ui.context.canQueryWorkflowIntent,
			canRetryWorkflowIntent: ui.context.canRetryWorkflowIntent,
		});
		const children = node => Array.isArray(node?.children) ? node.children : node?.children?.default ? node.children.default() : [];
		const textOf = node => typeof node === 'string' ? node : typeof node?.children === 'string' ? node.children : children(node).map(textOf).join('');
		const cards = [];
		const visit = node => { if (node?.type === 'el-card') cards.push(textOf(node)); else children(node).forEach(visit); };
		visit(render(context, []));
		assert.equal(cards.length, 2);
		for (const [index, resourceId] of ['resource-A', 'resource-B'].entries()) {
			assert.ok(cards[index].includes(kind === 'task' ? '同名审批' : '同名流程'));
			assert.ok(cards[index].includes(`${kind === 'task' ? '任务编号' : '流程编号'}：${resourceId}`), cards[index]);
			assert.ok(cards[index].includes('原内容：same original content'));
		}
	});
}


// The real storage helper and js-cookie run against a browser-like cookie/storage surface.
const authStorage = ui => {
	const cookies = new Map();
	ui.context.window = { sessionStorage: ui.context.sessionStorage, localStorage: memoryStorage() };
	ui.context.document = {
		get cookie() { return [...cookies].map(([key, value]) => `${key}=${value}`).join('; '); },
		set cookie(serialized) {
			const [pair, ...attributes] = serialized.split(';');
			const separator = pair.indexOf('='), key = pair.slice(0, separator), value = pair.slice(separator + 1);
			const expires = attributes.find(attribute => attribute.trim().toLowerCase().startsWith('expires='));
			if (expires && new Date(expires.trim().slice(8)).getTime() < Date.now()) cookies.delete(key);
			else cookies.set(key, value);
		},
	};
	return ui.loadModule('utils/storage').Session;
};

test('real authentication cleanup preserves a transfer intent across logout and relogin without exposing it to another actor', async t => {
	const user = vue.reactive({ id: '11' }), sent = [];
	const ui = setup('workflow/task/transfer-dialog.vue', {
		useUserInfo: () => ({ userInfos: { user } }),
		userListApi: async () => ({ code: 0, data: { records: [{ id: '22', name: 'Original assignee' }] } }),
		transfer: async request => { sent.push(JSON.parse(JSON.stringify(request))); throw { msg: 'timeout' }; },
	}); t.after(ui.destroy);
	const Session = authStorage(ui);
	Session.set('token', 'old-access'); Session.set('refresh_token', 'old-refresh'); Session.set('tenantId', 1); Session.set('userInfo', { id: '11' }); Session.set('ordinary-cache', 'discard');
	ui.context.document.cookie = 'tenantId=1';
	assert.match(ui.context.document.cookie, /token=old-access/); assert.match(ui.context.document.cookie, /refresh_token=old-refresh/);
	await ui.bindings.openDialog({ taskId: 'A', taskName: 'Original task' }); validTaskForm(ui.bindings);
	ui.bindings.dataForm.userId = '22'; ui.bindings.dataForm.comment = 'Original reason'; await ui.bindings.onSubmit();
	const original = ui.context.restoreWorkflowIntent('task', 'A');
	Session.clear();
	for (const key of ['token', 'refresh_token', 'tenantId', 'userInfo', 'ordinary-cache']) assert.equal(ui.context.sessionStorage.getItem(key), null, key);
	assert.equal(Session.getToken(), undefined); assert.equal(Session.get('refresh_token'), undefined); assert.equal(ui.context.document.cookie, '');
	user.id = ''; Session.clear(); // The unauthenticated route guard also clears the session.
	user.id = '33';
	assert.equal(ui.context.listWorkflowIntents('task').length, 0);
	assert.throws(() => ui.context.clearWorkflowIntent(original), /当前用户已变更/);
	Session.set('token', 'other-access'); Session.clear(); // Logging out another actor must also preserve it.
	user.id = '11'; Session.set('token', 'new-access');
	await ui.bindings.openDialog({ taskId: 'A' });
	assert.ok(ui.bindings.pendingIntent.value, 'the original command must survive authentication cleanup');
	assert.equal(ui.bindings.pendingIntent.value.request.requestId, original.request.requestId);
	assert.equal(ui.bindings.dataForm.userId, '22'); assert.equal(ui.bindings.dataForm.comment, 'Original reason');
	await ui.bindings.onSubmit(); assert.deepEqual(sent[1], sent[0]);
});

test('real authentication cleanup preserves the existing START request for the original actor only', async t => {
	let actorId = '11'; const sent = [];
	const ui = setup(startPath, { useUserInfo: () => ({ userInfos: { user: { id: actorId } } }),
		start: async request => { sent.push(JSON.parse(JSON.stringify(request))); throw { msg: 'timeout' }; },
	}); t.after(ui.destroy);
	const Session = authStorage(ui);
	await ui.bindings.openDialog(startDefinition); validStartForm(ui.bindings);
	ui.bindings.dataForm.title = 'Original start'; ui.bindings.dataForm.variables = { approver: '22' }; await ui.bindings.onSubmit(); ui.close();
	const key = 'workflow:START:11:approval', original = ui.context.sessionStorage.getItem(key);
	Session.set('token', 'expired'); Session.clear();
	assert.equal(ui.context.sessionStorage.getItem(key), original);
	assert.equal(Session.getToken(), undefined);
	actorId = '33'; await ui.bindings.openDialog(startDefinition);
	assert.equal(ui.bindings.pendingRequest.value, undefined); assert.equal(ui.bindings.dataForm.title, '');
	await ui.bindings.newIntent(); assert.equal(ui.context.sessionStorage.getItem(key), original); ui.close();
	Session.clear(); actorId = '11'; await ui.bindings.openDialog(startDefinition);
	assert.equal(ui.bindings.pendingRequest.value.requestId, sent[0].requestId); assert.equal(ui.bindings.dataForm.title, 'Original start');
	await ui.bindings.onSubmit(); assert.deepEqual(sent[1], sent[0]);
});

test('authentication cleanup preserves supported legacy and damaged workflow commands but clears unrelated namespaces', t => {
	const ui = setup('workflow/task/approve-dialog.vue'); t.after(ui.destroy);
	const Session = authStorage(ui), storage = ui.context.sessionStorage;
	const preserved = ['workflow:TRANSFER:11:task-A', 'workflow:TERMINATE:11:process-A', 'workflow:START:11:approval', `workflow:command:11:TRANSFER:task-A:${webcrypto.randomUUID()}`];
	const removed = ['workflow:token', 'workflow:other:11:A', 'workflow:command:token', 'unrelated', 'userInfo', 'token', 'refresh_token'];
	for (const key of [...preserved, ...removed]) storage.setItem(key, '{damaged');
	Session.clear();
	for (const key of preserved) assert.equal(storage.getItem(key), '{damaged', key);
	for (const key of removed) assert.equal(storage.getItem(key), null, key);
	assert.throws(() => ui.context.restoreWorkflowIntent('task', 'task-A'), /无法恢复/);
});
