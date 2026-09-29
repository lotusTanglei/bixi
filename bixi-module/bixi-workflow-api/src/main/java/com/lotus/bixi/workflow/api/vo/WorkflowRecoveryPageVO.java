package com.lotus.bixi.workflow.api.vo;

import java.util.List;

/** Stable page envelope shared by Workflow and UPMS recovery endpoints. */
public record WorkflowRecoveryPageVO<T>(List<T> records, long total, long current, long size, long pages) {
    public WorkflowRecoveryPageVO {
        records = List.copyOf(records);
    }

    public static <T> WorkflowRecoveryPageVO<T> of(List<T> records, long total, long current, long size) {
        return new WorkflowRecoveryPageVO<>(records, total, current, size,
                total == 0 ? 0 : (total + size - 1) / size);
    }
}
