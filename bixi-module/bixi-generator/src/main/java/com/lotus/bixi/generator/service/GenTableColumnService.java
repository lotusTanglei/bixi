package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.entity.GenTableColumn;
import java.util.List;

/**
 * 列属性
 *
 * @author 唐磊x code generator
 * @date 2025-01-01
 */
public interface GenTableColumnService extends IService<GenTableColumn> {

	void initFieldList(List<GenTableColumn> tableFieldList);

	/**
	 * Persist only metadata derived from the physical column. Explicit assignments are
	 * required so nullable metadata can clear stale database values.
	 * @param column reconciled persisted column
	 * @return whether the row was updated
	 */
	boolean updatePhysicalMetadataById(GenTableColumn column);

	void updateTableField(String dsName, String tableName, List<GenTableColumn> tableFieldList);

}
