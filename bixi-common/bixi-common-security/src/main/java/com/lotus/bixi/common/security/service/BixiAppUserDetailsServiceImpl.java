

package com.lotus.bixi.common.security.service;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 用户详细信息
 *
 * @author 唐磊 hccake
 */
@Slf4j
@RequiredArgsConstructor
public class BixiAppUserDetailsServiceImpl implements BixiUserDetailsService {

    private final UserQueryService userQueryService;

    private final BixiUserDetailsServiceImpl usernameUserDetailsService;

    /**
     * 手机号登录
     *
     * @param phone 手机号
     * @return
     */
    @Override
    @SneakyThrows
    public UserDetails loadUserByUsername(String phone) {
        UserDTO userDTO = new UserDTO();
        userDTO.setPhone(phone);
        R<UserInfo> result = userQueryService.info(userDTO);
        return getUserDetails(result);
    }

    /**
     * check-token 使用
     *
     * @param bixiUser user
     * @return
     */
    @Override
    public UserDetails loadUserByUser(BixiUser bixiUser) {
        return usernameUserDetailsService.loadUserByUser(bixiUser);
    }

    /**
     * 是否支持此客户端校验
     *
     * @param clientId 目标客户端
     * @return true/false
     */
    @Override
    public boolean support(String clientId, String grantType) {
        return SecurityConstants.MOBILE.equals(grantType);
    }

}
