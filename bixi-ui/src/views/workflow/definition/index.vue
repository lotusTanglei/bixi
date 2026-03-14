<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item label="流程名称" prop="name">
						<el-input v-model="state.queryForm.name" placeholder="请输入流程名称" clearable />
					</el-form-item>
					<el-form-item label="流程标识" prop="key">
						<el-input v-model="state.queryForm.key" placeholder="请输入流程标识" clearable />
					</el-form-item>
					<el-form-item>
						<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
						<el-button icon="Refresh" @click="resetQuery">重置</el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button v-auth="'workflow_definition_deploy'" icon="upload-filled" type="primary" @click="deployDialogRef.openDialog()">
						部署流程
					</el-button>
					<el-button
						plain
						v-auth="'workflow_definition_del'"
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
				<el-table-column label="流程名称" prop="name" show-overflow-tooltip></el-table-column>
				<el-table-column label="流程标识" prop="key" show-overflow-tooltip></el-table-column>
				<el-table-column label="版本" prop="version" width="80">
					<template #default="scope">
						<el-tag>v{{ scope.row.version }}</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="状态" width="100">
					<template #default="scope">
						<el-tag :type="scope.row.suspensionState === 1 ? 'success' : 'danger'">
							{{ scope.row.suspensionState === 1 ? '激活' : '挂起' }}
						</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="部署时间" prop="deploymentTime" show-overflow-tooltip width="180"></el-table-column>
				<el-table-column label="操作" width="200" fixed="right">
					<template #default="scope">
						<el-button
							v-if="scope.row.suspensionState === 1"
							v-auth="'workflow_definition_suspend'"
							icon="VideoPause"
							text
							type="primary"
							@click="handleSuspend(scope.row.id)"
						>
							挂起
						</el-button>
						<el-button
							v-if="scope.row.suspensionState !== 1"
							v-auth="'workflow_definition_activate'"
							icon="VideoPlay"
							text
							type="primary"
							@click="handleActivate(scope.row.id)"
						>
							激活
						</el-button>
						<el-button icon="View" text type="primary" @click="handleViewDiagram(scope.row.id)">流程图</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>

		<deploy-dialog ref="deployDialogRef" @refresh="getDataList" />

		<el-dialog v-model="diagramVisible" title="流程图" width="80%" destroy-on-close>
			<div v-if="diagramUrl" class="diagram-container">
				<el-image :src="diagramUrl" fit="contain" style="width: 100%; height: 600px" />
			</div>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowDefinition" setup>
import { pageList, suspend, activate, delObj, getDiagram } from '/@/api/workflow/definition';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const DeployDialog = defineAsyncComponent(() => import('./deploy-dialog.vue'));

const { t } = useI18n();

const deployDialogRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);
const diagramVisible = ref(false);
const diagramUrl = ref('');

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		name: '',
		key: '',
	},
	pageList: pageList,
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
		await useMessageBox().confirm(t('common.delConfirmText'));
	} catch {
		return;
	}

	try {
		await delObj(ids);
		getDataList();
		useMessage().success(t('common.delSuccessText'));
	} catch (err: any) {
		useMessage().error(err.msg);
	}
};

const handleSuspend = async (id: string) => {
	try {
		await useMessageBox().confirm('确认挂起该流程定义吗？挂起后将无法发起新的流程实例。');
		await suspend(id);
		useMessage().success('挂起成功');
		getDataList();
	} catch (err: any) {
		if (err !== 'cancel') {
			useMessage().error(err.msg || '挂起失败');
		}
	}
};

const handleActivate = async (id: string) => {
	try {
		await activate(id);
		useMessage().success('激活成功');
		getDataList();
	} catch (err: any) {
		useMessage().error(err.msg || '激活失败');
	}
};

const handleViewDiagram = async (processDefinitionId: string) => {
	try {
		const res = await getDiagram(processDefinitionId);
		if (res.code === 0) {
			diagramUrl.value = res.data;
			diagramVisible.value = true;
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取流程图失败');
	}
};
</script>

<style lang="scss" scoped>
.diagram-container {
	display: flex;
	justify-content: center;
	align-items: center;
	min-height: 600px;
}
</style>
