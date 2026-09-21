package com.lotus.bixi.workflow.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Terminal workflow result. Identity must match the business domain's confirmed binding. */
@Data
public class WorkflowResultDTO implements Serializable {
    @NotBlank @Size(max = 128)
    private String eventId;
    @Min(1) @Max(1)
    private int schemaVersion = 1;
    @NotBlank @Size(max = 64)
    private String processInstanceId;
    @NotBlank @Size(max = 128)
    private String processKey;
    @NotBlank @Size(max = 255)
    private String businessKey;
    @NotBlank @Size(max = 128)
    private String businessTable;
    @NotNull @Positive
    private Long businessId;
    @Positive
    private int round;
    @NotNull @Positive
    private Long startUserId;
    @NotBlank @Pattern(regexp = "completed|rejected|terminated")
    private String status;
    @NotNull
    private LocalDateTime endTime;
}
