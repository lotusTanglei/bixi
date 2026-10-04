package com.lotus.bixi.acceptance.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamCreateDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportResult;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportRowError;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamUpdateDTO;
import com.lotus.bixi.acceptance.api.entity.SysPublicParam;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamVO;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamExportVO;
import com.lotus.bixi.acceptance.api.service.SysPublicParamService;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController("acceptanceSysPublicParamController")
@RequiredArgsConstructor
@RequestMapping("/publicParam")
public class SysPublicParamController {
	private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
	private static final long MAX_REQUEST_BYTES = MAX_UPLOAD_BYTES + 64 * 1024;
	private static final int MAX_IMPORT_ROWS = 1000;

	private final SysPublicParamService service;

	@GetMapping("/page")
	@HasPermission("acceptance_public_param_view")
	public R<IPage<SysPublicParamVO>> page(@ParameterObject Page<SysPublicParam> page,
			@ParameterObject SysPublicParamQueryDTO query) {
		return R.ok(service.pageSysPublicParam(page, query));
	}

	@GetMapping("/details/{id}")
	@HasPermission("acceptance_public_param_view")
	public R<SysPublicParamVO> details(@PathVariable Long id) {
		return R.ok(service.details(id));
	}

	@PostMapping
	@HasPermission("acceptance_public_param_add")
	@SysLog("新增公共参数配置表")
	public R<Boolean> create(@Valid @RequestBody SysPublicParamCreateDTO dto) {
		return R.ok(service.create(dto));
	}

	@PutMapping
	@HasPermission("acceptance_public_param_edit")
	@SysLog("修改公共参数配置表")
	public R<Boolean> update(@Valid @RequestBody SysPublicParamUpdateDTO dto) {
		return R.ok(service.update(dto));
	}

	@DeleteMapping
	@HasPermission("acceptance_public_param_del")
	@SysLog("删除公共参数配置表")
	public R<Boolean> delete(@RequestBody List<Long> ids) {
		return R.ok(service.delete(ids));
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@HasPermission("acceptance_public_param_import")
	@SysLog("导入公共参数配置表")
	public R<SysPublicParamImportResult> importData(
			@RequestPart(value = "file", required = false) MultipartFile file,
			HttpServletRequest request) {
		SysPublicParamImportResult uploadFailure = validateUpload(file, request);
		if (uploadFailure != null) return R.ok(uploadFailure);
		List<SysPublicParamImportDTO> rows = new ArrayList<>(Math.min(MAX_IMPORT_ROWS, 128));
		try (var input = new BufferedInputStream(file.getInputStream())) {
			if (!hasSupportedExcelSignature(input)) {
				return R.ok(importFailure("INVALID_FILE", "导入文件无法解析"));
			}
			EasyExcel.read(input, SysPublicParamImportDTO.class,
				new AnalysisEventListener<SysPublicParamImportDTO>() {
					@Override
					public void invoke(SysPublicParamImportDTO row, AnalysisContext context) {
						if (rows.size() >= MAX_IMPORT_ROWS) {
							throw new ImportRowLimitException();
						}
						rows.add(row);
					}

					@Override
					public void doAfterAllAnalysed(AnalysisContext context) {
					}
				}).autoCloseStream(false).sheet().doRead();
		}
		catch (ImportRowLimitException failure) {
			return R.ok(importFailure("ROW_LIMIT_EXCEEDED", "导入行数不能超过" + MAX_IMPORT_ROWS));
		}
		catch (IOException | RuntimeException failure) {
			return R.ok(importFailure("INVALID_FILE", "导入文件无法解析"));
		}
		if (rows.isEmpty()) {
			return R.ok(importFailure("EMPTY_FILE", "导入文件不包含数据行"));
		}
		return R.ok(service.importRows(rows));
	}

	@GetMapping("/export")
	@ResponseExcel(name = "publicParam")
	@HasPermission("acceptance_public_param_export")
	@SysLog("导出公共参数配置表")
	public List<SysPublicParamExportVO> export(@ParameterObject SysPublicParamQueryDTO query) {
		return service.exportRows(query);
	}

	private static SysPublicParamImportResult validateUpload(MultipartFile file, HttpServletRequest request) {
		if (file == null || file.isEmpty()) return importFailure("EMPTY_FILE", "导入文件不能为空");
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			return importFailure("FILE_TOO_LARGE", "导入文件不能超过5MB");
		}
		long requestBytes = request == null ? -1 : request.getContentLengthLong();
		if (requestBytes > MAX_REQUEST_BYTES) return importFailure("REQUEST_TOO_LARGE", "导入请求过大");
		return null;
	}

	private static SysPublicParamImportResult importFailure(String code, String message) {
		return SysPublicParamImportResult.failure(code, 0,
			List.of(new SysPublicParamImportRowError(1, List.of(message))));
	}

	private static boolean hasSupportedExcelSignature(BufferedInputStream input) throws IOException {
		input.mark(8);
		byte[] signature = input.readNBytes(8);
		input.reset();
		boolean xlsx = signature.length >= 4 && signature[0] == 'P' && signature[1] == 'K'
			&& signature[2] == 3 && signature[3] == 4;
		boolean xls = signature.length >= 8 && signature[0] == (byte) 0xD0
			&& signature[1] == (byte) 0xCF && signature[2] == 0x11 && signature[3] == (byte) 0xE0
			&& signature[4] == (byte) 0xA1 && signature[5] == (byte) 0xB1
			&& signature[6] == 0x1A && signature[7] == (byte) 0xE1;
		return xlsx || xls;
	}

	private static final class ImportRowLimitException extends RuntimeException {
	}
}
