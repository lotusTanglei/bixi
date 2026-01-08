

package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysPost;
import com.lotus.bixi.upms.api.vo.PostExcelVO;
import com.lotus.bixi.common.core.util.R;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * 岗位信息表
 *
 * @author fxz
 * @date 2025-01-01
 */
public interface SysPostService extends IService<SysPost> {

    /**
     * 导出excel 表格
     *
     * @return
     */
    List<PostExcelVO> listPost();

    /**
     * 导入岗位
     *
     * @param excelVOList   岗位列表
     * @param bindingResult 错误信息列表
     * @return ok fail
     */
    R importPost(List<PostExcelVO> excelVOList, BindingResult bindingResult);

}
