<template>
	<div class="system-approve-dialog-container">
		<el-dialog :close-on-click-modal="false" title="审批" draggable v-model="visible" width="min(600px, 94vw)"
			:before-close="closeDialog" :close-on-press-escape="!loading" :show-close="!loading">
			<el-alert v-if="pendingIntent" title="已恢复上次提交的原内容。可查询结果或按原内容重试；修改内容前请确认开始新操作。" type="warning" :closable="false" class="mb16" />
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-form :model="dataForm" :rules="dataRules" :disabled="loading || !!pendingIntent || !storageReady" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="任务名称">
					<el-input v-model="taskData.taskName" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程名称">
					<el-input v-model="taskData.processName" disabled></el-input>
				</el-form-item>
				<el-form-item label="审批结果" prop="result">
					<el-radio-group v-model="dataForm.result">
						<el-radio label="通过">通过</el-radio>
						<el-radio label="拒绝">拒绝并结束</el-radio>
					</el-radio-group>
				</el-form-item>
				<el-form-item label="审批意见" prop="comment">
					<el-input
						v-model="dataForm.comment"
						type="textarea"
						:rows="4"
						placeholder="请输入审批意见"
						maxlength="500"
						show-word-limit
					></el-input>
				</el-form-item>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button :disabled="loading" @click="closeDialog()">取消</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_view'" :disabled="loading" @click="queryResult">查询结果</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_edit'" :disabled="loading" @click="newIntent">修改为新操作</el-button>
					<el-button v-auth="'workflow_task_edit'" @click="onSubmit" type="primary" :disabled="loading || !storageReady">{{ pendingIntent ? '重试本次操作' : '确定' }}</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowApproveDialog" setup>
import { useWorkflowIntentDialog } from '/@/utils/workflow-intent-dialog';
const emit = defineEmits(['refresh']);
const dataFormRef = ref();
const taskData = ref<any>({});
const dataForm = reactive({ taskId: '', result: '通过', comment: '' });
const dataRules = ref({
	result: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
	comment: [{ required: true, message: '请输入审批意见', trigger: 'blur' }],
});
const { visible, loading, error, storageReady, pendingIntent, closeDialog, openIntent, submitIntent, queryResult, newIntent } = useWorkflowIntentDialog({
	operations: ['COMPLETE', 'REJECT'],
	restore: intent => { dataForm.taskId = intent.resourceId; dataForm.result = intent.operation === 'COMPLETE' ? '通过' : '拒绝'; dataForm.comment = intent.request.approvalComment ?? intent.request.rejectReason; },
	reset: () => { taskData.value = {}; Object.assign(dataForm, { taskId: '', result: '通过', comment: '' }); },
	refresh: () => emit('refresh'),
});
const openDialog = (row: any) => {
	if (loading.value) return;
	taskData.value = { ...row };
	Object.assign(dataForm, { taskId: row.taskId, result: '通过', comment: '' });
	openIntent(row);
	nextTick(() => dataFormRef.value?.clearValidate());
};
const onSubmit = () => submitIntent({
	operation: dataForm.result === '通过' ? 'COMPLETE' : 'REJECT',
	payload: dataForm.result === '通过' ? { approvalComment: dataForm.comment } : { rejectReason: dataForm.comment },
}, () => dataFormRef.value.validate());
defineExpose({ openDialog });
</script>
