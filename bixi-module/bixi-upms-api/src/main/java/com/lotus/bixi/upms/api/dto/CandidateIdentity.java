package com.lotus.bixi.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * Server-owned identity state used when resolving workflow candidates.
 */
@Schema(description = "候选人身份状态")
public record CandidateIdentity(
        @Schema(description = "用户ID") Long userId,
        @Schema(description = "用户是否启用") boolean enabled,
        @Schema(description = "用户是否锁定") boolean locked,
        @Schema(description = "租户ID") Long tenantId
) implements Serializable {

    private static final long serialVersionUID = 1L;

}
