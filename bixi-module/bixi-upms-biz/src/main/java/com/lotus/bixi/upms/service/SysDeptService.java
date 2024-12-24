

package com.lotus.bixi.upms.service;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysDept;
import com.lotus.bixi.upms.api.vo.DeptExcelVo;
import com.lotus.bixi.common.core.util.R;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * <p>
 * 部门管理 服务类
 * </p>
 *
 * @author 唐磊
 * @since 2018-01-20
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 查询部门树菜单
     *
     * @param deptName 部门名称
     * @return 树
     */
    List<Tree<Long>> selectTree(String deptName);

    /**
     * 删除部门
     *
     * @param id 部门 ID
     * @return 成功、失败
     */
    Boolean removeDeptById(Long id);

    List<DeptExcelVo> listExcelVo();

    R importDept(List<DeptExcelVo> excelVOList, BindingResult bindingResult);

    /**
     * 获取部门的所有后代部门列表
     *
     * @param deptId 部门ID
     * @return 后代部门列表
     */
    List<SysDept> listDescendant(Long deptId);

}
