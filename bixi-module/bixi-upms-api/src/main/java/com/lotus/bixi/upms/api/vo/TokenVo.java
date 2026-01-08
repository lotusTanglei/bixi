package com.lotus.bixi.upms.api.vo;

import lombok.Data;

/**
 * 前端展示令牌管理
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
public class TokenVo {

    private String id;

    private Long userId;

    private String clientId;

    private String username;

    private String accessToken;

    private String issuedAt;

    private String expiresAt;

}
