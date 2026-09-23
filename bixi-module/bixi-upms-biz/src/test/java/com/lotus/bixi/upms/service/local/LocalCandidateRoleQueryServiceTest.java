package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.entity.SysRole;
import com.lotus.bixi.upms.service.SysRoleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalCandidateRoleQueryServiceTest {

    private final SysRoleService roleService = mock(SysRoleService.class);
    private final LocalCandidateRoleQueryService service = new LocalCandidateRoleQueryService(roleService);

    @Test
    void localAdapterReturnsActiveRoleState() {
        SysRole role = role(11L, "0", "0", 42L);
        when(roleService.getById(11L)).thenReturn(role);

        assertThat(service.findById(11L)).isEqualTo(new CandidateRole(11L, true, 42L));
    }

    @Test
    void localAdapterFailsClosedForDeletedOrUnknownStatus() {
        when(roleService.getById(12L)).thenReturn(role(12L, "1", "0", 42L));
        when(roleService.getById(13L)).thenReturn(role(13L, "0", "1", 42L));
        when(roleService.getById(14L)).thenReturn(role(14L, null, "0", 42L));

        assertThat(service.findById(12L)).isEqualTo(new CandidateRole(12L, false, 42L));
        assertThat(service.findById(13L)).isNull();
        assertThat(service.findById(14L)).isEqualTo(new CandidateRole(14L, false, 42L));
    }

    private static SysRole role(Long id, String status, String delFlag, Long tenantId) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setStatus(status);
        role.setDelFlag(delFlag);
        role.setTenantId(tenantId);
        return role;
    }
}
