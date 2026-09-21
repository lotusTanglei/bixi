package com.lotus.bixi.workflow.api.event;

import com.lotus.bixi.workflow.api.json.WorkflowJsonVariables;

import java.time.Instant;

final class WorkflowEventValidation {
    private WorkflowEventValidation() { }

    static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + "无效");
        }
        return WorkflowJsonVariables.requireUnicodeScalars(value);
    }

    static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash无效");
        }
        return value;
    }

    static String uuid(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException(field + "无效");
        }
        return value;
    }

    static Instant instant(Instant value, String field) {
        if (value == null) throw new IllegalArgumentException(field + "不能为空");
        return value;
    }
}
