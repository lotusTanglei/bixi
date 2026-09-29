#!/usr/bin/env node
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const rootSchema = readFileSync(new URL('../bixi-project-documents/sql/01_init_all_tables.sql', import.meta.url), 'utf8');
const jobSchema = readFileSync(new URL('../bixi-project-documents/sql/bixi_job.sql', import.meta.url), 'utf8');
const indexes = readFileSync(new URL('../bixi-project-documents/sql/03_add_indexes.sql', import.meta.url), 'utf8');
const migrationUrl = new URL('../bixi-project-documents/sql/migrations/20260926_quartz_retry_history.sql', import.meta.url);

const assertColumns = source => {
	assert.match(source, /`retry_count`\s+int\s+NOT NULL\s+DEFAULT\s+0/i);
	assert.match(source, /`retry_interval_seconds`\s+int\s+NOT NULL\s+DEFAULT\s+5/i);
	assert.match(source, /`execution_id`\s+varchar\(128\)/i);
	assert.match(source, /`attempt`\s+int\s+NOT NULL\s+DEFAULT\s+1/i);
	assert.match(source, /`max_attempts`\s+int\s+NOT NULL\s+DEFAULT\s+1/i);
	assert.match(source, /`trigger_type`\s+varchar\(16\)\s+NOT NULL\s+DEFAULT\s+'LEGACY'/i);
	assert.match(source, /`recovered`\s+tinyint\(1\)\s+NOT NULL\s+DEFAULT\s+0/i);
};

test('clean database schemas persist retry and recovery history', () => {
	assertColumns(rootSchema);
	assertColumns(jobSchema);
	assert.match(indexes, /idx_job_record_job_created[^\n]+sys_job_record/i);
	assert.match(indexes, /idx_job_record_execution[^\n]+sys_job_record/i);
});

test('existing databases have an additive guarded migration', () => {
	assert.ok(existsSync(migrationUrl), 'Quartz retry history migration is missing');
	const migration = readFileSync(migrationUrl, 'utf8');
	for (const column of ['retry_count', 'retry_interval_seconds', 'execution_id', 'attempt', 'max_attempts', 'trigger_type', 'recovered']) {
		assert.match(migration, new RegExp(`column_name = '${column}'`, 'i'), `${column} guard is missing`);
	}
	assert.match(migration, /idx_job_record_job_created/i);
	assert.match(migration, /idx_job_record_execution/i);
	assert.match(migration, /SIGNAL SQLSTATE '45000'/i);
});
