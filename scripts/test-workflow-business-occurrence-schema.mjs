import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const schema = readFileSync(new URL('../bixi-project-documents/sql/01_schema.sql', import.meta.url), 'utf8');
const migration = readFileSync(
	new URL('../bixi-project-documents/sql/migrations/20260924_workflow_business_occurrence.sql', import.meta.url),
	'utf8'
);

test('canonical workflow occurrence identity is source-owner scoped', () => {
	const table = schema.match(/CREATE TABLE `wf_process_instance` \([\s\S]*?\n\) ENGINE/iu)?.[0] ?? '';
	assert.match(table, /`business_owner` VARCHAR\(64\) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL/iu);
	assert.match(
		table,
		/UNIQUE KEY `uk_wf_process_business_round` \(`business_owner`,`business_table`,`business_id`,`business_round`\)/u
	);
	assert.doesNotMatch(table, /uk_wf_process_business_round[^\n]*tenant_id/u);
});

test('existing workflow occurrences are upgraded without inventing unknown owners', () => {
	assert.match(migration, /column_name = 'business_owner'/iu);
	assert.match(migration, /ADD COLUMN business_owner VARCHAR\(64\)[\s\S]*?COLLATE ascii_bin/iu);
	assert.match(migration, /Unsupported workflow business associations require manual owner reconciliation/iu);
	assert.match(
		migration,
		/UPDATE wf_process_instance[\s\S]*?SET business_owner = 'upms'[\s\S]*?process_key = 'demo_leave_approval'/iu
	);
	assert.match(
		migration,
		/COALESCE\(business_owner, 'upms'\) AS resolved_owner[\s\S]*?GROUP BY resolved_owner, business_table, business_id, business_round/iu
	);
	assert.match(
		migration,
		/business_owner,business_table,business_id,business_round/iu
	);
	assert.match(migration, /DROP INDEX uk_wf_process_business_round/iu);
	assert.match(
		migration,
		/ADD UNIQUE KEY uk_wf_process_business_round\s*\(business_owner, business_table, business_id, business_round\)/iu
	);
});
