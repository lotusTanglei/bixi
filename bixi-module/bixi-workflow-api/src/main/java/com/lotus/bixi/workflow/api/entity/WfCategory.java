package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_category")
@Schema(description = "流程分类表")
public class WfCategory extends BaseEntity<WfCategory> {

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "分类编码")
    private String categoryCode;

    @Schema(description = "父分类ID")
    private Long parentId;

    @Schema(description = "排序号")
    private Integer sn;

    @TableField(exist = false)
    @Schema(description = "子分类列表")
    private List<WfCategory> children;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
