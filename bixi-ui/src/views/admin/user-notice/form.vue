<template>
	<div class="sys-user-notice-dialog-container">
		<el-dialog :close-on-click-modal="false" title="查看通知" draggable v-model="visible" width="60%">
			<el-descriptions :column="1" border v-loading="loading">
				<el-descriptions-item label="标题">{{ dataForm.title }}</el-descriptions-item>
				<el-descriptions-item label="类型">
					<el-tag v-if="dataForm.type === '0'">通知</el-tag>
					<el-tag v-else-if="dataForm.type === '1'" type="warning">公告</el-tag>
					<el-tag v-else type="info">私信</el-tag>
				</el-descriptions-item>
				<el-descriptions-item label="优先级">
					<el-tag v-if="dataForm.priority === '0'" type="info">普通</el-tag>
					<el-tag v-else-if="dataForm.priority === '1'" type="warning">重要</el-tag>
					<el-tag v-else type="danger">紧急</el-tag>
				</el-descriptions-item>
				<el-descriptions-item label="发送人">{{ dataForm.senderName }}</el-descriptions-item>
				<el-descriptions-item label="发送时间">{{ dataForm.createTime }}</el-descriptions-item>
				<el-descriptions-item label="内容">
					<div v-html="DOMPurify.sanitize(dataForm.content || '')" style="min-height: 100px;"></div>
				</el-descriptions-item>
			</el-descriptions>
			<template #footer>
				<span class="dialog-footer">
					<el-button @click="visible = false">关闭</el-button>
				</span>
			</template>
		</el-dialog>
	</div>
</template>

<script lang="ts" setup name="sysUserNoticeDialog">
import { getObj } from '/@/api/admin/user-notice';
import { useMessage } from '/@/hooks/message';
import DOMPurify from 'dompurify';

const visible = ref(false);
const loading = ref(false);

const dataForm = reactive({
	id: '',
	title: '',
	content: '',
	type: '',
	priority: '',
	senderName: '',
	createTime: '',
});

const openDialog = async (id: string) => {
	visible.value = true;
	dataForm.id = '';
    // Reset data
    dataForm.title = '';
    dataForm.content = '';
    dataForm.type = '';
    dataForm.priority = '';
    dataForm.senderName = '';
    dataForm.createTime = '';

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

defineExpose({
	openDialog,
});
</script>
