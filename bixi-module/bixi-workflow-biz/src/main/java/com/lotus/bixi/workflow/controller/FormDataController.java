package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.entity.WfFormData;
import com.lotus.bixi.workflow.service.FormDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form/data")
@Tag(description = "formData", name = "表单数据管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormDataController {

    private final FormDataService formDataService;

    @PostMapping
    @SysLog("保存表单数据")
    @HasPermission("wf_form_data_save")
    @Operation(summary = "保存表单数据")
    public R<Boolean> save(@Valid @RequestBody FormDataDTO formDataDTO) {
        formDataService.saveFormData(formDataDTO);
        return R.ok(Boolean.TRUE);
    }

    @GetMapping("/process/{processInstanceId}")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询流程表单数据")
    public R<WfFormData> getByProcessInstanceId(@PathVariable String processInstanceId) {
        return R.ok(formDataService.getByProcessInstanceId(processInstanceId));
    }

    @GetMapping("/task/{taskId}")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询任务表单数据")
    public R<WfFormData> getByTaskId(@PathVariable String taskId) {
        return R.ok(formDataService.getByTaskId(taskId));
    }

    @GetMapping("/business/{businessKey}")
    @HasPermission("wf_form_view")
    @Operation(summary = "查询业务表单数据")
    public R<List<WfFormData>> getByBusinessKey(@PathVariable String businessKey) {
        return R.ok(formDataService.listByBusinessKey(businessKey));
    }
}
