<template>
	<el-dialog v-model="visible" title="流程进度" width="min(1100px, 94vw)" destroy-on-close>
		<div v-loading="loading">
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-row :gutter="20">
				<el-col :xs="24" :md="16"><el-image v-if="diagramUrl" :src="diagramUrl" fit="contain" style="width: 100%; min-height: 240px" /><el-empty v-else description="暂无流程图" /></el-col>
				<el-col :xs="24" :md="8"><h3>审批记录</h3><approval-history :records="records" /></el-col>
			</el-row>
		</div>
	</el-dialog>
</template>
<script lang="ts" name="workflowProcessDialog" setup>
import { getDiagram, getHistory } from '/@/api/workflow/process';
import ApprovalHistory from '../components/approval-history.vue';
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const diagramUrl = ref('');
const records = ref<any[]>([]);
let requestVersion = 0;
watch(visible, isOpen => { if (!isOpen) requestVersion++; }, { flush: 'sync' });
onBeforeUnmount(() => { requestVersion++; });
const openDialog = async (processInstanceId: string) => {
	const version = ++requestVersion;
	visible.value = true; loading.value = true; error.value = ''; diagramUrl.value = ''; records.value = [];
	try {
		const diagram = (await getDiagram(processInstanceId)).data;
		if (version !== requestVersion) return;
		diagramUrl.value = diagram ? `data:image/png;base64,${diagram}` : '';
		const history = await getHistory(processInstanceId);
		if (version === requestVersion) records.value = history.data || [];
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || '加载流程进度失败';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};
defineExpose({ openDialog });
</script>
