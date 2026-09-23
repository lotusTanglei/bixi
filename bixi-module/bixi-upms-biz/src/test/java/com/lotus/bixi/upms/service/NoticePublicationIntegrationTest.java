package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import com.lotus.bixi.upms.controller.SysNoticeController;
import com.lotus.bixi.upms.controller.SysUserNoticeController;
import com.lotus.bixi.upms.mq.NoticeConsumer;
import com.lotus.bixi.upms.service.impl.SysNoticeServiceImpl;
import com.lotus.bixi.upms.service.impl.SysUserNoticeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

/** Real secured controllers, MyBatis XML, transactions and canonical tables; no external delivery. */
@SpringJUnitConfig(NoticePublicationIntegrationTest.Config.class)
@TestPropertySource(properties = "mybatis-plus.global-config.banner=false")
class NoticePublicationIntegrationTest {
    @Autowired SysNoticeService notices;
    @Autowired SysUserNoticeService inbox;
    @Autowired SysNoticeController management;
    @Autowired SysUserNoticeController personal;
    @Autowired NoticeConsumer consumer;
    @Autowired RabbitTemplate rabbit;
    @Autowired SysUserNoticeSseService sse;
    @Autowired DataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.set(1L);
        jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"sys_notice", "sys_user_notice", "sys_user"}) {
            recreateCanonicalTable(table);
        }
        jdbc.update("INSERT INTO sys_user(id,username,nickname) VALUES (11,'author','作者'),(22,'recipient','收件人'),(33,'other','其他人')");
        reset(rabbit, sse);
        login(11L, "sys_notice_add", "sys_notice_edit");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void authorCannotPublishBySupplyingStatusOnCreateOrUpdate() {
        var draft = request();
        draft.setStatus("1");
        assertThat(management.save(draft).getData()).isEqualTo(true);
        assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo("0");

        draft.setStatus("1");
        draft.setContent("修订后的草稿");
        assertThat(management.updateById(draft).getData()).isEqualTo(true);
        assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo("0");
        assertThatThrownBy(() -> management.send(draft.getId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(rabbit, sse);

        login(22L);
        assertHidden(draft.getId(), recipientId(draft.getId()));
    }

    @Test
    void createAlwaysAttributesSenderToAuthenticatedAuthor() {
        var draft = request();
        draft.setSenderId(33L);
        assertThat(management.save(draft).getData()).isEqualTo(true);
        assertThat(notices.getById(draft.getId()).getSenderId()).isEqualTo(11L);
    }

    @Test
    void editingDraftCannotChangeItsOriginalSender() {
        var draft = savedDraft();
        login(33L, "sys_notice_edit");
        draft.setSenderId(22L);
        draft.setContent("合法编辑正文，但不能冒充发送人");
        assertThat(management.updateById(draft).getData()).isEqualTo(true);
        assertThat(notices.getById(draft.getId()).getSenderId()).isEqualTo(11L);
    }

    @Test
    void onlyExplicitSendMakesDraftVisibleToItsRecipient() {
        var draft = savedDraft();
        long recipientId = recipientId(draft.getId());
        login(22L);
        assertHidden(draft.getId(), recipientId);

        login(11L, "sys_notice_view");
        var records = (IPage<?>) personal.getNoticeRecordPage(new Page<>(1, 10), query(draft.getId())).getData();
        assertThat(records.getTotal()).isEqualTo(1);

        login(11L, "sys_notice_send");
        assertThat(management.send(draft.getId()).getData()).isEqualTo(true);
        verify(rabbit).convertAndSend(eq(MQConstants.SYS_NOTICE_FANOUT_EXCHANGE), eq(""), any(NoticeMessageDTO.class));
        consumer.handleNoticeMessage(event(draft.getId()));
        verify(sse).publishRefresh(22L, draft.getId(), recipientId);

        login(22L);
        assertThat(personal.getById(recipientId).getData()).isInstanceOf(UserNoticeVO.class);
        var page = personalPage(draft.getId(), "0");
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).extracting(UserNoticeVO::getContent).containsExactly("尚未发布的正文");

        login(33L);
        assertThat(personalPage(draft.getId(), null).getRecords()).isEmpty();
        assertThat(personal.getById(recipientId).getCode()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2", "deleted-notice", "deleted-recipient"})
    void hiddenStatesNeverLeakThroughPersonalListDetailOrUnreadCount(String state) {
        var draft = savedDraft();
        assertThat(notices.sendNotice(draft.getId())).isTrue();
        long recipientId = recipientId(draft.getId());
        if (state.equals("deleted-notice")) {
            assertThat(notices.removeById(draft.getId())).isTrue();
        } else if (state.equals("deleted-recipient")) {
            jdbc.update("UPDATE sys_user_notice SET del_flag='1' WHERE id=?", recipientId);
        } else {
            jdbc.update("UPDATE sys_notice SET status=? WHERE id=?", state, draft.getId());
        }
        login(22L);
        assertHidden(draft.getId(), recipientId);
    }

    @Test
    void sentContentCannotBeEditedAndReminderStillRequiresSendPermission() {
        var draft = savedDraft();
        assertThat(notices.sendNotice(draft.getId())).isTrue();
        draft.setStatus("0");
        draft.setContent("企图绕过发送权限修改已发布正文");
        draft.setTargetIds("33");
        assertThat(management.updateById(draft).getCode()).isEqualTo(1);
        assertThat(notices.getById(draft.getId()).getContent()).isEqualTo("尚未发布的正文");
        assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo("1");
        assertThat(jdbc.queryForList("SELECT user_id FROM sys_user_notice WHERE notice_id=?", Long.class, draft.getId()))
                .containsExactly(22L);
        assertThatThrownBy(() -> management.send(draft.getId())).isInstanceOf(AccessDeniedException.class);
        verify(rabbit, times(1)).convertAndSend(eq(MQConstants.SYS_NOTICE_FANOUT_EXCHANGE), eq(""), any(NoticeMessageDTO.class));
    }

    @Test
    void failedRealtimeDeliveryCanBeRetriedWithoutDuplicatingPublishedNoticeOrRecipients() {
        var draft = savedDraft();
        long recipientId = recipientId(draft.getId());
        login(33L, "sys_notice_send");
        doThrow(new AmqpException("broker unavailable")).doNothing().when(rabbit)
                .convertAndSend(eq(MQConstants.SYS_NOTICE_FANOUT_EXCHANGE), eq(""), any(NoticeMessageDTO.class));

        assertThatThrownBy(() -> management.send(draft.getId())).isInstanceOf(AmqpException.class);
        assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo("1");
        assertThat(inbox.markRead(recipientId, 22L)).isTrue();

        assertThat(management.send(draft.getId()).getData()).isEqualTo(true);
        assertThat(notices.getById(draft.getId()).getSenderId()).isEqualTo(11L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_notice", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM sys_user_notice WHERE notice_id=?", Long.class, draft.getId()))
                .containsExactly(recipientId);
        assertThat(jdbc.queryForObject("SELECT is_read FROM sys_user_notice WHERE id=?", String.class, recipientId)).isEqualTo("1");
        verify(rabbit, times(2)).convertAndSend(eq(MQConstants.SYS_NOTICE_FANOUT_EXCHANGE), eq(""), any(NoticeMessageDTO.class));
        consumer.handleNoticeMessage(event(draft.getId()));
        verify(sse).publishRefresh(22L, draft.getId(), recipientId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "deleted"})
    void withdrawnOrDeletedNoticeCannotBeSentAgain(String state) {
        var draft = savedDraft();
        if (state.equals("deleted")) {
            notices.removeById(draft.getId());
        } else {
            jdbc.update("UPDATE sys_notice SET status=? WHERE id=?", state, draft.getId());
        }
        login(33L, "sys_notice_send");
        assertThat(management.send(draft.getId()).getCode()).isEqualTo(1);
        verifyNoInteractions(rabbit, sse);
    }

    @Test
    void personalBulkActionsDoNotReadOrDeleteFutureDraftRecipients() {
        var draft = savedDraft();
        long draftRecipient = recipientId(draft.getId());
        login(22L);
        assertThat(inbox.markRead(draftRecipient, 22L)).isFalse();
        assertThat(inbox.deleteOne(draftRecipient, 22L)).isFalse();
        assertThat(inbox.markAllRead(22L)).isZero();
        assertThat(inbox.deleteAll(22L)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_read FROM sys_user_notice WHERE id=?", String.class, draftRecipient)).isEqualTo("0");

        assertThat(notices.sendNotice(draft.getId())).isTrue();
        assertThat(inbox.markAllRead(22L)).isEqualTo(1);
        assertThat(inbox.deleteAll(22L)).isEqualTo(1);
        assertHidden(draft.getId(), draftRecipient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2", "deleted"})
    void delayedMqDeliveryCannotPublishDraftOrReviveWithdrawnAndDeletedNotice(String state) {
        var draft = savedDraft();
        if (state.equals("deleted")) {
            notices.removeById(draft.getId());
        } else {
            jdbc.update("UPDATE sys_notice SET status=? WHERE id=?", state, draft.getId());
        }
        consumer.handleNoticeMessage(event(draft.getId()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_notice", Long.class)).isEqualTo(1);
        if (!state.equals("deleted")) {
            assertThat(notices.getById(draft.getId()).getStatus()).isEqualTo(state);
        }
        verifyNoInteractions(rabbit, sse);
    }

    @Test
    void trustedNewMqNoticeIsPublishedOnceWithoutRecursivelySendingMq() {
        var message = event(null);
        message.setSenderId(33L);
        consumer.handleNoticeMessage(message);
        var notice = notices.list().get(0);
        assertThat(notice.getStatus()).isEqualTo("1");
        assertThat(notice.getSenderId()).isEqualTo(33L);
        login(22L);
        assertThat(personalPage(notice.getId(), "0").getTotal()).isEqualTo(1);
        verify(sse).publishRefresh(22L, notice.getId(), recipientId(notice.getId()));
        verifyNoInteractions(rabbit);
    }

    private void assertHidden(Long noticeId, long recipientId) {
        assertThat(personalPage(noticeId, null).getRecords()).isEmpty();
        assertThat(personalPage(noticeId, "0").getTotal()).isZero();
        assertThat(personal.getById(recipientId).getCode()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private IPage<UserNoticeVO> personalPage(Long noticeId, String isRead) {
        var query = query(noticeId);
        query.setUserId(11L); // A client-supplied owner must never control the personal query.
        query.setIsRead(isRead);
        return (IPage<UserNoticeVO>) personal.getSysUserNoticePage(new Page<>(1, 10), query).getData();
    }

    private static UserNoticeVO query(Long noticeId) {
        var query = new UserNoticeVO();
        query.setNoticeId(noticeId);
        return query;
    }

    private SysNoticeVO savedDraft() {
        var draft = request();
        assertThat(management.save(draft).getData()).isEqualTo(true);
        return draft;
    }

    private static SysNoticeVO request() {
        var draft = new SysNoticeVO();
        draft.setTitle("发布权限测试");
        draft.setContent("尚未发布的正文");
        draft.setType("0");
        draft.setSenderId(11L);
        draft.setTargetType("3");
        draft.setTargetIds("22");
        return draft;
    }

    private static NoticeMessageDTO event(Long noticeId) {
        var message = new NoticeMessageDTO();
        message.setNoticeId(noticeId);
        message.setTitle("系统通知");
        message.setContent("可信系统内容");
        message.setSenderId(11L);
        message.setTargetType("3");
        message.setTargetIds("22");
        return message;
    }

    private long recipientId(Long noticeId) {
        return jdbc.queryForObject("SELECT id FROM sys_user_notice WHERE notice_id=? AND user_id=22", Long.class, noticeId);
    }

    private void recreateCanonicalTable(String table) throws Exception {
        String schema = new ClassPathResource("sql/01_init_all_tables.sql").getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("CREATE TABLE `" + table + "` \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(schema);
        assertThat(matcher.find()).as("canonical schema for %s", table).isTrue();
        String ddl = matcher.group().replaceFirst("\\) ENGINE[^;]*;", ")")
                .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+ COLLATE [a-zA-Z0-9_]+", "")
                .replace(" USING BTREE", "");
        jdbc.execute("DROP TABLE IF EXISTS " + table);
        jdbc.execute(ddl);
    }

    private static void login(long id, String... permissions) {
        var authorities = Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        var user = new BixiUser(id, 1L, 1L, "user-" + id, "unused", null, true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableMethodSecurity
    @Import({SysNoticeServiceImpl.class, SysUserNoticeServiceImpl.class, SysNoticeController.class,
            SysUserNoticeController.class, NoticeConsumer.class, MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.upms.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:notice-publication-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean RabbitTemplate rabbitTemplate() { return mock(RabbitTemplate.class); }
        @Bean SysUserNoticeSseService sseService() { return mock(SysUserNoticeSseService.class); }
        @Bean SysUserService users() { return mock(SysUserService.class); }
        @Bean SysUserRoleService roles() { return mock(SysUserRoleService.class); }
        @Bean("pms") PermissionService permissions() { return new PermissionService(); }
        @Bean static PrePostTemplateDefaults defaults() { return new PrePostTemplateDefaults(); }
    }
}
