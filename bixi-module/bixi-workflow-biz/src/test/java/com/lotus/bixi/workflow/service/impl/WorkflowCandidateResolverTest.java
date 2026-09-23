package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import org.flowable.bpmn.model.UserTask;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowCandidateResolverTest {

    private final CandidateIdentityQueryService identities = userId -> userId == 7L
            ? new CandidateIdentity(7L, true, false, 42L)
            : userId == 8L ? new CandidateIdentity(8L, true, false, 43L) : null;
    private final CandidateRoleQueryService roles = id -> id == 11L ? new CandidateRole(11L, true, 42L)
            : id == 12L ? new CandidateRole(12L, false, 42L) : null;
    private final WorkflowCandidateResolver resolver = new WorkflowCandidateResolver(identities, roles);

    @Test
    void effectiveGroupsUsesOnlyPositiveCanonicalRoleAuthoritiesAndDeduplicates() {
        BixiUser user = user(7L, 42L,
                new SimpleGrantedAuthority("ROLE_11"),
                new SimpleGrantedAuthority("ROLE_11"),
                new SimpleGrantedAuthority("ROLE_01"),
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_0"));

        assertThat(resolver.effectiveGroups(user)).containsExactly("role:11");
    }

    @Test
    void candidateUserAndRoleMemberAreVisibleOnlyForActiveSameTenantIdentity() {
        Task task = mock(Task.class);
        when(task.getAssignee()).thenReturn(null);
        when(task.getTenantId()).thenReturn("42");
        IdentityLinkInfo userLink = link("7", null);
        IdentityLinkInfo groupLink = link(null, "role:11");
        when(task.getIdentityLinks()).thenAnswer(invocation -> List.of(userLink, groupLink));
        BixiUser user = user(7L, 42L, new SimpleGrantedAuthority("ROLE_11"));

        assertThat(resolver.isCandidate(task, user)).isTrue();
        assertThat(resolver.isVisible(task, user)).isTrue();

        when(task.getTenantId()).thenReturn("43");
        assertThat(resolver.isCandidate(task, user)).isFalse();
        assertThat(resolver.isVisible(task, user)).isFalse();
    }

    @Test
    void assignedTaskIsVisibleOnlyToAssignee() {
        Task task = mock(Task.class);
        when(task.getAssignee()).thenReturn("99");
        when(task.getTenantId()).thenReturn("42");
        when(task.getIdentityLinks()).thenAnswer(invocation -> List.of(link(null, "role:11")));

        assertThat(resolver.isVisible(task, user(7L, 42L, new SimpleGrantedAuthority("ROLE_11")))).isFalse();
        when(task.getAssignee()).thenReturn("7");
        assertThat(resolver.isVisible(task, user(7L, 42L, new SimpleGrantedAuthority("ROLE_11")))).isTrue();
    }

    @Test
    void metadataIsCanonicalAndMalformedEntriesAreDiscarded() {
        WorkflowCandidateResolver.SanitizedCandidates sanitized = resolver.sanitize(
                List.of("7", "007", "${userId}", "-1", "7"),
                List.of("role:11", "role:011", "role:${roleId}", "role:11"));

        assertThat(sanitized.candidateUsers()).containsExactly("7");
        assertThat(sanitized.candidateGroups()).containsExactly("role:11");
    }

    @Test
    void bpmUserTaskValidationRejectsExpressionsUnknownRolesAndTenantMismatch() {
        UserTask task = new UserTask();
        task.setCandidateUsers(List.of("${approverId}"));
        assertInvalid(task, 42L);

        task.setCandidateUsers(List.of("7"));
        task.setCandidateGroups(List.of("role:99"));
        assertInvalid(task, 42L);

        task.setCandidateGroups(List.of());
        task.setCandidateUsers(List.of("99"));
        assertInvalid(task, 42L);

        task.setCandidateUsers(List.of("8"));
        assertInvalid(task, 42L);

        task.setCandidateGroups(List.of("role:11"));
        assertThatThrownBy(() -> resolver.validate(task, 43L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);

        assertInvalid(task, null);

        task.setCandidateGroups(List.of("role:12"));
        assertInvalid(task, 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "01", "9223372036854775808", "${userId}"})
    void bpmUserTaskValidationRejectsNonPositiveOrDynamicUsers(String value) {
        UserTask task = new UserTask();
        task.setCandidateUsers(List.of(value));
        assertInvalid(task, 42L);
    }

    private void assertInvalid(UserTask task, Long tenantId) {
        assertThatThrownBy(() -> resolver.validate(task, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(WorkflowCandidateResolver.INVALID_PREFIX);
    }

    private static IdentityLinkInfo link(String userId, String groupId) {
        IdentityLinkInfo link = mock(IdentityLinkInfo.class);
        when(link.getUserId()).thenReturn(userId);
        when(link.getGroupId()).thenReturn(groupId);
        return link;
    }

    private static BixiUser user(Long id, Long tenantId, SimpleGrantedAuthority... authorities) {
        return new BixiUser(id, 1L, tenantId, "user-" + id, "unused", null,
                true, true, true, true, new ArrayList<>(List.of(authorities)));
    }
}
