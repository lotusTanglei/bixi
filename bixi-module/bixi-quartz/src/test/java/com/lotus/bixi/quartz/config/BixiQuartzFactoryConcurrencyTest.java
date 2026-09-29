package com.lotus.bixi.quartz.config;

import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BixiQuartzFactoryConcurrencyTest {

	@Test
	void repeatedTriggersForTheSameLongRunningJobNeverOverlap() throws Exception {
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		CountDownLatch completed = new CountDownLatch(2);
		BixiQuartzInvokeFactory invoker = new BixiQuartzInvokeFactory(event -> {
			int concurrent = active.incrementAndGet();
			maxActive.accumulateAndGet(concurrent, Math::max);
			try {
				Thread.sleep(150);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			finally {
				active.decrementAndGet();
				completed.countDown();
			}
		});
		BixiQuartzFactory factory = new BixiQuartzFactory();
		Field field = BixiQuartzFactory.class.getDeclaredField("quartzInvokeFactory");
		field.setAccessible(true);
		field.set(factory, invoker);

		Scheduler scheduler = scheduler();
		scheduler.setJobFactory((bundle, ignored) -> factory);
		try {
			SysJob sysJob = SysJob.builder().name("slow").group("tests").build();
			sysJob.setTenantId(42L);
			JobDetail job = JobBuilder.newJob(BixiQuartzFactory.class)
				.withIdentity("slow", "tenant-42:tests")
				.storeDurably()
				.build();
			job.getJobDataMap().put(BixiQuartzEnum.SCHEDULE_JOB_KEY.getType(), sysJob);
			scheduler.addJob(job, false);
			scheduler.scheduleJob(TriggerBuilder.newTrigger()
				.withIdentity("first", "tenant-42:tests")
				.forJob(job)
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule())
				.build());
			scheduler.scheduleJob(TriggerBuilder.newTrigger()
				.withIdentity("second", "tenant-42:tests")
				.forJob(job)
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule())
				.build());

			scheduler.start();

			assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(maxActive.get()).isEqualTo(1);
		}
		finally {
			scheduler.shutdown(true);
		}
	}

	private Scheduler scheduler() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("org.quartz.scheduler.instanceName", "bixi-concurrency-" + UUID.randomUUID());
		properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
		properties.setProperty("org.quartz.threadPool.threadCount", "2");
		properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
		return new StdSchedulerFactory(properties).getScheduler();
	}

}
