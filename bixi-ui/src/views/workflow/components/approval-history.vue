<template>
	<el-timeline v-if="records.length" class="approval-history">
		<el-timeline-item v-for="record in records" :key="record.id" :timestamp="record.approvalTime"
			:type="record.approvalType === 'reject' ? 'danger' : record.approvalType === 'approve' ? 'success' : 'primary'">
			<strong>{{ record.taskName }} · {{ labels[record.approvalType] || record.approvalType }}</strong>
			<p>{{ record.approvalUserName || record.approvalUserId }}</p>
			<p>{{ record.approvalComment || '无附加意见' }}</p>
		</el-timeline-item>
	</el-timeline>
	<el-empty v-else description="暂无审批记录" />
</template>

<script setup lang="ts">
defineProps<{ records: any[] }>();
const labels: Record<string, string> = {
	approve: '通过', reject: '拒绝结束', transfer: '转办', delegate: '委派', resolve: '委派已交回',
};
</script>

<style scoped>
.approval-history { padding: 12px 8px; }
p { white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 6px; }
</style>
