<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-card shadow="never">
				<template #header>
					<div class="card-header">
						<div class="header-left">
							<el-button icon="Back" @click="handleBack">返回</el-button>
							<span class="form-name">{{ formName }} - 版本管理</span>
						</div>
						<div class="header-right">
							<el-button type="primary" icon="Plus" @click="handleCreateVersion">创建新版本</el-button>
						</div>
					</div>
				</template>

				<el-table
					v-loading="loading"
					:data="versionList"
					border
					:cell-style="tableStyle.cellStyle"
					:header-cell-style="tableStyle.headerCellStyle"
				>
					<el-table-column label="版本号" prop="version" width="120">
						<template #default="scope">
							<el-tag :type="scope.row.isActive ? 'success' : 'info'">
								v{{ scope.row.version }}
							</el-tag>
						</template>
					</el-table-column>
					<el-table-column label="状态" width="100">
						<template #default="scope">
							<el-tag v-if="scope.row.isActive" type="success">当前版本</el-tag>
							<el-tag v-else type="info">历史版本</el-tag>
						</template>
					</el-table-column>
					<el-table-column label="版本说明" prop="remark" show-overflow-tooltip></el-table-column>
					<el-table-column label="创建人" prop="createBy" width="120"></el-table-column>
					<el-table-column label="创建时间" prop="createTime" width="180"></el-table-column>
					<el-table-column label="操作" width="280" fixed="right">
						<template #default="scope">
							<el-button icon="View" text type="primary" @click="handleViewVersion(scope.row)">
								查看
							</el-button>
							<el-button
								v-if="!scope.row.isActive"
								icon="Check"
								text
								type="primary"
								@click="handleActivate(scope.row)"
							>
								激活
							</el-button>
							<el-button
								v-if="!scope.row.isActive"
								icon="RefreshLeft"
								text
								type="primary"
								@click="handleRollback(scope.row)"
							>
								回滚
							</el-button>
							<el-button icon="DocumentCopy" text type="primary" @click="handleDiff(scope.row)">
								对比
							</el-button>
						</template>
					</el-table-column>
				</el-table>
			</el-card>
		</div>

		<el-dialog v-model="viewVisible" title="版本详情" width="800px" destroy-on-close>
			<FormRenderer ref="formRendererRef" :form-schema="currentSchema" :readonly="true" />
		</el-dialog>

		<el-dialog v-model="diffVisible" title="版本对比" width="90%" destroy-on-close>
			<div class="diff-container">
				<div class="diff-left">
					<div class="diff-header">
						<span>版本选择：</span>
						<el-select v-model="diffVersion1" placeholder="选择版本" @change="loadDiffData">
							<el-option
								v-for="item in versionList"
								:key="item.id"
								:label="'v' + item.version"
								:value="item.version"
							/>
						</el-select>
					</div>
					<div class="diff-content">
						<pre>{{ diffData.version1 }}</pre>
					</div>
				</div>
				<div class="diff-right">
					<div class="diff-header">
						<span>版本选择：</span>
						<el-select v-model="diffVersion2" placeholder="选择版本" @change="loadDiffData">
							<el-option
								v-for="item in versionList"
								:key="item.id"
								:label="'v' + item.version"
								:value="item.version"
							/>
						</el-select>
					</div>
					<div class="diff-content">
						<pre>{{ diffData.version2 }}</pre>
					</div>
				</div>
			</div>
		</el-dialog>
	</div>
</template>

<script lang="ts" name="workflowFormVersion" setup>
import { useMessage, useMessageBox } from '/@/hooks/message';
import { getVersionList, activateVersion, rollbackVersion, diffVersions } from '/@/api/workflow/form';
import { useRoute, useRouter } from 'vue-router';
import { tableStyle } from '/@/hooks/table';

const FormRenderer = defineAsyncComponent(() => import('/@/components/form/FormRenderer.vue'));

const route = useRoute();
const router = useRouter();

const formRendererRef = ref();

const formId = ref('');
const formName = ref('');
const loading = ref(false);
const versionList = ref<any[]>([]);
const viewVisible = ref(false);
const diffVisible = ref(false);
const currentSchema = ref({});
const diffVersion1 = ref('');
const diffVersion2 = ref('');
const diffData = reactive({
	version1: '',
	version2: '',
});

onMounted(async () => {
	formId.value = route.query.formId as string;
	formName.value = route.query.formName as string || '未命名表单';
	await loadVersionList();
});

const loadVersionList = async () => {
	try {
		loading.value = true;
		const res = await getVersionList(formId.value);
		versionList.value = res.data || [];
		if (versionList.value.length > 0) {
			diffVersion1.value = versionList.value[0].version;
			if (versionList.value.length > 1) {
				diffVersion2.value = versionList.value[1].version;
			}
		}
	} catch (err: any) {
		useMessage().error(err.msg || '加载版本列表失败');
	} finally {
		loading.value = false;
	}
};

const handleBack = () => {
	router.push('/workflow/form');
};

const handleCreateVersion = () => {
	router.push({
		path: '/workflow/form/designer',
		query: {
			formId: formId.value,
			formName: formName.value,
		},
	});
};

const handleViewVersion = (row: any) => {
	if (row.schemaContent) {
		currentSchema.value = JSON.parse(row.schemaContent);
		viewVisible.value = true;
	}
};

const handleActivate = async (row: any) => {
	try {
		await useMessageBox().confirm(`确认激活版本 v${row.version} 吗？`);
		await activateVersion(formId.value, row.version);
		useMessage().success('激活成功');
		await loadVersionList();
	} catch (err: any) {
		if (err !== 'cancel') {
			useMessage().error(err.msg || '激活失败');
		}
	}
};

const handleRollback = async (row: any) => {
	try {
		await useMessageBox().confirm(`确认回滚到版本 v${row.version} 吗？`);
		await rollbackVersion(formId.value, row.version);
		useMessage().success('回滚成功');
		await loadVersionList();
	} catch (err: any) {
		if (err !== 'cancel') {
			useMessage().error(err.msg || '回滚失败');
		}
	}
};

const handleDiff = (row: any) => {
	diffVersion2.value = row.version;
	diffVisible.value = true;
	loadDiffData();
};

const loadDiffData = async () => {
	if (!diffVersion1.value || !diffVersion2.value) return;

	try {
		const res = await diffVersions(formId.value, diffVersion1.value, diffVersion2.value);
		diffData.version1 = JSON.stringify(res.data.version1, null, 2);
		diffData.version2 = JSON.stringify(res.data.version2, null, 2);
	} catch (err: any) {
		useMessage().error(err.msg || '加载对比数据失败');
	}
};
</script>

<style lang="scss" scoped>
.card-header {
	display: flex;
	justify-content: space-between;
	align-items: center;

	.header-left {
		display: flex;
		align-items: center;
		gap: 15px;

		.form-name {
			font-size: 16px;
			font-weight: 500;
		}
	}
}

.diff-container {
	display: flex;
	gap: 20px;
	height: 600px;

	.diff-left,
	.diff-right {
		flex: 1;
		display: flex;
		flex-direction: column;
		border: 1px solid #e4e7ed;
		border-radius: 4px;
		overflow: hidden;

		.diff-header {
			padding: 10px 15px;
			background: #f5f7fa;
			border-bottom: 1px solid #e4e7ed;
			display: flex;
			align-items: center;
			gap: 10px;
		}

		.diff-content {
			flex: 1;
			overflow: auto;
			padding: 15px;

			pre {
				margin: 0;
				font-size: 12px;
				line-height: 1.5;
			}
		}
	}
}
</style>
