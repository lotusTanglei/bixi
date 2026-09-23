<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<div class="mb16"><el-button icon="Refresh" @click="getDataList(false)">刷新{{ done ? '已办' : '待办' }}</el-button></div>
			<el-alert v-if="error" :title="error" type="error" class="mb16" :closable="false" />
			<pending-commands kind="task" @refresh="getDataList(false)" />
			<el-table :data="state.dataList" v-loading="state.loading || busy" border :empty-text="done ? '暂无已办任务' : '暂无待办任务'">
				<el-table-column prop="processName" label="流程名称" min-width="160" show-overflow-tooltip />
				<el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
				<el-table-column label="候选身份" min-width="200">
					<template #default="{ row }">
						<div v-if="candidateUsers(row).length || candidateGroups(row).length" class="task-candidates">
							<span v-if="candidateUsers(row).length" class="task-candidate-line" :title="candidateUsers(row).join('、')">候选用户：{{ candidateUsers(row).join('、') }}</span>
							<span v-if="candidateGroups(row).length" class="task-candidate-line" :title="candidateGroups(row).join('、')">候选角色：{{ candidateGroups(row).join('、') }}</span>
						</div>
						<span v-else class="task-candidate-empty">-</span>
					</template>
				</el-table-column>
				<el-table-column prop="createTime" label="创建时间" width="180" />
				<el-table-column v-if="done" prop="endTime" label="办结时间" width="180" />
				<el-table-column v-else label="状态" width="110"><template #default="{ row }">{{ row.delegationState === 'PENDING' ? '待交回委派' : row.assignee ? '待审批' : row.claimable ? '待认领' : '待分配' }}</template></el-table-column>
				<el-table-column label="操作" :width="done ? 150 : 340" fixed="right">
					<template #default="{ row }">
						<el-button v-auth-all="['workflow_task_view', 'workflow_process_view']" text type="primary" @click="detailRef.openDialog(row)">详情</el-button>
						<el-button v-if="done" v-auth="'workflow_process_view'" text type="primary" @click="diagramRef.openDialog(row.processInstanceId)">进度</el-button>
						<template v-else>
							<el-button v-if="!row.assignee && row.claimable" v-auth="'workflow_task_edit'" text type="primary" @click="claimTask(row)">认领</el-button>
							<template v-else-if="row.assignee">
								<el-button v-if="row.delegationState === 'PENDING'" v-auth="'workflow_task_edit'" text type="primary" @click="resolveTask(row)">处理并交回</el-button>
								<template v-else>
									<el-button v-auth="'workflow_task_edit'" text type="primary" @click="approveRef.openDialog(row)">审批</el-button>
									<el-button v-auth-all="['workflow_task_edit', 'demo_leave_view']" text type="primary" @click="transferRef.openDialog(row)">转办</el-button>
									<el-button v-auth-all="['workflow_task_edit', 'demo_leave_view']" text type="primary" @click="transferRef.openDialog(row, true)">委派</el-button>
								</template>
							</template>
						</template>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle" />
		</div>
		<task-detail ref="detailRef" />
		<process-diagram ref="diagramRef" />
		<approve-dialog ref="approveRef" @refresh="getDataList" />
		<transfer-dialog ref="transferRef" @refresh="getDataList" />
	</div>
</template>
<script setup lang="ts">
import { ElMessageBox } from 'element-plus';
import { todoPageList, donePageList, type WorkflowTaskRow } from '/@/api/workflow/task';
import { type BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage } from '/@/hooks/message';
import { clearWorkflowIntent, pendingWorkflowIntent, restoreWorkflowIntent, currentWorkflowActor, sendWorkflowIntent, workflowIntentError } from '/@/utils/workflow-intent';
import PendingCommands from '../components/pending-commands.vue';
import TaskDetail from './detail-dialog.vue';
import ProcessDiagram from '../process/process-dialog.vue';
import ApproveDialog from './approve-dialog.vue';
import TransferDialog from './transfer-dialog.vue';
const props = defineProps<{ done?: boolean }>();
const state = reactive<BasicTableProps>({ queryForm: {}, pageList: props.done ? donePageList : todoPageList });
const { getDataList, currentChangeHandle, sizeChangeHandle } = useTable(state);
const detailRef = ref(); const diagramRef = ref(); const approveRef = ref(); const transferRef = ref();
const busy = ref(false); const error = ref('');
const candidateUsers = (row: WorkflowTaskRow) => Array.isArray(row.candidateUsers) ? row.candidateUsers : [];
const candidateGroups = (row: WorkflowTaskRow) => Array.isArray(row.candidateGroups) ? row.candidateGroups : [];
let version = 0;
watch(currentWorkflowActor, () => { version++; error.value = ''; }, { flush: 'sync' });
onBeforeUnmount(() => { version++; });
const run = async (operation: string, row: WorkflowTaskRow, payload: () => Promise<Record<string, unknown>>, message: string) => {
	if (busy.value) return;
	busy.value = true; error.value = '';
	const actorId = currentWorkflowActor(), resourceId = row.taskId, capturedVersion = version;
	const display = { taskName: row.taskName, processName: row.processName };
	const current = () => actorId === currentWorkflowActor() && version === capturedVersion;
	try {
		if (restoreWorkflowIntent('task', resourceId, actorId)) throw new Error('该任务还有待确认操作，请在上方查询原结果或按原内容重试');
		const data = await payload();
		if (!current()) return;
		const intent = pendingWorkflowIntent(operation, resourceId, { ...data, taskId: resourceId }, display, actorId);
		await sendWorkflowIntent(intent);
		if (current() && clearWorkflowIntent(intent)) useMessage().success(message);
	} catch (err: any) {
		if (current() && err !== 'cancel' && err !== 'close') error.value = workflowIntentError(err);
	} finally { busy.value = false; if (current()) getDataList(false); }
};
const claimTask = (row: WorkflowTaskRow) => run('CLAIM', row, async () => ({}), '任务已认领');
const resolveTask = (row: WorkflowTaskRow) => run('RESOLVE', row, async () => {
	const response = await ElMessageBox.prompt('填写处理意见，任务将交回原办理人审批。', '交回委派', { inputType: 'textarea', inputValidator: value => !!value?.trim() || '请填写处理意见' });
	return { comment: response.value };
}, '已交回原办理人');
</script>

<style scoped lang="scss">
.task-candidates {
	display: flex;
	flex-direction: column;
	gap: 2px;
	min-width: 0;
}

.task-candidate-line {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.task-candidate-empty {
	color: var(--el-text-color-placeholder);
}
</style>
