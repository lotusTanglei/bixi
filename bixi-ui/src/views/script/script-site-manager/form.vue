<template>
	<div class="script-site-manager-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="dataForm.id ? $t('common.editBtn') : $t('common.addBtn')" draggable v-model="visible">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-row :gutter="20">
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.siteId')" prop="siteId">
							<el-input v-model="dataForm.siteId" :placeholder="$t('scriptSiteManager.inputSiteIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.userId')" prop="userId">
							<el-input v-model="dataForm.userId" :placeholder="$t('scriptSiteManager.inputUserIdTip')" clearable />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.isPrimary')" prop="isPrimary">
							<el-switch v-model="dataForm.isPrimary" active-value="1" inactive-value="0" />
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.roleType')" prop="roleType">
							<el-select v-model="dataForm.roleType" :placeholder="$t('scriptSiteManager.selectRoleTypeTip')" clearable class="w100">
								<el-option label="负责人" value="owner" />
								<el-option label="备份" value="backup" />
								<el-option label="观察者" value="observer" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.status')" prop="status">
							<el-select v-model="dataForm.status" :placeholder="$t('scriptSiteManager.selectStatusTip')" clearable class="w100">
								<el-option label="有效" value="0" />
								<el-option label="无效" value="1" />
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="24" class="mb20">
						<el-form-item :label="$t('scriptSiteManager.remark')" prop="remark">
							<el-input type="textarea" v-model="dataForm.remark" :placeholder="$t('scriptSiteManager.inputRemarkTip')" :rows="3" />
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

<script lang="ts" setup name="scriptSiteManagerDialog">
import { addObj, getObj, putObj } from '/@/api/script/script-site-manager';
import { useMessage } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	siteId: '',
	userId: '',
	isPrimary: '0',
	roleType: 'observer',
	status: '0',
	remark: '',
});

const dataRules = ref({
	siteId: [{ required: true, message: t('scriptSiteManager.inputSiteIdTip'), trigger: 'blur' }],
	userId: [{ required: true, message: t('scriptSiteManager.inputUserIdTip'), trigger: 'blur' }],
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
