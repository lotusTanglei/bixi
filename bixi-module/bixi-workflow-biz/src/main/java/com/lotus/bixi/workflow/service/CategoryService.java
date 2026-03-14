package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.entity.WfCategory;

import java.util.List;

public interface CategoryService extends IService<WfCategory> {

    List<WfCategory> listAll();

    List<WfCategory> tree();

    WfCategory saveCategory(WfCategory category);

    void delete(Long id);

}
