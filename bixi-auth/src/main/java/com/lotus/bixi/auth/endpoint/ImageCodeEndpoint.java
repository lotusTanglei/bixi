package com.lotus.bixi.auth.endpoint;

import cn.hutool.core.lang.Validator;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import io.springboot.captcha.ArithmeticCaptcha;
import io.springboot.captcha.ChineseCaptcha;
import io.springboot.captcha.SpecCaptcha;
import io.springboot.captcha.base.Captcha;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * 验证码相关的接口
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class ImageCodeEndpoint {

    private static final Integer DEFAULT_IMAGE_WIDTH = 100;

    private static final Integer DEFAULT_IMAGE_HEIGHT = 40;

    private final RedisTemplate redisTemplate;

    /**
     * 创建图形验证码
     */
    @SneakyThrows
    @GetMapping("/image")
    public void image(String randomStr, HttpServletResponse response,@RequestParam(value = "type", defaultValue = "spec") String type) {

        Captcha captcha;

        switch (type) {
            case "arithmetic":
                captcha = new ArithmeticCaptcha(DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
                break;
            case "chinese":
                captcha = new ChineseCaptcha(DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
                break;
            default:
                captcha = new SpecCaptcha(DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
                break;
        }

        if (Validator.isMobile(randomStr)) {
            return;
        }

        String result = captcha.text();
        redisTemplate.opsForValue()
                .set(CacheConstants.DEFAULT_CODE_KEY + randomStr, result, SecurityConstants.CODE_TIME, TimeUnit.SECONDS);
        // 转换流信息写出
        captcha.out(response.getOutputStream());
    }

}
