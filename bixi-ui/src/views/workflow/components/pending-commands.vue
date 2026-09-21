<template>
	<section v-if="entries.length || error" class="mb16" aria-label="待确认操作">
		<el-alert title="待确认操作" description="下列操作的结果尚未确认；即使任务已移出待办，仍可查询原结果或按原内容重试。" type="warning" :closable="false" />
		<el-alert v-if="error" :title="error" type="error" :closable="false" class="mt8" />
		<el-card v-for="intent in entries" :key="intent.request.requestId" class="mt8" shadow="never">
			<p>{{ workflowOperationLabels[intent.operation] }} · {{ intent.display.taskName || intent.display.processName || intent.resourceId }}</p>
			<p class="pending-content">{{ kind === 'task' ? '任务编号' : '流程编号' }}：{{ intent.resourceId }}</p>
			<p v-if="intent.request.transferUserId">办理人：{{ intent.display.userName || intent.request.transferUserName || intent.request.transferUserId }}</p>
			<p v-if="content(intent)" class="pending-content">原内容：{{ content(intent) }}</p>
			<p v-if="intent.acknowledged">已确认开始其他操作，此处保留原结果查询。</p>
			<el-button v-if="canQueryWorkflowIntent(intent)" v-auth="`workflow_${kind}_view`" :disabled="busy" @click="queryIntent(intent)">查询结果</el-button>
			<el-button v-if="canRetryWorkflowIntent(intent)" v-auth="`workflow_${kind}_edit`" :disabled="busy" @click="retryIntent(intent)">按原内容重试</el-button>
			<el-button v-if="canRetryWorkflowIntent(intent)" v-auth="`workflow_${kind}_edit`" :disabled="busy" @click="newIntent(intent)">已核实，允许新操作</el-button>
		</el-card>
	</section>
</template>
<script setup lang="ts">
import { auth } from '/@/utils/authFunction';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { listWorkflowIntents, workflowIntentRevision, currentWorkflowActor, workflowOperationLabels,
	canQueryWorkflowIntent, canRetryWorkflowIntent, sendWorkflowIntent, queryWorkflowIntent, clearWorkflowIntent,
	acknowledgeWorkflowIntent, workflowIntentError, type WorkflowIntent, type WorkflowKind } from '/@/utils/workflow-intent';
const props = defineProps<{ kind: WorkflowKind }>();
const emit = defineEmits(['refresh']);
const entries = ref<WorkflowIntent[]>([]), busy = ref(false), error = ref('');
let version = 0;
const content = (intent: WorkflowIntent) => intent.request.approvalComment || intent.request.rejectReason || intent.request.transferReason || intent.request.comment || intent.request.message || intent.request.reason;
watch([currentWorkflowActor, () => workflowIntentRevision.value, () => auth(`workflow_${props.kind}_view`)], () => {
	entries.value = [];
	if (!currentWorkflowActor() || !auth(`workflow_${props.kind}_view`)) return;
	try { entries.value = listWorkflowIntents(props.kind); }
	catch (err) { error.value = workflowIntentError(err); }
}, { immediate: true, flush: 'sync' });
watch([currentWorkflowActor, () => auth(`workflow_${props.kind}_view`)], () => { version++; error.value = ''; }, { flush: 'sync' });
onBeforeUnmount(() => { version++; });
const act = async (intent: WorkflowIntent, action: () => Promise<unknown>) => {
	if (busy.value || intent.actorId !== currentWorkflowActor()) return;
	busy.value = true; error.value = '';
	const capturedVersion = version;
	try {
		await action();
		if (capturedVersion !== version || intent.actorId !== currentWorkflowActor()) return;
		if (clearWorkflowIntent(intent)) { useMessage().success(`${workflowOperationLabels[intent.operation]}成功`); emit('refresh'); }
	} catch (err) {
		if (capturedVersion === version && intent.actorId === currentWorkflowActor()) error.value = workflowIntentError(err);
	} finally { busy.value = false; }
};
const queryIntent = (intent: WorkflowIntent) => act(intent, () => queryWorkflowIntent(intent));
const retryIntent = (intent: WorkflowIntent) => act(intent, () => sendWorkflowIntent(intent));
const newIntent = async (intent: WorkflowIntent) => {
	if (busy.value || !canRetryWorkflowIntent(intent)) return;
	busy.value = true;
	const capturedVersion = version;
	try {
		await useMessageBox().confirm('原操作可能已经成功。确认已核实当前状态并允许另一条新操作？原记录将保留供查询。');
		if (capturedVersion !== version || intent.actorId !== currentWorkflowActor()) return;
		acknowledgeWorkflowIntent(intent); error.value = '';
	} catch (err) {
		if (capturedVersion === version && err !== 'cancel' && err !== 'close') error.value = workflowIntentError(err);
	} finally { busy.value = false; }
};
</script>
<style scoped>
.pending-content { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
