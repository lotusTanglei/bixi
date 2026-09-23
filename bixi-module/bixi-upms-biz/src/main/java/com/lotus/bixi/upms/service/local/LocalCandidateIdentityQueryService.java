package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import com.lotus.bixi.upms.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Single-process adapter backed by the UPMS user service.
 */
@Primary
@Service
@RequiredArgsConstructor
public class LocalCandidateIdentityQueryService implements CandidateIdentityQueryService {

    private final SysUserService userService;

    @Override
    public CandidateIdentity findById(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        SysUser user = userService.getById(userId);
        if (user == null || !"0".equals(user.getDelFlag())) {
            return null;
        }
        return new CandidateIdentity(
                user.getId(),
                "0".equals(user.getStatus()),
                !"0".equals(user.getLockFlag()),
                user.getTenantId());
    }

}
