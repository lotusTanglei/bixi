<template>
	<div class="script-task-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptTask.title')" prop="title">
							<el-input v-model="dataForm.title" :placeholder="$t('scriptTask.inputTitleTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.planId')" prop="planId">
							<el-input v-model="dataForm.planId" :placeholder="$t('scriptTask.inputPlanIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.scriptId')" prop="scriptId">
							<el-input v-model="dataForm.scriptId" :placeholder="$t('scriptTask.inputScriptIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.siteId')" prop="siteId">
							<el-input v-model="dataForm.siteId" :placeholder="$t('scriptTask.inputSiteIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.assignedTo')" prop="assignedTo">
							<el-input v-model="dataForm.assignedTo" :placeholder="$t('scriptTask.inputAssignedToTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.assignerId')" prop="assignerId">
							<el-input v-model="dataForm.assignerId" :placeholder="$t('scriptTask.inputAssignerIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.roleType')" prop="roleType">
							<el-select v-model="dataForm.roleType" :placeholder="$t('scriptTask.selectRoleTypeTip')" clearable class="w100">
								<el-option label="执行者" value="executor" />
								<el-option label="审核者" value="reviewer" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.dueTime')" prop="dueTime">
							<el-date-picker v-model="dataForm.dueTime" type="datetime" :placeholder="$t('scriptTask.selectDueTimeTip')" value-format="YYYY-MM-DD HH:mm:ss" class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.priority')" prop="priority">
							<el-input-number v-model="dataForm.priority" :placeholder="$t('scriptTask.inputPriorityTip')" clearable class="w100" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptTask.selectStatusTip')" clearable class="w100">
								<el-option label="待处理" value="0" />
								<el-option label="进行中" value="1" />
								<el-option label="已完成" value="2" />
								<el-option label="阻塞" value="3" />
								<el-option label="已取消" value="4" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptTask.remindEnable')" prop="remindEnable">
							<el-switch v-model="dataForm.remindEnable" active-value="1" inactive-value="0" />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptTask.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" :placeholder="$t('scriptTask.inputRemarkTip')" :rows="3" />
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

<script lang="ts" setup name="scriptTaskDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-task';
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
	planId: '',
	scriptId: '',
	siteId: '',
	assignedTo: '',
	assignerId: '',
	roleType: '',
	dueTime: '',
	status: '0',
	priority: 0,
	remindEnable: '1',
	remark: '',
});

const dataRules = ref({
	title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
	scriptId: [{ required: true, message: '请输入脚本ID', trigger: 'blur' }],
	siteId: [{ required: true, message: '请输入现场ID', trigger: 'blur' }],
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
