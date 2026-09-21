<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item label="流程标识" prop="processKey">
						<el-input v-model="state.queryForm.processKey" placeholder="请输入流程标识" clearable />
					</el-form-item>
					<el-form-item label="流程状态" prop="status">
						<el-select v-model="state.queryForm.status" placeholder="请选择流程状态" clearable>
							<el-option label="进行中" value="running" />
							<el-option label="已完成" value="completed" />
							<el-option label="已取消" value="terminated" />
						<el-option label="已拒绝" value="rejected" />
						<el-option label="已挂起" value="suspended" />
						</el-select>
					</el-form-item>
					<el-form-item>
						<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
						<el-button icon="Refresh" @click="resetQuery">重置</el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<right-toolbar
						v-model:showSearch="showSearch"
						@queryTable="getDataList"
						class="ml10 mr20"
						style="float: right"
					/>
				</div>
			</el-row>
			<pending-commands kind="process" @refresh="getDataList" />
			<el-alert v-if="commandError" :title="commandError" type="error" :closable="false" class="mb16" />
			<el-table
				v-loading="state.loading"
				:data="state.dataList"
				border
				:cell-style="tableStyle.cellStyle"
				:header-cell-style="tableStyle.headerCellStyle"
			>
				<el-table-column label="序号" type="index" width="60" />
				<el-table-column label="流程标识" prop="processKey" show-overflow-tooltip></el-table-column>
				<el-table-column label="流程实例ID" prop="processInstanceId" show-overflow-tooltip width="200"></el-table-column>
				<el-table-column label="发起人" prop="startUserName" width="100"></el-table-column>
				<el-table-column label="当前节点" prop="currentActivityName" show-overflow-tooltip></el-table-column>
				<el-table-column label="流程状态" width="100">
					<template #default="scope">
						<el-tag :type="getStatusType(scope.row.status)">
							{{ getStatusText(scope.row.status) }}
						</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="发起时间" prop="createTime" width="180"></el-table-column>
				<el-table-column label="结束时间" prop="endTime" width="180"></el-table-column>
				<el-table-column label="操作" width="200" fixed="right">
					<template #default="scope">
						<el-button icon="View" text type="primary" @click="handleDetail(scope.row)">详情</el-button>
						<el-button icon="Share" text type="primary" @click="handleViewDiagram(scope.row)">进度</el-button>
						<el-button
							v-if="scope.row.status === 'running'"
							v-auth="'workflow_process_edit'"
							icon="Close"
							text
							type="danger"
							:disabled="commandBusy"
							@click="handleCancel(scope.row)"
						>
							取消
						</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>

		<process-detail-dialog ref="processDetailRef" />

		<process-dialog ref="processDialogRef" @refresh="getDataList" />
	</div>
</template>

<script lang="ts" name="workflowProcessInstance" setup>
import { myProcessPageList } from '/@/api/workflow/process';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { clearWorkflowIntent, pendingWorkflowIntent, restoreWorkflowIntent, currentWorkflowActor, sendWorkflowIntent, workflowIntentError } from '/@/utils/workflow-intent';
import PendingCommands from '../components/pending-commands.vue';

const ProcessDetailDialog = defineAsyncComponent(() => import('./detail-dialog.vue'));
const ProcessDialog = defineAsyncComponent(() => import('./process-dialog.vue'));

const commandBusy = ref(false), commandError = ref('');
let commandVersion = 0;
watch(currentWorkflowActor, () => { commandVersion++; commandError.value = ''; }, { flush: 'sync' });
onBeforeUnmount(() => { commandVersion++; });

const processDetailRef = ref();
const processDialogRef = ref();
const queryRef = ref();
const showSearch = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		processKey: '',
		status: '',
	},
	pageList: myProcessPageList,
});
const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
};

const getStatusType = (status: string) => {
	const statusMap: Record<string, string> = {
		running: 'warning',
		completed: 'success',
			rejected: 'danger',
			suspended: 'warning',
		terminated: 'info',
	};
	return statusMap[status] || 'info';
};

const getStatusText = (status: string) => {
	const statusMap: Record<string, string> = {
		running: '进行中',
		completed: '已完成',
			rejected: '已拒绝',
			suspended: '已挂起',
		terminated: '已取消',
	};
	return statusMap[status] || status;
};

const handleDetail = (row: any) => {
	processDetailRef.value.openDialog(row);
};

const handleViewDiagram = (row: any) => {
	processDialogRef.value.openDialog(row.processInstanceId);
};

const handleCancel = async (row: any) => {
	if (commandBusy.value) return;
	commandBusy.value = true; commandError.value = '';
	const actorId = currentWorkflowActor(), resourceId = row.processInstanceId, version = commandVersion;
	const display = { processName: row.processName || row.processKey };
	const current = () => actorId === currentWorkflowActor() && version === commandVersion;
	try {
		if (restoreWorkflowIntent('process', resourceId, actorId)) throw new Error('该流程还有待确认操作，请在上方查询原结果或按原内容重试');
		await useMessageBox().confirm('确认取消该流程实例吗？');
		if (!current()) return;
		const intent = pendingWorkflowIntent('TERMINATE', resourceId, { reason: '用户取消' }, display, actorId);
		await sendWorkflowIntent(intent);
		if (current() && clearWorkflowIntent(intent)) { useMessage().success('取消成功'); getDataList(); }
	} catch (err: any) {
		if (current() && err !== 'cancel' && err !== 'close') commandError.value = workflowIntentError(err);
	} finally { commandBusy.value = false; }
};
</script>
