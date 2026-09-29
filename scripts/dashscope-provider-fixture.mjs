import { createServer } from 'node:http';

// DashScope's Spring AI client sends the provider URL as a base URL. Keep the
// two default ports for cloud and single probes, while allowing a test to use
// an isolated port without editing this fixture.
const ports = (process.env.DASHSCOPE_PROVIDER_PORTS || '39995,39996')
	.split(',').map(value => Number(value.trim())).filter(Number.isInteger);
const host = process.env.DASHSCOPE_PROVIDER_HOST || '0.0.0.0';
const marker = 'LOCAL-DETERMINISTIC-ANSWER';
const requests = [];

function requestSummary(request, body) {
	return {
		method: request.method,
		path: request.url || '/',
		streamHeader: request.headers['x-dashscope-sse'] || null,
		accept: request.headers.accept || null,
		model: body?.model || null,
		stream: body?.stream === true,
		incrementalOutput: body?.parameters?.incremental_output === true,
		embeddingCount: Array.isArray(body?.input?.texts) ? body.input.texts.length : 0,
	};
}

function hashEmbedding(text, dimension = 4) {
	const vector = Array.from({ length: dimension }, () => 0);
	for (const token of String(text || '').toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean)) {
		let hash = 0;
		for (const character of token) hash = Math.imul(hash ^ character.codePointAt(0), 16777619);
		vector[Math.abs(hash) % dimension] += 1;
	}
	return vector;
}

function chatContent(body) {
	const messages = Array.isArray(body?.input?.messages) ? body.input.messages : [];
	const last = messages.at(-1)?.content;
	return `${marker}:${typeof last === 'string' ? last.slice(-120) : 'ok'}`;
}

function responseFor(path, body) {
	const requestId = `local-${Date.now()}-${requests.length}`;
	if (path === '/api/v1/services/embeddings/text-embedding/text-embedding') {
		const texts = Array.isArray(body?.input?.texts) ? body.input.texts : [];
		return {
			request_id: requestId,
			code: null,
			message: null,
			output: { embeddings: texts.map((text, index) => ({
				text_index: index,
				embedding: hashEmbedding(text),
			})) },
			usage: { total_tokens: texts.length },
		};
	}
	return {
		request_id: requestId,
		output: {
			text: chatContent(body),
			choices: [{ finish_reason: 'stop', message: { role: 'assistant', content: chatContent(body) } }],
		},
		usage: { input_tokens: 1, output_tokens: 1, total_tokens: 2 },
	};
}

function isChatStream(request, body) {
	// DashScope's WebClient sets X-DashScope-SSE and stream=true. Some direct
	// probes set only Accept, so accept all three protocol indicators.
	return request.headers['x-dashscope-sse']?.toLowerCase() === 'enable'
		|| request.headers.accept?.includes('text/event-stream')
		|| body?.stream === true
		|| body?.parameters?.incremental_output === true;
}

function streamPayload(body) {
	const content = chatContent(body);
	const split = Math.max(1, Math.ceil(content.length / 2));
	const chunks = [content.slice(0, split), content.slice(split)].filter(Boolean);
	return chunks.map((chunk, index) => JSON.stringify({
		request_id: `local-stream-${Date.now()}-${index}`,
		output: { choices: [{
			finish_reason: index === chunks.length - 1 ? 'stop' : null,
			message: { role: 'assistant', content: chunk },
		}] },
		usage: { input_tokens: 1, output_tokens: chunks.length, total_tokens: chunks.length + 1 },
	})).join('\n\n');
}

function writeJson(response, payload, status = 200) {
	response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
	response.end(JSON.stringify(payload));
}

function createProviderServer(port) {
	const server = createServer((request, response) => {
		let raw = '';
		request.setEncoding('utf8');
		request.on('data', chunk => { raw += chunk; });
		request.on('end', () => {
			let body = {};
			try { body = raw ? JSON.parse(raw) : {}; } catch { /* deterministic fallback */ }
			requests.push(requestSummary(request, body));

			if (request.method === 'GET' && request.url === '/health') {
				writeJson(response, { status: 'UP', provider: 'local-deterministic' });
				return;
			}
			if (request.method === 'GET' && request.url === '/requests') {
				writeJson(response, requests);
				return;
			}
			if (request.method !== 'POST') {
				response.writeHead(405, { Allow: 'POST, GET' });
				response.end();
				return;
			}

			const path = request.url || '/';
			if (path !== '/api/v1/services/aigc/text-generation/generation'
				&& path !== '/api/v1/services/embeddings/text-embedding/text-embedding') {
				writeJson(response, { code: 'NOT_FOUND', message: `unsupported path: ${path}` }, 404);
				return;
			}

			if (path === '/api/v1/services/aigc/text-generation/generation' && isChatStream(request, body)) {
				response.writeHead(200, {
					'Content-Type': 'text/event-stream; charset=utf-8',
					'Cache-Control': 'no-cache',
					'Connection': 'keep-alive',
				});
				response.end(`${streamPayload(body).split('\n\n').map(payload => `data: ${payload}`).join('\n\n')}\n\ndata: [DONE]\n\n`);
				return;
			}
			writeJson(response, responseFor(path, body));
		});
	});
	server.listen(port, host, () => console.log(`deterministic provider listening on ${host}:${port}`));
	return server;
}

const servers = ports.map(createProviderServer);
function shutdown() {
	for (const server of servers) server.close();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
