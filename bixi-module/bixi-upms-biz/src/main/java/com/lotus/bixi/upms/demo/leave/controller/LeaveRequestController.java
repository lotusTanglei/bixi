package com.lotus.bixi.upms.demo.leave.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.upms.demo.leave.dto.LeaveRequestDTO;
import com.lotus.bixi.upms.demo.leave.dto.LeaveApproverVO;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.service.LeaveRequestService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@RequestMapping("/demo/leave")
@Tag(name = "请假申请")
public class LeaveRequestController {
    private final LeaveRequestService leaves;

    @GetMapping("/approvers") @HasPermission("demo_leave_view")
    public R<Page<LeaveApproverVO>> approvers(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "100") long size,
                                            @RequestParam(required = false) String name) {
        return R.ok(leaves.approvers(current, size, name));
    }

    @GetMapping("/page") @HasPermission("demo_leave_view")
    public R<Page<LeaveRequest>> page(Page<LeaveRequest> page, @RequestParam(required = false) String leaveStatus) {
        return R.ok(leaves.page(page, leaveStatus));
    }
    @GetMapping("/details/{id}") @HasPermission("demo_leave_view")
    public R<LeaveRequest> details(@PathVariable Long id) { return R.ok(leaves.details(id)); }

    @PostMapping @HasPermission("demo_leave_add") @SysLog("新增请假申请")
    public R<LeaveRequest> create(@Valid @RequestBody LeaveRequestDTO dto) { return R.ok(leaves.create(dto)); }

    @PutMapping("/{id}") @HasPermission("demo_leave_edit") @SysLog("修改请假申请")
    public R<LeaveRequest> update(@PathVariable Long id, @Valid @RequestBody LeaveRequestDTO dto) { return R.ok(leaves.update(id, dto)); }

    @DeleteMapping("/{id}") @HasPermission("demo_leave_del") @SysLog("删除请假申请")
    public R<Void> delete(@PathVariable Long id) { leaves.delete(id); return R.ok(); }

    @PostMapping("/{id}/submit") @HasPermission("demo_leave_edit") @SysLog("提交请假申请")
    public R<LeaveRequest> submit(@PathVariable Long id) { return R.ok(leaves.submit(id)); }

    @PostMapping("/{id}/refresh") @HasPermission("demo_leave_edit") @SysLog("刷新请假状态")
    public R<LeaveRequest> refresh(@PathVariable Long id) { return R.ok(leaves.refresh(id)); }

    @GetMapping("/{id}/history") @HasPermission("demo_leave_view")
    public R<List<ApprovalRecordVO>> history(@PathVariable Long id) { return R.ok(leaves.history(id)); }
}
