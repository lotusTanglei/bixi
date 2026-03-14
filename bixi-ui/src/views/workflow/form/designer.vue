<template>
	<div class="form-designer-container">
		<div class="designer-header">
			<div class="header-left">
				<el-button icon="Back" @click="handleBack">返回</el-button>
				<span class="form-name">{{ formName }}</span>
			</div>
			<div class="header-right">
				<el-button icon="View" @click="handlePreview">预览</el-button>
				<el-button icon="DocumentChecked" @click="handleSaveVersion" :loading="saving">保存为新版本</el-button>
				<el-button type="primary" icon="Check" @click="handleSave" :loading="saving">保存</el-button>
			</div>
		</div>
		<div class="designer-content">
			<div class="designer-main">
				<v-form-designer ref="vFormDesignerRef" :global-dsv="globalDsv" :designer-config="designerConfig">
				</v-form-designer>
			</div>
		</div>

		<el-dialog v-model="previewVisible" title="表单预览" width="800px" destroy-on-close>
			<FormRenderer ref="formRendererRef" :form-schema="formSchema" />
			<template #footer>
				<el-button @click="previewVisible = false">关闭</el-button>
				<el-button type="primary" @click="handleGetData">获取数据</el-button>
			</template>
		</el-dialog>

		<el-dialog v-model="versionDialogVisible" title="保存新版本" width="500px">
			<el-form :model="versionForm" :rules="versionRules" ref="versionFormRef" label-width="100px">
				<el-form-item label="版本号" prop="version">
					<el-input v-model="versionForm.version" placeholder="请输入版本号，如 1.0.0" />
				</el-form-item>
				<el-form-item label="版本说明" prop="remark">
					<el-input v-model="versionForm.remark" type="textarea" rows="3" placeholder="请输入版本说明" />
				</el-form-item>
			</el-form>
			<template #footer>
				<el-button @click="versionDialogVisible = false">取消</el-button>
				<el-button type="primary" @click="submitVersion" :loading="saving">确定</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowFormDesigner" setup>
import { useMessage } from '/@/hooks/message';
import { getFormByKey, updateForm, createVersion } from '/@/api/workflow/form';
import { useRoute, useRouter } from 'vue-router';

const FormRenderer = defineAsyncComponent(() => import('/@/components/form/FormRenderer.vue'));

const route = useRoute();
const router = useRouter();

const vFormDesignerRef = ref();
const formRendererRef = ref();
const versionFormRef = ref();

const formId = ref('');
const formKey = ref('');
const formName = ref('');
const saving = ref(false);
const previewVisible = ref(false);
const versionDialogVisible = ref(false);
const formSchema = ref({});

const globalDsv = reactive({
	formConfig: {},
});

const designerConfig = reactive({
	languageMenu: false,
	toolbarMinimap: false,
});

const versionForm = reactive({
	version: '',
	remark: '',
});

const versionRules = {
	version: [
		{ required: true, message: '请输入版本号', trigger: 'blur' },
		{ pattern: /^\d+\.\d+\.\d+$/, message: '版本号格式不正确，如 1.0.0', trigger: 'blur' },
	],
};

onMounted(async () => {
	formId.value = route.query.formId as string;
	formKey.value = route.query.formKey as string;
	formName.value = route.query.formName as string || '未命名表单';

	if (formKey.value) {
		await loadFormData();
	}
});

const loadFormData = async () => {
	try {
		const res = await getFormByKey(formKey.value);
		if (res.data && res.data.schemaContent) {
			const schema = JSON.parse(res.data.schemaContent);
			vFormDesignerRef.value?.setFormJson(schema);
			formName.value = res.data.formName;
		}
	} catch (err: any) {
		useMessage().error(err.msg || '加载表单失败');
	}
};

const handleBack = () => {
	router.push('/workflow/form');
};

const handlePreview = () => {
	const json = vFormDesignerRef.value?.getFormJson();
	if (json) {
		formSchema.value = json;
		previewVisible.value = true;
	}
};

const handleGetData = () => {
	formRendererRef.value?.getFormData().then((data: any) => {
		console.log('表单数据:', data);
		useMessage().success('表单数据已打印到控制台');
	});
};

const handleSave = async () => {
	try {
		const json = vFormDesignerRef.value?.getFormJson();
		if (!json) {
			useMessage().warning('表单内容为空');
			return;
		}

		saving.value = true;
		await updateForm({
			id: formId.value,
			schemaContent: JSON.stringify(json),
		});
		useMessage().success('保存成功');
	} catch (err: any) {
		useMessage().error(err.msg || '保存失败');
	} finally {
		saving.value = false;
	}
};

const handleSaveVersion = () => {
	versionDialogVisible.value = true;
	versionForm.version = '';
	versionForm.remark = '';
};

const submitVersion = async () => {
	const valid = await versionFormRef.value?.validate().catch(() => {});
	if (!valid) return;

	try {
		const json = vFormDesignerRef.value?.getFormJson();
		if (!json) {
			useMessage().warning('表单内容为空');
			return;
		}

		saving.value = true;
		await createVersion({
			formId: formId.value,
			version: versionForm.version,
			remark: versionForm.remark,
			schemaContent: JSON.stringify(json),
		});
		useMessage().success('版本保存成功');
		versionDialogVisible.value = false;
	} catch (err: any) {
		useMessage().error(err.msg || '版本保存失败');
	} finally {
		saving.value = false;
	}
};
</script>

<style lang="scss" scoped>
.form-designer-container {
	height: 100vh;
	display: flex;
	flex-direction: column;
	background: #f5f5f5;
}

.designer-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 10px 20px;
	background: #fff;
	border-bottom: 1px solid #e4e7ed;
	box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);

	.header-left {
		display: flex;
		align-items: center;
		gap: 15px;

		.form-name {
			font-size: 16px;
			font-weight: 500;
			color: #303133;
		}
	}

	.header-right {
		display: flex;
		gap: 10px;
	}
}

.designer-content {
	flex: 1;
	overflow: hidden;

	.designer-main {
		height: 100%;
	}
}
</style>
