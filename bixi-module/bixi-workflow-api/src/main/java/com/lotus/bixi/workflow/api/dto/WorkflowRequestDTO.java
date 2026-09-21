package com.lotus.bixi.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.io.Serializable;

/** A caller must reuse the same ID and content when retrying one intention. */
@Data
public abstract class WorkflowRequestDTO implements Serializable {
    public static final String REQUEST_ID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    @NotBlank(message = "requestId不能为空")
    @Pattern(regexp = REQUEST_ID_PATTERN, message = "requestId必须是标准小写UUID")
    private String requestId;

    public static void requireRequestId(String requestId) {
        if (requestId == null || !requestId.matches(REQUEST_ID_PATTERN)) {
            throw new IllegalArgumentException("requestId必须是标准小写UUID");
        }
    }
}
