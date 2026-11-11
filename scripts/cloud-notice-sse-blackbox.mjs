#!/usr/bin/env node

import assert from 'node:assert/strict';
import { createCipheriv, createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const envPath = process.env.BIXI_ENV_FILE || '.env';
const fileEnv = Object.fromEntries((await readFile(envPath, 'utf8'))
	.split(/\r?\n/)
	.filter(line => line.trim() && !line.trim().startsWith('#'))
	.map(line => {
		const separator = line.indexOf('=');
		return [line.slice(0, separator).trim(), line.slice(separator + 1).trim().replace(/^(['"])(.*)\1$/, '$2')];
	}));
const env = { ...fileEnv, ...process.env };
const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');

for (const key of ['ADMIN_USERNAME', 'ADMIN_PASSWORD', 'OAUTH_PASSWORD_CLIENT_SECRET', 'BIXI_ENCODE_KEY']) {
	assert(env[key], `Missing ${key}`);
}

const key = createHash('sha256').update(env.BIXI_ENCODE_KEY, 'utf8').digest();
const iv = createHash('md5').update(`${env.BIXI_ENCODE_KEY}bixi-iv-salt-2025`, 'utf8').digest();
const encryptPassword = (password) => {
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(password, 'utf8'), cipher.final()]).toString('base64');
};

async function request(path, { token, method = 'GET', body, signal } = {}) {
	const response = await fetch(`${apiBase}${path}`, {
		method,
		signal,
		headers: {
			...(token ? { Authorization: `Bearer ${token}` } : {}),
			...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
		},
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	let parsed;
	try {
		parsed = JSON.parse(text);
	} catch {
		parsed = null;
	}
	return { status: response.status, headers: response.headers, body: parsed, text };
}

function api(result, label) {
	assert.equal(result.status, 200, `${label} HTTP ${result.status}: ${(result.text || '').slice(0, 300)}`);
	assert.equal(result.body?.code, 0, `${label} API: ${(result.text || '').slice(0, 500)}`);
	return result.body.data;
}

function parseSse(raw) {
	let event = 'message';
	const data = [];
	for (const line of raw.split(/\r?\n/)) {
		if (line.startsWith('event:')) event = line.slice(6).trim();
		if (line.startsWith('data:')) data.push(line.slice(5).trim());
	}
	return { event, data: data.join('\n'), raw };
}

async function run() {
	const login = await fetch(`${apiBase}/auth/oauth2/token`, {
		method: 'POST',
		signal: AbortSignal.timeout(30000),
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${env.OAUTH_PASSWORD_CLIENT_SECRET}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: new URLSearchParams({
			username: env.ADMIN_USERNAME,
			password: encryptPassword(env.ADMIN_PASSWORD),
			grant_type: 'password',
			scope: 'server',
		}),
	});
	const loginText = await login.text();
	let loginBody;
	try {
		loginBody = JSON.parse(loginText);
	} catch {
		loginBody = {};
	}
	assert.equal(login.status, 200, `login HTTP ${login.status}`);
	assert(loginBody.access_token, 'login token missing');
	const token = loginBody.access_token;

	const userInfo = api(await request('/admin/user/info', { token }), 'user info');
	const user = userInfo.sysUser || userInfo;
	const userId = String(user.id);
	assert(userId && userId !== 'undefined', 'authenticated user ID missing');

	let streamController = new AbortController();
	let reader;
	const notices = new Map();
	let failure;
	try {
		const stream = await fetch(`${apiBase}/admin/user-notice/stream`, {
			headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
			signal: streamController.signal,
		});
		assert.equal(stream.status, 200, `SSE HTTP ${stream.status}`);
		assert.match(stream.headers.get('content-type') || '', /text\/event-stream/);
		reader = stream.body.getReader();
		let decoder = new TextDecoder();
		let pending = '';
		const nextEvent = async (timeoutMs) => {
			const deadline = Date.now() + timeoutMs;
			while (Date.now() < deadline) {
				const separator = pending.indexOf('\n\n');
				if (separator >= 0) {
					const raw = pending.slice(0, separator);
					pending = pending.slice(separator + 2);
					return parseSse(raw);
				}
				const remaining = Math.max(1, deadline - Date.now());
				const result = await Promise.race([
					reader.read(),
					new Promise((_, reject) => setTimeout(() => reject(new Error('SSE timeout')), remaining)),
				]);
				if (result.done) throw new Error('SSE closed');
				pending += decoder.decode(result.value, { stream: true });
			}
			throw new Error('SSE timeout');
		};

		const opened = await nextEvent(10000);
		assert.equal(opened.event, 'open');
		assert.equal(opened.data, 'ok');

		const title = `cloud-blackbox-${Date.now()}`;
		notices.set(title, undefined);
		api(await request('/admin/notice', {
			token,
			method: 'POST',
			body: {
				title,
				content: 'cloud SSE blackbox',
				type: '0',
				priority: '0',
				deliveryChannel: 'IN_APP',
				targetType: '3',
				targetIds: userId,
			},
		}), 'create notice');

		let notice;
		for (let attempt = 0; attempt < 20; attempt++) {
			const page = api(await request(`/admin/notice/page?title=${encodeURIComponent(title)}&current=1&size=10`, { token }), 'find notice');
			notice = page.records?.find(candidate => candidate.title === title);
			if (notice) break;
			await new Promise(resolve => setTimeout(resolve, 250));
		}
		assert(notice?.id, 'notice ID missing');
		const noticeId = String(notice.id);
		notices.set(title, noticeId);
		assert.equal(api(await request(`/admin/notice/send/${noticeId}`, { token, method: 'POST' }), 'send notice'), true);

		let recipient;
		for (let attempt = 0; attempt < 40; attempt++) {
			const inbox = api(await request(`/admin/user-notice/page?noticeId=${noticeId}&current=1&size=10`, { token }), 'inbox');
			recipient = inbox.records?.find(candidate => String(candidate.userId) === userId);
			if (recipient?.deliveryStatus === 'DELIVERED') break;
			await new Promise(resolve => setTimeout(resolve, 500));
		}
		assert(recipient, 'recipient row missing');
		assert.equal(recipient.deliveryStatus, 'DELIVERED', `unexpected delivery state ${JSON.stringify(recipient)}`);
		const recipientId = String(recipient.id);
		const refresh = await nextEvent(15000);
		assert.equal(refresh.event, 'message');
		const refreshData = JSON.parse(refresh.data);
		assert.equal(String(refreshData.noticeId), noticeId);
		assert.equal(String(refreshData.userNoticeId), recipientId);

		assert.equal(api(await request('/admin/user-notice', {
			token,
			method: 'PUT',
			body: { id: recipientId, isRead: '1' },
		}), 'mark read'), true);
		assert.equal(api(await request(`/admin/notice/send/${noticeId}`, { token, method: 'POST' }), 'resend notice'), true);
		assert.equal(api(await request(`/admin/notice/${noticeId}/delivery/retry`, { token, method: 'POST' }), 'retry endpoint'), true);
		const afterRetry = api(await request(`/admin/user-notice/page?noticeId=${noticeId}&current=1&size=10`, { token }), 'post-retry inbox');
		const sameRecipient = afterRetry.records?.find(candidate => String(candidate.id) === recipientId);
		assert(sameRecipient, 'recipient disappeared after resend/retry');
		assert.equal(String(sameRecipient.id), recipientId);
		assert.equal(sameRecipient.isRead, '1');

		streamController.abort();
		try {
			await reader.cancel();
		} catch {
			// The abort may already have closed the stream.
		}
		streamController = new AbortController();
		const reconnected = await fetch(`${apiBase}/admin/user-notice/stream`, {
			headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
			signal: streamController.signal,
		});
		assert.equal(reconnected.status, 200, `reconnected SSE HTTP ${reconnected.status}`);
		assert.match(reconnected.headers.get('content-type') || '', /text\/event-stream/);
		reader = reconnected.body.getReader();
		decoder = new TextDecoder();
		pending = '';
		const reopened = await nextEvent(10000);
		assert.equal(reopened.event, 'open');
		assert.equal(reopened.data, 'ok');

		const secondTitle = `${title}-reconnect`;
		notices.set(secondTitle, undefined);
		api(await request('/admin/notice', {
			token,
			method: 'POST',
			body: {
				title: secondTitle,
				content: 'cloud SSE reconnect blackbox',
				type: '0',
				priority: '0',
				deliveryChannel: 'IN_APP',
				targetType: '3',
				targetIds: userId,
			},
		}), 'create reconnect notice');
		let secondNotice;
		for (let attempt = 0; attempt < 20; attempt++) {
			const page = api(await request(`/admin/notice/page?title=${encodeURIComponent(secondTitle)}&current=1&size=10`, { token }), 'find reconnect notice');
			secondNotice = page.records?.find(candidate => candidate.title === secondTitle);
			if (secondNotice) break;
			await new Promise(resolve => setTimeout(resolve, 250));
		}
		assert(secondNotice?.id, 'reconnect notice ID missing');
		const secondNoticeId = String(secondNotice.id);
		notices.set(secondTitle, secondNoticeId);
		assert.equal(api(await request(`/admin/notice/send/${secondNoticeId}`, { token, method: 'POST' }), 'send reconnect notice'), true);
		let secondInbox;
		let secondRecipient;
		for (let attempt = 0; attempt < 40; attempt++) {
			secondInbox = api(await request(`/admin/user-notice/page?noticeId=${secondNoticeId}&current=1&size=10`, { token }), 'reconnect inbox');
			secondRecipient = secondInbox.records?.find(candidate => String(candidate.userId) === userId);
			if (secondRecipient?.deliveryStatus === 'DELIVERED') break;
			await new Promise(resolve => setTimeout(resolve, 500));
		}
		assert(secondRecipient, 'reconnect recipient row missing');
		assert.equal(secondRecipient.deliveryStatus, 'DELIVERED');
		const secondRecipients = api(await request(`/admin/user-notice/record/page?noticeId=${secondNoticeId}&current=1&size=10`,
			{ token }), 'reconnect recipient isolation');
		assert.equal(secondRecipients.records?.length, 1, 'reconnect notice must have only the intended recipient');
		assert.equal(String(secondRecipients.records[0].userId), userId);
		let secondRefreshData;
		const refreshDeadline = Date.now() + 15000;
		while (!secondRefreshData && Date.now() < refreshDeadline) {
			const event = await nextEvent(Math.max(1, refreshDeadline - Date.now()));
			if (event.event !== 'message') continue;
			const data = JSON.parse(event.data);
			if (String(data.noticeId) === secondNoticeId) secondRefreshData = data;
		}
		assert(secondRefreshData, 'reconnected stream did not receive the new notice');
		assert.equal(String(secondRefreshData.userNoticeId), String(secondRecipient.id));

		return {
			status: 'passed',
			apiBase,
			userId,
			noticeId,
			recipientId,
			sseOpen: true,
			refresh: refreshData,
			reconnectOpen: true,
			reconnectRefresh: secondRefreshData,
			secondNoticeId,
			deliveryStatus: recipient.deliveryStatus,
			resend: true,
			retry: true,
			cleanup: true,
		};
	} catch (error) {
		failure = error;
		throw error;
	} finally {
		streamController.abort();
		if (reader) {
			try {
				await reader.cancel();
			} catch {
				// The abort may already have closed the stream.
			}
		}
		let cleanupFailure;
		for (const [title, id] of notices) {
			try {
				const cleanupId = id || api(await request(`/admin/notice/page?title=${encodeURIComponent(title)}&current=1&size=10`, { token }),
					'find notice for cleanup').records?.find(candidate => candidate.title === title)?.id;
				if (cleanupId) assert.equal(api(await request(`/admin/notice/${cleanupId}`, { token, method: 'DELETE' }),
					'cleanup notice'), true);
			} catch (error) {
				cleanupFailure ||= error;
			}
		}
		if (cleanupFailure && !failure) throw cleanupFailure;
	}
}

try {
	console.log(JSON.stringify(await run()));
} catch (error) {
	console.error(JSON.stringify({ status: 'failed', error: error.message }));
	process.exitCode = 1;
}
