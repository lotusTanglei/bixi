

package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.entity.GenTableColumn;

import java.util.List;

/**
 * 列属性
 *
 * @author tanglei
 * @date 2023-02-06 20:16:01
 */
public interface GenTableColumnService extends IService<GenTableColumn> {

	void initFieldList(List<GenTableColumn> tableFieldList);

	void updateTableField(String dsName, String tableName, List<GenTableColumn> tableFieldList);

}
