package com.lotus.bixi.generator.controller;

import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GenTemplateControllerSecurityTest {

	@Test
	void onlineUpdateIsAnAuditedEditOperation() throws Exception {
		Method online = GenTemplateController.class.getDeclaredMethod("online");

		assertThat(online.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(online.getAnnotation(GetMapping.class)).isNull();
		assertThat(online.getAnnotation(HasPermission.class).value()).containsExactly("codegen_template_edit");
		assertThat(online.getAnnotation(SysLog.class).value()).isEqualTo("在线更新模板");
	}
}
