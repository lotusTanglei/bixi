import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const script = await readFile(new URL('./test-reliable-rabbit.sh', import.meta.url), 'utf8');
const rabbitBootstrap = await readFile(new URL('../deploy/rabbitmq/configure-access.sh', import.meta.url), 'utf8');

test('reliable Rabbit script fails fast when Docker is unavailable', () => {
	assert.match(script, /command -v docker/);
	assert.match(script, /command -v python3/);
	assert.match(script, /\["docker", "version", "--format"/);
	assert.match(script, /timeout=timeout_seconds/);
	assert.match(script, /Environment blocked: Docker daemon did not answer/);
	assert.match(script, /exit 2/);
});

test('invalid Docker host exits before creating disposable containers', () => {
	const result = spawnSync('bash', ['./scripts/test-reliable-rabbit.sh'], {
		cwd: new URL('..', import.meta.url),
		env: {
			...process.env,
			DOCKER_HOST: `unix:///tmp/bixi-reliable-rabbit-static-${process.pid}-${Date.now()}.sock`,
			RELIABLE_RABBIT_DOCKER_PREFLIGHT_SECONDS: '1',
		},
		encoding: 'utf8',
		timeout: 5_000,
	});
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
	assert.match(`${result.stdout}\n${result.stderr}`, /Environment blocked/);
});

test('Docker command timeout and cleanup scope are explicit', () => {
	assert.match(script, /RELIABLE_RABBIT_DOCKER_PREFLIGHT_SECONDS/);
	assert.match(script, /RELIABLE_RABBIT_DOCKER_COMMAND_SECONDS/);
	assert.match(script, /Docker command timed out before completion/);
	assert.match(script, /rabbit_docker run/);
	assert.match(script, /rabbit_docker restart/);
	assert.match(script, /rabbit_docker exec/);
	assert.match(script, /rabbit_docker rm --force --volumes "\$rabbit_test_broker_id"/);
	assert.match(script, /rabbit_docker rm --force --volumes "\$rabbit_test_broker_container"/);
	assert.match(script, /rabbit_docker rm --force --volumes "\$rabbit_test_mysql_id"/);
	assert.match(script, /rabbit_docker rm --force --volumes "\$rabbit_test_mysql_container"/);
	assert.doesNotMatch(script, /docker compose\s+(down|rm)/);
	assert.doesNotMatch(script, /docker rm[^\n]*(--all|\$\(docker ps)/);
});

test('bounded wrapper is used for every runtime Docker operation', () => {
	const runtimeLines = script
		.split('\n')
		.map(line => line.trim())
		.filter(line => !line.startsWith('#'))
		.filter(line => /\bdocker\s+(run|exec|port|restart|rm)\b/.test(line));
	assert.equal(runtimeLines.length, 0, runtimeLines.join('\n'));
});

test('Rabbit bootstrap does not demote an admin reused as the app user', () => {
	assert.match(rabbitBootstrap, /RABBITMQ_APP_USERNAME.*RABBITMQ_ADMIN_USERNAME/);
	assert.match(rabbitBootstrap, /preserv|skip|shared.*user|same.*user/i);
});
