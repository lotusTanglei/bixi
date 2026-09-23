package com.lotus.bixi.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.sensitive.SensitiveWordEngine;
import com.lotus.bixi.upms.api.entity.SysSensitiveWord;
import com.lotus.bixi.upms.mapper.SysSensitiveWordMapper;
import com.lotus.bixi.upms.service.SysSensitiveWordService;
import com.lotus.bixi.upms.service.SensitiveWordRefreshNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;

@Service
@RequiredArgsConstructor
public class SysSensitiveWordServiceImpl extends ServiceImpl<SysSensitiveWordMapper, SysSensitiveWord>
		implements SysSensitiveWordService {

	private final SensitiveWordEngine engine;
	private final SensitiveWordRefreshNotifier refreshNotifier;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean save(SysSensitiveWord word) {
		TenantContextHolder.assertWritable();
		boolean saved = super.save(word);
		if (saved) refresh();
		return saved;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateById(SysSensitiveWord word) {
		TenantContextHolder.assertWritable();
		boolean updated = super.updateById(word);
		if (updated) refresh();
		return updated;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeById(Serializable id) {
		TenantContextHolder.assertWritable();
		boolean removed = super.removeById(id);
		if (removed) refresh();
		return removed;
	}

	private void refresh() {
		Long tenantId = TenantContextHolder.get();
		if (tenantId == null) return;
		Runnable refresh = () -> {
			reloadTenant(tenantId);
			refreshNotifier.publish(tenantId);
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					refresh.run();
				}
			});
		}
		else {
			refresh.run();
		}
	}

	@Override
	public void reloadTenant(Long tenantId) {
		if (tenantId == null) return;
		Long previousTenant = TenantContextHolder.get();
		boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
		try {
			TenantContextHolder.set(tenantId);
			TenantContextHolder.setReadOnlySwitch(false);
			engine.reload(tenantId, list().stream().filter(word -> !"1".equals(word.getStatus()))
					.map(SysSensitiveWord::getWord).toList());
		}
		finally {
			if (previousTenant == null) TenantContextHolder.clear();
			else {
				TenantContextHolder.set(previousTenant);
				TenantContextHolder.setReadOnlySwitch(previousReadOnly);
			}
		}
	}

}
