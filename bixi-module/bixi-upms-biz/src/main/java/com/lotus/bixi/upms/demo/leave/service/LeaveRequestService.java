package com.lotus.bixi.upms.demo.leave.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.upms.demo.leave.dto.LeaveRequestDTO;
import com.lotus.bixi.upms.demo.leave.dto.LeaveApproverVO;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventPublisher;
import com.lotus.bixi.upms.service.SysUserService;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One leave implementation for cloud and single; only workflow-api crosses the module boundary. */
@Slf4j
@Service
@ConditionalOnWorkflowEnabled
public class LeaveRequestService {
    private static final String PROCESS_KEY = "demo_leave_approval";
    private static final String BUSINESS_TABLE = "demo_leave_request";
    private static final List<String> TERMINAL = List.of("APPROVED", "REJECTED", "CANCELED");
    private static final List<String> STATES = List.of("DRAFT", "SUBMITTING", "IN_REVIEW", "APPROVED", "REJECTED", "CANCELED");
    private final LeaveRequestMapper mapper;
    private final WorkflowService workflows;
    private final SysUserService users;
    private final ObjectProvider<LeaveWorkflowEventPublisher> reliablePublisher;
    private final TransactionTemplate transaction;

    public LeaveRequestService(LeaveRequestMapper mapper, WorkflowService workflows, SysUserService users,
                               PlatformTransactionManager transactionManager,
                               ObjectProvider<LeaveWorkflowEventPublisher> reliablePublisher) {
        this.mapper = mapper;
        this.workflows = workflows;
        this.users = users;
        this.reliablePublisher = reliablePublisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @HasPermission("demo_leave_view")
    public Page<LeaveApproverVO> approvers(long current, long size, String name) {
        if (current < 1 || size < 1 || size > 100 || (name != null && name.length() > 64)) {
            throw new IllegalArgumentException("审批人查询参数无效");
        }
        Page<SysUser> found = users.page(new Page<>(current, size), Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getName, SysUser::getUsername)
                .ne(SysUser::getId, caller()).eq(SysUser::getLockFlag, "0").eq(SysUser::getStatus, "0")
                .eq(SysUser::getDelFlag, "0")
                .and(StringUtils.hasText(name), query -> query.like(SysUser::getName, name).or().like(SysUser::getUsername, name))
                .orderByAsc(SysUser::getId));
        Page<LeaveApproverVO> result = new Page<>(found.getCurrent(), found.getSize(), found.getTotal());
        result.setRecords(found.getRecords().stream()
                .map(user -> new LeaveApproverVO(user.getId(), user.getName(), user.getUsername())).toList());
        return result;
    }

    @HasPermission("demo_leave_add")
    public LeaveRequest create(LeaveRequestDTO dto) {
        Long applicant = caller();
        validateRequest(dto, applicant);
        return transaction.execute(status -> {
            LeaveRequest leave = new LeaveRequest();
            leave.setId(IdWorker.getId());
            leave.setApplicantId(applicant);
            leave.setApproverId(dto.getApproverId());
            leave.setStartDate(dto.getStartDate());
            leave.setEndDate(dto.getEndDate());
            leave.setReason(dto.getReason().trim());
            leave.setLeaveStatus("DRAFT");
            leave.setRound(1);
            leave.setBusinessKey("demo_leave:" + leave.getId() + ":1");
            mapper.insert(leave);
            return required(leave.getId());
        });
    }

    @HasPermission("demo_leave_edit")
    public LeaveRequest update(Long id, LeaveRequestDTO dto) {
        Long applicant = caller();
        return transaction.execute(status -> {
            LeaveRequest leave = ownedDraft(id, applicant);
            validateRequest(dto, applicant);
            requireChanged(mapper.update(null, draftUpdate(leave, applicant)
                    .set(LeaveRequest::getApproverId, dto.getApproverId())
                    .set(LeaveRequest::getStartDate, dto.getStartDate())
                    .set(LeaveRequest::getEndDate, dto.getEndDate())
                    .set(LeaveRequest::getReason, dto.getReason().trim())));
            return required(id);
        });
    }

    @HasPermission("demo_leave_del")
    public void delete(Long id) {
        Long applicant = caller();
        transaction.executeWithoutResult(status -> {
            LeaveRequest leave = ownedDraft(id, applicant);
            requireChanged(mapper.update(null, draftUpdate(leave, applicant).set(LeaveRequest::getDelFlag, "1")));
        });
    }

    @HasPermission("demo_leave_view")
    public Page<LeaveRequest> page(Page<LeaveRequest> page, String leaveStatus) {
        if (page == null || page.getCurrent() < 1 || page.getSize() < 1 || page.getSize() > 100) {
            throw new IllegalArgumentException("分页参数无效，每页最多100条");
        }
        if (StringUtils.hasText(leaveStatus) && !STATES.contains(leaveStatus)) {
            throw new IllegalArgumentException("请假状态无效");
        }
        return mapper.selectPage(page, Wrappers.<LeaveRequest>lambdaQuery()
                .eq(LeaveRequest::getApplicantId, caller())
                .eq(StringUtils.hasText(leaveStatus), LeaveRequest::getLeaveStatus, leaveStatus)
                .orderByDesc(LeaveRequest::getCreateTime).orderByDesc(LeaveRequest::getId));
    }

    @HasPermission("demo_leave_view")
    public LeaveRequest details(Long id) {
        LeaveRequest leave = required(id);
        authorizeRead(leave);
        return leave;
    }

    /** Suspend any ambient transaction: the start call must follow a committed reservation. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @HasPermission("demo_leave_edit")
    public LeaveRequest submit(Long id) {
        Long applicant = caller();
        LeaveWorkflowEventPublisher publisher = reliablePublisher.getIfAvailable();
        if (publisher != null) {
            var actor = SecurityUtils.getUser();
            return transaction.execute(status -> {
                LeaveRequest leave = ownedDraft(id, applicant);
                validateApprover(leave.getApproverId(), applicant);
                requireChanged(mapper.update(null, draftUpdate(leave, applicant)
                        .set(LeaveRequest::getLeaveStatus, "SUBMITTING")
                        .set(LeaveRequest::getSubmittedAt, LocalDateTime.now())));
                LeaveRequest reserved = required(id);
                LeaveWorkflowEventPublisher.Published published = publisher.publishStart(reserved, actor);
                requireChanged(mapper.update(null, identityUpdate(reserved)
                        .eq(LeaveRequest::getLeaveStatus, "SUBMITTING")
                        .set(LeaveRequest::getStartCommandId, published.commandId())
                        .set(LeaveRequest::getStartRequestHash, published.requestHash())));
                return required(id);
            });
        }
        LeaveRequest reserved = transaction.execute(status -> {
            LeaveRequest leave = ownedDraft(id, applicant);
            validateApprover(leave.getApproverId(), applicant);
            requireChanged(mapper.update(null, draftUpdate(leave, applicant)
                    .set(LeaveRequest::getLeaveStatus, "SUBMITTING")
                    .set(LeaveRequest::getSubmittedAt, LocalDateTime.now())));
            return required(id);
        });
        ProcessStartDTO start = new ProcessStartDTO();
        start.setRequestId(UUID.nameUUIDFromBytes(("upms:leave:start:" + reserved.getId() + ":" + reserved.getRound())
                .getBytes(StandardCharsets.UTF_8)).toString());
        start.setProcessKey(PROCESS_KEY);
        start.setBusinessTable(BUSINESS_TABLE);
        start.setBusinessId(reserved.getId());
        start.setBusinessKey(reserved.getBusinessKey());
        start.setTitle("请假申请 " + reserved.getStartDate() + " 至 " + reserved.getEndDate());
        start.setVariables(Map.of("approverId", reserved.getApproverId().toString(), "businessRound", reserved.getRound()));
        // Any exception here is ambiguous. Keep SUBMITTING; stage 2 adds durable reconciliation.
        ProcessInstanceVO process = response(workflows.startProcess(start));
        validateIdentity(reserved, process);
        LeaveRequest bound = transaction.execute(status -> {
            requireChanged(mapper.update(null, identityUpdate(reserved)
                    .eq(LeaveRequest::getLeaveStatus, "SUBMITTING")
                    .isNull(LeaveRequest::getProcessInstanceId)
                    .set(LeaveRequest::getProcessInstanceId, process.getProcessInstanceId())
                    .set(LeaveRequest::getLeaveStatus, "IN_REVIEW")
                    .set(LeaveRequest::getUpdateBy, applicant)
                    .set(LeaveRequest::getUpdateTime, LocalDateTime.now())));
            applyProcess(required(id), process);
            return required(id);
        });
        // A callback can run before binding or the response can predate a fast completion.
        // A failed read leaves the confirmed binding available to the explicit refresh endpoint.
        ProcessInstanceVO latest;
        try {
            latest = response(workflows.getProcessInstance(process.getProcessInstanceId()));
        }
        catch (RuntimeException ex) {
            log.warn("请假流程已绑定，后续状态查询失败，请通过刷新核查: leaveId={}, processInstanceId={}", id, process.getProcessInstanceId());
            return bound;
        }
        return transaction.execute(status -> {
            applyProcess(required(id), latest);
            return required(id);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @HasPermission("demo_leave_edit")
    public LeaveRequest refresh(Long id) {
        LeaveRequest leave = required(id);
        authorizeRead(leave);
        if (!StringUtils.hasText(leave.getProcessInstanceId())) {
            throw new IllegalArgumentException("尚无已确认的流程绑定，不能自动重试，请联系管理员核查");
        }
        ProcessInstanceVO process = response(workflows.getProcessInstance(leave.getProcessInstanceId()));
        return transaction.execute(status -> {
            applyProcess(required(id), process);
            return required(id);
        });
    }

    @HasPermission("demo_leave_view")
    public List<ApprovalRecordVO> history(Long id) {
        LeaveRequest leave = required(id);
        authorizeRead(leave);
        if (!StringUtils.hasText(leave.getProcessInstanceId())) {
            return List.of();
        }
        return response(workflows.getApprovalHistory(leave.getProcessInstanceId()));
    }

    /** Invoked only by the trusted internal HTTP adapter or the local receiver. */
    @SysLog("回写请假审批结果")
    public void receive(WorkflowResultDTO result) {
        if (result == null || result.getSchemaVersion() != 1 || !StringUtils.hasText(result.getEventId())
                || result.getRound() < 1 || result.getBusinessId() == null || result.getEndTime() == null
                || !StringUtils.hasText(result.getProcessInstanceId())) {
            throw new IllegalArgumentException("流程结果参数无效");
        }
        String target = terminalState(result.getStatus());
        if (target == null) {
            throw new IllegalArgumentException("流程结果必须为终态");
        }
        transaction.executeWithoutResult(status -> {
            LeaveRequest leave = required(result.getBusinessId());
            if (!Objects.equals(result.getRound(), leave.getRound())
                    || !Objects.equals(result.getBusinessKey(), leave.getBusinessKey())
                    || !Objects.equals(result.getStartUserId(), leave.getApplicantId())
                    || !BUSINESS_TABLE.equals(result.getBusinessTable()) || !PROCESS_KEY.equals(result.getProcessKey())) {
                throw new IllegalArgumentException("流程结果与请假申请身份不匹配");
            }
            // Never let an early callback establish authority over an unconfirmed start.
            if (!StringUtils.hasText(leave.getProcessInstanceId())) {
                return;
            }
            if (!Objects.equals(result.getProcessInstanceId(), leave.getProcessInstanceId())) {
                throw new IllegalArgumentException("流程结果与已确认的流程绑定不匹配");
            }
            applyTerminal(leave, target, result.getEndTime());
        });
    }

    private void authorizeRead(LeaveRequest leave) {
        if (Objects.equals(leave.getApplicantId(), caller())) {
            return;
        }
        if (!StringUtils.hasText(leave.getProcessInstanceId())) {
            throw new AccessDeniedException("仅申请人可查看草稿");
        }
        // WorkflowService enforces actual process participation, including reassigned tasks.
        validateIdentity(leave, response(workflows.getProcessInstance(leave.getProcessInstanceId())));
    }

    private LeaveRequest ownedDraft(Long id, Long applicant) {
        LeaveRequest leave = required(id);
        if (!Objects.equals(leave.getApplicantId(), applicant)) {
            throw new AccessDeniedException("仅申请人可操作此申请");
        }
        if (!"DRAFT".equals(leave.getLeaveStatus())) {
            throw new IllegalArgumentException("仅草稿可修改、删除或提交");
        }
        return leave;
    }

    private LeaveRequest required(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("请假ID无效");
        LeaveRequest leave = mapper.selectById(id);
        if (leave == null) throw new IllegalArgumentException("请假申请不存在");
        return leave;
    }

    private void validateRequest(LeaveRequestDTO dto, Long applicant) {
        if (dto == null || dto.getStartDate() == null || dto.getEndDate() == null
                || dto.getEndDate().isBefore(dto.getStartDate()) || !StringUtils.hasText(dto.getReason())
                || dto.getReason().length() > 1000) {
            throw new IllegalArgumentException("请填写有效的起止日期和1000字以内的请假原因");
        }
        validateApprover(dto.getApproverId(), applicant);
    }

    private void validateApprover(Long approver, Long applicant) {
        if (approver == null || approver <= 0 || approver.equals(applicant)) {
            throw new IllegalArgumentException("请选择申请人以外的有效审批人");
        }
        var user = users.getById(approver);
        if (user == null || !"0".equals(user.getLockFlag()) || !"0".equals(user.getDelFlag()) || !"0".equals(user.getStatus())) {
            throw new IllegalArgumentException("审批人不存在或已停用");
        }
    }

    private static Long caller() {
        var user = SecurityUtils.getUser();
        if (user == null || user.getId() == null) throw new AccessDeniedException("请先登录");
        return user.getId();
    }

    private LambdaUpdateWrapper<LeaveRequest> draftUpdate(LeaveRequest leave, Long applicant) {
        return identityUpdate(leave).eq(LeaveRequest::getApplicantId, applicant)
                .eq(LeaveRequest::getLeaveStatus, "DRAFT")
                .set(LeaveRequest::getUpdateBy, applicant).set(LeaveRequest::getUpdateTime, LocalDateTime.now());
    }

    private LambdaUpdateWrapper<LeaveRequest> identityUpdate(LeaveRequest leave) {
        return Wrappers.<LeaveRequest>lambdaUpdate().eq(LeaveRequest::getId, leave.getId())
                .eq(LeaveRequest::getBusinessKey, leave.getBusinessKey()).eq(LeaveRequest::getRound, leave.getRound());
    }

    private static void requireChanged(int updated) {
        if (updated != 1) throw new IllegalArgumentException("申请状态已变化，请刷新后重试");
    }

    private static <T> T response(R<T> response) {
        if (response == null || response.getCode() != CommonConstants.SUCCESS || response.getData() == null) {
            throw new IllegalStateException("工作流调用未得到有效响应，请核查当前状态");
        }
        return response.getData();
    }

    private void validateIdentity(LeaveRequest leave, ProcessInstanceVO process) {
        if (process == null || !StringUtils.hasText(process.getProcessInstanceId())
                || !PROCESS_KEY.equals(process.getProcessKey()) || !BUSINESS_TABLE.equals(process.getBusinessTable())
                || !Objects.equals(leave.getId(), process.getBusinessId())
                || !Objects.equals(leave.getBusinessKey(), process.getBusinessKey())
                || !Objects.equals(leave.getApplicantId(), process.getStartUserId())
                || (StringUtils.hasText(leave.getProcessInstanceId()) && !leave.getProcessInstanceId().equals(process.getProcessInstanceId()))) {
            throw new IllegalArgumentException("流程响应与请假申请身份不匹配");
        }
    }

    private void applyProcess(LeaveRequest leave, ProcessInstanceVO process) {
        validateIdentity(leave, process);
        String target = terminalState(process.getStatus());
        if (target != null) {
            if (process.getEndTime() == null) throw new IllegalArgumentException("流程终态缺少结束时间");
            applyTerminal(leave, target, process.getEndTime());
        }
        else if (!"running".equals(process.getStatus()) && !"suspended".equals(process.getStatus())) {
            throw new IllegalArgumentException("未知流程状态");
        }
    }

    private void applyTerminal(LeaveRequest leave, String target, LocalDateTime endedAt) {
        if (TERMINAL.contains(leave.getLeaveStatus())) {
            if (!target.equals(leave.getLeaveStatus())) throw new IllegalStateException("请假结果已确定，拒绝矛盾终态");
            return;
        }
        int updated = mapper.update(null, identityUpdate(leave)
                .eq(LeaveRequest::getProcessInstanceId, leave.getProcessInstanceId())
                .eq(LeaveRequest::getLeaveStatus, "IN_REVIEW")
                .set(LeaveRequest::getLeaveStatus, target).set(LeaveRequest::getEndedAt, endedAt)
                .set(LeaveRequest::getUpdateTime, LocalDateTime.now()));
        // Concurrent duplicate events converge to the same state; conflicting outcomes remain rejected.
        if (updated == 0) {
            // A locking read sees the concurrent winner even under MySQL REPEATABLE READ;
            // a plain select could reuse this transaction's earlier IN_REVIEW snapshot.
            LeaveRequest current = mapper.selectOne(Wrappers.<LeaveRequest>lambdaQuery()
                    .eq(LeaveRequest::getId, leave.getId()).last("FOR UPDATE"));
            if (current == null || !target.equals(current.getLeaveStatus())) {
                throw new IllegalStateException("请假结果冲突，请核查流程状态");
            }
        }
    }

    private static String terminalState(String status) {
        if (status == null) return null;
        return switch (status) {
            case "completed" -> "APPROVED";
            case "rejected" -> "REJECTED";
            case "terminated" -> "CANCELED";
            default -> null;
        };
    }
}
