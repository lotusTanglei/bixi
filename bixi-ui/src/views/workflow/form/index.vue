<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item label="表单名称" prop="formName">
						<el-input v-model="state.queryForm.formName" placeholder="请输入表单名称" clearable />
					</el-form-item>
					<el-form-item label="表单标识" prop="formKey">
						<el-input v-model="state.queryForm.formKey" placeholder="请输入表单标识" clearable />
					</el-form-item>
					<el-form-item>
						<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
						<el-button icon="Refresh" @click="resetQuery">重置</el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button v-auth="'workflow_form_add'" icon="folder-add" type="primary" @click="formDialogRef.openDialog()">
						新增
					</el-button>
					<el-button
						plain
						v-auth="'workflow_form_del'"
						:disabled="multiple"
						class="ml10"
						icon="Delete"
						type="primary"
						@click="handleDelete(selectObjs)"
					>
						删除
					</el-button>
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
				@selection-change="handleSelectionChange"
				border
				:cell-style="tableStyle.cellStyle"
				:header-cell-style="tableStyle.headerCellStyle"
			>
				<el-table-column type="selection" width="40" />
				<el-table-column label="序号" type="index" width="60" />
				<el-table-column label="表单名称" prop="formName" show-overflow-tooltip></el-table-column>
				<el-table-column label="表单标识" prop="formKey" show-overflow-tooltip></el-table-column>
				<el-table-column label="当前版本" prop="version" width="100">
					<template #default="scope">
						<el-tag>v{{ scope.row.version }}</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="状态" width="100">
					<template #default="scope">
						<el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
							{{ scope.row.status === 1 ? '启用' : '禁用' }}
						</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="描述" prop="description" show-overflow-tooltip></el-table-column>
				<el-table-column label="创建时间" prop="createTime" width="180"></el-table-column>
				<el-table-column label="操作" width="280" fixed="right">
					<template #default="scope">
						<el-button v-auth="'workflow_form_design'" icon="Edit" text type="primary" @click="handleDesign(scope.row)">
							设计
						</el-button>
						<el-button v-auth="'workflow_form_edit'" icon="EditPen" text type="primary" @click="formDialogRef.openDialog(scope.row.id)">
							编辑
						</el-button>
						<el-button v-auth="'workflow_form_version'" icon="Clock" text type="primary" @click="handleVersion(scope.row)">
							版本
						</el-button>
						<el-button v-auth="'workflow_form_permission'" icon="Lock" text type="primary" @click="handlePermission(scope.row)">
							权限
						</el-button>
						<el-button v-auth="'workflow_form_del'" icon="Delete" text type="primary" @click="handleDelete([scope.row.id])">
							删除
						</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>

		<form-dialog ref="formDialogRef" @refresh="getDataList" />
	</div>
</template>

<script lang="ts" name="workflowForm" setup>
import { getFormList, deleteForm } from '/@/api/workflow/form';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useRouter } from 'vue-router';

const FormDialog = defineAsyncComponent(() => import('./form-dialog.vue'));

const router = useRouter();
const formDialogRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		formName: '',
		formKey: '',
	},
	pageList: getFormList,
});
const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
};

const handleSelectionChange = (objs: { id: string }[]) => {
	selectObjs.value = objs.map(({ id }) => id);
	multiple.value = !objs.length;
};

const handleDelete = async (ids: string[]) => {
	try {
		await useMessageBox().confirm('确认删除选中的表单吗？');
	} catch {
		return;
	}

	try {
		await deleteForm(ids.join(','));
		getDataList();
		useMessage().success('删除成功');
	} catch (err: any) {
		useMessage().error(err.msg);
	}
};

const handleDesign = (row: any) => {
	router.push({
		path: '/workflow/form/designer',
		query: {
			formId: row.id,
			formKey: row.formKey,
		},
	});
};

const handleVersion = (row: any) => {
	router.push({
		path: '/workflow/form/version',
		query: {
			formId: row.id,
			formName: row.formName,
		},
	});
};

const handlePermission = (row: any) => {
	router.push({
		path: '/workflow/form/permission',
		query: {
			formId: row.id,
			formName: row.formName,
		},
	});
};
</script>
