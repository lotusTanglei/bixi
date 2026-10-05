#!/usr/bin/env node
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const generator = readFileSync(new URL('../bixi-ui/src/views/gen/gener/index.vue', import.meta.url), 'utf8');
const api = readFileSync(new URL('../bixi-ui/src/api/gen/table.ts', import.meta.url), 'utf8');
const tableIndex = readFileSync(new URL('../bixi-ui/src/views/gen/table/index.vue', import.meta.url), 'utf8');
const templateApi = readFileSync(new URL('../bixi-ui/src/api/gen/template.ts', import.meta.url), 'utf8');
const templateView = readFileSync(new URL('../bixi-ui/src/views/gen/template/index.vue', import.meta.url), 'utf8');

const exportedFunction = (source, name) => {
	const start = source.indexOf(`export function ${name}`);
	assert.notEqual(start, -1, `${name} is missing`);
	const next = source.indexOf('export function ', start + 1);
	return source.slice(start, next === -1 ? source.length : next);
};

test('directory generation uses the response body and publishes its exact preview version', () => {
	assert.doesNotMatch(generator, /const\s*\{\s*data\s*\}\s*=\s*await\s+useGeneratorPreviewApi/);
	assert.match(generator, /const\s+previewFiles\s*=\s*\(?await\s+useGeneratorPreviewApi/);
	assert.match(generator, /previewFiles\[0\]\?\.templateVersion/);
	assert.match(generator, /if\s*\(\s*!templateVersion\s*\)/);
	assert.match(generator, /useGeneratorCodeApi\(\{\s*tableIds:\s*\[tableId\.value\],\s*templateVersion,\s*overwrite:/s);
});

test('ZIP generation downloads the exact previewed version', () => {
	assert.match(generator, /useGeneratorPreviewApi\(tableId\.value\)/);
	assert.match(generator, /templateVersion=/);
	assert.match(generator, /encodeURIComponent\(templateVersion\)/);
	assert.match(generator, /\/gen\/generator\/download\?tableIds=/);
});

test('generation and schema synchronization are POST requests', () => {
	assert.match(api, /useGeneratorCodeApi[\s\S]*?method:\s*['"]post['"]/);
	assert.match(api, /useSyncTableApi[\s\S]*?request\.post/);
	assert.match(api, /useTableApi[\s\S]*?request\.post/);
	assert.match(tableIndex,
		/v-auth-all="\['codegen_table_generate',\s*'codegen_table_edit'\]"[^>]+@click="openGen\(scope\.row\)"/);
	assert.match(exportedFunction(templateApi, 'online'), /method:\s*['"]post['"]/);
});

test('template source updates use edit permission and display verified source metadata', () => {
	const buttonStart = templateView.indexOf('@click="onlineUpdate"');
	assert.notEqual(buttonStart, -1, 'online update button is missing');
	const updateButton = templateView.slice(buttonStart, templateView.indexOf('</el-button>', buttonStart));
	assert.match(updateButton, /v-auth="'codegen_template_edit'"/);
	assert.match(templateView, /data\.revision/);
	assert.match(templateView, /data\.manifestDigest/);
});

test('phase-two generator and Quartz permissions are seeded and migratable', () => {
	const seed = readFileSync(new URL('../bixi-project-documents/sql/02_data.sql', import.meta.url), 'utf8');
	const migrationUrl = new URL('../bixi-project-documents/sql/migrations/20260924_phase2_permissions.sql', import.meta.url);
	assert.ok(existsSync(migrationUrl), 'existing databases need an additive phase-two permission migration');
	const migration = readFileSync(migrationUrl, 'utf8');
	const permissions = new Map([
		['codegen_table_view', 2301],
		['codegen_table_sync', 2302],
		['codegen_table_edit', 2303],
		['codegen_table_generate', 2304],
		['codegen_table_export', 2305],
		['job_sys_job_view', 2872],
		['job_sys_job_record_view', 2873],
		['job_sys_job_record_del', 2874],
	]);
	for (const [permission, menuId] of permissions) {
		assert.match(seed, new RegExp(`VALUES \\(${menuId},[^\\n]+['"]${permission}['"]`), `${permission} missing from clean seed`);
		assert.match(seed, new RegExp(`sys_role_menu[^\\n]+VALUES \\(1, ${menuId},`), `${permission} missing administrator grant`);
		assert.match(migration, new RegExp(`\\(${menuId},[^\\n]+['"]${permission}['"]`), `${permission} missing from migration`);
	}
});

test('fixed-source updates have a clean-schema uniqueness guard and operator contract', () => {
	const schema = readFileSync(new URL('../bixi-project-documents/sql/01_schema.sql', import.meta.url), 'utf8');
	const migrationUrl = new URL('../bixi-project-documents/sql/migrations/20260924_generator_template_group_uniqueness.sql', import.meta.url);
	const operationsUrl = new URL('../.docs/generator/FIXED-SOURCE-UPDATES.md', import.meta.url);
	const manifestUrl = new URL('../.docs/generator/examples/template-update-manifest.json.template', import.meta.url);
	assert.ok(existsSync(migrationUrl), 'existing databases need an active template-group uniqueness migration');
	assert.ok(existsSync(operationsUrl), 'fixed-source update operations must be documented');
	assert.ok(existsSync(manifestUrl), 'a manifest template must be available without fake source pins');
	assert.match(schema, /active_group_name[^\n]+GENERATED ALWAYS AS[^\n]+del_flag[^\n]+group_name/i);
	assert.match(schema, /UNIQUE KEY `uk_gen_group_active_name` \(`active_group_name`\)/);
	const migration = readFileSync(migrationUrl, 'utf8');
	assert.match(migration, /Duplicate active generator group names require manual resolution/);
	assert.match(migration, /ADD CONSTRAINT uk_gen_group_active_name UNIQUE \(active_group_name\)/);
	const operations = readFileSync(operationsUrl, 'utf8');
	assert.match(operations, /默认关闭/);
	assert.match(operations, /POST \/template\/online/);
});
