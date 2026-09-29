<template>
	<div class="system-approve-dialog-container">
		<el-dialog
			:close-on-click-modal="false"
			title="审批"
			draggable
			v-model="visible"
			width="min(600px, 94vw)"
			:before-close="closeDialog"
			:close-on-press-escape="!loading"
			:show-close="!loading"
		>
			<el-alert
				v-if="pendingIntent"
				title="已恢复上次提交的原内容。可查询结果或按原内容重试；修改内容前请确认开始新操作。"
				type="warning"
				:closable="false"
				class="mb16"
			/>
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-form
				:model="dataForm"
				:rules="dataRules"
				:disabled="loading || !!pendingIntent || !storageReady"
				label-width="100px"
				ref="dataFormRef"
				v-loading="loading"
			>
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
					<el-input v-model="dataForm.comment" type="textarea" :rows="4" placeholder="请输入审批意见" maxlength="500" show-word-limit></el-input>
				</el-form-item>
			</el-form>
			<div v-if="runtimeForm" class="runtime-form" v-loading="formLoading">
				<FormRenderer
					ref="formRendererRef"
					:form-schema="runtimeForm.schema"
					:form-data="runtimeForm.data"
					:permissions="runtimeForm.permissions"
					:readonly="!!pendingIntent"
				/>
			</div>
			<template #footer>
				<span class="dialog-footer">
					<el-button :disabled="loading" @click="closeDialog()">取消</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_view'" :disabled="loading" @click="queryResult">查询结果</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_edit'" :disabled="loading" @click="newIntent">修改为新操作</el-button>
					<el-button v-auth="'workflow_task_edit'" @click="onSubmit" type="primary" :disabled="loading || formLoading || !storageReady">{{
						pendingIntent ? '重试本次操作' : '确定'
					}}</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowApproveDialog" setup>
import { useWorkflowIntentDialog } from '/@/utils/workflow-intent-dialog';
import { getTaskForm } from '/@/api/workflow/task';
import FormRenderer from '/@/components/form/FormRenderer.vue';
const emit = defineEmits(['refresh']);
const dataFormRef = ref();
const formRendererRef = ref();
const taskData = ref<any>({});
const runtimeForm = ref<any>();
const formLoading = ref(false);
let formRequestVersion = 0;
const dataForm = reactive({ taskId: '', result: '通过', comment: '' });
const dataRules = ref({
	result: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
	comment: [{ required: true, message: '请输入审批意见', trigger: 'blur' }],
});
const { visible, loading, error, storageReady, pendingIntent, closeDialog, openIntent, submitIntent, queryResult, newIntent } =
	useWorkflowIntentDialog({
		operations: ['COMPLETE', 'REJECT'],
		restore: (intent) => {
			dataForm.taskId = intent.resourceId;
			dataForm.result = intent.operation === 'COMPLETE' ? '通过' : '拒绝';
			dataForm.comment = intent.request.approvalComment ?? intent.request.rejectReason;
		},
		reset: () => {
			formRequestVersion++;
			taskData.value = {};
			runtimeForm.value = undefined;
			Object.assign(dataForm, { taskId: '', result: '通过', comment: '' });
		},
		refresh: () => emit('refresh'),
	});

watch(
	visible,
	(open) => {
		if (!open) formRequestVersion++;
	},
	{ flush: 'sync' }
);

const parseRuntimeForm = (value: any) => {
	if (!value) return undefined;
	const pendingData = pendingIntent.value?.request?.formDataJson;
	const schema = JSON.parse(value.schemaJson || '{"widgetList":[],"formConfig":{}}');
	const data = JSON.parse(pendingData ?? value.dataJson ?? '{}');
	if (!schema || typeof schema !== 'object' || Array.isArray(schema) || !data || typeof data !== 'object' || Array.isArray(data)) {
		throw new Error('任务表单数据格式无效');
	}
	return { ...value, schema, data, permissions: value.permissions || {} };
};

const openDialog = async (row: any) => {
	if (loading.value) return;
	taskData.value = { ...row };
	Object.assign(dataForm, { taskId: row.taskId, result: '通过', comment: '' });
	if (!openIntent(row)) return;
	const version = ++formRequestVersion;
	runtimeForm.value = undefined;
	nextTick(() => dataFormRef.value?.clearValidate());
	if (typeof getTaskForm !== 'function') return;
	formLoading.value = true;
	try {
		const response = await getTaskForm(row.taskId);
		if (version !== formRequestVersion) return;
		if (response.code !== 0) throw new Error(response.msg || '获取任务表单失败');
		runtimeForm.value = parseRuntimeForm(response.data);
	} catch (err: any) {
		if (version === formRequestVersion) {
			error.value = err?.msg || err?.message || '获取任务表单失败';
			if (!pendingIntent.value) storageReady.value = false;
		}
	} finally {
		if (version === formRequestVersion) formLoading.value = false;
	}
};
const onSubmit = () => {
	const operation = dataForm.result === '通过' ? 'COMPLETE' : 'REJECT';
	const formSnapshot = operation === 'COMPLETE' && runtimeForm.value ? formRendererRef.value?.getFormDataSnapshot() : undefined;
	const payload =
		operation === 'COMPLETE'
			? {
					approvalComment: dataForm.comment,
					...(runtimeForm.value
						? {
								formId: runtimeForm.value.formId,
								formDataJson: JSON.stringify(formSnapshot || {}),
							}
						: {}),
				}
			: { rejectReason: dataForm.comment };
	return submitIntent({ operation, payload }, async () => {
		const valid = await dataFormRef.value.validate();
		if (!valid || operation !== 'COMPLETE' || !runtimeForm.value) return valid;
		return formRendererRef.value?.validateForm() ?? false;
	});
};
defineExpose({ openDialog });
</script>

<style scoped>
.runtime-form {
	margin-top: 16px;
}
</style>
