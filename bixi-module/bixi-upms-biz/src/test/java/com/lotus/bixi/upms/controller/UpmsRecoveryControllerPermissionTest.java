package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.service.UpmsRecoveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(UpmsRecoveryControllerPermissionTest.Config.class)
@TestPropertySource(properties = {"workflow.enabled=true", "bixi.reliable.enabled=true"})
class UpmsRecoveryControllerPermissionTest {

    @org.springframework.beans.factory.annotation.Autowired
    UpmsRecoveryController controller;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recoveryReadRequiresTheExistingWorkflowRecoveryPermission() {
        authenticate("demo_task_view");
        assertThatThrownBy(() -> controller.outbox(null, 20)).isInstanceOf(AccessDeniedException.class);

        authenticate("workflow_recovery_view");
        assertThatCode(() -> controller.outbox(null, 20)).doesNotThrowAnyException();
    }

    @Test
    void recoveryRetryRequiresEditPermission() {
        authenticate("workflow_recovery_view");
        assertThatThrownBy(() -> controller.retryInbox(
                "00000000-0000-0000-0000-000000000007", "人工恢复"))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("workflow_recovery_edit");
        assertThatCode(() -> controller.retryInbox(
                "00000000-0000-0000-0000-000000000007", "人工恢复"))
                .doesNotThrowAnyException();
    }

    private void authenticate(String permission) {
        BixiUser actor = new BixiUser(41L, 1L, 1L, "operator", "unused", null, true,
                true, true, true, AuthorityUtils.createAuthorityList(permission));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actor, "unused", actor.getAuthorities()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(UpmsRecoveryController.class)
    static class Config {
        @Bean static PrePostTemplateDefaults prePostTemplateDefaults() { return new PrePostTemplateDefaults(); }
        @Bean("pms") PermissionService permissions() { return new PermissionService(); }
        @Bean UpmsRecoveryService recovery() { return mock(UpmsRecoveryService.class); }
    }
}
