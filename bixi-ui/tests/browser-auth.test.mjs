import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';

const source = (path) => readFileSync(new URL(path, import.meta.url), 'utf8');
const require = createRequire(import.meta.url);

test('auth tokens stay in each tab and old token cookies are removed', () => {
	const cookies = new Map([['token', 'legacy-access'], ['refresh_token', 'legacy-refresh']]);
	const previousDocument = globalThis.document;
	const previousWindow = globalThis.window;
	const sessionStorage = () => {
		const values = new Map();
		return {
			setItem: (key, value) => values.set(key, value),
			getItem: (key) => values.get(key) ?? null,
			removeItem: (key) => values.delete(key),
			key: (index) => [...values.keys()][index] ?? null,
			get length() { return values.size; },
		};
	};
	try {
		globalThis.document = {
			get cookie() { return [...cookies].map(([key, value]) => `${key}=${value}`).join('; '); },
			set cookie(value) {
				const [entry] = value.split(';');
				const [key, cookieValue] = entry.split('=');
				const expires = value.match(/;\s*expires=([^;]+)/);
				if (expires && Date.parse(expires[1]) <= Date.now()) cookies.delete(key);
				else cookies.set(key, cookieValue);
			},
		};
		const firstTab = sessionStorage();
		globalThis.window = { sessionStorage: firstTab };
		const ts = require('typescript');
		const compiled = ts.transpileModule(source('../src/utils/storage.ts'), {
			compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
		}).outputText;
		const module = { exports: {} };
		new Function('require', 'module', 'exports', compiled)(require, module, module.exports);
		const { Session } = module.exports;

		assert.equal(cookies.has('token'), false);
		assert.equal(cookies.has('refresh_token'), false);
		Session.set('token', 'alice');
		Session.set('refresh_token', 'alice-refresh');
		globalThis.window.sessionStorage = sessionStorage();
		Session.set('token', 'bob');
		Session.set('refresh_token', 'bob-refresh');
		globalThis.window.sessionStorage = firstTab;
		assert.equal(Session.getToken(), 'alice');
		assert.equal(Session.get('refresh_token'), 'alice-refresh');
		assert.equal(cookies.has('token'), false);
		assert.equal(cookies.has('refresh_token'), false);
		Session.remove('token');
		assert.equal(Session.getToken(), null);
	} finally {
		globalThis.document = previousDocument;
		globalThis.window = previousWindow;
	}
});

test('browser auth source has no published credentials or internal token check', () => {
	const login = source('../src/api/login/index.ts');
	const request = source('../src/utils/request.ts');
	const scheduler = source('../src/components/CheckToken/index.vue');
	const env = source('../.env') + source('../.env.development');
	const publicCode = login + request + scheduler + env;

	assert.match(login, /['"]bixi-browser['"]/);
	assert.equal((login.match(/client_id: BROWSER_CLIENT_ID/g) || []).length, 4);
	assert.doesNotMatch(publicCode, /VITE_OAUTH2_|VITE_PWD_ENC_KEY|basicAuth|Basic |Enc-Flag|check_token/);
	assert.doesNotMatch(login, /Authorization:/);
});

test('logout keeps browser auth state when server revocation fails', () => {
	const menu = source('../src/layout/navBars/breadcrumb/user.vue');
	assert.match(menu, /await logout\(\);\s*\} catch \{\s*ElMessage\.error\('退出失败，请重试'\);\s*return;\s*\}/);
	assert.match(menu, /mittBus\.emit\([\s\S]*?Session\.clear\(\);\s*window\.location\.reload\(\);/);
});

test('refresh target comes from expires_in and stays before expiry', async () => {
	const { refreshAtFrom } = await import('../src/utils/tokenRefresh.ts');
	assert.equal(refreshAtFrom(43_200, 1_000), 41_401_000);
	assert.equal(refreshAtFrom(120, 1_000), 109_000);
	assert.throws(() => refreshAtFrom(0), /expires_in/);
	assert.throws(() => refreshAtFrom('invalid'), /expires_in/);
});

test('concurrent refreshes share one request and can retry after failure', async () => {
	const { refreshOnce } = await import('../src/utils/tokenRefresh.ts');
	let calls = 0;
	let resolveRequest;
	const refresh = () => {
		calls++;
		return new Promise((resolve) => { resolveRequest = resolve; });
	};
	const first = refreshOnce(refresh);
	const second = refreshOnce(refresh);
	assert.strictEqual(first, second);
	assert.equal(calls, 1);
	resolveRequest('new-token');
	assert.equal(await first, 'new-token');

	await assert.rejects(refreshOnce(() => Promise.reject(new Error('expired'))), /expired/);
	assert.equal(await refreshOnce(() => Promise.resolve('retried')), 'retried');
});

test('transient refresh errors preserve a session but rejected credentials end it', async () => {
	const { isRejectedRefreshCredential } = await import('../src/utils/tokenRefresh.ts');
	assert.equal(isRejectedRefreshCredential({ code: 1, msg: 'invalid_grant' }), true);
	assert.equal(isRejectedRefreshCredential({ error: 'invalid_client' }), true);
	assert.equal(isRejectedRefreshCredential({ code: 'ERR_NETWORK', message: 'Network Error' }), false);
	assert.equal(isRejectedRefreshCredential({ code: 1, msg: 'Bad Gateway' }), false);
	assert.equal(isRejectedRefreshCredential({ response: { status: 502 } }), false);
});
