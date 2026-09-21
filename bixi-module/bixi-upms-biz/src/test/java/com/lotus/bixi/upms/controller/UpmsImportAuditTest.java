package com.lotus.bixi.upms.controller;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.aspect.SysLogAspect;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import com.lotus.bixi.upms.api.service.UserQueryService;
import com.lotus.bixi.upms.api.vo.DeptExcelVo;
import com.lotus.bixi.upms.api.vo.RoleExcelVO;
import com.lotus.bixi.upms.api.vo.UserExcelVO;
import com.lotus.bixi.upms.service.SysDeptService;
import com.lotus.bixi.upms.service.SysRoleService;
import com.lotus.bixi.upms.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.WebDataBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/** Import writes must pass method security and publish a real audit event. */
@SpringJUnitConfig(UpmsImportAuditTest.Config.class)
class UpmsImportAuditTest {
    @Autowired ApplicationContext context;
    @Autowired OperationLogService persistence;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        reset(persistence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "role", "dept"})
    void importsRecordTheAuthenticatedActorAndOperation(String resource) {
        var actor = new BixiUser(41L, 1L, "importer", "unused", null, true, true, true, true,
                AuthorityUtils.createAuthorityList("sys_" + resource + "_import"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, "", actor.getAuthorities()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/" + resource + "/import")));

        // Match RequestExcelArgumentResolver: the real BindingResult contains a self-referencing model.
        var bindingResult = new WebDataBinder(new ArrayList<>(), "excel").getBindingResult();
        assertThatCode(() -> {
            switch (resource) {
                case "user" -> {
                    var row = new UserExcelVO();
                    row.setUsername("imported-user");
                    row.setPhone("sensitive-import-phone");
                    context.getBean(SysUserController.class).importUser(List.of(row), bindingResult);
                }
                case "role" -> {
                    var row = new RoleExcelVO();
                    row.setRoleName("imported-role");
                    context.getBean(SysRoleController.class).importRole(List.of(row), bindingResult);
                }
                case "dept" -> {
                    var row = new DeptExcelVo();
                    row.setName("imported-dept");
                    context.getBean(SysDeptController.class).importDept(List.of(row), bindingResult);
                }
                default -> throw new IllegalArgumentException(resource);
            }
        }).doesNotThrowAnyException();

        var saved = ArgumentCaptor.forClass(SysLog.class);
        verify(persistence).saveLog(saved.capture());
        assertThat(saved.getValue().getCreateBy()).isEqualTo(41L);
        assertThat(saved.getValue().getTitle()).isEqualTo(Map.of("user", "导入用户", "role", "导入角色", "dept", "导入部门").get(resource));
        assertThat(saved.getValue().getMethod()).isEqualTo("POST");
        assertThat(saved.getValue().getRequestUri()).isEqualTo("/" + resource + "/import");
        assertThat(saved.getValue().getParams()).contains("imported-" + resource)
                .doesNotContain("sensitive-import-phone", "BindingResult", "bindingResult", "propertyEditorRegistry");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableMethodSecurity
    @Import({SysUserController.class, SysRoleController.class, SysDeptController.class, SysLogAspect.class, SysLogListener.class})
    static class Config {
        @Bean static PrePostTemplateDefaults prePostTemplateDefaults() { return new PrePostTemplateDefaults(); }
        @Bean("pms") PermissionService permissionService() { return new PermissionService(); }
        @Bean BixiLogProperties logProperties() { return new BixiLogProperties(); }
        @Bean SpringContextHolder contextHolder() { return new SpringContextHolder(); }
        @Bean static SpringUtil springUtil() { return new SpringUtil(); }
        @Bean SysUserService users() { return mock(SysUserService.class); }
        @Bean UserQueryService userQuery() { return mock(UserQueryService.class); }
        @Bean SysRoleService roles() { return mock(SysRoleService.class); }
        @Bean SysDeptService departments() { return mock(SysDeptService.class); }
        @Bean OperationLogService persistence() { return mock(OperationLogService.class); }
    }
}
