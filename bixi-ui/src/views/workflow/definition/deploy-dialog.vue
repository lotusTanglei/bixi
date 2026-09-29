<template>
	<div class="system-deploy-dialog-container">
		<el-dialog :close-on-click-modal="false" title="部署流程" draggable v-model="visible" width="600px">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="流程名称" prop="name">
					<el-input v-model="dataForm.name" placeholder="请输入流程名称" clearable></el-input>
				</el-form-item>
				<el-form-item label="流程分类" prop="category">
					<el-select v-model="dataForm.category" placeholder="请选择流程分类" class="w100" clearable>
						<el-option v-for="item in categoryList" :key="item.id" :label="item.categoryName" :value="item.id" />
					</el-select>
				</el-form-item>
				<el-form-item label="绑定表单" prop="formKey">
					<el-input v-model="dataForm.formKey" maxlength="255" placeholder="请输入已发布表单标识（可选）" clearable />
				</el-form-item>
				<el-form-item label="BPMN文件" prop="file">
					<el-upload
						ref="uploadRef"
						:limit="1"
						:file-list="fileList"
						:on-change="handleFileChange"
						:on-remove="handleFileRemove"
						:on-exceed="handleFileExceed"
						accept=".bpmn,.bpmn20.xml"
						:auto-upload="false"
					>
						<el-button type="primary">选择文件</el-button>
						<template #tip>
							<div class="el-upload__tip">支持 .bpmn/.bpmn20.xml，最大 1 MiB</div>
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
import type { UploadFile, UploadFiles, UploadInstance, UploadUserFile } from 'element-plus';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const uploadRef = ref<UploadInstance>();
const visible = ref(false);
const loading = ref(false);
const categoryList = ref<any[]>([]);
const fileList = ref<UploadUserFile[]>([]);

const dataForm = reactive({
	name: '',
	category: '',
	formKey: '',
	file: null as File | null,
});

const dataRules = ref({
	name: [{ required: true, message: '流程名称不能为空', trigger: 'blur' }],
	file: [{ required: true, message: '请选择 BPMN 文件', trigger: 'change' }],
});

const openDialog = async () => {
	visible.value = true;
	fileList.value = [];
	dataForm.name = '';
	dataForm.category = '';
	dataForm.formKey = '';
	dataForm.file = null;
	uploadRef.value?.clearFiles();

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

const validateFile = (file: File) => {
	const fileName = file.name.toLowerCase();
	const isBpmn = fileName.endsWith('.bpmn') || fileName.endsWith('.bpmn20.xml');
	if (!isBpmn) {
		useMessage().error('只能上传 .bpmn 或 .bpmn20.xml 文件');
		return false;
	}
	if (file.size > 1024 * 1024) {
		useMessage().error('BPMN 文件不能超过 1 MiB');
		return false;
	}
	return true;
};

const handleFileChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
	if (!uploadFile.raw || !validateFile(uploadFile.raw)) {
		dataForm.file = null;
		fileList.value = [];
		uploadRef.value?.clearFiles();
		void dataFormRef.value?.validateField('file').catch(() => {});
		return;
	}
	dataForm.file = uploadFile.raw;
	fileList.value = uploadFiles;
	dataFormRef.value?.clearValidate('file');
};

const handleFileRemove = () => {
	dataForm.file = null;
	void dataFormRef.value?.validateField('file').catch(() => {});
};

const handleFileExceed = () => {
	useMessage().error('一次只能选择一个 BPMN 文件');
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;
	if (!dataForm.file || !validateFile(dataForm.file)) return false;

	loading.value = true;

	try {
		const formData = new FormData();
		formData.append('name', dataForm.name.trim());
		if (dataForm.category) {
			formData.append('category', dataForm.category);
		}
		if (dataForm.formKey.trim()) {
			formData.append('formKey', dataForm.formKey.trim());
		}
		formData.append('file', dataForm.file);

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
