package com.lotus.bixi.upms.api.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;

import java.util.Map;

public interface TokenManagementService {

    R<Page> getTokenPage(Map<String, Object> params);

    R<Boolean> removeTokenById(String token);

    R<Map<String, Object>> queryToken(String token);

}
