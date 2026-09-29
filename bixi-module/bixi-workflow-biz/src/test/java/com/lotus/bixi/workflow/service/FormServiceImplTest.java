package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.mybatis.config.MybatisPlusMetaObjectHandler;
import com.lotus.bixi.workflow.api.dto.FormDTO;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.impl.FormServiceImpl;
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

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(FormServiceImplTest.Config.class)
@TestPropertySource(properties = "workflow.enabled=true")
class FormServiceImplTest {
    @Autowired FormService service;
    @Autowired DataSource dataSource;

    @BeforeEach
    void createSchema() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_form", "wf_form_version", "wf_process_definition",
                "wf_process_instance");
        jdbc.update("INSERT INTO wf_form (id, form_key, form_name, current_version, status, del_flag, data_status) "
                + "VALUES (7, 'leave', 'Leave', 99, '1', '0', '0')");
        jdbc.update("INSERT INTO wf_form_version "
                + "(id, form_id, version, schema_json, is_active, del_flag, status, data_status) "
                + "VALUES (71, 7, 1, '{\"widgetList\":[]}', '1', '0', '0', '0')");
    }

    @Test
    void renderIdentityComesFromTheSelectedPublishedVersionRow() {
        FormRenderVO render = service.getRenderInfo("leave");

        assertThat(render.getFormId()).isEqualTo(7L);
        assertThat(render.getFormVersionId()).isEqualTo(71L);
        assertThat(render.getVersion()).isEqualTo(1);
        assertThat(render.getSchemaJson()).isEqualTo("{\"widgetList\":[]}");
    }

    @Test
    void rendersTheExactFrozenVersionEvenAfterAnotherVersionBecomesActive() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("UPDATE wf_form_version SET is_active = '0' WHERE id = 71");
        jdbc.update("INSERT INTO wf_form_version "
                + "(id, form_id, version, schema_json, is_active, del_flag, status, data_status) "
                + "VALUES (72, 7, 2, '{\"widgetList\":[{\"type\":\"input\"}]}', '1', '0', '0', '0')");

        FormRenderVO render = service.getRenderInfo("leave", 71L);

        assertThat(render.getFormVersionId()).isEqualTo(71L);
        assertThat(render.getVersion()).isEqualTo(1);
        assertThat(render.getSchemaJson()).isEqualTo("{\"widgetList\":[]}");
    }

    @Test
    void newFormsStartAsDraftsWithoutClaimingANonexistentVersion() {
        FormDTO request = new FormDTO();
        request.setFormKey("expense");
        request.setFormName("Expense");
        request.setStatus("1");

        WfForm created = service.saveForm(request);

        assertThat(created.getCurrentVersion()).isZero();
        assertThat(created.getStatus()).isEqualTo("0");
    }

    @Test
    void rejectsDeletingAFormReferencedByAProcessDefinition() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wf_process_definition "
                + "(id, process_definition_id, process_key, form_key, form_version_id, del_flag, data_status) "
                + "VALUES (81, 'approval:1:81', 'approval', 'leave', 71, '0', '0')");

        assertThatThrownBy(() -> service.removeById(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("流程定义");
        assertThat(jdbc.queryForObject("SELECT del_flag FROM wf_form WHERE id = 7", String.class)).isEqualTo("0");
    }

    @Test
    void batchDeletionIsAtomicWhenOneFormIsReferencedByAnExistingInstance() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO wf_form (id, form_key, form_name, current_version, status, del_flag, data_status) "
                + "VALUES (8, 'expense', 'Expense', 0, '0', '0', '0')");
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id, process_instance_id, process_definition_id, process_key, form_id, form_version_id, "
                + "status, del_flag, data_status) "
                + "VALUES (91, 'instance-91', 'approval:1:81', 'approval', 7, 71, 'running', '0', '0')");

        assertThatThrownBy(() -> service.removeByIds(java.util.List.of(7L, 8L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("流程实例");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_form WHERE del_flag = '0'", Integer.class))
                .isEqualTo(2);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = WfFormMapper.class)
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:form-render-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;MODE=MYSQL", "sa", "");
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean MybatisPlusMetaObjectHandler mybatisPlusMetaObjectHandler() {
            return new MybatisPlusMetaObjectHandler();
        }

        @Bean FormService formService(WfFormMapper forms, WfFormVersionMapper versions,
                WfProcessDefinitionMapper definitions, WfProcessInstanceMapper instances) {
            FormServiceImpl service = new FormServiceImpl(versions, definitions, instances);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", forms);
            return service;
        }
    }
}
