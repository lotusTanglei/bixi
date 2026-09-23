package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.sensitive.SensitiveWordCheck;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.api.entity.SysSensitiveWord;
import com.lotus.bixi.upms.service.SysSensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sensitive-word")
public class SysSensitiveWordController {

	private final SysSensitiveWordService service;

	@GetMapping("/page")
	@HasPermission("sensitive_word_view")
	public R<?> page(Page<SysSensitiveWord> page) {
		return R.ok(service.page(page));
	}

	@PostMapping
	@SysLog("添加敏感词")
	@HasPermission("sensitive_word_add")
	@SensitiveWordCheck
	public R<?> save(@RequestBody SysSensitiveWord word) {
		return R.ok(service.save(word));
	}

	@PutMapping
	@SysLog("修改敏感词")
	@HasPermission("sensitive_word_edit")
	@SensitiveWordCheck
	public R<?> update(@RequestBody SysSensitiveWord word) {
		return R.ok(service.updateById(word));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除敏感词")
	@HasPermission("sensitive_word_del")
	public R<?> delete(@PathVariable Long id) {
		return R.ok(service.removeById(id));
	}

}
