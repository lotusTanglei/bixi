<template>
	<div class="system-transfer-dialog-container">
		<el-dialog :close-on-click-modal="false" :title="delegating ? '委派' : '转办'" draggable v-model="visible" width="min(600px, 94vw)"
			:before-close="closeDialog" :close-on-press-escape="!loading" :show-close="!loading">
			<el-alert v-if="pendingIntent" title="已恢复上次提交的原内容。可查询结果或按原内容重试；修改内容前请确认开始新操作。" type="warning" :closable="false" class="mb16" />
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-form :model="dataForm" :rules="dataRules" :disabled="loading || !!pendingIntent || !storageReady" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="任务名称">
					<el-input v-model="taskData.taskName" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程名称">
					<el-input v-model="taskData.processName" disabled></el-input>
				</el-form-item>
				<el-form-item label="转办用户" prop="userId">
					<el-select v-model="dataForm.userId" placeholder="搜索办理人" filterable remote :remote-method="getUserList" class="w100">
						<el-option v-for="item in userList" :key="item.id" :label="item.name || item.username" :value="item.id" />
					</el-select>
				</el-form-item>
				<el-form-item label="转办意见" prop="comment">
					<el-input
						v-model="dataForm.comment"
						type="textarea"
						:rows="4"
						placeholder="请输入转办意见"
						maxlength="500"
						show-word-limit
					></el-input>
				</el-form-item>
			</el-form>
			<template #footer>
				<span class="dialog-footer">
					<el-button :disabled="loading" @click="closeDialog()">取消</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_view'" :disabled="loading" @click="queryResult">查询结果</el-button>
					<el-button v-if="pendingIntent" v-auth="'workflow_task_edit'" :disabled="loading" @click="newIntent">修改为新操作</el-button>
					<el-button v-auth="'workflow_task_edit'" @click="onSubmit" type="primary" :disabled="loading || !storageReady">{{ pendingIntent ? '重试本次操作' : '确定' }}</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowTransferDialog" setup>
import { approvers as userListApi } from '/@/api/demo/leave';
import { useMessage } from '/@/hooks/message';
import { currentWorkflowActor } from '/@/utils/workflow-intent';
import { useWorkflowIntentDialog } from '/@/utils/workflow-intent-dialog';
const emit = defineEmits(['refresh']);
const dataFormRef = ref();
const taskData = ref<any>({});
const delegating = ref(false);
const userList = ref<any[]>([]);
let userSearchVersion = 0;
const dataForm = reactive({ taskId: '', userId: '', comment: '' });
const dataRules = ref({
	userId: [{ required: true, message: '请选择办理人', trigger: 'change' }],
	comment: [{ required: true, message: '请输入办理意见', trigger: 'blur' }],
});
const { visible, loading, error, storageReady, pendingIntent, closeDialog, openIntent, submitIntent, queryResult, newIntent } = useWorkflowIntentDialog({
	operations: ['TRANSFER', 'DELEGATE'],
	restore: intent => {
		dataForm.taskId = intent.resourceId; dataForm.userId = intent.request.transferUserId; dataForm.comment = intent.request.transferReason;
		delegating.value = intent.operation === 'DELEGATE';
		userList.value = [{ id: intent.request.transferUserId, name: intent.display.userName || intent.request.transferUserName || `办理人 ${intent.request.transferUserId}` }];
	},
	reset: () => { taskData.value = {}; userList.value = []; Object.assign(dataForm, { taskId: '', userId: '', comment: '' }); },
	refresh: () => emit('refresh'),
});
watch(visible, open => { if (!open) userSearchVersion++; }, { flush: 'sync' });
onBeforeUnmount(() => { userSearchVersion++; });
const openDialog = async (row: any, isDelegate = false) => {
	if (loading.value) return;
	userSearchVersion++; userList.value = [];
	delegating.value = isDelegate; taskData.value = { ...row };
	Object.assign(dataForm, { taskId: row.taskId, userId: '', comment: '' });
	openIntent(row);
	nextTick(() => dataFormRef.value?.clearValidate());
	if (!pendingIntent.value && storageReady.value) await getUserList();
};
const getUserList = async (name = '') => {
	if (pendingIntent.value) return;
	const search = ++userSearchVersion, actorId = currentWorkflowActor();
	try {
		const res = await userListApi(name);
		if (search === userSearchVersion && actorId === currentWorkflowActor() && !pendingIntent.value && res.code === 0) userList.value = res.data?.records || [];
	} catch (err: any) {
		if (search === userSearchVersion && actorId === currentWorkflowActor()) useMessage().error(err?.msg || '获取用户列表失败');
	}
};
const onSubmit = () => submitIntent({
	operation: delegating.value ? 'DELEGATE' : 'TRANSFER',
	payload: { transferUserId: dataForm.userId, transferReason: dataForm.comment },
	display: { userName: userList.value.find(user => user.id === dataForm.userId)?.name || userList.value.find(user => user.id === dataForm.userId)?.username },
}, () => dataFormRef.value.validate());
defineExpose({ openDialog });
</script>
