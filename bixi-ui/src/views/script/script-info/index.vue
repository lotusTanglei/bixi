<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item :label="$t('scriptInfo.name')" prop="name">
						<el-input v-model="state.queryForm.name" :placeholder="$t('scriptInfo.inputNameTip')" clearable />
					</el-form-item>
					<el-form-item :label="$t('scriptInfo.code')" prop="code">
						<el-input v-model="state.queryForm.code" :placeholder="$t('scriptInfo.inputCodeTip')" clearable />
					</el-form-item>
					<el-form-item>
						<el-button icon="Search" type="primary" @click="getDataList">{{ $t('common.queryBtn') }}</el-button>
						<el-button icon="Refresh" @click="resetQuery">{{ $t('common.resetBtn') }}</el-button>
					</el-form-item>
				</el-form>
			</el-row>
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button icon="folder-add" type="primary" @click="formRef.openDialog()">
						{{ $t('common.addBtn') }}
					</el-button>
					<el-button
						plain
						:disabled="multiple"
						class="ml10"
						icon="Delete"
						type="primary"
						@click="handleDelete(selectObjs)"
					>
						{{ $t('common.delBtn') }}
					</el-button>
					<right-toolbar
						v-model:showSearch="showSearch"
						@queryTable="getDataList"
						class="ml10 mr20"
						style="float: right"
					/>
				</div>
			</el-row>
			<el-table
				v-loading="state.loading"
				:data="state.dataList"
				@selection-change="handleSelectionChange"
				border
				:cell-style="tableStyle.cellStyle"
				:header-cell-style="tableStyle.headerCellStyle"
			>
				<el-table-column type="selection" width="40" />
				<el-table-column label="序号" type="index" width="60" />
				<el-table-column :label="$t('scriptInfo.name')" prop="name" show-overflow-tooltip />
				<el-table-column :label="$t('scriptInfo.code')" prop="code" show-overflow-tooltip />
				<el-table-column :label="$t('scriptInfo.version')" prop="version" show-overflow-tooltip />
				<el-table-column :label="$t('scriptInfo.type')" prop="type" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.type === '0'">DDL</el-tag>
						<el-tag v-else-if="scope.row.type === '1'" type="success">DML</el-tag>
						<el-tag v-else type="info">其他</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptInfo.riskLevel')" prop="riskLevel" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.riskLevel === '0'" type="success">低</el-tag>
						<el-tag v-else-if="scope.row.riskLevel === '1'" type="warning">中</el-tag>
						<el-tag v-else type="danger">高</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptInfo.status')" prop="status" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.status === '0'" type="info">草稿</el-tag>
						<el-tag v-else-if="scope.row.status === '1'" type="success">已发布</el-tag>
						<el-tag v-else type="danger">已废弃</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptInfo.createTime')" prop="createTime" show-overflow-tooltip width="180" />
				<el-table-column :label="$t('common.action')" width="150" fixed="right">
					<template #default="scope">
						<el-button icon="edit-pen" text type="primary" @click="formRef.openDialog(scope.row.id)">
							{{ $t('common.editBtn') }}
						</el-button>
						<el-button icon="delete" text type="primary" @click="handleDelete([scope.row.id])">
							{{ $t('common.delBtn') }}
						</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>
		<script-info-form ref="formRef" @refresh="getDataList(false)" />
	</div>
</template>

<script lang="ts" setup name="scriptInfo">
import { delObj, pageList } from '/@/api/script/script-info';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

// 引入组件
const ScriptInfoForm = defineAsyncComponent(() => import('./form.vue'));

const { t } = useI18n();

// 定义变量内容
const formRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		name: '',
		code: '',
	},
	pageList: pageList,
});

const { getDataList, currentChangeHandle, sizeChangeHandle, tableStyle } = useTable(state);

// 清空搜索条件
const resetQuery = () => {
	queryRef.value?.resetFields();
	getDataList();
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
		// 循环删除或批量删除，此处假设循环
        for (const id of ids) {
            await delObj(id);
        }
		getDataList();
		useMessage().success(t('common.delSuccessText'));
	} catch (err: any) {
		useMessage().error(err.msg);
	}
};
</script>
