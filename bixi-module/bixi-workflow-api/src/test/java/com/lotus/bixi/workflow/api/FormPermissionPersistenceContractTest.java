package com.lotus.bixi.workflow.api;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.entity.SysRoleFormPermission;
import com.lotus.bixi.workflow.api.entity.WfForm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormPermissionPersistenceContractTest {

    @Test
    void javaModelsUseTheColumnsDefinedByThePermissionTables() throws NoSuchFieldException {
        assertThat(SysFormPermission.class.getDeclaredField("fieldCode")
                .getAnnotation(TableField.class).value()).isEqualTo("field_code");
        assertThat(SysFormPermission.class.getDeclaredField("permission")
                .getAnnotation(TableField.class).value()).isEqualTo("permission");
        assertThat(SysFormPermission.class.getDeclaredField("description")
                .getAnnotation(TableField.class).value()).isEqualTo("description");
        assertThat(SysRoleFormPermission.class.getDeclaredField("formPermId")
                .getAnnotation(TableField.class).value()).isEqualTo("form_perm_id");
    }

    @Test
    void formModelUsesTheCanonicalFormColumns() throws NoSuchFieldException {
        assertThat(WfForm.class.getDeclaredField("formDesc")
                .getAnnotation(TableField.class).value()).isEqualTo("description");
        assertThat(WfForm.class.getDeclaredField("formType")).isNotNull();
        assertThat(WfForm.class.getDeclaredField("category")).isNotNull();
    }
}
