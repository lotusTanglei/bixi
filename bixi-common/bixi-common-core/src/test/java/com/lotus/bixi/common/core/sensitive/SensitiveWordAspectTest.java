package com.lotus.bixi.common.core.sensitive;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveWordAspectTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void checksStringFieldsInsideRequestObjects() throws Throwable {
        TenantContextHolder.set(7L);
        SensitiveWordEngine engine = new SensitiveWordEngine();
        engine.reload(7L, List.of("blocked"));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("Payload");
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{new Payload("blocked content")});

        assertThatThrownBy(() -> new SensitiveWordAspect(engine).check(point))
                .isInstanceOf(com.lotus.bixi.common.core.exception.SensitiveWordException.class)
                .hasMessage("sensitive_word_detected");
    }

    private record Payload(String text) {
    }
}
