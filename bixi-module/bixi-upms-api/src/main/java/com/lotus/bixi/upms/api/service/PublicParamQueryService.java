package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.common.core.util.R;

public interface PublicParamQueryService {

    R<String> getByKey(String key);

}
