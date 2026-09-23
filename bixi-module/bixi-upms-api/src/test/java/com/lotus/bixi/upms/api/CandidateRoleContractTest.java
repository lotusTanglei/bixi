package com.lotus.bixi.upms.api;

import com.lotus.bixi.upms.api.dto.CandidateRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRoleContractTest {
    @Test
    void contractExposesOnlyServerOwnedRoleState() {
        assertThat(Arrays.stream(CandidateRole.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("roleId", "active", "tenantId");
    }
}
