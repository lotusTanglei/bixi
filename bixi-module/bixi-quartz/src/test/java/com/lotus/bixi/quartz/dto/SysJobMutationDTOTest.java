package com.lotus.bixi.quartz.dto;

import com.lotus.bixi.quartz.entity.SysJob;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SysJobMutationDTOTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void acceptsAValidBoundedRetryConfigurationAndMapsOnlyMutableFields() {
		SysJobMutationDTO request = SysJobMutationDTO.builder()
			.id(7L)
			.name("billing")
			.group("finance")
			.type("2")
			.className("billingTask")
			.methodName("run")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy("3")
			.retryCount(2)
			.retryIntervalSeconds(30)
			.remark("idempotent task")
			.build();

		assertThat(validator.validate(request)).isEmpty();
		SysJob job = request.toEntity();
		assertThat(job.getId()).isEqualTo(7L);
		assertThat(job.getRetryCount()).isEqualTo(2);
		assertThat(job.getRetryIntervalSeconds()).isEqualTo(30);
		assertThat(job.getStatus()).isNull();
		assertThat(job.getTenantId()).isNull();
	}

	@Test
	void rejectsInvalidCronUnboundedRetryAndMissingInvocationTarget() {
		SysJobMutationDTO request = SysJobMutationDTO.builder()
			.name("billing")
			.group("finance")
			.type("2")
			.cronExpression("not-cron")
			.misfirePolicy("9")
			.retryCount(6)
			.retryIntervalSeconds(0)
			.build();

		Set<String> paths = validator.validate(request).stream()
			.map(violation -> violation.getPropertyPath().toString())
			.collect(Collectors.toSet());

		assertThat(paths).contains("cronExpressionValid", "invocationTargetValid", "misfirePolicy",
			"retryCount", "retryIntervalSeconds");
	}

	@Test
	void restAndJarTasksRequireAnExecutionPath() {
		SysJobMutationDTO request = SysJobMutationDTO.builder()
			.name("remote")
			.group("integration")
			.type("3")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy("3")
			.build();

		assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
			.contains("invocationTargetValid");
	}

}
