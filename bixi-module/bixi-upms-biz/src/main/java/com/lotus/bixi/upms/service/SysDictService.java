
package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysDict;
import com.lotus.bixi.common.core.util.R;

/**
 * 字典表
 *
 * @author 唐磊
 * @date 2025-01-01
 */
public interface SysDictService extends IService<SysDict> {

    /**
     * 根据ID 删除字典
     *
     * @param ids ID列表
     * @return
     */
    R removeDictByIds(Long[] ids);

    /**
     * 更新字典
     *
     * @param sysDict 字典
     * @return
     */
    R updateDict(SysDict sysDict);

    /**
     * 同步缓存 （清空缓存）
     *
     * @return R
     */
    R syncDictCache();

}
