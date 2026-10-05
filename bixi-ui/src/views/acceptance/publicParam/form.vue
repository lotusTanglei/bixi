<template>
	<el-dialog v-model="visible" :close-on-click-modal="false" :title="form.id ? '编辑公共参数配置表' : '新增公共参数配置表'" width="min(600px, 92vw)">
		<el-form ref="dataFormRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
			<el-form-item label="名称" prop="name">
				<el-input v-model="form.name" clearable placeholder="输入名称" />
			</el-form-item>
			<el-form-item label="键" prop="key">
				<el-input v-model="form.key" clearable placeholder="输入键" />
			</el-form-item>
			<el-form-item label="值" prop="value">
				<el-input v-model="form.value" clearable placeholder="输入值" />
			</el-form-item>
			<el-form-item label="校验码" prop="validateCode">
				<el-input v-model="form.validateCode" clearable placeholder="输入校验码" />
			</el-form-item>
			<el-form-item label="类型，0未知，1系统，2业务" prop="type">
				<el-input v-model="form.type" clearable placeholder="输入类型，0未知，1系统，2业务" />
			</el-form-item>
			<el-form-item label="系统标识，0非系统，1系统" prop="systemFlag">
				<el-input v-model="form.systemFlag" clearable placeholder="输入系统标识，0非系统，1系统" />
			</el-form-item>
			<el-form-item label="排序" prop="sn">
				<el-input v-model="form.sn" clearable placeholder="输入排序" />
			</el-form-item>
			<el-form-item label="状态（0正常 1停用）" prop="status">
				<el-input v-model="form.status" clearable placeholder="输入状态（0正常 1停用）" />
			</el-form-item>
			<el-form-item label="数据状态（用来标识数据状态，可用于割接，特殊数据处理）" prop="dataStatus">
				<el-input v-model="form.dataStatus" clearable placeholder="输入数据状态（用来标识数据状态，可用于割接，特殊数据处理）" />
			</el-form-item>
			<el-form-item label="备注" prop="remark">
				<el-input v-model="form.remark" clearable placeholder="输入备注" />
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button :loading="loading" type="primary" @click="submit">确认</el-button>
		</template>
	</el-dialog>
</template>

<script lang="ts" name="acceptanceSysPublicParamDialog" setup>
import { addObj, getObj, putObj, SysPublicParamForm } from '/@/api/acceptance/publicParam';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);
const visible = ref(false);
const loading = ref(false);
const dataFormRef = ref();
const emptyForm = (): SysPublicParamForm => ({
	name: '',
	key: '',
	value: '',
	validateCode: '',
	type: '',
	systemFlag: '',
	sn:  undefined,
	status: '',
	dataStatus: '',
	remark: '',
});
const form = reactive<SysPublicParamForm>(emptyForm());
const rules = {
	name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
	key: [{ required: true, message: '请输入键', trigger: 'blur' }],
	value: [{ required: true, message: '请输入值', trigger: 'blur' }],
};
const openDialog = async (id?: string) => {
	Object.assign(form, emptyForm());
	visible.value = true;
	await nextTick();
	dataFormRef.value?.clearValidate();
	if (!id) return;
	try { loading.value = true; Object.assign(form, (await getObj(id)).data); }
	catch (error: any) { useMessage().error(error?.msg || '加载失败'); }
	finally { loading.value = false; }
};
const submit = async () => {
	if (!(await dataFormRef.value?.validate().catch(() => false))) return;
	try {
		loading.value = true;
		await (form.id ? putObj({ ...form }) : addObj({ ...form }));
		useMessage().success(form.id ? '修改成功' : '新增成功');
		visible.value = false;
		emit('refresh');
	} catch (error: any) { useMessage().error(error?.msg || '保存失败'); }
	finally { loading.value = false; }
};
defineExpose({ openDialog });
</script>
