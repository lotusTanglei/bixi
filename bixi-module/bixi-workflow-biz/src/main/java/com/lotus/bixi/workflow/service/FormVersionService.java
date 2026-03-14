package com.lotus.bixi.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;

import java.util.List;
import java.util.Map;

public interface FormVersionService extends IService<WfFormVersion> {

    WfFormVersion createVersion(Long formId, String schemaJson, String changeLog);

    WfFormVersion activateVersion(Long formId, Integer version);

    WfFormVersion rollback(Long formId, Integer version);

    Map<String, Object> diffVersions(Long formId, Integer v1, Integer v2);

    List<WfFormVersion> listVersions(Long formId);

    WfFormVersion getActiveVersion(Long formId);

}
