package com.lotus.bixi.upms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.upms.api.entity.SysTenant;
import com.lotus.bixi.upms.service.SysTenantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tenant")
@Tag(name = "tenant", description = "租户管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysTenantController {

	private final SysTenantService tenantService;

	@GetMapping("/status/{tenantId}")
	@Inner
	public com.lotus.bixi.upms.api.service.TenantStatusService.TenantStatus status(@PathVariable Long tenantId) {
		return tenantService.getStatus(tenantId);
	}

	@GetMapping("/page")
	@HasPermission("tenant_view")
	public R<?> page(Page<SysTenant> page) {
		return R.ok(tenantService.page(page));
	}

	@PostMapping
	@SysLog("创建租户")
	@HasPermission("tenant_add")
	public R<?> save(@Valid @RequestBody SysTenant tenant) {
		return R.ok(tenantService.save(tenant));
	}

	@PutMapping
	@SysLog("修改租户")
	@HasPermission("tenant_edit")
	public R<?> update(@Valid @RequestBody SysTenant tenant) {
		return R.ok(tenantService.updateById(tenant));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除租户")
	@HasPermission("tenant_del")
	public R<?> delete(@PathVariable Long id) {
		return R.ok(id != null && id != 1L && tenantService.removeById(id));
	}

	@PutMapping("/{id}/status")
	@SysLog("切换租户状态")
	@HasPermission("tenant_edit")
	public R<?> status(@PathVariable Long id, @RequestParam String status) {
		return R.ok(tenantService.changeStatus(id, status));
	}

}
