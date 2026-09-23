package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Redacted runtime evidence for workflow recovery; message payloads are never exposed. */
public record WorkflowRuntimeDiagnosticsVO(
        String lockOwner,
        boolean asyncExecutorActivate,
        boolean asyncExecutorAutoActivate,
        boolean asyncExecutorActive,
        int asyncJobLockTimeMillis,
        int timerJobLockTimeMillis,
        int resetExpiredJobsIntervalMillis,
        int defaultAsyncJobAcquireWaitTimeMillis,
        int defaultTimerJobAcquireWaitTimeMillis,
        int maxAsyncJobsDuePerAcquisition,
        int maxTimerJobsPerAcquisition,
        int resetExpiredJobsPageSize,
        boolean resetExpiredJobsEnabled,
        boolean unlockOwnedJobs,
        long outboxPendingCount,
        long outboxFailedCount,
        Instant oldestOutboxWaitingAt,
        long inboxPendingCount,
        long inboxFailedCount,
        Instant oldestInboxWaitingAt,
        String recentFailureSource,
        String recentFailureEventId,
        String recentFailureSummary,
        Instant recentFailureAt,
        long flowableExecutableJobCount,
        long flowableTimerJobCount,
        long flowableFailedJobCount) {
}
