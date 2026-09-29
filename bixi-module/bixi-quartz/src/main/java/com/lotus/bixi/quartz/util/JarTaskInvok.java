package com.lotus.bixi.quartz.util;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务可执行jar反射实现
 *
 * @author 唐磊
 */
@Slf4j
@Component("jarTaskInvok")
public class JarTaskInvok implements TaskInvok {

	private static final long PROCESS_TIMEOUT_SECONDS = 300;

	private static final int MAX_CAPTURED_OUTPUT = 4000;

	@Override
	public void invokMethod(SysJob sysJob) throws TaskException {
		File jar = new File(sysJob.getExecutePath());
		if (!jar.isFile()) {
			throw new TaskException("定时任务JAR文件不存在,任务:" + sysJob.getName());
		}
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.directory(jar.getParentFile());
		processBuilder.redirectErrorStream(true);
		List<String> commands = new ArrayList<>();
		commands.add("java");
		commands.add("-jar");
		commands.add(sysJob.getExecutePath());
		if (StrUtil.isNotEmpty(sysJob.getMethodParamsValue())) {
			commands.add(sysJob.getMethodParamsValue());
		}
		processBuilder.command(commands);
		ExecutorService outputReader = Executors.newSingleThreadExecutor();
		Process process = null;
		try {
			process = processBuilder.start();
			Process runningProcess = process;
			Future<String> output = outputReader.submit(() -> drainOutput(runningProcess.getInputStream()));
			if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(10, TimeUnit.SECONDS);
				throw new TaskException("定时任务JAR执行超时,任务:" + sysJob.getName());
			}
			String capturedOutput = output.get(10, TimeUnit.SECONDS);
			if (process.exitValue() != 0) {
				throw new TaskException("定时任务JAR以非零状态退出(" + process.exitValue() + "),任务:"
					+ sysJob.getName() + ",输出:" + capturedOutput);
			}
		}
		catch (TaskException ex) {
			throw ex;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new TaskException("定时任务JAR执行被中断,任务:" + sysJob.getName());
		}
		catch (Exception e) {
			log.error("定时任务jar反射执行异常,执行任务：{}", sysJob.getExecutePath(), e);
			throw new TaskException("定时任务jar反射执行异常,执行任务：" + sysJob.getExecutePath());
		}
		finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
			outputReader.shutdownNow();
		}
	}

	private String drainOutput(InputStream input) throws IOException {
		StringBuilder captured = new StringBuilder();
		byte[] buffer = new byte[1024];
		int count;
		while ((count = input.read(buffer)) >= 0) {
			int remaining = MAX_CAPTURED_OUTPUT - captured.length();
			if (remaining > 0) {
				captured.append(new String(buffer, 0, Math.min(count, remaining), java.nio.charset.StandardCharsets.UTF_8));
			}
		}
		return captured.toString();
	}

}
