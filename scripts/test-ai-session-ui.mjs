#!/usr/bin/env node
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = join(fileURLToPath(new URL('..', import.meta.url)));
const source = readFileSync(
	join(root, 'bixi-ui/src/views/ai/chat/components/SessionList.vue'),
	'utf8',
);
const chatPage = readFileSync(join(root, 'bixi-ui/src/views/ai/chat/index.vue'), 'utf8');
const chatApi = readFileSync(join(root, 'bixi-ui/src/api/ai/chat.ts'), 'utf8');
const requestClient = readFileSync(join(root, 'bixi-ui/src/utils/request.ts'), 'utf8');
const requestPaths = readFileSync(join(root, 'bixi-ui/src/utils/other.ts'), 'utf8');
const configPage = readFileSync(join(root, 'bixi-ui/src/views/ai/config/index.vue'), 'utf8');
const knowledgePage = readFileSync(join(root, 'bixi-ui/src/views/ai/knowledge/index.vue'), 'utf8');
const ssePath = join(root, 'bixi-ui/src/utils/sse.ts');

assert.match(source, /import \{ deleteSession, updateSession \} from '\/@\/api\/ai\/chat';/);
assert.match(
	source,
	/await deleteSession\(id\);[\s\S]*?aiStore\.removeSession\(id\);/,
	'delete must persist before removing the local session',
);
assert.match(
	source,
	/await updateSession\(\{ id, title: value \}\);[\s\S]*?aiStore\.updateSessionTitle\(id, value\);/,
	'rename must persist before updating the local session',
);
assert.match(source, /useMessage\(\)\.error\(error\?\.msg \|\| '删除失败'\)/);
assert.match(source, /useMessage\(\)\.error\(error\?\.msg \|\| '重命名失败'\)/);

assert.doesNotMatch(
	chatPage,
	/\.\.\.aiStore\.config/,
	'chat requests must use the persisted tenant configuration unless the user explicitly overrides it',
);
assert.match(chatPage, /import \{[^}]*createSession[^}]*sessionList[^}]*\} from '\/@\/api\/ai\/chat';/s);
assert.match(chatPage, /<SessionList[^>]*@create="handleCreate"/s);
assert.match(chatPage, /await createSession\(/);
assert.match(chatPage, /await sessionList\(/);

assert.ok(existsSync(ssePath), 'AI streaming must use a tested SSE decoder');
const { consumeSseResponse } = await import(pathToFileURL(ssePath).href);
const encoder = new TextEncoder();
const response = (status, chunks, failure) => ({
	status,
	data: new ReadableStream({
		start(controller) {
			for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
			if (failure) controller.error(failure);
			else controller.close();
		},
	}),
});

await assert.rejects(
	consumeSseResponse(response(503, ['{"msg":"provider unavailable"}']), () => {}),
	/provider unavailable/,
	'non-2xx responses must fail with the backend diagnostic',
);

const serverErrorPartial = [];
await assert.rejects(
	consumeSseResponse(
		response(200, ['data: par', 'tial\n\nevent: error\ndata: provider disconnected\n\n']),
		(chunk) => serverErrorPartial.push(chunk),
	),
	/provider disconnected/,
	'SSE error events must reject the active request',
);
assert.equal(serverErrorPartial.join(''), 'partial');

let serverErrorCancelled = false;
const openServerErrorBody = new ReadableStream({
	start(controller) {
		controller.enqueue(encoder.encode('event: error\ndata: provider disconnected\n\n'));
	},
	cancel() {
		serverErrorCancelled = true;
	},
});
await assert.rejects(
	consumeSseResponse({ status: 200, data: openServerErrorBody }, () => {}),
	/provider disconnected/,
);
assert.equal(serverErrorCancelled, true, 'SSE error events must cancel an open response stream');

const networkPartial = [];
const interruptedBody = new ReadableStream({
	start(controller) {
		controller.enqueue(encoder.encode('data: partial\n\n'));
		setTimeout(() => controller.error(new Error('network interrupted')), 0);
	},
});
await assert.rejects(
	consumeSseResponse({ status: 200, data: interruptedBody }, (chunk) => networkPartial.push(chunk)),
	/network interrupted/,
);
assert.equal(networkPartial.join(''), 'partial', 'network failure must not discard delivered content');

const abortController = new AbortController();
const abortBody = new ReadableStream({
	start(controller) {
		abortController.signal.addEventListener('abort', () => controller.error(abortController.signal.reason), { once: true });
	},
});
const aborted = consumeSseResponse({ status: 200, data: abortBody }, () => {});
abortController.abort();
await assert.rejects(aborted, (failure) => failure?.name === 'AbortError');

assert.match(chatApi, /adapter:\s*'fetch'/, 'browser streaming must use Axios fetch adapter');
assert.match(requestClient, /response\.config\.responseType === 'stream'[\s\S]*?return response;/);
assert.match(requestClient, /response\?\.config\?\.responseType === 'stream' \? response/);
assert.match(
	requestPaths,
	/originUrl\?\.startsWith\('\/ai\/'\)[\s\S]*?return `\/admin\$\{originUrl\}`/,
	'single mode must retain the AI controller prefix',
);
assert.match(chatPage, /import \{[^}]*streamChat[^}]*\} from '\/@\/api\/ai\/chat';/s);
assert.match(chatPage, /await streamChat\(/);
assert.match(chatPage, /new AbortController\(\)/);
assert.match(chatPage, /if \(!answer\) aiStore\.updateMessage\(/, 'stream errors must preserve delivered partial content');
assert.match(chatPage, /@cancel="handleCancel"/);
assert.match(chatPage, /onBeforeUnmount\(handleCancel\)/);
assert.doesNotMatch(chatPage, /await chat\(/, 'the chat page must not fall back to buffered chat');

assert.match(configPage, /import \{ getConfig, updateConfig \} from '\/@\/api\/ai\/config';/);
assert.match(configPage, /await getConfig\(\)/);
assert.match(configPage, /await updateConfig\(/);
assert.match(
	knowledgePage,
	/:label="doc\.title"/,
	'the knowledge selector must display the DocumentVO title returned by the backend',
);

console.log('AI session, streaming recovery, model config, and knowledge selection UI contract: 5/5');
