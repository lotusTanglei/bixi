import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

import {
	buildEvidence,
	normalizeServiceUrl,
	selectHealthyInstances,
} from './sba-multi-instance-acceptance.mjs';

const readProjectFile = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const instance = (id, status, serviceUrl) => ({
	id,
	registered: true,
	registration: {
		name: 'bixi-upms-biz',
		serviceUrl,
		managementUrl: `${serviceUrl}actuator`,
		healthUrl: `${serviceUrl}actuator/health`,
		metadata: { 'user.name': 'monitor', 'user.password': '******' },
	},
	statusInfo: { status },
});

test('selects distinct healthy instances for one service', () => {
	const selected = selectHealthyInstances([
		instance('offline', 'OFFLINE', 'http://10.0.0.1:4000/'),
		instance('one', 'UP', 'http://10.0.0.2:4000/'),
		instance('duplicate', 'UP', 'http://10.0.0.2:4000'),
		instance('two', 'UP', 'http://10.0.0.3:4000/'),
	], { serviceName: 'bixi-upms-biz', minimum: 2 });

	assert.deepEqual(selected.map(item => item.id), ['one', 'two']);
	assert.equal(normalizeServiceUrl(selected[0].registration.serviceUrl), 'http://10.0.0.2:4000');
});

test('rejects insufficient healthy instances and records a reviewable evidence shape', () => {
	assert.throws(
		() => selectHealthyInstances([instance('one', 'UP', 'http://10.0.0.2:4000/')], {
			serviceName: 'bixi-upms-biz',
			minimum: 2,
		}),
		/at least 2 healthy instances/,
	);

	const evidence = buildEvidence({
		status: 'passed',
		serviceName: 'bixi-upms-biz',
		minimum: 1,
		instances: [instance('one', 'UP', 'http://10.0.0.2:4000/')],
		expectedServiceUrls: [],
	});
	assert.equal(evidence.status, 'passed');
	assert.equal(evidence.serviceName, 'bixi-upms-biz');
	assert.equal(evidence.observedHealthyCount, 1);
});

test('shared business-service Actuator endpoints are exposed behind Basic auth', () => {
	const shared = readProjectFile('deploy/nacos/application-dev.yml');
	assert.match(shared, /^\s*include:\s*health,info,metrics,loggers,logfile,dynamictp\s*$/m);

	for (const path of [
		'deploy/nacos/bixi-gateway-dev.yml',
		'deploy/nacos/bixi-auth-dev.yml',
		'bixi-single/src/main/resources/application-dev.yml',
	]) {
		assert.match(readProjectFile(path), /^\s*include:\s*health\s*$/m, `${path} must retain its health-only override`);
	}

	const security = readProjectFile(
		'bixi-common/bixi-common-security/src/main/java/com/lotus/bixi/common/security/component/BixiActuatorSecurityConfiguration.java',
	);
	assert.match(security, /requestMatchers\("\/actuator\/health", "\/actuator\/health\/\*\*"\)\.permitAll\(\)/);
	assert.match(security, /\.anyRequest\(\)\.authenticated\(\)/);
	assert.match(security, /\.httpBasic\(/);
});
