<template>
	<div class="system-task-detail-dialog-container">
		<el-dialog :close-on-click-modal="false" title="任务详情" draggable v-model="visible" width="800px">
			<el-descriptions :column="2" border>
				<el-descriptions-item label="流程名称">{{ taskData.processName }}</el-descriptions-item>
				<el-descriptions-item label="任务名称">{{ taskData.taskName }}</el-descriptions-item>
				<el-descriptions-item label="发起人">{{ taskData.startUser }}</el-descriptions-item>
				<el-descriptions-item label="任务创建时间">{{ taskData.createTime }}</el-descriptions-item>
			</el-descriptions>

			<el-divider content-position="left">表单数据</el-divider>
			<el-descriptions v-if="taskData.formData" :column="2" border>
				<el-descriptions-item v-for="(value, key) in taskData.formData" :key="key" :label="key">
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
					:type="item.result === '通过' ? 'success' : 'danger'"
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

<script lang="ts" name="workflowTaskDetailDialog" setup>
import { getObj, getCommentList } from '/@/api/workflow/task';
import { getHistory } from '/@/api/workflow/process';
import { useMessage } from '/@/hooks/message';

const visible = ref(false);
const taskData = ref<any>({});
const historyList = ref<any[]>([]);

const openDialog = async (row: any) => {
	visible.value = true;
	taskData.value = {};
	historyList.value = [];

	try {
		const res = await getObj(row.id);
		if (res.code === 0) {
			taskData.value = res.data || {};
		}

		if (taskData.value.processInstanceId) {
			const historyRes = await getHistory(taskData.value.processInstanceId);
			if (historyRes.code === 0) {
				historyList.value = historyRes.data || [];
			}
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取任务详情失败');
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
