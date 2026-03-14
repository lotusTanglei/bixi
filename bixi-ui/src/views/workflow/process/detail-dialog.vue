<template>
	<div class="system-process-detail-dialog-container">
		<el-dialog :close-on-click-modal="false" title="流程实例详情" draggable v-model="visible" width="900px">
			<el-descriptions :column="2" border>
				<el-descriptions-item label="流程名称">{{ processData.processName }}</el-descriptions-item>
				<el-descriptions-item label="流程实例ID">{{ processData.processInstanceId }}</el-descriptions-item>
				<el-descriptions-item label="发起人">{{ processData.startUser }}</el-descriptions-item>
				<el-descriptions-item label="流程状态">
					<el-tag :type="getStatusType(processData.status)">
						{{ getStatusText(processData.status) }}
					</el-tag>
				</el-descriptions-item>
				<el-descriptions-item label="发起时间">{{ processData.startTime }}</el-descriptions-item>
				<el-descriptions-item label="结束时间">{{ processData.endTime || '-' }}</el-descriptions-item>
			</el-descriptions>

			<el-divider content-position="left">表单数据</el-divider>
			<el-descriptions v-if="processData.formData" :column="2" border>
				<el-descriptions-item v-for="(value, key) in processData.formData" :key="key" :label="key">
					{{ value }}
				</el-descriptions-item>
			</el-descriptions>
			<el-empty v-else description="暂无表单数据" />

			<el-divider content-position="left">审批历史</el-divider>
			<el-timeline v-if="historyList.length > 0">
				<el-timeline-item
					v-for="item in historyList"
					:key="item.id"
					:timestamp="item.endTime"
					placement="top"
					:type="getTimelineType(item.result)"
				>
					<el-card>
						<h4>{{ item.taskName }}</h4>
						<p>审批人：{{ item.assignee }}</p>
						<p>审批结果：{{ item.result }}</p>
						<p v-if="item.comment">审批意见：{{ item.comment }}</p>
					</el-card>
				</el-timeline-item>
			</el-timeline>
			<el-empty v-else description="暂无审批历史" />
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowProcessDetailDialog" setup>
import { getObj, getHistory } from '/@/api/workflow/process';
import { useMessage } from '/@/hooks/message';

const visible = ref(false);
const processData = ref<any>({});
const historyList = ref<any[]>([]);

const getStatusType = (status: string) => {
	const statusMap: Record<string, string> = {
		running: 'warning',
		finished: 'success',
		canceled: 'info',
	};
	return statusMap[status] || 'info';
};

const getStatusText = (status: string) => {
	const statusMap: Record<string, string> = {
		running: '进行中',
		finished: '已完成',
		canceled: '已取消',
	};
	return statusMap[status] || status;
};

const getTimelineType = (result: string) => {
	if (result === '通过') return 'success';
	if (result === '驳回') return 'danger';
	return 'primary';
};

const openDialog = async (row: any) => {
	visible.value = true;
	processData.value = {};
	historyList.value = [];

	try {
		const res = await getObj(row.processInstanceId || row.id);
		if (res.code === 0) {
			processData.value = res.data || {};
		}

		if (processData.value.processInstanceId) {
			const historyRes = await getHistory(processData.value.processInstanceId);
			if (historyRes.code === 0) {
				historyList.value = historyRes.data || [];
			}
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取流程详情失败');
	}
};

defineExpose({
	openDialog,
});
</script>

<style lang="scss" scoped>
.el-timeline {
	padding: 20px 0;
}
</style>
