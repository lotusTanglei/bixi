#!/usr/bin/env node

import { createCipheriv, createHash, randomBytes, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

import { generateStrongPassword } from './acceptance-password.mjs';
import { extractWorkbookText } from './tenant-isolation-support.mjs';

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
	for (const permission of [
		'demo_task_view', 'demo_task_add', 'demo_task_edit', 'demo_task_del',
		'acceptance_dict_aggregate_view', 'acceptance_dict_aggregate_add',
		'acceptance_dict_aggregate_edit', 'acceptance_dict_aggregate_del',
		'acceptance_dict_aggregate_import', 'acceptance_dict_aggregate_export',
		'acceptance_sys_dict_item_add', 'acceptance_sys_dict_item_edit',
		'acceptance_sys_dict_item_del', 'acceptance_public_param_view',
		'acceptance_public_param_add', 'acceptance_public_param_edit',
		'acceptance_public_param_del', 'acceptance_public_param_import',
		'acceptance_public_param_export',
	]) {
		assert(permissions.includes(permission), `missing permission: ${permission}`, permissions);
	}

	const menu = assertApi(await authorized('/admin/menu'), 'load menu');
	assert(containsMenuPath(menu, '/demo/task/index'), 'sample task menu is not visible', menu);
	assert(containsMenuPath(menu, '/acceptance/dictAggregate/index'), 'Acceptance dictionary menu is not visible', menu);
	assert(containsMenuPath(menu, '/acceptance/publicParam/index'), 'Acceptance public parameter menu is not visible', menu);
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
	const acceptance = await verifyAcceptanceResources();
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
				acceptance,
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

async function verifyAcceptanceResources() {
	const suffix = `${mode}-${Date.now()}-${randomBytes(3).toString('hex')}`;
	const dictPath = '/admin/dictAggregate';
	const paramPath = '/admin/publicParam';
	const auditBaseline = new Set((await loadAuditLogs()).map((record) => String(record.id)));
	const auditExpected = [];
	let dictId;
	let publicParamId;
	const dictMarker = `acceptance-dict-${suffix}`;
	const paramMarker = `acceptance-param-${suffix}`;
	try {
		const dictPayload = {
			type: dictMarker,
			name: `Acceptance 字典 ${suffix}`,
			description: 'black-box acceptance dictionary',
			sn: 1,
			systemFlag: '0',
			children: [
				{ value: `${dictMarker}-one`, label: 'One', dictType: dictMarker, description: 'first', sn: 1 },
				{ value: `${dictMarker}-two`, label: 'Two', dictType: dictMarker, description: 'second', sn: 2 },
			],
		};
		assertDenied(await authorized(dictPath, {
			method: 'POST',
			body: { ...dictPayload, type: '', name: '', children: [{ value: '', label: '' }] },
		}), 'invalid Acceptance dictionary create');
		assertApi(await authorized(dictPath, { method: 'POST', body: dictPayload }), 'create Acceptance dictionary');
		auditExpected.push({ title: '新增字典表', method: 'POST', path: dictPath, marker: dictMarker });
		const dictPage = assertApi(await authorized(`${dictPath}/page?current=1&size=20&type=${encodeURIComponent(dictMarker)}`), 'query Acceptance dictionary');
		const dict = dictPage.records?.find((record) => record.type === dictMarker);
		assert(dict?.id, 'created Acceptance dictionary was not returned by its filter', dictPage);
		dictId = String(dict.id);
		const dictDetails = assertApi(await authorized(`${dictPath}/details/${dictId}`), 'load Acceptance dictionary details');
		assert(dictDetails.type === dictMarker && dictDetails.children?.length === 2, 'Acceptance dictionary details are incomplete', dictDetails);

		const dictUpdate = {
			...dictDetails,
			name: `${dictPayload.name} updated`,
			children: [
				{ ...dictDetails.children[0], value: `${dictMarker}-updated`, label: 'Updated' },
				{ value: `${dictMarker}-three`, label: 'Three', dictType: dictMarker, description: 'third', sn: 3 },
			],
		};
		assertApi(await authorized(dictPath, { method: 'PUT', body: dictUpdate }), 'update Acceptance dictionary');
		auditExpected.push({ title: '修改字典表', method: 'PUT', path: dictPath, marker: dictMarker });
		const updatedDict = assertApi(await authorized(`${dictPath}/details/${dictId}`), 'reload Acceptance dictionary');
		assert(updatedDict.name === dictUpdate.name && updatedDict.children?.some((child) => child.value === `${dictMarker}-three`), 'Acceptance dictionary update was not persisted', updatedDict);

		const invalidDictUpdate = await authorized(dictPath, {
			method: 'PUT',
			body: { ...dictUpdate, name: 'must-not-persist', children: [{ ...updatedDict.children[0], dictId: '999999999999999999' }] },
		});
		assertDenied(invalidDictUpdate, 'invalid Acceptance dictionary child relationship');
		const afterInvalidDict = assertApi(await authorized(`${dictPath}/details/${dictId}`), 'verify Acceptance dictionary rollback');
		assert(afterInvalidDict.name === updatedDict.name, 'invalid Acceptance dictionary update changed the parent', afterInvalidDict);

		assertDenied(await authorized(`${dictPath}/details/999999999999999999`), 'unknown Acceptance dictionary details');
		assertDenied(await authorized(dictPath, { method: 'DELETE', body: [] }), 'empty Acceptance dictionary delete');
		const dictExport = await authorized(`${dictPath}/export?type=${encodeURIComponent(dictMarker)}`, { expectBinary: true });
		const dictExportText = await assertWorkbook(dictExport, 'export Acceptance dictionary');
		assert(dictExportText.includes(dictMarker), 'Acceptance dictionary export omitted the filter marker');
		const invalidDictImport = await invalidImport(`${dictPath}/import`, 'dict-invalid.xlsx');
		assert(invalidDictImport.code === 'INVALID_FILE' && invalidDictImport.success === false, 'invalid Acceptance dictionary import was accepted', invalidDictImport);

		const deletedDictId = dictId;
		assertApi(await authorized(dictPath, { method: 'DELETE', body: [dictId] }), 'delete Acceptance dictionary');
		auditExpected.push({ title: '删除字典表', method: 'DELETE', path: dictPath, marker: deletedDictId });
		dictId = undefined;
		const deletedDictPage = assertApi(await authorized(`${dictPath}/page?current=1&size=20&type=${encodeURIComponent(dictMarker)}`), 'query deleted Acceptance dictionary');
		assert(!deletedDictPage.records?.some((record) => String(record.id) === deletedDictId), 'deleted Acceptance dictionary is still visible', deletedDictPage);

		const paramPayload = {
			name: `Acceptance parameter ${suffix}`,
			key: paramMarker,
			value: 'initial',
			validateCode: 'acceptance',
			type: '2',
			systemFlag: '0',
			sn: 1,
		};
		assertDenied(await authorized(paramPath, {
			method: 'POST',
			body: { ...paramPayload, name: '', key: '', value: '' },
		}), 'invalid Acceptance public parameter create');
		assertApi(await authorized(paramPath, { method: 'POST', body: paramPayload }), 'create Acceptance public parameter');
		auditExpected.push({ title: '新增公共参数配置表', method: 'POST', path: paramPath, marker: paramMarker });
		const paramPage = assertApi(await authorized(`${paramPath}/page?current=1&size=20&key=${encodeURIComponent(paramMarker)}`), 'query Acceptance public parameter');
		const param = paramPage.records?.find((record) => record.key === paramMarker);
		assert(param?.id, 'created Acceptance public parameter was not returned by its filter', paramPage);
		publicParamId = String(param.id);
		const paramDetails = assertApi(await authorized(`${paramPath}/details/${publicParamId}`), 'load Acceptance public parameter details');
		assert(paramDetails.key === paramMarker && paramDetails.value === 'initial', 'Acceptance public parameter details are incomplete', paramDetails);
		assertApi(await authorized(paramPath, { method: 'PUT', body: { ...paramDetails, value: 'updated' } }), 'update Acceptance public parameter');
		auditExpected.push({ title: '修改公共参数配置表', method: 'PUT', path: paramPath, marker: paramMarker });
		const updatedParam = assertApi(await authorized(`${paramPath}/details/${publicParamId}`), 'reload Acceptance public parameter');
		assert(updatedParam.value === 'updated', 'Acceptance public parameter update was not persisted', updatedParam);
		assertDenied(await authorized(`${paramPath}/details/999999999999999999`), 'unknown Acceptance public parameter details');
		assertDenied(await authorized(paramPath, { method: 'DELETE', body: [] }), 'empty Acceptance public parameter delete');
		const paramExport = await authorized(`${paramPath}/export?key=${encodeURIComponent(paramMarker)}`, { expectBinary: true });
		const paramExportText = await assertWorkbook(paramExport, 'export Acceptance public parameter');
		assert(paramExportText.includes(paramMarker), 'Acceptance public parameter export omitted the key');
		const invalidParamImport = await invalidImport(`${paramPath}/import`, 'param-invalid.xlsx');
		assert(invalidParamImport.code === 'INVALID_FILE' && invalidParamImport.success === false, 'invalid Acceptance public parameter import was accepted', invalidParamImport);
		const deletedPublicParamId = publicParamId;
		assertApi(await authorized(paramPath, { method: 'DELETE', body: [publicParamId] }), 'delete Acceptance public parameter');
		auditExpected.push({ title: '删除公共参数配置表', method: 'DELETE', path: paramPath, marker: deletedPublicParamId });
		publicParamId = undefined;
		return {
			resources: ['dictAggregate', 'publicParam'],
			checks: ['menu', 'permissions', 'crud', 'filters', 'details', 'invalid-request', 'empty-delete', 'unknown-id', 'invalid-import', 'xlsx-export'],
			auditLogs: await waitForAcceptanceAuditLogs(auditBaseline, auditExpected),
		};
	}
	finally {
		if (dictId) await authorized(dictPath, { method: 'DELETE', body: [dictId] }).catch(() => undefined);
		if (publicParamId) await authorized(paramPath, { method: 'DELETE', body: [publicParamId] }).catch(() => undefined);
	}
}

async function invalidImport(path, filename) {
	const body = new FormData();
	body.append('file', new Blob(['this is not an Excel archive'], { type: 'application/octet-stream' }), filename);
	return assertApi(await authorized(path, { method: 'POST', body }), `invalid import ${filename}`);
}

async function assertWorkbook(response, action) {
	assert(response.status === 200 && response.binary?.length > 4, `${action} did not return a binary workbook`, response);
	return extractWorkbookText(response.binary);
}

async function waitForAcceptanceAuditLogs(baselineIds, expected) {
	for (let attempt = 1; attempt <= 30; attempt += 1) {
		const records = (await loadAuditLogs()).filter((record) => !baselineIds.has(String(record.id)));
		const matches = expected.map((item) => records.find((record) =>
			record.title === item.title && record.method === item.method
			&& String(record.requestUri || '').endsWith(item.path.replace(/^\/admin/, ''))
			&& String(record.params || '').includes(item.marker)));
		if (matches.every(Boolean)) {
			return matches.map(({ id, title, method, requestUri }) => ({ id: String(id), title, method, requestUri }));
		}
		await sleep(1000);
	}
	throw new Error(`Acceptance operation logs were not persisted: ${expected.map((item) => item.title).join(', ')}`);
}

async function verifyWorkflow(menu, currentUser) {
	const enabled = env.WORKFLOW_ENABLED === 'true';
	if (enabled) {
		assert(env.BIXI_RELIABLE_ENABLED === 'true', 'enabled workflow requires BIXI_RELIABLE_ENABLED=true');
		if (mode === 'cloud') {
			assert(env.BIXI_RELIABLE_RABBIT_ENABLED === 'true',
				'cloud workflow requires BIXI_RELIABLE_RABBIT_ENABLED=true');
		}
	}
	for (const path of ['/demo/leave/index', '/workflow/task/todo', '/workflow/task/done', '/workflow/process/instance', '/workflow/form/index']) {
		assert(containsMenuPath(menu, path) === enabled, `workflow menu visibility does not match enabled=${enabled}: ${path}`);
	}
	if (!enabled) {
		for (const path of ['/admin/demo/leave/page', '/admin/workflow/task/todo/page', '/admin/workflow/form/list']) {
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
		const formVersioning = await verifyWorkflowFormVersioning(suffix, processIds);
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
		assertDenied(await authorized('/admin/workflow/process/start', { method: 'POST', body: startIntent }),
			'business workflow must reject public START');
		const rejectedStartCommand = await authorized(`/admin/workflow/command/${startIntent.requestId}`);
		assert(rejectedStartCommand.status === 404, 'rejected public START created a command', rejectedStartCommand);
		assert(await processCount() === beforeStart, 'rejected public START created a process');
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
			const submitRequestId = randomUUID();
			const submitPath = `/admin/demo/leave/${draft.id}/submit?requestId=${submitRequestId}`;
			const concurrentSubmissions = await Promise.all([0, 1].map(() => authorized(submitPath, { method: 'POST' })));
			const [submittedResponse, replayedSubmission] = concurrentSubmissions.map(response => assertApi(response, 'submit leave'));
			assert(JSON.stringify(replayedSubmission) === JSON.stringify(submittedResponse), 'leave submit replay changed the saved response');
			assert(JSON.stringify(assertApi(await authorized(submitPath, { method: 'POST' }), 'replay leave submit'))
				=== JSON.stringify(submittedResponse), 'sequential leave submit replay changed the saved response');
				const submitted = await poll(
						async () => assertApi(await authorized(`/admin/demo/leave/details/${draft.id}`), 'read reliable leave binding'),
						value => value.leaveStatus === 'IN_REVIEW' && value.processInstanceId,
						'reliable leave workflow binding'
					);
			assert(submitted.leaveStatus === 'IN_REVIEW' && submitted.processInstanceId, 'leave not bound to running process', submitted);
			processIds.push(submitted.processInstanceId);
			assertDenied(await authorized(`/admin/demo/leave/${draft.id}/submit?requestId=${randomUUID()}`, { method: 'POST' }),
				'repeat submitted leave with a new requestId');
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
				const recoveryRows = assertApi(await authorized('/admin/upms/recovery/reconcile?limit=100'), 'query UPMS recovery reconciliation');
				const currentRecovery = recoveryRows.find(row => String(row.businessId) === String(draft.id));
				assert(currentRecovery, 'UPMS recovery reconciliation did not return the submitted leave', recoveryRows);
				assert(['MATCHED', 'PENDING_DELIVERY', 'FAILED_DELIVERY'].includes(currentRecovery.classification),
					'UPMS recovery reconciliation returned an unknown classification', currentRecovery);
			auditExpected.push({ title: '提交请假申请', marker: String(draft.id) });
		}
		const disposable = assertApi(await authorized('/admin/demo/leave', { method: 'POST', body: { approverId: reviewer.id, startDate: '2026-10-01', endDate: '2026-10-01', reason: `delete-${suffix}` } }), 'create disposable draft');
		drafts.push(String(disposable.id));
		assertApi(await authorized(`/admin/demo/leave/${disposable.id}`, { method: 'DELETE' }), 'delete own draft');
		auditExpected.push({ title: '删除请假申请', marker: String(disposable.id) });
		const invalid = await authorized('/admin/demo/leave', { method: 'POST', body: { approverId: reviewer.id, startDate: '2026-10-03', endDate: '2026-10-01', reason: 'invalid dates' } });
		assertDenied(invalid, 'invalid leave dates');
		const audit = await poll(loadAuditLogs, records => auditExpected.every(expected => records.some(record =>
			!baseline.has(String(record.id))
			&& record.title === expected.title
			&& `${record.requestUri || ''}\n${record.params || ''}`.includes(expected.marker)
		)), 'leave write audit');
		return { enabled: true, states: ['APPROVED', 'REJECTED', 'CANCELED'], startRetryAndConflict: true,
			assigneeAndDataPermissions: true, history: true, formVersioning,
			auditLogs: audit.filter(record => !baseline.has(String(record.id)) && record.title.includes('请假'))
				.map(record => ({ id: String(record.id), title: record.title })) };
	} finally {
		for (const id of processIds) await authorized(`/admin/workflow/process/cancel/${id}?requestId=${randomUUID()}`, { method: 'DELETE' }).catch(() => undefined);
		for (const id of drafts) await authorized(`/admin/demo/leave/${id}`, { method: 'DELETE' }).catch(() => undefined);
		if (actorIds.length) await authorized('/admin/user', { method: 'DELETE', body: actorIds }).catch(() => undefined);
	}

	async function createActor(actorUsername) {
		const actorPassword = generateStrongPassword();
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

async function verifyWorkflowFormVersioning(suffix, processIds) {
	const processKey = 'stage2_form_acceptance';
	const allowed = new Set((env.WORKFLOW_PUBLIC_START_MODELS || '').split(',').map(value => value.trim()).filter(Boolean));
	assert(allowed.size === 1 && allowed.has(processKey),
		`enabled acceptance requires WORKFLOW_PUBLIC_START_MODELS=${processKey}`, [...allowed]);

	const formKey = `stage2_form_${suffix.replaceAll('-', '_')}`;
	assertApi(await authorized('/admin/workflow/form', {
		method: 'POST',
		body: { formKey, formName: `Stage 2 form ${suffix}`, formType: 'approval', category: 'acceptance' },
	}), 'create workflow acceptance form');
	const formPage = assertApi(await authorized(
		`/admin/workflow/form/list?current=1&size=10&formKey=${encodeURIComponent(formKey)}`),
	'load workflow acceptance form');
	const form = formPage.records?.find(candidate => candidate.formKey === formKey);
	assert(form?.id, 'created workflow form was not returned', formPage);

	const schemaV1 = JSON.stringify({ widgetList: [
		{ type: 'input', options: { name: 'legacyNote', label: 'Legacy note', required: true } },
	] });
	await createAndActivateFormVersion(form.id, 1, schemaV1, 'acceptance v1');
	const v1 = await findFormVersion(form.id, 1);
	const definitionV1 = await deployAcceptanceDefinition(formKey, `${suffix}-v1`);
	const oldProcess = assertApi(await authorized('/admin/workflow/process/start', {
		method: 'POST',
		body: { requestId: randomUUID(), processKey, businessKey: `form-old-${suffix}`,
			title: `Form v1 ${suffix}`, formId: form.id,
			formDataJson: JSON.stringify({ legacyNote: 'kept-on-v1' }) },
	}), 'start v1-bound workflow');
	assert(oldProcess?.processInstanceId, 'v1-bound workflow did not return an instance', oldProcess);
	processIds.push(oldProcess.processInstanceId);

	const schemaV2 = JSON.stringify({ widgetList: [
		{ type: 'input', options: { name: 'legacyNote', label: 'Legacy note', required: true } },
		{ type: 'input', options: { name: 'newNote', label: 'New note', required: true } },
	] });
	await createAndActivateFormVersion(form.id, 2, schemaV2, 'acceptance v2');
	const v2 = await findFormVersion(form.id, 2);
	const definitionV2 = await deployAcceptanceDefinition(formKey, `${suffix}-v2`);
	assert(Number(definitionV2.version) > Number(definitionV1.version),
		'redeploying the same process key did not create a newer definition', { definitionV1, definitionV2 });

	const oldRender = assertApi(await authorized(
		`/admin/workflow/form/data/process/${encodeURIComponent(oldProcess.processInstanceId)}`),
	'render old workflow form after v2 deployment');
	assert(String(oldRender.formVersionId) === String(v1.id) && oldRender.version === 1,
		'old workflow instance drifted away from form v1', oldRender);
	assert(JSON.parse(oldRender.dataJson).legacyNote === 'kept-on-v1',
		'old workflow instance lost its v1 data', oldRender);
	assert(!oldRender.schemaJson.includes('newNote'), 'old workflow instance received a v2-only field', oldRender);

	const newProcess = assertApi(await authorized('/admin/workflow/process/start', {
		method: 'POST',
		body: { requestId: randomUUID(), processKey, businessKey: `form-new-${suffix}`,
			title: `Form v2 ${suffix}`, formId: form.id,
			formDataJson: JSON.stringify({ legacyNote: 'new-on-v2', newNote: 'required-on-v2' }) },
	}), 'start v2-bound workflow');
	assert(newProcess?.processInstanceId, 'v2-bound workflow did not return an instance', newProcess);
	processIds.push(newProcess.processInstanceId);
	const newRender = assertApi(await authorized(
		`/admin/workflow/form/data/process/${encodeURIComponent(newProcess.processInstanceId)}`),
	'render new workflow form');
	assert(String(newRender.formVersionId) === String(v2.id) && newRender.version === 2,
		'new workflow instance did not bind form v2', newRender);
	assert(JSON.parse(newRender.dataJson).newNote === 'required-on-v2'
		&& newRender.schemaJson.includes('newNote'), 'new workflow instance did not use the v2 contract', newRender);

	return { processKey, formKey, definitionVersions: [definitionV1.version, definitionV2.version],
		formVersions: [v1.version, v2.version], oldInstanceFrozen: true, newInstanceUsesV2: true };
}

async function createAndActivateFormVersion(formId, version, schemaJson, changeLog) {
	assertApi(await authorized('/admin/workflow/form/version', {
		method: 'POST', body: { formId, schemaJson, changeLog },
	}), `create workflow form v${version}`);
	assertApi(await authorized(`/admin/workflow/form/version/activate/${formId}/${version}`, { method: 'PUT' }),
		`activate workflow form v${version}`);
}

async function findFormVersion(formId, version) {
	const versions = assertApi(await authorized(`/admin/workflow/form/version/list/${formId}`),
		`list workflow form versions for v${version}`);
	const found = versions.find(candidate => Number(candidate.version) === version);
	assert(found?.id && found.isActive === '1', `workflow form v${version} is not active`, versions);
	return found;
}

async function deployAcceptanceDefinition(formKey, name) {
	const bpmn = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="https://bixi.dev/acceptance">
  <process id="stage2_form_acceptance" name="Stage 2 form acceptance" isExecutable="true">
    <startEvent id="start"/>
    <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
    <userTask id="review" name="Review" flowable:assignee="\${initiator}"/>
    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
    <endEvent id="end"/>
  </process>
</definitions>`;
	const body = new FormData();
	body.append('name', name);
	body.append('category', 'acceptance');
	body.append('formKey', formKey);
	body.append('file', new Blob([bpmn], { type: 'application/xml' }), 'stage2_form_acceptance.bpmn20.xml');
	return assertApi(await authorized('/admin/workflow/definition/deploy', { method: 'POST', body }),
		`deploy ${name}`);
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
	if (body && typeof body !== 'string' && !(body instanceof FormData)) {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(body);
	}
	return fetchResponse(`${apiBase}${path}`, { ...options, headers, body });
}

async function fetchResponse(url, options = {}) {
	try {
		const { expectBinary = false, ...fetchOptions } = options;
		const response = await fetch(url, { ...fetchOptions, signal: AbortSignal.timeout(10_000) });
		const contentType = response.headers.get('content-type') || '';
		if (expectBinary || (contentType && !contentType.toLowerCase().includes('json') && !contentType.toLowerCase().startsWith('text/'))) {
			return {
				status: response.status,
				body: null,
				binary: Buffer.from(await response.arrayBuffer()),
				contentType,
			};
		}
		const text = await response.text();
		let body = text;
		if (text) {
			try {
				body = JSON.parse(text);
			} catch {
				// Health endpoints may intentionally return plain text.
			}
		}
		return { status: response.status, body, contentType };
	} catch (error) {
		return { status: 0, body: String(error), contentType: '' };
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
