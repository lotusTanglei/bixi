package com.lotus.bixi.generator.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.generator.dto.GenerateCodeRequest;
import com.lotus.bixi.generator.service.GeneratorService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成器
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/generator")
public class GeneratorController {

	private final GeneratorService generatorService;

	/**
	 * ZIP 下载生成代码
	 * @param tableIds 数据表ID
	 * @param response 流输出对象
	 */
	@SneakyThrows
	@GetMapping("/download")
	@HasPermission("codegen_table_generate")
	public void download(String tableIds, String templateVersion, HttpServletResponse response) {
		if (tableIds == null || tableIds.contains(StrUtil.COMMA)) {
			throw new IllegalArgumentException("ZIP 下载必须逐表预览并下载");
		}
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(outputStream);

		generatorService.downloadCode(Long.parseLong(tableIds), templateVersion, zip);

		IoUtil.close(zip);

		// zip压缩包数据
		byte[] data = outputStream.toByteArray();

		response.reset();
		response.setHeader(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s.zip", tableIds));
		response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length));
		response.setContentType("application/octet-stream; charset=UTF-8");
		IoUtil.write(response.getOutputStream(), false, data);
	}

	/**
	 * 目标目录生成代码
	 */
	@ResponseBody
	@PostMapping("/code")
	@HasPermission("codegen_table_generate")
	@SysLog("生成代码到项目目录")
	public R<String> code(@Valid @RequestBody GenerateCodeRequest request) {
		generatorService.generatorCode(request.tableIds(), request.templateVersion(), request.overwrite());
		return R.ok();
	}

	/**
	 * 预览代码
	 * @param tableId 表ID
	 * @return
	 */
	@SneakyThrows
	@GetMapping("/preview")
	@HasPermission("codegen_table_view")
	public List<Map<String, String>> preview(Long tableId) {
		return generatorService.preview(tableId);
	}

}
