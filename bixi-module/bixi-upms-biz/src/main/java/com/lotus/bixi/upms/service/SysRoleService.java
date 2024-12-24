

package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysRole;
import com.lotus.bixi.upms.api.vo.RoleExcelVO;
import com.lotus.bixi.upms.api.vo.RoleVO;
import com.lotus.bixi.common.core.util.R;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 通过用户ID，查询角色信息
     *
     * @param userId
     * @return
     */
    List<SysRole> findRolesByUserId(Long userId);

    /**
     * 根据角色ID 查询角色列表
     *
     * @param roleIdList 角色ID列表
     * @param key        缓存key
     * @return
     */
    List<SysRole> findRolesByRoleIds(List<Long> roleIdList, String key);

    /**
     * 通过角色ID，删除角色
     *
     * @param ids
     * @return
     */
    Boolean removeRoleByIds(Long[] ids);

    /**
     * 根据角色菜单列表
     *
     * @param roleVo 角色&菜单列表
     * @return
     */
    Boolean updateRoleMenus(RoleVO roleVo);

    /**
     * 导入角色
     *
     * @param excelVOList   角色列表
     * @param bindingResult 错误信息列表
     * @return ok fail
     */
    R importRole(List<RoleExcelVO> excelVOList, BindingResult bindingResult);

    /**
     * 查询全部的角色
     *
     * @return list
     */
    List<RoleExcelVO> listRole();

}
