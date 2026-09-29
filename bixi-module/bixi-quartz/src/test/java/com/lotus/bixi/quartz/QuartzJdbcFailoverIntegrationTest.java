package com.lotus.bixi.quartz;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in black-box Quartz cluster exercise.
 *
 * <p>The normal Maven suite deliberately skips this test because it needs a real
 * MySQL schema and two child JVMs. The shell harness starts that disposable
 * environment and sets {@code BIXI_QUARTZ_HA_JDBC_URL}.</p>
 */
class QuartzJdbcFailoverIntegrationTest {

	private static final String JDBC_URL = "BIXI_QUARTZ_HA_JDBC_URL";
	private static final String JDBC_USER = "QUARTZ_HA_JDBC_USERNAME";
	private static final String JDBC_PASSWORD = "QUARTZ_HA_JDBC_PASSWORD";
	private static final String PROBE_TABLE = "bixi_quartz_ha_probe";
	private static final long WAIT_MILLIS = 30_000L;

	@Test
	void killingOwnerLetsAnotherJdbcNodeRecoverAndPersistsFailureHistory() throws Exception {
		String jdbcUrl = System.getenv(JDBC_URL);
		Assumptions.assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(),
			"Set BIXI_QUARTZ_HA_JDBC_URL to run the opt-in two-node exercise");
		String username = env(JDBC_USER, "root");
		String password = env(JDBC_PASSWORD, "");
		String probeId = "ha-" + UUID.randomUUID();
		Path markerDirectory = Files.createTempDirectory("bixi-quartz-ha-");
		List<Process> children = new ArrayList<>();
		try {
			Process owner = launchNode("owner", "hold", probeId, markerDirectory, jdbcUrl);
			children.add(owner);
			waitForMarker(markerDirectory.resolve("ready-owner"), owner, WAIT_MILLIS);
			waitForMarker(markerDirectory.resolve("started-owner"), owner, WAIT_MILLIS);

			// Start the second scheduler only after the owner is inside the long-running
			// invocation. This makes the process we kill unambiguously the job owner.
			Process survivor = launchNode("survivor", "hold", probeId, markerDirectory, jdbcUrl);
			children.add(survivor);
			waitForMarker(markerDirectory.resolve("ready-survivor"), survivor, WAIT_MILLIS);
			waitForSchedulerNodes(jdbcUrl, username, password, 2, WAIT_MILLIS);
			assertThat(owner.isAlive()).as("owner must still be running while the long task is held").isTrue();

			long killAt = System.currentTimeMillis();
			owner.destroyForcibly();
			assertThat(owner.waitFor(10, TimeUnit.SECONDS)).as("owner process must terminate").isTrue();

			waitForEvent(jdbcUrl, username, password, probeId, "RECOVERY_START", WAIT_MILLIS);
			waitForEvent(jdbcUrl, username, password, probeId, "END", WAIT_MILLIS);

			assertThat(countEvents(jdbcUrl, username, password, probeId, "START"))
				.as("the killed owner must have one initial invocation").isEqualTo(1);
			assertThat(countEvents(jdbcUrl, username, password, probeId, "RECOVERY_START"))
				.as("the survivor must execute the recovered invocation once").isEqualTo(1);
			assertThat(countRecoveries(jdbcUrl, username, password, probeId)).isEqualTo(1);
			assertThat(firstEventAfter(jdbcUrl, username, password, probeId, "RECOVERY_START", killAt))
				.as("recovery cannot precede the owner kill").isTrue();

			Process failure = launchNode("failure", "failure", probeId, markerDirectory, jdbcUrl);
			children.add(failure);
			waitForEvent(jdbcUrl, username, password, probeId, "FAILURE", WAIT_MILLIS);
			assertThat(countEvents(jdbcUrl, username, password, probeId, "FAILURE"))
				.as("a failed invocation must remain queryable in the probe history").isEqualTo(1);
		}
		finally {
			for (Process child : children) {
				if (child.isAlive()) {
					child.destroyForcibly();
				}
			}
			for (Process child : children) {
				child.waitFor(5, TimeUnit.SECONDS);
			}
			deleteRecursively(markerDirectory);
		}
	}

	private static Process launchNode(String nodeId, String mode, String probeId, Path markerDirectory,
			String jdbcUrl) throws IOException {
		String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		ProcessBuilder builder = new ProcessBuilder(java, "-cp", classPath,
				QuartzJdbcFailoverIntegrationTest.class.getName(), "node", nodeId, mode, probeId,
				markerDirectory.toString(), jdbcUrl);
		builder.environment().put(JDBC_URL, jdbcUrl);
		builder.environment().put(JDBC_USER, env(JDBC_USER, "root"));
		builder.environment().put(JDBC_PASSWORD, env(JDBC_PASSWORD, ""));
		Path logFile = markerDirectory.resolve(nodeId + ".log");
		return builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile())).start();
	}

	private static void waitForMarker(Path marker, Process process, long timeoutMillis) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (Files.exists(marker)) {
				return;
			}
			if (!process.isAlive()) {
				throw new AssertionError("Quartz child exited before marker " + marker.getFileName());
			}
			Thread.sleep(100L);
		}
		throw new AssertionError("Timed out waiting for Quartz child marker " + marker);
	}

	private static void waitForEvent(String url, String username, String password, String probeId, String event,
			long timeoutMillis) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (countEvents(url, username, password, probeId, event) > 0) {
				return;
			}
			Thread.sleep(150L);
		}
		throw new AssertionError("Timed out waiting for Quartz event " + event);
	}

	private static int countEvents(String url, String username, String password, String probeId, String event)
			throws SQLException {
		try (Connection connection = connection(url, username, password);
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM " + PROBE_TABLE + " WHERE probe_id=? AND event=?")) {
			statement.setString(1, probeId);
			statement.setString(2, event);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private static int countRecoveries(String url, String username, String password, String probeId)
			throws SQLException {
		try (Connection connection = connection(url, username, password);
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM " + PROBE_TABLE
								+ " WHERE probe_id=? AND event='RECOVERY_START' AND recovering=1")) {
			statement.setString(1, probeId);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private static void waitForSchedulerNodes(String url, String username, String password, int expected,
			long timeoutMillis) throws Exception {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (schedulerNodeCount(url, username, password) >= expected) {
				return;
			}
			Thread.sleep(100L);
		}
		throw new AssertionError("Timed out waiting for clustered Quartz scheduler state rows");
	}

	private static int schedulerNodeCount(String url, String username, String password) throws SQLException {
		try (Connection connection = connection(url, username, password);
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM QRTZ_SCHEDULER_STATE WHERE SCHED_NAME='bixiQuartzHa'")) {
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1);
			}
		}
	}

	private static boolean firstEventAfter(String url, String username, String password, String probeId, String event,
			long epochMillis) throws SQLException {
		try (Connection connection = connection(url, username, password);
				PreparedStatement statement = connection.prepareStatement(
						"SELECT COUNT(*) FROM " + PROBE_TABLE
								+ " WHERE probe_id=? AND event=? AND created_at >= FROM_UNIXTIME(? / 1000.0)")) {
			statement.setString(1, probeId);
			statement.setString(2, event);
			statement.setLong(3, epochMillis);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getInt(1) > 0;
			}
		}
	}

	private static Connection connection(String url, String username, String password) throws SQLException {
		return DriverManager.getConnection(url, username, password);
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null ? fallback : value;
	}

	private static void deleteRecursively(Path directory) throws IOException {
		if (directory == null || !Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
					// The temporary directory is best-effort cleanup after a child kill.
				}
			});
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && "node".equals(args[0])) {
			runNode(args);
		}
	}

	private static void runNode(String[] args) throws Exception {
		if (args.length != 6) {
			throw new IllegalArgumentException("node mode requires node, mode, probe, marker directory and JDBC URL");
		}
		String nodeId = args[1];
		String mode = args[2];
		String probeId = args[3];
		Path markerDirectory = Path.of(args[4]);
		String jdbcUrl = args[5];
		String username = env(JDBC_USER, "root");
		String password = env(JDBC_PASSWORD, "");
		Class.forName("com.mysql.cj.jdbc.Driver");
		Scheduler scheduler = new StdSchedulerFactory(properties(nodeId, jdbcUrl, username, password)).getScheduler();
		scheduler.start();
		touch(markerDirectory.resolve("ready-" + nodeId));
		try {
			if ("owner".equals(nodeId)) {
				schedule(scheduler, probeId, "hold", "owner", false, markerDirectory);
				touch(markerDirectory.resolve("scheduled-owner"));
			}
			else if ("failure".equals(mode)) {
				schedule(scheduler, probeId, "failure", "failure", true, markerDirectory);
				touch(markerDirectory.resolve("scheduled-failure"));
			}
			if ("owner".equals(nodeId)) {
				waitForChildTermination();
			}
			else if ("failure".equals(mode)) {
				Thread.sleep(Duration.ofSeconds(20).toMillis());
			}
			else {
				waitForChildTermination();
			}
		}
		finally {
			scheduler.shutdown(false);
		}
	}

	private static Properties properties(String nodeId, String url, String username, String password) {
		Properties properties = new Properties();
		properties.setProperty("org.quartz.scheduler.instanceName", "bixiQuartzHa");
		properties.setProperty("org.quartz.scheduler.instanceId", nodeId + "-" + UUID.randomUUID());
		properties.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
		properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
		properties.setProperty("org.quartz.threadPool.threadCount", "2");
		properties.setProperty("org.quartz.threadPool.threadPriority", "5");
		properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
		properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
		properties.setProperty("org.quartz.jobStore.dataSource", "ha");
		properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
		properties.setProperty("org.quartz.jobStore.isClustered", "true");
		properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "1000");
		properties.setProperty("org.quartz.jobStore.misfireThreshold", "2000");
		properties.setProperty("org.quartz.dataSource.ha.driver", "com.mysql.cj.jdbc.Driver");
		properties.setProperty("org.quartz.dataSource.ha.provider", "hikaricp");
		properties.setProperty("org.quartz.dataSource.ha.URL", url);
		properties.setProperty("org.quartz.dataSource.ha.user", username);
		properties.setProperty("org.quartz.dataSource.ha.password", password);
		properties.setProperty("org.quartz.dataSource.ha.maxConnections", "5");
		properties.setProperty("org.quartz.dataSource.ha.validationQuery", "SELECT 1");
		return properties;
	}

	private static void schedule(Scheduler scheduler, String probeId, String mode, String markerName, boolean failure,
			Path markerDirectory) throws Exception {
		JobDataMap data = new JobDataMap();
		data.put("probeId", probeId);
		data.put("mode", mode);
		data.put("markerName", markerName);
		data.put("failure", failure);
		data.put("markerDirectory", markerDirectory.toString());
		data.put("holdMillis", Long.parseLong(env("QUARTZ_HA_JOB_HOLD_MILLIS", "15000")));
		JobDetail detail = JobBuilder.newJob(ProbeJob.class)
				.withIdentity("probe-" + mode + "-" + probeId, "bixi-ha")
				.usingJobData(data)
				.requestRecovery()
				.build();
		Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity("trigger-" + mode + "-" + probeId, "bixi-ha")
				.forJob(detail)
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule())
				.build();
		scheduler.scheduleJob(detail, trigger);
	}

	private static void waitForChildTermination() throws InterruptedException {
		Thread.sleep(Duration.ofMinutes(5).toMillis());
	}

	private static void touch(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, Long.toString(System.currentTimeMillis()), StandardCharsets.UTF_8);
	}

	@DisallowConcurrentExecution
	public static class ProbeJob implements Job {

		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			JobDataMap data = context.getMergedJobDataMap();
			String probeId = data.getString("probeId");
			String nodeId;
			try {
				nodeId = context.getScheduler().getSchedulerInstanceId();
				boolean recovering = context.isRecovering();
				insert(probeId, nodeId, recovering ? "RECOVERY_START" : "START", recovering, null);
				touch(Path.of(data.getString("markerDirectory"))
						.resolve((recovering ? "recovered-" : "started-") + data.getString("markerName")));
				if (recovering) {
					insert(probeId, nodeId, "RECOVERY", true, "Quartz recovery fire");
				}
				if (Boolean.TRUE.equals(data.getBoolean("failure"))) {
					insert(probeId, nodeId, "FAILURE", recovering, "intentional failure history probe");
					throw new JobExecutionException("intentional failure history probe");
				}
				if (!recovering) {
					Thread.sleep(data.getLong("holdMillis"));
				}
				insert(probeId, nodeId, "END", recovering, null);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new JobExecutionException(interrupted);
			}
			catch (Exception failure) {
				if (failure instanceof JobExecutionException jobFailure) {
					throw jobFailure;
				}
				throw new JobExecutionException(failure);
			}
		}

		private void insert(String probeId, String nodeId, String event, boolean recovering, String message)
				throws SQLException {
			String url = env(JDBC_URL, "");
			try (Connection connection = DriverManager.getConnection(url, env(JDBC_USER, "root"), env(JDBC_PASSWORD, ""));
					PreparedStatement statement = connection.prepareStatement(
							"INSERT INTO " + PROBE_TABLE
									+ " (probe_id,node_id,event,recovering,message) VALUES (?,?,?,?,?)")) {
				statement.setString(1, probeId);
				statement.setString(2, nodeId);
				statement.setString(3, event);
				statement.setBoolean(4, recovering);
				statement.setString(5, message);
				statement.executeUpdate();
			}
		}
	}

}
