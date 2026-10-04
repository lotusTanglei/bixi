package com.lotus.bixi.acceptance.api.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamCreateDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportResult;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamUpdateDTO;
import com.lotus.bixi.acceptance.api.entity.SysPublicParam;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamVO;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamExportVO;

import java.util.List;

/**
 * Transport-neutral application contract shared by the cloud adapter and the
 * single-process implementation. Persistence interfaces stay in the biz
 * module; this API only exposes DTOs and result projections.
 */
public interface SysPublicParamService {
	IPage<SysPublicParamVO> pageSysPublicParam(Page<SysPublicParam> page, SysPublicParamQueryDTO query);
	SysPublicParamVO details(Long id);
	boolean create(SysPublicParamCreateDTO dto);
	boolean update(SysPublicParamUpdateDTO dto);
	boolean delete(List<Long> ids);
	SysPublicParamImportResult importRows(List<SysPublicParamImportDTO> rows);
	List<SysPublicParamExportVO> exportRows(SysPublicParamQueryDTO query);
}
