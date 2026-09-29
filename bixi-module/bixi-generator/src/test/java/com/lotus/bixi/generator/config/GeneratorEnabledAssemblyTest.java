package com.lotus.bixi.generator.config;

import com.lotus.bixi.generator.controller.GenDatasourceConfigController;
import com.lotus.bixi.generator.controller.GenFieldTypeController;
import com.lotus.bixi.generator.controller.GenGroupController;
import com.lotus.bixi.generator.controller.GenTableController;
import com.lotus.bixi.generator.controller.GenTemplateController;
import com.lotus.bixi.generator.controller.GenTemplateGroupController;
import com.lotus.bixi.generator.controller.GeneratorController;
import com.lotus.bixi.generator.service.impl.GenDatasourceConfigServiceImpl;
import com.lotus.bixi.generator.service.impl.GenFieldTypeServiceImpl;
import com.lotus.bixi.generator.service.impl.GenGroupServiceImpl;
import com.lotus.bixi.generator.service.impl.GenTableServiceImpl;
import com.lotus.bixi.generator.service.impl.GenTemplateGroupServiceImpl;
import com.lotus.bixi.generator.service.impl.GenTemplateServiceImpl;
import com.lotus.bixi.generator.service.impl.GeneratorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorEnabledAssemblyTest {

	@Test
	void generatorDefaultsToEnabledForTheDedicatedCloudApplication() {
		assertThat(new BixiGeneratorDefaultProperties().isEnabled()).isTrue();
	}

	@Test
	void everyGeneratorEntryPointAndServiceIsConditionallyAssembled() {
		List<Class<?>> types = List.of(
				GeneratorController.class,
				GenDatasourceConfigController.class,
				GenFieldTypeController.class,
				GenGroupController.class,
				GenTableController.class,
				GenTemplateController.class,
				GenTemplateGroupController.class,
				GeneratorServiceImpl.class,
				GenDatasourceConfigServiceImpl.class,
				GenFieldTypeServiceImpl.class,
				GenGroupServiceImpl.class,
				GenTableServiceImpl.class,
				GenTemplateServiceImpl.class,
				GenTemplateGroupServiceImpl.class);
		for (Class<?> type : types) {
			ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
			assertThat(condition).as(type.getName()).isNotNull();
			assertThat(condition.prefix()).isEqualTo("generator");
			assertThat(condition.name()).containsExactly("enabled");
			assertThat(condition.havingValue()).isEqualTo("true");
			assertThat(condition.matchIfMissing()).isTrue();
		}
	}

}
