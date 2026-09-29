package com.lotus.bixi.workflow.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Common filters for owner-scoped recovery inventories. */
@Data
public class WorkflowRecoveryQueryDTO {
    @Pattern(regexp = "[a-z][a-z0-9_-]{0,31}")
    private String owner;
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String type;
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}")
    private String status;
    @Pattern(regexp = "OUTBOX|INBOX")
    private String direction;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
    @Min(1)
    private Long businessId;
    @Min(1)
    private long current = 1;
    @Min(1) @Max(200)
    private long size = 20;
}
