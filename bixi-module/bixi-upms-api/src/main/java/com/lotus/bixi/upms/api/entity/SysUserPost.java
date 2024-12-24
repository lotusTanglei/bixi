

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户岗位表
 * </p>
 *
 * @author fxz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserPost extends BaseRelationEntity<SysUserPost> {

    /**
     * 用户ID
     */
    @Schema(description = "用户id")
    private Long userId;

    /**
     * 岗位ID
     */
    @Schema(description = "岗位id")
    private Long postId;

}
