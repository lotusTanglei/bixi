package com.lotus.bixi.common.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.UUID;

@Data
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private Boolean enabled = false;

    private Boolean asyncExecutorActivate = true;

    private String databaseSchemaUpdate = "false";

    private String historyLevel = "full";

    /** A process-local default keeps two replicas from claiming the same job identity. */
    private String lockOwner = UUID.randomUUID().toString();

    private Duration asyncJobLockTime = Duration.ofMinutes(5);

    private Duration timerLockTime = Duration.ofMinutes(5);

    private Duration resetExpiredJobsInterval = Duration.ofMinutes(1);

    private Duration defaultAsyncJobAcquireWaitTime = Duration.ofSeconds(10);

    private Duration defaultTimerJobAcquireWaitTime = Duration.ofSeconds(10);

    private Integer maxAsyncJobsDuePerAcquisition = 1;

    private Integer maxTimerJobsPerAcquisition = 1;

    private Integer resetExpiredJobsPageSize = 100;

    private Boolean resetExpiredJobEnabled = true;

    private Boolean unlockOwnedJobs = true;

}
