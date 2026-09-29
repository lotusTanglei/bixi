#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const port = Number(process.env.DASHSCOPE_PROVIDER_TEST_PORT || 40195);
const providerScript = join(root, 'scripts', 'dashscope-provider-fixture.mjs');
const evidenceFile = process.env.DASHSCOPE_PROVIDER_EVIDENCE
	|| join(root, 'target', 'phase2-runtime', 'EVIDENCE-C-AI-PROVIDER-20260927.json');
const baseUrl = `http://127.0.0.1:${port}`;
const child = spawn(process.execPath, [providerScript], {
	env: { ...process.env, DASHSCOPE_PROVIDER_PORTS: String(port), DASHSCOPE_PROVIDER_HOST: '127.0.0.1' },
	stdio: ['ignore', 'pipe', 'pipe'],
});
let stdout = '';
let stderr = '';
child.stdout.on('data', chunk => { stdout += chunk.toString(); });
child.stderr.on('data', chunk => { stderr += chunk.toString(); });

async function waitForHealth() {
	const deadline = Date.now() + 5000;
	while (Date.now() < deadline) {
		try {
			const response = await fetch(`${baseUrl}/health`);
			if (response.ok) return;
		} catch {
			// The child may still be binding its port.
		}
		await new Promise(resolve => setTimeout(resolve, 50));
	}
	throw new Error(`provider did not become healthy\nstdout=${stdout}\nstderr=${stderr}`);
}

async function post(path, body, headers = {}) {
	const response = await fetch(`${baseUrl}${path}`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...headers },
		body: JSON.stringify(body),
	});
	return { response, text: await response.text() };
}

const result = {
	observedAt: new Date().toISOString(),
	provider: 'local-deterministic',
	baseUrl,
	status: 'failed',
	checks: [],
	requestSummary: [],
	secretsOmitted: true,
};

try {
	await waitForHealth();
	result.checks.push('health endpoint is reachable');

	const sync = await post('/api/v1/services/aigc/text-generation/generation', {
		model: 'qwen-plus',
		input: { messages: [{ role: 'user', content: 'protocol sync' }] },
		parameters: { result_format: 'message', stream: false },
	});
	assert.equal(sync.response.status, 200);
	assert.match(sync.response.headers.get('content-type') || '', /^application\/json/);
	const syncBody = JSON.parse(sync.text);
	assert.match(syncBody.output.choices[0].message.content, /^LOCAL-DETERMINISTIC-ANSWER:/);
	result.checks.push('non-stream chat endpoint and DashScope response shape');

	const stream = await post('/api/v1/services/aigc/text-generation/generation', {
		model: 'qwen-plus',
		input: { messages: [{ role: 'user', content: 'protocol stream' }] },
		parameters: { result_format: 'message', incremental_output: true },
		stream: true,
	}, { 'X-DashScope-SSE': 'enable' });
	assert.equal(stream.response.status, 200);
	assert.match(stream.response.headers.get('content-type') || '', /^text\/event-stream/);
	const streamEvents = stream.text.split(/\r?\n/).filter(line => line.startsWith('data: ')).map(line => line.slice(6));
	assert.equal(streamEvents.at(-1), '[DONE]');
	const streamContent = streamEvents.slice(0, -1).map(value => JSON.parse(value).output.choices[0].message.content).join('');
	assert.match(streamContent, /^LOCAL-DETERMINISTIC-ANSWER:/);
	result.checks.push('X-DashScope-SSE stream request and SSE chunk framing');

	const embedding = await post('/api/v1/services/embeddings/text-embedding/text-embedding', {
		model: 'text-embedding-v2',
		input: { texts: ['protocol embedding', 'second text'] },
	});
	assert.equal(embedding.response.status, 200);
	const embeddingBody = JSON.parse(embedding.text);
	assert.equal(embeddingBody.output.embeddings.length, 2);
	assert.deepEqual(embeddingBody.output.embeddings.map(value => value.text_index), [0, 1]);
	assert.equal(embeddingBody.output.embeddings[0].embedding.length, 4);
	result.checks.push('embedding endpoint path and text_index/embedding response shape');

	const requests = await fetch(`${baseUrl}/requests`).then(response => response.json());
	result.requestSummary = requests;
	assert(requests.some(request => request.path === '/api/v1/services/aigc/text-generation/generation' && request.streamHeader === 'enable'));
	assert(requests.some(request => request.path === '/api/v1/services/embeddings/text-embedding/text-embedding'));
	result.status = 'passed';
} catch (error) {
	result.error = error instanceof Error ? error.message : String(error);
	} finally {
		child.kill('SIGTERM');
		await new Promise(resolve => {
			const timer = setTimeout(resolve, 1000);
			child.once('exit', () => { clearTimeout(timer); resolve(); });
		});
		await mkdir(join(root, 'target', 'phase2-runtime'), { recursive: true });
		await writeFile(evidenceFile, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
		console.log(JSON.stringify(result, null, 2));
		if (result.status !== 'passed') process.exitCode = 1;
}
