<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item :label="$t('scriptSiteManager.siteId')" prop="siteId">
						<el-input v-model="state.queryForm.siteId" :placeholder="$t('scriptSiteManager.inputSiteIdTip')" clearable />
					</el-form-item>
					<el-form-item :label="$t('scriptSiteManager.userId')" prop="userId">
						<el-input v-model="state.queryForm.userId" :placeholder="$t('scriptSiteManager.inputUserIdTip')" clearable />
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
				<el-table-column :label="$t('scriptSiteManager.siteId')" prop="siteId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptSiteManager.userId')" prop="userId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptSiteManager.isPrimary')" prop="isPrimary" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.isPrimary === '1'" type="success">是</el-tag>
						<el-tag v-else type="info">否</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptSiteManager.roleType')" prop="roleType" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.roleType === 'owner'">负责人</el-tag>
						<el-tag v-else-if="scope.row.roleType === 'backup'">备份</el-tag>
						<el-tag v-else>观察者</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptSiteManager.status')" prop="status" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.status === '0'" type="success">有效</el-tag>
						<el-tag v-else type="danger">无效</el-tag>
					</template>
				</el-table-column>
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
		<script-site-manager-form ref="formRef" @refresh="getDataList(false)" />
	</div>
</template>

<script lang="ts" setup name="scriptSiteManager">
import { delObj, pageList } from '/@/api/script/script-site-manager';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

// 引入组件
const ScriptSiteManagerForm = defineAsyncComponent(() => import('./form.vue'));

const { t } = useI18n();

// 定义变量内容
const formRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		siteId: '',
		userId: '',
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
