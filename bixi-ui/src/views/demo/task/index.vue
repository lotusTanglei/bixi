<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-form v-show="showSearch" ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
				<el-form-item label="任务标题" prop="title">
					<el-input v-model="state.queryForm.title" clearable placeholder="输入任务标题" />
				</el-form-item>
				<el-form-item label="负责人" prop="assignee">
					<el-input v-model="state.queryForm.assignee" clearable placeholder="输入负责人" />
				</el-form-item>
				<el-form-item label="优先级" prop="priority">
					<el-select v-model="state.queryForm.priority" clearable placeholder="全部" style="width: 140px">
						<el-option label="低" value="LOW" />
						<el-option label="中" value="MEDIUM" />
						<el-option label="高" value="HIGH" />
					</el-select>
				</el-form-item>
				<el-form-item label="状态" prop="taskStatus">
					<el-select v-model="state.queryForm.taskStatus" clearable placeholder="全部" style="width: 140px">
						<el-option label="待处理" value="TODO" />
						<el-option label="进行中" value="IN_PROGRESS" />
						<el-option label="已完成" value="DONE" />
					</el-select>
				</el-form-item>
				<el-form-item>
					<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
					<el-button icon="Refresh" @click="resetQuery">重置</el-button>
				</el-form-item>
			</el-form>

			<el-row class="mb8">
				<el-button v-auth="'demo_task_add'" icon="FolderAdd" type="primary" @click="formDialogRef.openDialog()">新增</el-button>
				<el-button
					v-auth="'demo_task_del'"
					class="ml10"
					:disabled="multiple"
					icon="Delete"
					plain
					type="danger"
					@click="handleDelete(selectIds)"
				>
					删除
				</el-button>
				<right-toolbar v-model:showSearch="showSearch" class="ml10" style="margin-left: auto" @queryTable="getDataList" />
			</el-row>

			<el-table
				:data="state.dataList"
				border
				:cell-style="tableStyle?.cellStyle"
				:header-cell-style="tableStyle?.headerCellStyle"
				v-loading="state.loading"
				@selection-change="handleSelectionChange"
			>
				<el-table-column align="center" type="selection" width="44" />
				<el-table-column label="任务标题" min-width="190" prop="title" show-overflow-tooltip />
				<el-table-column label="负责人" min-width="120" prop="assignee" show-overflow-tooltip />
				<el-table-column align="center" label="优先级" width="90">
					<template #default="scope">
						<el-tag v-if="scope.row.priority === 'HIGH'" type="danger">高</el-tag>
						<el-tag v-else-if="scope.row.priority === 'MEDIUM'" type="warning">中</el-tag>
						<el-tag v-else type="info">低</el-tag>
					</template>
				</el-table-column>
				<el-table-column align="center" label="状态" width="100">
					<template #default="scope">
						<el-tag v-if="scope.row.taskStatus === 'DONE'" type="success">已完成</el-tag>
						<el-tag v-else-if="scope.row.taskStatus === 'IN_PROGRESS'">进行中</el-tag>
						<el-tag v-else type="info">待处理</el-tag>
					</template>
				</el-table-column>
				<el-table-column align="center" label="截止日期" prop="dueDate" width="120" />
				<el-table-column label="备注" min-width="160" prop="remark" show-overflow-tooltip />
				<el-table-column align="center" fixed="right" label="操作" width="170">
					<template #default="scope">
						<el-button v-auth="'demo_task_edit'" icon="EditPen" text type="primary" @click="formDialogRef.openDialog(scope.row.id)">
							编辑
						</el-button>
						<el-button v-auth="'demo_task_del'" icon="Delete" text type="danger" @click="handleDelete([scope.row.id])">
							删除
						</el-button>
					</template>
				</el-table-column>
			</el-table>

			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle" />
		</div>

		<form-dialog ref="formDialogRef" @refresh="getDataList" />
	</div>
</template>

<script lang="ts" name="demoTask" setup>
import { delObj, fetchList } from '/@/api/demo/task';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';

const FormDialog = defineAsyncComponent(() => import('./form.vue'));

const formDialogRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectIds = ref<string[]>([]);
const multiple = computed(() => selectIds.value.length === 0);

const state = reactive<BasicTableProps>({
	queryForm: {},
	pageList: fetchList,
});

const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
};

const handleSelectionChange = (rows: Array<{ id: string }>) => {
	selectIds.value = rows.map(({ id }) => id);
};

const handleDelete = async (ids: string[]) => {
	try {
		await useMessageBox().confirm('确认删除选中的示例任务吗？');
		await delObj(ids);
		useMessage().success('删除成功');
		selectIds.value = [];
		getDataList();
	} catch (error: any) {
		if (error?.msg) useMessage().error(error.msg);
	}
};
</script>
