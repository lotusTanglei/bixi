package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.dto.FormPermissionDTO;
import com.lotus.bixi.workflow.api.dto.RoleFormPermissionDTO;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.vo.FormFieldPermissionVO;
import com.lotus.bixi.workflow.api.vo.FormPermissionVO;

import java.util.List;

public interface FormPermissionService extends IService<SysFormPermission> {

    List<FormPermissionVO> listByFormId(Long formId);

    Boolean savePermission(FormPermissionDTO dto);

    void deletePermission(Long id);

    List<FormFieldPermissionVO> getFieldPermissions(Long formId, Long roleId);

    Boolean saveRolePermission(RoleFormPermissionDTO dto);

    boolean hasPermission(String formKey, String permType);

}
