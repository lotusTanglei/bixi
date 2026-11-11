package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class SysUserServiceUsernameTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankUsernameBeforeCreatingUser(String username) {
        UserDTO user = new UserDTO();
        user.setUsername(username);

        assertThatThrownBy(() -> mock(SysUserServiceImpl.class, CALLS_REAL_METHODS).saveUser(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能为空");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsBlankUsernameBeforeUpdatingUser(String username) {
        UserDTO user = new UserDTO();
        user.setUsername(username);

        assertThatThrownBy(() -> mock(SysUserServiceImpl.class, CALLS_REAL_METHODS).updateUser(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能为空");
    }

    @Test
    void rejectsUsernameChangesBeforeUpdatingUser() {
        SysUserServiceImpl service = mock(SysUserServiceImpl.class, CALLS_REAL_METHODS);
        SysUser current = new SysUser();
        current.setUsername("original");
        doReturn(current).when(service).getById(42L);
        UserDTO user = new UserDTO();
        user.setId(42L);
        user.setUsername("renamed");

        assertThatThrownBy(() -> service.updateUser(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名不能修改");
    }
}
