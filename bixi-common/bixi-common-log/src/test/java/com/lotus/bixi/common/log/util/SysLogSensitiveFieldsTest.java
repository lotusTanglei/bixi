package com.lotus.bixi.common.log.util;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogEvent;
import com.lotus.bixi.common.log.event.SysLogEventSource;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SysLogSensitiveFieldsTest {

    private static final List<String> SENSITIVE_FIELDS = List.of(
            "password", "PASSWORD", "newpassword", "newPassword", "new_password", "oldPassword",
            "confirmPassword", "code", "CODE", "smsCode", "verifyCode", "client_secret", "clientSecret",
            "CLIENT_SECRET", "token", "TOKEN", "access_token", "accessToken", "refresh_token", "refreshToken",
            "authorization", "Authorization", "mobile", "MOBILE", "phone", "Phone", "idcard", "idCard");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(BixiLogProperties.class)
            .withBean(SpringContextHolder.class)
            .withBean(SpringUtil.class)
            .withPropertyValues("spring.application.name=audit-sensitive-fields-test");

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "private-note")
    void servletParametersAlwaysExcludeCredentialsAndAppendCustomFields(String customExclusion) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        sensitiveValues().forEach((name, value) -> request.addParameter(name, value.toString()));
        request.addParameter("operation", "login");
        request.addParameter("Private_Note", "custom-sensitive-value");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            BixiLogProperties properties = context.getBean(BixiLogProperties.class);
            configureExclusions(properties, customExclusion);
            SysLog saved = save(properties, SysLogUtils.getSysLog());

            assertThat(saved.getParams()).contains("operation=", "login").doesNotContain("credential-");
            assertThat(saved.getParams().contains("custom-sensitive-value"))
                    .isEqualTo(customExclusion == null || customExclusion.isEmpty());
            assertThat(request.getParameter("password")).isEqualTo("credential-password");
            assertThat(request.getParameter("code")).isEqualTo("credential-code");
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "private-note")
    void nestedJsonObjectsMapsAndListsUseTheSameCredentialPolicy(String customExclusion) throws Exception {
        BixiLogProperties properties = new BixiLogProperties();
        configureExclusions(properties, customExclusion);
        Map<String, Object> nested = sensitiveValues();
        nested.put("operation", "login");
        nested.put("Private_Note", "custom-sensitive-value");
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(new AuditBody("reset", "credential-body-password", "credential-body-newpassword",
                "credential-body-clientSecret", Map.of("details", nested), List.of(nested)));

        SysLog saved = save(properties, source);

        Map<String, Object> expectedNested = new LinkedHashMap<>();
        expectedNested.put("operation", "login");
        if (customExclusion == null || customExclusion.isEmpty()) {
            expectedNested.put("Private_Note", "custom-sensitive-value");
        }
        ObjectMapper reader = new ObjectMapper();
        assertThat(reader.readTree(saved.getParams())).isEqualTo(reader.valueToTree(Map.of(
                "operation", "reset", "metadata", Map.of("details", expectedNested),
                "attempts", List.of(expectedNested))));
    }

    @Test
    void defaultConfigurationProtectsCredentialsWithoutSpringInjection() throws Exception {
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(Map.of("operation", "login", "code", "credential-sms-code"));

        SysLog saved = save(new BixiLogProperties(), source);

        assertThat(new ObjectMapper().readTree(saved.getParams()))
                .isEqualTo(new ObjectMapper().valueToTree(Map.of("operation", "login")));
    }

    @Test
    void jsonTreesAreFilteredWithoutMutatingTheOriginalBody() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.valueToTree(Map.of("operation", "login", "attempts", List.of(
                Map.of("code", "credential-sms-code", "clientSecret", "credential-client-secret",
                        "status", "failed"))));
        BixiLogProperties properties = new BixiLogProperties();
        properties.setExcludeFields(List.of("password"));
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(body);

        SysLog saved = save(properties, source);

        assertThat(mapper.readTree(saved.getParams())).isEqualTo(mapper.valueToTree(Map.of(
                "operation", "login", "attempts", List.of(Map.of("status", "failed")))));
        assertThat(body.at("/attempts/0/code").asText()).isEqualTo("credential-sms-code");
    }

    @Test
    void rawJsonNodesAreFilteredWithoutChangingTheOriginalBodyOrPlainText() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var body = mapper.createObjectNode();
        body.put("operation", "login");
        body.put("note", "{\"password\":\"ordinary-documentation\"}");
        body.putRawValue("details", new RawValue("{\"password\":\"credential-password\",\"status\":\"failed\"}"));
        body.putArray("attempts").addRawValue(new RawValue(
                "{\"code\":\"credential-sms-code\",\"clientSecret\":\"credential-client-secret\",\"attempt\":1}"));
        String originalBody = mapper.writeValueAsString(body);
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(body);

        SysLog saved = save(new BixiLogProperties(), source);

        assertThat(mapper.readTree(saved.getParams())).isEqualTo(mapper.valueToTree(Map.of(
                "operation", "login", "note", "{\"password\":\"ordinary-documentation\"}",
                "details", Map.of("status", "failed"), "attempts", List.of(Map.of("attempt", 1)))));
        assertThat(mapper.writeValueAsString(body)).isEqualTo(originalBody);
    }

    @Test
    void annotatedRawJsonArraysUseTheSameCredentialPolicy() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String rawDetails = "[{\"access_token\":\"credential-token\",\"status\":\"failed\"}]";
        RawAuditBody body = new RawAuditBody("login", rawDetails);
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(body);

        SysLog saved = save(new BixiLogProperties(), source);

        assertThat(mapper.readTree(saved.getParams())).isEqualTo(mapper.valueToTree(Map.of(
                "operation", "login", "details", List.of(Map.of("status", "failed")))));
        assertThat(body.details()).isEqualTo(rawDetails);
    }

    @Test
    void oneListenerCannotChangeAnotherListenersExclusions() throws Exception {
        BixiLogProperties firstProperties = new BixiLogProperties();
        firstProperties.setExcludeFields(List.of("firstSecret"));
        BixiLogProperties secondProperties = new BixiLogProperties();
        secondProperties.setExcludeFields(List.of("secondSecret"));
        OperationLogService firstPersistence = mock(OperationLogService.class);
        OperationLogService secondPersistence = mock(OperationLogService.class);
        SysLogListener firstListener = listener(firstPersistence, firstProperties);
        SysLogListener secondListener = listener(secondPersistence, secondProperties);
        SysLogEventSource source = new SysLogEventSource();
        source.setBody(Map.of("firstSecret", "first-value", "secondSecret", "second-value",
                "operation", "update", "password", "credential-password"));

        firstListener.saveSysLog(new SysLogEvent(source));
        secondListener.saveSysLog(new SysLogEvent(source));

        ObjectMapper reader = new ObjectMapper();
        assertThat(reader.readTree(savedLog(firstPersistence).getParams()))
                .isEqualTo(reader.valueToTree(Map.of("operation", "update", "secondSecret", "second-value")));
        assertThat(reader.readTree(savedLog(secondPersistence).getParams()))
                .isEqualTo(reader.valueToTree(Map.of("operation", "update", "firstSecret", "first-value")));
    }

    private static Map<String, Object> sensitiveValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        SENSITIVE_FIELDS.forEach(name -> values.put(name, "credential-" + name));
        return values;
    }

    private static void configureExclusions(BixiLogProperties properties, String customExclusion) {
        properties.setExcludeFields(customExclusion == null ? null
                : customExclusion.isEmpty() ? List.of() : List.of(customExclusion));
        properties.setMaxLength(10000);
    }

    private static SysLog save(BixiLogProperties properties, SysLogEventSource source) throws Exception {
        OperationLogService persistence = mock(OperationLogService.class);
        listener(persistence, properties).saveSysLog(new SysLogEvent(source));
        return savedLog(persistence);
    }

    private static SysLogListener listener(OperationLogService persistence, BixiLogProperties properties)
            throws Exception {
        SysLogListener listener = new SysLogListener(persistence, properties);
        listener.afterPropertiesSet();
        return listener;
    }

    private static SysLog savedLog(OperationLogService persistence) {
        ArgumentCaptor<SysLog> saved = ArgumentCaptor.forClass(SysLog.class);
        verify(persistence).saveLog(saved.capture());
        return saved.getValue();
    }

    public record AuditBody(String operation, String password, String newpassword, String clientSecret,
            Map<String, Object> metadata, List<Map<String, Object>> attempts) {
    }

    public record RawAuditBody(String operation, @JsonRawValue String details) {
    }
}
