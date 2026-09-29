import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const script = await readFile(new URL('./test-workflow-process-restart.sh', import.meta.url), 'utf8');
const evidence = await readFile(new URL('../.docs/workflow/EVIDENCE-2G.md', import.meta.url), 'utf8');
const makefile = await readFile(new URL('../Makefile', import.meta.url), 'utf8');
const runtimeVerifier = await readFile(new URL('./verify-runtime-config.sh', import.meta.url), 'utf8');

test('process restart script fails fast when Docker is unavailable', () => {
	assert.match(script, /command -v docker/);
	assert.match(script, /command -v python3/);
	assert.match(script, /\["docker", "version", "--format"/);
	assert.match(script, /timeout=timeout_seconds/);
	assert.match(script, /Environment blocked: Docker daemon did not answer/);
	assert.match(script, /exit 2/);
});

test('invalid Docker host exits before creating a disposable container', () => {
	const result = spawnSync('bash', ['./scripts/test-workflow-process-restart.sh'], {
		cwd: new URL('..', import.meta.url),
		env: {
			...process.env,
			DOCKER_HOST: `unix:///tmp/bixi-process-restart-static-${process.pid}-${Date.now()}.sock`,
			WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS: '1',
		},
		encoding: 'utf8',
		timeout: 5_000,
	});
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
	assert.match(`${result.stdout}\n${result.stderr}`, /Environment blocked/);
});

test('process restart script bounds the preflight and records an observed recovery interval', () => {
	assert.match(script, /WORKFLOW_RESTART_DOCKER_PREFLIGHT_SECONDS/);
	assert.match(script, /WORKFLOW_RESTART_DOCKER_COMMAND_SECONDS/);
	assert.match(script, /Docker command timed out before completion/);
	assert.match(script, /restart_docker run/);
	assert.match(script, /restart_recovery_started_ms=/);
	assert.match(script, /restart_recovery_finished_ms=/);
	assert.match(script, /observed elapsed_ms=/);
	assert.match(script, /not an SLO/);
});

test('cleanup is limited to the temporary database created by this invocation', () => {
	assert.match(script, /bixi-workflow-process-restart-mysql-\$\$/);
	assert.match(script, /bixi-workflow-process-restart-mysql-\$\$-\$\(openssl rand -hex 6\)/);
	assert.match(script, /restart_mysql_id=""/);
	assert.match(script, /docker rm --force --volumes "\$restart_mysql_id"/);
	assert.doesNotMatch(script, /docker compose\s+(down|rm)/);
	assert.doesNotMatch(script, /docker rm[^\n]*(--all|\$\(docker ps)/);
});

test('evidence names every remaining 2G runtime dimension and keeps partial status explicit', () => {
	for (const dimension of [
		'完整 UPMS/Workflow 应用容器重启',
		'发起成功后的响应丢失再重试',
		'恢复时延',
		'业务结果在 Inbox 提交前丢失',
		'业务结果在提交后、ACK 前丢失',
		'结果重复、乱序、旧轮次、未知版本、过期',
		'四组 single/cloud enabled/disabled 重启矩阵',
	]) assert.match(evidence, new RegExp(dimension));
	for (const status of ['`[x]`', '`[~]`', '`[ ]`']) assert.match(evidence, new RegExp(status.replace(/[\[\]`]/g, '\\$&')));
	assert.match(evidence, /2026-09-26 fresh/);
	assert.match(evidence, /脚本墙钟观测为 `\d+ ms`/);
	assert.match(evidence, /不能当作 SLO/);
	assert.match(evidence, /Docker daemon 不可用/);
});

test('static gate runs this audit before the runtime process-restart test', () => {
	assert.match(makefile, /workflow-process-restart-static-test:/);
	assert.match(makefile, /node --test scripts\/test-workflow-process-restart\.test\.mjs/);
	assert.match(makefile, /workflow-process-restart-test: workflow-process-restart-static-test/);
	assert.match(runtimeVerifier, /scripts\/test-workflow-process-restart\.test\.mjs/);
});
