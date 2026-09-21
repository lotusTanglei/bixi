package com.lotus.bixi.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.service.WorkflowResultReceiver;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

/**
 * Stage-one notification of committed outcomes. A failed delivery is observable and
 * recoverable through business refresh; durable delivery is provided in stage two.
 */
@Slf4j
@Component
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
public class WorkflowResultNotifier {
    private static final Set<String> TERMINAL_STATES = Set.of("completed", "rejected", "terminated");
    private final WfProcessInstanceMapper instances;
    // Lazy receiver lookup prevents single's workflow -> leave -> workflow construction cycle.
    private final ObjectProvider<WorkflowResultReceiver> receivers;
    private final PlatformTransactionManager transactionManager;

    public void afterCommit(String processInstanceId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("流程结果通知必须注册在工作流事务内");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deliver(processInstanceId); }
        });
    }

    private void deliver(String processInstanceId) {
        try {
            // afterCommit still has the completed transaction's resources bound. Read in
            // a fresh transaction, then release it before entering the receiving module.
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setReadOnly(true);
            WorkflowResultDTO result = transaction.execute(ignored -> snapshot(processInstanceId));
            if (result == null) return;
            WorkflowResultReceiver receiver = receivers.getIfAvailable();
            if (receiver == null) throw new IllegalStateException("未配置业务结果接收适配器");
            var response = receiver.receive(result);
            if (response == null || response.getCode() != 0) {
                throw new IllegalStateException("业务接收方未确认结果");
            }
        } catch (Exception exception) {
            // The engine transaction is committed. Do not report a failed approval or
            // encourage retrying its side effect. The business refresh reads this result.
            log.error("Workflow result notification failed for process {}; business refresh required",
                    processInstanceId, exception);
        }
    }

    private WorkflowResultDTO snapshot(String processInstanceId) {
        WfProcessInstance instance = instances.selectOne(Wrappers.<WfProcessInstance>lambdaQuery()
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
        if (instance == null || !TERMINAL_STATES.contains(instance.getStatus())
                || instance.getBusinessId() == null || instance.getBusinessTable() == null
                || instance.getBusinessKey() == null || instance.getBusinessRound() == null) return null;

        int round = instance.getBusinessRound();
        if (round < 1) throw new IllegalStateException("流程申请轮次无效");
        var result = new WorkflowResultDTO();
        result.setEventId(processInstanceId + ":" + instance.getStatus());
        result.setProcessInstanceId(processInstanceId);
        result.setProcessKey(instance.getProcessKey());
        result.setBusinessKey(instance.getBusinessKey());
        result.setBusinessTable(instance.getBusinessTable());
        result.setBusinessId(instance.getBusinessId());
        result.setRound(round);
        result.setStartUserId(instance.getStartUserId());
        result.setStatus(instance.getStatus());
        result.setEndTime(instance.getEndTime());
        return result;
    }
}
