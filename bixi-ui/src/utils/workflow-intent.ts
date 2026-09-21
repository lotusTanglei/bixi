import { ref } from 'vue';
import { useUserInfo } from '/@/stores/userInfo';
import { auth } from '/@/utils/authFunction';
import { complete, reject, transfer, delegate, resolve, claim, unclaim, addComment } from '/@/api/workflow/task';
import { cancel, suspend, activate, getCommand } from '/@/api/workflow/process';

export type WorkflowKind = 'task' | 'process';
export interface WorkflowIntent {
	storageKey: string;
	actorId: string;
	operation: string;
	resourceId: string;
	request: Record<string, any> & { requestId: string };
	display: { taskName?: string; processName?: string; userName?: string };
	acknowledged?: boolean;
}
export const workflowIntentRevision = ref(0);
export const workflowOperationLabels: Record<string, string> = {
	COMPLETE: '审批通过', REJECT: '拒绝并结束', TRANSFER: '转办', DELEGATE: '委派', RESOLVE: '交回委派',
	CLAIM: '认领', UNCLAIM: '取消认领', COMMENT: '添加意见', TERMINATE: '取消流程', SUSPEND: '挂起流程', ACTIVATE: '恢复流程',
};
const taskOperations = ['COMPLETE', 'REJECT', 'TRANSFER', 'DELEGATE', 'RESOLVE', 'CLAIM', 'UNCLAIM', 'COMMENT'];
const processOperations = ['TERMINATE', 'SUSPEND', 'ACTIVATE'];
const uuidPattern = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
export const workflowKind = (operation: string): WorkflowKind => taskOperations.includes(operation) ? 'task' : 'process';
export const currentWorkflowActor = () => String(useUserInfo().userInfos.user?.id ?? '');
const assertActor = (actorId: string) => {
	if (!actorId || actorId !== currentWorkflowActor()) throw new Error('当前用户已变更，请关闭并重新打开窗口');
};
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));
const uuid = () => {
	if (crypto.randomUUID) return crypto.randomUUID();
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	bytes[6] = (bytes[6] & 15) | 64;
	bytes[8] = (bytes[8] & 63) | 128;
	const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};
const storageError = () => new Error('无法恢复已保存的操作，请保留当前窗口并联系管理员核实结果');
const validRequest = (operation: string, resourceId: string, request: any) => {
	if (!request || !uuidPattern.test(request.requestId) || typeof resourceId !== 'string' || !resourceId) return false;
	if (taskOperations.includes(operation) && request.taskId !== resourceId) return false;
	const field = ({ COMPLETE: 'approvalComment', REJECT: 'rejectReason', TRANSFER: 'transferReason', DELEGATE: 'transferReason', RESOLVE: 'comment', COMMENT: 'message', TERMINATE: 'reason' } as Record<string, string>)[operation];
	if (field && typeof request[field] !== 'string') return false;
	if (['TRANSFER', 'DELEGATE'].includes(operation) && !['number', 'string'].includes(typeof request.transferUserId)) return false;
	return taskOperations.includes(operation) || processOperations.includes(operation);
};

/** Keep unresolved commands independent of the live todo list, including acknowledged earlier attempts. */
export const listWorkflowIntents = (kind: WorkflowKind, actorId = currentWorkflowActor()): WorkflowIntent[] => {
	assertActor(actorId);
	const entries: WorkflowIntent[] = [];
	try {
		for (let index = 0; index < sessionStorage.length; index++) {
			const storageKey = sessionStorage.key(index)!;
			const parts = storageKey.split(':');
			if (parts[0] !== 'workflow') continue;
			const modern = parts[1] === 'command';
			const operation = modern ? parts[3] : parts[1];
			if (!(kind === 'task' ? taskOperations : processOperations).includes(operation)) continue;
			if (parts[2] !== encodeURIComponent(actorId)) continue;
			const saved = JSON.parse(sessionStorage.getItem(storageKey)!);
			const resourceId = decodeURIComponent(modern ? parts[4] : parts[3]);
			const entry: WorkflowIntent = modern ? { ...saved, storageKey } : { storageKey, actorId, operation, resourceId, request: saved, display: {} };
			if (entry.actorId !== actorId || entry.operation !== operation || entry.resourceId !== resourceId || !validRequest(operation, resourceId, entry.request)
				|| (entry.acknowledged !== undefined && typeof entry.acknowledged !== 'boolean')
				|| !entry.display || typeof entry.display !== 'object' || (modern && parts[5] !== entry.request.requestId)) throw storageError();
			entries.push(entry);
		}
	} catch { throw storageError(); }
	return entries;
};
export const restoreWorkflowIntent = (kind: WorkflowKind, resourceId: string, actorId = currentWorkflowActor()) =>
	listWorkflowIntents(kind, actorId).find(intent => intent.resourceId === resourceId && !intent.acknowledged) ?? null;

export const pendingWorkflowIntent = (operation: string, resourceId: string, payload: Record<string, unknown>, display: WorkflowIntent['display'] = {}, actorId = currentWorkflowActor()): WorkflowIntent => {
	assertActor(actorId);
	if (restoreWorkflowIntent(workflowKind(operation), resourceId, actorId)) throw new Error('该任务或流程还有待确认操作，请先查询结果或明确开始新操作');
	const request = clone({ ...payload, requestId: uuid() });
	if (!validRequest(operation, resourceId, request)) throw new Error('操作内容不完整，请重新填写');
	const storageKey = `workflow:command:${encodeURIComponent(actorId)}:${operation}:${encodeURIComponent(resourceId)}:${request.requestId}`;
	const intent = { storageKey, actorId, operation, resourceId, request, display: clone(display) };
	try { sessionStorage.setItem(storageKey, JSON.stringify(intent)); }
	catch { throw new Error('无法保存本次操作，请检查浏览器存储后重试'); }
	workflowIntentRevision.value++;
	return intent;
};
const matchingIntent = (intent: WorkflowIntent) => {
	assertActor(intent.actorId);
	return listWorkflowIntents(workflowKind(intent.operation), intent.actorId).find(saved => saved.storageKey === intent.storageKey
		&& saved.operation === intent.operation && saved.resourceId === intent.resourceId && JSON.stringify(saved.request) === JSON.stringify(intent.request));
};
export const clearWorkflowIntent = (intent: WorkflowIntent) => {
	if (!matchingIntent(intent)) return false;
	sessionStorage.removeItem(intent.storageKey);
	workflowIntentRevision.value++;
	return true;
};
export const acknowledgeWorkflowIntent = (intent: WorkflowIntent) => {
	const saved = matchingIntent(intent);
	if (!saved) throw storageError();
	// Preserve the original request for result lookup after the user starts another intent.
	const storageKey = `workflow:command:${encodeURIComponent(intent.actorId)}:${intent.operation}:${encodeURIComponent(intent.resourceId)}:${intent.request.requestId}`;
	sessionStorage.setItem(storageKey, JSON.stringify({ ...saved, storageKey, acknowledged: true }));
	if (storageKey !== saved.storageKey) sessionStorage.removeItem(saved.storageKey);
	workflowIntentRevision.value++;
};
export const canQueryWorkflowIntent = (intent: WorkflowIntent) => intent.actorId === currentWorkflowActor() && auth(`workflow_${workflowKind(intent.operation)}_view`);
export const canRetryWorkflowIntent = (intent: WorkflowIntent) => !intent.acknowledged && canQueryWorkflowIntent(intent) && auth(`workflow_${workflowKind(intent.operation)}_edit`);
export const sendWorkflowIntent = async (intent: WorkflowIntent) => {
	const saved = matchingIntent(intent);
	if (!saved || !canRetryWorkflowIntent(saved)) throw new Error('当前操作不可重试，请查询原结果');
	const request = clone(saved.request);
	let response;
	switch (saved.operation) {
		case 'COMPLETE': response = await complete(request); break;
		case 'REJECT': response = await reject(request); break;
		case 'TRANSFER': response = await transfer(request); break;
		case 'DELEGATE': response = await delegate(request); break;
		case 'RESOLVE': response = await resolve(request as { taskId: string; comment: string; requestId: string }); break;
		case 'CLAIM': response = await claim(saved.resourceId, request.requestId); break;
		case 'UNCLAIM': response = await unclaim(saved.resourceId, request.requestId); break;
		case 'COMMENT': response = await addComment(request); break;
		case 'TERMINATE': response = await cancel(saved.resourceId, request.requestId, request.reason); break;
		case 'SUSPEND': response = await suspend(saved.resourceId, request.requestId); break;
		case 'ACTIVATE': response = await activate(saved.resourceId, request.requestId); break;
	}
	assertActor(saved.actorId);
	if (!response || response.code !== 0 || (workflowKind(saved.operation) === 'process' && response.data !== true)) throw response || new Error('操作结果尚未确认');
	return response;
};
export const queryWorkflowIntent = async (intent: WorkflowIntent) => {
	if (!matchingIntent(intent) || !canQueryWorkflowIntent(intent)) throw new Error('当前操作不可查询');
	const response = await getCommand(intent.request.requestId);
	assertActor(intent.actorId);
	const result = response?.data;
	if (response?.code !== 0) throw response;
	if (result?.requestId !== intent.request.requestId || result.operation !== intent.operation || result.resourceId !== intent.resourceId
		|| result.resultCode !== 'SUCCESS' || !result.completedAt || (workflowKind(intent.operation) === 'process' && result.response !== true)) throw new Error('尚未确认本次操作结果，请稍后查询');
	return result;
};
export const workflowIntentError = (error: any) => {
	const code = error?.data?.errorCode || error?.response?.data?.data?.errorCode;
	if (code === 'WORKFLOW_COMMAND_NOT_FOUND') return '尚未查询到本次结果，可重试本次操作';
	if (code === 'WORKFLOW_REQUEST_CONFLICT') return '本次操作内容与已保存记录不一致，请查询原结果';
	return error instanceof Error ? error.message : '操作结果尚未确认，可查询结果或重试本次操作';
};
