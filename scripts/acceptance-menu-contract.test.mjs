import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const dataSql = await readFile(new URL('../bixi-project-documents/sql/02_data.sql', import.meta.url), 'utf8');
const dictMenuSql = await readFile(new URL('../bixi-project-documents/sql/generated/acceptance_dictAggregate_menu.sql', import.meta.url), 'utf8');
const paramMenuSql = await readFile(new URL('../bixi-project-documents/sql/generated/acceptance_publicParam_menu.sql', import.meta.url), 'utf8');
const migrationSql = await readFile(new URL('../bixi-project-documents/sql/migrations/20261005_acceptance_menus.sql', import.meta.url), 'utf8');

const dictMenus = [
	[7200, null, '/acceptance/dictAggregate/index'],
	[7201, 'acceptance_dict_aggregate_view'],
	[7202, 'acceptance_dict_aggregate_add'],
	[7203, 'acceptance_dict_aggregate_edit'],
	[7204, 'acceptance_dict_aggregate_del'],
	[7205, 'acceptance_dict_aggregate_import'],
	[7206, 'acceptance_dict_aggregate_export'],
	[7207, 'acceptance_sys_dict_item_add'],
	[7208, 'acceptance_sys_dict_item_edit'],
	[7209, 'acceptance_sys_dict_item_del'],
];

const paramMenus = [
	[7210, null, '/acceptance/publicParam/index'],
	[7211, 'acceptance_public_param_view'],
	[7212, 'acceptance_public_param_add'],
	[7213, 'acceptance_public_param_edit'],
	[7214, 'acceptance_public_param_del'],
	[7215, 'acceptance_public_param_import'],
	[7216, 'acceptance_public_param_export'],
];

test('new database seed contains stable Acceptance menus and administrator grants', () => {
	for (const [id, permission, path] of [...dictMenus, ...paramMenus]) {
		assert.match(dataSql, new RegExp(`INSERT INTO sys_menu[\\s\\S]*?\\(${id},`), `missing menu ${id}`);
		assert.match(dataSql, new RegExp(`\\(1, ${id}, CURRENT_TIMESTAMP\\)`), `administrator is missing menu ${id}`);
		if (permission) assert.match(dataSql, new RegExp(`'${permission}'`), `missing permission ${permission}`);
		if (path) assert.match(dataSql, new RegExp(`'${path}'`), `missing route ${path}`);
	}
	assert.doesNotMatch(dataSql, /UUID_SHORT\(\)/, 'new database seed must not allocate unstable menu IDs');
});

test('generated Acceptance menu scripts use the same stable identity contract', () => {
	for (const [id, permission, path] of dictMenus) {
		assert.match(dictMenuSql, new RegExp(`\\(${id},`), `dict menu ${id} is not stable`);
		if (permission) assert.match(dictMenuSql, new RegExp(`'${permission}'`));
		if (path) assert.match(dictMenuSql, new RegExp(`'${path}'`));
	}
	for (const [id, permission, path] of paramMenus) {
		assert.match(paramMenuSql, new RegExp(`\\(${id},`), `param menu ${id} is not stable`);
		if (permission) assert.match(paramMenuSql, new RegExp(`'${permission}'`));
		if (path) assert.match(paramMenuSql, new RegExp(`'${path}'`));
	}
	assert.doesNotMatch(`${dictMenuSql}\n${paramMenuSql}`, /UUID_SHORT\(\)/);
});

test('existing database migration is guarded, repeatable, and binds the administrator role', () => {
	assert.match(migrationSql, /CREATE PROCEDURE bixi_migrate_acceptance_menus_20261005/);
	assert.match(migrationSql, /DROP PROCEDURE IF EXISTS bixi_migrate_acceptance_menus_20261005/);
	assert.match(migrationSql, /SIGNAL SQLSTATE '45000'/);
	assert.match(migrationSql, /sys_role_menu/);
	for (const [id] of [...dictMenus, ...paramMenus]) {
		assert.match(migrationSql, new RegExp(`\\b${id}\\b`), `migration is missing menu ${id}`);
	}
});
