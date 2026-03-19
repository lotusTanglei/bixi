package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormVersionDTO;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.service.FormVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form/version")
@Tag(description = "formVersion", name = "表单版本管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormVersionController {

    private final FormVersionService formVersionService;

    @GetMapping("/list/{formId}")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询版本列表")
    public R<List<WfFormVersion>> listByFormId(@PathVariable Long formId) {
        return R.ok(formVersionService.listVersions(formId));
    }

    @PostMapping
    @SysLog("创建新版本")
    @HasPermission("wf_form_edit")
    @Operation(summary = "创建新版本")
    public R<Boolean> createVersion(@Valid @RequestBody FormVersionDTO versionDTO) {
        formVersionService.createVersion(versionDTO.getFormId(), versionDTO.getSchemaJson(), versionDTO.getChangeLog());
        return R.ok(Boolean.TRUE);
    }

    @PutMapping("/activate/{formId}/{version}")
    @SysLog("激活版本")
    @HasPermission("wf_form_edit")
    @Operation(summary = "激活版本")
    public R<Boolean> activate(@PathVariable Long formId, @PathVariable Integer version) {
        formVersionService.activateVersion(formId, version);
        return R.ok(Boolean.TRUE);
    }

    @PostMapping("/rollback/{formId}/{version}")
    @SysLog("回滚版本")
    @HasPermission("wf_form_edit")
    @Operation(summary = "回滚版本")
    public R<Boolean> rollback(@PathVariable Long formId, @PathVariable Integer version) {
        formVersionService.rollback(formId, version);
        return R.ok(Boolean.TRUE);
    }

    @GetMapping("/diff/{formId}/{v1}/{v2}")
    @HasPermission("wf_form_view")
    @Operation(summary = "版本对比")
    public R<Map<String, Object>> diff(@PathVariable Long formId, @PathVariable Integer v1, @PathVariable Integer v2) {
        return R.ok(formVersionService.diffVersions(formId, v1, v2));
    }
}
