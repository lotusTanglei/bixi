import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const readScript = (name) => readFile(new URL(`./${name}`, import.meta.url), 'utf8');

function assertPreflightContract(script, { label, runMarker, mode = 'single' }) {
	assert.match(script, /run_local_test_preflight\(\)/, `${label} must define a shared preflight wrapper`);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_SKIP:-false/, `${label} must default to the resource gate`);
	const invocation = script.indexOf(`run_local_test_preflight ${mode}`);
	assert.notEqual(invocation, -1, `${label} must invoke the shared preflight for ${mode}`);
	const resource = script.indexOf(runMarker);
	assert.notEqual(resource, -1, `${label} resource creation marker is missing`);
	assert.ok(invocation < resource, `${label} must preflight before ${runMarker}`);
}

test('disposable MySQL and Rabbit entrypoints gate resources before the first run', async () => {
	const scripts = await Promise.all([
		readScript('test-reliable-rabbit.sh'),
		readScript('test-workflow-process-restart.sh'),
		readScript('test-local-process-restart.sh'),
		readScript('test-reliable-mysql.sh'),
		readScript('test-workflow-mysql.sh'),
	]);
	const [rabbit, workflowRestart, localRestart, reliableMysql, workflowMysql] = scripts;
	assertPreflightContract(rabbit, {
		label: 'reliable Rabbit',
		runMarker: 'rabbit_test_mysql_id="$(rabbit_docker run',
	});
	assertPreflightContract(workflowRestart, {
		label: 'Workflow process restart',
		runMarker: 'restart_mysql_id="$(restart_docker run',
	});
	assertPreflightContract(localRestart, {
		label: 'local process restart',
		runMarker: 'local_restart_mysql_id="$(local_restart_docker run',
	});
	assertPreflightContract(reliableMysql, {
		label: 'reliable MySQL',
		runMarker: 'reliable_test_container_id="$(docker run',
	});
	assertPreflightContract(workflowMysql, {
		label: 'Workflow MySQL',
		runMarker: 'workflow_test_container_id="$(docker run',
	});
});

test('full restart gates every requested mode before creating its run directory', async () => {
	const script = await readScript('test-full-application-restart.sh');
	assert.match(script, /run_local_test_preflight\(\)/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_SKIP:-false/);
	const invocation = script.indexOf('run_local_test_preflight "${mode}"');
	assert.notEqual(invocation, -1, 'full restart must preflight each requested mode');
	assert.ok(invocation < script.indexOf('mkdir -p "${RUN_DIR}"'),
		'full restart must preflight before creating its report directory');
	assert.ok(invocation < script.indexOf('compose_mode "${mode}" up'),
		'full restart must preflight before creating Compose resources');
});

test('Quartz gates only its disposable database path and leaves external JDBC unblocked', async () => {
	const script = await readScript('test-quartz-jdbc-failover.sh');
	assert.match(script, /run_local_test_preflight\(\)/);
	assert.match(script, /BIXI_LOCAL_PREFLIGHT_SKIP:-false/);
	const externalBranch = script.indexOf('if [[ -z "${external_jdbc_url}" ]]');
	const preflight = script.indexOf('run_local_test_preflight single');
	const dockerRun = script.indexOf('quartz_docker "${docker_timeout}" run');
	assert.notEqual(externalBranch, -1, 'Quartz external JDBC branch is missing');
	assert.notEqual(preflight, -1, 'Quartz disposable path must invoke the shared preflight');
	assert.ok(externalBranch < preflight, 'Quartz must decide external versus disposable before preflight');
	assert.ok(preflight < dockerRun, 'Quartz must preflight before creating disposable MySQL');
	const externalBody = script.slice(externalBranch, preflight);
	assert.doesNotMatch(externalBody, /local-test-preflight\.sh/,
		'external JDBC path must not invoke local Docker resource checks');
});

test('direct Python Docker fixtures expose the same explicit local resource gate', async () => {
	for (const name of [
		'test-auth-proxy.py',
		'test-generator-migration.py',
		'test-quartz-migration.py',
		'test-security-menu-migration.py',
		'test-workflow-command-migration.py',
		'test-workflow-menu-migration.py',
	]) {
		const script = await readScript(name);
		assert.match(script, /from local_test_preflight import ensure_local_test_preflight/,
			`${name} must use the shared Python preflight wrapper`);
		const preflight = script.indexOf('ensure_local_test_preflight("single")');
		assert.notEqual(preflight, -1, `${name} must invoke the shared preflight`);
		const dockerRun = Math.min(...['docker("run"', "['docker', 'run'"].map(marker => {
			const index = script.indexOf(marker);
			return index === -1 ? Number.MAX_SAFE_INTEGER : index;
		}));
		assert.ok(preflight < dockerRun, `${name} must gate before its first Docker run`);
	}
	const helper = await readScript('local_test_preflight.py');
	assert.match(helper, /BIXI_LOCAL_PREFLIGHT_SKIP/);
	assert.match(helper, /local-test-preflight\.sh/);
});
