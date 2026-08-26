package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.service.PublicParamQueryService;
import com.lotus.bixi.upms.service.SysPublicParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
public class LocalPublicParamQueryService implements PublicParamQueryService {

    private final SysPublicParamService publicParamService;

    @Override
    public R<String> getByKey(String key) {
        return R.ok(publicParamService.getSysPublicParamKeyToValue(key));
    }

}
