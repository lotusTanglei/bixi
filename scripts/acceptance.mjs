#!/usr/bin/env node

import { createCipheriv, createHash, randomBytes, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const mode = process.env.BIXI_MODE || 'cloud';

if (!['cloud', 'single'].includes(mode)) {
	throw new Error(`BIXI_MODE must be cloud or single, received: ${mode}`);
}

const envFile = process.env.BIXI_ENV_FILE ? resolve(process.env.BIXI_ENV_FILE) : resolve(root, '.env');
const fileEnv = await readEnv(envFile);
const env = { ...fileEnv, ...process.env };
const origin = (env.BIXI_ORIGIN || `http://localhost:${env.BIXI_HTTP_PORT || '8080'}`).replace(/\/$/, '');
const apiBase = (env.BIXI_API_BASE_URL || `${origin}/api`).replace(/\/$/, '');
const username = required('ADMIN_USERNAME');
const password = required('ADMIN_PASSWORD');
const clientSecret = required('OAUTH_PASSWORD_CLIENT_SECRET');
const encodeKey = required('BIXI_ENCODE_KEY');
const createdTitles = ['新增示例任务', '修改示例任务', '删除示例任务'];

let token;
let taskId;
let deleted = false;

try {
	await waitForHealth(`${origin}/healthz`, (response) => response.status === 200, 'frontend');
	await waitForHealth(
		`${apiBase}/admin/actuator/health`,
		(response) => response.status === 200 && response.body?.status === 'UP',
		'backend'
	);

	const loginBody = new URLSearchParams({
		username,
		password: encryptPassword(password, encodeKey),
		grant_type: 'password',
		scope: 'server',
	});
	const login = await request('/auth/oauth2/token', {
		method: 'POST',
		headers: {
			Authorization: `Basic ${Buffer.from(`bixi:${clientSecret}`).toString('base64')}`,
			'Content-Type': 'application/x-www-form-urlencoded',
		},
		body: loginBody.toString(),
	});
	assert(login.status === 200 && login.body?.access_token, `login failed (${login.status})`, login.body);
	token = login.body.access_token;

	const userInfo = assertApi(await authorized('/admin/user/info'), 'load user info');
	const currentUser = userInfo.sysUser || userInfo;
	assert(currentUser.username === username, 'user info returned an unexpected username', userInfo);
	assert(!Object.hasOwn(currentUser, 'password'), 'user info exposed a password field', currentUser);
	const permissions = Array.isArray(userInfo.permissions) ? userInfo.permissions : [];
	const roles = Array.isArray(userInfo.roles) ? userInfo.roles.map(String) : [];
	assert(roles.includes('1'), 'administrator role was not returned', userInfo.roles);
	for (const permission of ['demo_task_view', 'demo_task_add', 'demo_task_edit', 'demo_task_del']) {
		assert(permissions.includes(permission), `missing permission: ${permission}`, permissions);
	}

	const menu = assertApi(await authorized('/admin/menu'), 'load menu');
	assert(containsMenuPath(menu, '/demo/task/index'), 'sample task menu is not visible', menu);
	const auditLogBaseline = new Set((await loadAuditLogs()).map((record) => String(record.id)));

	const suffix = `${mode}-${Date.now()}`;
	const title = `双模验收任务-${suffix}`;
	const createPayload = {
		title,
		assignee: '验收负责人',
		priority: 'HIGH',
		taskStatus: 'TODO',
		dueDate: formatDate(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
		remark: `created by ${mode} acceptance`,
	};
	assertApi(await authorized('/admin/demo/task', { method: 'POST', body: createPayload }), 'create task');

	const page = assertApi(
		await authorized(`/admin/demo/task/page?current=1&size=10&title=${encodeURIComponent(title)}`),
		'query task page'
	);
	const createdTask = page.records?.find((record) => record.title === title);
	assert(createdTask?.id, 'created task was not returned by the page query', page);
	taskId = String(createdTask.id);

	const details = assertApi(await authorized(`/admin/demo/task/details/${taskId}`), 'load task details');
	assert(details.title === title && details.priority === 'HIGH', 'task details do not match the created task', details);

	const updatePayload = {
		...details,
		assignee: '双模验收负责人',
		priority: 'MEDIUM',
		taskStatus: 'IN_PROGRESS',
	};
	assertApi(await authorized('/admin/demo/task', { method: 'PUT', body: updatePayload }), 'update task');
	const updated = assertApi(await authorized(`/admin/demo/task/details/${taskId}`), 'reload updated task');
	assert(
		updated.assignee === '双模验收负责人' && updated.taskStatus === 'IN_PROGRESS',
		'updated task values were not persisted',
		updated
	);

	const invalid = await authorized('/admin/demo/task', {
		method: 'POST',
		body: { ...createPayload, title: '' },
	});
	const invalidIsApiError = invalid.status === 200 && Number.isInteger(invalid.body?.code) && invalid.body.code !== 0;
	const invalidIsClientError = invalid.status >= 400 && invalid.status < 500;
	assert(invalid.status !== 0, 'invalid task request did not receive a response', invalid.body);
	assert(invalidIsApiError || invalidIsClientError, 'invalid task request was accepted', invalid.body);
	assert(
		invalid.body && typeof invalid.body === 'object' && typeof invalid.body.msg === 'string' && invalid.body.msg.length > 0,
		'invalid task response did not contain a structured error message',
		invalid.body
	);

	assertApi(await authorized('/admin/demo/task', { method: 'DELETE', body: [taskId] }), 'delete task');
	deleted = true;
	const deletedPage = assertApi(
		await authorized(`/admin/demo/task/page?current=1&size=10&title=${encodeURIComponent(title)}`),
		'query deleted task'
	);
	assert(!deletedPage.records?.some((record) => String(record.id) === taskId), 'deleted task is still visible', deletedPage);

	const auditLogs = await waitForAuditLogs(auditLogBaseline, title, taskId);
	const workflow = await verifyWorkflow(menu, currentUser);
	console.log(
		JSON.stringify(
			{
				mode,
				origin,
				login: login.status,
				username: currentUser.username,
				roles,
				permissions: permissions.filter((value) => value.startsWith('demo_task_')).sort(),
				menu: '/demo/task/index',
				crud: ['create', 'page', 'details', 'update', 'invalid-request', 'delete'],
				auditLogs,
				workflow,
			},
			null,
			2
		)
	);
} finally {
	if (token && taskId && !deleted) {
		await authorized('/admin/demo/task', { method: 'DELETE', body: [taskId] }).catch(() => undefined);
	}
}

async function verifyWorkflow(menu, currentUser) {
	const enabled = env.WORKFLOW_ENABLED === 'true';
	for (const path of ['/demo/leave/index', '/workflow/task/todo', '/workflow/task/done', '/workflow/process/instance']) {
		assert(containsMenuPath(menu, path) === enabled, `workflow menu visibility does not match enabled=${enabled}: ${path}`);
	}
	if (!enabled) {
		for (const path of ['/admin/demo/leave/page', '/admin/workflow/task/todo/page']) {
			const response = await authorized(path);
			assert(response.status === 404, `disabled workflow endpoint must be absent: ${path}`, response);
		}
		return { enabled: false, menusHidden: true, endpointsAbsent: true };
	}

	const actorIds = [];
	const processIds = [];
	const drafts = [];
	const suffix = `${Date.now()}-${randomBytes(3).toString('hex')}`;
	const baseline = new Set((await loadAuditLogs()).map(record => String(record.id)));
	const auditExpected = [];
	try {
		const reviewer = await createActor(`wf-r-${suffix}`);
		const outsider = await createActor(`wf-o-${suffix}`);
		const actor = (who, path, options = {}) => authorized(path, { ...options, headers: { Authorization: `Bearer ${who.token}` } });
		assertApi(await authorized('/admin/workflow/definition/deploy-demo', { method: 'POST' }), 'deploy leave model');
		assertApi(await authorized('/admin/workflow/definition/deploy-demo', { method: 'POST' }), 'redeploy leave model');
		const definitions = assertApi(await authorized('/admin/workflow/definition/list?processKey=demo_leave_approval'), 'list leave models');
		assert(definitions.length >= 1, 'leave model was not deployed');
		const startIntent = { requestId: randomUUID(), processKey: 'demo_leave_approval',
			businessKey: `start-retry-${suffix}`, title: `START重试-${suffix}`, variables: { approverId: reviewer.id } };
		const processCount = async () => Number(assertApi(await authorized('/admin/workflow/process/my/page?current=1&size=1&processKey=demo_leave_approval'), 'count own processes').total);
		const beforeStart = await processCount();
		const invalidStart = await authorized('/admin/workflow/process/start', { method: 'POST', body: { ...startIntent, requestId: undefined } });
		assertDenied(invalidStart, 'START without requestId');
		const invalidUnicodeStartId = randomUUID();
		const invalidUnicodeStart = await authorized('/admin/workflow/process/start', { method: 'POST', body: {
			...startIntent, requestId: invalidUnicodeStartId, variables: { approverId: reviewer.id, text: '\uD800' },
		} });
		assert(invalidUnicodeStart.status === 400, 'START must reject isolated Unicode surrogate with HTTP400', invalidUnicodeStart);
		const invalidUnicodeStartCommand = await authorized(`/admin/workflow/command/${invalidUnicodeStartId}`);
		assert(invalidUnicodeStartCommand.status === 404, 'invalid START created a command', invalidUnicodeStartCommand);
		assert(await processCount() === beforeStart, 'invalid START created a process');
		// Two simultaneous HTTP requests model a client/proxy retry while the first response is still pending.
		const concurrentStarts = await Promise.all([0, 1].map(() => authorized('/admin/workflow/process/start', { method: 'POST', body: startIntent })));
		const [originalStart, concurrentStart] = concurrentStarts.map(response => assertApi(response, 'concurrent start intention'));
		processIds.push(originalStart.processInstanceId);
		assert(JSON.stringify(concurrentStart) === JSON.stringify(originalStart), 'concurrent START returned different instances/snapshots');
		const repeatedStart = assertApi(await authorized('/admin/workflow/process/start', { method: 'POST', body: startIntent }), 'replay same start intention');
		assert(JSON.stringify(repeatedStart) === JSON.stringify(originalStart), 'START replay changed the saved response');
		assert(await processCount() === beforeStart + 1, 'START retry or rejected request created extra process instances');
		const startCommand = assertApi(await authorized(`/admin/workflow/command/${startIntent.requestId}`), 'query own start command');
		assert(startCommand.resultCode === 'SUCCESS' && startCommand.processInstanceId === originalStart.processInstanceId,
			'START command result missing', startCommand);
		assert(JSON.stringify(startCommand.response) === JSON.stringify(originalStart), 'command query changed the START wire snapshot');
		const startConflict = await authorized('/admin/workflow/process/start', { method: 'POST', body: { ...startIntent, title: 'changed intention' } });
		assert(startConflict.status === 409 && startConflict.body?.data?.errorCode === 'WORKFLOW_REQUEST_CONFLICT', 'START content conflict must be HTTP409', startConflict);
		const hiddenCommand = await actor(outsider, `/admin/workflow/command/${startIntent.requestId}`);
		assert(hiddenCommand.status === 404 && hiddenCommand.body?.data?.errorCode === 'WORKFLOW_COMMAND_NOT_FOUND', 'another actor can infer command existence', hiddenCommand);
		assertApi(await authorized(`/admin/workflow/process/cancel/${originalStart.processInstanceId}?requestId=${randomUUID()}`, { method: 'DELETE' }), 'finish START test process');
		const endedStart = assertApi(await authorized('/admin/workflow/process/start', { method: 'POST', body: startIntent }), 'replay START after process ended');
		assert(JSON.stringify(endedStart) === JSON.stringify(originalStart), 'ended process changed immutable START result');
		const currentStart = assertApi(await authorized(`/admin/workflow/process/details/${originalStart.processInstanceId}`), 'read current START process');
		assert(currentStart.status === 'terminated', 'START replay restarted an ended process');
		auditExpected.push({ title: '发起流程', marker: startIntent.requestId });
		const approvers = assertApi(await authorized(`/admin/demo/leave/approvers?name=${encodeURIComponent(reviewer.username)}`), 'find approver');
		assert(approvers.records.some(user => String(user.id) === reviewer.id), 'reviewer not returned as active approver');
		assert(approvers.records.every(user => !Object.hasOwn(user, 'password')), 'approver lookup exposed passwords');

		for (const outcome of ['approve', 'reject', 'cancel']) {
			const reason = `工作流验收-${suffix}-${outcome}`;
			const payload = { approverId: reviewer.id, startDate: '2026-10-01', endDate: '2026-10-02', reason };
			const draft = assertApi(await authorized('/admin/demo/leave', { method: 'POST', body: { ...payload, applicantId: outsider.id, leaveStatus: 'APPROVED' } }), 'create leave draft');
			drafts.push(String(draft.id));
			assert(draft.leaveStatus === 'DRAFT' && String(draft.applicantId) === String(currentUser.id), 'client replaced server-owned leave state', draft);
			assertDenied(await actor(outsider, `/admin/demo/leave/details/${draft.id}`), 'outsider read draft');
			assertDenied(await actor(outsider, `/admin/demo/leave/${draft.id}`, { method: 'PUT', body: payload }), 'outsider edit draft');
			assertApi(await authorized(`/admin/demo/leave/${draft.id}`, { method: 'PUT', body: { ...payload, reason: `${reason}-edited` } }), 'edit leave draft');
			auditExpected.push({ title: '新增请假申请', marker: reason }, { title: '修改请假申请', marker: String(draft.id) });
			const submitted = assertApi(await authorized(`/admin/demo/leave/${draft.id}/submit`, { method: 'POST' }), 'submit leave');
			assert(submitted.leaveStatus === 'IN_REVIEW' && submitted.processInstanceId, 'leave not bound to running process', submitted);
			processIds.push(submitted.processInstanceId);
			assertDenied(await authorized(`/admin/demo/leave/${draft.id}/submit`, { method: 'POST' }), 'repeat submitted leave');
			assertDenied(await authorized(`/admin/demo/leave/${draft.id}`, { method: 'PUT', body: payload }), 'edit submitted leave');
			assertDenied(await actor(outsider, `/admin/workflow/process/details/${submitted.processInstanceId}`), 'outsider read process');
			assertDenied(await actor(outsider, `/admin/demo/leave/details/${draft.id}`), 'outsider read submitted leave');
			const todo = assertApi(await actor(reviewer, '/admin/workflow/task/todo/page?current=1&size=100'), 'reviewer todo');
			const task = todo.records.find(task => task.processInstanceId === submitted.processInstanceId);
			assert(task?.taskId, 'review task missing from real assignee todo', todo);
			if (outcome === 'approve') {
				const invalidUnicodeCompleteId = randomUUID();
				const invalidUnicodeComplete = await actor(reviewer, '/admin/workflow/task/complete', { method: 'POST',
					body: { requestId: invalidUnicodeCompleteId, taskId: task.taskId,
						variables: { nested: ['\uD801'] } } });
				assert(invalidUnicodeComplete.status === 400, 'COMPLETE must reject isolated Unicode surrogate with HTTP400', invalidUnicodeComplete);
				const invalidUnicodeCommand = await actor(reviewer, `/admin/workflow/command/${invalidUnicodeCompleteId}`);
				assert(invalidUnicodeCommand.status === 404, 'invalid COMPLETE created a command', invalidUnicodeCommand);
				const stillTodo = assertApi(await actor(reviewer, '/admin/workflow/task/todo/page?current=1&size=100'), 'reviewer todo after invalid COMPLETE');
				assert(stillTodo.records.some(record => record.taskId === task.taskId), 'invalid COMPLETE removed task', stillTodo);
			}
			assertDenied(await authorized('/admin/workflow/task/complete', { method: 'POST', body: { requestId: randomUUID(), taskId: task.taskId, approvalComment: 'self approval forbidden' } }), 'non-assignee approval');
			assertApi(await actor(reviewer, `/admin/demo/leave/details/${draft.id}`), 'reviewer read business data');
			if (outcome === 'cancel') {
				const suspendRequestId = randomUUID();
				assertApi(await authorized(`/admin/workflow/process/suspend/${submitted.processInstanceId}?requestId=${suspendRequestId}`, { method: 'PUT' }), 'suspend process');
				assertApi(await authorized(`/admin/workflow/process/suspend/${submitted.processInstanceId}?requestId=${suspendRequestId}`, { method: 'PUT' }), 'replay process suspension');
				const activateRequestId = randomUUID();
				assertApi(await authorized(`/admin/workflow/process/activate/${submitted.processInstanceId}?requestId=${activateRequestId}`, { method: 'PUT' }), 'activate process');
				assertApi(await authorized(`/admin/workflow/process/activate/${submitted.processInstanceId}?requestId=${activateRequestId}`, { method: 'PUT' }), 'replay process activation');
				const cancelRequestId = randomUUID();
				const cancelPath = `/admin/workflow/process/cancel/${submitted.processInstanceId}?requestId=${cancelRequestId}&reason=${encodeURIComponent('验收取消')}`;
				assertApi(await authorized(cancelPath, { method: 'DELETE' }), 'cancel own process');
				assertApi(await authorized(cancelPath, { method: 'DELETE' }), 'replay process cancellation');
				for (const [requestId, operation] of [[suspendRequestId, 'SUSPEND'], [activateRequestId, 'ACTIVATE'], [cancelRequestId, 'TERMINATE']]) {
					const command = assertApi(await authorized(`/admin/workflow/command/${requestId}`), `query ${operation} command`);
					assert(command.operation === operation && command.processInstanceId === submitted.processInstanceId,
						`${operation} command result missing`, command);
				}
				const changedCancel = await authorized(`/admin/workflow/process/cancel/${submitted.processInstanceId}?requestId=${cancelRequestId}&reason=${encodeURIComponent('修改取消原因')}`, { method: 'DELETE' });
				assert(changedCancel.status === 409 && changedCancel.body?.data?.errorCode === 'WORKFLOW_REQUEST_CONFLICT',
					'changed cancellation content must conflict', changedCancel);
			} else {
				const taskRequestId = randomUUID();
				const body = outcome === 'approve'
					? { requestId: taskRequestId, taskId: task.taskId, approvalComment: '同意请假' }
					: { requestId: taskRequestId, taskId: task.taskId, rejectReason: '拒绝并结束' };
				const taskPath = `/admin/workflow/task/${outcome === 'approve' ? 'complete' : 'reject'}`;
				const concurrentWrites = await Promise.all([0, 1].map(() => actor(reviewer, taskPath, { method: 'POST', body })));
				concurrentWrites.forEach(response => assertApi(response, `concurrent ${outcome} intention`));
				assertApi(await actor(reviewer, taskPath, { method: 'POST', body }), `replay ${outcome} intention`);
				auditExpected.push({ title: outcome === 'approve' ? '完成任务' : '驳回任务', marker: task.taskId });
				const command = assertApi(await actor(reviewer, `/admin/workflow/command/${taskRequestId}`), `query ${outcome} command`);
				assert(command.operation === (outcome === 'approve' ? 'COMPLETE' : 'REJECT')
					&& command.processInstanceId === submitted.processInstanceId, `${outcome} command result missing`, command);
				const hiddenTaskCommand = await actor(outsider, `/admin/workflow/command/${taskRequestId}`);
				assert(hiddenTaskCommand.status === 404 && hiddenTaskCommand.body?.data?.errorCode === 'WORKFLOW_COMMAND_NOT_FOUND',
					'another actor can infer task command existence', hiddenTaskCommand);
				const changedBody = outcome === 'approve'
					? { ...body, approvalComment: '修改审批意见' }
					: { ...body, rejectReason: '修改拒绝原因' };
				const changedTask = await actor(reviewer, taskPath, { method: 'POST', body: changedBody });
				assert(changedTask.status === 409 && changedTask.body?.data?.errorCode === 'WORKFLOW_REQUEST_CONFLICT',
					`changed ${outcome} content must conflict`, changedTask);
				const otherPath = outcome === 'approve' ? '/admin/workflow/task/reject' : '/admin/workflow/task/complete';
				const otherBody = outcome === 'approve'
					? { requestId: taskRequestId, taskId: task.taskId, rejectReason: '改为拒绝' }
					: { requestId: taskRequestId, taskId: task.taskId, approvalComment: '改为通过' };
				const changedOperation = await actor(reviewer, otherPath, { method: 'POST', body: otherBody });
				assert(changedOperation.status === 409 && changedOperation.body?.data?.errorCode === 'WORKFLOW_REQUEST_CONFLICT',
					'using one requestId for another operation must conflict', changedOperation);
				const repeatedTask = await actor(reviewer, '/admin/workflow/task/complete',
					{ method: 'POST', body: { requestId: randomUUID(), taskId: task.taskId } });
				assert(repeatedTask.status === 409 && repeatedTask.body?.data?.errorCode === 'WORKFLOW_OPERATION_CONFLICT',
					'a different terminal request must report operation conflict', repeatedTask);
				const history = assertApi(await authorized(`/admin/demo/leave/${draft.id}/history`), 'leave approval history');
				const matchingHistory = history.filter(record => record.taskId === task.taskId && record.approvalType === outcome
					&& String(record.approvalUserId) === reviewer.id);
				assert(matchingHistory.length === 1, 'retry created duplicate approval history', history);
				const done = assertApi(await actor(reviewer, '/admin/workflow/task/done/page?current=1&size=100'), 'reviewer done');
				assert(done.records.some(record => record.taskId === task.taskId && record.endTime), 'done task missing end time');
			}
			const expected = { approve: 'APPROVED', reject: 'REJECTED', cancel: 'CANCELED' }[outcome];
			const final = await poll(async () => assertApi(await authorized(`/admin/demo/leave/details/${draft.id}`), 'read final leave'), value => value.leaveStatus === expected, 'automatic business writeback');
			assert(final.endedAt, 'business terminal time missing', final);
			assertApi(await authorized(`/admin/demo/leave/${draft.id}/refresh`, { method: 'POST' }), 'reconcile final state');
			auditExpected.push({ title: '提交请假申请', marker: String(draft.id) });
		}
		const disposable = assertApi(await authorized('/admin/demo/leave', { method: 'POST', body: { approverId: reviewer.id, startDate: '2026-10-01', endDate: '2026-10-01', reason: `delete-${suffix}` } }), 'create disposable draft');
		drafts.push(String(disposable.id));
		assertApi(await authorized(`/admin/demo/leave/${disposable.id}`, { method: 'DELETE' }), 'delete own draft');
		auditExpected.push({ title: '删除请假申请', marker: String(disposable.id) });
		const invalid = await authorized('/admin/demo/leave', { method: 'POST', body: { approverId: reviewer.id, startDate: '2026-10-03', endDate: '2026-10-01', reason: 'invalid dates' } });
		assertDenied(invalid, 'invalid leave dates');
		const audit = await poll(loadAuditLogs, records => auditExpected.every(expected => records.some(record => !baseline.has(String(record.id)) && record.title === expected.title && String(record.params).includes(expected.marker))), 'leave write audit');
		return { enabled: true, states: ['APPROVED', 'REJECTED', 'CANCELED'], startRetryAndConflict: true, assigneeAndDataPermissions: true, history: true, auditLogs: audit.filter(record => !baseline.has(String(record.id)) && record.title.includes('请假')).map(record => ({ id: String(record.id), title: record.title })) };
	} finally {
		for (const id of processIds) await authorized(`/admin/workflow/process/cancel/${id}?requestId=${randomUUID()}`, { method: 'DELETE' }).catch(() => undefined);
		for (const id of drafts) await authorized(`/admin/demo/leave/${id}`, { method: 'DELETE' }).catch(() => undefined);
		if (actorIds.length) await authorized('/admin/user', { method: 'DELETE', body: actorIds }).catch(() => undefined);
	}

	async function createActor(actorUsername) {
		const actorPassword = randomBytes(18).toString('base64url');
		assertApi(await authorized('/admin/user', { method: 'POST', body: { username: actorUsername, name: actorUsername, password: actorPassword, role: [1], post: [], deptId: currentUser.deptId || 1, lockFlag: '0' } }), 'create acceptance actor');
		const users = assertApi(await authorized(`/admin/user/page?current=1&size=20&username=${encodeURIComponent(actorUsername)}`), 'find acceptance actor');
		const user = users.records.find(user => user.username === actorUsername);
		assert(user?.id, 'acceptance actor not found');
		actorIds.push(String(user.id));
		const body = new URLSearchParams({ username: actorUsername, password: encryptPassword(actorPassword, encodeKey), grant_type: 'password', scope: 'server' });
		const response = await request('/auth/oauth2/token', { method: 'POST', headers: { Authorization: `Basic ${Buffer.from(`bixi:${clientSecret}`).toString('base64')}`, 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString() });
		assert(response.status === 200 && response.body?.access_token, 'acceptance actor login failed', { status: response.status });
		return { id: String(user.id), username: actorUsername, token: response.body.access_token };
	}
}

function assertDenied(response, action) {
	assert((response.status >= 400 && response.status < 500) || (response.status === 200 && Number.isInteger(response.body?.code) && response.body.code !== 0), `${action} unexpectedly succeeded or failed without a client response`, response);
}

async function poll(read, ready, description) {
	for (let attempt = 0; attempt < 30; attempt += 1) {
		const value = await read();
		if (ready(value)) return value;
		await sleep(500);
	}
	throw new Error(`timed out waiting for ${description}`);
}

async function waitForAuditLogs(baselineIds, taskTitle, deletedTaskId) {
	const expected = [
		{ title: '新增示例任务', method: 'POST', marker: taskTitle },
		{ title: '修改示例任务', method: 'PUT', marker: taskTitle },
		{ title: '删除示例任务', method: 'DELETE', marker: deletedTaskId },
	];
	for (let attempt = 1; attempt <= 30; attempt += 1) {
		const records = await loadAuditLogs();
		const currentRunRecords = records.filter((record) => !baselineIds.has(String(record.id)));
		const matches = expected.map((item) =>
			currentRunRecords.find(
				(record) =>
					record.title === item.title &&
					record.method === item.method &&
					String(record.requestUri || '').endsWith('/demo/task') &&
					String(record.params || '').includes(item.marker)
			)
		);
		if (matches.every(Boolean)) {
			return matches.map(({ id, title, method, requestUri }) => ({ id: String(id), title, method, requestUri }));
		}
		await sleep(1000);
	}
	throw new Error(`operation logs were not persisted: ${createdTitles.join(', ')}`);
}

async function loadAuditLogs() {
	// Repeated acceptance runs retain audit history; inspect the newest stable ID window.
	const response = await authorized('/admin/log/page?current=1&size=500&descs=id');
	assert(response.status === 200, `load operation logs returned HTTP ${response.status}`, response.body);
	assert(response.body?.code === 0, 'load operation logs returned an API error', response.body);
	return response.body.data?.records || [];
}

async function waitForHealth(url, predicate, name) {
	for (let attempt = 1; attempt <= 60; attempt += 1) {
		const response = await fetchResponse(url);
		if (predicate(response)) return;
		await sleep(1000);
	}
	throw new Error(`${name} health check did not pass: ${url}`);
}

async function authorized(path, options = {}) {
	return request(path, {
		...options,
		headers: {
			Authorization: `Bearer ${token}`,
			...options.headers,
		},
	});
}

async function request(path, options = {}) {
	const headers = { Accept: 'application/json', ...options.headers };
	let body = options.body;
	if (body && typeof body !== 'string') {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(body);
	}
	return fetchResponse(`${apiBase}${path}`, { ...options, headers, body });
}

async function fetchResponse(url, options = {}) {
	try {
		const response = await fetch(url, { ...options, signal: AbortSignal.timeout(10_000) });
		const text = await response.text();
		let body = text;
		if (text) {
			try {
				body = JSON.parse(text);
			} catch {
				// Health endpoints may intentionally return plain text.
			}
		}
		return { status: response.status, body };
	} catch (error) {
		return { status: 0, body: String(error) };
	}
}

function assertApi(response, action) {
	assert(response.status === 200, `${action} returned HTTP ${response.status}`, response.body);
	assert(response.body?.code === 0, `${action} returned an API error`, response.body);
	return response.body.data;
}

function containsMenuPath(value, expectedPath) {
	if (Array.isArray(value)) return value.some((item) => containsMenuPath(item, expectedPath));
	if (!value || typeof value !== 'object') return false;
	if (value.path === expectedPath) return true;
	return Object.values(value).some((item) => containsMenuPath(item, expectedPath));
}

function encryptPassword(value, keyword) {
	const key = createHash('sha256').update(keyword, 'utf8').digest();
	const iv = createHash('md5').update(`${keyword}bixi-iv-salt-2025`, 'utf8').digest();
	const cipher = createCipheriv('aes-256-cbc', key, iv);
	return Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]).toString('base64');
}

function formatDate(value) {
	const year = value.getFullYear();
	const month = String(value.getMonth() + 1).padStart(2, '0');
	const day = String(value.getDate()).padStart(2, '0');
	return `${year}-${month}-${day}`;
}

function required(name) {
	const value = env[name];
	if (!value || value === 'GENERATE_ON_FIRST_RUN') throw new Error(`missing generated runtime value: ${name}`);
	return value;
}

function assert(condition, message, details) {
	if (condition) return;
	const suffix = details === undefined ? '' : `\n${JSON.stringify(details, null, 2)}`;
	throw new Error(`${message}${suffix}`);
}

async function readEnv(path) {
	const result = {};
	const content = await readFile(path, 'utf8');
	for (const line of content.split(/\r?\n/)) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith('#')) continue;
		const separator = trimmed.indexOf('=');
		if (separator < 1) continue;
		const key = trimmed.slice(0, separator).trim();
		let value = trimmed.slice(separator + 1).trim();
		if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith('"') && value.endsWith('"'))) {
			value = value.slice(1, -1);
		}
		result[key] = value;
	}
	return result;
}

function sleep(milliseconds) {
	return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}
