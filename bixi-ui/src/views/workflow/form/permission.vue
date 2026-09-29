<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view permission-page">
			<header class="page-header">
				<div class="header-title">
					<el-button icon="Back" @click="handleBack">返回</el-button>
					<span>{{ formName }} - 字段权限</span>
				</div>
				<el-button
					v-auth="'workflow_form_edit'"
					type="primary"
					icon="Check"
					:disabled="!selectedRole || !selectedVersionId || fieldList.length === 0"
					:loading="saving"
					@click="handleSaveFieldPermission"
				>
					保存字段权限
				</el-button>
			</header>

			<el-form :inline="true" :model="scopeForm" class="scope-toolbar">
				<el-form-item label="表单版本" required>
					<el-select v-model="selectedVersionId" placeholder="请选择版本" style="width: 180px" @change="handleScopeChange">
						<el-option
							v-for="item in versionList"
							:key="item.id"
							:label="`v${item.version}${item.isActive === '1' ? '（当前）' : ''}`"
							:value="item.id"
						/>
					</el-select>
				</el-form-item>
				<el-form-item label="流程定义 ID">
					<el-input v-model="scopeForm.processDefinitionId" clearable placeholder="留空表示全部流程定义" @change="handleScopeChange" />
				</el-form-item>
				<el-form-item label="任务节点 Key">
					<el-input v-model="scopeForm.taskDefinitionKey" clearable placeholder="留空表示全部节点" @change="handleScopeChange" />
				</el-form-item>
				<el-form-item>
					<el-button icon="Refresh" :disabled="!selectedRole" @click="loadFieldPermissions">刷新</el-button>
				</el-form-item>
			</el-form>

			<div class="permission-grid">
				<section class="role-pane">
					<div class="pane-title">角色</div>
					<el-table
						:data="roleList"
						highlight-current-row
						v-loading="roleLoading"
						@current-change="handleRoleChange"
					>
						<el-table-column label="角色名称" prop="roleName" min-width="120" />
						<el-table-column label="角色标识" prop="roleCode" min-width="120" show-overflow-tooltip />
					</el-table>
				</section>

				<section class="field-pane">
					<div class="pane-title">
						<span>字段策略</span>
						<span v-if="selectedRole" class="selected-role">{{ selectedRole.roleName }}</span>
					</div>
					<el-empty v-if="!selectedRole" description="请选择角色" />
					<el-empty v-else-if="!selectedVersionId" description="当前表单还没有可配置版本" />
					<el-table
						v-else
						:data="fieldList"
						border
						v-loading="fieldLoading"
						:cell-style="tableStyle.cellStyle"
						:header-cell-style="tableStyle.headerCellStyle"
					>
						<el-table-column label="字段名称" prop="fieldLabel" min-width="160" />
						<el-table-column label="字段标识" prop="fieldCode" min-width="160" show-overflow-tooltip />
						<el-table-column label="组件类型" prop="fieldType" width="120" />
						<el-table-column label="访问策略" width="180">
							<template #default="scope">
								<el-select v-model="scope.row.permType" aria-label="字段访问策略">
									<el-option label="可编辑" value="edit" />
									<el-option label="只读" value="readonly" />
									<el-option label="隐藏" value="hidden" />
								</el-select>
							</template>
						</el-table-column>
					</el-table>
				</section>
			</div>
		</div>
	</div>
</template>

<script lang="ts" name="workflowFormPermission" setup>
import { getFieldPermissions, getVersionList, saveFieldPermissions } from '/@/api/workflow/form';
import { list as getRoleList } from '/@/api/admin/role';
import { useMessage } from '/@/hooks/message';
import { tableStyle } from '/@/hooks/table';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const formId = ref('');
const formName = ref('');
const versionList = ref<any[]>([]);
const selectedVersionId = ref<string | number>('');
const roleList = ref<any[]>([]);
const selectedRole = ref<any>(null);
const fieldList = ref<any[]>([]);
const roleLoading = ref(false);
const fieldLoading = ref(false);
const saving = ref(false);
const scopeForm = reactive({
	processDefinitionId: '',
	taskDefinitionKey: '',
});

onMounted(async () => {
	formId.value = route.query.formId as string;
	formName.value = (route.query.formName as string) || '未命名表单';
	await Promise.all([loadRoleList(), loadVersionList()]);
});

const loadRoleList = async () => {
	try {
		roleLoading.value = true;
		const res = await getRoleList();
		roleList.value = res.data || [];
	} catch (err: any) {
		useMessage().error(err.msg || '加载角色列表失败');
	} finally {
		roleLoading.value = false;
	}
};

const loadVersionList = async () => {
	try {
		const res = await getVersionList(formId.value);
		versionList.value = res.data || [];
		selectedVersionId.value = versionList.value.find((item) => item.isActive === '1')?.id || versionList.value[0]?.id || '';
	} catch (err: any) {
		useMessage().error(err.msg || '加载表单版本失败');
	}
};

const handleBack = () => {
	router.push('/workflow/form/index');
};

const handleRoleChange = async (row: any) => {
	selectedRole.value = row;
	fieldList.value = [];
	if (row && selectedVersionId.value) await loadFieldPermissions();
};

const handleScopeChange = async () => {
	fieldList.value = [];
	if (selectedRole.value && selectedVersionId.value) await loadFieldPermissions();
};

const scopeParams = () => ({
	processDefinitionId: scopeForm.processDefinitionId.trim() || undefined,
	taskDefinitionKey: scopeForm.taskDefinitionKey.trim() || undefined,
});

const loadFieldPermissions = async () => {
	if (!selectedRole.value || !selectedVersionId.value) return;
	try {
		fieldLoading.value = true;
		const res = await getFieldPermissions(formId.value, selectedRole.value.id, selectedVersionId.value, scopeParams());
		fieldList.value = res.data || [];
	} catch (err: any) {
		fieldList.value = [];
		useMessage().error(err.msg || '加载字段权限失败');
	} finally {
		fieldLoading.value = false;
	}
};

const handleSaveFieldPermission = async () => {
	if (!selectedRole.value || !selectedVersionId.value || fieldList.value.length === 0) return;
	try {
		saving.value = true;
		await saveFieldPermissions(formId.value, selectedRole.value.id, selectedVersionId.value, {
			...scopeParams(),
			fields: fieldList.value.map((field) => ({
				fieldCode: field.fieldCode,
				permType: field.permType,
			})),
		});
		useMessage().success('字段权限保存成功');
		await loadFieldPermissions();
	} catch (err: any) {
		useMessage().error(err.msg || '保存字段权限失败');
	} finally {
		saving.value = false;
	}
};
</script>

<style lang="scss" scoped>
.permission-page {
	display: flex;
	min-height: 0;
	flex-direction: column;
	gap: 16px;
}

.page-header,
.header-title,
.pane-title {
	display: flex;
	align-items: center;
}

.page-header {
	justify-content: space-between;
	gap: 16px;
	border-bottom: 1px solid var(--el-border-color-light);
	padding-bottom: 14px;
}

.header-title {
	gap: 12px;
	font-size: 16px;
	font-weight: 600;
}

.scope-toolbar {
	border-bottom: 1px solid var(--el-border-color-lighter);
}

.permission-grid {
	display: grid;
	min-height: 0;
	grid-template-columns: minmax(280px, 0.35fr) minmax(480px, 1fr);
	gap: 20px;
}

.role-pane,
.field-pane {
	min-width: 0;
}

.role-pane {
	border-right: 1px solid var(--el-border-color-lighter);
	padding-right: 20px;
}

.pane-title {
	min-height: 36px;
	justify-content: space-between;
	font-weight: 600;
}

.selected-role {
	color: var(--el-text-color-secondary);
	font-size: 13px;
	font-weight: 400;
}

@media (max-width: 900px) {
	.page-header {
		align-items: flex-start;
		flex-direction: column;
	}

	.permission-grid {
		grid-template-columns: 1fr;
	}

	.role-pane {
		border-bottom: 1px solid var(--el-border-color-lighter);
		border-right: 0;
		padding-bottom: 16px;
		padding-right: 0;
	}
}
</style>
