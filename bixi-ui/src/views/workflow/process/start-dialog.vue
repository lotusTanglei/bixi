<template>
	<div class="system-start-dialog-container">
		<el-dialog :close-on-click-modal="false" title="发起流程" draggable v-model="visible" width="600px">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="流程名称">
					<el-input v-model="definitionData.name" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程标识">
					<el-input v-model="definitionData.key" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程标题" prop="title">
					<el-input v-model="dataForm.title" placeholder="请输入流程标题" clearable></el-input>
				</el-form-item>
				<el-form-item label="备注" prop="remark">
					<el-input
						v-model="dataForm.remark"
						type="textarea"
						:rows="4"
						placeholder="请输入备注"
						maxlength="500"
						show-word-limit
					></el-input>
				</el-form-item>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="visible = false">取消</el-button>
					<el-button @click="onSubmit" type="primary" :disabled="loading">提交</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowStartDialog" setup>
import { start } from '/@/api/workflow/process';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);
const definitionData = ref<any>({});

const dataForm = reactive({
	processDefinitionId: '',
	title: '',
	remark: '',
	variables: {} as any,
});

const dataRules = ref({
	title: [{ required: true, message: '请输入流程标题', trigger: 'blur' }],
});

const openDialog = (row: any) => {
	visible.value = true;
	definitionData.value = row;
	dataForm.processDefinitionId = row.id;
	dataForm.title = '';
	dataForm.remark = '';
	dataForm.variables = {};

	nextTick(() => {
		dataFormRef.value?.resetFields();
	});
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;

	loading.value = true;

	try {
		const res = await start({
			processDefinitionId: dataForm.processDefinitionId,
			title: dataForm.title,
			remark: dataForm.remark,
			variables: dataForm.variables,
		});

		if (res.code === 0) {
			useMessage().success('发起成功');
			visible.value = false;
			emit('refresh');
		} else {
			useMessage().error(res.msg || '发起失败');
		}
	} catch (err: any) {
		useMessage().error(err.msg || '发起失败');
	} finally {
		loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>
