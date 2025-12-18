<template>
	<div class="script-info-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="90px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.name')" prop="name">
							<el-input v-model="dataForm.name" :placeholder="$t('scriptInfo.inputNameTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.code')" prop="code">
							<el-input v-model="dataForm.code" :placeholder="$t('scriptInfo.inputCodeTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.version')" prop="version">
							<el-input v-model="dataForm.version" :placeholder="$t('scriptInfo.inputVersionTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.type')" prop="type">
							<el-select v-model="dataForm.type" :placeholder="$t('scriptInfo.selectTypeTip')" clearable class="w100">
								<el-option label="DDL" value="0" />
								<el-option label="DML" value="1" />
								<el-option label="其他" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.riskLevel')" prop="riskLevel">
							<el-select v-model="dataForm.riskLevel" :placeholder="$t('scriptInfo.selectRiskLevelTip')" clearable class="w100">
								<el-option label="低" value="0" />
								<el-option label="中" value="1" />
								<el-option label="高" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptInfo.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptInfo.selectStatusTip')" clearable class="w100">
								<el-option label="草稿" value="0" />
								<el-option label="已发布" value="1" />
								<el-option label="已废弃" value="2" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptInfo.storagePath')" prop="storagePath">
							<el-input v-model="dataForm.storagePath" :placeholder="$t('scriptInfo.inputStoragePathTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptInfo.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" :placeholder="$t('scriptInfo.inputRemarkTip')" :rows="3" />
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

<script lang="ts" setup name="scriptInfoDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-info';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	name: '',
	code: '',
	version: '',
	type: '0',
	riskLevel: '0',
	storagePath: '',
	status: '0',
	remark: '',
});

const dataRules = ref({
	name: [{ required: true, message: t('scriptInfo.inputNameTip'), trigger: 'blur' }],
	code: [{ required: true, message: t('scriptInfo.inputCodeTip'), trigger: 'blur' }],
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
