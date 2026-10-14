import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import test from 'node:test';

const source = readFileSync(new URL('./acceptance.mjs', import.meta.url), 'utf8');
const start = source.indexOf('async function verifySmsLogin(currentUser) {');
const end = source.indexOf('\nasync function verifyAcceptanceResources()', start);
assert.ok(start >= 0 && end > start, 'SMS acceptance function was not found');
const verifySmsLoginSource = source.slice(start, end);

for (const mode of ['cloud', 'single']) for (const configured of [false, true]) {
	test(`${mode} SMS fixture login works with ${configured ? 'configured' : 'unconfigured'} sender without sending to fixture phone`, async () => {
		let userCreated = false;
		const redisKeys = new Set();
		let challengeCalls = 0;
		let phoneLookups = 0;
		let randomCalls = 0;
		let probeMobile;
		let fixtureMobile;
		let smsUsername;
		let delayedProbeLookup;
		let deletedKeys;
		const ok = data => ({ status: 200, body: { code: 0, data } });
		const rejected = msg => ({ status: 200, body: { code: 1, msg } });
		const context = {
			mode,
			browserClientSecret: 'test-secret',
			browserAuthPath: mode === 'single' ? '/admin' : '/auth',
			randomBytes: size => Buffer.alloc(size, ++randomCalls),
			generateStrongPassword: () => 'fixture-password',
			assert: (condition, message) => assert.ok(condition, message),
			assertApi: response => {
				assert.equal(response.status, 200);
				assert.equal(response.body?.code, 0);
				return response.body.data;
			},
			request: async path => {
				if (path.startsWith('/admin/mobile/')) {
					challengeCalls++;
					assert.equal(userCreated, false, 'SMS challenge would send to the registered fixture phone');
					probeMobile = path.slice('/admin/mobile/'.length);
					delayedProbeLookup = () => userCreated && probeMobile === fixtureMobile;
					if (!configured) return rejected('短信发送失败，请稍后再试');
					redisKeys.add(`SMS_RATE_LIMIT_KEY:TENANT:1:${probeMobile}`);
					return ok(true);
				}
				if (path === '/admin/user/info') return ok({ sysUser: { username: smsUsername } });
				if (path === `${mode === 'single' ? '/admin' : '/auth'}/token/logout`) return ok(true);
				throw new Error(`unexpected request: ${path}`);
			},
			authorized: async (path, options = {}) => {
				if (path.startsWith('/admin/user/page?')) {
					const phone = new URL(`http://localhost${path}`).searchParams.get('phone');
					if (phone) {
						phoneLookups++;
						assert.equal(userCreated, false, 'fixture phone was checked after registration');
						return ok({ total: 0, records: [] });
					}
					return ok({ records: userCreated ? [{ id: '42', username: smsUsername }] : [] });
				}
				if (path === '/admin/user' && options.method === 'POST') {
					fixtureMobile = options.body.phone;
					smsUsername = options.body.username;
					userCreated = true;
					assert.equal(delayedProbeLookup(), false,
						'delayed SMS provider lookup would send to the registered fixture phone');
					return ok(true);
				}
				if (path === '/admin/user' && options.method === 'PUT') {
					return rejected(options.body.username === ' ' ? '用户名不能为空' : '用户名不能修改');
				}
				if (path === '/admin/user' && options.method === 'DELETE') {
					userCreated = false;
					return ok(true);
				}
				throw new Error(`unexpected authorized request: ${path}`);
			},
			browserToken: async parameters => {
				if (parameters.grant_type === 'refresh_token') return { status: 200, body: {
					access_token: 'refreshed-token', refresh_token: 'refreshed-refresh-token' } };
				const codeKey = `SMS_CODE_KEY:TENANT:1:${parameters.mobile}`;
				if (parameters.code === '12345678' && redisKeys.has(codeKey) && !parameters.client_secret) {
					redisKeys.delete(codeKey);
					return { status: 200, body: { access_token: 'sms-token', refresh_token: 'sms-refresh-token' } };
				}
				return { status: 401, body: {} };
			},
			assertAcceptanceKeyState: (key, expected) => assert.equal(redisKeys.has(key), expected),
			seedAcceptanceCode: key => { redisKeys.add(key); },
			deleteAcceptanceKeys: (...keys) => {
				deletedKeys = keys;
				for (const key of keys) redisKeys.delete(key);
			},
			console,
		};
		const verifySmsLogin = runInNewContext(`${verifySmsLoginSource}\nverifySmsLogin`, context);
		const result = await verifySmsLogin({ tenantId: 1, deptId: 1 });
		assert.equal(phoneLookups, 2);
		assert.equal(challengeCalls, 1);
		assert.notEqual(probeMobile, fixtureMobile);
		assert.equal(userCreated, false);
		assert.equal(redisKeys.size, 0);
		assert.deepEqual(new Set(deletedKeys), new Set([
			`SMS_CODE_KEY:TENANT:1:${probeMobile}`,
			`SMS_RATE_LIMIT_KEY:TENANT:1:${probeMobile}`,
			`SMS_CODE_KEY:TENANT:1:${fixtureMobile}`,
			`SMS_RATE_LIMIT_KEY:TENANT:1:${fixtureMobile}`,
		]));
		assert.equal(result.unknownMobileChallenge, configured ? 'accepted' : 'rejected');
		assert.equal(result.fixtureLogin, 200);
		assert.equal(result.fixtureRefresh, 200);
		assert.equal(result.replayRejected, 401);
	});
}

test('occupied fixture phones are rejected before an SMS challenge', async () => {
	let lookups = 0;
	let challenges = 0;
	const verifySmsLogin = runInNewContext(`${verifySmsLoginSource}\nverifySmsLogin`, {
		randomBytes: size => Buffer.alloc(size, 1),
		authorized: async path => {
			assert.match(path, /^\/admin\/user\/page\?current=1&size=1&phone=/);
			lookups++;
			return { status: 200, body: { code: 0, data: { total: 1, records: [] } } };
		},
		assertApi: response => response.body.data,
		assert: (condition, message) => assert.ok(condition, message),
		request: () => { challenges++; throw new Error('SMS challenge must not be requested'); },
	});
	await assert.rejects(verifySmsLogin({ tenantId: 1 }), /could not find.*unused SMS acceptance phone/);
	assert.equal(lookups, 10);
	assert.equal(challenges, 0);
});
