<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item label="文档名称" prop="name">
						<el-input v-model="state.queryForm.name" placeholder="请输入文档名称" clearable />
					</el-form-item>
					<el-form-item>
						<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
						<el-button icon="Refresh" @click="resetQuery">重置</el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button icon="upload-filled" type="primary" @click="uploadDialogRef.show()">上传文档</el-button>
					<right-toolbar v-model:showSearch="showSearch" @queryTable="getDataList" class="ml10 mr20" style="float: right" />
				</div>
			</el-row>
			<document-table :data-list="state.dataList" :loading="state.loading" @delete="handleDelete" />
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle" />
		</div>

		<upload-dialog ref="uploadDialogRef" @success="getDataList" />
	</div>
</template>

<script lang="ts" name="aiDocument" setup>
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { deleteDocument, pageList } from '/@/api/ai/document';

const DocumentTable = defineAsyncComponent(() => import('./components/DocumentTable.vue'));
const UploadDialog = defineAsyncComponent(() => import('./components/UploadDialog.vue'));

const queryRef = ref();
const showSearch = ref(true);
const uploadDialogRef = ref();

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		name: '',
	},
	pageList: pageList,
});

const { getDataList, currentChangeHandle, sizeChangeHandle } = useTable(state);

const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
};

const handleDelete = async (id: string) => {
	try {
		await useMessageBox().confirm('确定要删除该文档吗？');
	} catch {
		return;
	}

	try {
		await deleteDocument(id);
		getDataList();
		useMessage().success('删除成功');
	} catch (err: any) {
		useMessage().error(err.msg);
	}
};
</script>
