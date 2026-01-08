

package com.lotus.bixi.upms.controller;

import com.lotus.bixi.upms.api.feign.RemoteTokenService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@RestController
@AllArgsConstructor
@RequestMapping("/token")
@Tag(description = "token", name = "令牌管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysTokenController {

    private final RemoteTokenService remoteTokenService;

    /**
     * 分页token 信息
     *
     * @param params 参数集
     * @return token集合
     */
    @RequestMapping("/page")
    public R getTokenPage(@RequestBody Map<String, Object> params) {
        return remoteTokenService.getTokenPage(params);
    }

    /**
     * 删除
     *
     * @param tokens tokens
     * @return success/false
     */
    @SysLog("删除用户token")
    @DeleteMapping("/delete")
    @HasPermission("sys_token_del")
    public R removeById(@RequestBody String[] tokens) {
        for (String token : tokens) {
            remoteTokenService.removeTokenById(token);
        }
        return R.ok();
    }

}
