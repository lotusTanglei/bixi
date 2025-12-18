<template>
	<div class="script-site-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.name')" prop="name">
							<el-input v-model="dataForm.name" :placeholder="$t('scriptSite.inputNameTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.code')" prop="code">
							<el-input v-model="dataForm.code" :placeholder="$t('scriptSite.inputCodeTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.env')" prop="env">
							<el-select v-model="dataForm.env" :placeholder="$t('scriptSite.selectEnvTip')" clearable class="w100">
								<el-option label="Prod" value="prod" />
								<el-option label="Test" value="test" />
								<el-option label="Dev" value="dev" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.region')" prop="region">
							<el-input v-model="dataForm.region" :placeholder="$t('scriptSite.inputRegionTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.ownerId')" prop="ownerId">
							<el-input v-model="dataForm.ownerId" :placeholder="$t('scriptSite.inputOwnerIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSite.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptSite.selectStatusTip')" clearable class="w100">
								<el-option label="正常" value="0" />
								<el-option label="停用" value="1" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptSite.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" :placeholder="$t('scriptSite.inputRemarkTip')" :rows="3" />
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

<script lang="ts" setup name="scriptSiteDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-site';
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
	env: 'dev',
	region: '',
	ownerId: '',
	status: '0',
	remark: '',
});

const dataRules = ref({
	name: [{ required: true, message: t('scriptSite.inputNameTip'), trigger: 'blur' }],
	code: [{ required: true, message: t('scriptSite.inputCodeTip'), trigger: 'blur' }],
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
