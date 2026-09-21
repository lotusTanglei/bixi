<template><process-detail ref="detailRef" /></template>
<script lang="ts" name="workflowTaskDetailDialog" setup>
import ProcessDetail from '../process/detail-dialog.vue';
import { getObj } from '/@/api/workflow/task';
import { useMessage } from '/@/hooks/message';
const detailRef = ref();
let requestVersion = 0;
onBeforeUnmount(() => { requestVersion++; });
const openDialog = async (row: { taskId: string }) => {
	const version = ++requestVersion;
	try {
		const { data } = await getObj(row.taskId);
		if (version === requestVersion) await detailRef.value.openDialog(data);
	} catch (err: any) {
		if (version === requestVersion) useMessage().error(err?.msg || '加载任务详情失败');
	}
};
defineExpose({ openDialog });
</script>
