

package com.lotus.bixi.upms.api.entity;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 部门关系表
 * </p>
 *
 * @author 唐磊
 * @since 2018-01-22
 */
@Data
@Schema(description = "部门关系")
@EqualsAndHashCode(callSuper = true)
public class SysDeptRelation extends BaseRelationEntity<SysDeptRelation> {
    /**
     * 祖先节点
     */
    @Schema(description = "祖先节点")
    private Long ancestor;

    /**
     * 后代节点
     */
    @Schema(description = "后代节点")
    private Long descendant;

}
