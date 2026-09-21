package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.feign.sentinel.handle.GlobalBizExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.dto.SysLogDTO;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.entity.SysRole;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import com.lotus.bixi.upms.api.vo.UserVO;
import com.lotus.bixi.upms.api.service.*;
import com.lotus.bixi.upms.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Exercises the production permission annotation through Spring method security. */
@SpringJUnitConfig(UpmsManagementPermissionTest.Config.class)
class UpmsManagementPermissionTest {

    @Autowired ApplicationContext context;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        reset(context.getBean(SysUserService.class));
    }

    @ParameterizedTest(name = "ordinary user cannot access {0}")
    @MethodSource("managementOperations")
    void ordinaryUserCannotAccessManagementOperations(Operation operation) {
        authenticate("demo_task_view");
        assertThatThrownBy(() -> operation.invoke().accept(context)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "authorized operator can access {0}")
    @MethodSource("managementOperations")
    void explicitPermissionAllowsTheOperation(Operation operation) {
        authenticate(operation.permission());
        assertThatCode(() -> operation.invoke().accept(context)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "form dependency: {0}")
    @MethodSource("formDependencies")
    void formsCanLoadTheirRequiredOptionsWithoutUnrelatedManagementPermissions(Operation operation) {
        authenticate(operation.permission());
        assertThatCode(() -> operation.invoke().accept(context)).doesNotThrowAnyException();
    }

    @Test
    void exportPermissionDoesNotAllowImportingUsersOrRoles() {
        authenticate("sys_user_export");
        assertThatThrownBy(() -> context.getBean(SysUserController.class).importUser(List.of(), null))
                .isInstanceOf(AccessDeniedException.class);
        authenticate("sys_role_export");
        assertThatThrownBy(() -> context.getBean(SysRoleController.class).importRole(List.of(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ordinaryUserCanStillReadTheirOwnNavigation() {
        authenticate("demo_task_view");
        assertThatCode(() -> context.getBean(SysMenuController.class).getUserMenu(null, null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/me", "/user/me?id=999&userId=999"})
    void ordinaryUserCanReadOnlyTheirOwnProfileWithoutManagementPermission(String path) throws Exception {
        authenticate("demo_task_view");
        var profile = new UserVO();
        profile.setId(41L);
        profile.setUsername("operator");
        profile.setName("Current user");
        profile.setPassword("profile-password-must-not-be-returned");
        profile.setSalt("profile-salt-must-not-be-returned");
        var users = context.getBean(SysUserService.class);
        when(users.selectUserVoById(41L)).thenReturn(profile);
        var http = MockMvcBuilders.standaloneSetup(context.getBean(SysUserController.class)).build();

        var response = http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse();
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("code").asInt()).isZero();
        var data = body.get("data");
        assertThat(data.get("id").asLong()).isEqualTo(41L);
        assertThat(data.get("username").asText()).isEqualTo("operator");
        assertThat(data.get("name").asText()).isEqualTo("Current user");
        assertThat(data.has("password")).isFalse();
        assertThat(data.has("salt")).isFalse();
        assertThat(response.getContentAsString()).doesNotContain(profile.getPassword(), profile.getSalt());
        var managementHttp = MockMvcBuilders.standaloneSetup(context.getBean(SysUserController.class))
                .setControllerAdvice(new GlobalBizExceptionHandler()).build();
        managementHttp.perform(get("/user/details/41")).andExpect(status().isForbidden());
        managementHttp.perform(get("/user/details/999")).andExpect(status().isForbidden());
        verify(users).selectUserVoById(41L);
        verifyNoMoreInteractions(users);
    }

    @Test
    void noticeEditorCanReadOnlyTheUserOptionFields() throws Exception {
        authenticate("sys_notice_add");
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "permission-test"), SysUser.class);
        var user = new SysUser();
        user.setId(42L);
        user.setUsername("recipient");
        user.setName("Recipient");
        user.setPassword("must-not-be-returned");
        user.setPhone("13800000000");
        user.setEmail("private@example.test");
        var page = new Page<SysUser>();
        page.setRecords(List.of(user));
        when(context.getBean(SysUserService.class).page(any(Page.class), any())).thenReturn(page);
        var http = MockMvcBuilders.standaloneSetup(context.getBean(SysUserController.class))
                .setControllerAdvice(new GlobalBizExceptionHandler()).build();
        var response = http.perform(get("/user/options")).andExpect(status().isOk()).andReturn().getResponse();
        var data = new ObjectMapper().readTree(response.getContentAsString()).get("data");
        assertThat(data).isNotNull();
        assertThat(data.size()).isEqualTo(1);
        var fields = new java.util.HashSet<String>();
        data.get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "name");
        assertThat(data.get(0).get("username").asText()).isEqualTo("recipient");
        http.perform(get("/user/page")).andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotReadRecipientOptions() throws Exception {
        authenticate("demo_task_view");
        MockMvcBuilders.standaloneSetup(context.getBean(SysUserController.class))
                .setControllerAdvice(new GlobalBizExceptionHandler()).build()
                .perform(get("/user/options")).andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotForgeARecipientRecordToReadUnsentNotices() throws Exception {
        authenticate("demo_task_view");
        MockMvcBuilders.standaloneSetup(context.getBean(SysUserNoticeController.class))
                .setControllerAdvice(new GlobalBizExceptionHandler()).build()
                .perform(post("/user-notice").contentType("application/json")
                        .content("{\"noticeId\":2,\"userId\":41}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "PUT"));
    }

    @Test
    void personalInboxOverridesTheRequestedUserId() {
        authenticate("demo_task_view");
        var query = new UserNoticeVO();
        query.setUserId(999L);
        context.getBean(SysUserNoticeController.class).getSysUserNoticePage(new Page<>(), query);
        assertThat(query.getUserId()).isEqualTo(41L);
    }

    private void authenticate(String permission) {
        var actor = new BixiUser(41L, 1L, "operator", "unused", null, true, true, true, true,
                AuthorityUtils.createAuthorityList(permission));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actor, "unused", actor.getAuthorities()));
    }

    static Stream<Operation> managementOperations() {
        return Stream.of(
                new Operation("user details", "sys_user_view", c -> c.getBean(SysUserController.class).user(2L)),
                new Operation("user page", "sys_user_view", c -> c.getBean(SysUserController.class).getUserPage(new Page<>(), new UserDTO())),
                new Operation("user import", "sys_user_import", c -> c.getBean(SysUserController.class).importUser(List.of(), null)),
                new Operation("role details", "sys_role_view", c -> c.getBean(SysRoleController.class).getById(2L)),
                new Operation("role query", "sys_role_view", c -> c.getBean(SysRoleController.class).getDetails(new SysRole())),
                new Operation("role page", "sys_role_view", c -> c.getBean(SysRoleController.class).getRolePage(new Page<>(), new SysRole())),
                new Operation("role options", "sys_role_view", c -> c.getBean(SysRoleController.class).listRoles()),
                new Operation("role lookup", "sys_role_view", c -> c.getBean(SysRoleController.class).getRoleList(List.of(2L))),
                new Operation("role import", "sys_role_import", c -> c.getBean(SysRoleController.class).importRole(List.of(), null)),
                new Operation("log page", "sys_log_view", c -> c.getBean(SysLogController.class).getLogPage(new Page<>(), new SysLogDTO())),
                new Operation("menu details", "sys_menu_view", c -> c.getBean(SysMenuController.class).getById(2L)),
                new Operation("menu tree", "sys_menu_view", c -> c.getBean(SysMenuController.class).getTree(null, null, null)),
                new Operation("role menu tree", "sys_role_perm", c -> c.getBean(SysMenuController.class).getRoleTree(2L)),
                new Operation("department details", "sys_dept_view", c -> c.getBean(SysDeptController.class).getById(2L)),
                new Operation("department list", "sys_dept_view", c -> c.getBean(SysDeptController.class).list()),
                new Operation("department tree", "sys_dept_view", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("department descendants", "sys_dept_view", c -> c.getBean(SysDeptController.class).getDescendantList(2L)),
                new Operation("department export", "sys_dept_export", c -> c.getBean(SysDeptController.class).export()),
                new Operation("department import", "sys_dept_import", c -> c.getBean(SysDeptController.class).importDept(List.of(), null)),
                new Operation("client details", "sys_client_view", c -> c.getBean(SysClientController.class).getByClientId("bixi")),
                new Operation("client page", "sys_client_view", c -> c.getBean(SysClientController.class).getOauthClientDetailsPage(new Page<>(), new SysOauthClientDetails())),
                new Operation("client export", "sys_client_export", c -> c.getBean(SysClientController.class).export(new SysOauthClientDetails())),
                new Operation("client cache sync", "sys_client_edit", c -> c.getBean(SysClientController.class).sync()),
                new Operation("session page", "sys_token_view", c -> c.getBean(SysTokenController.class).getTokenPage(Map.of("current", 1, "size", 10))),
                new Operation("cache monitor", "sys_system_view", c -> c.getBean(SysSystemInfoController.class).cache()),
                new Operation("notice page", "sys_notice_view", c -> c.getBean(SysNoticeController.class).getSysNoticePage(new Page<>(), new SysNotice())),
                new Operation("notice details", "sys_notice_view", c -> c.getBean(SysNoticeController.class).getById(2L)),
                new Operation("notice create", "sys_notice_add", c -> c.getBean(SysNoticeController.class).save(new SysNoticeVO())),
                new Operation("notice update", "sys_notice_edit", c -> c.getBean(SysNoticeController.class).updateById(new SysNoticeVO())),
                new Operation("notice delete", "sys_notice_del", c -> c.getBean(SysNoticeController.class).removeById(2L)),
                new Operation("notice send", "sys_notice_send", c -> c.getBean(SysNoticeController.class).send(2L)),
                new Operation("notice delivery records", "sys_notice_view", c -> c.getBean(SysUserNoticeController.class).getNoticeRecordPage(new Page<>(), new UserNoticeVO()))
        );
    }

    static Stream<Operation> formDependencies() {
        return Stream.of(
                new Operation("assign roles to new user", "sys_user_add", c -> c.getBean(SysRoleController.class).listRoles()),
                new Operation("edit user roles", "sys_user_edit", c -> c.getBean(SysRoleController.class).listRoles()),
                new Operation("user list department filter", "sys_user_view", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("new user department", "sys_user_add", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("edit user department", "sys_user_edit", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("new role department", "sys_role_add", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("edit role department", "sys_role_edit", c -> c.getBean(SysDeptController.class).getTree(null)),
                new Operation("new role name validation", "sys_role_add", c -> c.getBean(SysRoleController.class).getDetails(new SysRole())),
                new Operation("assign role permissions", "sys_role_perm", c -> c.getBean(SysMenuController.class).getTree(null, null, null)),
                new Operation("notice role recipients", "sys_notice_add", c -> c.getBean(SysRoleController.class).listRoles()),
                new Operation("notice department recipients", "sys_notice_add", c -> c.getBean(SysDeptController.class).getTree(null))
        );
    }

    record Operation(String name, String permission, Consumer<ApplicationContext> invoke) {
        @Override public String toString() { return name; }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({SysUserController.class, SysRoleController.class, SysLogController.class, SysMenuController.class,
            SysDeptController.class, SysClientController.class, SysTokenController.class, SysSystemInfoController.class,
            SysNoticeController.class, SysUserNoticeController.class})
    static class Config {
        @Bean static PrePostTemplateDefaults prePostTemplateDefaults() { return new PrePostTemplateDefaults(); }
        @Bean("pms") PermissionService permissions() { return new PermissionService(); }
        @Bean SysUserService users() { return mock(SysUserService.class); }
        @Bean UserQueryService userQuery() { return mock(UserQueryService.class); }
        @Bean SysRoleService roles() { return mock(SysRoleService.class); }
        @Bean SysLogService logs() { return mock(SysLogService.class); }
        @Bean OperationLogService operationLogs() { return mock(OperationLogService.class); }
        @Bean SysMenuService menus() { return mock(SysMenuService.class); }
        @Bean SysDeptService departments() { return mock(SysDeptService.class); }
        @Bean SysNoticeService notices() { return mock(SysNoticeService.class); }
        @Bean SysUserNoticeService userNotices() { return mock(SysUserNoticeService.class); }
        @Bean SysUserNoticeSseService noticeStream() { return mock(SysUserNoticeSseService.class); }
        @Bean SysOauthClientDetailsService clients() { return mock(SysOauthClientDetailsService.class); }
        @Bean ClientDetailsQueryService clientQuery() { return mock(ClientDetailsQueryService.class); }
        @Bean TokenManagementService tokens() { return mock(TokenManagementService.class); }
        @Bean RedisTemplate<String, String> redis() { return mock(RedisTemplate.class); }
    }
}
