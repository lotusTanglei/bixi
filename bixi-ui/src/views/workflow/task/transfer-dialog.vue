<template>
	<div class="system-transfer-dialog-container">
		<el-dialog :close-on-click-modal="false" title="转办" draggable v-model="visible" width="600px">
			<el-form :model="dataForm" :rules="dataRules" label-width="100px" ref="dataFormRef" v-loading="loading">
				<el-form-item label="任务名称">
					<el-input v-model="taskData.taskName" disabled></el-input>
				</el-form-item>
				<el-form-item label="流程名称">
					<el-input v-model="taskData.processName" disabled></el-input>
				</el-form-item>
				<el-form-item label="转办用户" prop="userId">
					<el-select v-model="dataForm.userId" placeholder="请选择转办用户" filterable class="w100">
						<el-option v-for="item in userList" :key="item.id" :label="item.name" :value="item.id" />
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
					<el-button @click="visible = false">取消</el-button>
					<el-button @click="onSubmit" type="primary" :disabled="loading">确定</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowTransferDialog" setup>
import { transfer } from '/@/api/workflow/task';
import { list as userListApi } from '/@/api/admin/user';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);
const taskData = ref<any>({});
const userList = ref<any[]>([]);

const dataForm = reactive({
	taskId: '',
	userId: '',
	comment: '',
});

const dataRules = ref({
	userId: [{ required: true, message: '请选择转办用户', trigger: 'change' }],
	comment: [{ required: true, message: '请输入转办意见', trigger: 'blur' }],
});

const openDialog = async (row: any) => {
	visible.value = true;
	taskData.value = row;
	dataForm.taskId = row.id;
	dataForm.userId = '';
	dataForm.comment = '';

	nextTick(() => {
		dataFormRef.value?.resetFields();
	});

	await getUserList();
};

const getUserList = async () => {
	try {
		const res = await userListApi();
		if (res.code === 0) {
			userList.value = res.data || [];
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取用户列表失败');
	}
};

const onSubmit = async () => {
	const valid = await dataFormRef.value.validate().catch(() => {});
	if (!valid) return false;

	loading.value = true;

	try {
		const res = await transfer({
			taskId: dataForm.taskId,
			userId: dataForm.userId,
			comment: dataForm.comment,
		});

		if (res.code === 0) {
			useMessage().success('转办成功');
			visible.value = false;
			emit('refresh');
		} else {
			useMessage().error(res.msg || '转办失败');
		}
	} catch (err: any) {
		useMessage().error(err.msg || '转办失败');
	} finally {
		loading.value = false;
	}
};

defineExpose({
	openDialog,
});
</script>
