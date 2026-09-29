package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.mybatis.config.MybatisPlusMetaObjectHandler;
import com.lotus.bixi.workflow.api.dto.FormPermissionDTO;
import com.lotus.bixi.workflow.api.dto.FormFieldPermissionBatchDTO;
import com.lotus.bixi.workflow.api.dto.FormFieldPermissionItemDTO;
import com.lotus.bixi.workflow.api.dto.RoleFormPermissionDTO;
import com.lotus.bixi.workflow.mapper.SysFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.SysRoleFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.service.impl.FormPermissionServiceImpl;
import com.lotus.bixi.workflow.service.impl.WorkflowFormSchema;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(FormPermissionServiceImplTest.Config.class)
@TestPropertySource(properties = "workflow.enabled=true")
class FormPermissionServiceImplTest {
    @Autowired FormPermissionService permissions;
    @Autowired FormService forms;
    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_form", "wf_form_version",
                "sys_form_permission", "sys_role_form_permission");
        jdbc.update("INSERT INTO wf_form "
                + "(id, form_key, form_name, current_version, status, del_flag, data_status, tenant_id) "
                + "VALUES (7, 'expense', 'Expense', 1, '1', '0', '0', 1)");
        jdbc.update("INSERT INTO wf_form_version "
                + "(id, form_id, version, schema_json, is_active, del_flag, status, data_status, tenant_id) "
                + "VALUES (71, 7, 1, ?, '1', '0', '0', '0', 1)", """
                {"widgetList":[
                  {"type":"number","options":{"name":"amount","label":"Amount","required":true}},
                  {"type":"input","options":{"name":"note","label":"Note"}}
                ]}
                """);
    }

    @Test
    void persistsPermissionAndRoleAssociationUsingTheCanonicalSchema() {
        FormPermissionDTO permission = new FormPermissionDTO();
        permission.setFormId(7L);
        permission.setFieldCode("amount");
        permission.setPermission("edit");
        permission.setPermType("edit");
        permission.setDescription("金额");
        assertThat(permissions.savePermission(permission)).isTrue();

        Long permissionId = jdbc.queryForObject(
                "SELECT id FROM sys_form_permission WHERE form_id = 7 AND field_code = 'amount'", Long.class);
        RoleFormPermissionDTO role = new RoleFormPermissionDTO();
        role.setRoleId(11L);
        role.setFormPermId(permissionId);
        assertThat(permissions.saveRolePermission(role)).isTrue();

        assertThat(jdbc.queryForObject("SELECT form_perm_id FROM sys_role_form_permission WHERE role_id = 11",
                Long.class)).isEqualTo(permissionId);
        assertThat(permissions.getFieldPermissions(7L, 11L)).singleElement()
                .satisfies(field -> {
                    assertThat(field.getFieldCode()).isEqualTo("amount");
                    assertThat(field.getFieldLabel()).isEqualTo("金额");
                    assertThat(field.getPermType()).isEqualTo("edit");
                });
    }

    @Test
    void hasPermissionRequiresAnExplicitGrantForTheCurrentTrustedRole() {
        WfForm form = new WfForm();
        form.setId(7L);
        when(forms.getByKey("expense")).thenReturn(form);
        jdbc.update("INSERT INTO sys_form_permission "
                + "(id, form_id, permission, perm_type, del_flag, status, data_status, tenant_id) "
                + "VALUES (101, 7, 'expense_edit', 'edit', '0', '0', '0', 1)");
        jdbc.update("INSERT INTO sys_role_form_permission "
                + "(id, role_id, form_perm_id, del_flag, status, data_status, tenant_id) "
                + "VALUES (201, 9, 101, '0', '0', '0', 1)");

        login(9L);
        assertThat(permissions.hasPermission("expense", "edit")).isTrue();
        login(10L);
        assertThat(permissions.hasPermission("expense", "edit")).isFalse();
        SecurityContextHolder.clearContext();
        assertThat(permissions.hasPermission("expense", "edit")).isFalse();
    }

    @Test
    void replacesAFullVersionScopedRoleFieldPolicyAtomically() {
        FormFieldPermissionBatchDTO batch = new FormFieldPermissionBatchDTO();
        batch.setFields(List.of(field("amount", "readonly"), field("note", "edit")));

        assertThat(permissions.saveFieldPermissions(7L, 11L, 71L, batch)).isTrue();
        assertThat(permissions.getFieldPermissions(7L, 11L, 71L, null, null))
                .extracting(field -> field.getFieldCode() + ":" + field.getFieldType() + ":" + field.getPermType())
                .containsExactly("amount:number:readonly", "note:input:edit");

        batch.setFields(List.of(field("amount", "edit"), field("note", "hidden")));
        assertThat(permissions.saveFieldPermissions(7L, 11L, 71L, batch)).isTrue();
        assertThat(permissions.getFieldPermissions(7L, 11L, 71L, null, null))
                .extracting(field -> field.getFieldCode() + ":" + field.getPermType())
                .containsExactly("amount:edit", "note:hidden");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_form_permission "
                        + "WHERE role_id = 11 AND del_flag = '0'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectsIncompleteUnknownAndCrossFormVersionPolicies() {
        FormFieldPermissionBatchDTO incomplete = new FormFieldPermissionBatchDTO();
        incomplete.setFields(List.of(field("amount", "edit")));
        assertThatThrownBy(() -> permissions.saveFieldPermissions(7L, 11L, 71L, incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("全部字段");

        FormFieldPermissionBatchDTO unknown = new FormFieldPermissionBatchDTO();
        unknown.setFields(List.of(field("amount", "edit"), field("admin", "hidden")));
        assertThatThrownBy(() -> permissions.saveFieldPermissions(7L, 11L, 71L, unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字段");

        assertThatThrownBy(() -> permissions.getFieldPermissions(8L, 11L, 71L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本");
    }

    private static FormFieldPermissionItemDTO field(String code, String permission) {
        FormFieldPermissionItemDTO field = new FormFieldPermissionItemDTO();
        field.setFieldCode(code);
        field.setPermType(permission);
        return field;
    }

    private static void login(long roleId) {
        var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_" + roleId));
        var user = new BixiUser(11L, null, 1L, "reviewer", "", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = SysFormPermissionMapper.class)
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:form-permission-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;MODE=MYSQL", "sa", "");
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean MybatisPlusMetaObjectHandler mybatisPlusMetaObjectHandler() {
            return new MybatisPlusMetaObjectHandler();
        }

        @Bean FormService formService() {
            return mock(FormService.class);
        }

        @Bean WorkflowFormSchema workflowFormSchema() {
            return new WorkflowFormSchema(new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Bean FormPermissionService formPermissionService(SysFormPermissionMapper formPermissions,
                SysRoleFormPermissionMapper rolePermissions, WfFormVersionMapper versions,
                FormService forms, WorkflowFormSchema schemas) {
            FormPermissionServiceImpl service = new FormPermissionServiceImpl(forms, rolePermissions, versions, schemas);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", formPermissions);
            return service;
        }
    }
}
