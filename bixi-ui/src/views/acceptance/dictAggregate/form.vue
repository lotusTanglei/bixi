<template>
	<el-dialog v-model="visible" :close-on-click-modal="false" :title="form.id ? '编辑字典表' : '新增字典表'" width="min(900px, 94vw)">
		<el-form ref="dataFormRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
			<el-form-item label="字典类型" prop="type">
				<el-input v-model="form.type" clearable placeholder="输入字典类型" />
			</el-form-item>
			<el-form-item label="字典名称" prop="name">
				<el-input v-model="form.name" clearable placeholder="输入字典名称" />
			</el-form-item>
			<el-form-item label="字典描述" prop="description">
				<el-input v-model="form.description" clearable placeholder="输入字典描述" />
			</el-form-item>
			<el-form-item label="排序号" prop="sn">
				<el-input-number v-model="form.sn" />
			</el-form-item>
			<el-form-item label="系统标志" prop="systemFlag">
				<el-input v-model="form.systemFlag" clearable placeholder="输入系统标志" />
			</el-form-item>
			<el-form-item label="状态（0正常 1停用）" prop="status">
				<el-input v-model="form.status" clearable placeholder="输入状态（0正常 1停用）" />
			</el-form-item>
			<el-form-item label="数据状态（用来标识数据状态，可用于割接，特殊数据处理）" prop="dataStatus">
				<el-input v-model="form.dataStatus" clearable placeholder="输入数据状态（用来标识数据状态，可用于割接，特殊数据处理）" />
			</el-form-item>
			<el-form-item label="备注" prop="remark">
				<el-input v-model="form.remark" clearable placeholder="输入备注" />
			</el-form-item>
			<div class="child-toolbar">
				<span>字典表明细</span>
					<el-button v-auth-all="writePermissions" icon="Plus" type="primary" @click="addChild">新增明细</el-button>
			</div>
			<el-table :data="form.children" border empty-text="暂无明细">
				<el-table-column label="字典项值" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.value`">
							<el-input v-model="scope.row.value" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="字典项标签" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.label`">
							<el-input v-model="scope.row.label" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="字典类型" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.dictType`">
							<el-input v-model="scope.row.dictType" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="字典项描述" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.description`">
							<el-input v-model="scope.row.description" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="排序" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.sn`">
							<el-input-number v-model="scope.row.sn" />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="状态（0正常 1停用）" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.status`">
							<el-input v-model="scope.row.status" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="数据状态（用来标识数据状态，可用于割接，特殊数据处理）" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.dataStatus`">
							<el-input v-model="scope.row.dataStatus" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column label="备注" min-width="150">
					<template #default="scope">
							<el-form-item :prop="`children.${scope.$index}.remark`">
							<el-input v-model="scope.row.remark" clearable />
						</el-form-item>
					</template>
				</el-table-column>
				<el-table-column align="center" fixed="right" label="操作" width="76">
					<template #default="scope">
									<el-button v-auth-all="writePermissions" icon="Delete" text type="danger" @click="removeChild(scope.$index)" />
					</template>
				</el-table-column>
			</el-table>
		</el-form>
		<template #footer>
			<el-button @click="visible = false">取消</el-button>
				<el-button v-auth-all="writePermissions" :loading="loading" type="primary" @click="submit">确认</el-button>
		</template>
	</el-dialog>
</template>

<script lang="ts" name="acceptanceSysDictDialog" setup>
import { addObj, getObj, putObj, SysDictItemForm, SysDictForm } from '/@/api/acceptance/dictAggregate';
import { useMessage } from '/@/hooks/message';

const emit = defineEmits(['refresh']);
const visible = ref(false);
const loading = ref(false);
const dataFormRef = ref();
const emptyChild = (): SysDictItemForm => ({
	value: '',
	label: '',
	dictType: '',
	description: '',
	sn:  0,
	status: '',
	dataStatus: '',
	remark: '',
});
const emptyForm = (): SysDictForm => ({
	type: '',
	name: '',
	description: '',
	sn:  0,
	systemFlag: '',
	status: '',
	dataStatus: '',
	remark: '',
	children: [],
});
const form = reactive<SysDictForm>(emptyForm());
const writePermissions = computed(() => form.id
	? ['acceptance_dict_aggregate_edit', 'acceptance_sys_dict_item_edit']
	: ['acceptance_dict_aggregate_add', 'acceptance_sys_dict_item_add']);
const rules = {
	type: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
	name: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
};
const addChild = () => form.children.push(emptyChild());
const removeChild = (index: number) => form.children.splice(index, 1);
const openDialog = async (id?: string) => {
	Object.assign(form, emptyForm());
	visible.value = true;
	await nextTick();
	dataFormRef.value?.clearValidate();
	if (!id) return;
	try { loading.value = true; Object.assign(form, (await getObj(id)).data); }
	catch (error: any) { useMessage().error(error?.msg || '加载失败'); }
	finally { loading.value = false; }
};
const submit = async () => {
	if (!(await dataFormRef.value?.validate().catch(() => false))) return;
	if (form.children.length === 0 || form.children.some((child) => !child.value?.trim() || !child.label?.trim())) {
		useMessage().error('请至少添加一条完整的字典明细');
		return;
	}
	try {
		loading.value = true;
		await (form.id ? putObj({ ...form }) : addObj({ ...form }));
		useMessage().success(form.id ? '修改成功' : '新增成功');
		visible.value = false;
		emit('refresh');
	} catch (error: any) { useMessage().error(error?.msg || '保存失败'); }
	finally { loading.value = false; }
};
defineExpose({ openDialog });
</script>

<style scoped>
.child-toolbar { display: flex; align-items: center; justify-content: space-between; margin: 12px 0 8px; }
:deep(.el-table .el-form-item) { margin: 14px 0; }
</style>
