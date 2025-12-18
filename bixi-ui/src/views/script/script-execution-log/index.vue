<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item :label="$t('scriptExecutionLog.scriptId')" prop="scriptId">
						<el-input v-model="state.queryForm.scriptId" :placeholder="$t('scriptExecutionLog.inputScriptIdTip')" clearable />
					</el-form-item>
					<el-form-item :label="$t('scriptExecutionLog.siteId')" prop="siteId">
						<el-input v-model="state.queryForm.siteId" :placeholder="$t('scriptExecutionLog.inputSiteIdTip')" clearable />
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
				<el-table-column :label="$t('scriptExecutionLog.scriptId')" prop="scriptId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptExecutionLog.siteId')" prop="siteId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptExecutionLog.taskId')" prop="taskId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptExecutionLog.status')" prop="status" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.status === '0'" type="success">成功</el-tag>
						<el-tag v-else-if="scope.row.status === '1'" type="danger">失败</el-tag>
						<el-tag v-else-if="scope.row.status === '2'" type="info">跳过</el-tag>
						<el-tag v-else type="warning">部分成功</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="$t('scriptExecutionLog.executorId')" prop="executorId" show-overflow-tooltip />
				<el-table-column :label="$t('scriptExecutionLog.startTime')" prop="startTime" show-overflow-tooltip width="180" />
				<el-table-column :label="$t('scriptExecutionLog.durationMs')" prop="durationMs" show-overflow-tooltip />
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
		<script-execution-log-form ref="formRef" @refresh="getDataList(false)" />
	</div>
</template>

<script lang="ts" setup name="scriptExecutionLog">
import { delObj, pageList } from '/@/api/script/script-execution-log';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

// 引入组件
const ScriptExecutionLogForm = defineAsyncComponent(() => import('./form.vue'));

const { t } = useI18n();

// 定义变量内容
const formRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		scriptId: '',
		siteId: '',
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
