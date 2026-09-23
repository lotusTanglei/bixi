import request from '/@/utils/request';

export interface RecoveryQuery {
	status?: string;
	limit?: number;
}

export type RecoveryOwner = 'workflow' | 'upms';

const recoveryBase = (owner: RecoveryOwner) => `/admin/${owner}/recovery`;

export interface RecoveryAction {
	direction: string;
	eventId: string;
	changed: boolean;
}

export interface RecoveryReconciliation {
	owner: RecoveryOwner;
	eventId?: string;
	eventType?: string;
	businessTable?: string;
	businessId?: number;
	businessKey?: string;
	processInstanceId?: string;
	requestId?: string;
	commandStatus?: string;
	operationId?: string;
	businessTaskStatus?: string;
	compensationId?: string;
	quarantineEvidenceId?: string;
	round?: number;
	durableStatus?: string;
	businessStatus?: string;
	classification: string;
	detail: string;
}

export interface RecoveryReplay {
	evidenceId: string;
	eventId?: string;
	result: string;
	accepted: boolean;
}

export interface WorkflowRuntimeDiagnostics {
	lockOwner: string;
	asyncExecutorActivate: boolean;
	asyncExecutorAutoActivate: boolean;
	asyncExecutorActive: boolean;
	asyncJobLockTimeMillis: number;
	timerJobLockTimeMillis: number;
	resetExpiredJobsIntervalMillis: number;
	defaultAsyncJobAcquireWaitTimeMillis: number;
	defaultTimerJobAcquireWaitTimeMillis: number;
	maxAsyncJobsDuePerAcquisition: number;
	maxTimerJobsPerAcquisition: number;
	resetExpiredJobsPageSize: number;
	resetExpiredJobsEnabled: boolean;
	unlockOwnedJobs: boolean;
	outboxPendingCount: number;
	outboxFailedCount: number;
	oldestOutboxWaitingAt?: string;
	inboxPendingCount: number;
	inboxFailedCount: number;
	oldestInboxWaitingAt?: string;
	recentFailureSource?: string;
	recentFailureEventId?: string;
	recentFailureSummary?: string;
	recentFailureAt?: string;
	flowableExecutableJobCount: number;
	flowableTimerJobCount: number;
	flowableFailedJobCount: number;
}

export const listOutbox = (params?: RecoveryQuery, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/outbox`, method: 'get', params,
});
export const listInbox = (params?: RecoveryQuery, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/inbox`, method: 'get', params,
});
export const listQuarantine = (params?: { limit?: number }, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/quarantine`, method: 'get', params,
});
export const listReconciliation = (params?: { limit?: number }, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/reconcile`, method: 'get', params,
});
export const getDiagnostics = () => request({
	url: `${recoveryBase('workflow')}/diagnostics`, method: 'get',
});
export const retryOutbox = (eventId: string, reason: string, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/outbox/${encodeURIComponent(eventId)}/retry`, method: 'post', params: { reason },
});
export const retryInbox = (eventId: string, reason: string, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/inbox/${encodeURIComponent(eventId)}/retry`, method: 'post', params: { reason },
});
export const replayQuarantine = (evidenceId: string, reason: string, owner: RecoveryOwner = 'workflow') => request({
	url: `${recoveryBase(owner)}/quarantine/${encodeURIComponent(evidenceId)}/replay`, method: 'post', params: { reason },
});
