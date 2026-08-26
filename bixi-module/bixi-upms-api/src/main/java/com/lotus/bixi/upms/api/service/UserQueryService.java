package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;

public interface UserQueryService {

    R<UserInfo> info(UserDTO user);

}
