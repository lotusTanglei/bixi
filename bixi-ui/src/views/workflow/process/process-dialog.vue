<template>
	<div class="system-process-dialog-container">
		<el-dialog :close-on-click-modal="false" title="流程进度" draggable v-model="visible" width="80%" destroy-on-close>
			<el-row :gutter="20">
				<el-col :span="16">
					<div class="diagram-container">
						<el-image v-if="diagramUrl" :src="diagramUrl" fit="contain" style="width: 100%; height: 600px" />
						<el-empty v-else description="暂无流程图" />
					</div>
				</el-col>
				<el-col :span="8">
					<div class="history-container">
						<h3>审批历史</h3>
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
					</div>
				</el-col>
			</el-row>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowProcessDialog" setup>
import { getDiagram, getHistory } from '/@/api/workflow/process';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);

const visible = ref(false);
const diagramUrl = ref('');
const historyList = ref<any[]>([]);

const getTimelineType = (result: string) => {
	if (result === '通过') return 'success';
	if (result === '驳回') return 'danger';
	return 'primary';
};

const openDialog = async (processInstanceId: string) => {
	visible.value = true;
	diagramUrl.value = '';
	historyList.value = [];

	try {
		const res = await getDiagram(processInstanceId);
		if (res.code === 0) {
			diagramUrl.value = res.data;
		}

		const historyRes = await getHistory(processInstanceId);
		if (historyRes.code === 0) {
			historyList.value = historyRes.data || [];
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取流程进度失败');
	}
};

defineExpose({
	openDialog,
});
</script>

<style lang="scss" scoped>
.diagram-container {
	display: flex;
	justify-content: center;
	align-items: center;
	min-height: 600px;
	background: #f5f7fa;
	border-radius: 4px;
}

.history-container {
	height: 600px;
	overflow-y: auto;
	padding: 0 10px;

	h3 {
		margin-bottom: 20px;
		padding-bottom: 10px;
		border-bottom: 1px solid #ebeef5;
	}
}
</style>
