package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalCandidateIdentityQueryServiceTest {

    @Mock
    SysUserService userService;

    @InjectMocks
    LocalCandidateIdentityQueryService service;

    @Test
    void localAdapterReturnsStatusAndTenantForExistingUser() {
        SysUser user = user(7L, "0", "0", "0", 42L);
        when(userService.getById(7L)).thenReturn(user);

        CandidateIdentity identity = service.findById(7L);

        assertThat(identity).isEqualTo(new CandidateIdentity(7L, true, false, 42L));
    }

    @Test
    void localAdapterDoesNotExposeDeletedOrMissingUsers() {
        SysUser deleted = user(8L, "0", "0", "1", 42L);
        when(userService.getById(8L)).thenReturn(deleted);

        assertThat(service.findById(8L)).isNull();
        assertThat(service.findById(9L)).isNull();
    }

    @Test
    void localAdapterFailsClosedForUnknownDeletionFlags() {
        SysUser missingFlag = user(10L, "0", "0", null, 42L);
        SysUser unexpectedFlag = user(11L, "0", "0", "2", 42L);
        when(userService.getById(10L)).thenReturn(missingFlag);
        when(userService.getById(11L)).thenReturn(unexpectedFlag);

        assertThat(service.findById(10L)).isNull();
        assertThat(service.findById(11L)).isNull();
    }

    private static SysUser user(Long id, String status, String lockFlag, String delFlag, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        user.setLockFlag(lockFlag);
        user.setDelFlag(delFlag);
        user.setTenantId(tenantId);
        return user;
    }

}
