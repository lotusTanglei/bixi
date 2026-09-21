<template>
	<el-dialog v-model="visible" title="请假申请详情" width="min(760px, 94vw)">
		<div v-loading="loading">
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-descriptions v-if="request" :column="1" border>
				<el-descriptions-item label="申请编号">{{ request.id }}</el-descriptions-item>
				<el-descriptions-item label="状态">{{ leaveStatusLabels[request.leaveStatus] }}</el-descriptions-item>
				<el-descriptions-item label="请假日期">{{ request.startDate }} 至 {{ request.endDate }}</el-descriptions-item>
				<el-descriptions-item label="请假原因"><span class="reason">{{ request.reason }}</span></el-descriptions-item>
				<el-descriptions-item label="审批人编号">{{ request.approverId }}</el-descriptions-item>
				<el-descriptions-item label="提交时间">{{ request.submittedAt || '尚未提交' }}</el-descriptions-item>
				<el-descriptions-item label="结束时间">{{ request.endedAt || '—' }}</el-descriptions-item>
			</el-descriptions>
			<el-divider content-position="left">审批记录</el-divider>
			<approval-history :records="records" />
		</div>
	</el-dialog>
</template>

<script setup lang="ts">
import { getObj, history, leaveStatusLabels, type LeaveRequest } from '/@/api/demo/leave';
import ApprovalHistory from '/@/views/workflow/components/approval-history.vue';
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const request = ref<LeaveRequest>();
const records = ref<any[]>([]);
let requestVersion = 0;
watch(visible, isOpen => { if (!isOpen) requestVersion++; }, { flush: 'sync' });
onBeforeUnmount(() => { requestVersion++; });
const openDialog = async (id: string) => {
	const version = ++requestVersion;
	visible.value = true; loading.value = true; error.value = ''; request.value = undefined; records.value = [];
	try {
		const { data } = await getObj(id);
		if (version !== requestVersion) return;
		request.value = data;
		if (data?.processInstanceId) {
			const result = await history(id);
			if (version === requestVersion) records.value = result.data || [];
		}
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || '加载详情失败';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};
defineExpose({ openDialog });
</script>
<style scoped>.reason { white-space: pre-wrap; overflow-wrap: anywhere; }</style>
