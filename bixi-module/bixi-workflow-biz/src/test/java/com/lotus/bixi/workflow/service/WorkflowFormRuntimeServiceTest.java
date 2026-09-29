package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.config.MybatisPlusMetaObjectHandler;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.command.WorkflowRequestHasher;
import com.lotus.bixi.workflow.mapper.SysFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.SysRoleFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.WfFormDataMapper;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import com.lotus.bixi.workflow.service.impl.WorkflowFormRuntimeService;
import com.lotus.bixi.workflow.service.impl.WorkflowFormSchema;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(WorkflowFormRuntimeServiceTest.Config.class)
@TestPropertySource(properties = "workflow.enabled=true")
class WorkflowFormRuntimeServiceTest {
    private static final String SCHEMA = """
            {"widgetList":[
              {"type":"number","options":{"name":"amount","required":true,"min":1}},
              {"type":"input","options":{"name":"note","maxLength":20}},
              {"type":"input","options":{"name":"secret"}},
              {"type":"input","options":{"name":"free"}}
            ]}
            """;

    @Autowired WorkflowFormRuntimeService forms;
    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_form", "wf_form_version", "wf_process_definition",
                "wf_process_instance", "wf_form_data", "sys_form_permission", "sys_role_form_permission");
        jdbc.update("INSERT INTO wf_form (id, form_key, form_name, current_version, status, del_flag, data_status, tenant_id) "
                + "VALUES (7, 'expense', 'Expense', 2, '1', '0', '0', 1)");
        jdbc.update("INSERT INTO wf_form_version "
                + "(id, form_id, version, schema_json, is_active, del_flag, status, data_status, tenant_id) "
                + "VALUES (71, 7, 1, ?, '0', '0', '0', '0', 1), "
                + "(72, 7, 2, '{\"widgetList\":[]}', '1', '0', '0', '0', 1)", SCHEMA);
        jdbc.update("INSERT INTO wf_process_definition "
                + "(id, process_definition_id, process_key, version, form_key, form_version_id, del_flag, status, data_status, tenant_id) "
                + "VALUES (81, 'approval:1:1', 'approval', 1, 'expense', 71, '0', '0', '0', 1)");
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id, process_instance_id, process_definition_id, process_key, form_id, form_version_id, "
                + "start_user_id, status, del_flag, data_status, tenant_id) "
                + "VALUES (91, 'instance-1', 'approval:1:1', 'approval', 7, 71, 11, 'running', '0', '0', 1)");
        jdbc.update("INSERT INTO wf_form_data "
                + "(id, form_id, form_version_id, process_instance_id, data_json, del_flag, status, data_status, tenant_id) "
                + "VALUES (92, 7, 71, 'instance-1', "
                + "'{\"amount\":10,\"note\":\"old\",\"secret\":\"classified\",\"free\":\"visible\"}', "
                + "'0', '0', '0', 1)");

        insertPermission(101, "amount", "readonly");
        insertPermission(102, "note", "edit");
        insertPermission(103, "secret", "hidden");
        insertPermission(104, "note", "readonly");
        insertPermission(105, "note", "hidden");
        for (long permissionId : List.of(101L, 102L, 103L, 104L, 105L)) {
            jdbc.update("INSERT INTO sys_role_form_permission "
                    + "(id, role_id, form_perm_id, del_flag, status, data_status, tenant_id) "
                    + "VALUES (?, 11, ?, '0', '0', '0', 1)", 1000 + permissionId, permissionId);
        }
        login(11L);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void definitionBindingRejectsClientSelectionAndKeepsThePublishedVersion() {
        assertThatThrownBy(() -> forms.prepareStart("approval:1:1", 8L,
                "{\"amount\":10,\"note\":\"new\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("绑定表单");

        WorkflowFormRuntimeService.PreparedForm prepared = forms.prepareStart("approval:1:1", 7L,
                "{\"amount\":10,\"note\":\"new\"}");

        assertThat(prepared.formId()).isEqualTo(7L);
        assertThat(prepared.formVersionId()).isEqualTo(71L);
        assertThat(prepared.dataJson()).isEqualTo("{\"amount\":10,\"note\":\"new\"}");
    }

    @Test
    void startRenderUsesTheFrozenVersionAndAppliesServerFieldPermissions() {
        insertPermission(201, WorkflowFormRuntimeService.START_TASK_KEY, "amount", "readonly");
        insertPermission(202, WorkflowFormRuntimeService.START_TASK_KEY, "secret", "hidden");
        for (long permissionId : List.of(201L, 202L)) {
            jdbc.update("INSERT INTO sys_role_form_permission "
                    + "(id, role_id, form_perm_id, del_flag, status, data_status, tenant_id) "
                    + "VALUES (?, 11, ?, '0', '0', '0', 1)", 1000 + permissionId, permissionId);
        }

        FormRenderVO render = forms.renderStart("approval:1:1");

        assertThat(render.getFormId()).isEqualTo(7L);
        assertThat(render.getFormVersionId()).isEqualTo(71L);
        assertThat(render.getVersion()).isEqualTo(1);
        assertThat(render.getSchemaJson()).contains("amount", "note", "free").doesNotContain("secret");
        assertThat(render.getDataJson()).isEqualTo("{}");
        assertThat(render.getPermissions()).containsEntry("amount", "readonly")
                .containsEntry("note", "edit")
                .containsEntry("free", "edit")
                .containsEntry("secret", "hidden");
    }

    @Test
    void taskWriteMergesOnlyEditableFieldsAndRenderRemovesHiddenFields() {
        WfProcessInstance instance = boundInstance();

        assertThatThrownBy(() -> forms.prepareTask(instance, "review", "{\"amount\":20}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
        assertThatThrownBy(() -> forms.prepareTask(instance, "review", "{\"secret\":\"changed\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret");

        WorkflowFormRuntimeService.PreparedForm prepared =
                forms.prepareTask(instance, "review", "{\"note\":\"updated\"}");
        assertThat(prepared.dataId()).isEqualTo(92L);
        assertThat(prepared.dataJson()).isEqualTo(
                "{\"amount\":10,\"free\":\"visible\",\"note\":\"updated\",\"secret\":\"classified\"}");

        FormRenderVO render = forms.render(instance, "review");
        assertThat(render.getFormId()).isEqualTo(7L);
        assertThat(render.getFormVersionId()).isEqualTo(71L);
        assertThat(render.getVersion()).isEqualTo(1);
        assertThat(render.getSchemaJson()).contains("amount", "note", "free").doesNotContain("secret");
        assertThat(render.getDataJson()).contains("amount", "note", "free").doesNotContain("secret", "classified");
        assertThat(render.getPermissions()).containsEntry("amount", "readonly")
                .containsEntry("note", "edit")
                .containsEntry("free", "edit")
                .containsEntry("secret", "hidden");
    }

    @Test
    void scopedFieldsWithoutACurrentRoleGrantFailClosed() {
        login(12L);

        FormRenderVO render = forms.render(boundInstance(), "review");

        assertThat(render.getSchemaJson()).contains("free").doesNotContain("amount", "note", "secret");
        assertThat(render.getDataJson()).contains("free", "visible")
                .doesNotContain("amount", "note", "secret", "classified");
        assertThat(render.getPermissions()).containsEntry("free", "edit")
                .containsEntry("amount", "hidden")
                .containsEntry("note", "hidden")
                .containsEntry("secret", "hidden");
    }

    private void insertPermission(long id, String field, String type) {
        insertPermission(id, "review", field, type);
    }

    private void insertPermission(long id, String taskDefinitionKey, String field, String type) {
        jdbc.update("INSERT INTO sys_form_permission "
                + "(id, form_id, form_version_id, process_definition_id, task_definition_key, field_code, "
                + "permission, perm_type, del_flag, status, data_status, tenant_id) "
                + "VALUES (?, 7, 71, 'approval:1:1', ?, ?, ?, ?, '0', '0', '0', 1)",
                id, taskDefinitionKey, field, "expense_" + field + "_" + type, type);
    }

    private WfProcessInstance boundInstance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setProcessInstanceId("instance-1");
        instance.setProcessDefinitionId("approval:1:1");
        instance.setFormId(7L);
        instance.setFormVersionId(71L);
        return instance;
    }

    private static void login(long roleId) {
        BixiUser user = new BixiUser(11L, null, 1L, "reviewer", "", null,
                true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + roleId)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "", user.getAuthorities()));
        TenantContextHolder.set(1L);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = WfFormMapper.class)
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:workflow-form-runtime-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;MODE=MYSQL", "sa", "");
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean MybatisPlusMetaObjectHandler mybatisPlusMetaObjectHandler() {
            return new MybatisPlusMetaObjectHandler();
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean WorkflowFormSchema workflowFormSchema(ObjectMapper objectMapper) {
            return new WorkflowFormSchema(objectMapper);
        }

        @Bean WorkflowRequestHasher workflowRequestHasher() {
            return new WorkflowRequestHasher();
        }

        @Bean WorkflowFormRuntimeService workflowFormRuntimeService(
                WfProcessDefinitionMapper definitions, WfFormMapper forms, WfFormVersionMapper versions,
                WfFormDataMapper data, SysFormPermissionMapper permissions,
                SysRoleFormPermissionMapper rolePermissions, WorkflowFormSchema schemas,
                WorkflowRequestHasher hasher) {
            return new WorkflowFormRuntimeService(definitions, forms, versions, data, permissions,
                    rolePermissions, schemas, hasher);
        }
    }
}
