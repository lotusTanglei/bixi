

package com.lotus.bixi.upms.controller;

import com.lotus.bixi.upms.service.SysMobileService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.Inner;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 手机验证码
 */
@RestController
@AllArgsConstructor
@RequestMapping("/mobile")
@Tag(description = "mobile", name = "手机管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysMobileController {

    private final SysMobileService mobileService;

    @Inner(value = false)
    @GetMapping("/{mobile}")
    public R sendSmsCode(@PathVariable String mobile) {
        return mobileService.sendSmsCode(mobile);
    }

}
