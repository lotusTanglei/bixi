<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item label="流程名称" prop="processName">
						<el-input v-model="state.queryForm.processName" placeholder="请输入流程名称" clearable />
					</el-form-item>
					<el-form-item label="任务名称" prop="taskName">
						<el-input v-model="state.queryForm.taskName" placeholder="请输入任务名称" clearable />
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
			<el-table
				v-loading="state.loading"
				:data="state.dataList"
				border
				:cell-style="tableStyle.cellStyle"
				:header-cell-style="tableStyle.headerCellStyle"
			>
				<el-table-column label="序号" type="index" width="60" />
				<el-table-column label="流程名称" prop="processName" show-overflow-tooltip></el-table-column>
				<el-table-column label="任务名称" prop="taskName" show-overflow-tooltip></el-table-column>
				<el-table-column label="发起人" prop="startUser" width="100"></el-table-column>
				<el-table-column label="任务创建时间" prop="createTime" width="180"></el-table-column>
				<el-table-column label="操作" width="280" fixed="right">
					<template #default="scope">
						<el-button icon="View" text type="primary" @click="handleDetail(scope.row)">详情</el-button>
						<el-button v-auth="'workflow_task_complete'" icon="Select" text type="primary" @click="handleApprove(scope.row)">
							审批
						</el-button>
						<el-button v-auth="'workflow_task_transfer'" icon="Rank" text type="primary" @click="handleTransfer(scope.row)">
							转办
						</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>

		<task-detail-dialog ref="taskDetailRef" />

		<approve-dialog ref="approveRef" @refresh="getDataList" />

		<transfer-dialog ref="transferRef" @refresh="getDataList" />
	</div>
</template>

<script lang="ts" name="workflowTaskTodo" setup>
import { todoPageList } from '/@/api/workflow/task';
import { BasicTableProps, useTable } from '/@/hooks/table';

const TaskDetailDialog = defineAsyncComponent(() => import('./detail-dialog.vue'));
const ApproveDialog = defineAsyncComponent(() => import('./approve-dialog.vue'));
const TransferDialog = defineAsyncComponent(() => import('./transfer-dialog.vue'));

const taskDetailRef = ref();
const approveRef = ref();
const transferRef = ref();
const queryRef = ref();
const showSearch = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		processName: '',
		taskName: '',
	},
	pageList: todoPageList,
});
const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
};

const handleDetail = (row: any) => {
	taskDetailRef.value.openDialog(row);
};

const handleApprove = (row: any) => {
	approveRef.value.openDialog(row);
};

const handleTransfer = (row: any) => {
	transferRef.value.openDialog(row);
};
</script>
