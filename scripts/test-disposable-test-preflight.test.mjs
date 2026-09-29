import assert from 'node:assert/strict';
import { chmod, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const root = resolve(new URL('..', import.meta.url).pathname);

const harnesses = [
	['scripts/test-reliable-rabbit.sh', 'single', /rabbit_docker run/],
	['scripts/test-workflow-process-restart.sh', 'single', /restart_docker run/],
	['scripts/test-local-process-restart.sh', 'single', /local_restart_docker run/],
	['scripts/test-reliable-mysql.sh', 'single', /docker run --detach/],
	['scripts/test-workflow-mysql.sh', 'single', /docker run --detach/],
];

test('disposable local harnesses gate resources before their first container', async () => {
	for (const [relativePath, mode, resourcePattern] of harnesses) {
		const source = await readFile(resolve(root, relativePath), 'utf8');
		const preflightMatch = source.match(new RegExp(`local-test-preflight\\.sh"?\\s+${mode}`));
		const preflight = preflightMatch?.index ?? -1;
		assert.notEqual(preflight, -1, `${relativePath} must invoke the shared ${mode} preflight`);
		assert.match(source, /BIXI_LOCAL_PREFLIGHT_SKIP/,
			`${relativePath} needs an explicit, visible preflight bypass`);
		const resource = source.search(resourcePattern);
		assert.notEqual(resource, -1, `${relativePath} resource operation disappeared`);
		assert.ok(preflight < resource,
			`${relativePath} must preflight before creating disposable resources`);
	}
});

test('full application restart gates each requested mode before creating its report directory', async () => {
	const source = await readFile(resolve(root, 'scripts/test-full-application-restart.sh'), 'utf8');
	assert.match(source, /scripts\/local-test-preflight\.sh/);
	assert.match(source, /BIXI_LOCAL_PREFLIGHT_SKIP/);
	assert.match(source, /for mode in "\$\{modes\[@\]\}"/);
	const preflight = source.indexOf('scripts/local-test-preflight.sh');
	const reportDirectory = source.indexOf('mkdir -p "${RUN_DIR}"');
	assert.ok(preflight !== -1 && reportDirectory !== -1 && preflight < reportDirectory,
		'full restart must gate resources before creating RUN_DIR');
});

test('Quartz JDBC harness gates only its disposable database branch', async () => {
	const source = await readFile(resolve(root, 'scripts/test-quartz-jdbc-failover.sh'), 'utf8');
	assert.match(source, /local-test-preflight\.sh"?\s+single/);
	assert.match(source, /BIXI_LOCAL_PREFLIGHT_SKIP/);
	const branch = source.indexOf('if [[ -z "${external_jdbc_url}" ]]');
	const preflight = source.indexOf('\trun_local_test_preflight', branch);
	const resource = source.indexOf('quartz_docker "${docker_timeout}" run --detach');
	assert.ok(branch !== -1 && preflight > branch && resource !== -1 && preflight < resource,
		'Quartz preflight must stay inside the disposable branch and precede docker run');
});

test('a low-memory local preflight stops a disposable harness before docker run', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-disposable-preflight-'));
	const marker = join(directory, 'docker-run-called');
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '1073741824\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf '' ;;
  "system df") printf '' ;;
  "run --detach") touch "${marker}"; exit 99 ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = spawnSync('bash', ['./scripts/test-reliable-mysql.sh', 'single'], {
			cwd: root,
			env: {
				...process.env,
				PATH: `${directory}:${process.env.PATH}`,
				BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
			},
			encoding: 'utf8',
			timeout: 10_000,
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 1, `${result.stdout}\n${result.stderr}`);
		assert.match(`${result.stdout}\n${result.stderr}`, /Docker VM memory is 1\.0 GiB/);
		await assert.rejects(() => readFile(marker));
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});
