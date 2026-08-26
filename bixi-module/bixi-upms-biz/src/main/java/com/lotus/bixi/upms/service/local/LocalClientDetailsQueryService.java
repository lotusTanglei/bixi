package com.lotus.bixi.upms.service.local;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.api.service.ClientDetailsQueryService;
import com.lotus.bixi.upms.service.SysOauthClientDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
@RequiredArgsConstructor
public class LocalClientDetailsQueryService implements ClientDetailsQueryService {

    private final SysOauthClientDetailsService clientDetailsService;

    @Override
    public R<SysOauthClientDetails> getClientDetailsById(String clientId) {
        return R.ok(clientDetailsService.getOne(Wrappers.<SysOauthClientDetails>lambdaQuery()
                .eq(SysOauthClientDetails::getClientId, clientId), false));
    }

}
