package com.lotus.bixi.upms.controller;

import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, read-only candidate identity projection.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/upms/candidate-identities")
public class CandidateIdentityController {

    private final CandidateIdentityQueryService identities;

    @Inner
    @GetMapping("/{userId}")
    public CandidateIdentity find(@PathVariable @Positive Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID必须是正整数");
        }
        return identities.findById(userId);
    }

}
