<template>
	<div class="system-approve-dialog-container">
		<el-dialog :close-on-click-modal="false" title="审批" draggable v-model="visible" width="600px">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="任务名称">
					<el-input v-model="taskData.taskName" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程名称">
					<el-input v-model="taskData.processName" disabled></el-input>
				</el-form-item>
				<el-form-item label="审批结果" prop="result">
					<el-radio-group v-model="dataForm.result">
						<el-radio label="通过">通过</el-radio>
						<el-radio label="驳回">驳回</el-radio>
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
					<el-button @click="visible = false">取消</el-button>
					<el-button @click="onSubmit" type="primary" :disabled="loading">确定</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowApproveDialog" setup>
import { complete, reject } from '/@/api/workflow/task';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);
const taskData = ref<any>({});

const dataForm = reactive({
	taskId: '',
	result: '通过',
	comment: '',
});

const dataRules = ref({
	result: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
	comment: [{ required: true, message: '请输入审批意见', trigger: 'blur' }],
});

const openDialog = (row: any) => {
	visible.value = true;
	taskData.value = row;
	dataForm.taskId = row.id;
	dataForm.result = '通过';
	dataForm.comment = '';

	nextTick(() => {
		dataFormRef.value?.resetFields();
	});
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;

	loading.value = true;

	try {
		const params = {
			taskId: dataForm.taskId,
			comment: dataForm.comment,
		};

		let res;
		if (dataForm.result === '通过') {
			res = await complete(params);
		} else {
			res = await reject(params);
		}

		if (res.code === 0) {
			useMessage().success('审批成功');
			visible.value = false;
			emit('refresh');
		} else {
			useMessage().error(res.msg || '审批失败');
		}
	} catch (err: any) {
		useMessage().error(err.msg || '审批失败');
	} finally {
		loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>
