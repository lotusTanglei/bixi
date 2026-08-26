package com.lotus.bixi.upms.service.local;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.entity.SysDictItem;
import com.lotus.bixi.upms.api.service.DictionaryQueryService;
import com.lotus.bixi.upms.service.SysDictItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Primary
@Service
@RequiredArgsConstructor
public class LocalDictionaryQueryService implements DictionaryQueryService {

    private final SysDictItemService dictItemService;

    @Override
    @Cacheable(value = CacheConstants.DICT_DETAILS, key = "#type", unless = "#result.data.isEmpty()")
    public R<List<SysDictItem>> getDictByType(String type) {
        return R.ok(dictItemService.list(Wrappers.<SysDictItem>lambdaQuery()
                .eq(SysDictItem::getDictType, type)));
    }

}
