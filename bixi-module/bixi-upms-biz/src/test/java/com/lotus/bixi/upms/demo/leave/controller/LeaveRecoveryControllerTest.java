package com.lotus.bixi.upms.demo.leave.controller;

import com.lotus.bixi.common.security.annotation.HasPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LeaveRecoveryControllerTest {

    @Test
    void exposesLeaveOwnedRecoveryContractWithDedicatedPermissions() {
        assertThat(LeaveRecoveryController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/demo/leave/recovery");
        assertThat(Arrays.stream(LeaveRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .contains("/events/page", "/events/{eventId}", "/commands/page",
                        "/commands/{commandId}", "/business-tasks/page", "/business-tasks/{operationId}");
        assertThat(Arrays.stream(LeaveRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .contains("/events/{eventId}/retry", "/commands/{commandId}/retry",
                        "/business-tasks/{operationId}/reconcile");
        assertThat(Arrays.stream(LeaveRecoveryController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(HasPermission.class).value())
                .flatMap(Arrays::stream))
                .allMatch(permission -> permission.equals("demo_leave_recovery_view")
                        || permission.equals("demo_leave_recovery_edit"));
    }
}
