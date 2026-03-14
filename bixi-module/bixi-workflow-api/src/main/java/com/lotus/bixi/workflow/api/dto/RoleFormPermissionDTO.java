package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "角色表单权限关联传输对象")
public class RoleFormPermissionDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID")
    private Long roleId;

    @NotNull(message = "表单权限ID不能为空")
    @Schema(description = "表单权限ID")
    private Long formPermId;

    private static final long serialVersionUID = 1L;
}
