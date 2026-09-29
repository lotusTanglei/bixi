package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.LocalDurableTransport;
import com.lotus.bixi.common.mq.reliable.OutboxDispatcher;
import com.lotus.bixi.common.mq.reliable.ReliableDeliveryProperties;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.upms.mq.NoticeDelivery;
import com.lotus.bixi.upms.mq.PublishedNoticeNotifier;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import com.lotus.bixi.upms.service.SysUserRoleService;
import com.lotus.bixi.upms.service.SysUserService;
import com.lotus.bixi.upms.service.impl.SysNoticeServiceImpl;
import com.lotus.bixi.upms.service.impl.SysUserNoticeServiceImpl;
import com.lotus.bixi.upms.service.impl.SysUserRoleServiceImpl;
import com.lotus.bixi.upms.workflow.WorkflowTaskNotificationHandler;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotificationCodec;
import com.lotus.bixi.workflow.event.WorkflowTaskNotificationPublisher;
import com.lotus.bixi.workflow.event.WorkflowTaskNotificationSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Real owner-to-owner proof for the single-mode task notification path. */
@SpringJUnitConfig(WorkflowTaskNotificationReliabilityIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class WorkflowTaskNotificationReliabilityIntegrationTest {

    @Autowired DataSource source;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired SysNoticeService notices;
    @Autowired PublishedNoticeNotifier notifier;
    @Autowired CandidateIdentityQueryService identities;
    @Autowired CandidateRoleQueryService candidateRoles;
    @Autowired SysUserRoleService userRoles;
    @Autowired SysUserNoticeSseService sse;

    private JdbcTemplate jdbc;
    private JdbcOutboxStore workflowOutbox;
    private LocalDurableTransport transport;
    private OutboxDispatcher dispatcher;
    private WorkflowTaskNotificationPublisher publisher;

    @BeforeEach
    void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        WorkflowTestSchema.create(jdbc, "sys_notice", "sys_user_notice", "sys_user_role");
        installReliableTable("20260921_reliable_outbox.sql");
        installReliableTable("20260921_reliable_inbox.sql");
        jdbc.execute("TRUNCATE TABLE reliable_outbox");
        jdbc.execute("TRUNCATE TABLE reliable_inbox");
        reset(sse);

        ReliableDeliveryProperties properties = ReliableDeliveryProperties.defaults();
        workflowOutbox = new JdbcOutboxStore(source, transactionManager, properties);
        JdbcInboxStore upmsInbox = new JdbcInboxStore(source, transactionManager, properties);
        WorkflowTaskNotificationCodec codec = new WorkflowTaskNotificationCodec();
        WorkflowTaskNotificationHandler handler =
                new WorkflowTaskNotificationHandler(codec, identities, candidateRoles, userRoles, notices, notifier);
        InboxExecutor upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", WorkflowTaskNotification.TYPE, 1), handler,
                new InboxExecutor.Route("workflow", WorkflowTaskNotification.TYPE, 2), handler));
        transport = new LocalDurableTransport(Map.of("upms", upmsExecutor));
        dispatcher = new OutboxDispatcher(workflowOutbox, "workflow", transport, properties);
        publisher = new WorkflowTaskNotificationPublisher(workflowOutbox, codec);
    }

    @AfterEach
    void cleanup() {
        if (dispatcher != null) dispatcher.close();
        TenantContextHolder.clear();
    }

    @Test
    void committedTargetAndReplayedSourceCreateOnePublishedNotice() {
        String commandId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.record(
                new WorkflowTaskNotificationSink.Context(
                        "demo_leave_approval:3:deployment-1", "process-7", "demo_leave_approval",
                        "task-9", "请假审批", List.of(22L), List.of(), "42", "demo_leave:7:1",
                        commandId, operationId, Instant.parse("2026-09-24T08:00:00Z"))));

        assertThat(count("reliable_outbox")).isOne();
        assertThat(count("sys_notice")).isZero();
        JdbcOutboxStore.Lease first = workflowOutbox.claim("workflow", 1).get(0);

        transport.deliver(first.message());

        Long noticeId = jdbc.queryForObject("SELECT id FROM sys_notice", Long.class);
        Long recipientId = jdbc.queryForObject("SELECT id FROM sys_user_notice", Long.class);
        assertThat(jdbc.queryForObject("SELECT status FROM sys_notice WHERE id = ?", String.class, noticeId))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM sys_notice WHERE id = ?", Long.class, noticeId))
                .isEqualTo(42L);
        assertThat(jdbc.queryForObject("SELECT user_id FROM sys_user_notice WHERE id = ?", Long.class, recipientId))
                .isEqualTo(22L);
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner = 'upms'",
                String.class)).isEqualTo("PROCESSED");
        verify(sse).publishRefresh(22L, noticeId, recipientId);

        jdbc.update("UPDATE reliable_outbox SET lease_until = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) "
                + "WHERE source_owner = 'workflow' AND event_id = ?", first.message().eventId());
        assertThat(dispatcher.dispatchOnce()).isOne();

        assertThat(count("sys_notice")).isOne();
        assertThat(count("sys_user_notice")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_outbox WHERE source_owner = 'workflow'",
                String.class)).isEqualTo("DELIVERED");
        verify(sse, times(1)).publishRefresh(22L, noticeId, recipientId);
    }

    @Test
    void candidateRoleMembersBecomeConcreteRecipientsInTheReliableTransaction() {
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id, create_time) VALUES (22, 11, UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO sys_user_role (user_id, role_id, create_time) VALUES (33, 11, UTC_TIMESTAMP(6))");
        String commandId = UUID.randomUUID().toString();
        String operationId = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> publisher.record(
                new WorkflowTaskNotificationSink.Context(
                        "demo_leave_approval:3:deployment-1", "process-role-7", "demo_leave_approval",
                        "task-role-9", "角色审批", List.of(), List.of(11L), "42", "demo_leave:8:1",
                        commandId, operationId, Instant.parse("2026-09-24T08:05:00Z"))));

        JdbcOutboxStore.Lease first = workflowOutbox.claim("workflow", 1).get(0);
        transport.deliver(first.message());

        Long noticeId = jdbc.queryForObject("SELECT id FROM sys_notice", Long.class);
        List<Long> recipientIds = jdbc.queryForList(
                "SELECT user_id FROM sys_user_notice ORDER BY user_id", Long.class);
        assertThat(recipientIds).containsExactly(22L, 33L);
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner = 'upms'",
                String.class)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM sys_notice WHERE id = ?", Long.class, noticeId))
                .isEqualTo(42L);
        verify(sse).publishRefresh(22L, noticeId,
                jdbc.queryForObject("SELECT id FROM sys_user_notice WHERE user_id = 22", Long.class));
        verify(sse).publishRefresh(33L, noticeId,
                jdbc.queryForObject("SELECT id FROM sys_user_notice WHERE user_id = 33", Long.class));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void installReliableTable(String migration) {
        Path migrations = Path.of(System.getProperty("reliable.test.migrations",
                "../../bixi-project-documents/sql/migrations"));
        new ResourceDatabasePopulator(new FileSystemResource(migrations.resolve(migration))).execute(source);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({SysNoticeServiceImpl.class, SysUserNoticeServiceImpl.class, SysUserRoleServiceImpl.class,
            PublishedNoticeNotifier.class, MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.upms.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root",
                    System.getenv("MYSQL_ROOT_PASSWORD"));
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        CandidateIdentityQueryService candidateIdentityQueryService() {
            return userId -> userId != null && (userId == 22L || userId == 33L)
                    ? new CandidateIdentity(userId, true, false, 42L) : null;
        }

        @Bean
        CandidateRoleQueryService candidateRoleQueryService() {
            return roleId -> roleId != null && roleId == 11L
                    ? new CandidateRole(11L, true, 42L) : null;
        }

        @Bean
        SysUserNoticeSseService sseService() {
            return mock(SysUserNoticeSseService.class);
        }

        @Bean
        NoticeDelivery noticeDelivery() {
            return mock(NoticeDelivery.class);
        }

        @Bean
        SysUserService users() {
            return mock(SysUserService.class);
        }

    }
}
