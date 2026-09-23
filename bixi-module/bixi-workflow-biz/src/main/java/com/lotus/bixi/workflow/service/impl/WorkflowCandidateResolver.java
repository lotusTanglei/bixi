package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.identity.WorkflowCandidateGroup;
import org.flowable.bpmn.model.UserTask;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves workflow candidates from server-owned security and UPMS state.
 *
 * <p>The role lookup is deliberately transport-neutral. Cloud supplies a Feign-backed
 * adapter and single supplies a local adapter; this module must not depend on either
 * deployment-specific implementation.</p>
 */
@Service
@ConditionalOnWorkflowEnabled
public final class WorkflowCandidateResolver {

    public static final String INVALID_PREFIX = "工作流候选身份无效:";
    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("[1-9][0-9]*");
    private static final String ROLE_PREFIX = "ROLE_";

    private final CandidateIdentityQueryService identities;
    private final CandidateRoleQueryService roles;

    public WorkflowCandidateResolver(CandidateIdentityQueryService identities, CandidateRoleQueryService roles) {
        this.identities = java.util.Objects.requireNonNull(identities, "identities");
        this.roles = java.util.Objects.requireNonNull(roles, "roles");
    }

    /** Returns canonical role groups represented by trusted ROLE_<positive-id> authorities. */
    public Set<String> effectiveGroups(BixiUser user) {
        if (user == null || user.getAuthorities() == null) {
            return Set.of();
        }
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        user.getAuthorities().forEach(authority -> {
            if (authority == null || authority.getAuthority() == null) {
                return;
            }
            String value = authority.getAuthority();
            if (!value.startsWith(ROLE_PREFIX)) {
                return;
            }
            String digits = value.substring(ROLE_PREFIX.length());
            if (isPositiveDecimal(digits)) {
                groups.add("role:" + Long.parseLong(digits));
            }
        });
        return Collections.unmodifiableSet(groups);
    }

    /** Whether an unassigned task has a valid candidate user or active role candidate. */
    public boolean isCandidate(Task task, BixiUser user) {
        return isCandidate(task, user, task == null ? List.of() : task.getIdentityLinks());
    }

    public boolean isCandidate(Task task, BixiUser user, Collection<? extends IdentityLinkInfo> links) {
        if (task == null || user == null || task.getAssignee() != null || !sameTenant(task, user)) {
            return false;
        }
        CandidateIdentity identity = identities.findById(user.getId());
        if (user.getTenantId() == null || identity == null || !identity.enabled() || identity.locked()
                || !java.util.Objects.equals(identity.userId(), user.getId())
                || !java.util.Objects.equals(identity.tenantId(), user.getTenantId())) {
            return false;
        }
        for (IdentityLinkInfo link : safeLinks(links)) {
            if (link == null) {
                continue;
            }
            if (java.util.Objects.equals(String.valueOf(user.getId()), link.getUserId())) {
                return true;
            }
            String groupId = link.getGroupId();
            if (groupId != null && effectiveGroups(user).contains(groupId)
                    && activeRoleForTenant(groupId, user.getTenantId())) {
                return true;
            }
        }
        return false;
    }

    /** Visibility follows the assignee fence; candidate links are considered only while unassigned. */
    public boolean isVisible(Task task, BixiUser user) {
        return isVisible(task, user, task == null ? List.of() : task.getIdentityLinks());
    }

    public boolean isVisible(Task task, BixiUser user, Collection<? extends IdentityLinkInfo> links) {
        if (task == null || user == null) {
            return false;
        }
        if (task.getAssignee() != null) {
            return java.util.Objects.equals(task.getAssignee(), String.valueOf(user.getId()))
                    && activeIdentity(user)
                    && (task.getTenantId() == null || task.getTenantId().isBlank() || sameTenant(task, user));
        }
        if (!sameTenant(task, user)) return false;
        return isCandidate(task, user, links);
    }

    /** Canonicalizes safe metadata and drops malformed/dynamic values. */
    public SanitizedCandidates sanitize(Collection<String> candidateUsers, Collection<String> candidateGroups) {
        LinkedHashSet<String> users = new LinkedHashSet<>();
        if (candidateUsers != null) {
            for (String value : candidateUsers) {
                if (isPositiveDecimal(value)) {
                    users.add(Long.toString(Long.parseLong(value)));
                }
            }
        }
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        if (candidateGroups != null) {
            for (String value : candidateGroups) {
                try {
                    groups.add(WorkflowCandidateGroup.parse(value).value());
                } catch (IllegalArgumentException ignored) {
                    // Metadata from a deployed definition is untrusted until validated.
                }
            }
        }
        return new SanitizedCandidates(List.copyOf(users), List.copyOf(groups));
    }

    public SanitizedCandidates sanitize(UserTask task) {
        return task == null ? new SanitizedCandidates(List.of(), List.of())
                : sanitize(task.getCandidateUsers(), task.getCandidateGroups());
    }

    /**
     * Validates server-executable UserTask candidate metadata. Every role is checked against
     * the current tenant and active role state before a definition can be deployed.
     */
    public void validate(UserTask task, Long tenantId) {
        if (task == null) {
            invalid("节点为空");
        }
        for (String value : safeValues(task.getCandidateUsers())) {
            if (!isPositiveDecimal(value)) {
                invalid("候选用户必须是正十进制用户ID");
            }
            long userId = parsePositive(value, "候选用户");
            CandidateIdentity identity = identities.findById(userId);
            if (identity == null || !java.util.Objects.equals(identity.userId(), userId)
                    || identity.tenantId() == null || !java.util.Objects.equals(identity.tenantId(), tenantId)) {
                invalid("候选用户不存在或租户不匹配");
            }
        }
        for (String value : safeValues(task.getCandidateGroups())) {
            final WorkflowCandidateGroup group;
            try {
                group = WorkflowCandidateGroup.parse(value);
            } catch (IllegalArgumentException invalidGroup) {
                invalid("候选组格式无效");
                return;
            }
            CandidateRole role = roles.findById(group.roleId());
            if (tenantId == null || role == null || role.roleId() == null || role.roleId() != group.roleId()
                    || !role.active() || role.tenantId() == null || !java.util.Objects.equals(role.tenantId(), tenantId)) {
                invalid("候选组角色不存在、未启用或租户不匹配");
            }
        }
    }

    private boolean activeIdentity(BixiUser user) {
        CandidateIdentity identity = identities.findById(user.getId());
        return user.getTenantId() != null && identity != null && identity.enabled() && !identity.locked()
                && java.util.Objects.equals(identity.userId(), user.getId())
                && identity.tenantId() != null && java.util.Objects.equals(identity.tenantId(), user.getTenantId());
    }

    private boolean activeRoleForTenant(String value, Long tenantId) {
        try {
            WorkflowCandidateGroup group = WorkflowCandidateGroup.parse(value);
            CandidateRole role = roles.findById(group.roleId());
            return tenantId != null && role != null && role.active() && role.roleId() == group.roleId()
                    && role.tenantId() != null && java.util.Objects.equals(role.tenantId(), tenantId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean sameTenant(Task task, BixiUser user) {
        String taskTenant = task.getTenantId();
        return taskTenant != null && !taskTenant.isBlank()
                && user.getTenantId() != null && String.valueOf(user.getTenantId()).equals(taskTenant);
    }

    private static Collection<? extends IdentityLinkInfo> safeLinks(Collection<? extends IdentityLinkInfo> links) {
        return links == null ? List.of() : links;
    }

    private static List<String> safeValues(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static boolean isPositiveDecimal(String value) {
        return value != null && POSITIVE_DECIMAL.matcher(value).matches()
                && parsePositiveOrNull(value) != null;
    }

    private static long parsePositive(String value, String kind) {
        Long parsed = parsePositiveOrNull(value);
        if (parsed == null) {
            invalid(kind + "超出范围");
        }
        return parsed;
    }

    private static Long parsePositiveOrNull(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException(INVALID_PREFIX + detail);
    }

    public record SanitizedCandidates(List<String> candidateUsers, List<String> candidateGroups) {
        public SanitizedCandidates {
            candidateUsers = List.copyOf(candidateUsers == null ? List.of() : candidateUsers);
            candidateGroups = List.copyOf(candidateGroups == null ? List.of() : candidateGroups);
        }
    }

}
