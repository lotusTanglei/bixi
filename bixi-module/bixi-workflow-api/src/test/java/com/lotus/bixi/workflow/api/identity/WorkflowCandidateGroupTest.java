package com.lotus.bixi.workflow.api.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowCandidateGroupTest {

    @Test
    void parsesCanonicalRoleGroup() {
        assertThat(WorkflowCandidateGroup.parse("role:42").roleId()).isEqualTo(42L);
        assertThat(WorkflowCandidateGroup.parse("role:9223372036854775807").roleId())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsAnythingOtherThanCanonicalPositiveDecimalRoleId() {
        List<String> invalid = List.of(
                "role:0",
                "role:-1",
                "role:01",
                " role:1",
                "role:1 ",
                "role: 1",
                "user:1",
                "role:*",
                "role:${id}",
                "role:1:2",
                "role:9223372036854775808");

        assertThatThrownBy(() -> WorkflowCandidateGroup.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        for (String value : invalid) {
            assertThatThrownBy(() -> WorkflowCandidateGroup.parse(value))
                    .as("reject invalid candidate group: %s", value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
