
package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.entity.SysDict;
import com.lotus.bixi.upms.api.entity.SysDictItem;
import com.lotus.bixi.upms.mapper.SysDictItemMapper;
import com.lotus.bixi.upms.mapper.SysDictMapper;
import com.lotus.bixi.upms.service.SysDictService;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.enums.DictTypeEnum;
import com.lotus.bixi.common.core.exception.ErrorCodes;
import com.lotus.bixi.common.core.util.MsgUtils;
import com.lotus.bixi.common.core.util.R;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典表
 *
 * @author 唐磊
 * @date 2019/03/19
 */
@Service
@AllArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {

    private final SysDictItemMapper dictItemMapper;

    /**
     * 根据ID 删除字典
     *
     * @param ids 字典ID 列表
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConstants.DICT_DETAILS, allEntries = true)
    public R removeDictByIds(Long[] ids) {

        List<Long> dictIdList = baseMapper.selectBatchIds(CollUtil.toList(ids))
                .stream()
                .filter(sysDict -> !sysDict.getSystemFlag().equals(DictTypeEnum.SYSTEM.getType()))// 系统内置类型不删除
                .map(SysDict::getId)
                .collect(Collectors.toList());

        baseMapper.deleteByIds(dictIdList);

        dictItemMapper.delete(Wrappers.<SysDictItem>lambdaQuery().in(SysDictItem::getDictId, dictIdList));
        return R.ok();
    }

    /**
     * 更新字典
     *
     * @param dict 字典
     * @return
     */
    @Override
    @CacheEvict(value = CacheConstants.DICT_DETAILS, key = "#dict.type")
    public R updateDict(SysDict dict) {
        SysDict sysDict = this.getById(dict.getId());
        // 系统内置
        if (DictTypeEnum.SYSTEM.getType().equals(sysDict.getSystemFlag())) {
            return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_DICT_UPDATE_SYSTEM));
        }
        this.updateById(dict);
        return R.ok(dict);
    }

    /**
     * 同步缓存 （清空缓存）
     *
     * @return R
     */
    @Override
    @CacheEvict(value = CacheConstants.DICT_DETAILS, allEntries = true)
    public R syncDictCache() {
        return R.ok();
    }

}
