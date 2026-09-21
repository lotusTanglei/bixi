<template>
	<div class="layout-padding"><div class="layout-padding-auto layout-padding-view">
		<el-form inline @submit.prevent="getDataList">
			<el-form-item label="流程名称"><el-input v-model="state.queryForm.processName" clearable /></el-form-item>
			<el-form-item label="流程标识"><el-input v-model="state.queryForm.processKey" clearable /></el-form-item>
			<el-form-item><el-button icon="Search" @click="getDataList">查询</el-button></el-form-item>
		</el-form>
		<div class="mb16"><el-button v-auth="'workflow_definition_edit'" type="primary" :loading="busy" @click="deployExample">部署请假示例</el-button></div>
		<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
		<el-table :data="state.dataList" v-loading="state.loading || busy" border empty-text="暂无流程定义">
			<el-table-column label="流程名称" prop="processName" min-width="180" show-overflow-tooltip />
			<el-table-column label="流程标识" prop="processKey" min-width="180" show-overflow-tooltip />
			<el-table-column label="版本" prop="version" width="80" />
			<el-table-column label="状态" width="100"><template #default="{ row }">{{ row.suspensionState === 1 ? '可发起' : '已挂起' }}</template></el-table-column>
			<el-table-column label="操作" width="180" fixed="right"><template #default="{ row }">
				<el-button v-auth="'workflow_definition_edit'" text type="primary" @click="changeState(row)">{{ row.suspensionState === 1 ? '挂起' : '激活' }}</el-button>
			</template></el-table-column>
		</el-table>
	</div></div>
</template>
<script lang="ts" name="workflowDefinition" setup>
import { list, deployDemo, suspend, activate } from '/@/api/workflow/definition';
import { type BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
const state = reactive<BasicTableProps>({ queryForm: {}, isPage: false, pageList: list });
const { getDataList } = useTable(state);
const busy = ref(false); const error = ref('');
const run = async (action: () => Promise<unknown>, message: string) => {
	busy.value = true; error.value = '';
	try { await action(); useMessage().success(message); }
	catch (err: any) { error.value = err?.msg || '操作失败'; }
	finally { busy.value = false; getDataList(); }
};
const deployExample = () => run(() => deployDemo(), '请假示例已部署');
const changeState = async (row: any) => {
	try { await useMessageBox().confirm(row.suspensionState === 1 ? '挂起后不可发起新的申请，现有实例继续办理。确认挂起？' : '确认激活此流程定义？'); }
	catch { return; }
	await run(() => (row.suspensionState === 1 ? suspend : activate)(row.processDefinitionId), '流程定义状态已更新');
};
</script>
