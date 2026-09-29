package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JarTaskInvokTest {

	@TempDir
	Path tempDirectory;

	@Test
	void rejectsANonZeroJavaProcessExit() throws Exception {
		Path invalidJar = tempDirectory.resolve("invalid.jar");
		Files.writeString(invalidJar, "not a jar");
		SysJob job = SysJob.builder().name("jar-job").executePath(invalidJar.toString()).build();

		assertThatThrownBy(() -> new JarTaskInvok().invokMethod(job))
			.isInstanceOf(TaskException.class)
			.hasMessageContaining("非零状态");
	}
}
