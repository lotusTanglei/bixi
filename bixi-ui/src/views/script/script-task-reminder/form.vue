<template>
	<div class="script-task-reminder-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.taskId')" prop="taskId">
							<el-input v-model="dataForm.taskId" :placeholder="$t('scriptTaskReminder.inputTaskIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.title')" prop="title">
							<el-input v-model="dataForm.title" :placeholder="$t('scriptTaskReminder.inputTitleTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.remindTime')" prop="remindTime">
							<el-date-picker v-model="dataForm.remindTime" type="datetime" :placeholder="$t('scriptTaskReminder.selectRemindTimeTip')" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.channel')" prop="channel">
							<el-select v-model="dataForm.channel" :placeholder="$t('scriptTaskReminder.selectChannelTip')" clearable class="w100">
								<el-option label="邮件" value="email" />
								<el-option label="短信" value="sms" />
								<el-option label="站内信" value="message" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptTaskReminder.selectStatusTip')" clearable class="w100">
								<el-option label="待发送" value="0" />
								<el-option label="已发送" value="1" />
								<el-option label="发送失败" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptTaskReminder.content')" prop="content">
							<el-input type="textarea" v-model="dataForm.content" :placeholder="$t('scriptTaskReminder.inputContentTip')" :rows="3" />
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

<script lang="ts" setup name="scriptTaskReminderDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-task-reminder';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	taskId: '',
	remindTime: '',
	channel: 'message',
	status: '0',
	title: '',
	content: '',
});

const dataRules = ref({
	taskId: [{ required: true, message: t('scriptTaskReminder.inputTaskIdTip'), trigger: 'blur' }],
	remindTime: [{ required: true, message: t('scriptTaskReminder.selectRemindTimeTip'), trigger: 'blur' }],
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
