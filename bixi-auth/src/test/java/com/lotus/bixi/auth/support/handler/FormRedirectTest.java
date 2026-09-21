package com.lotus.bixi.auth.support.handler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.context.request.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class FormRedirectTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "/admin", "/api/auth", "/api/admin"})
    void loginFailureRetainsExternalPrefixAndEncodesError(String prefix) throws Exception {
        var request = new MockHttpServletRequest("POST", prefix + "/token/form");
        var response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            new FormAuthenticationFailureHandler().onAuthenticationFailure(request, response,
                    new BadCredentialsException("bad & password=错误<script>"));
            var location = URI.create("https://example.test" + prefix + "/token/form").resolve(response.getRedirectedUrl());
            assertThat(location.getPath()).isEqualTo(prefix + "/token/login");
            assertThat(location.getRawQuery()).doesNotContain("&");
            assertThat(URLDecoder.decode(location.getRawQuery(), StandardCharsets.UTF_8))
                    .isEqualTo("error=bad & password=错误<script>");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/admin", "/api/auth", "/api/admin"})
    void logoutDefaultRetainsExternalPrefix(String prefix) throws Exception {
        var request = new MockHttpServletRequest("POST", prefix + "/logout");
        var response = new MockHttpServletResponse();
        new SsoLogoutSuccessHandler().onLogoutSuccess(request, response, null);
        var location = URI.create("https://example.test" + prefix + "/logout").resolve(response.getRedirectedUrl());
        assertThat(location.getPath()).isEqualTo(prefix + "/token/login");
    }
}
