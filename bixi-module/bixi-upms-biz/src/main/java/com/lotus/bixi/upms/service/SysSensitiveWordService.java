package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysSensitiveWord;

public interface SysSensitiveWordService extends IService<SysSensitiveWord> {

	void reloadTenant(Long tenantId);
}
