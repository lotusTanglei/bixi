package com.lotus.bixi.upms.service.local;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.exception.ErrorCodes;
import com.lotus.bixi.common.core.util.MsgUtils;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.UserQueryService;
import com.lotus.bixi.upms.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
public class LocalUserQueryService implements UserQueryService {

    private final SysUserService userService;

    @Override
    public R<UserInfo> info(UserDTO userQuery) {
        SysUser user = userService.getOne(Wrappers.<SysUser>query()
                .lambda()
                .eq(StrUtil.isNotBlank(userQuery.getUsername()), SysUser::getUsername, userQuery.getUsername())
                .eq(StrUtil.isNotBlank(userQuery.getPhone()), SysUser::getPhone, userQuery.getPhone()));
        if (user == null) {
            return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_USERINFO_EMPTY, userQuery.getUsername()));
        }
        UserInfo userInfo = userService.findUserInfo(user);
        userInfo.setEncodedPassword(user.getPassword());
        return R.ok(userInfo);
    }

}
