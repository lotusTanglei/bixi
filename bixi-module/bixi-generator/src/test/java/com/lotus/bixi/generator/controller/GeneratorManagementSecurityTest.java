package com.lotus.bixi.generator.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import com.lotus.bixi.generator.entity.GenFieldType;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generator's administrative endpoints are privileged APIs too. Keep their
 * permission names stable so the seed menu and the UI cannot silently drift.
 */
class GeneratorManagementSecurityTest {

	@Test
	void datasourceManagementUsesViewAddEditDeletePermissionsAndAuditsWrites() throws Exception {
		assertPermission(GenDatasourceConfigController.class.getMethod("getSysDatasourceConfPage", Page.class,
				GenDatasourceConfig.class), "codegen_datasource_view");
		assertPermission(GenDatasourceConfigController.class.getMethod("list"), "codegen_datasource_view");
		assertPermission(GenDatasourceConfigController.class.getMethod("getById", Long.class),
				"codegen_datasource_view");
		assertPermission(GenDatasourceConfigController.class.getMethod("generatorDoc", String.class,
				HttpServletResponse.class), "codegen_datasource_view");
		assertWrite(GenDatasourceConfigController.class.getMethod("save", GenDatasourceConfig.class),
				"codegen_datasource_add", "新增数据源");
		assertWrite(GenDatasourceConfigController.class.getMethod("updateById", GenDatasourceConfig.class),
				"codegen_datasource_edit", "修改数据源");
		assertWrite(GenDatasourceConfigController.class.getMethod("removeById", Long[].class),
				"codegen_datasource_del", "删除数据源");
	}

	@Test
	void fieldTypeManagementUsesSnakeCasePermissionsAndAuditsWrites() throws Exception {
		assertPermission(GenFieldTypeController.class.getMethod("getFieldTypePage", Page.class, GenFieldType.class),
				"codegen_field_type_view");
		assertPermission(GenFieldTypeController.class.getMethod("list", GenFieldType.class),
				"codegen_field_type_view");
		assertPermission(GenFieldTypeController.class.getMethod("getById", Long.class), "codegen_field_type_view");
		assertPermission(GenFieldTypeController.class.getMethod("getDetails", GenFieldType.class),
				"codegen_field_type_view");
		assertPermission(GenFieldTypeController.class.getMethod("export", GenFieldType.class),
				"codegen_field_type_export");
		assertWrite(GenFieldTypeController.class.getMethod("save", GenFieldType.class), "codegen_field_type_add",
				"新增列属性");
		assertWrite(GenFieldTypeController.class.getMethod("updateById", GenFieldType.class),
				"codegen_field_type_edit", "修改列属性");
		assertWrite(GenFieldTypeController.class.getMethod("removeById", Long[].class), "codegen_field_type_del",
				"通过id删除列属性");
	}

	@Test
	void groupListIsProtectedAndTemplateGroupPermissionsUseSnakeCase() throws Exception {
		assertPermission(GenGroupController.class.getMethod("list"), "codegen_group_view");

		assertPermission(GenTemplateGroupController.class.getMethod("getgenTemplateGroupPage", Page.class,
				GenTemplateGroup.class), "codegen_template_group_view");
		assertPermission(GenTemplateGroupController.class.getMethod("getById", Long.class),
				"codegen_template_group_view");
		assertWrite(GenTemplateGroupController.class.getMethod("save", GenTemplateGroup.class),
				"codegen_template_group_add", "新增模板分组关联表");
		assertWrite(GenTemplateGroupController.class.getMethod("updateById", GenTemplateGroup.class),
				"codegen_template_group_edit", "修改模板分组关联表");
		assertWrite(GenTemplateGroupController.class.getMethod("removeById", Long[].class),
				"codegen_template_group_del", "通过id删除模板分组关联表");
		assertPermission(GenTemplateGroupController.class.getMethod("export", GenTemplateGroup.class),
				"codegen_template_group_export");
	}

	private void assertWrite(Method method, String permission, String auditTitle) {
		assertPermission(method, permission);
		assertThat(method.getAnnotation(SysLog.class)).as(method.toGenericString()).isNotNull();
		assertThat(method.getAnnotation(SysLog.class).value()).isEqualTo(auditTitle);
	}

	private void assertPermission(Method method, String expected) {
		HasPermission permission = method.getAnnotation(HasPermission.class);
		assertThat(permission).as(method.toGenericString()).isNotNull();
		assertThat(permission.value()).containsExactly(expected);
		assertThat(expected).doesNotContainPattern("[A-Z]");
	}

}
