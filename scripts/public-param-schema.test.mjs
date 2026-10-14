import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const sqlRoot = new URL('../bixi-project-documents/sql/', import.meta.url);

test('active public parameter keys are unique per tenant without blocking soft-delete reuse', () => {
	for (const file of ['01_schema.sql', 'bixi.sql']) {
		const sql = readFileSync(new URL(file, sqlRoot), 'utf8');
		const table = sql.match(/CREATE TABLE `sys_public_param` \(([\s\S]*?)\) ENGINE=InnoDB/)?.[1];
		assert.ok(table, `${file}: missing sys_public_param`);
		assert.match(table, /`active_key`[^\n]*GENERATED ALWAYS AS \(IF\(`del_flag` = '0', `key`, NULL\)\) STORED/);
		assert.match(table, /`key`[^\n]*NOT NULL/);
	}

	const constraints = readFileSync(new URL('03_constraints.sql', sqlRoot), 'utf8');
	assert.match(constraints, /ALTER TABLE sys_public_param MODIFY COLUMN tenant_id bigint NOT NULL;/);
	assert.match(constraints, /UNIQUE \(tenant_id, `active_key`\)/);
	assert.doesNotMatch(constraints, /UNIQUE \(tenant_id, `key`\)/);
});

test('administrator public parameter view permission belongs to tenant 1', () => {
	const data = readFileSync(new URL('02_data.sql', sqlRoot), 'utf8');
	assert.match(data, /VALUES \(2214,[^\n]*'sys_syspublicparam_view'[^\n]*, 1, NULL\);/);
	assert.match(data, /VALUES \(1, 2214,/);
});
