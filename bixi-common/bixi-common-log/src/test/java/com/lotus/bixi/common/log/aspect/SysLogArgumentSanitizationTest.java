package com.lotus.bixi.common.log.aspect;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogEvent;
import com.lotus.bixi.common.log.event.SysLogEventSource;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.upms.api.service.OperationLogService;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysLogArgumentSanitizationTest {

    @AfterEach
    void clearSpringContext() {
        SpringContextHolder.clearHolder();
    }

    @Test
    void multipartAndTransportArgumentsProduceAJsonBodyThatCanBePersisted() throws Exception {
        OperationLogService persistence = mock(OperationLogService.class);
        List<SysLogEvent> events = new ArrayList<>();
        long fileSize;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(AuditConfiguration.class);
            context.registerBean(OperationLogService.class, () -> persistence);
            context.addApplicationListener(event -> {
                if (event instanceof SysLogEvent sysLogEvent) {
                    events.add(sysLogEvent);
                }
            });
            context.refresh();

            MultipartFile file = mock(MultipartFile.class);
            when(file.getName()).thenReturn("file");
            when(file.getOriginalFilename()).thenReturn(
                    "C:\\private\\uploads/" + "x".repeat(150) + ".xlsx");
            when(file.getContentType()).thenReturn(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            when(file.getSize()).thenReturn(23L);
            fileSize = 23L;
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inventory/import");
            request.addHeader("Authorization", "Bearer credential-token");
            List<MultipartFile> attachments = new ArrayList<>();
            for (int index = 0; index < 30; index++) {
                attachments.add(new MockMultipartFile("attachments", "part-" + index + ".txt",
                        "text/plain", ("part-secret-" + index).getBytes(StandardCharsets.UTF_8)));
            }
            Map<Object, Object> metadata = new LinkedHashMap<>();
            metadata.put(Path.of("/private/audit/path-secret"), "path-key-value");
            metadata.put(new File("/private/audit/file-secret"), "file-key-value");
            metadata.put("label", "retained");

            context.getBean(AuditedImporter.class).importData(file, request,
                    new ImportCommand("inventory", "credential-token"), attachments,
                    attachments.toArray(MultipartFile[]::new), metadata,
                    "raw-binary-secret".getBytes(StandardCharsets.UTF_8),
                    new ByteArrayInputStream("stream-secret".getBytes(StandardCharsets.UTF_8)));

            verify(file, never()).getBytes();
            verify(file, never()).getInputStream();
        }

        assertThat(events).hasSize(1);
        BixiLogProperties properties = new BixiLogProperties();
        properties.setMaxLength(10_000);
        SysLogListener listener = new SysLogListener(persistence, properties);
        listener.afterPropertiesSet();
        listener.saveSysLog(events.get(0));

        ArgumentCaptor<com.lotus.bixi.upms.api.entity.SysLog> savedLog =
                ArgumentCaptor.forClass(com.lotus.bixi.upms.api.entity.SysLog.class);
        verify(persistence).saveLog(savedLog.capture());
        JsonNode body = new ObjectMapper().readTree(savedLog.getValue().getParams());

        assertThat(body.toString()).contains("inventory", "file", "application/vnd.openxmlformats")
                .doesNotContain("credential-token", "workbook-secret-content", "part-secret-",
                        "raw-binary-secret", "stream-secret", "Authorization", "private", "uploads");
        JsonNode fileMetadata = body.get(0);
        assertThat(fileMetadata.get("fieldName").asText()).isEqualTo("file");
        assertThat(fileMetadata.get("size").asLong()).isEqualTo(fileSize);
        assertThat(fileMetadata.get("originalFilename").asText()).endsWith(".xlsx");
        assertThat(fileMetadata.get("originalFilename").asText().codePointCount(
                0, fileMetadata.get("originalFilename").asText().length())).isLessThanOrEqualTo(128);
        assertThat(body.get(1).get("operation").asText()).isEqualTo("inventory");
        assertThat(body.get(2)).hasSizeLessThanOrEqualTo(20);
        assertThat(body.get(3)).hasSizeLessThanOrEqualTo(20);
        assertThat(body.get(4)).isEqualTo(new ObjectMapper().valueToTree(Map.of("label", "retained")));
        assertThat(body).hasSize(5);
    }

    @Test
    void cyclicAndDeepContainersAreBoundedWithoutLosingSafeValues() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("label", "retained");
        cyclic.put("self", cyclic);

        Object nested = List.of("too-deep");
        for (int depth = 0; depth < 10; depth++) {
            nested = List.of(nested);
        }

        Object[] sanitized = SysLogArgumentSanitizer.sanitize(new Object[] {cyclic, nested});

        assertThat(sanitized[0]).isEqualTo(Map.of("label", "retained"));
        Object current = sanitized[1];
        int depth = 0;
        while (current instanceof List<?> list && !list.isEmpty()) {
            depth++;
            current = list.get(0);
        }
        assertThat(depth).isLessThanOrEqualTo(8);
        assertThat(current).isEqualTo(List.of());
    }

    @Test
    void nestedContainersHonorTheGlobalItemBudget() {
        List<List<Integer>> matrix = new ArrayList<>();
        for (int row = 0; row < 20; row++) {
            List<Integer> values = new ArrayList<>();
            for (int column = 0; column < 20; column++) {
                values.add(row * 20 + column);
            }
            matrix.add(values);
        }

        Object[] sanitized = SysLogArgumentSanitizer.sanitize(new Object[] {matrix});

        assertThat(countContainerItems(sanitized[0])).isEqualTo(100);
    }

    @Test
    void filenameKeepsAnExtensionThatExactlyFitsTheCodePointLimit() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        String extension = "." + "x".repeat(127);
        when(file.getOriginalFilename()).thenReturn("stem" + extension);

        Object[] sanitized = SysLogArgumentSanitizer.sanitize(new Object[] {file});
        JsonNode metadata = new ObjectMapper().valueToTree(sanitized[0]);

        assertThat(metadata.get("originalFilename").asText()).isEqualTo(extension);
        assertThat(metadata.get("originalFilename").asText().codePointCount(0, extension.length())).isEqualTo(128);
        verify(file, never()).getBytes();
        verify(file, never()).getInputStream();
    }

    private static int countContainerItems(Object value) {
        if (value instanceof List<?> list) {
            return list.size() + list.stream().mapToInt(SysLogArgumentSanitizationTest::countContainerItems).sum();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size() + map.values().stream()
                    .mapToInt(SysLogArgumentSanitizationTest::countContainerItems).sum();
        }
        return 0;
    }

    record ImportCommand(String operation, String token) {
    }

    static class AuditedImporter {
        @SysLog("Import inventory")
        public void importData(MultipartFile file, ServletRequest request, ImportCommand command,
                List<MultipartFile> attachments, MultipartFile[] attachmentArray,
                Map<Object, Object> metadata, byte[] bytes, InputStream stream) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AuditConfiguration {
        @Bean
        AuditedImporter auditedImporter() {
            return new AuditedImporter();
        }

        @Bean
        SysLogAspect sysLogAspect() {
            return new SysLogAspect();
        }

        @Bean
        BixiLogProperties logProperties() {
            return new BixiLogProperties();
        }

        @Bean
        SpringContextHolder springContextHolder() {
            return new SpringContextHolder();
        }

        @Bean
        static SpringUtil springUtil() {
            return new SpringUtil();
        }
    }
}
