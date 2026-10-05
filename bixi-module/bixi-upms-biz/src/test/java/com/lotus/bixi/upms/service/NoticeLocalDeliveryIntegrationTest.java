package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.mq.LocalNoticeDelivery;
import com.lotus.bixi.upms.mq.PublishedNoticeNotifier;
import com.lotus.bixi.upms.service.impl.SysNoticeServiceImpl;
import com.lotus.bixi.upms.service.impl.SysUserNoticeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(NoticeLocalDeliveryIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "bixi.deployment.mode=single",
        "mybatis-plus.global-config.banner=false"
})
class NoticeLocalDeliveryIntegrationTest {

    @Autowired
    SysNoticeService notices;

    @Autowired
    SysUserNoticeSseService sse;

    @Autowired
    ApplicationContext context;

    @Autowired
    DataSource source;

    @Autowired
    SysUserNoticeMapper userNoticeMapper;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.set(1L);
        login(11L);
        jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"sys_notice", "sys_user_notice", "sys_user"}) {
            recreateCanonicalTable(table);
        }
        jdbc.update("INSERT INTO sys_user(id,tenant_id,username,nickname) VALUES "
                + "(11,1,'author','作者'),(22,1,'recipient','收件人')");
        reset(sse);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void singlePublishesAndRefreshesRecipientWithoutRabbit() {
        assertThat(context.getBeansOfType(org.springframework.amqp.rabbit.core.RabbitTemplate.class)).isEmpty();

        SysNoticeVO draft = new SysNoticeVO();
        draft.setTitle("single 本地通知");
        draft.setContent("无需 RabbitMQ");
        draft.setType("0");
        draft.setTargetType("3");
        draft.setTargetIds("22");
        assertThat(notices.saveNotice(draft)).isTrue();

        Long recipientRowId = jdbc.queryForObject(
                "SELECT id FROM sys_user_notice WHERE notice_id=? AND user_id=22", Long.class, draft.getId());

        assertThat(notices.sendNotice(draft.getId())).isTrue();
        assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo("1");
        assertThat(jdbc.queryForList("SELECT id FROM sys_user_notice WHERE notice_id=?", Long.class, draft.getId()))
                .containsExactly(recipientRowId);
        verify(sse).publishRefresh(22L, draft.getId(), recipientRowId);
        assertThat(jdbc.queryForMap("SELECT delivery_status, delivery_attempts FROM sys_user_notice"
                + " WHERE id=?", recipientRowId))
                .containsEntry("DELIVERY_STATUS", "DELIVERED")
                .containsEntry("DELIVERY_ATTEMPTS", 1);
    }

    @Test
    void failedLocalRefreshLeavesAReplayableDeliveryFailure() {
        SysNoticeVO draft = new SysNoticeVO();
        draft.setTitle("失败可恢复通知");
        draft.setContent("SSE 会失败");
        draft.setType("0");
        draft.setTargetType("3");
        draft.setTargetIds("22");
        assertThat(notices.saveNotice(draft)).isTrue();
        Long recipientRowId = jdbc.queryForObject(
                "SELECT id FROM sys_user_notice WHERE notice_id=? AND user_id=22", Long.class, draft.getId());
        doThrow(new IllegalStateException("sse unavailable"))
                .when(sse).publishRefresh(22L, draft.getId(), recipientRowId);

        assertThatThrownBy(() -> notices.sendNotice(draft.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sse unavailable");
        assertThat(jdbc.queryForMap("SELECT delivery_status, delivery_attempts, delivery_last_error"
                + " FROM sys_user_notice WHERE id=?", recipientRowId))
                .containsEntry("DELIVERY_STATUS", "FAILED")
                .containsEntry("DELIVERY_ATTEMPTS", 1)
                .containsEntry("DELIVERY_LAST_ERROR", "sse unavailable");
    }

    @Test
    void duplicateLocalDeliveryDoesNotSendAnAlreadyDeliveredRecipientAgain() {
        SysNoticeVO draft = new SysNoticeVO();
        draft.setTitle("幂等通知");
        draft.setContent("只刷新一次");
        draft.setType("0");
        draft.setTargetType("3");
        draft.setTargetIds("22");
        assertThat(notices.saveNotice(draft)).isTrue();

        assertThat(notices.sendNotice(draft.getId())).isTrue();
        assertThat(notices.sendNotice(draft.getId())).isTrue();

        Long recipientRowId = jdbc.queryForObject(
                "SELECT id FROM sys_user_notice WHERE notice_id=? AND user_id=22", Long.class, draft.getId());
        verify(sse, times(1)).publishRefresh(22L, draft.getId(), recipientRowId);
        assertThat(jdbc.queryForObject("SELECT delivery_attempts FROM sys_user_notice WHERE id=?",
                Integer.class, recipientRowId)).isEqualTo(1);
    }

    @Test
    void freshInFlightDeliveryIsLeftForItsCurrentWorker() {
        SysNoticeVO draft = savedNotice();
        Long recipientRowId = recipientId(draft.getId());

        assertThat(userNoticeMapper.claimDelivery(recipientRowId,
                LocalDateTime.now().minusMinutes(5), 0)).isEqualTo(1);
        reset(sse);

        assertThat(notices.sendNotice(draft.getId())).isTrue();
        verifyNoInteractions(sse);
        assertThat(jdbc.queryForMap("SELECT delivery_status, delivery_attempts FROM sys_user_notice"
                + " WHERE id=?", recipientRowId))
                .containsEntry("DELIVERY_STATUS", "IN_FLIGHT")
                .containsEntry("DELIVERY_ATTEMPTS", 1);
    }

    @Test
    void staleInFlightDeliveryIsReclaimedAndLateWorkerCannotCompleteIt() {
        SysNoticeVO draft = savedNotice();
        Long recipientRowId = recipientId(draft.getId());

        assertThat(userNoticeMapper.claimDelivery(recipientRowId,
                LocalDateTime.now().minusMinutes(5), 0)).isEqualTo(1);
        jdbc.update("UPDATE sys_user_notice SET delivery_last_attempt_at=? WHERE id=?",
                LocalDateTime.of(2000, 1, 1, 0, 0), recipientRowId);

        assertThat(notices.sendNotice(draft.getId())).isTrue();
        verify(sse).publishRefresh(22L, draft.getId(), recipientRowId);
        assertThat(jdbc.queryForMap("SELECT delivery_status, delivery_attempts FROM sys_user_notice"
                + " WHERE id=?", recipientRowId))
                .containsEntry("DELIVERY_STATUS", "DELIVERED")
                .containsEntry("DELIVERY_ATTEMPTS", 2);

        // A worker from attempt 1 must not be able to complete attempt 2.
        assertThat(userNoticeMapper.markDeliveryDelivered(recipientRowId, 1)).isZero();
    }

    private SysNoticeVO savedNotice() {
        SysNoticeVO draft = new SysNoticeVO();
        draft.setTitle("lease recovery notice");
        draft.setContent("recover after worker crash");
        draft.setType("0");
        draft.setTargetType("3");
        draft.setTargetIds("22");
        assertThat(notices.saveNotice(draft)).isTrue();
        return draft;
    }

    private Long recipientId(Long noticeId) {
        return jdbc.queryForObject("SELECT id FROM sys_user_notice WHERE notice_id=? AND user_id=22",
                Long.class, noticeId);
    }

    private void recreateCanonicalTable(String table) throws Exception {
        String schema = new ClassPathResource("sql/01_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("CREATE TABLE `" + table + "` \\([\\s\\S]*?\\) ENGINE[^;]*;")
                .matcher(schema);
        assertThat(matcher.find()).as("canonical schema for %s", table).isTrue();
        String ddl = matcher.group().replaceFirst("\\) ENGINE[^;]*;", ")")
                .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+ COLLATE [a-zA-Z0-9_]+", "")
                .replace(" USING BTREE", "");
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute(ddl);
    }

    private static void login(long id) {
        BixiUser user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null,
                true, true, true, true, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({SysNoticeServiceImpl.class, SysUserNoticeServiceImpl.class,
            PublishedNoticeNotifier.class, LocalNoticeDelivery.class, MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.upms.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:notice-local-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        SysUserNoticeSseService sseService() {
            return mock(SysUserNoticeSseService.class);
        }

        @Bean
        SysUserService users() {
            return mock(SysUserService.class);
        }

        @Bean
        SysUserRoleService roles() {
            return mock(SysUserRoleService.class);
        }
    }
}
