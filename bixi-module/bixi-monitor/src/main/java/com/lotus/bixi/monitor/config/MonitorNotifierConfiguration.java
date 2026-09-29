package com.lotus.bixi.monitor.config;

import de.codecentric.boot.admin.server.domain.entities.InstanceRepository;
import de.codecentric.boot.admin.server.notify.LoggingNotifier;
import de.codecentric.boot.admin.server.notify.RemindingNotifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Records instance status changes and repeats alerts while an instance remains unavailable.
 */
@Configuration(proxyBeanMethods = false)
public class MonitorNotifierConfiguration {

	@Bean(initMethod = "start", destroyMethod = "stop")
	RemindingNotifier statusChangeNotifier(InstanceRepository repository) {
		var notifier = new RemindingNotifier(new LoggingNotifier(repository), repository);
		notifier.setReminderStatuses(new String[] { "DOWN", "OFFLINE" });
		return notifier;
	}

}
