package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.dto.ProcessQueryDTO;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.api.vo.ProcessDefinitionVO;
import com.lotus.bixi.workflow.service.ProcessDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/workflow/definition")
@Tag(description = "definition", name = "流程定义管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    @GetMapping("/list")
    @HasPermission("wf_definition_view")
    @Operation(summary = "查询流程定义列表")
    public R<List<ProcessDefinitionVO>> list(ProcessQueryDTO queryDTO) {
        return R.ok(processDefinitionService.listDefinitions(queryDTO));
    }

    @GetMapping("/{processKey}")
    @HasPermission("wf_definition_view")
    @Operation(summary = "查询流程定义详情")
    public R<ProcessDefinitionVO> getByProcessKey(@PathVariable String processKey) {
        return R.ok(processDefinitionService.getByProcessKey(processKey));
    }

    @PutMapping("/suspend/{processDefinitionId}")
    @SysLog("挂起流程定义")
    @HasPermission("wf_definition_manage")
    @Operation(summary = "挂起流程定义")
    public R<Boolean> suspend(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.suspend(processDefinitionId));
    }

    @PutMapping("/activate/{processDefinitionId}")
    @SysLog("激活流程定义")
    @HasPermission("wf_definition_manage")
    @Operation(summary = "激活流程定义")
    public R<Boolean> activate(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.activate(processDefinitionId));
    }

    @GetMapping(value = "/diagram/{processDefinitionId}", produces = MediaType.IMAGE_PNG_VALUE)
    @HasPermission("wf_definition_view")
    @Operation(summary = "获取流程图")
    public byte[] getDiagram(@PathVariable String processDefinitionId) {
        return processDefinitionService.getDiagram(processDefinitionId);
    }

    @GetMapping("/form/{processKey}")
    @HasPermission("wf_definition_view")
    @Operation(summary = "获取流程关联的表单")
    public R<FormRenderVO> getFormByProcessKey(@PathVariable String processKey) {
        return R.ok(processDefinitionService.getFormByProcessKey(processKey));
    }
}
