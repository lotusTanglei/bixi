<template>
	<div class="sys-user-notice-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="90px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('userNotice.noticeId')" prop="noticeId">
							<el-input v-model="dataForm.noticeId" :placeholder="$t('userNotice.inputNoticeIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('userNotice.userId')" prop="userId">
							<el-input v-model="dataForm.userId" :placeholder="$t('userNotice.inputUserIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('userNotice.isRead')" prop="isRead">
							<el-select v-model="dataForm.isRead" :placeholder="$t('userNotice.inputIsReadTip')" clearable class="w100">
								<el-option label="未读" value="0" />
								<el-option label="已读" value="1" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('userNotice.readTime')" prop="readTime">
							<el-date-picker v-model="dataForm.readTime" type="datetime" placeholder="选择日期时间" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('userNotice.remark')" prop="remark">
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

<script lang="ts" setup name="sysUserNoticeDialog">
import { addObj, getObj, putObj } from '/@/api/admin/user-notice';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	noticeId: '',
	userId: '',
	isRead: '0',
	readTime: '',
	remark: '',
});

const dataRules = ref({
	noticeId: [{ required: true, message: '请输入通知ID', trigger: 'blur' }],
	userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
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
