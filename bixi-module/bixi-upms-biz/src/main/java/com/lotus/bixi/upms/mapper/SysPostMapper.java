

package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.api.entity.SysPost;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 岗位信息表
 *
 * @author fxz
 * @date 2022-03-26 12:50:43
 */
@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {

    /**
     * 通过用户ID，查询岗位信息
     *
     * @param userId 用户id
     * @return 岗位信息
     */
    List<SysPost> listPostsByUserId(Long userId);

}
