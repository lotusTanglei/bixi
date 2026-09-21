package com.lotus.bixi.common.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

    private Boolean enabled = false;

    private Boolean asyncExecutorActivate = true;

    private String databaseSchemaUpdate = "false";

    private String historyLevel = "full";

}
