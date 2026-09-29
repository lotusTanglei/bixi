import assert from 'node:assert/strict';
import { access, mkdtemp, readFile, writeFile, chmod, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scriptPath = resolve(root, 'scripts/local-test-preflight.sh');
const script = await readFile(scriptPath, 'utf8');

function runPreflight(args, env = {}) {
	const baseEnv = { ...process.env };
	for (const key of [
		'BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB',
		'BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB',
	'BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB',
		'BIXI_LOCAL_PREFLIGHT_DOCKER_TIMEOUT_SECONDS',
		'BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT',
	]) delete baseEnv[key];
	return spawnSync('bash', [scriptPath, ...args], {
		cwd: root,
		env: { ...baseEnv, ...env },
		encoding: 'utf8',
		timeout: 10_000,
	});
}

test('local preflight is read-only, bounded, and exposes the resource controls', () => {
	assert.match(script, /'docker', 'info', '--format'/);
	assert.match(script, /'docker', 'compose', 'version'/);
	assert.match(script, /'docker', 'ps', '--format'/);
	assert.match(script, /timeout=timeout/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_DOCKER_TIMEOUT_SECONDS/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT/);
	assert.match(script, /docker', 'system', 'df'/);
	assert.match(script, /WORKFLOW_CLUSTER_ENABLED/);
	assert.match(script, /single and cloud should not run concurrently/);
	assert.doesNotMatch(script, /docker[^\n]*(?:\s|')(?:up|down|run|rm|stop|kill)(?:\s|')/);
});

test('static mode checks host capacity without requiring Docker', () => {
	const result = runPreflight(['static'], { BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1' });
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
	assert.match(result.stdout, /static mode does not contact Docker/);
});

test('daemon failure is reported as an environment block with exit code 2', () => {
	const result = runPreflight(['single'], {
		DOCKER_HOST: `unix:///tmp/bixi-local-preflight-${process.pid}-${Date.now()}.sock`,
		BIXI_LOCAL_PREFLIGHT_DOCKER_TIMEOUT_SECONDS: '1',
		BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
	});
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
	assert.match(`${result.stdout}\n${result.stderr}`, /BLOCKED \(environment\).*Docker daemon/);
});

test('invalid resource overrides fail before probing the environment', () => {
	const result = runPreflight(['static'], {
		BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
		BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB: 'not-a-number',
	});
	assert.equal(result.error, undefined, result.error?.message);
	assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
	assert.match(result.stderr, /BIXI_LOCAL_PREFLIGHT_MIN_DOCKER_GIB/);
});

test('an insufficient disk threshold blocks before any Docker operation', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-disk-'));
	const marker = join(directory, 'docker-called');
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
touch "${marker}"
exit 1
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['single'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '999999',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 1, `${result.stdout}\n${result.stderr}`);
		assert.match(result.stderr, /BLOCKED: .*at least 999999 GiB/);
		assert.match(result.stderr, /skipping Docker probe/);
		let markerExists = true;
		try {
			await access(marker);
		} catch {
			markerExists = false;
		}
		assert.equal(markerExists, false);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test('makefile exposes separate local preflight and static contract targets', async () => {
	const makefile = await readFile(new URL('../Makefile', import.meta.url), 'utf8');
	assert.match(makefile, /local-test-preflight-static-test/);
	assert.match(makefile, /local-test-preflight:/);
	assert.match(makefile, /scripts\/local-test-preflight\.sh/);
});

test('Docker memory threshold can be evaluated with a fake read-only CLI', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-'));
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '4294967296\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf '' ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['cloud'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 1, `${result.stdout}\n${result.stderr}`);
		assert.match(result.stderr, /Docker VM memory is 4\.0 GiB/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test('Docker storage pressure is reported without blocking a compatible local mode', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-storage-'));
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '8589934592\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf '' ;;
  "system df") printf '%s\\n' '{"Type":"Local Volumes","Size":"47.99GB","Reclaimable":"45.1GB (93%)","Active":"41","TotalCount":"268"}' '{"Type":"Build Cache","Size":"11.21GB","Reclaimable":"8.109GB","Active":"0","TotalCount":"120"}' ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['single'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
			BIXI_LOCAL_PREFLIGHT_MAX_RECLAIMABLE_GIB: '20',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
		assert.match(`${result.stdout}\n${result.stderr}`, /Docker storage reclaimable .*45\.1/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test('unavailable Docker storage reporting is non-fatal and visible', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-storage-unavailable-'));
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '8589934592\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf '' ;;
  "system df") exit 1 ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['single'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
		assert.match(`${result.stdout}\n${result.stderr}`, /Docker storage report is unavailable/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test('mode conflict warnings are scoped to Bixi Compose projects', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-labels-'));
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '8589934592\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf 'other-project|gateway\\nbixi|single\\nbixi-check|gateway\\n' ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['single'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
		assert.match(result.stderr, /both single and cloud containers are running/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});

test('runtime harnesses can turn a conflicting Bixi mode into a hard local block', async () => {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-local-preflight-exclusive-'));
	try {
		const dockerPath = join(directory, 'docker');
		await writeFile(dockerPath, `#!/bin/sh
case "$1 $2" in
  "info --format") printf '8589934592\\n' ;;
  "compose version") printf 'Docker Compose version v5\\n' ;;
  "ps --format") printf 'bixi|single\\nbixi-cloud|gateway\\n' ;;
  *) exit 1 ;;
esac
`);
		await chmod(dockerPath, 0o755);
		const result = runPreflight(['single'], {
			PATH: `${directory}:${process.env.PATH}`,
			BIXI_LOCAL_PREFLIGHT_MIN_FREE_GIB: '1',
			BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT: 'true',
		});
		assert.equal(result.error, undefined, result.error?.message);
		assert.equal(result.status, 1, `${result.stdout}\n${result.stderr}`);
		assert.match(result.stderr, /BLOCKED: Bixi containers are already running/);
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
});
