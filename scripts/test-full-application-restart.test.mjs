import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scriptPath = resolve(root, 'scripts/test-full-application-restart.sh');
const composePath = resolve(root, 'scripts/full-application-restart.compose.yaml');
const script = readFileSync(scriptPath, 'utf8');
const compose = readFileSync(composePath, 'utf8');

test('full restart harness passes shell syntax and keeps the run isolated', () => {
	const syntax = spawnSync('bash', ['-n', scriptPath], { encoding: 'utf8' });
	assert.equal(syntax.status, 0, syntax.stderr || syntax.stdout);
	assert.match(script, /PROJECT="bixi-full-restart-\$\{RUN_ID\}"/);
	assert.match(script, /OUTPUT_ROOT="\$\{FULL_RESTART_OUTPUT_DIR:-/);
	assert.match(script, /FULL_RESTART_SOURCE_ENV/);
	assert.match(script, /REQUESTED_MODES="\$\{FULL_RESTART_MODES:-single\}"/);
	assert.match(script, /cloud plus single rehearsal is intentionally opt-in/);
	assert.match(script, /FULL_RESTART_MODES=cloud,single/);
	assert.match(script, /--project-name "\$\{PROJECT\}"/);
	assert.match(script, /--no-deps/);
	assert.match(script, /wait_stack_ready/);
	assert.match(script, /write_evidence/);
	assert.match(script, /compose --profile cloud --profile workflow --profile single down --volumes --remove-orphans/);
	assert.match(script, /reset_project_between_modes/);
	assert.match(script, /cleanupBetweenModes/);
	assert.match(script, /evidenceEligibleForPersonalComputer/);
});

test('full restart harness uses explicit staged gates for both modes', () => {
		for (const service of ['mysql', 'redis', 'rabbitmq', 'nacos', 'rabbitmq-config', 'nacos-config',
			'monitor', 'gateway', 'upms', 'generator', 'quartz', 'workflow', 'auth', 'frontend-cloud', 'single', 'frontend-single']) {
		assert.match(script, new RegExp(`\\b${service}\\b`), `missing service ${service}`);
	}
	assert.match(script, /WORKFLOW_PUBLIC_START_MODELS stage2_form_acceptance/);
	assert.match(script, /BIXI_RELIABLE_RABBIT_ENABLED true/);
	assert.match(script, /BIXI_RELIABLE_RABBIT_ENABLED false/);
	assert.match(script, /baseline_status/);
	assert.match(script, /post_status/);
	assert.match(script, /recovery_ms=/);
	assert.match(script, /PREFLIGHT_BYPASS=true variable=BIXI_LOCAL_PREFLIGHT_SKIP/);
	assert.match(script, /run_local_test_preflight "\$\{next_mode\}"/);
});

test('compose override is image-only and does not introduce a second business implementation', () => {
	for (const variable of [
		'FULL_RESTART_CLOUD_MONITOR_IMAGE',
		'FULL_RESTART_CLOUD_GATEWAY_IMAGE',
		'FULL_RESTART_CLOUD_AUTH_IMAGE',
		'FULL_RESTART_CLOUD_UPMS_IMAGE',
			'FULL_RESTART_CLOUD_GENERATOR_IMAGE',
			'FULL_RESTART_CLOUD_QUARTZ_IMAGE',
			'FULL_RESTART_CLOUD_WORKFLOW_IMAGE',
		'FULL_RESTART_SINGLE_IMAGE',
		'FULL_RESTART_SINGLE_FRONTEND_IMAGE',
	]) {
		assert.match(compose, new RegExp(variable));
	}
	assert.doesNotMatch(compose, /build:/);
	assert.match(compose, /# Image-only overrides/);
});
