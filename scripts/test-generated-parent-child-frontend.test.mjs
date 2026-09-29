import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import test from 'node:test';

const require = createRequire(new URL('../bixi-ui/package.json', import.meta.url));
const ts = require('typescript');
const { compileScript, parse } = require('@vue/compiler-sfc');
const fixtureRoot = new URL('../bixi-module/bixi-generator/target/generated-parent-child-frontend/', import.meta.url);

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

test('Java-rendered parent-child API and Vue form have valid TypeScript and SFC syntax', async () => {
	const [api, form] = await Promise.all([
		readFile(new URL('purchaseOrder.ts', fixtureRoot), 'utf8'),
		readFile(new URL('form.vue', fixtureRoot), 'utf8'),
	]);
	assertTypeScriptSyntax(api, 'purchaseOrder.ts');

	const parsed = parse(form, { filename: 'form.vue' });
	assert.deepEqual(parsed.errors, []);
	assert.ok(parsed.descriptor.template, 'generated form must contain a template');
	assert.ok(parsed.descriptor.scriptSetup, 'generated form must contain script setup');
	const compiled = compileScript(parsed.descriptor, { id: 'generated-parent-child-form' });
	assertTypeScriptSyntax(compiled.content, 'form.vue.ts');
});
