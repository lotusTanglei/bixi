<template>
	<el-dialog v-model="visible" title="流程详情" width="min(840px, 94vw)">
		<div v-loading="loading">
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-descriptions v-if="processData.processInstanceId" :column="1" border>
				<el-descriptions-item label="流程标题">{{ processData.title || processData.processKey }}</el-descriptions-item>
				<el-descriptions-item label="流程实例">{{ processData.processInstanceId }}</el-descriptions-item>
				<el-descriptions-item label="发起人">{{ processData.startUserName }}</el-descriptions-item>
				<el-descriptions-item label="状态">{{ statusLabels[processData.status] || processData.status }}</el-descriptions-item>
				<el-descriptions-item label="发起时间">{{ processData.createTime }}</el-descriptions-item>
				<el-descriptions-item label="结束时间">{{ processData.endTime || '—' }}</el-descriptions-item>
			</el-descriptions>
			<el-divider v-if="leave" content-position="left">请假申请</el-divider>
			<el-descriptions v-if="leave" :column="1" border>
				<el-descriptions-item label="请假日期">{{ leave.startDate }} 至 {{ leave.endDate }}</el-descriptions-item>
				<el-descriptions-item label="请假原因"><span class="reason">{{ leave.reason }}</span></el-descriptions-item>
				<el-descriptions-item label="申请状态">{{ leaveStatusLabels[leave.leaveStatus] }}</el-descriptions-item>
			</el-descriptions>
			<el-divider content-position="left">审批记录</el-divider>
			<approval-history :records="records" />
		</div>
	</el-dialog>
</template>
<script lang="ts" name="workflowProcessDetailDialog" setup>
import { getObj, getHistory } from '/@/api/workflow/process';
import { getObj as getLeave, leaveStatusLabels } from '/@/api/demo/leave';
import { auth } from '/@/utils/authFunction';
import ApprovalHistory from '../components/approval-history.vue';
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const processData = ref<any>({});
const leave = ref<any>();
const records = ref<any[]>([]);
let requestVersion = 0;
watch(visible, isOpen => { if (!isOpen) requestVersion++; }, { flush: 'sync' });
onBeforeUnmount(() => { requestVersion++; });
const statusLabels: Record<string, string> = { running: '审批中', completed: '已完成', rejected: '已拒绝', terminated: '已取消', suspended: '已挂起' };
const openDialog = async (row: { processInstanceId: string }) => {
	const version = ++requestVersion;
	visible.value = true; loading.value = true; error.value = ''; processData.value = {}; leave.value = undefined; records.value = [];
	try {
		const { data } = await getObj(row.processInstanceId);
		if (version !== requestVersion) return;
		processData.value = data;
		const history = await getHistory(row.processInstanceId);
		if (version !== requestVersion) return;
		records.value = history.data || [];
		if (data.businessTable === 'demo_leave_request' && data.businessId && auth('demo_leave_view')) {
			const result = await getLeave(data.businessId);
			if (version === requestVersion) leave.value = result.data;
		}
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || '加载流程详情失败';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};
defineExpose({ openDialog });
</script>
<style scoped>.reason { white-space: pre-wrap; overflow-wrap: anywhere; }</style>
