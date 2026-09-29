package com.lotus.bixi.upms.workflow;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.upms.api.entity.SysUserRole;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.mq.PublishedNoticeNotifier;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserRoleService;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotificationCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTaskNotificationHandlerTest {

    private final CandidateIdentityQueryService identities = mock(CandidateIdentityQueryService.class);
    private final CandidateRoleQueryService roles = mock(CandidateRoleQueryService.class);
    private final SysUserRoleService userRoles = mock(SysUserRoleService.class);
    private final SysNoticeService notices = mock(SysNoticeService.class);
    private final PublishedNoticeNotifier notifier = mock(PublishedNoticeNotifier.class);
    private final WorkflowTaskNotificationCodec codec = new WorkflowTaskNotificationCodec();
    private final WorkflowTaskNotificationHandler handler =
            new WorkflowTaskNotificationHandler(codec, identities, roles, userRoles, notices, notifier);

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void persistsAndPushesTheServerResolvedRecipientInTheInboxTransaction() {
        TenantContextHolder.set(77L);
        when(identities.findById(22L)).thenReturn(new CandidateIdentity(22L, true, false, 42L));
        when(notices.savePublishedNotice(any())).thenAnswer(invocation -> {
            SysNoticeVO value = invocation.getArgument(0);
            value.setId(99L);
            return true;
        });

        assertThat(handler.handle(message())).isEqualTo(DurableMessageHandler.Result.PROCESSED);

        ArgumentCaptor<SysNoticeVO> captured = ArgumentCaptor.forClass(SysNoticeVO.class);
        verify(notices).savePublishedNotice(captured.capture());
        assertThat(captured.getValue().getTargetType()).isEqualTo("3");
        assertThat(captured.getValue().getTargetIds()).isEqualTo("22");
        assertThat(captured.getValue().getContent()).contains("task-9", "process-7");
        verify(notifier).notifyRecipientsAfterCommit(99L);
        assertThat(TenantContextHolder.get()).isEqualTo(77L);
    }

    @Test
    void rejectsARecipientOutsideTheEventTenant() {
        when(identities.findById(22L)).thenReturn(new CandidateIdentity(22L, true, false, 43L));

        assertThatThrownBy(() -> handler.handle(message()))
                .isInstanceOf(InboxDeliveryException.class);
        verify(notices, never()).savePublishedNotice(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesCandidateRolesInsideTheEventTenantAndDeduplicatesUsers() {
        when(roles.findById(11L)).thenReturn(new CandidateRole(11L, true, 42L));
        SysUserRole first = new SysUserRole();
        first.setRoleId(11L);
        first.setUserId(22L);
        SysUserRole second = new SysUserRole();
        second.setRoleId(11L);
        second.setUserId(33L);
        when(userRoles.list(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(identities.findById(22L)).thenReturn(new CandidateIdentity(22L, true, false, 42L));
        when(identities.findById(33L)).thenReturn(new CandidateIdentity(33L, true, false, 42L));
        when(notices.savePublishedNotice(any())).thenAnswer(invocation -> {
            SysNoticeVO value = invocation.getArgument(0);
            value.setId(100L);
            return true;
        });

        assertThat(handler.handle(message(List.of(22L), List.of(11L))))
                .isEqualTo(DurableMessageHandler.Result.PROCESSED);

        ArgumentCaptor<SysNoticeVO> captured = ArgumentCaptor.forClass(SysNoticeVO.class);
        verify(notices).savePublishedNotice(captured.capture());
        assertThat(captured.getValue().getTargetType()).isEqualTo("3");
        assertThat(captured.getValue().getTargetIds()).isEqualTo("22,33");
    }

    @Test
    @SuppressWarnings("unchecked")
    void boundsAndDeduplicatesTheCandidateRoleMembershipQuery() {
        when(roles.findById(11L)).thenReturn(new CandidateRole(11L, true, 42L));
        when(userRoles.list(any(Wrapper.class))).thenReturn(List.of());

        assertThat(handler.handle(message(List.of(), List.of(11L))))
                .isEqualTo(DurableMessageHandler.Result.PROCESSED);

        ArgumentCaptor<Wrapper<SysUserRole>> captured = ArgumentCaptor.forClass(Wrapper.class);
        verify(userRoles).list(captured.capture());
        QueryWrapper<SysUserRole> query = (QueryWrapper<SysUserRole>) captured.getValue();
        assertThat(query.getSqlSelect()).isEqualTo("user_id");
        assertThat(query.getCustomSqlSegment())
                .contains("GROUP BY user_id", "ORDER BY user_id ASC", "LIMIT 1001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAnOverBroadCandidateRoleBeforePerUserResolution() {
        when(roles.findById(11L)).thenReturn(new CandidateRole(11L, true, 42L));
        List<SysUserRole> memberships = LongStream.rangeClosed(1, 1_001).mapToObj(userId -> {
            SysUserRole membership = new SysUserRole();
            membership.setRoleId(11L);
            membership.setUserId(userId);
            return membership;
        }).toList();
        when(userRoles.list(any(Wrapper.class))).thenReturn(memberships);

        assertThatThrownBy(() -> handler.handle(message(List.of(), List.of(11L))))
                .isInstanceOf(InboxDeliveryException.class)
                .hasMessageContaining("数量超限");
        verify(identities, never()).findById(any());
        verify(notices, never()).savePublishedNotice(any());
    }

    @Test
    void rejectsACandidateRoleOutsideTheEventTenant() {
        when(roles.findById(11L)).thenReturn(new CandidateRole(11L, true, 43L));

        assertThatThrownBy(() -> handler.handle(message(List.of(), List.of(11L))))
                .isInstanceOf(InboxDeliveryException.class);
        verify(notices, never()).savePublishedNotice(any());
    }

    private DurableMessage message() {
        return message(List.of(22L), List.of());
    }

    private DurableMessage message(List<Long> users, List<Long> roles) {
        WorkflowTaskNotification event = new WorkflowTaskNotification(
                "11111111-1111-1111-1111-111111111111", 2, "workflow", "upms", "42",
                "demo_leave_approval:3:deployment-1", "process-7", "demo_leave_approval",
                "task-9", "请假审批", users, roles, "leave:7:1",
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
                Instant.parse("2026-09-24T08:00:00Z"));
        String json = new String(codec.encode(event), StandardCharsets.UTF_8);
        return DurableMessage.create("workflow", "upms", event.eventId(), WorkflowTaskNotification.TYPE, 2, json);
    }
}
