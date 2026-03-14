<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-card shadow="never">
				<template #header>
					<div class="card-header">
						<div class="header-left">
							<el-button icon="Back" @click="handleBack">返回</el-button>
							<span class="form-name">{{ formName }} - 权限配置</span>
						</div>
					</div>
				</template>

				<el-tabs v-model="activeTab" class="permission-tabs">
					<el-tab-pane label="角色权限" name="role">
						<el-row :gutter="20">
							<el-col :span="8">
								<el-card shadow="never" class="role-card">
									<template #header>
										<div class="card-title">角色列表</div>
									</template>
									<el-table
										:data="roleList"
										highlight-current-row
										@current-change="handleRoleChange"
										v-loading="roleLoading"
										max-height="500"
									>
										<el-table-column label="角色名称" prop="roleName" />
										<el-table-column label="角色标识" prop="roleCode" />
									</el-table>
								</el-card>
							</el-col>
							<el-col :span="16">
								<el-card shadow="never" class="permission-card">
									<template #header>
										<div class="card-title">
											<span>权限配置</span>
											<el-button
												type="primary"
												size="small"
												:disabled="!selectedRole"
												:loading="saving"
												@click="handleSavePermission"
											>
												保存权限
											</el-button>
										</div>
									</template>
									<el-empty v-if="!selectedRole" description="请先选择角色" />
									<div v-else>
										<el-form :model="permissionForm" label-width="100px" v-loading="permissionLoading">
											<el-form-item label="表单权限">
												<el-radio-group v-model="permissionForm.formPermission">
													<el-radio label="view">只读</el-radio>
													<el-radio label="edit">编辑</el-radio>
													<el-radio label="none">无权限</el-radio>
												</el-radio-group>
											</el-form-item>
											<el-form-item label="数据权限">
												<el-radio-group v-model="permissionForm.dataPermission">
													<el-radio label="all">全部数据</el-radio>
													<el-radio label="dept">本部门数据</el-radio>
													<el-radio label="self">仅本人数据</el-radio>
												</el-radio-group>
											</el-form-item>
										</el-form>
									</div>
								</el-card>
							</el-col>
						</el-row>
					</el-tab-pane>

					<el-tab-pane label="字段权限" name="field">
						<el-row :gutter="20">
							<el-col :span="8">
								<el-card shadow="never" class="role-card">
									<template #header>
										<div class="card-title">角色列表</div>
									</template>
									<el-table
										:data="roleList"
										highlight-current-row
										@current-change="handleFieldRoleChange"
										v-loading="roleLoading"
										max-height="500"
									>
										<el-table-column label="角色名称" prop="roleName" />
										<el-table-column label="角色标识" prop="roleCode" />
									</el-table>
								</el-card>
							</el-col>
							<el-col :span="16">
								<el-card shadow="never" class="field-card">
									<template #header>
										<div class="card-title">
											<span>字段权限配置</span>
											<el-button
												type="primary"
												size="small"
												:disabled="!selectedFieldRole"
												:loading="fieldSaving"
												@click="handleSaveFieldPermission"
											>
												保存字段权限
											</el-button>
										</div>
									</template>
									<el-empty v-if="!selectedFieldRole" description="请先选择角色" />
									<el-table
										v-else
										:data="fieldList"
										border
										v-loading="fieldLoading"
										:cell-style="tableStyle.cellStyle"
										:header-cell-style="tableStyle.headerCellStyle"
									>
										<el-table-column label="字段名称" prop="fieldName" />
										<el-table-column label="字段标识" prop="fieldKey" />
										<el-table-column label="字段类型" prop="fieldType" width="120" />
										<el-table-column label="权限设置" width="200">
											<template #default="scope">
												<el-select v-model="scope.row.permission" placeholder="请选择权限">
													<el-option label="可编辑" value="edit" />
													<el-option label="只读" value="view" />
													<el-option label="隐藏" value="hidden" />
												</el-select>
											</template>
										</el-table-column>
									</el-table>
								</el-card>
							</el-col>
						</el-row>
					</el-tab-pane>
				</el-tabs>
			</el-card>
		</div>
	</div>
</template>

<script lang="ts" name="workflowFormPermission" setup>
import { useMessage } from '/@/hooks/message';
import { getPermissionList, savePermission, getFieldPermissions } from '/@/api/workflow/form';
import { useRoute, useRouter } from 'vue-router';
import { tableStyle } from '/@/hooks/table';
import { pageList as getRoleList } from '/@/api/admin/role';

const route = useRoute();
const router = useRouter();

const formId = ref('');
const formName = ref('');
const activeTab = ref('role');
const roleList = ref<any[]>([]);
const roleLoading = ref(false);
const selectedRole = ref<any>(null);
const selectedFieldRole = ref<any>(null);
const permissionLoading = ref(false);
const fieldLoading = ref(false);
const saving = ref(false);
const fieldSaving = ref(false);

const permissionForm = reactive({
	formPermission: 'view',
	dataPermission: 'self',
});

const fieldList = ref<any[]>([]);

onMounted(async () => {
	formId.value = route.query.formId as string;
	formName.value = route.query.formName as string || '未命名表单';
	await loadRoleList();
});

const loadRoleList = async () => {
	try {
		roleLoading.value = true;
		const res = await getRoleList({ size: -1 });
		roleList.value = res.data?.records || res.data || [];
	} catch (err: any) {
		useMessage().error(err.msg || '加载角色列表失败');
	} finally {
		roleLoading.value = false;
	}
};

const handleBack = () => {
	router.push('/workflow/form');
};

const handleRoleChange = async (row: any) => {
	selectedRole.value = row;
	if (!row) return;

	try {
		permissionLoading.value = true;
		const res = await getPermissionList(formId.value);
		const permission = res.data?.find((p: any) => p.roleId === row.id);
		if (permission) {
			permissionForm.formPermission = permission.formPermission || 'view';
			permissionForm.dataPermission = permission.dataPermission || 'self';
		} else {
			permissionForm.formPermission = 'view';
			permissionForm.dataPermission = 'self';
		}
	} catch (err: any) {
		useMessage().error(err.msg || '加载权限配置失败');
	} finally {
		permissionLoading.value = false;
	}
};

const handleSavePermission = async () => {
	if (!selectedRole.value) return;

	try {
		saving.value = true;
		await savePermission({
			formId: formId.value,
			roleId: selectedRole.value.id,
			formPermission: permissionForm.formPermission,
			dataPermission: permissionForm.dataPermission,
		});
		useMessage().success('保存成功');
	} catch (err: any) {
		useMessage().error(err.msg || '保存失败');
	} finally {
		saving.value = false;
	}
};

const handleFieldRoleChange = async (row: any) => {
	selectedFieldRole.value = row;
	if (!row) return;

	try {
		fieldLoading.value = true;
		const res = await getFieldPermissions(formId.value, row.id);
		fieldList.value = res.data || [];
	} catch (err: any) {
		useMessage().error(err.msg || '加载字段权限失败');
	} finally {
		fieldLoading.value = false;
	}
};

const handleSaveFieldPermission = async () => {
	if (!selectedFieldRole.value) return;

	try {
		fieldSaving.value = true;
		await savePermission({
			formId: formId.value,
			roleId: selectedFieldRole.value.id,
			fieldPermissions: fieldList.value.map((f) => ({
				fieldKey: f.fieldKey,
				permission: f.permission,
			})),
		});
		useMessage().success('保存成功');
	} catch (err: any) {
		useMessage().error(err.msg || '保存失败');
	} finally {
		fieldSaving.value = false;
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

.permission-tabs {
	.card-title {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-weight: 500;
	}
}

.role-card,
.permission-card,
.field-card {
	min-height: 600px;
}
</style>
