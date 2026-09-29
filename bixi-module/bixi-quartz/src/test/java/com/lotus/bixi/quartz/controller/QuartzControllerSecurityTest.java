package com.lotus.bixi.quartz.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.dto.SysJobMutationDTO;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzControllerSecurityTest {

	@Test
	void jobReadsAndExportRequireTheirDeclaredPermissions() throws Exception {
		assertPermission(SysJobController.class.getMethod("getSysJobPage", Page.class, SysJob.class),
			"job_sys_job_view");
		assertPermission(SysJobController.class.getMethod("getById", Long.class), "job_sys_job_view");
		assertPermission(SysJobController.class.getMethod("getLog", Page.class, SysJobRecord.class),
			"job_sys_job_view");
		assertPermission(SysJobController.class.getMethod("isValidTaskName", String.class, String.class),
			"job_sys_job_view");
		assertPermission(SysJobController.class.getMethod("export", SysJob.class), "job_sys_job_export");
	}

	@Test
	void recordReadsAndDeletesAreProtectedAndDeleteIsAudited() throws Exception {
		Method page = SysJobRecordController.class.getMethod("getSysJobRecordPage", Page.class, SysJobRecord.class);
		Method delete = SysJobRecordController.class.getMethod("deleteLogs", Long[].class);

		assertPermission(page, "job_sys_job_record_view");
		assertPermission(delete, "job_sys_job_record_del");
		assertThat(delete.getAnnotation(SysLog.class)).isNotNull();
		assertThat(delete.getAnnotation(SysLog.class).value()).isEqualTo("删除定时任务日志");
	}

	@Test
	void jobMutationsUseValidatedRequestDtos() throws Exception {
		Method save = SysJobController.class.getMethod("save", SysJobMutationDTO.class);
		Method update = SysJobController.class.getMethod("updateById", SysJobMutationDTO.class);

		assertThat(save.getParameterAnnotations()[0]).anyMatch(annotation -> annotation instanceof Valid);
		assertThat(update.getParameterAnnotations()[0]).anyMatch(annotation -> annotation instanceof Valid);
	}

	private void assertPermission(Method method, String expected) {
		HasPermission permission = method.getAnnotation(HasPermission.class);
		assertThat(permission).as(method.toGenericString()).isNotNull();
		assertThat(permission.value()).containsExactly(expected);
	}
}
