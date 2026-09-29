package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.vo.WorkflowRuntimeDiagnosticsVO;
import com.lotus.bixi.workflow.service.impl.WorkflowRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRecoveryControllerTest {

    @Test
    void exposesUnifiedEventRetryRoute() {
        assertThat(Arrays.stream(WorkflowRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .toList())
                .contains("/events/{eventId}/retry");
    }

    @Test
    void unifiedEventRetryAcceptsAValidatedJsonBody() {
        var retry = Arrays.stream(WorkflowRecoveryController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))
                .filter(method -> Arrays.asList(method.getAnnotation(PostMapping.class).value())
                        .contains("/events/{eventId}/retry"))
                .findFirst()
                .orElseThrow();

        assertThat(Arrays.stream(retry.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
                .filter(parameter -> parameter.isAnnotationPresent(jakarta.validation.Valid.class)))
                .hasSize(1);
    }

    @Test
    void exposesPagedRecoveryAndBusinessTaskReconcileRoutes() {
        assertThat(Arrays.stream(WorkflowRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .contains("/events/page", "/events/{eventId}", "/commands/page", "/commands/{commandId}",
                        "/business-tasks/page");
        assertThat(Arrays.stream(WorkflowRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .contains("/business-tasks/{operationId}/reconcile");
    }

    @Test
    void diagnosticsDelegatesToRecoveryService() {
        WorkflowRecoveryService service = mock(WorkflowRecoveryService.class);
        WorkflowRuntimeDiagnosticsVO diagnostics = new WorkflowRuntimeDiagnosticsVO(
                "workflow-test-a", true, true, true,
                7_000, 9_000, 11_000, 13_000, 15_000,
                3, 4, 25, true, true,
                3, 1, null, 2, 4, null,
                "INBOX", "00000000-0000-0000-0000-000000000099", "java.lang.IllegalStateException", null,
                6, 2, 1);
        when(service.diagnostics()).thenReturn(diagnostics);

        R<WorkflowRuntimeDiagnosticsVO> response = new WorkflowRecoveryController(service).diagnostics();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isSameAs(diagnostics);
        verify(service).diagnostics();
    }
}
