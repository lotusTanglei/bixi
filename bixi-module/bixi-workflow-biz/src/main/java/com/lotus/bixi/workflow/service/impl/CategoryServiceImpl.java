package com.lotus.bixi.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.workflow.api.entity.WfCategory;
import com.lotus.bixi.workflow.mapper.WfCategoryMapper;
import com.lotus.bixi.workflow.service.CategoryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<WfCategoryMapper, WfCategory> implements CategoryService {

    @Override
    public List<WfCategory> listAll() {
        return this.lambdaQuery()
                .orderByAsc(WfCategory::getSn)
                .list();
    }

    @Override
    public List<WfCategory> tree() {
        List<WfCategory> allCategories = listAll();

        Map<Long, List<WfCategory>> categoryMap = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(WfCategory::getParentId));

        List<WfCategory> rootCategories = allCategories.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0L)
                .collect(Collectors.toList());

        for (WfCategory root : rootCategories) {
            buildTree(root, categoryMap);
        }

        return rootCategories;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfCategory saveCategory(WfCategory category) {
        this.saveOrUpdate(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        List<WfCategory> children = this.lambdaQuery()
                .eq(WfCategory::getParentId, id)
                .list();

        if (CollUtil.isNotEmpty(children)) {
            throw new RuntimeException("存在子分类，无法删除");
        }

        this.removeById(id);
    }

    private void buildTree(WfCategory parent, Map<Long, List<WfCategory>> categoryMap) {
        List<WfCategory> children = categoryMap.get(parent.getId());
        if (CollUtil.isNotEmpty(children)) {
            parent.setChildren(children);
            for (WfCategory child : children) {
                buildTree(child, categoryMap);
            }
        }
    }

}
