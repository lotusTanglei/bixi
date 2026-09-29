import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const script = await readFile(new URL('./test-local-process-restart.sh', import.meta.url), 'utf8');
const source = await readFile(new URL('../bixi-common/bixi-common-mq/src/test/java/com/lotus/bixi/common/mq/reliable/LocalProcessRestartIntegrationTest.java', import.meta.url), 'utf8');

test('local process restart script uses bounded disposable resources and both JVM phases', () => {
	assert.match(script, /LOCAL_PROCESS_RESTART_DOCKER_PREFLIGHT_SECONDS/);
	assert.match(script, /LOCAL_PROCESS_RESTART_DOCKER_COMMAND_SECONDS/);
	assert.match(script, /Docker command timed out before completion/);
	assert.match(script, /bixi-local-process-restart-mysql-/);
	assert.match(script, /RELIABLE_LOCAL_PROCESS_PHASE=hold/);
	assert.match(script, /RELIABLE_LOCAL_PROCESS_PHASE=resume/);
	assert.match(script, /kill -KILL/);
	assert.match(script, /not an SLO/);
});

test('local restart integration test proves outbox and inbox recovery in one local transport path', () => {
	assert.match(source, /LocalDurableTransport/);
	assert.match(source, /localTransportRollsBackHandlerAndLeavesLeasesRecoverableWhenOwnerProcessDies/);
	assert.match(source, /newLocalOwnerReclaimsBothLeasesAndCommitsBusinessExactlyOnce/);
	assert.match(source, /status.*PROCESSED/s);
	assert.match(source, /status.*DELIVERED/s);
});

test('invalid Docker host exits before creating local restart resources', () => {
	const result = spawnSync('bash', ['./scripts/test-local-process-restart.sh'], {
		cwd: new URL('..', import.meta.url),
		env: {
			...process.env,
			DOCKER_HOST: `unix:///tmp/bixi-local-process-restart-static-${process.pid}-${Date.now()}.sock`,
			LOCAL_PROCESS_RESTART_DOCKER_PREFLIGHT_SECONDS: '1',
		},
		encoding: 'utf8',
		timeout: 5_000,
	});
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
	assert.match(`${result.stdout}\n${result.stderr}`, /Environment blocked/);
});
