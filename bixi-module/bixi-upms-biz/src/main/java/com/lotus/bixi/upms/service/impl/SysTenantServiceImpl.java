package com.lotus.bixi.upms.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.entity.SysTenant;
import com.lotus.bixi.upms.api.service.TenantStatusService;
import com.lotus.bixi.upms.mapper.SysTenantMapper;
import com.lotus.bixi.upms.service.SysTenantService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.lotus.bixi.common.core.cache.TenantCacheInvalidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;

@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

	private final SysTenantMapper tenantMapper;
	private final TenantCacheInvalidator tenantCacheInvalidator;

	@Override
	@Cacheable(cacheNames = "tenant_status", key = "#tenantId", unless = "#result == null")
	public TenantStatus getStatus(Long tenantId) {
		SysTenant tenant = tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getId, tenantId));
		return tenant == null ? null : new TenantStatus(tenant.getId(), tenant.getStatus(), tenant.getExpireTime());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean save(SysTenant tenant) {
		TenantContextHolder.assertWritable();
		validateTenant(tenant, null);
		if (!StringUtils.hasText(tenant.getStatus())) tenant.setStatus("0");
		if (tenant.getMaxUserCount() == null) tenant.setMaxUserCount(-1);
		return super.save(tenant);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateById(SysTenant tenant) {
		TenantContextHolder.assertWritable();
		if (tenant == null || tenant.getId() == null || tenant.getId() == 1L) return false;
		validateTenant(tenant, tenant.getId());
		return super.updateById(tenant);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeById(Serializable id) {
		TenantContextHolder.assertWritable();
		if (id == null || Long.valueOf(id.toString()) == 1L) return false;
		return super.removeById(id);
	}

	private void validateTenant(SysTenant tenant, Long currentId) {
		if (tenant == null || !StringUtils.hasText(tenant.getName()) || !StringUtils.hasText(tenant.getCode())) {
			throw new IllegalArgumentException("tenant_name_code_required");
		}
		long sameCode = tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
				.eq(SysTenant::getCode, tenant.getCode())
				.ne(currentId != null, SysTenant::getId, currentId));
		if (sameCode > 0) throw new IllegalArgumentException("tenant_code_exists");
		if (tenant.getMaxUserCount() != null && tenant.getMaxUserCount() < -1) {
			throw new IllegalArgumentException("tenant_user_limit_invalid");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(cacheNames = "tenant_status", key = "#id")
	public boolean changeStatus(Long id, String status) {
		TenantContextHolder.assertWritable();
		if (id == null || id == 1L || !("0".equals(status) || "1".equals(status))) {
			return false;
		}
		boolean updated = tenantMapper.update(null, Wrappers.<SysTenant>lambdaUpdate()
				.eq(SysTenant::getId, id).set(SysTenant::getStatus, status)) > 0;
		if (updated && "1".equals(status)) tenantCacheInvalidator.clearTenant(id);
		return updated;
	}

}
