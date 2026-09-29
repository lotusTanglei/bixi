package com.lotus.bixi.generator.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.generator.dto.GenerateCodeRequest;
import com.lotus.bixi.generator.dto.GenTableImportRequest;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorControllerSecurityTest {

	@Test
	void generationIsAPostMutationWithDedicatedPermissionAndAudit() throws Exception {
		Method code = GeneratorController.class.getMethod("code", GenerateCodeRequest.class);

		assertThat(code.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(code.getAnnotation(GetMapping.class)).isNull();
		assertPermission(code, "codegen_table_generate");
		assertAudit(code, "生成代码到项目目录");
	}

	@Test
	void previewAndGeneratedArchiveUseTheDeclaredReadAndGeneratePermissions() throws Exception {
		assertPermission(GeneratorController.class.getMethod("preview", Long.class), "codegen_table_view");
		assertPermission(GeneratorController.class.getMethod("download", String.class, String.class,
				HttpServletResponse.class),
			"codegen_table_generate");
	}

	@Test
	void tableReadsWritesSynchronizationAndExportAreProtected() throws Exception {
		assertPermission(GenTableController.class.getMethod("getTablePage", Page.class, GenTable.class),
			"codegen_table_view");
		assertPermission(GenTableController.class.getMethod("getTable", Long.class), "codegen_table_view");
		assertPermission(GenTableController.class.getMethod("listTable", String.class), "codegen_table_view");
		Method initializeTable = GenTableController.class.getMethod("getTable", String.class, String.class);
		assertThat(initializeTable.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(initializeTable.getAnnotation(GetMapping.class)).isNull();
		assertPermission(initializeTable, "codegen_table_edit");
		assertAudit(initializeTable, "初始化代码生成表配置");
		assertPermission(GenTableController.class.getMethod("getColumn", String.class, String.class),
			"codegen_table_view");
		assertPermission(GenTableController.class.getMethod("getDdl", String.class, String.class),
			"codegen_table_view");
		Method exactConfiguration = GenTableController.class
			.getMethod("getConfiguredTable", String.class, String.class);
		assertThat(exactConfiguration.getAnnotation(GetMapping.class)).isNotNull();
		assertPermission(exactConfiguration, "codegen_table_view");
		assertThat(exactConfiguration.getAnnotation(SysLog.class)).isNull();

		Method importTable = GenTableController.class.getMethod("importTable", String.class, String.class,
			GenTableImportRequest.class);
		assertThat(importTable.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(importTable.getAnnotation(GetMapping.class)).isNull();
		assertPermission(importTable, "codegen_table_edit");
		assertAudit(importTable, "导入代码生成表");
		assertThat(importTable.getParameterAnnotations()[2])
			.anySatisfy(annotation -> assertThat(annotation).isInstanceOf(Valid.class));

		Method sync = GenTableController.class.getMethod("syncTable", String.class, String.class);
		assertThat(sync.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(sync.getAnnotation(GetMapping.class)).isNull();
		assertPermission(sync, "codegen_table_sync");
		assertAudit(sync, "同步代码生成表结构");

		assertPermission(GenTableController.class.getMethod("updateById", GenTable.class),
			"codegen_table_edit");
		assertPermission(GenTableController.class.getMethod("updateTableField", String.class, String.class, List.class),
			"codegen_table_edit");
		assertPermission(GenTableController.class.getMethod("export", GenTable.class), "codegen_table_export");
	}

	private void assertPermission(Method method, String expected) {
		HasPermission permission = method.getAnnotation(HasPermission.class);
		assertThat(permission).as(method.toGenericString()).isNotNull();
		assertThat(permission.value()).containsExactly(expected);
	}

	private void assertAudit(Method method, String expected) {
		SysLog audit = method.getAnnotation(SysLog.class);
		assertThat(audit).as(method.toGenericString()).isNotNull();
		assertThat(audit.value()).isEqualTo(expected);
	}
}
