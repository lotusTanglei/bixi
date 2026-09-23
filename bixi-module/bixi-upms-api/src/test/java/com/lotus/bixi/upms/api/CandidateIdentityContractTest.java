package com.lotus.bixi.upms.api;

import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateIdentityContractTest {

    @Test
    void contractExposesOnlyServerOwnedIdentityState() {
        assertThat(Arrays.stream(CandidateIdentity.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("userId", "enabled", "locked", "tenantId");
    }

}
