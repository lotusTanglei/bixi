package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.entity.SysFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SysFileServiceImplAuthorizationTest {

    @Mock
    PermissionService permissionService;

    @InjectMocks
    SysFileServiceImpl service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void fileOwnerCanDownloadTheirPrivateFile() {
        TenantContextHolder.set(3L);
        authenticate(7L, 3L);
        SysFile file = file(7L);

        assertThat(service.isDownloadAuthorized(file)).isTrue();
    }

    @Test
    void anotherUserCannotDownloadPrivateFileWithoutPermission() {
        TenantContextHolder.set(3L);
        authenticate(8L, 3L);
        SysFile file = file(7L);

        assertThat(service.isDownloadAuthorized(file)).isFalse();
    }

    @Test
    void fileViewerPermissionAllowsTenantLocalDownload() {
        TenantContextHolder.set(3L);
        authenticate(8L, 3L);
        SysFile file = file(7L);
        org.mockito.Mockito.when(permissionService.hasPermission("sys_file_view", "sys_file_del")).thenReturn(true);

        assertThat(service.isDownloadAuthorized(file)).isTrue();
    }

    private SysFile file(Long ownerId) {
        SysFile file = new SysFile();
        file.setCreateBy(ownerId);
        file.setTenantId(3L);
        return file;
    }

    private void authenticate(Long userId, Long tenantId) {
        BixiUser user = new BixiUser(userId, 1L, tenantId, "user-" + userId, "password", null,
                true, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
