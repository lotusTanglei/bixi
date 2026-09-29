import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const projectRoot = resolve(import.meta.dirname, '..');
const helper = join(projectRoot, 'scripts/ci-artifacts.sh');
const commit = '0123456789abcdef0123456789abcdef01234567';

function run(root, ...args) {
	return spawnSync('sh', [helper, ...args], {
		cwd: root,
		encoding: 'utf8',
		env: {
			...process.env,
			BIXI_PROJECT_VERSION: '3.2.0-test',
			CI_COMMIT_SHA: commit,
			CI_PROJECT_DIR: root,
		},
	});
}

function job(source, name) {
	const start = source.indexOf(`${name}:\n`);
	assert.notEqual(start, -1, `missing GitLab job ${name}`);
	const rest = source.slice(start + name.length + 2);
	const next = rest.search(/^\S[^\n]*:\s*$/m);
	return next < 0 ? rest : rest.slice(0, next);
}

test('artifact helper verifies exact build output and fails closed', () => {
	const root = mkdtempSync(join(tmpdir(), 'bixi-ci-artifacts-'));
	try {
		const jar = join(root, 'module-a/target/module-a.jar');
		const nestedJar = join(root, 'module-a/target/lib/dependency.jar');
		mkdirSync(dirname(jar), { recursive: true });
		mkdirSync(dirname(nestedJar), { recursive: true });
		writeFileSync(jar, 'verified build');
		writeFileSync(nestedJar, 'dependency copy');

		const created = run(root, 'create', 'cloud');
		assert.equal(created.status, 0, created.stderr || created.error?.message);
		const manifest = readFileSync(join(root, 'target/ci-artifacts/cloud/manifest.env'), 'utf8');
		assert.equal(manifest, `commit=${commit}\nproject_version=3.2.0-test\nmode=cloud\nartifact_count=1\n`);
		const checksums = readFileSync(join(root, 'target/ci-artifacts/cloud/SHA256SUMS'), 'utf8');
			assert.match(checksums, /module-a\/target\/module-a\.jar/);
			assert.doesNotMatch(checksums, /dependency\.jar/);
			assert.equal(run(root, 'verify', 'cloud').status, 0);

			const unverifiedJar = join(root, 'module-b/target/module-b.jar');
			mkdirSync(dirname(unverifiedJar), { recursive: true });
			writeFileSync(unverifiedJar, 'unverified build');
			assert.notEqual(run(root, 'verify', 'cloud').status, 0, 'unlisted artifacts must be rejected');
			unlinkSync(unverifiedJar);

			writeFileSync(jar, 'tampered build');
		assert.notEqual(run(root, 'verify', 'cloud').status, 0, 'tampered artifacts must be rejected');
		writeFileSync(jar, 'verified build');
		unlinkSync(jar);
		assert.notEqual(run(root, 'verify', 'cloud').status, 0, 'missing artifacts must be rejected');
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('artifact helper rejects builds with no distributable jar', () => {
	const root = mkdtempSync(join(tmpdir(), 'bixi-ci-artifacts-empty-'));
	try {
		assert.notEqual(run(root, 'create', 'cloud').status, 0);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('single mode records only the verified single-process jar', () => {
	const root = mkdtempSync(join(tmpdir(), 'bixi-ci-artifacts-single-'));
	try {
		const singleJar = join(root, 'bixi-single/target/bixi-single.jar');
		const unrelatedJar = join(root, 'bixi-auth/target/bixi-auth.jar');
		mkdirSync(dirname(singleJar), { recursive: true });
		mkdirSync(dirname(unrelatedJar), { recursive: true });
		writeFileSync(singleJar, 'single build');
		writeFileSync(unrelatedJar, 'unrelated build');

		const created = run(root, 'create', 'single');
		assert.equal(created.status, 0, created.stderr);
		const checksums = readFileSync(join(root, 'target/ci-artifacts/single/SHA256SUMS'), 'utf8');
		assert.match(checksums, /bixi-single\/target\/bixi-single\.jar/);
		assert.doesNotMatch(checksums, /bixi-auth\.jar/);
		assert.equal(run(root, 'verify', 'single').status, 0);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

test('GitLab promotes the artifacts created by the verified backend jobs', () => {
	const source = readFileSync(join(projectRoot, '.gitlab-ci.yml'), 'utf8');
	for (const [mode, testJob, packageJob] of [
		['cloud', 'test_backend_cloud', 'package_backend_cloud'],
		['single', 'test_backend_single', 'package_backend_single'],
	]) {
		const build = job(source, testJob);
		assert.match(build, new RegExp(`scripts/ci-artifacts\\.sh create ${mode}`));
		assert.match(build, new RegExp(`target/ci-artifacts/${mode}/`));

		const promotion = job(source, packageJob);
		assert.doesNotMatch(promotion, /\bmvn\b|make backend-/);
		assert.match(promotion, new RegExp(`job: ${testJob}`));
		assert.match(promotion, /job: accept_dual_mode/);
		assert.match(promotion, new RegExp(`scripts/ci-artifacts\\.sh verify ${mode}`));
		assert.match(promotion, new RegExp(`target/ci-artifacts/${mode}/`));
	}
});
