import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const rootUrl = new URL('../', import.meta.url);

function readSource(path) {
	return readFile(new URL(path, rootUrl), 'utf8');
}

function composeService(source, name) {
	const lines = source.split(/\r?\n/);
	const start = lines.indexOf(`  ${name}:`);
	assert.notEqual(start, -1, `missing Compose service: ${name}`);
	const relativeEnd = lines.slice(start + 1).findIndex((line) => /^  [a-zA-Z0-9_-]+:$/.test(line));
	const end = relativeEnd === -1 ? lines.length : start + 1 + relativeEnd;
	return lines.slice(start, end).join('\n');
}

test('cloud OpenAPI paths match gateway routes and AI documentation follows AI_ENABLED', async () => {
	const [workflowApplication, workflowConfig, aiConfig] = await Promise.all([
		readSource('bixi-module/bixi-workflow-biz/src/main/java/com/lotus/bixi/workflow/WorkflowApplication.java'),
		readSource('deploy/nacos/bixi-workflow-biz-dev.yml'),
		readSource('deploy/nacos/bixi-ai-biz-dev.yml'),
	]);

	assert.match(workflowApplication, /@EnableBixiDoc\(value = "admin\/workflow"\)/);
	assert.match(workflowConfig, /springdoc:\s+api-docs:\s+path: \/workflow\/v3\/api-docs/s);
	assert.doesNotMatch(workflowConfig, /^\s*-\s+\/(?:v3\/api-docs|swagger-ui|doc\.html)/m);

	assert.match(aiConfig, /api-docs:\s+enabled: \$\{AI_ENABLED:false\}/s);
	assert.match(aiConfig, /api-docs:\s+enabled: \$\{AI_ENABLED:false\}\s+path: \/ai\/v3\/api-docs/s);
	assert.match(aiConfig, /swagger-ui:\s+enabled: \$\{AI_ENABLED:false\}/s);
	assert.doesNotMatch(aiConfig, /^\s*-\s+\/(?:v3\/api-docs|swagger-ui|doc\.html)/m);
});

test('SBA exposes protected log endpoints backed by the application logfile', async () => {
	const [envExample, startScript, composeConfig, loggerInitializer] = await Promise.all([
		readSource('.env.example'),
		readSource('scripts/bixi.sh'),
		readSource('compose.yaml'),
		readSource('bixi-common/bixi-common-log/src/main/java/com/lotus/bixi/common/log/init/ApplicationLoggerInitializer.java'),
	]);

	assert.match(envExample, /^BIXI_MANAGEMENT_ENDPOINTS=health,info,metrics,loggers,logfile,dynamictp$/m);
	assert.match(startScript, /append_env_if_missing BIXI_MANAGEMENT_ENDPOINTS health,info,metrics,loggers,logfile,dynamictp/);
	assert.match(loggerInitializer, /System\.setProperty\("logging\.file\.name",/);
	assert.match(loggerInitializer, /debug\.log/);
	assert.match(composeConfig, /MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: \$\{BIXI_MANAGEMENT_ENDPOINTS:-health,info,metrics,loggers,logfile,dynamictp\}/);

	for (const service of ['gateway', 'single']) {
		const block = composeService(composeConfig, service);
		assert.match(block, /MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health/);
	}
});

test('SBA status changes use built-in logging alerts with DOWN and OFFLINE reminders', async () => {
	const notifierConfig = await readSource(
		'bixi-module/bixi-monitor/src/main/java/com/lotus/bixi/monitor/config/MonitorNotifierConfiguration.java',
	);

	assert.match(notifierConfig, /new LoggingNotifier\(repository\)/);
	assert.match(notifierConfig, /new RemindingNotifier\(/);
	assert.match(notifierConfig, /@Bean\(initMethod = "start", destroyMethod = "stop"\)/);
	assert.match(notifierConfig, /setReminderStatuses\(new String\[\] \{ "DOWN", "OFFLINE" \}\)/);
});
