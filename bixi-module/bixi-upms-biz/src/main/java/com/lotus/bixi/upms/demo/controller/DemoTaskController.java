package com.lotus.bixi.upms.demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.demo.dto.DemoTaskQuery;
import com.lotus.bixi.upms.demo.entity.DemoTask;
import com.lotus.bixi.upms.demo.service.DemoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/demo/task")
@Tag(description = "demoTask", name = "示例任务管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class DemoTaskController {

    private final DemoTaskService demoTaskService;

    @GetMapping("/page")
    @Operation(summary = "分页查询示例任务")
    @HasPermission("demo_task_view")
    public R<Page<DemoTask>> page(@ParameterObject Page<DemoTask> page,
                                  @ParameterObject DemoTaskQuery query) {
        return R.ok(demoTaskService.pageTasks(page, query));
    }

    @GetMapping("/details/{id}")
    @Operation(summary = "查询示例任务详情")
    @HasPermission("demo_task_view")
    public R<DemoTask> details(@PathVariable Long id) {
        return R.ok(demoTaskService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增示例任务")
    @SysLog("新增示例任务")
    @HasPermission("demo_task_add")
    public R<Boolean> save(@Valid @RequestBody DemoTask task) {
        return R.ok(demoTaskService.save(task));
    }

    @PutMapping
    @Operation(summary = "修改示例任务")
    @SysLog("修改示例任务")
    @HasPermission("demo_task_edit")
    public R<Boolean> update(@Valid @RequestBody DemoTask task) {
        return R.ok(demoTaskService.updateById(task));
    }

    @DeleteMapping
    @Operation(summary = "删除示例任务")
    @SysLog("删除示例任务")
    @HasPermission("demo_task_del")
    public R<Boolean> delete(@RequestBody List<Long> ids) {
        return R.ok(demoTaskService.removeBatchByIds(ids));
    }

}
