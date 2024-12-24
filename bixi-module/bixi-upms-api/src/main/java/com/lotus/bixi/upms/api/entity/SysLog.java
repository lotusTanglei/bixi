

package com.lotus.bixi.upms.api.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.*;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <p>
 * 日志表
 * </p>
 *
 * @author 唐磊
 * @since 2017-11-20
 */
@Data
@Schema(description = "日志")
public class SysLog extends BaseEntity<SysLog> {

    /**
     * 日志类型
     */
    @NotBlank(message = "日志类型不能为空")
    @ExcelProperty("日志类型（0-正常 9-错误）")
    @Schema(description = "日志类型")
    private String type;

    /**
     * 日志标题
     */
    @NotBlank(message = "日志标题不能为空")
    @ExcelProperty("日志标题")
    @Schema(description = "日志标题")
    private String title;

    /**
     * 操作IP地址
     */
    @ExcelProperty("操作ip地址")
    @Schema(description = "操作ip地址")
    private String remoteAddr;

    /**
     * 用户代理
     */
    @Schema(description = "用户代理")
    private String userAgent;

    /**
     * 请求URI
     */
    @ExcelProperty("浏览器")
    @Schema(description = "请求uri")
    private String requestUri;

    /**
     * 操作方式
     */
    @ExcelProperty("操作方式")
    @Schema(description = "操作方式")
    private String method;

    /**
     * 操作提交的数据
     */
    @ExcelProperty("提交数据")
    @Schema(description = "提交数据")
    private String params;

    /**
     * 执行时间
     */
    @ExcelProperty("执行时间")
    @Schema(description = "方法执行时间")
    private Long time;

    /**
     * 异常信息
     */
    @ExcelProperty("异常信息")
    @Schema(description = "异常信息")
    private String exception;

    /**
     * 服务ID
     */
    @ExcelProperty("应用标识")
    @Schema(description = "应用标识")
    private String serviceId;


}
