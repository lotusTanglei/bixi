<template>
	<el-dialog :close-on-click-modal="false" :title="form.id ? '编辑表单' : '新增表单'" width="600" draggable v-model="visible">
		<el-form :model="form" :rules="dataRules" label-width="90px" ref="dataFormRef" v-loading="loading">
			<el-form-item label="表单名称" prop="formName">
				<el-input placeholder="请输入表单名称" clearable v-model="form.formName"></el-input>
			</el-form-item>
			<el-form-item label="表单标识" prop="formKey">
				<el-input
					placeholder="请输入表单标识"
					:disabled="form.id !== ''"
					clearable
					v-model="form.formKey"
				></el-input>
			</el-form-item>
			<el-form-item label="状态" prop="status">
				<el-radio-group v-model="form.status">
					<el-radio :label="1">启用</el-radio>
					<el-radio :label="0">禁用</el-radio>
				</el-radio-group>
			</el-form-item>
			<el-form-item label="描述" prop="description">
				<el-input
					placeholder="请输入表单描述"
					maxlength="200"
					rows="3"
					type="textarea"
					v-model="form.description"
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
</template>

<script lang="ts" name="workflowFormDialog" setup>
import { useMessage } from '/@/hooks/message';
import { createForm, getFormByKey, updateForm } from '/@/api/workflow/form';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const form = reactive({
	id: '',
	formName: '',
	formKey: '',
	description: '',
	status: 1,
});

const dataRules = ref({
	formName: [
		{ required: true, message: '表单名称不能为空', trigger: 'blur' },
		{ min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' },
	],
	formKey: [
		{ required: true, message: '表单标识不能为空', trigger: 'blur' },
		{ min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' },
		{ pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '只能包含字母、数字和下划线，且以字母开头', trigger: 'blur' },
	],
});

const openDialog = async (id?: string) => {
	visible.value = true;
	form.id = '';

	nextTick(() => {
		dataFormRef.value?.resetFields();
	});

	if (id) {
		form.id = id;
		await getFormData(id);
	}
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;

	try {
		loading.value = true;
		form.id ? await updateForm(form) : await createForm(form);
		useMessage().success(form.id ? '编辑成功' : '新增成功');
		visible.value = false;
		emit('refresh');
	} catch (err: any) {
		useMessage().error(err.msg);
	} finally {
		loading.value = false;
	}
};

const getFormData = async (id: string) => {
	try {
		loading.value = true;
		const res = await getFormByKey(id);
		Object.assign(form, res.data);
	} catch (err: any) {
		useMessage().error(err.msg);
	} finally {
		loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>
