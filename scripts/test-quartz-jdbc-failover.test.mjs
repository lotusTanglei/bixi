import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const script = await readFile(new URL('./test-quartz-jdbc-failover.sh', import.meta.url), 'utf8');
const javaTest = await readFile(
	new URL('../bixi-module/bixi-quartz/src/test/java/com/lotus/bixi/quartz/QuartzJdbcFailoverIntegrationTest.java', import.meta.url),
	'utf8'
);
const makefile = await readFile(new URL('../Makefile', import.meta.url), 'utf8');
const runtimeVerifier = await readFile(new URL('./verify-runtime-config.sh', import.meta.url), 'utf8');

test('Quartz JDBC failover harness is opt-in and bounded', () => {
	assert.match(script, /BIXI_QUARTZ_HA_JDBC_URL/);
	assert.match(script, /QUARTZ_HA_DOCKER_PREFLIGHT_SECONDS/);
	assert.match(script, /timeout=timeout_seconds/);
	assert.match(script, /Environment blocked/);
	assert.match(script, /rm --force --volumes/);
	assert.match(script, /quartz-ha-schema/);
	assert.match(script, /mvn[\s\S]*QuartzJdbcFailoverIntegrationTest/);
});

test('Java harness proves kill-owner recovery and persisted failure history', () => {
	assert.match(javaTest, /Assumptions\.assumeTrue/);
	assert.match(javaTest, /JobStoreTX/);
	assert.match(javaTest, /isClustered/);
	assert.match(javaTest, /requestRecovery\(\)/);
	assert.match(javaTest, /destroyForcibly/);
	assert.match(javaTest, /isRecovering\(\)/);
	assert.match(javaTest, /DisallowConcurrentExecution/);
	assert.match(javaTest, /RECOVERY_START/);
	assert.match(javaTest, /FAILURE/);
	assert.match(javaTest, /QRTZ_SCHEDULER_STATE/);
});

test('static gate exposes the runtime harness without making normal CI depend on Docker', () => {
	assert.match(makefile, /quartz-jdbc-failover-static-test/);
	assert.match(makefile, /quartz-jdbc-failover-test: quartz-jdbc-failover-static-test/);
	assert.match(runtimeVerifier, /scripts\/test-quartz-jdbc-failover\.test\.mjs/);
	assert.match(runtimeVerifier, /scripts\/test-quartz-jdbc-failover\.sh/);
	assert.match(runtimeVerifier, /QuartzJdbcFailoverIntegrationTest/);
});

test('harness uses the project Quartz schema and does not claim application-level HA', async () => {
	const schema = await readFile(new URL('../scripts/quartz-ha-schema.sql', import.meta.url), 'utf8');
	for (const table of ['QRTZ_JOB_DETAILS', 'QRTZ_FIRED_TRIGGERS', 'QRTZ_SCHEDULER_STATE', 'QRTZ_TRIGGERS']) {
		assert.match(schema, new RegExp('CREATE TABLE\\s+`?' + table + '`?', 'i'));
	}
	assert.match(script, /application-level|Bixi HTTP|not.*application/i);
});
