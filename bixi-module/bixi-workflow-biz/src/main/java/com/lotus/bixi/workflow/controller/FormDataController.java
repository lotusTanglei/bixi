package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.FormDataDTO;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.service.FormDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@ConditionalOnWorkflowEnabled
@RestController
@AllArgsConstructor
@RequestMapping("/workflow/form/data")
@Tag(description = "formData", name = "表单数据管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class FormDataController {

    private final FormDataService formDataService;

    @PostMapping
    @SysLog("保存表单数据")
    @HasPermission("workflow_form_edit")
    @Operation(summary = "保存表单数据")
    public R<Boolean> save(@Valid @RequestBody FormDataDTO formDataDTO) {
        throw new IllegalArgumentException("表单数据只能通过流程发起或任务办理提交");
    }

    @GetMapping("/process/{processInstanceId}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询流程表单数据")
    public R<FormRenderVO> getByProcessInstanceId(@PathVariable String processInstanceId) {
        return R.ok(formDataService.renderByProcessInstanceId(processInstanceId));
    }

    @GetMapping("/task/{taskId}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询任务表单数据")
    public R<FormRenderVO> getByTaskId(@PathVariable String taskId) {
        return R.ok(formDataService.renderByTaskId(taskId));
    }

    @GetMapping("/business/{businessKey}")
    @HasPermission("workflow_form_view")
    @Operation(summary = "查询业务表单数据")
    public R<List<FormRenderVO>> getByBusinessKey(@PathVariable String businessKey) {
        return R.ok(formDataService.renderByBusinessKey(businessKey));
    }
}
