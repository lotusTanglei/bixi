package com.lotus.bixi.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.lotus.bixi.workflow.service.ProcessDefinitionService;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/process")
@Tag(description = "process", name = "流程实例管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;
    private final ProcessDefinitionService processDefinitionService;

    @PostMapping("/start")
    @SysLog("发起流程")
    @HasPermission("wf_process_start")
    @Operation(summary = "发起流程")
    public R<ProcessInstanceVO> start(@Valid @RequestBody ProcessStartDTO startDTO) {
        return R.ok(processInstanceService.start(startDTO));
    }

    @GetMapping("/page")
    @HasPermission("wf_process_view")
    @Operation(summary = "分页查询流程实例")
    public R<IPage<ProcessInstanceVO>> page(Page<ProcessInstanceVO> page, ProcessQueryDTO queryDTO) {
        return R.ok(processInstanceService.page(page, queryDTO));
    }

    @GetMapping("/my/page")
    @HasPermission("wf_process_view")
    @Operation(summary = "分页查询我的流程实例")
    public R<IPage<ProcessInstanceVO>> myPage(Page<ProcessInstanceVO> page, ProcessQueryDTO queryDTO) {
        return R.ok(processInstanceService.myPage(page, queryDTO));
    }

    @GetMapping("/details/{processInstanceId}")
    @HasPermission("wf_process_view")
    @Operation(summary = "查询流程实例详情")
    public R<ProcessInstanceVO> getByProcessInstanceId(@PathVariable String processInstanceId) {
        return R.ok(processInstanceService.getById(processInstanceId));
    }

    @DeleteMapping("/cancel/{processInstanceId}")
    @SysLog("取消流程")
    @HasPermission("wf_process_view")
    @Operation(summary = "取消流程")
    public R<Boolean> cancel(@PathVariable String processInstanceId, @RequestParam(required = false) String reason) {
        return R.ok(processInstanceService.terminate(processInstanceId, reason != null ? reason : "用户取消"));
    }

    @PutMapping("/suspend/{processInstanceId}")
    @SysLog("挂起流程")
    @HasPermission("wf_process_view")
    @Operation(summary = "挂起流程")
    public R<Boolean> suspend(@PathVariable String processInstanceId) {
        return R.ok(processInstanceService.suspend(processInstanceId));
    }

    @PutMapping("/activate/{processInstanceId}")
    @SysLog("激活流程")
    @HasPermission("wf_process_view")
    @Operation(summary = "激活流程")
    public R<Boolean> activate(@PathVariable String processInstanceId) {
        return R.ok(processInstanceService.activate(processInstanceId));
    }

    @GetMapping("/diagram/{processInstanceId}")
    @HasPermission("wf_process_view")
    @Operation(summary = "获取流程图")
    public R<String> getDiagram(@PathVariable String processInstanceId) {
        return R.ok(processInstanceService.getProcessDiagram(processInstanceId));
    }

    @GetMapping("/form/{processDefinitionId}")
    @HasPermission("wf_process_view")
    @Operation(summary = "获取流程表单")
    public R<Object> getForm(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.getFormByDefinitionId(processDefinitionId));
    }

    @GetMapping("/history/{processInstanceId}")
    @HasPermission("wf_process_view")
    @Operation(summary = "获取审批历史")
    public R<List<ApprovalRecordVO>> getHistory(@PathVariable String processInstanceId) {
        return R.ok(processInstanceService.getApprovalHistory(processInstanceId));
    }
}
