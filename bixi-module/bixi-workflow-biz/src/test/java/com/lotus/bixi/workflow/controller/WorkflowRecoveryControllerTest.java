package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.vo.WorkflowRuntimeDiagnosticsVO;
import com.lotus.bixi.workflow.service.impl.WorkflowRecoveryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRecoveryControllerTest {

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
