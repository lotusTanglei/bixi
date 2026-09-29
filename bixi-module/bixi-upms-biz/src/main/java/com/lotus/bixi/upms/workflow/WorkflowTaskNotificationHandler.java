package com.lotus.bixi.upms.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.entity.SysUserRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.mq.PublishedNoticeNotifier;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserRoleService;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotificationCodec;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Creates a durable in-app notice from an authenticated workflow inbox message. */
public final class WorkflowTaskNotificationHandler implements DurableMessageHandler {
    private static final int MAX_RESOLVED_RECIPIENTS = 1_000;

    private final WorkflowTaskNotificationCodec codec;
    private final CandidateIdentityQueryService identities;
    private final CandidateRoleQueryService roles;
    private final SysUserRoleService userRoles;
    private final SysNoticeService notices;
    private final PublishedNoticeNotifier notifier;

    public WorkflowTaskNotificationHandler(
            @Qualifier("upmsWorkflowTaskNotificationCodec") WorkflowTaskNotificationCodec codec,
            CandidateIdentityQueryService identities, CandidateRoleQueryService roles,
            SysUserRoleService userRoles, SysNoticeService notices,
            PublishedNoticeNotifier notifier) {
        this.codec = codec;
        this.identities = identities;
        this.roles = roles;
        this.userRoles = userRoles;
        this.notices = notices;
        this.notifier = notifier;
    }

    @Override
    public Result handle(DurableMessage message) {
        WorkflowTaskNotification event = decode(message);
        validateEnvelope(message, event);
        Long previousTenant = TenantContextHolder.get();
        try {
            long tenantId = Long.parseLong(event.tenantScope());
            TenantContextHolder.set(tenantId);
            TreeSet<Long> recipients = resolveRecipients(event, tenantId);
            if (recipients.isEmpty()) {
                return Result.PROCESSED;
            }

            SysNoticeVO notice = new SysNoticeVO();
            notice.setTitle("流程待办：" + event.taskName());
            notice.setContent(content(event));
            notice.setType("0");
            notice.setPriority("1");
            notice.setTargetType("3");
            notice.setTargetIds(recipients.stream().map(String::valueOf).collect(Collectors.joining(",")));
            notice.setRemark("workflow operationId=" + event.operationId());
            if (!notices.savePublishedNotice(notice) || notice.getId() == null) {
                throw new IllegalStateException("工作流任务通知保存失败");
            }
            notifier.notifyRecipientsAfterCommit(notice.getId());
            return Result.PROCESSED;
        } finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private TreeSet<Long> resolveRecipients(WorkflowTaskNotification event, long tenantId) {
        TreeSet<Long> recipients = new TreeSet<>();
        for (Long userId : event.recipientUserIds()) {
            requireRecipient(userId, tenantId);
            recipients.add(userId);
        }
        for (Long roleId : event.recipientRoleIds()) {
            requireRole(roleId, tenantId);
        }
        if (!event.recipientRoleIds().isEmpty()) {
            List<SysUserRole> memberships = userRoles.list(new QueryWrapper<SysUserRole>()
                    .select("user_id")
                    .in("role_id", event.recipientRoleIds())
                    .groupBy("user_id")
                    .orderByAsc("user_id")
                    .last("LIMIT " + (MAX_RESOLVED_RECIPIENTS + 1)));
            if (memberships != null) {
                if (memberships.size() > MAX_RESOLVED_RECIPIENTS) {
                    throw permanent("任务通知收件人数量超限");
                }
                for (SysUserRole membership : memberships) {
                    if (membership != null && eligibleRecipient(membership.getUserId(), tenantId)) {
                        recipients.add(membership.getUserId());
                    }
                }
            }
        }
        return recipients;
    }

    private void requireRecipient(long userId, long tenantId) {
        if (!eligibleRecipient(userId, tenantId)) {
            throw permanent("任务通知收件人不存在、不可用或租户不匹配");
        }
    }

    private boolean eligibleRecipient(Long userId, long tenantId) {
        if (userId == null || userId <= 0) return false;
        CandidateIdentity identity = identities.findById(userId);
        return identity != null && identity.enabled() && !identity.locked()
                && Objects.equals(identity.userId(), userId)
                && Objects.equals(identity.tenantId(), tenantId);
    }

    private void requireRole(long roleId, long tenantId) {
        CandidateRole role = roles.findById(roleId);
        if (role == null || !role.active() || !Objects.equals(role.roleId(), roleId)
                || !Objects.equals(role.tenantId(), tenantId)) {
            throw permanent("任务通知候选角色不存在、不可用或租户不匹配");
        }
    }

    private WorkflowTaskNotification decode(DurableMessage message) {
        try {
            return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException invalid) {
            throw permanent("工作流任务通知载荷无效");
        }
    }

    private static void validateEnvelope(DurableMessage message, WorkflowTaskNotification event) {
        if (!message.eventId().equals(event.eventId())
                || !WorkflowTaskNotification.TYPE.equals(message.type())
                || message.schemaVersion() != event.schemaVersion()
                || !message.sourceOwner().equals(event.sourceOwner())
                || !message.targetOwner().equals(event.targetOwner())) {
            throw permanent("工作流任务通知信封与载荷不一致");
        }
    }

    private static String content(WorkflowTaskNotification event) {
        String business = event.businessKey() == null ? "" : "，业务单号 " + event.businessKey();
        return "您有新的流程待办：" + event.taskName() + "（流程实例 " + event.processInstanceId()
                + "，任务 " + event.taskId() + business + "）。";
    }

    private static InboxDeliveryException permanent(String message) {
        return new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, message);
    }
}
