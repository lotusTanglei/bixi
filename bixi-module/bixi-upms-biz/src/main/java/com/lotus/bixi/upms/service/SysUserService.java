

package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.vo.UserExcelVO;
import com.lotus.bixi.upms.api.vo.UserVO;
import com.lotus.bixi.common.core.util.R;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 查询用户信息
     *
     * @param sysUser 用户
     * @return userInfo
     */
    UserInfo findUserInfo(SysUser sysUser);

    /**
     * 分页查询用户信息（含有角色信息）
     *
     * @param page    分页对象
     * @param userDTO 参数列表
     * @return
     */
    IPage getUsersWithRolePage(Page page, UserDTO userDTO);

    /**
     * 删除用户
     *
     * @param ids 用户
     * @return boolean
     */
    Boolean deleteUserByIds(Long[] ids);

    /**
     * 更新当前用户基本信息
     *
     * @param userDto 用户信息
     * @return Boolean
     */
    R<Boolean> updateUserInfo(UserDTO userDto);

    /**
     * 更新指定用户信息
     *
     * @param userDto 用户信息
     * @return
     */
    Boolean updateUser(UserDTO userDto);

    /**
     * 通过ID查询用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    UserVO selectUserVoById(Long id);

    /**
     * 保存用户信息
     *
     * @param userDto DTO 对象
     * @return success/fail
     */
    Boolean saveUser(UserDTO userDto);

    /**
     * 查询全部的用户
     *
     * @param userDTO 查询条件
     * @return list
     */
    List<UserExcelVO> listUser(UserDTO userDTO);

    /**
     * excel 导入用户
     *
     * @param excelVOList   excel 列表数据
     * @param bindingResult 错误数据
     * @return ok fail
     */
    R importUser(List<UserExcelVO> excelVOList, BindingResult bindingResult);

    /**
     * 注册用户
     *
     * @param userDto 用户信息
     * @return success/false
     */
    R<Boolean> registerUser(UserDTO userDto);

    /**
     * 锁定用户
     *
     * @param username
     * @return
     */
    R<Boolean> lockUser(String username);

    /**
     * 修改密码
     *
     * @param userDto 用户信息
     * @return
     */
    R changePassword(UserDTO userDto);

    /**
     * 校验密码
     *
     * @param password 密码明文
     * @return
     */
    R checkPassword(String password);

}
