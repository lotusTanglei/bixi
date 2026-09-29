import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import test from 'node:test';

const scriptUrl = new URL('./test-quartz-migration.py', import.meta.url);

test('Quartz migration runtime harness exists and covers guarded retry schema', async () => {
	await access(scriptUrl);
	const source = await readFile(scriptUrl, 'utf8');
	assert.match(source, /20260926_quartz_retry_history\.sql/);
	assert.match(source, /Conflicting sys_job\.retry_count definition/);
	assert.match(source, /Conflicting idx_job_record_execution definition/);
	assert.match(source, /schema_snapshot/);
});

test('Quartz migration harness bounds Docker calls and cleans only its disposable container', async () => {
	const source = await readFile(scriptUrl, 'utf8');
	assert.match(source, /DOCKER_TIMEOUT_SECONDS\s*=\s*30/);
	assert.match(source, /timeout=DOCKER_TIMEOUT_SECONDS/);
	assert.match(source, /EnvironmentBlocked/);
	assert.match(source, /cleanup_container/);
	assert.match(source, /--name", CONTAINER/);
});
