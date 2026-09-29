package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.mybatis.config.MybatisPlusMetaObjectHandler;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.service.impl.FormVersionServiceImpl;
import com.lotus.bixi.workflow.service.impl.WorkflowFormSchema;
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
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(FormVersionServiceImplTest.Config.class)
@TestPropertySource(properties = "workflow.enabled=true")
class FormVersionServiceImplTest {
    @Autowired FormVersionService service;
    @Autowired DataSource dataSource;

    @BeforeEach
    void createSchema() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        WorkflowTestSchema.create(jdbc, "wf_form", "wf_form_version");
        jdbc.update("INSERT INTO wf_form (id, form_key, form_name, current_version, status, del_flag, data_status) "
                + "VALUES (7, 'leave', 'Leave', 0, '0', '0', '0')");
    }

    @Test
    void createsAnUnpublishedVersionOneWhenAFormHasNoVersionYet() {
        WfFormVersion created = service.createVersion(7L, "{\"widgetList\":[]}", "initial");

        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getIsActive()).isEqualTo("0");
        assertThat(created.getSchemaJson()).isEqualTo("{\"widgetList\":[]}");
    }

    @Test
    void validatesSchemaBeforeSavingAndAllocatesSequentialVersions() {
        assertThatThrownBy(() -> service.createVersion(7L, "not-json", "invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表单 schema");

        assertThat(service.createVersion(7L, "{\"widgetList\":[]}", "one").getVersion()).isEqualTo(1);
        assertThat(service.createVersion(7L, "{\"widgetList\":[]}", "two").getVersion()).isEqualTo(2);
    }

    @Test
    void activationAndRollbackKeepExactlyOnePublishedVersion() {
        service.createVersion(7L, "{\"widgetList\":[]}", "one");
        service.createVersion(7L, "{\"widgetList\":[]}", "two");

        service.activateVersion(7L, 2);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM wf_form_version WHERE form_id = 7 AND is_active = '1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_version FROM wf_form WHERE id = 7", Integer.class))
                .isEqualTo(2);

        service.rollback(7L, 1);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM wf_form_version WHERE form_id = 7 AND is_active = '1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_version FROM wf_form WHERE id = 7", Integer.class))
                .isEqualTo(1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = WfFormVersionMapper.class)
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:form-version-" + UUID.randomUUID()
                    + ";DB_CLOSE_DELAY=-1;MODE=MYSQL", "sa", "");
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean WorkflowFormSchema workflowFormSchema() {
            return new WorkflowFormSchema(new ObjectMapper());
        }

        @Bean MybatisPlusMetaObjectHandler mybatisPlusMetaObjectHandler() {
            return new MybatisPlusMetaObjectHandler();
        }

        @Bean FormVersionService formVersionService(WfFormVersionMapper mapper, WfFormMapper forms,
                WorkflowFormSchema schemas) {
            FormVersionServiceImpl service = new FormVersionServiceImpl(forms, schemas);
            org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
            return service;
        }
    }
}
