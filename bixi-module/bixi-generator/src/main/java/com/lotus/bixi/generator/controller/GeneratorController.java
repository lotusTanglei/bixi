package com.lotus.bixi.generator.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.generator.service.GeneratorService;
import com.lotus.bixi.common.core.util.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
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
 * @author tanglei
 * @date 2018-07-30
 */
@RestController
@RequiredArgsConstructor
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
	public void download(String tableIds, HttpServletResponse response) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(outputStream);

		// 生成代码
		for (String tableId : tableIds.split(StrUtil.COMMA)) {
			generatorService.downloadCode(Long.parseLong(tableId), zip);
		}

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
	@GetMapping("/code")
	public R<String> code(String tableIds) throws Exception {
		// 生成代码
		for (String tableId : tableIds.split(StrUtil.COMMA)) {
			generatorService.generatorCode(Long.valueOf(tableId));
		}

		return R.ok();
	}

	/**
	 * 预览代码
	 * @param tableId 表ID
	 * @return
	 */
	@SneakyThrows
	@GetMapping("/preview")
	public List<Map<String, String>> preview(Long tableId) {
		return generatorService.preview(tableId);
	}

}
