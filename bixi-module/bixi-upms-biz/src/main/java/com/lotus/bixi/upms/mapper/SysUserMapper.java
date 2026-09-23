

package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.vo.UserVO;
import com.lotus.bixi.common.mybatis.annotation.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 通过用户名查询用户信息（含有角色信息）
     *
     * @param username 用户名
     * @return userVo
     */
    @DataScope(userAlias = "u", deptAlias = "d")
    UserVO getUserVoByUsername(String username);

    /**
     * 分页查询用户信息（含角色）
     *
     * @param page      分页
     * @param userDTO   查询参数
     * @param dataScope
     * @return list
     */
    @DataScope(userAlias = "u", deptAlias = "d")
    IPage<UserVO> getUserVosPage(Page page, @Param("query") UserDTO userDTO);

    /**
     * 通过ID查询用户信息
     *
     * @param id 用户ID
     * @return userVo
     */
    @DataScope(userAlias = "u", deptAlias = "d")
    UserVO getUserVoById(Long id);

    /**
     * 查询用户列表
     *
     * @param userDTO   查询条件
     * @return
     */
    @DataScope(userAlias = "u", deptAlias = "d")
    List<UserVO> selectVoList(@Param("query") UserDTO userDTO);

}
