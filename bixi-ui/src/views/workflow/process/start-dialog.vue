<template>
	<div class="system-start-dialog-container">
		<el-dialog :close-on-click-modal="false" :close-on-press-escape="!loading" :show-close="!loading" :before-close="closeDialog" title="发起流程" draggable v-model="visible" width="min(680px, 94vw)">
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-alert v-if="pendingRequest" title="本次发起结果待确认，可查询结果或使用原内容重试。" type="warning" :closable="false" class="mb16" />
			<el-form :model="dataForm" :rules="dataRules" :disabled="loading || !!pendingRequest || !storageReady" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="流程名称">
					<el-input v-model="definitionData.name" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程标识">
					<el-input v-model="definitionData.key" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程标题" prop="title">
					<el-input v-model="dataForm.title" placeholder="请输入流程标题" clearable></el-input>
				</el-form-item>
				<el-form-item label="备注" prop="remark">
					<el-input
						v-model="dataForm.remark"
						type="textarea"
						:rows="4"
						placeholder="请输入备注"
						maxlength="500"
						show-word-limit
					></el-input>
				</el-form-item>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="closeDialog()" :disabled="loading">关闭</el-button>
					<el-button v-if="pendingRequest" v-auth="'workflow_process_view'" @click="queryResult" :disabled="loading">查询本次结果</el-button>
					<el-button v-if="pendingRequest" @click="newIntent" :disabled="loading">修改并发起新流程</el-button>
					<el-button v-auth="'workflow_process_add'" @click="onSubmit" type="primary" :loading="loading" :disabled="loading || !storageReady">{{ pendingRequest ? '重试本次操作' : '提交' }}</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowStartDialog" setup>
import { start, getCommand, type ProcessStartRequest } from '/@/api/workflow/process';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useUserInfo } from '/@/stores/userInfo';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);
const error = ref('');
const storageReady = ref(false);
const pendingRequest = ref<ProcessStartRequest>();
const definitionData = ref<any>({});
let storageKey = '';
let actorId = '';
let requestVersion = 0;
const currentActor = () => String(useUserInfo().userInfos.user?.id ?? '');
onBeforeUnmount(() => { requestVersion++; });

const dataForm = reactive({
	processKey: '',
	title: '',
	remark: '',
	variables: {} as any,
});

const dataRules = ref({
	title: [{ required: true, message: '请输入流程标题', trigger: 'blur' }],
});

const openDialog = (row: any) => {
	if (loading.value) return;
	requestVersion++;
	visible.value = true;
	error.value = '';
	storageReady.value = false;
	pendingRequest.value = undefined;
	definitionData.value = row;
	dataForm.processKey = row.key;
	dataForm.title = '';
	dataForm.remark = '';
	dataForm.variables = {};
	try {
		actorId = currentActor();
		if (!actorId) throw new Error('用户信息尚未就绪，请重新打开窗口');
		if (typeof row.key !== 'string' || !row.key.trim()) throw new Error('流程标识缺失，请重新加载流程列表');
		storageKey = `workflow:START:${encodeURIComponent(actorId)}:${encodeURIComponent(row.key)}`;
		const saved = sessionStorage.getItem(storageKey);
		if (saved) {
			const payload = JSON.parse(saved) as ProcessStartRequest;
			if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(payload.requestId) || payload.processKey !== row.key) {
				throw new Error('待确认申请记录无法读取，请联系管理员核查');
			}
			pendingRequest.value = payload;
			Object.assign(dataForm, { processKey: payload.processKey, title: payload.title, remark: payload.remark, variables: JSON.parse(JSON.stringify(payload.variables)) });
		}
		storageReady.value = true;
	} catch (err: any) {
		error.value = err?.message || '无法读取待确认申请，请检查浏览器会话存储';
	}
	nextTick(() => {
		dataFormRef.value?.clearValidate();
	});
};

const closeDialog = (done?: () => void) => {
	if (loading.value) return;
	if (done) done();
	else visible.value = false;
};

const assertActor = () => {
	if (currentActor() !== actorId) throw new Error('当前用户已变更，请关闭并重新打开窗口');
};

const newRequestId = () => {
	if (crypto.randomUUID) return crypto.randomUUID();
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	bytes[6] = (bytes[6] & 15) | 64;
	bytes[8] = (bytes[8] & 63) | 128;
	const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

const finishSuccess = () => {
	sessionStorage.removeItem(storageKey);
	pendingRequest.value = undefined;
	useMessage().success('发起成功');
	visible.value = false;
	emit('refresh');
};

const onSubmit = async () => {
	if (loading.value || !visible.value || !storageReady.value) return;
	loading.value = true;
	error.value = '';
	const version = requestVersion;
	try {
		assertActor();
		if (!pendingRequest.value) {
			const valid = await dataFormRef.value?.validate().catch(() => false);
			if (!valid || version !== requestVersion) return;
			assertActor();
			const payload = JSON.parse(JSON.stringify({ ...dataForm, requestId: newRequestId() })) as ProcessStartRequest;
			// Persist before sending so a refresh cannot discard an ambiguous request.
			sessionStorage.setItem(storageKey, JSON.stringify(payload));
			pendingRequest.value = payload;
		}
		const res = await start(JSON.parse(JSON.stringify(pendingRequest.value)));
		if (version !== requestVersion) return;
		assertActor();
		if (res.code === 0) finishSuccess();
		else error.value = res.msg || '发起失败，可查询本次结果或重试';
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.msg || err?.message || '发起结果尚未确认，请查询本次结果或重试';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};

const queryResult = async () => {
	if (loading.value || !visible.value || !pendingRequest.value) return;
	loading.value = true;
	error.value = '';
	const version = requestVersion;
	try {
		assertActor();
		const requestId = pendingRequest.value.requestId;
		const res = await getCommand(requestId);
		if (version !== requestVersion) return;
		assertActor();
		if (res.code === 0 && res.data?.requestId === requestId && res.data.operation === 'START'
			&& res.data.resultCode === 'SUCCESS' && res.data.completedAt && res.data.response?.processInstanceId) finishSuccess();
		else error.value = res.msg || '尚未确认本次发起成功，可继续查询或重试本次操作';
	} catch (err: any) {
		if (version === requestVersion) error.value = err?.data?.errorCode === 'WORKFLOW_COMMAND_NOT_FOUND' || err?.response?.status === 404
			? '尚未查询到本次结果，可重试本次操作'
			: err?.msg || err?.message || '查询失败，请稍后重试查询';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};

const newIntent = async () => {
	if (loading.value || !pendingRequest.value) return;
	loading.value = true;
	const version = requestVersion;
	try {
		await useMessageBox().confirm('上一次发起可能已成功。确认创建另一条流程，并使用新的申请内容？');
		if (version !== requestVersion) return;
		assertActor();
		sessionStorage.removeItem(storageKey);
		pendingRequest.value = undefined;
		error.value = '';
	} catch (err: any) {
		if (version === requestVersion && err !== 'cancel' && err !== 'close') error.value = err?.msg || err?.message || '无法创建新申请';
	} finally {
		if (version === requestVersion) loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>

<style scoped>
.dialog-footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.dialog-footer :deep(.el-button + .el-button) { margin-left: 0; }
</style>
