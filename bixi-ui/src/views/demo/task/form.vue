<template>
	<el-dialog v-model="visible" :close-on-click-modal="false" :title="form.id ? '编辑任务' : '新增任务'" width="min(560px, 92vw)">
		<el-form ref="dataFormRef" v-loading="loading" :model="form" :rules="dataRules" label-width="90px">
			<el-form-item label="任务标题" prop="title">
				<el-input v-model="form.title" maxlength="120" placeholder="输入任务标题" show-word-limit />
			</el-form-item>
			<el-form-item label="负责人" prop="assignee">
				<el-input v-model="form.assignee" maxlength="64" placeholder="输入负责人" />
			</el-form-item>
			<el-form-item label="优先级" prop="priority">
				<el-segmented
					v-model="form.priority"
					:options="[
						{ label: '低', value: 'LOW' },
						{ label: '中', value: 'MEDIUM' },
						{ label: '高', value: 'HIGH' },
					]"
				/>
			</el-form-item>
			<el-form-item label="任务状态" prop="taskStatus">
				<el-select v-model="form.taskStatus" style="width: 100%">
					<el-option label="待处理" value="TODO" />
					<el-option label="进行中" value="IN_PROGRESS" />
					<el-option label="已完成" value="DONE" />
				</el-select>
			</el-form-item>
			<el-form-item label="截止日期" prop="dueDate">
				<el-date-picker v-model="form.dueDate" placeholder="选择截止日期" style="width: 100%" type="date" value-format="YYYY-MM-DD" />
			</el-form-item>
			<el-form-item label="备注" prop="remark">
				<el-input v-model="form.remark" maxlength="500" :rows="3" show-word-limit type="textarea" />
			</el-form-item>
		</el-form>

		<template #footer>
			<el-button @click="visible = false">取消</el-button>
			<el-button :loading="loading" type="primary" @click="onSubmit">确认</el-button>
		</template>
	</el-dialog>
</template>

<script lang="ts" name="demoTaskDialog" setup>
import { addObj, DemoTaskForm, getObj, putObj } from '/@/api/demo/task';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);

const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

const emptyForm = (): DemoTaskForm => ({
	id: '',
	title: '',
	assignee: '',
	priority: 'MEDIUM',
	taskStatus: 'TODO',
	dueDate: '',
	remark: '',
});

const form = reactive<DemoTaskForm>(emptyForm());

const dataRules = {
	title: [{ required: true, message: '任务标题不能为空', trigger: 'blur' }],
	assignee: [{ required: true, message: '负责人不能为空', trigger: 'blur' }],
	priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
	taskStatus: [{ required: true, message: '请选择任务状态', trigger: 'change' }],
	dueDate: [{ required: true, message: '请选择截止日期', trigger: 'change' }],
};

const openDialog = async (id?: string) => {
	Object.assign(form, emptyForm());
	visible.value = true;
	await nextTick();
	dataFormRef.value?.clearValidate();

	if (!id) return;

	try {
		loading.value = true;
		const response = await getObj(id);
		Object.assign(form, response.data);
	} catch (error: any) {
		useMessage().error(error.msg || '加载任务失败');
	} finally {
		loading.value = false;
	}
};

const onSubmit = async () => {
	const valid = await dataFormRef.value?.validate().catch(() => false);
	if (!valid) return;

	try {
		loading.value = true;
		if (form.id) {
			await putObj({ ...form });
		} else {
			const payload = { ...form };
			delete payload.id;
			await addObj(payload);
		}
		useMessage().success(form.id ? '修改成功' : '新增成功');
		visible.value = false;
		emit('refresh');
	} catch (error: any) {
		useMessage().error(error.msg || '保存失败');
	} finally {
		loading.value = false;
	}
};

defineExpose({ openDialog });
</script>
