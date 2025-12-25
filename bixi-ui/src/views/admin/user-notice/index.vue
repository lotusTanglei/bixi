<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row v-show="showSearch">
				<el-form ref="queryRef" :inline="true" :model="state.queryForm" @keyup.enter="getDataList">
					<el-form-item :label="t('userNotice.noticeId')" prop="noticeId">
						<el-input v-model="state.queryForm.noticeId" :placeholder="t('userNotice.inputNoticeIdTip')" clearable />
					</el-form-item>
					<el-form-item :label="t('userNotice.isRead')" prop="isRead">
						<el-select v-model="state.queryForm.isRead" :placeholder="t('userNotice.inputIsReadTip')" clearable>
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
			<el-row>
				<div class="mb8" style="width: 100%">
					<el-button
						plain
						:disabled="multiple"
						class="ml10"
						icon="Delete"
						type="primary"
						@click="handleDelete(selectObjs)"
					>
						{{ t('common.delBtn') }}
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
				<el-table-column :label="t('userNotice.index')" type="index" width="60" />
				<el-table-column label="标题" prop="title" show-overflow-tooltip />
				<el-table-column label="类型" prop="type" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.type === '0'">通知</el-tag>
						<el-tag v-else-if="scope.row.type === '1'" type="warning">公告</el-tag>
						<el-tag v-else type="info">私信</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="优先级" prop="priority" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.priority === '0'" type="info">普通</el-tag>
						<el-tag v-else-if="scope.row.priority === '1'" type="warning">重要</el-tag>
						<el-tag v-else type="danger">紧急</el-tag>
					</template>
				</el-table-column>
				<el-table-column label="发送人" prop="senderName" show-overflow-tooltip />
				<el-table-column :label="t('userNotice.isRead')" prop="isRead" show-overflow-tooltip>
					<template #default="scope">
						<el-tag v-if="scope.row.isRead === '0'" type="danger">未读</el-tag>
						<el-tag v-else type="success">已读</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="t('userNotice.readTime')" prop="readTime" show-overflow-tooltip width="180" />
				<el-table-column :label="t('common.action')" width="150" fixed="right">
					<template #default="scope">
						<el-button icon="view" text type="primary" @click="formRef.openDialog(scope.row.id)">
							查看
						</el-button>
						<el-button icon="delete" text type="primary" @click="handleDelete([scope.row.id])">
							{{ t('common.delBtn') }}
						</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" @current-change="currentChangeHandle" @size-change="sizeChangeHandle"> </pagination>
		</div>
		<user-notice-form ref="formRef" @refresh="getDataList(false)" />
	</div>
</template>

<script lang="ts" setup name="sysUserNotice">
import { delObj, pageList } from '/@/api/admin/user-notice';
import { BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

// 引入组件
const UserNoticeForm = defineAsyncComponent(() => import('./form.vue'));

const { t } = useI18n();

// 定义变量内容
const formRef = ref();
const queryRef = ref();
const showSearch = ref(true);
const selectObjs = ref([]) as any;
const multiple = ref(true);

const state: BasicTableProps = reactive<BasicTableProps>({
	queryForm: {
		noticeId: '',
		isRead: '',
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
