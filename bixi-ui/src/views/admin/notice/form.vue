<template>
	<div class="sys-notice-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="90px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('notice.title')" prop="title">
							<el-input v-model="dataForm.title" :placeholder="$t('notice.inputTitleTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('notice.type')" prop="type">
							<el-select v-model="dataForm.type" :placeholder="$t('notice.inputTypeTip')" clearable class="w100">
								<el-option label="通知" value="0" />
								<el-option label="公告" value="1" />
								<el-option label="私信" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('notice.priority')" prop="priority">
							<el-select v-model="dataForm.priority" :placeholder="$t('notice.inputPriorityTip')" clearable class="w100">
								<el-option label="普通" value="0" />
								<el-option label="重要" value="1" />
								<el-option label="紧急" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('notice.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('notice.inputStatusTip')" clearable class="w100">
								<el-option label="草稿" value="0" />
								<el-option label="已发布" value="1" />
								<el-option label="已撤回" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('notice.content')" prop="content">
							<el-input type="textarea" v-model="dataForm.content" :placeholder="$t('notice.inputContentTip')" :rows="5" />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('notice.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" placeholder="请输入备注" :rows="3" />
						</el-form-item>
					</el-col>
				</el-row>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="visible = false">{{ $t('common.cancelButtonText') }}</el-button>
					<el-button @click="onSubmit" type="primary" :disabled="loading">{{ $t('common.confirmButtonText') }}</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" setup name="sysNoticeDialog">
import { addObj, getObj, putObj } from '/@/api/admin/notice';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	title: '',
	content: '',
	type: '0',
	priority: '0',
	status: '0',
	remark: '',
});

const dataRules = ref({
	title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
	type: [{ required: true, message: '请选择类型', trigger: 'change' }],
});

const openDialog = async (id: string) => {
	visible.value = true;
	dataForm.id = '';
	nextTick(() => {
		dataFormRef.value?.resetFields();
	});

	if (id) {
		dataForm.id = id;
		await getDetail(id);
	}
};

const getDetail = async (id: string) => {
	loading.value = true;
	try {
		const { data } = await getObj(id);
		Object.assign(dataForm, data);
	} catch (err: any) {
		useMessage().error(err.msg);
	} finally {
		loading.value = false;
	}
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return;

	loading.value = true;
	try {
		if (dataForm.id) {
			await putObj(dataForm);
			useMessage().success(t('common.editSuccessText'));
		} else {
			await addObj(dataForm);
			useMessage().success(t('common.addSuccessText'));
		}
		visible.value = false;
		emit('refresh');
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
