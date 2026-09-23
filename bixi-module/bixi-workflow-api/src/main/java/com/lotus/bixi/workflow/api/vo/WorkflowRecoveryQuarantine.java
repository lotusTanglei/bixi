package com.lotus.bixi.workflow.api.vo;

import com.lotus.bixi.workflow.api.event.WorkflowRecoveryMetadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure merge policy for bounded, redacted quarantine entries in owner recovery reports. */
public final class WorkflowRecoveryQuarantine {
    private WorkflowRecoveryQuarantine() {
    }

    public static List<WorkflowRecoveryReconciliationVO> merge(String owner,
            List<WorkflowRecoveryReconciliationVO> businessRows, List<Evidence> evidence, int limit) {
        List<WorkflowRecoveryReconciliationVO> merged = new ArrayList<>();
        Set<Integer> replaced = new HashSet<>();
        for (Evidence item : evidence) {
            int match = findBusinessRow(businessRows, item.metadata(), replaced);
            if (match >= 0) {
                replaced.add(match);
                merged.add(asQuarantined(businessRows.get(match), item));
            }
            else {
                merged.add(asQuarantined(owner, item));
            }
            if (merged.size() == limit) {
                return List.copyOf(merged);
            }
        }
        for (int index = 0; index < businessRows.size() && merged.size() < limit; index++) {
            if (!replaced.contains(index)) {
                merged.add(businessRows.get(index));
            }
        }
        return List.copyOf(merged);
    }

    private static int findBusinessRow(List<WorkflowRecoveryReconciliationVO> rows,
            WorkflowRecoveryMetadata metadata, Set<Integer> replaced) {
        if (metadata == null) return -1;
        for (int index = 0; index < rows.size(); index++) {
            WorkflowRecoveryReconciliationVO row = rows.get(index);
            if (!replaced.contains(index)
                    && (same(metadata.processInstanceId(), row.processInstanceId())
                    || same(metadata.businessKey(), row.businessKey()))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private static WorkflowRecoveryReconciliationVO asQuarantined(
            WorkflowRecoveryReconciliationVO row, Evidence evidence) {
        WorkflowRecoveryMetadata metadata = evidence.metadata();
        return new WorkflowRecoveryReconciliationVO(row.owner(), evidence.eventId(), evidence.eventType(),
                row.businessTable(), row.businessId(), row.businessKey(), row.processInstanceId(), row.round(),
                first(metadata == null ? null : metadata.requestId(), row.requestId()), row.commandStatus(),
                first(metadata == null ? null : metadata.operationId(), row.operationId()),
                row.businessTaskStatus(),
                first(metadata == null ? null : metadata.compensationId(), row.compensationId()),
                evidence.eventId(),
                row.durableStatus(), row.businessStatus(), "QUARANTINED", detail(evidence.reason()));
    }

    private static WorkflowRecoveryReconciliationVO asQuarantined(String owner, Evidence evidence) {
        WorkflowRecoveryMetadata metadata = evidence.metadata();
        return new WorkflowRecoveryReconciliationVO(owner, evidence.eventId(), evidence.eventType(),
                metadata == null ? null : metadata.businessTable(),
                metadata == null ? null : metadata.businessId(),
                metadata == null ? null : metadata.businessKey(),
                metadata == null ? null : metadata.processInstanceId(),
                metadata == null ? null : metadata.round(),
                metadata == null ? null : metadata.requestId(), null,
                metadata == null ? null : metadata.operationId(), null,
                metadata == null ? null : metadata.compensationId(), evidence.eventId(), null, null,
                "QUARANTINED", detail(evidence.reason()));
    }

    private static String first(String preferred, String fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String detail(String reason) {
        return "隔离消息待处理：" + reason;
    }

    public record Evidence(String eventId, String eventType, String reason,
            WorkflowRecoveryMetadata metadata) {
    }
}
