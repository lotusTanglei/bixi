<template>
	<el-table
		v-loading="loading"
		:data="dataList"
		border
		:cell-style="tableStyle.cellStyle"
		:header-cell-style="tableStyle.headerCellStyle"
	>
		<el-table-column label="序号" type="index" width="60" />
		<el-table-column label="文档名称" prop="title" show-overflow-tooltip />
		<el-table-column label="类型" prop="docType" width="120">
			<template #default="scope">
				<el-tag>{{ scope.row.docType }}</el-tag>
			</template>
		</el-table-column>
		<el-table-column label="来源" prop="source" width="120" show-overflow-tooltip />
		<el-table-column label="向量化状态" prop="vectorStatus" width="120">
			<template #default="scope">
				<el-tag :type="getVectorStatusType(scope.row.vectorStatus)">
					{{ getVectorStatusText(scope.row.vectorStatus) }}
				</el-tag>
			</template>
		</el-table-column>
		<el-table-column label="上传时间" prop="createTime" width="180" show-overflow-tooltip />
		<el-table-column label="操作" width="100" fixed="right">
			<template #default="scope">
				<el-button icon="delete" text type="primary" @click="handleDelete(scope.row.id)">
					删除
				</el-button>
			</template>
		</el-table-column>
	</el-table>
</template>

<script lang="ts" name="DocumentTable" setup>
import { useTable } from '/@/hooks/table';

defineProps<{
	dataList: any[];
	loading: boolean;
}>();

const emit = defineEmits<{
	(e: 'delete', id: string): void;
}>();

const { tableStyle } = useTable({} as any);

const getVectorStatusType = (status: string) => {
	const statusMap: Record<string, string> = {
		'0': 'warning',
		'1': 'success',
		'2': 'danger',
	};
	return statusMap[status] || 'info';
};

const getVectorStatusText = (status: string) => {
	const statusMap: Record<string, string> = {
		'0': '待处理',
		'1': '已完成',
		'2': '失败',
	};
	return statusMap[status] || '未知';
};

const handleDelete = (id: string) => {
	emit('delete', id);
};
</script>
