#!/usr/bin/env node

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function normalizeServiceUrl(value) {
	if (typeof value !== 'string' || value.trim() === '') {
		throw new TypeError('service URL must be a non-empty string');
	}
	const url = new URL(value);
	return url.toString().replace(/\/$/, '');
}

export function selectHealthyInstances(instances, options = {}) {
	if (!Array.isArray(instances)) throw new TypeError('SBA instances must be an array');
	const serviceName = String(options.serviceName || '').trim();
	const minimum = Number.isSafeInteger(options.minimum) ? options.minimum : 2;
	if (!serviceName) throw new TypeError('serviceName must not be empty');
	if (minimum < 1) throw new RangeError('minimum must be positive');
	const expected = new Set((options.expectedServiceUrls || []).map(normalizeServiceUrl));
	const selected = [];
	const seenUrls = new Set();
	for (const instance of instances) {
		const registration = instance?.registration;
		if (registration?.name !== serviceName || instance?.registered !== true
			|| instance?.statusInfo?.status !== 'UP') continue;
		const serviceUrl = normalizeServiceUrl(registration.serviceUrl);
		if (seenUrls.has(serviceUrl)) continue;
		seenUrls.add(serviceUrl);
		selected.push(instance);
	}
	if (selected.length < minimum) {
		throw new Error(`expected at least ${minimum} healthy instances for ${serviceName}, observed ${selected.length}`);
	}
	for (const expectedUrl of expected) {
		assert(selected.some(instance => normalizeServiceUrl(instance.registration.serviceUrl) === expectedUrl),
			`expected healthy SBA instance ${expectedUrl} was not observed`);
	}
	for (const instance of selected) {
		assert.match(String(instance.registration.managementUrl || ''), /\/actuator\/?$/,
			`management URL missing for ${instance.id}`);
		assert.match(String(instance.registration.healthUrl || ''), /\/actuator\/health\/?$/,
			`health URL missing for ${instance.id}`);
	}
	return selected;
}

function redactInstance(instance) {
	const registration = instance.registration || {};
	const metadata = { ...(registration.metadata || {}) };
	for (const key of Object.keys(metadata)) {
		if (/pass|secret|token|key/i.test(key)) metadata[key] = '******';
	}
	return {
		id: instance.id,
		registered: instance.registered,
		status: instance.statusInfo?.status,
		serviceUrl: normalizeServiceUrl(registration.serviceUrl),
		managementUrl: registration.managementUrl,
		healthUrl: registration.healthUrl,
		metadata,
	};
}

export function buildEvidence({ status, serviceName, minimum, instances, expectedServiceUrls }) {
	const healthy = selectHealthyInstances(instances, { serviceName, minimum, expectedServiceUrls });
	return {
		status,
		serviceName,
		minimumHealthyInstances: minimum,
		observedHealthyCount: healthy.length,
		expectedServiceUrls: (expectedServiceUrls || []).map(normalizeServiceUrl),
		instances: healthy.map(redactInstance),
		observedAt: new Date().toISOString(),
	};
}

function readDotEnv(text) {
	return Object.fromEntries(text.split(/\r?\n/)
		.filter(line => line.trim() && !line.trim().startsWith('#'))
		.map(line => {
			const separator = line.indexOf('=');
			if (separator < 1) return ['', ''];
			return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
		})
		.filter(([key]) => key));
}

async function run() {
	const envPath = process.env.BIXI_ENV_FILE || '.env';
	const fileEnv = await readFile(envPath, 'utf8').then(readDotEnv).catch(() => ({}));
	const env = { ...fileEnv, ...process.env };
	const baseUrl = String(env.SBA_URL || env.BIXI_SBA_CLIENT_URL || 'http://127.0.0.1:5001').replace(/\/$/, '');
	const username = env.SBA_USERNAME || env.BIXI_SBA_CLIENT_USERNAME;
	const password = env.SBA_PASSWORD || env.BIXI_SBA_CLIENT_PASSWORD;
	const serviceName = env.SBA_EXPECTED_SERVICE_NAME || 'bixi-upms-biz';
	const minimum = Number(env.SBA_EXPECTED_MIN_UP || 2);
	const expectedServiceUrls = String(env.SBA_EXPECTED_SERVICE_URLS || '')
		.split(',').map(value => value.trim()).filter(Boolean);
	assert(username && password, 'SBA_USERNAME/SBA_PASSWORD (or BIXI_SBA_CLIENT_*) are required');

	const response = await fetch(`${baseUrl}/instances`, {
		headers: {
			Accept: 'application/json',
			Authorization: `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`,
		},
		signal: AbortSignal.timeout(Number(env.SBA_TIMEOUT_MS || 15000)),
	});
	const body = await response.text();
	assert.equal(response.status, 200, `SBA /instances HTTP ${response.status}: ${body.slice(0, 300)}`);
	let instances;
	try {
		instances = JSON.parse(body);
	} catch (error) {
		throw new Error(`SBA /instances did not return JSON: ${error.message}`);
	}
	const evidence = buildEvidence({ status: 'passed', serviceName, minimum, instances, expectedServiceUrls });
	return { ...evidence, sbaUrl: baseUrl };
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (isMain) {
	try {
		console.log(JSON.stringify(await run()));
	} catch (error) {
		console.error(JSON.stringify({ status: 'failed', error: error.message }));
		process.exitCode = 1;
	}
}
