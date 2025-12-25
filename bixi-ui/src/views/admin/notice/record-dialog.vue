<template>
	<el-dialog :title="t('notice.record')" v-model="visible" width="70%" :close-on-click-modal="false" destroy-on-close append-to-body>
		<div>
			<el-row v-show="showSearch">
					<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
						<el-form-item label="接收人" prop="recipientName">
							<el-input v-model="state.queryForm.recipientName" placeholder="请输入接收人" clearable />
						</el-form-item>
						<el-form-item label="状态" prop="isRead">
							<el-select v-model="state.queryForm.isRead" placeholder="请选择状态" clearable>
								<el-option label="未读" value="0" />
								<el-option label="已读" value="1" />
							</el-select>
						</el-form-item>
						<el-form-item>
							<el-button icon="Search" type="primary" @click="getDataList">{{ t('common.queryBtn') }}</el-button>
							<el-button icon="Refresh" @click="resetQuery">{{ t('common.resetBtn') }}</el-button>
						</el-form-item>
					</el-form>
				</el-row>
				<el-table
					v-loading="state.loading"
					:data="state.dataList"
					border
					:cell-style="tableStyle.cellStyle"
					:header-cell-style="tableStyle.headerCellStyle"
				>
					<el-table-column label="序号" type="index" width="60" />
					<el-table-column label="接收人" prop="recipientName" show-overflow-tooltip />
					<el-table-column label="状态" prop="isRead" show-overflow-tooltip width="100">
						<template #default="scope">
							<el-tag v-if="scope.row.isRead === '0'" type="danger">未读</el-tag>
							<el-tag v-else type="success">已读</el-tag>
						</template>
					</el-table-column>
					<el-table-column label="阅读时间" prop="readTime" show-overflow-tooltip width="180" />
					<el-table-column label="发送时间" prop="createTime" show-overflow-tooltip width="180" />
				</el-table>
				<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>
	</el-dialog>
</template>

<script lang="ts" setup name="noticeRecordDialog">
import { recordPageList } from '/@/api/admin/user-notice';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const visible = ref(false);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		noticeId: '',
		recipientName: '',
		isRead: '',
	},
	pageList: recordPageList,
	createdIsNeed: false,
});

const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

const showSearch = ref(true);

const openDialog = (id: string) => {
	visible.value = true;
	state.queryForm.noticeId = id;
	getDataList();
};

const resetQuery = () => {
	state.queryForm.recipientName = '';
	state.queryForm.isRead = '';
	getDataList();
};

defineExpose({
	openDialog,
});
</script>
