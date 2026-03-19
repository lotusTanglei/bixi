

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户")
public class SysUser extends BaseEntity<SysUser> {

    /**
     * 用户名
     */
    @Schema(description = "用户名")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码")
    private String password;

    /**
     * 随机盐
     */
    @JsonIgnore
    @Schema(description = "随机盐")
    private String salt;

    /**
     * 锁定标记
     */
    @Schema(description = "锁定标记")
    private String lockFlag;

    /**
     * 手机号
     */
    @Schema(description = "手机号")
    private String phone;

    /**
     * 头像
     */
    @Schema(description = "头像地址")
    private String avatar;

    /**
     * 部门ID
     */
    @Schema(description = "用户所属部门id")
    private Long deptId;

    /**
     * 微信openid
     */
    @Schema(description = "微信openid")
    private String wxOpenid;

    /**
     * 微信小程序openId
     */
    @Schema(description = "微信小程序openid")
    private String miniOpenid;

    /**
     * QQ openid
     */
    @Schema(description = "QQ openid")
    private String qqOpenid;

    /**
     * 码云唯一标识
     */
    @Schema(description = "码云唯一标识")
    private String giteeLogin;

    /**
     * 开源中国唯一标识
     */
    @Schema(description = "开源中国唯一标识")
    private String oscId;

    /**
     * 昵称
     */
    @Schema(description = "昵称")
    private String nickname;

    /**
     * 姓名
     */
    @Schema(description = "姓名")
    private String name;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

}
