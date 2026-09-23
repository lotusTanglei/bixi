package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysTenant;
import com.lotus.bixi.upms.api.service.TenantStatusService;

public interface SysTenantService extends IService<SysTenant>, TenantStatusService {

	boolean changeStatus(Long id, String status);

}
