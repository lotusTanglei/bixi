<template>
	<div class="system-deploy-dialog-container">
		<el-dialog :close-on-click-modal="false" title="部署流程" draggable v-model="visible" width="600px">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="流程名称" prop="name">
					<el-input v-model="dataForm.name" placeholder="请输入流程名称" clearable></el-input>
				</el-form-item>
				<el-form-item label="流程分类" prop="category">
					<el-select v-model="dataForm.category" placeholder="请选择流程分类" class="w100" clearable>
						<el-option v-for="item in categoryList" :key="item.id" :label="item.name" :value="item.id" />
					</el-select>
				</el-form-item>
				<el-form-item label="BPMN文件" prop="file">
					<el-upload
						ref="uploadRef"
						:action="uploadUrl"
						:headers="headers"
						:limit="1"
						:file-list="fileList"
						:on-success="handleUploadSuccess"
						:on-error="handleUploadError"
						:before-upload="beforeUpload"
						accept=".bpmn,.bpmn20.xml"
						:auto-upload="false"
					>
						<el-button type="primary">选择文件</el-button>
						<template #tip>
							<div class="el-upload__tip">只能上传 bpmn/bpmn20.xml 文件</div>
						</template>
					</el-upload>
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

<script lang="ts" name="workflowDeployDialog" setup>
import { deploy } from '/@/api/workflow/definition';
import { list as categoryListApi } from '/@/api/workflow/category';
import { useMessage } from '/@/hooks/message';
import { Session } from '/@/utils/storage';
import type { UploadInstance } from 'element-plus';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const uploadRef = ref<UploadInstance>();
const visible = ref(false);
const loading = ref(false);
const categoryList = ref<any[]>([]);
const fileList = ref<any[]>([]);

const uploadUrl = import.meta.env.VITE_API_URL + '/workflow/definition/deploy';
const headers = {
	Authorization: 'Bearer ' + Session.get('token'),
};

const dataForm = reactive({
	name: '',
	category: '',
	file: null as any,
});

const dataRules = ref({
	name: [{ required: true, message: '流程名称不能为空', trigger: 'blur' }],
	category: [{ required: true, message: '流程分类不能为空', trigger: 'change' }],
});

const openDialog = async () => {
	visible.value = true;
	fileList.value = [];
	dataForm.name = '';
	dataForm.category = '';
	dataForm.file = null;

	nextTick(() => {
		dataFormRef.value?.resetFields();
	});

	await getCategoryList();
};

const getCategoryList = async () => {
	try {
		const res = await categoryListApi();
		if (res.code === 0) {
			categoryList.value = res.data || [];
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取分类列表失败');
	}
};

const beforeUpload = (file: any) => {
	const isBpmn = file.name.endsWith('.bpmn') || file.name.endsWith('.bpmn20.xml');
	if (!isBpmn) {
		useMessage().error('只能上传 BPMN 文件!');
		return false;
	}
	return true;
};

const handleUploadSuccess = (response: any) => {
	if (response.code === 0) {
		useMessage().success('部署成功');
		visible.value = false;
		emit('refresh');
	} else {
		useMessage().error(response.msg || '部署失败');
	}
	loading.value = false;
};

const handleUploadError = () => {
	useMessage().error('上传失败');
	loading.value = false;
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;

	loading.value = true;

	try {
		const formData = new FormData();
		formData.append('name', dataForm.name);
		formData.append('category', dataForm.category);

		const uploadFiles = uploadRef.value?.uploadFiles;
		if (uploadFiles && uploadFiles.length > 0) {
			formData.append('file', uploadFiles[0].raw);
		}

		const res = await deploy(formData);
		if (res.code === 0) {
			useMessage().success('部署成功');
			visible.value = false;
			emit('refresh');
		} else {
			useMessage().error(res.msg || '部署失败');
		}
	} catch (err: any) {
		useMessage().error(err.msg || '部署失败');
	} finally {
		loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>
