<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-radio-group v-model="owner" class="mb16" @change="switchOwner">
				<el-radio-button label="workflow">Workflow owner</el-radio-button>
				<el-radio-button label="upms">UPMS owner</el-radio-button>
			</el-radio-group>
			<el-tabs v-model="active" @tab-change="load">
				<el-tab-pane label="运行诊断" name="diagnostics" :disabled="owner !== 'workflow'" />
				<el-tab-pane label="Outbox" name="outbox" />
				<el-tab-pane label="Inbox" name="inbox" />
				<el-tab-pane label="隔离消息" name="quarantine" />
				<el-tab-pane label="业务对账" name="reconcile" />
			</el-tabs>
			<el-form v-if="active === 'outbox' || active === 'inbox'" inline @submit.prevent="load">
				<el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部状态" style="width: 160px">
					<el-option v-for="item in statusOptions" :key="item" :label="item" :value="item" />
				</el-select></el-form-item>
				<el-form-item><el-button icon="Search" type="primary" @click="load">查询</el-button></el-form-item>
			</el-form>
			<el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
			<el-descriptions v-if="active === 'diagnostics' && diagnostics" v-loading="loading" :column="3" border class="diagnostics mb16">
				<el-descriptions-item label="锁 owner">{{ diagnostics.lockOwner }}</el-descriptions-item>
				<el-descriptions-item label="执行器状态">{{ executorState }}</el-descriptions-item>
				<el-descriptions-item label="领取配置">{{ diagnostics.maxAsyncJobsDuePerAcquisition }} / {{ diagnostics.maxTimerJobsPerAcquisition }}</el-descriptions-item>
				<el-descriptions-item label="异步锁时长">{{ duration(diagnostics.asyncJobLockTimeMillis) }}</el-descriptions-item>
				<el-descriptions-item label="定时锁时长">{{ duration(diagnostics.timerJobLockTimeMillis) }}</el-descriptions-item>
				<el-descriptions-item label="过期扫描">{{ duration(diagnostics.resetExpiredJobsIntervalMillis) }}</el-descriptions-item>
				<el-descriptions-item label="Flowable 可执行">{{ diagnostics.flowableExecutableJobCount }}</el-descriptions-item>
				<el-descriptions-item label="Flowable 定时">{{ diagnostics.flowableTimerJobCount }}</el-descriptions-item>
				<el-descriptions-item label="Flowable 失败">{{ diagnostics.flowableFailedJobCount }}</el-descriptions-item>
				<el-descriptions-item label="Outbox 待处理">{{ diagnostics.outboxPendingCount }}</el-descriptions-item>
				<el-descriptions-item label="Outbox 失败">{{ diagnostics.outboxFailedCount }}</el-descriptions-item>
				<el-descriptions-item label="Outbox 最早等待">{{ diagnostics.oldestOutboxWaitingAt || '—' }}</el-descriptions-item>
				<el-descriptions-item label="Inbox 待处理">{{ diagnostics.inboxPendingCount }}</el-descriptions-item>
				<el-descriptions-item label="Inbox 失败">{{ diagnostics.inboxFailedCount }}</el-descriptions-item>
				<el-descriptions-item label="Inbox 最早等待">{{ diagnostics.oldestInboxWaitingAt || '—' }}</el-descriptions-item>
				<el-descriptions-item label="最近失败来源">{{ diagnostics.recentFailureSource || '—' }}</el-descriptions-item>
				<el-descriptions-item label="最近失败事件">{{ diagnostics.recentFailureEventId || '—' }}</el-descriptions-item>
				<el-descriptions-item label="最近失败摘要" :span="3">{{ diagnostics.recentFailureSummary || '—' }}</el-descriptions-item>
			</el-descriptions>
			<el-empty v-else-if="active === 'diagnostics' && !loading && !error" description="暂无诊断数据" />
			<el-empty v-else-if="!loading && !error && rows.length === 0" description="暂无记录" />
			<el-table v-else-if="active !== 'diagnostics'" v-loading="loading" :data="rows" border empty-text="暂无记录">
				<el-table-column label="事件 ID" prop="eventId" min-width="260" show-overflow-tooltip />
				<el-table-column label="来源" prop="sourceOwner" width="100" />
				<el-table-column label="目标" prop="targetOwner" width="100" />
				<el-table-column label="类型/业务" min-width="180" show-overflow-tooltip>
					<template #default="scope">{{ scope.row.type || scope.row.eventType || scope.row.businessKey || scope.row.reason }}</template>
				</el-table-column>
				<el-table-column label="状态" prop="status" width="110" />
				<el-table-column v-if="active === 'reconcile'" label="对账结果" prop="classification" width="150" />
				<el-table-column v-if="active === 'reconcile'" label="业务状态" prop="businessStatus" width="120" />
				<el-table-column v-if="active === 'reconcile'" label="请求 ID" prop="requestId" min-width="220" show-overflow-tooltip />
				<el-table-column v-if="active === 'reconcile'" label="命令状态" prop="commandStatus" width="110" show-overflow-tooltip />
				<el-table-column v-if="active === 'reconcile'" label="操作 ID" prop="operationId" min-width="220" show-overflow-tooltip />
				<el-table-column v-if="active === 'reconcile'" label="业务任务状态" prop="businessTaskStatus" width="130" show-overflow-tooltip />
				<el-table-column v-if="active === 'reconcile'" label="补偿 ID" prop="compensationId" min-width="220" show-overflow-tooltip />
				<el-table-column v-if="active === 'reconcile'" label="隔离证据 ID" prop="quarantineEvidenceId" min-width="220" show-overflow-tooltip />
				<el-table-column label="尝试次数" prop="attempts" width="100" />
				<el-table-column label="时间/说明" min-width="220"><template #default="scope">{{ scope.row.createdAt || scope.row.receivedAt || scope.row.quarantinedAt || scope.row.detail }}</template></el-table-column>
				<el-table-column v-if="active !== 'reconcile'" label="操作" width="150" fixed="right">
					<template #default="scope">
						<el-button v-if="scope.row.status === 'FAILED'" v-auth="'workflow_recovery_edit'" text type="primary" icon="Refresh" @click="retry(scope.row)">重试</el-button>
						<el-button v-if="active === 'quarantine'" v-auth="'workflow_recovery_edit'" text type="warning" icon="Promotion" @click="replay(scope.row)">重放</el-button>
					</template>
				</el-table-column>
			</el-table>
		</div>
	</div>
</template>

<script lang="ts" name="workflowRecovery" setup>
import { getDiagnostics, listInbox, listOutbox, listQuarantine, listReconciliation, replayQuarantine, retryInbox, retryOutbox, type RecoveryOwner } from '/@/api/workflow/recovery';
import { useMessage } from '/@/hooks/message';
import { ElMessageBox } from 'element-plus';

const active = ref('outbox');
const owner = ref<RecoveryOwner>('workflow');
const status = ref('');
const loading = ref(false);
const error = ref('');
const rows = ref<any[]>([]);
const diagnostics = ref<any>(null);
const statusOptions = ['PENDING', 'IN_FLIGHT', 'DELIVERED', 'RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'];

const executorState = computed(() => diagnostics.value
	? `${diagnostics.value.asyncExecutorActive ? 'active' : 'inactive'} / ${diagnostics.value.asyncExecutorAutoActivate ? 'auto' : 'manual'}`
	: '—');

const duration = (millis: number) => `${Math.round(millis / 1000)}s`;

const load = async () => {
	loading.value = true; error.value = '';
	try {
		const response: any = active.value === 'diagnostics'
			? await getDiagnostics()
			: active.value === 'outbox'
			? await listOutbox({ status: status.value || undefined, limit: 100 }, owner.value)
			: active.value === 'inbox'
				? await listInbox({ status: status.value || undefined, limit: 100 }, owner.value)
				: active.value === 'quarantine'
					? await listQuarantine({ limit: 100 }, owner.value)
					: await listReconciliation({ limit: 100 }, owner.value);
		if (active.value === 'diagnostics') {
			diagnostics.value = response?.data || null; rows.value = [];
		} else {
			diagnostics.value = null; rows.value = response?.data || [];
		}
	} catch (err: any) {
		error.value = err?.msg || '加载恢复记录失败'; rows.value = []; diagnostics.value = null;
	} finally { loading.value = false; }
};

const switchOwner = () => {
	if (owner.value === 'upms' && active.value === 'diagnostics') active.value = 'outbox';
	load();
};

const retry = async (row: any) => {
	let reason = '';
	try {
		const response = await ElMessageBox.prompt('请输入恢复原因', '人工重试', { inputValidator: (value: string) => value?.trim() ? true : '恢复原因不能为空' });
		reason = response.value.trim();
	} catch { return; }
	try {
		await (active.value === 'outbox' ? retryOutbox(row.eventId, reason, owner.value) : retryInbox(row.eventId, reason, owner.value));
		useMessage().success('已提交重试'); load();
	} catch (err: any) { error.value = err?.msg || '重试失败'; }
};

const replay = async (row: any) => {
	let reason = '';
	try {
		const response = await ElMessageBox.prompt('请输入重放原因', '重放隔离消息', { inputValidator: (value: string) => value?.trim() ? true : '重放原因不能为空' });
		reason = response.value.trim();
	} catch { return; }
	try {
		await replayQuarantine(row.evidenceId, reason, owner.value);
		useMessage().success('已提交重放'); load();
	} catch (err: any) { error.value = err?.msg || '重放失败'; }
};

onMounted(load);
</script>
