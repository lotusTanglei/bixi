package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.service.SysJobService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TaskInvokUtilSpringWiringTest {

	@Test
	void productionConstructorIsUnambiguousToSpring() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(SysJobService.class, () -> mock(SysJobService.class));
			context.register(TaskInvokUtil.class);

			context.refresh();

			assertThat(context.getBean(TaskInvokUtil.class)).isNotNull();
		}
	}
}
