package com.lotus.bixi.upms.service.local;

import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.entity.SysRole;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import com.lotus.bixi.upms.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Single-process adapter backed by the UPMS role service. */
@Primary
@Service
@RequiredArgsConstructor
public class LocalCandidateRoleQueryService implements CandidateRoleQueryService {
    private final SysRoleService roleService;

    @Override
    public CandidateRole findById(Long roleId) {
        if (roleId == null || roleId <= 0) {
            return null;
        }
        SysRole role = roleService.getById(roleId);
        if (role == null || !"0".equals(role.getDelFlag())) {
            return null;
        }
        return new CandidateRole(role.getId(), "0".equals(role.getStatus()), role.getTenantId());
    }
}
