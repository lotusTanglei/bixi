package com.lotus.bixi.acceptance.api.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lotus.bixi.acceptance.api.dto.SysDictCreateDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictImportResult;
import com.lotus.bixi.acceptance.api.dto.SysDictQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictUpdateDTO;
import com.lotus.bixi.acceptance.api.entity.SysDict;
import com.lotus.bixi.acceptance.api.vo.SysDictVO;
import com.lotus.bixi.acceptance.api.vo.SysDictExportVO;

import java.util.List;

/**
 * Transport-neutral application contract shared by the cloud adapter and the
 * single-process implementation. Persistence interfaces stay in the biz
 * module; this API only exposes DTOs and result projections.
 */
public interface SysDictService {
	IPage<SysDictVO> pageSysDict(Page<SysDict> page, SysDictQueryDTO query);
	SysDictVO details(Long id);
	boolean create(SysDictCreateDTO dto);
	boolean update(SysDictUpdateDTO dto);
	boolean delete(List<Long> ids);
	SysDictImportResult importRows(List<SysDictImportDTO> rows);
	List<SysDictExportVO> exportRows(SysDictQueryDTO query);
}
