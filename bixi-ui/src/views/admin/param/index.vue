<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row class="ml10" v-show="showSearch">
				<el-form :inline="true" :model="state.queryForm" ref="queryRef">
					<el-form-item :label="$t('param.name')" prop="name">
						<el-input :placeholder="$t('param.inputPublicParamNameTip')" style="max-width: 180px" v-model="state.queryForm.name" />
					</el-form-item>
					<el-form-item :label="$t('param.key')" prop="key">
						<el-input :placeholder="$t('param.inputPublicParamKeyTip')" style="max-width: 180px" v-model="state.queryForm.key" />
					</el-form-item>
					<el-form-item :label="t('param.systemFlag')" class="ml2" prop="systemFlag">
						<el-select :placeholder="t('param.inputSystemFlagTip')" v-model="state.queryForm.systemFlag">
							<el-option :key="index" :label="item.label" :value="item.value" v-for="(item, index) in dict_type"></el-option>
						</el-select>
					</el-form-item>
					<el-form-item>
						<el-button @click="getDataList" formDialogRef icon="search" type="primary">
							{{ $t('common.queryBtn') }}
						</el-button>
						<el-button @click="resetQuery" formDialogRef icon="Refresh">{{ $t('common.resetBtn') }} </el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button v-auth="'sys_syspublicparam_add'" @click="formDialogRef.openDialog()" class="ml10" icon="folder-add" type="primary">
						{{ $t('common.addBtn') }}
					</el-button>

					<el-button plain v-auth="'sys_syspublicparam_del'" @click="handleRefreshCache()" class="ml10" icon="refresh-left" type="primary">
						{{ $t('common.refreshCacheBtn') }}
					</el-button>

					<el-button
						plain
						v-auth="'sys_syspublicparam_del'"
						:disabled="multiple"
						@click="handleDelete(selectObjs)"
						class="ml10"
						icon="Delete"
						type="primary"
					>
						{{ $t('common.delBtn') }}
					</el-button>

					<right-toolbar
						:export="'sys_syspublicparam_del'"
						@exportExcel="exportExcel"
						@queryTable="getDataList"
						class="ml10"
						style="float: right; margin-right: 20px"
						v-model:showSearch="showSearch"
					></right-toolbar>
				</div>
			</el-row>
			<el-table
				:data="state.dataList"
				@selection-change="handleSelectionChange"
				style="width: 100%"
				v-loading="state.loading"
				border
				:cell-style="tableStyle.cellStyle"
				:header-cell-style="tableStyle.headerCellStyle"
			>
				<el-table-column :selectable="handleSelectable" align="center" type="selection" width="40" />
				<el-table-column :label="t('param.index')" type="index" width="60" />
				<el-table-column :label="t('param.name')" prop="name" show-overflow-tooltip />
				<el-table-column :label="t('param.key')" prop="key" show-overflow-tooltip />
				<el-table-column :label="t('param.value')" prop="value" show-overflow-tooltip />
        <el-table-column :label="t('param.sn')" prop="sn" show-overflow-tooltip />
        <el-table-column :label="t('param.status')" prop="status" show-overflow-tooltip>
					<template #default="scope">
						<dict-tag :options="status_type" :value="scope.row.status"></dict-tag>
					</template>
				</el-table-column>
				<el-table-column :label="t('param.createTime')" prop="createTime" show-overflow-tooltip />
				<el-table-column :label="t('param.systemFlag')" prop="systemFlag" show-overflow-tooltip>
					<template #default="scope">
						<dict-tag :options="dict_type" :value="scope.row.systemFlag"></dict-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('common.action')" width="150">
					<template #default="scope">
						<el-button icon="edit-pen" @click="formDialogRef.openDialog(scope.row.id)" text type="primary"
							>{{ $t('common.editBtn') }}
						</el-button>

						<el-tooltip :content="$t('param.deleteDisabledTip')" :disabled="scope.row.systemFlag === '0'" placement="top">
							<span style="margin-left: 12px">
								<el-button
									icon="delete"
									v-auth="'sys_syspublicparam_del'"
									:disabled="scope.row.systemFlag !== '0'"
									@click="handleDelete([scope.row.id])"
									text
									type="primary"
								>
									{{ $t('common.delBtn') }}
								</el-button>
							</span>
						</el-tooltip>
					</template>
				</el-table-column>
			</el-table>
			<pagination @current-change="currentChangeHandle" @size-change="sizeChangeHandle" v-bind="state.pagination" />
		</div>

		<!-- 编辑、新增  -->
		<form-dialog @refresh="getDataList()" ref="formDialogRef" />
	</div>
</template>

<script lang="ts" name="systemSysPublicParam" setup>
import { BasicTableProps, useTable } from '/@/hooks/table';
import { delObj, fetchList, refreshCache } from '/@/api/admin/param';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useDict } from '/@/hooks/dict';
import { useI18n } from 'vue-i18n';

// 引入组件
const FormDialog = defineAsyncComponent(() => import('./form.vue'));
const { t } = useI18n();
// 定义查询字典

const { dict_type, status_type } = useDict('dict_type', 'status_type');
// 定义变量内容
const formDialogRef = ref();
// 搜索变量
const queryRef = ref();
const showSearch = ref(true);
// 多选变量
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		systemFlag: '',
	},
	pageList: fetchList,
	ascs: ['sn'],
});

//  table hook
const { getDataList, currentChangeHandle, sizeChangeHandle, downBlobFile, tableStyle } = useTable(state);

// 清空搜索条件
const resetQuery = () => {
	queryRef.value.resetFields();
	getDataList();
};

// 是否可以多选
const handleSelectable = (row: any) => {
	// 系统类不可多选删除
	return row.systemFlag !== '1';
};

// 导出excel
const exportExcel = () => {
	downBlobFile('/admin/param/export', state.queryForm, 'param.xlsx');
};

const handleRefreshCache = () => {
	refreshCache().then(() => {
		useMessage().success('同步成功');
	});
};

// 多选事件
const handleSelectionChange = (objs: { id: string }[]) => {
	selectObjs.value = objs.map(({ id }) => id);
	multiple.value = !objs.length;
};

// 删除操作
const handleDelete = async (ids: string[]) => {
	try {
		await useMessageBox().confirm(t('common.delConfirmText'));
	} catch {
		return;
	}

	try {
		await delObj(ids);
		getDataList();
		useMessage().success(t('common.delSuccessText'));
	} catch (err: any) {
		useMessage().error(err.msg);
	}
};
</script>
