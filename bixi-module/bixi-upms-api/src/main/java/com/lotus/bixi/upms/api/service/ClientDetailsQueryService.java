package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;

public interface ClientDetailsQueryService {

    R<SysOauthClientDetails> getClientDetailsById(String clientId);

}
