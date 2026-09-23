package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, read-only candidate role projection. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/upms/candidate-roles")
public class CandidateRoleController {
    private final CandidateRoleQueryService roles;

    @Inner
    @GetMapping("/{roleId}")
    public CandidateRole find(@PathVariable @Positive Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new IllegalArgumentException("角色ID必须是正整数");
        }
        return roles.findById(roleId);
    }
}
