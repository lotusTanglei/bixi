package com.lotus.bixi.common.security.component;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.security.annotation.Inner;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BixiSecurityInnerAspectTest {

    @Test
    void singleRejectsForgedInternalHeader() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.FROM, SecurityConstants.FROM_IN);

        assertThatThrownBy(() -> aspect("single", request).around(point(), annotation("internal")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cloudDefaultStillAcceptsInternalHeaderAndRejectsAnonymousCall() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.FROM, SecurityConstants.FROM_IN);
        assertThatCode(() -> aspect(null, request).around(point(), annotation("internal")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> aspect(null, new MockHttpServletRequest())
                .around(point(), annotation("internal")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void explicitlyPublicInnerEndpointRemainsAvailableInBothModes() throws Exception {
        for (String mode : new String[] { "single", "cloud" }) {
            assertThatCode(() -> aspect(mode, new MockHttpServletRequest())
                    .around(point(), annotation("publicEndpoint")))
                    .doesNotThrowAnyException();
        }
    }

    private static BixiSecurityInnerAspect aspect(String mode, HttpServletRequest request) {
        var environment = new MockEnvironment();
        if (mode != null) environment.withProperty("bixi.deployment.mode", mode);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(environment);
            context.registerBean(HttpServletRequest.class, () -> request);
            context.registerBean(BixiSecurityInnerAspect.class);
            context.refresh();
            return context.getBean(BixiSecurityInnerAspect.class);
        }
    }

    private static Inner annotation(String method) throws NoSuchMethodException {
        return Endpoints.class.getDeclaredMethod(method).getAnnotation(Inner.class);
    }

    private static JoinPoint point() {
        var point = mock(JoinPoint.class);
        var signature = mock(Signature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("internal");
        return point;
    }

    static class Endpoints {
        @Inner void internal() { }
        @Inner(false) void publicEndpoint() { }
    }
}
