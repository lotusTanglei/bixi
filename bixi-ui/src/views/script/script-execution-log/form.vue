<template>
	<div class="script-execution-log-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.scriptId')" prop="scriptId">
							<el-input v-model="dataForm.scriptId" :placeholder="$t('scriptExecutionLog.inputScriptIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.siteId')" prop="siteId">
							<el-input v-model="dataForm.siteId" :placeholder="$t('scriptExecutionLog.inputSiteIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.taskId')" prop="taskId">
							<el-input v-model="dataForm.taskId" :placeholder="$t('scriptExecutionLog.inputTaskIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptExecutionLog.selectStatusTip')" clearable class="w100">
								<el-option label="成功" value="0" />
								<el-option label="失败" value="1" />
								<el-option label="跳过" value="2" />
								<el-option label="部分成功" value="3" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.executorId')" prop="executorId">
							<el-input v-model="dataForm.executorId" :placeholder="$t('scriptExecutionLog.inputExecutorIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.durationMs')" prop="durationMs">
							<el-input-number v-model="dataForm.durationMs" :placeholder="$t('scriptExecutionLog.inputDurationMsTip')" clearable class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.startTime')" prop="startTime">
							<el-date-picker v-model="dataForm.startTime" type="datetime" :placeholder="$t('scriptExecutionLog.selectStartTimeTip')" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.finishTime')" prop="finishTime">
							<el-date-picker v-model="dataForm.finishTime" type="datetime" :placeholder="$t('scriptExecutionLog.selectFinishTimeTip')" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.errorMsg')" prop="errorMsg">
							<el-input type="textarea" v-model="dataForm.errorMsg" :placeholder="$t('scriptExecutionLog.inputErrorMsgTip')" :rows="2" />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptExecutionLog.logContent')" prop="logContent">
							<el-input type="textarea" v-model="dataForm.logContent" :placeholder="$t('scriptExecutionLog.inputLogContentTip')" :rows="4" />
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

<script lang="ts" setup name="scriptExecutionLogDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-execution-log';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	scriptId: '',
	siteId: '',
	taskId: '',
	status: '0',
	executorId: '',
	startTime: '',
	finishTime: '',
	durationMs: 0,
	logContent: '',
	errorMsg: '',
});

const dataRules = ref({
	scriptId: [{ required: true, message: t('scriptExecutionLog.inputScriptIdTip'), trigger: 'blur' }],
	siteId: [{ required: true, message: t('scriptExecutionLog.inputSiteIdTip'), trigger: 'blur' }],
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
