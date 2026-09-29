import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import test from 'node:test';

const scriptUrl = new URL('./test-generator-migration.py', import.meta.url);

test('generator migration runtime harness exists and targets the fixed-source migration', async () => {
	await access(scriptUrl);
	const source = await readFile(scriptUrl, 'utf8');
	assert.match(source, /20260924_generator_template_group_uniqueness\.sql/);
	assert.match(source, /active_group_name/);
	assert.match(source, /uk_gen_group_active_name/);
});

test('generator migration harness bounds Docker calls and cleans only its disposable container', async () => {
	const source = await readFile(scriptUrl, 'utf8');
	assert.match(source, /DOCKER_TIMEOUT_SECONDS\s*=\s*30/);
	assert.match(source, /timeout=DOCKER_TIMEOUT_SECONDS/);
	assert.match(source, /EnvironmentBlocked/);
	assert.match(source, /cleanup_container/);
	assert.match(source, /--name", CONTAINER/);
});
