#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(new URL('../bixi-ui/package.json', import.meta.url));
const ts = require('typescript');
const { compileScript, compileTemplate, parse } = require('@vue/compiler-sfc');
const fixtureRoot = new URL('../bixi-module/bixi-generator/target/generated-single-table-frontend/', import.meta.url);

function assertTypeScriptSyntax(source, filename) {
	const result = ts.transpileModule(source, {
		fileName: filename,
		reportDiagnostics: true,
		compilerOptions: {
			target: ts.ScriptTarget.ES2022,
			module: ts.ModuleKind.ESNext,
			isolatedModules: true,
		},
	});
	const errors = (result.diagnostics || []).filter(diagnostic => diagnostic.category === ts.DiagnosticCategory.Error);
	assert.deepEqual(errors.map(diagnostic => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')), []);
}

function assertComponent(source, filename) {
	const parsed = parse(source, { filename });
	assert.deepEqual(parsed.errors, []);
	assert.ok(parsed.descriptor.template, `${filename} must contain a template`);
	assert.ok(parsed.descriptor.scriptSetup, `${filename} must contain script setup`);
	const compiledScript = compileScript(parsed.descriptor, { id: `generated-single-${filename}` });
	assertTypeScriptSyntax(compiledScript.content, `${filename}.ts`);
	const compiledTemplate = compileTemplate({
		id: `generated-single-${filename}`,
		filename,
		source: parsed.descriptor.template.content,
	});
	assert.deepEqual(compiledTemplate.errors, []);
}

test('Java-rendered single-table API, list and form have valid TypeScript and SFC syntax', async () => {
	const [api, list, form] = await Promise.all([
		readFile(new URL('inventoryItem.ts', fixtureRoot), 'utf8'),
		readFile(new URL('index.vue', fixtureRoot), 'utf8'),
		readFile(new URL('form.vue', fixtureRoot), 'utf8'),
	]);
	assertTypeScriptSyntax(api, 'inventoryItem.ts');
	assertComponent(list, 'index.vue');
	assertComponent(form, 'form.vue');
	assert.match(api, /fetchList/);
	assert.match(api, /getObj/);
	assert.match(api, /addObj/);
	assert.match(api, /putObj/);
	assert.match(api, /delObj/);
	assert.match(list, /inventory_inventory_item_add/);
	assert.match(list, /inventory_inventory_item_edit/);
	assert.match(list, /inventory_inventory_item_del/);
	assert.match(list, /empty-text="暂无数据"/);
	assert.match(list, /loadError/);
	assert.match(form, /itemName/);
	assert.match(form, /rules/);
});
