<template>
	<el-dialog v-model="visible" :title="id ? '编辑请假草稿' : '新建请假申请'" width="min(560px, 94vw)" :close-on-click-modal="false"
		:before-close="closeDialog" :close-on-press-escape="!saving" :show-close="!saving">
		<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
		<el-form ref="formRef" v-loading="loading || saving" :disabled="loading || saving" :model="form" :rules="rules" label-width="85px">
			<el-form-item label="审批人" prop="approverId">
				<el-select v-model="form.approverId" filterable remote :remote-method="searchUsers" placeholder="搜索审批人" class="w100">
					<el-option v-for="user in users" :key="user.id" :value="user.id" :label="user.name || user.username" />
				</el-select>
			</el-form-item>
			<el-form-item label="开始日期" prop="startDate">
				<el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" class="w100" />
			</el-form-item>
			<el-form-item label="结束日期" prop="endDate">
				<el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" class="w100" />
			</el-form-item>
			<el-form-item label="请假原因" prop="reason">
				<el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
			</el-form-item>
		</el-form>
		<template #footer>
			<el-button :disabled="saving" @click="closeDialog()">取消</el-button>
			<el-button type="primary" :disabled="loading" :loading="saving" @click="saveDraft">保存草稿</el-button>
		</template>
	</el-dialog>
</template>

<script setup lang="ts">
import { getObj, save, approvers, type LeaveForm } from '/@/api/demo/leave';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const id = ref<string>();
const users = ref<any[]>([]);
const formRef = ref();
const blank = (): LeaveForm => ({ approverId: '', startDate: '', endDate: '', reason: '' });
const form = reactive<LeaveForm>(blank());
let requestVersion = 0;
let userSearchVersion = 0;
watch(visible, isOpen => { if (!isOpen) requestVersion++; }, { flush: 'sync' });
onBeforeUnmount(() => { requestVersion++; });
const searchUsers = async (name = '') => {
	const version = requestVersion;
	const search = ++userSearchVersion;
	try {
		const { data } = await approvers(name);
		if (version === requestVersion && search === userSearchVersion) users.value = data?.records || [];
	} catch (err: any) {
		if (version === requestVersion && search === userSearchVersion) error.value = err?.msg || '获取审批人失败';
	}
};
const closeDialog = (done?: () => void) => {
	if (saving.value) return;
	if (done) done();
	else visible.value = false;
};
const rules = {
	approverId: [{ required: true, message: '请选择审批人', trigger: 'change' }],
	startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
	endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
	reason: [{ required: true, whitespace: true, message: '请填写请假原因', trigger: 'blur' }],
};
const openDialog = async (requestId?: string) => {
	if (saving.value) return;
	const version = ++requestVersion;
	id.value = requestId;
	Object.assign(form, blank());
	users.value = [];
	error.value = '';
	visible.value = true;
	loading.value = true;
	await nextTick();
	if (version !== requestVersion) return;
	formRef.value?.clearValidate();
	try {
		await searchUsers();
		if (version !== requestVersion) return;
		if (requestId) {
			const { data } = await getObj(requestId);
			if (version !== requestVersion) return;
			Object.assign(form, { approverId: data.approverId, startDate: data.startDate, endDate: data.endDate, reason: data.reason });
		}
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || '加载申请失败，请重新打开';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};
const saveDraft = async () => {
	if (loading.value || saving.value || !visible.value) return;
	saving.value = true;
	const version = requestVersion;
	error.value = '';
	try {
		if (!(await formRef.value?.validate().catch(() => false)) || version !== requestVersion) return;
		if (form.endDate < form.startDate) { error.value = '结束日期不能早于开始日期'; return; }
		await save({ ...form }, id.value);
		if (version !== requestVersion) return;
		useMessage().success('草稿已保存');
		visible.value = false;
		emit('refresh');
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || '保存失败，请重试';
	} finally { saving.value = false; }
};
defineExpose({ openDialog });
</script>
