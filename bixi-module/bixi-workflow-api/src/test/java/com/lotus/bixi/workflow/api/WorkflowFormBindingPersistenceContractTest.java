package com.lotus.bixi.workflow.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormBindingPersistenceContractTest {

    @Test
    void persistsThePublishedFormVersionOnEachProcessDefinition() throws Exception {
        assertColumn(WfProcessDefinition.class, "formVersionId", "form_version_id");
    }

    @Test
    void persistsTheImmutableFormBindingOnEachProcessInstance() throws Exception {
        assertColumn(WfProcessInstance.class, "formId", "form_id");
        assertColumn(WfProcessInstance.class, "formVersionId", "form_version_id");
    }

    @Test
    void scopesFieldRulesToAFormVersionAndWorkflowNode() throws Exception {
        assertColumn(SysFormPermission.class, "formVersionId", "form_version_id");
        assertColumn(SysFormPermission.class, "processDefinitionId", "process_definition_id");
        assertColumn(SysFormPermission.class, "taskDefinitionKey", "task_definition_key");
    }

    private static void assertColumn(Class<?> entity, String fieldName, String columnName) throws Exception {
        Field field = entity.getDeclaredField(fieldName);
        TableField mapping = field.getAnnotation(TableField.class);
        assertThat(mapping).as("explicit mapping for %s.%s", entity.getSimpleName(), fieldName).isNotNull();
        assertThat(mapping.value()).isEqualTo(columnName);
    }
}
