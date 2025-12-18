<template>
	<div class="script-execution-plan-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="110px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.scriptId')" prop="scriptId">
							<el-input v-model="dataForm.scriptId" :placeholder="$t('scriptExecutionPlan.inputScriptIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.siteId')" prop="siteId">
							<el-input v-model="dataForm.siteId" :placeholder="$t('scriptExecutionPlan.inputSiteIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.planStatus')" prop="planStatus">
							<el-select v-model="dataForm.planStatus" :placeholder="$t('scriptExecutionPlan.selectPlanStatusTip')" clearable class="w100">
								<el-option label="待计划" value="0" />
								<el-option label="已计划" value="1" />
								<el-option label="已执行" value="2" />
								<el-option label="已取消" value="3" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.execOrder')" prop="execOrder">
							<el-input-number v-model="dataForm.execOrder" :placeholder="$t('scriptExecutionPlan.inputExecOrderTip')" clearable class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.scheduleTime')" prop="scheduleTime">
							<el-date-picker v-model="dataForm.scheduleTime" type="datetime" :placeholder="$t('scriptExecutionPlan.selectScheduleTimeTip')" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.priority')" prop="priority">
							<el-input-number v-model="dataForm.priority" :placeholder="$t('scriptExecutionPlan.inputPriorityTip')" clearable class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptExecutionPlan.selectStatusTip')" clearable class="w100">
								<el-option label="有效" value="0" />
								<el-option label="无效" value="1" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptExecutionPlan.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" :placeholder="$t('scriptExecutionPlan.inputRemarkTip')" :rows="3" />
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

<script lang="ts" setup name="scriptExecutionPlanDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-execution-plan';
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
	planStatus: '0',
	execOrder: 0,
	scheduleTime: '',
	priority: 0,
	status: '0',
	remark: '',
});

const dataRules = ref({
	scriptId: [{ required: true, message: t('scriptExecutionPlan.inputScriptIdTip'), trigger: 'blur' }],
	siteId: [{ required: true, message: t('scriptExecutionPlan.inputSiteIdTip'), trigger: 'blur' }],
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
