import { ref, watch, onBeforeUnmount } from 'vue';
import { auth } from '/@/utils/authFunction';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { currentWorkflowActor, restoreWorkflowIntent, pendingWorkflowIntent, clearWorkflowIntent, acknowledgeWorkflowIntent,
	sendWorkflowIntent, queryWorkflowIntent, workflowIntentError, workflowOperationLabels, type WorkflowIntent } from './workflow-intent';

/** Shared lifecycle for editable task dialogs: freeze one payload before sending and restore it on reopening. */
export const useWorkflowIntentDialog = (options: {
	operations: string[];
	restore: (intent: WorkflowIntent) => void;
	reset: () => void;
	refresh: () => void;
}) => {
	const visible = ref(false), loading = ref(false), error = ref(''), storageReady = ref(false);
	const pendingIntent = ref<WorkflowIntent | null>(null);
	let actorId = '', resourceId = '', version = 0;
	let display: WorkflowIntent['display'] = {};
	watch(visible, open => { if (!open) version++; }, { flush: 'sync' });
	watch([currentWorkflowActor, () => auth('workflow_task_view')], () => { version++; visible.value = false; pendingIntent.value = null; options.reset(); }, { flush: 'sync' });
	onBeforeUnmount(() => { version++; });
	const current = (capturedVersion: number) => capturedVersion === version && actorId === currentWorkflowActor();
	const closeDialog = (done?: () => void) => {
		if (loading.value) return;
		if (done) done(); else visible.value = false;
	};
	const openIntent = (row: any) => {
		if (loading.value) return false;
		version++; actorId = currentWorkflowActor(); resourceId = row.taskId;
		display = { taskName: row.taskName, processName: row.processName };
		visible.value = true; error.value = ''; storageReady.value = false; pendingIntent.value = null;
		try {
			if (!auth('workflow_task_view')) throw new Error('当前用户无权查看任务操作');
			const saved = restoreWorkflowIntent('task', resourceId, actorId);
			if (saved && !options.operations.includes(saved.operation)) throw new Error(`该任务还有待确认的${workflowOperationLabels[saved.operation]}操作，请先在待确认操作中查询结果`);
			pendingIntent.value = saved;
			if (saved) options.restore(saved);
			storageReady.value = true;
		} catch (err) { error.value = workflowIntentError(err); }
		return true;
	};
	const finish = (intent: WorkflowIntent, capturedVersion: number) => {
		if (!current(capturedVersion) || !clearWorkflowIntent(intent)) return;
		pendingIntent.value = null;
		useMessage().success(`${workflowOperationLabels[intent.operation]}成功`);
		visible.value = false;
		options.refresh();
	};
	const submitIntent = async (draft: { operation: string; payload: Record<string, unknown>; display?: WorkflowIntent['display'] }, validate: () => Promise<boolean>) => {
		if (loading.value || !visible.value || !storageReady.value) return;
		loading.value = true; error.value = '';
		const capturedVersion = version;
		const snapshot = JSON.parse(JSON.stringify(draft));
		try {
			if (actorId !== currentWorkflowActor()) throw new Error('当前用户已变更，请关闭并重新打开窗口');
			if (!pendingIntent.value) {
				if (!await validate().catch(() => false) || !current(capturedVersion)) return;
				pendingIntent.value = pendingWorkflowIntent(snapshot.operation, resourceId, { ...snapshot.payload, taskId: resourceId }, { ...display, ...snapshot.display }, actorId);
			}
			const intent = pendingIntent.value;
			options.restore(intent);
			await sendWorkflowIntent(intent);
			finish(intent, capturedVersion);
		} catch (err) { if (current(capturedVersion)) error.value = workflowIntentError(err); }
		finally { loading.value = false; }
	};
	const queryResult = async () => {
		if (loading.value || !visible.value || !pendingIntent.value) return;
		loading.value = true; error.value = '';
		const capturedVersion = version, intent = pendingIntent.value;
		try { await queryWorkflowIntent(intent); finish(intent, capturedVersion); }
		catch (err) { if (current(capturedVersion)) error.value = workflowIntentError(err); }
		finally { loading.value = false; }
	};
	const newIntent = async () => {
		if (loading.value || !pendingIntent.value) return;
		loading.value = true;
		const capturedVersion = version, intent = pendingIntent.value;
		try {
			await useMessageBox().confirm('原操作可能已经成功。确认已核实任务状态并开始修改？原操作将保留供查询，新提交会作为另一条操作。');
			if (!current(capturedVersion)) return;
			acknowledgeWorkflowIntent(intent);
			pendingIntent.value = null; error.value = '';
		} catch (err) { if (current(capturedVersion) && err !== 'cancel' && err !== 'close') error.value = workflowIntentError(err); }
		finally { loading.value = false; }
	};
	return { visible, loading, error, storageReady, pendingIntent, closeDialog, openIntent, submitIntent, queryResult, newIntent };
};
