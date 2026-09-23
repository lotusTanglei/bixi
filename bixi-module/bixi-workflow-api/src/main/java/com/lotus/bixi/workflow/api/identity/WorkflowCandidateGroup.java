package com.lotus.bixi.workflow.api.identity;

/** A workflow candidate group backed by an existing UPMS role. */
public record WorkflowCandidateGroup(long roleId) {

    private static final String ROLE_PREFIX = "role:";

    public WorkflowCandidateGroup {
        if (roleId <= 0) {
            throw new IllegalArgumentException("角色ID必须为正数");
        }
    }

    public static WorkflowCandidateGroup parse(String value) {
        if (value == null || !value.startsWith(ROLE_PREFIX)) {
            throw new IllegalArgumentException("候选组格式无效");
        }
        String digits = value.substring(ROLE_PREFIX.length());
        if (digits.isEmpty() || (digits.length() > 1 && digits.charAt(0) == '0')) {
            throw new IllegalArgumentException("候选组角色ID格式无效");
        }
        for (int index = 0; index < digits.length(); index++) {
            if (digits.charAt(index) < '0' || digits.charAt(index) > '9') {
                throw new IllegalArgumentException("候选组角色ID格式无效");
            }
        }
        try {
            return new WorkflowCandidateGroup(Long.parseLong(digits));
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException("候选组角色ID超出范围", overflow);
        }
    }

    public String value() {
        return ROLE_PREFIX + roleId;
    }

    @Override
    public String toString() {
        return value();
    }
}
