<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-form inline @submit.prevent="getDataList">
				<el-form-item label="申请状态">
					<el-select v-model="state.queryForm.leaveStatus" clearable placeholder="全部状态" style="width: 160px">
						<el-option v-for="(label, value) in leaveStatusLabels" :key="value" :label="label" :value="value" />
					</el-select>
				</el-form-item>
				<el-form-item><el-button icon="Search" @click="getDataList">查询</el-button></el-form-item>
			</el-form>
			<div class="mb16">
				<el-button v-auth="'demo_leave_add'" type="primary" icon="Plus" @click="formRef.openDialog()">新建请假</el-button>
				<el-button v-auth="'demo_leave_del'" :disabled="!selected.length || busy" type="danger" plain @click="deleteDrafts">删除所选草稿</el-button>
				<el-button icon="Refresh" @click="getDataList(false)">刷新列表</el-button>
			</div>
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-table :data="state.dataList" v-loading="state.loading || busy" border empty-text="暂无请假申请" @selection-change="selected = $event">
				<el-table-column type="selection" width="44" :selectable="(row: LeaveRequest) => row.leaveStatus === 'DRAFT'" />
				<el-table-column prop="startDate" label="开始日期" width="120" />
				<el-table-column prop="endDate" label="结束日期" width="120" />
				<el-table-column prop="reason" label="请假原因" min-width="200" show-overflow-tooltip />
				<el-table-column label="状态" width="120"><template #default="{ row }"><el-tag>{{ leaveStatusLabels[row.leaveStatus] }}</el-tag></template></el-table-column>
				<el-table-column prop="submittedAt" label="提交时间" width="180" />
				<el-table-column label="操作" width="280" fixed="right">
					<template #default="{ row }">
						<el-button v-auth="'demo_leave_view'" text type="primary" @click="detailRef.openDialog(row.id)">详情</el-button>
						<template v-if="row.leaveStatus === 'DRAFT'">
							<el-button v-auth="'demo_leave_edit'" text type="primary" @click="formRef.openDialog(row.id)">编辑</el-button>
							<el-button v-auth="'demo_leave_edit'" text type="primary" @click="submitDraft(row)">提交审批</el-button>
						</template>
						<el-button v-if="row.processInstanceId" v-auth="'demo_leave_edit'" text type="primary" @click="refreshState(row)">同步状态</el-button>
					</template>
				</el-table-column>
			</el-table>
			<pagination v-bind="state.pagination" :page-sizes="[10, 20, 50, 100]" @current-change="currentChangeHandle" @size-change="sizeChangeHandle" />
		</div>
		<form-dialog ref="formRef" @refresh="getDataList" />
		<detail-dialog ref="detailRef" />
	</div>
</template>
<script lang="ts" name="demoLeave" setup>
import { fetchList, remove, submit, refresh, leaveStatusLabels, type LeaveRequest } from '/@/api/demo/leave';
import { type BasicTableProps, useTable } from '/@/hooks/table';
import { useMessage, useMessageBox } from '/@/hooks/message';
import FormDialog from './form.vue';
import DetailDialog from './detail.vue';
const formRef = ref();
const detailRef = ref();
const selected = ref<LeaveRequest[]>([]);
const busy = ref(false);
const error = ref('');
const state = reactive<BasicTableProps>({ queryForm: {}, pageList: fetchList });
const { getDataList, currentChangeHandle, sizeChangeHandle } = useTable(state);
const run = async (action: () => Promise<unknown>, success: string) => {
	busy.value = true; error.value = '';
	try { await action(); useMessage().success(success); }
	catch (err: any) { error.value = err?.msg || '操作失败，请刷新确认当前状态'; }
	finally { busy.value = false; getDataList(false); }
};
const submitDraft = async (row: LeaveRequest) => {
	try { await useMessageBox().confirm('提交后将进入审批，申请内容不能再修改。确认提交？'); }
	catch { return; }
	await run(() => submit(row.id), '申请已提交');
};
const refreshState = (row: LeaveRequest) => run(() => refresh(row.id), '状态已同步');
const deleteDrafts = async () => {
	try { await useMessageBox().confirm(`确认删除 ${selected.value.length} 份草稿？`); }
	catch { return; }
	const ids = selected.value.map(row => row.id);
	await run(async () => { for (const id of ids) await remove(id); }, '草稿已删除');
};
</script>
