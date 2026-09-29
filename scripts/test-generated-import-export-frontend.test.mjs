import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const require = createRequire(new URL('../bixi-ui/package.json', import.meta.url));
const ts = require('typescript');
const { compileScript, compileTemplate, parse } = require('@vue/compiler-sfc');
const fixtureRoot = new URL('../bixi-module/bixi-generator/target/generated-import-export-frontend/', import.meta.url);

function typeScriptDiagnostics(apiSource, componentSource) {
	const virtualRoot = fileURLToPath(new URL('../bixi-ui/.generated-import-export-typecheck/', import.meta.url));
	const files = new Map([
		[`${virtualRoot}api/inventory/inventoryItem.ts`, apiSource],
		[`${virtualRoot}views/inventory/inventoryItem/index.ts`, componentSource],
		[`${virtualRoot}utils/request.d.ts`, `
			export interface RequestOptions {
				url: string;
				method: 'get' | 'post' | 'put' | 'delete';
				params?: Record<string, unknown>;
				data?: unknown;
				responseType?: 'blob';
			}
			export interface RequestResponse<T> { data: T; }
			export default function request<T = unknown>(options: RequestOptions): Promise<RequestResponse<T>>;
		`],
		[`${virtualRoot}hooks/table.d.ts`, `
			export interface BasicTableProps {
				queryForm: Record<string, unknown>;
				pageList: (query?: Record<string, unknown>) => Promise<unknown>;
				loading?: boolean;
				dataList?: unknown[];
				pagination?: Record<string, unknown>;
			}
			export function useTable(state: BasicTableProps): {
				getDataList: () => Promise<void>;
				currentChangeHandle: (page: number) => void;
				sizeChangeHandle: (size: number) => void;
			};
		`],
		[`${virtualRoot}hooks/message.d.ts`, `
			export function useMessage(): { success(message: string): void; error(message: string): void };
			export function useMessageBox(): { confirm(message: string): Promise<void> };
		`],
		[`${virtualRoot}utils/other.d.ts`, 'export function handleBlobFile(value: unknown, filename: string): void;'],
		[`${virtualRoot}views/inventory/inventoryItem/form.vue.d.ts`, `
			declare const component: import('vue').Component;
			export default component;
		`],
		[`${virtualRoot}globals.d.ts`, `
			export {};
			declare global {
				const defineAsyncComponent: typeof import('vue')['defineAsyncComponent'];
				const reactive: typeof import('vue')['reactive'];
				const ref: typeof import('vue')['ref'];
			}
		`],
	]);
	const options = {
		allowSyntheticDefaultImports: true,
		baseUrl: virtualRoot,
		esModuleInterop: true,
		lib: ['lib.es2022.d.ts', 'lib.dom.d.ts', 'lib.dom.iterable.d.ts'],
		module: ts.ModuleKind.ESNext,
		moduleResolution: ts.ModuleResolutionKind.Bundler,
		noEmit: true,
		paths: { '/@/*': ['*'] },
		skipLibCheck: true,
		strict: true,
		target: ts.ScriptTarget.ES2022,
		types: [],
	};
	const defaultHost = ts.createCompilerHost(options, true);
	const host = {
		...defaultHost,
		fileExists: filename => files.has(filename) || defaultHost.fileExists(filename),
		readFile: filename => files.get(filename) ?? defaultHost.readFile(filename),
		getSourceFile: (filename, languageVersion, onError, shouldCreateNewSourceFile) => {
			const source = files.get(filename);
			return source === undefined
				? defaultHost.getSourceFile(filename, languageVersion, onError, shouldCreateNewSourceFile)
				: ts.createSourceFile(filename, source, languageVersion, true);
		},
		resolveModuleNames: (moduleNames, containingFile) => moduleNames.map(moduleName => {
			let candidate;
			if (moduleName.startsWith('/@/')) {
				const base = `${virtualRoot}${moduleName.slice(3)}`;
				candidate = files.has(`${base}.ts`) ? `${base}.ts` : `${base}.d.ts`;
			}
			else if (moduleName === './form.vue') {
				candidate = `${containingFile.slice(0, containingFile.lastIndexOf('/') + 1)}form.vue.d.ts`;
			}
			if (candidate && files.has(candidate)) {
				return {
					extension: candidate.endsWith('.d.ts') ? ts.Extension.Dts : ts.Extension.Ts,
					isExternalLibraryImport: false,
					resolvedFileName: candidate,
				};
			}
			return ts.resolveModuleName(moduleName, containingFile, options, defaultHost).resolvedModule;
		}),
	};
	const program = ts.createProgram({
		rootNames: [
			`${virtualRoot}api/inventory/inventoryItem.ts`,
			`${virtualRoot}views/inventory/inventoryItem/index.ts`,
			`${virtualRoot}globals.d.ts`,
		],
		options,
		host,
	});
	return ts.getPreEmitDiagnostics(program).filter(diagnostic => diagnostic.category === ts.DiagnosticCategory.Error);
}

function diagnosticMessages(diagnostics) {
	return diagnostics.map(diagnostic => {
		const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n');
		if (!diagnostic.file || diagnostic.start === undefined) return message;
		const { line, character } = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
		return `${diagnostic.file.fileName}:${line + 1}:${character + 1} ${message}`;
	});
}

function walkTemplate(node, visit) {
	visit(node);
	for (const child of Array.isArray(node.children) ? node.children : []) walkTemplate(child, visit);
	for (const branch of Array.isArray(node.branches) ? node.branches : []) walkTemplate(branch, visit);
	if (node.type === 12 && node.content) walkTemplate(node.content, visit);
}

function expressionSource(node) {
	if (typeof node === 'string') return node;
	if (node?.type === 4) return node.content;
	if (node?.type === 8) return node.children.map(expressionSource).join('');
	return '';
}

function attribute(element, name) {
	return element.props?.find(prop => prop.type === 6 && prop.name === name)?.value?.content;
}

function directiveExpression(element, name) {
	return element.props?.find(prop => prop.type === 7 && prop.name === name)?.exp?.content;
}

function boundExpression(element, argument) {
	const binding = element.props?.find(prop => prop.type === 7 && prop.name === 'bind'
		&& expressionSource(prop.arg) === argument);
	return binding?.exp?.loc?.source ?? expressionSource(binding?.exp);
}

function directText(element) {
	return (element.children || []).map(child => {
		if (child.type === 2) return child.content;
		if (child.type === 12 && child.content?.type === 2) return child.content.content;
		return '';
	}).map(text => text.trim()).join('');
}

test('Java-rendered import/export API and Vue list compile and preserve UI behavior', async () => {
	const [api, component] = await Promise.all([
		readFile(new URL('inventoryItem.ts', fixtureRoot), 'utf8'),
		readFile(new URL('index.vue', fixtureRoot), 'utf8'),
	]);
	assert.match(api, /export const importRows/);
	assert.match(api, /export const exportRows/);

	const parsed = parse(component, { filename: 'index.vue' });
	assert.deepEqual(parsed.errors, []);
	assert.ok(parsed.descriptor.template, 'generated list must contain a template');
	assert.ok(parsed.descriptor.scriptSetup, 'generated list must contain script setup');
	const compiledScript = compileScript(parsed.descriptor, { id: 'generated-import-export-list' });
	const diagnostics = typeScriptDiagnostics(api, compiledScript.content);
	assert.deepEqual(diagnosticMessages(diagnostics), []);
	const typeErrorDiagnostics = typeScriptDiagnostics(
		api.replace("responseType: 'blob'", 'responseType: 123'), compiledScript.content);
	assert.ok(typeErrorDiagnostics.length > 0, 'semantic compilation must reject an invalid responseType');
	assert.ok(diagnosticMessages(typeErrorDiagnostics).some(message => message.includes("Type 'number'")));
	const compiledTemplate = compileTemplate({
		id: 'generated-import-export-list',
		filename: 'index.vue',
		source: parsed.descriptor.template.content,
	});
	assert.deepEqual(compiledTemplate.errors, []);

	const elements = [];
	const interpolations = [];
	walkTemplate(compiledTemplate.ast, node => {
		if (node.type === 1) elements.push(node);
		if (node.type === 5) interpolations.push(expressionSource(node.content));
	});

	const importButton = elements.find(element => element.tag === 'el-button' && directText(element) === '导入');
	const exportButton = elements.find(element => element.tag === 'el-button' && directText(element) === '导出');
	assert.ok(importButton, 'generated list must render an import button');
	assert.ok(exportButton, 'generated list must render an export button');
	assert.equal(directiveExpression(importButton, 'auth'), "'inventory_inventory_item_import'");
	assert.equal(directiveExpression(exportButton, 'auth'), "'inventory_inventory_item_export'");

	const rowNumberColumn = elements.find(element => element.tag === 'el-table-column'
		&& attribute(element, 'label') === '错误行');
	assert.ok(rowNumberColumn, 'import result must show the failing row number');
	assert.equal(attribute(rowNumberColumn, 'prop'), 'rowNumber');
	const errorExpression = interpolations.find(expression => expression.includes('scope.row.errors.join'));
	assert.ok(errorExpression, 'import result must render every error message for a row');
	const renderErrors = new Function('scope', `return ${errorExpression}`);
	const errorTable = elements.find(element => element.tag === 'el-table'
		&& attribute(element, 'empty-text') === '没有错误明细');
	assert.ok(errorTable, 'import result must contain an error details table');
	const errorRowsExpression = boundExpression(errorTable, 'data');
	assert.ok(errorRowsExpression, 'error details table must bind its displayed rows');
	const selectDisplayedRows = new Function('importResult', `return ${errorRowsExpression}`);
	const allRows = Array.from({ length: 101 }, (_, index) => ({
		rowNumber: index + 2,
		errors: [`第${index + 1}行格式错误`, `第${index + 1}行字段必填`],
	}));
	const displayedRows = selectDisplayedRows({ errors: allRows });
	assert.equal(displayedRows.length, 100);
	for (const index of [0, 49, 99]) {
		assert.equal(displayedRows[index], allRows[index]);
		assert.equal(renderErrors({ row: displayedRows[index] }), allRows[index].errors.join('；'));
	}
	assert.ok(!displayedRows.includes(allRows[100]));
});
