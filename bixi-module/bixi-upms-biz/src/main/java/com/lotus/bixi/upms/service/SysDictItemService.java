
package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysDictItem;
import com.lotus.bixi.common.core.util.R;

/**
 * 字典项
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public interface SysDictItemService extends IService<SysDictItem> {

    /**
     * 删除字典项
     *
     * @param id 字典项ID
     * @return
     */
    R removeDictItem(Long id);

    /**
     * 更新字典项
     *
     * @param item 字典项
     * @return
     */
    R updateDictItem(SysDictItem item);

}
