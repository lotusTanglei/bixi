package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "角色表单权限关联展示对象")
public class RoleFormPermissionVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "表单权限ID")
    private Long formPermId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
