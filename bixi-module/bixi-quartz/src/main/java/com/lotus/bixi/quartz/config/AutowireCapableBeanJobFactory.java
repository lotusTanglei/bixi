package com.lotus.bixi.quartz.config;

import org.quartz.JobKey;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.util.Assert;

/**
 * @author 唐磊
 */
class AutowireCapableBeanJobFactory extends SpringBeanJobFactory {

	private final AutowireCapableBeanFactory beanFactory;

	AutowireCapableBeanJobFactory(AutowireCapableBeanFactory beanFactory) {
		Assert.notNull(beanFactory, "Bean factory must not be null");
		this.beanFactory = beanFactory;
	}

	@Override
	protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
		Object jobInstance = super.createJobInstance(bundle);
		this.beanFactory.autowireBean(jobInstance);

		// 此处必须注入 beanName 不然sentinel 报错
		JobKey jobKey = bundle.getTrigger().getJobKey();
		String beanName = jobKey + jobKey.getName();
		this.beanFactory.initializeBean(jobInstance, beanName);
		return jobInstance;
	}

}
