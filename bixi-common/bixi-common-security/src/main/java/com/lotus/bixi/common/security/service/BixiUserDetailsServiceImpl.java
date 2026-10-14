

package com.lotus.bixi.common.security.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 用户详细信息
 *
 * @author 唐磊 hccake
 */
@Slf4j
@Primary
@RequiredArgsConstructor
public class BixiUserDetailsServiceImpl implements BixiUserDetailsService {

    private final UserQueryService userQueryService;

    /**
     * 用户名密码登录
     *
     * @param username 用户名
     * @return
     */
    @Override
    @SneakyThrows
    public UserDetails loadUserByUsername(String username) {
        // A pre-commit read can finish after an account deletion evicts its cache entry.
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(username);
        R<UserInfo> result = userQueryService.info(userDTO);
        return getUserDetails(result);
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

}
