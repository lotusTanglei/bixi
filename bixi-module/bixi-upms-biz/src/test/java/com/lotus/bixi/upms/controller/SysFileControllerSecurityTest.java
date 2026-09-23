package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.security.annotation.Inner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SysFileControllerSecurityTest {

    @Test
    void privateDownloadIsNotMarkedAsAnInnerUnauthenticatedEndpoint() throws Exception {
        Method method = SysFileController.class.getMethod("file", String.class, String.class,
                jakarta.servlet.http.HttpServletResponse.class);

        Inner inner = method.getAnnotation(Inner.class);

        assertThat(inner).isNull();
    }
}
