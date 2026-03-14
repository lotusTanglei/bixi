<template>
	<el-dialog v-model="visible" title="上传文档" width="500px" :close-on-click-modal="false">
		<el-upload
			ref="uploadRef"
			class="upload-demo"
			drag
			:auto-upload="false"
			:limit="1"
			:file-list="fileList"
			:accept="acceptTypes"
			:on-change="handleFileChange"
			:on-exceed="handleExceed"
		>
			<el-icon class="el-icon--upload"><upload-filled /></el-icon>
			<div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
			<template #tip>
				<div class="el-upload__tip">支持 PDF、Word、TXT、Markdown 格式文件</div>
			</template>
		</el-upload>
		<template #footer>
			<span class="dialog-footer">
				<el-button @click="handleClose">取消</el-button>
				<el-button type="primary" :loading="uploading" @click="handleUpload">确定</el-button>
			</span>
		</template>
	</el-dialog>
</template>

<script lang="ts" name="UploadDialog" setup>
import { UploadFilled } from '@element-plus/icons-vue';
import type { UploadFile, UploadFiles, UploadInstance } from 'element-plus';
import { uploadDocument } from '/@/api/ai/document';
import { useMessage } from '/@/hooks/message';

const visible = ref(false);
const uploading = ref(false);
const fileList = ref<UploadFile[]>([]);
const currentFile = ref<File | null>(null);
const uploadRef = ref<UploadInstance>();

const acceptTypes = '.pdf,.doc,.docx,.txt,.md';

const emit = defineEmits<{
	(e: 'success'): void;
}>();

const show = () => {
	visible.value = true;
	fileList.value = [];
	currentFile.value = null;
};

const handleClose = () => {
	visible.value = false;
	fileList.value = [];
	currentFile.value = null;
};

const handleFileChange = (file: UploadFile, _files: UploadFiles) => {
	currentFile.value = file.raw as File;
};

const handleExceed = () => {
	useMessage().warning('最多只能上传一个文件');
};

const handleUpload = async () => {
	if (!currentFile.value) {
		useMessage().warning('请选择要上传的文件');
		return;
	}

	uploading.value = true;
	try {
		await uploadDocument(currentFile.value);
		useMessage().success('上传成功');
		emit('success');
		handleClose();
	} catch (err: any) {
		useMessage().error(err.msg || '上传失败');
	} finally {
		uploading.value = false;
	}
};

defineExpose({
	show,
});
</script>

<style scoped>
.upload-demo {
	width: 100%;
}
</style>
