package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@ConditionalOnWorkflowEnabled
@RestController
@AllArgsConstructor
@RequestMapping("/workflow/definition")
@Tag(description = "definition", name = "流程定义管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class ProcessDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    @PostMapping("/deploy-demo")
    @HasPermission("workflow_definition_edit")
    @SysLog("部署请假流程示例")
    public R<ProcessDefinitionVO> deployDemo() {
        return R.ok(processDefinitionService.deployDemo());
    }

    @PostMapping("/deploy-demo/v3")
    @HasPermission("workflow_definition_edit")
    @SysLog("部署请假流程示例 v3")
    @Operation(summary = "显式部署请假流程 v3")
    public R<ProcessDefinitionVO> deployDemoV3() {
        return R.ok(processDefinitionService.deployDemoV3());
    }

    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @HasPermission("workflow_definition_edit")
    @SysLog("上传部署流程定义")
    @Operation(summary = "上传并部署 BPMN 流程定义")
    public R<ProcessDefinitionVO> deploy(@RequestPart("file") MultipartFile file,
                                        @RequestParam("name") String name,
                                        @RequestParam(value = "category", required = false) String category,
                                        @RequestParam(value = "formKey", required = false) String formKey) {
        return R.ok(processDefinitionService.deploy(file, name, category, formKey));
    }

    @GetMapping("/list")
    @HasPermission("workflow_definition_view")
    @Operation(summary = "查询流程定义列表")
    public R<List<ProcessDefinitionVO>> list(ProcessQueryDTO queryDTO) {
        return R.ok(processDefinitionService.listDefinitions(queryDTO));
    }

    @GetMapping("/startable")
    @HasPermission("workflow_process_add")
    @Operation(summary = "查询当前用户可发起的流程定义")
    public R<List<ProcessDefinitionVO>> startable(ProcessQueryDTO queryDTO) {
        return R.ok(processDefinitionService.listStartableDefinitions(queryDTO));
    }

    @GetMapping("/start-form/{processDefinitionId}")
    @HasPermission("workflow_process_add")
    @Operation(summary = "获取流程发起表单")
    public R<FormRenderVO> getStartForm(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.getStartForm(processDefinitionId));
    }

    @GetMapping("/{processKey}")
    @HasPermission("workflow_definition_view")
    @Operation(summary = "查询流程定义详情")
    public R<ProcessDefinitionVO> getByProcessKey(@PathVariable String processKey) {
        return R.ok(processDefinitionService.getByProcessKey(processKey));
    }

    @PutMapping("/suspend/{processDefinitionId}")
    @SysLog("挂起流程定义")
    @HasPermission("workflow_definition_edit")
    @Operation(summary = "挂起流程定义")
    public R<Boolean> suspend(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.suspend(processDefinitionId));
    }

    @PutMapping("/activate/{processDefinitionId}")
    @SysLog("激活流程定义")
    @HasPermission("workflow_definition_edit")
    @Operation(summary = "激活流程定义")
    public R<Boolean> activate(@PathVariable String processDefinitionId) {
        return R.ok(processDefinitionService.activate(processDefinitionId));
    }

    @GetMapping(value = "/diagram/{processDefinitionId}", produces = MediaType.IMAGE_PNG_VALUE)
    @HasPermission("workflow_definition_view")
    @Operation(summary = "获取流程图")
    public byte[] getDiagram(@PathVariable String processDefinitionId) {
        return processDefinitionService.getDiagram(processDefinitionId);
    }

    @GetMapping("/form/{processKey}")
    @HasPermission("workflow_definition_view")
    @Operation(summary = "获取流程关联的表单")
    public R<FormRenderVO> getFormByProcessKey(@PathVariable String processKey) {
        return R.ok(processDefinitionService.getFormByProcessKey(processKey));
    }
}
