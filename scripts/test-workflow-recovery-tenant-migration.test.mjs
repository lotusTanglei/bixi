import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const migration = readFileSync(new URL(
	'../bixi-project-documents/sql/migrations/20260929_workflow_recovery_tenant_scope.sql',
	import.meta.url), 'utf8');

test('recovery audit tenant migration preserves historical replay identity', () => {
	assert.match(migration, /reliable_outbox/);
	assert.match(migration, /reliable_inbox/);
	assert.match(migration, /wf_command/);
	assert.match(migration, /wf_business_task/);
	assert.match(migration, /sys_user/);
	assert.match(migration, /legacy-unresolved/);
	assert.match(migration,
		/SELECT a\.id, CAST\(u\.tenant_id AS CHAR\)[\s\S]*?JOIN sys_user u ON u\.id = a\.actor_id[\s\S]*?AND a\.request_id IS NULL/);
	assert.doesNotMatch(migration, /SET tenant_scope = '1'\s+WHERE tenant_scope IS NULL/);
	assert.match(migration,
		/UNIQUE KEY uk_wf_recovery_request \(tenant_scope, owner, action, request_id\)/);
});

test('conflicting historical tenant evidence fails closed', () => {
	assert.match(migration, /CREATE TEMPORARY TABLE bixi_recovery_tenant_candidates_20260929/);
	assert.match(migration, /COUNT\(DISTINCT tenant_scope\) = 1/);
	assert.match(migration, /THEN MIN\(tenant_scope\)\s+ELSE 'legacy-unresolved'/);
	assert.match(migration,
		/SET a\.tenant_scope = COALESCE\(resolved\.tenant_scope, 'legacy-unresolved'\)/);
});
