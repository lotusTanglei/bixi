<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-form v-show="showSearch" ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
				<el-form-item label="字典类型" prop="type">
					<el-input v-model="state.queryForm.type" clearable placeholder="输入字典类型" />
				</el-form-item>
				<el-form-item label="字典名称" prop="name">
					<el-input v-model="state.queryForm.name" clearable placeholder="输入字典名称" />
				</el-form-item>
				<el-form-item label="系统标志" prop="systemFlag">
					<el-input v-model="state.queryForm.systemFlag" clearable placeholder="输入系统标志" />
				</el-form-item>
				<el-form-item>
					<el-button icon="Search" type="primary" @click="getDataList">查询</el-button>
					<el-button icon="Refresh" @click="resetQuery">重置</el-button>
				</el-form-item>
			</el-form>

				<el-row class="mb8">
					<el-button v-auth-all="['acceptance_dict_aggregate_add', 'acceptance_sys_dict_item_add']" icon="FolderAdd" type="primary" @click="formDialogRef.openDialog()">新增</el-button>
					<el-button v-auth-all="['acceptance_dict_aggregate_del', 'acceptance_sys_dict_item_del']" class="ml10" :disabled="selectedIds.length === 0" icon="Delete" plain type="danger" @click="handleDelete(selectedIds)">删除</el-button>
					<el-button v-auth="'acceptance_dict_aggregate_import'" class="ml10" icon="Upload" plain type="primary" @click="fileInputRef?.click()">导入</el-button>
					<el-button v-auth="'acceptance_dict_aggregate_export'" class="ml10" icon="Download" plain type="success" @click="handleExport">导出</el-button>
					<input ref="fileInputRef" accept=".xlsx,.xls" class="import-file-input" type="file" @change="handleImportFile" />
					<right-toolbar v-model:showSearch="showSearch" class="ml10" style="margin-left: auto" @queryTable="getDataList" />
			</el-row>

			<el-alert v-if="loadError" :closable="false" show-icon :title="loadError" type="error" />
			<el-table v-loading="state.loading" :data="state.dataList" border empty-text="暂无数据" @selection-change="handleSelectionChange">
				<el-table-column align="center" type="selection" width="44" />
				<el-table-column label="字典类型" min-width="120" prop="type" show-overflow-tooltip />
				<el-table-column label="字典名称" min-width="120" prop="name" show-overflow-tooltip />
				<el-table-column label="字典描述" min-width="120" prop="description" show-overflow-tooltip />
				<el-table-column label="排序号" min-width="120" prop="sn" show-overflow-tooltip />
				<el-table-column label="系统标志" min-width="120" prop="systemFlag" show-overflow-tooltip />
				<el-table-column label="状态（0正常 1停用）" min-width="120" prop="status" show-overflow-tooltip />
				<el-table-column label="数据状态（用来标识数据状态，可用于割接，特殊数据处理）" min-width="120" prop="dataStatus" show-overflow-tooltip />
				<el-table-column label="备注" min-width="120" prop="remark" show-overflow-tooltip />
					<el-table-column align="center" fixed="right" label="操作" width="170">
						<template #default="scope">
							<el-button v-auth-all="['acceptance_dict_aggregate_edit', 'acceptance_sys_dict_item_edit']" icon="EditPen" text type="primary" @click="formDialogRef.openDialog(scope.row.id)">编辑</el-button>
							<el-button v-auth-all="['acceptance_dict_aggregate_del', 'acceptance_sys_dict_item_del']" icon="Delete" text type="danger" @click="handleDelete([scope.row.id])">删除</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle" />
		</div>
			<form-dialog ref="formDialogRef" @refresh="loadPage" />
			<el-dialog v-model="importResultVisible" title="导入结果" width="min(720px, 92vw)">
				<el-alert v-if="importResult?.success" :closable="false" show-icon :title="'成功导入 ' + importResult.importedRows + ' 行'" type="success" />
				<template v-else>
					<el-alert :closable="false" show-icon title="导入失败，未写入任何数据" type="error" />
					<el-table :data="importResult ? importResult.errors.slice(0, 100) : []" class="mt10" empty-text="没有错误明细" max-height="360">
						<el-table-column label="错误行" prop="rowNumber" width="100" />
						<el-table-column label="错误原因" min-width="360">
							<template #default="scope">{{ scope.row.errors.join('；') }}</template>
						</el-table-column>
					</el-table>
				</template>
			</el-dialog>
		</div>
</template>

<script lang="ts" name="acceptanceSysDict" setup>
import { delObj, exportRows, fetchList, importRows, SysDictImportResult } from '/@/api/acceptance/dictAggregate';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { handleBlobFile } from '/@/utils/other';

const FormDialog = defineAsyncComponent(() => import('./form.vue'));
const formDialogRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectedIds = ref<string[]>([]);
const loadError = ref('');
const fileInputRef = ref<HTMLInputElement>();
const importResult = ref<SysDictImportResult>();
const importResultVisible = ref(false);
const state = reactive<BasicTableProps>({ queryForm: { type: '', name: '', systemFlag: '' }, pageList: fetchList });
const { getDataList, currentChangeHandle, sizeChangeHandle } = useTable(state);

const loadPage = async () => {
	loadError.value = '';
	try { await getDataList(); } catch (error: any) { loadError.value = error?.msg || '加载失败'; }
};
const resetQuery = () => { queryRef.value?.resetFields(); loadPage(); };
const handleSelectionChange = (rows: Array<{ id: string }>) => { selectedIds.value = rows.map((row) => row.id); };
const handleDelete = async (ids: string[]) => {
	try {
		await useMessageBox().confirm('确认删除选中的字典表吗？');
		await delObj(ids);
		useMessage().success('删除成功');
		selectedIds.value = [];
		await loadPage();
	} catch (error: any) { if (error?.msg) useMessage().error(error.msg); }
};
const handleImportFile = async (event: Event) => {
	const input = event.target as HTMLInputElement;
	const file = input.files?.[0];
	if (!file) return;
	try {
		const response = await importRows(file);
		importResult.value = response.data;
		importResultVisible.value = true;
		if (response.data.success) await loadPage();
	} catch (error: any) { useMessage().error(error?.msg || '导入失败'); }
	finally { input.value = ''; }
};
const handleExport = async () => {
	try { handleBlobFile(await exportRows(state.queryForm), 'dictAggregate.xlsx'); }
	catch (error: any) { useMessage().error(error?.msg || '导出失败'); }
};
</script>

<style scoped>
.import-file-input { display: none; }
</style>
