import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

const scriptUrl = new URL('./migrate-workflow-schema.sh', import.meta.url);

function runList(scope) {
	return new Promise((resolve, reject) => {
		const child = spawn('bash', [scriptUrl.pathname, '--list', '--scope', scope], {
			stdio: ['ignore', 'pipe', 'pipe'],
		});
		let stdout = '';
		let stderr = '';
		child.stdout.on('data', (chunk) => { stdout += chunk; });
		child.stderr.on('data', (chunk) => { stderr += chunk; });
		child.once('error', reject);
		child.once('close', (code) => resolve({ code, stdout, stderr }));
	});
}

async function runPhase2(envContents, dockerSource) {
	const directory = await mkdtemp(join(tmpdir(), 'bixi-phase2-migration-'));
	const envPath = join(directory, '.env');
	const dockerPath = join(directory, 'docker');
	const dockerLogPath = join(directory, 'docker.log');
	await writeFile(envPath, envContents);
	await writeFile(dockerPath, dockerSource, { mode: 0o755 });

	try {
		const result = await new Promise((resolve, reject) => {
			const child = spawn('bash', [scriptUrl.pathname, '--scope', 'phase2'], {
				env: {
					...process.env,
					BIXI_ENV_FILE: envPath,
					FAKE_DOCKER_LOG: dockerLogPath,
					PATH: `${directory}:${process.env.PATH || ''}`,
				},
				stdio: ['ignore', 'pipe', 'pipe'],
			});
			let stdout = '';
			let stderr = '';
			child.stdout.on('data', (chunk) => { stdout += chunk; });
			child.stderr.on('data', (chunk) => { stderr += chunk; });
			child.once('error', reject);
			child.once('close', (code) => resolve({ code, stdout, stderr }));
		});
		const dockerCalls = await readFile(dockerLogPath, 'utf8').catch(() => '');
		return { ...result, dockerCalls };
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
}

test('phase2 migration preview includes every ordered additive migration', async () => {
	const result = await runList('phase2');
	assert.equal(result.code, 0, result.stderr);
	const files = result.stdout.trim().split(/\r?\n/).filter(Boolean);
	assert.ok(files.length >= 30, `expected the full Phase 2 migration set, got ${files.length}`);
	assert.ok(files.some((file) => file.endsWith('20260924_generator_template_group_uniqueness.sql')));
	assert.ok(files.some((file) => file.endsWith('20260926_ai_rag_ingestion.sql')));
	assert.ok(files.some((file) => file.endsWith('20260926_notice_delivery.sql')));
	assert.deepEqual(files, [...files].sort(), 'migration preview must be lexical and deterministic');
});

test('workflow scope remains backward compatible and does not silently select unrelated modules', async () => {
	const result = await runList('workflow');
	assert.equal(result.code, 0, result.stderr);
	const files = result.stdout.trim().split(/\r?\n/).filter(Boolean);
	assert.ok(files.some((file) => file.endsWith('20260922_flowable_engine_7_1_0.sql')));
	assert.ok(files.some((file) => file.endsWith('20260924_workflow_business_occurrence.sql')));
	assert.equal(files.some((file) => file.endsWith('20260926_notice_delivery.sql')), false);
});

test('migration runner keeps maintenance guards and ledger semantics', async () => {
	const source = await readFile(scriptUrl, 'utf8');
	assert.match(source, /WORKFLOW_MAINTENANCE/);
	assert.match(source, /WORKFLOW_MIGRATION_CONFIRM/);
	assert.match(source, /bixi_schema_migration/);
	assert.match(source, /BIXI_MIGRATION_SCOPE/);
	assert.match(source, /phase2/);
});

test('phase2 scope rejects workflow-only maintenance confirmation', async () => {
	const maintenanceResult = await runPhase2([
		'WORKFLOW_SCHEMA_UPDATE=false',
		'WORKFLOW_MAINTENANCE=true',
		'WORKFLOW_MIGRATION_CONFIRM=APPLY_WORKFLOW_MIGRATIONS',
	].join('\n'), '#!/usr/bin/env bash\nexit 0\n');

	assert.equal(maintenanceResult.code, 1);
	assert.match(maintenanceResult.stderr, /BIXI_SCHEMA_MAINTENANCE=true/);
	assert.equal(maintenanceResult.dockerCalls, '', 'guard failure must happen before Compose is inspected');

	const confirmationResult = await runPhase2([
		'WORKFLOW_SCHEMA_UPDATE=false',
		'BIXI_SCHEMA_MAINTENANCE=true',
		'WORKFLOW_MIGRATION_CONFIRM=APPLY_WORKFLOW_MIGRATIONS',
	].join('\n'), '#!/usr/bin/env bash\nexit 0\n');
	assert.equal(confirmationResult.code, 1);
	assert.match(confirmationResult.stderr, /BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS/);
	assert.equal(confirmationResult.dockerCalls, '', 'guard failure must happen before Compose is inspected');
});

test('phase2 scope rejects every related running application including workflow replicas', async () => {
	const dockerSource = `#!/usr/bin/env bash
printf '%s\\n' "$*" >> "$FAKE_DOCKER_LOG"
if [[ " $* " == *" ps -q gateway "* ]]; then
	printf 'running-application\\n'
fi
`;
	const result = await runPhase2([
		'WORKFLOW_SCHEMA_UPDATE=false',
		'BIXI_SCHEMA_MAINTENANCE=true',
		'BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS',
		'WORKFLOW_CLUSTER_ENABLED=false',
	].join('\n'), dockerSource);

	assert.equal(result.code, 1);
	assert.match(result.stderr, /Stop gateway\/auth\/upms\/generator\/quartz\/ai\/workflow\/workflow-a\/workflow-b\/single/);
	const processCheck = result.dockerCalls.split(/\r?\n/).find((call) => call.includes(' ps -q gateway '));
	assert.ok(processCheck, `missing related application process check:\n${result.dockerCalls}`);
	assert.match(processCheck, /-f compose\.workflow-cluster\.yaml/);
	for (const service of ['gateway', 'auth', 'upms', 'generator', 'quartz', 'ai', 'workflow', 'workflow-a', 'workflow-b', 'single']) {
		assert.match(` ${processCheck} `, new RegExp(` ${service} `));
	}
});
