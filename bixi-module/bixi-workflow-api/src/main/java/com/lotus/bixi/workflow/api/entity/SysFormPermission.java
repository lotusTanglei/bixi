package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_permission")
@Schema(description = "表单权限配置表")
public class SysFormPermission extends BaseEntity<SysFormPermission> {

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段标签")
    private String fieldLabel;

    @Schema(description = "权限类型 read:只读 write:可写 hide:隐藏")
    private String permType;

    @Schema(description = "角色ID")
    private Long roleId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
