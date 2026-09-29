package com.lotus.bixi.quartz;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.event.SysJobEvent;
import com.lotus.bixi.quartz.event.SysJobListener;
import com.lotus.bixi.quartz.event.SysJobRecordEvent;
import com.lotus.bixi.quartz.event.SysJobRecordListener;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskInvokUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzExecutionSemanticsTest {

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void jobAndRecordListenersRunOnTheQuartzThread() throws NoSuchMethodException {
		Method jobListener = SysJobListener.class.getMethod("comSysJob", SysJobEvent.class);
		Method recordListener = SysJobRecordListener.class.getMethod("saveSysJobRecord", SysJobRecordEvent.class);

		assertThat(jobListener.getAnnotation(Async.class)).isNull();
		assertThat(recordListener.getAnnotation(Async.class)).isNull();
	}

	@Test
	void failedInvocationIsRecordedAndPropagatedToQuartz() {
		AtomicReference<SysJobRecord> record = new AtomicReference<>();
		ApplicationEventPublisher publisher = event -> {
			if (event instanceof SysJobRecordEvent recordEvent) {
				record.set(recordEvent.getSysJobRecord());
			}
		};
		SysJobService jobService = mock(SysJobService.class);
		when(jobService.updateById(any(SysJob.class))).thenReturn(true);
		TaskInvokUtil invoker = new TaskInvokUtil(publisher, jobService);
		SysJob job = SysJob.builder().name("broken").group("test").type("9").build();
		job.setId(7L);
		job.setTenantId(42L);

		Trigger trigger = TriggerBuilder.newTrigger().startNow().build();
		assertThatThrownBy(() -> invoker.invokMethod(job, trigger))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("broken");

		assertThat(record.get()).isNotNull();
		assertThat(record.get().getStatus()).isEqualTo("1");
		verify(jobService).updateById(any(SysJob.class));
	}

	@Test
	void invocationUsesThePersistedJobTenantAndRestoresTheSchedulerThread() {
		AtomicReference<Long> eventTenant = new AtomicReference<>();
		ApplicationEventPublisher publisher = event -> eventTenant.set(TenantContextHolder.get());
		SysJobService jobService = mock(SysJobService.class);
		AtomicReference<Long> updateTenant = new AtomicReference<>();
		when(jobService.updateById(any(SysJob.class))).thenAnswer(invocation -> {
			updateTenant.set(TenantContextHolder.get());
			assertThat(TenantContextHolder.isReadOnlySwitch()).isFalse();
			return true;
		});
		TaskInvokUtil invoker = new TaskInvokUtil(publisher, jobService);
		SysJob job = SysJob.builder().name("tenant-job").group("test").type("9").build();
		job.setId(8L);
		job.setTenantId(42L);
		TenantContextHolder.set(99L);
		TenantContextHolder.setReadOnlySwitch(true);

		assertThatThrownBy(() -> invoker.invokMethod(job, TriggerBuilder.newTrigger().startNow().build()))
			.isInstanceOf(IllegalStateException.class);

		assertThat(eventTenant.get()).isEqualTo(42L);
		assertThat(updateTenant.get()).isEqualTo(42L);
		assertThat(TenantContextHolder.get()).isEqualTo(99L);
		assertThat(TenantContextHolder.isReadOnlySwitch()).isTrue();
	}
}
