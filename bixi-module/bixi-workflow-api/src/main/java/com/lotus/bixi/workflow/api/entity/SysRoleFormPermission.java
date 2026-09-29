package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_form_permission")
@Schema(description = "角色表单权限关联表")
public class SysRoleFormPermission extends BaseEntity<SysRoleFormPermission> {

    @Schema(description = "角色ID")
    private Long roleId;

    @TableField("form_perm_id")
    @Schema(description = "表单权限ID")
    private Long formPermId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
