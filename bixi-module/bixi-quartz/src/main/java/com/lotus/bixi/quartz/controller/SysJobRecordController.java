package com.lotus.bixi.quartz.controller;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/**
 * @author 唐磊
 * <p>
 * 定时任务执行日志表
 */
@RestController
@AllArgsConstructor
@RequestMapping("/sys-job-record")
@Tag(description = "sys-job-record", name = "定时任务日志")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysJobRecordController {

	private final SysJobRecordService sysJobRecordService;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param sysJobLog 定时任务执行日志表
	 * @return
	 */
	@GetMapping("/page")
	@Operation(description = "分页定时任务日志查询")
	public R getSysJobRecordPage(Page page, SysJobRecord sysJobLog) {
		return R.ok(sysJobRecordService.page(page, Wrappers.query(sysJobLog)));
	}

	@DeleteMapping
	@Operation(description = "批量删除日志")
	public R deleteLogs(@RequestBody Long[] ids) {
		return R.ok(sysJobRecordService.removeBatchByIds(CollUtil.toList(ids)));
	}

}
