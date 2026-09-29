package com.lotus.bixi.upms.demo.leave.command;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Persists the accepted leave command, business transition, outbox write and response atomically. */
@Service
@ConditionalOnWorkflowEnabled
public class LeaveSubmitCommandExecutor {
    private static final int HASH_VERSION = 1;
    private static final String OPERATION = "START";
    private static final String SOURCE_OWNER = "upms";
    private static final Set<String> REPLAYABLE_STATUSES = Set.of("ACCEPTED", "STARTED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate write;
    private final TransactionTemplate independentRead;

    public LeaveSubmitCommandExecutor(DataSource dataSource, PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.json = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.write = new TransactionTemplate(transactionManager);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independentRead = new TransactionTemplate(transactionManager);
        this.independentRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independentRead.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public LeaveRequest execute(BixiUser actor, String clientRequestId, LeaveRequest intent, Work work) {
        WorkflowRequestDTO.requireRequestId(clientRequestId);
        Objects.requireNonNull(actor, "操作者不能为空");
        Objects.requireNonNull(intent, "请假申请不能为空");
        if (actor.getTenantId() == null || actor.getTenantId() <= 0 || actor.getId() == null || actor.getId() <= 0) {
            throw new IllegalArgumentException("租户或操作者上下文缺失");
        }
        String tenantScope = actor.getTenantId().toString();
        String requestHash = hash(actor, intent);
        Saved saved = find(tenantScope, actor.getId(), clientRequestId);
        if (saved != null) return replay(clientRequestId, requestHash, saved);

        String commandId = stableCommandId(tenantScope, actor.getId(), clientRequestId);
        try {
            return write.execute(status -> executeNew(actor, tenantScope, clientRequestId, commandId,
                    requestHash, intent, work));
        }
        catch (RequestCollision collision) {
            Saved winner = find(tenantScope, actor.getId(), clientRequestId);
            if (winner != null) return replay(clientRequestId, requestHash, winner);
            throw collision.cause();
        }
        catch (BusinessCollision collision) {
            throw new IllegalArgumentException("请假申请已提交，不能使用新的requestId重复提交", collision);
        }
    }

    public String hash(BixiUser actor, LeaveRequest intent) {
        Map<String, Object> content = new TreeMap<>();
        content.put("hashVersion", HASH_VERSION);
        content.put("sourceOwner", SOURCE_OWNER);
        content.put("operation", OPERATION);
        content.put("tenantScope", actor.getTenantId().toString());
        content.put("actorId", actor.getId());
        content.put("leaveId", intent.getId());
        content.put("round", intent.getRound());
        content.put("applicantId", intent.getApplicantId());
        content.put("approverId", intent.getApproverId());
        content.put("startDate", String.valueOf(intent.getStartDate()));
        content.put("endDate", String.valueOf(intent.getEndDate()));
        content.put("reason", intent.getReason());
        content.put("businessKey", intent.getBusinessKey());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(content)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("请假提交内容无法生成摘要", invalid);
        }
    }

    private LeaveRequest executeNew(BixiUser actor, String tenantScope, String clientRequestId, String commandId,
            String requestHash, LeaveRequest intent, Work work) {
        try {
            jdbc.update("""
                    INSERT INTO demo_leave_command
                        (command_id, tenant_scope, actor_id, actor_name, client_request_id, operation,
                         leave_id, round, request_hash, hash_version, payload_json, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'START', ?, ?, ?, ?, ?, 'ACCEPTED', ?)
                    """, commandId, tenantScope, actor.getId(), actor.getUsername(), clientRequestId,
                    intent.getId(), intent.getRound(), requestHash, HASH_VERSION, writeIntent(actor, intent),
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        catch (DuplicateKeyException collision) {
            String constraint = String.valueOf(collision.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (constraint.contains("uk_demo_leave_command_business")) throw new BusinessCollision(collision);
            // command_id is derived from the same request scope. After rollback the outer
            // read decides whether this was its committed winner or an unrelated constraint.
            throw new RequestCollision(collision);
        }

        LeaveRequest response = work.run(commandId, requestHash);
        String snapshot = writeResponse(response);
        int changed = jdbc.update("""
                UPDATE demo_leave_command
                SET response_json = ?, process_instance_id = ?, completed_at = ?
                WHERE command_id = ? AND status = 'ACCEPTED'
                """, snapshot, response.getProcessInstanceId(), LocalDateTime.now(ZoneOffset.UTC), commandId);
        if (changed != 1) throw new IllegalStateException("请假提交命令结果未保存");
        return readResponse(snapshot);
    }

    private Saved find(String tenantScope, Long actorId, String requestId) {
        return independentRead.execute(status -> jdbc.query("""
                SELECT request_hash, hash_version, status, response_json
                FROM demo_leave_command
                WHERE tenant_scope = ? AND actor_id = ? AND client_request_id = ?
                """, (rs, row) -> new Saved(rs.getString("request_hash"), rs.getInt("hash_version"),
                        rs.getString("status"), rs.getString("response_json")),
                tenantScope, actorId, requestId).stream().findFirst().orElse(null));
    }

    private LeaveRequest replay(String requestId, String requestHash, Saved saved) {
        if (saved.hashVersion() != HASH_VERSION || !requestHash.equals(saved.requestHash())) {
            throw new IllegalArgumentException("同一requestId已用于不同的请假提交");
        }
        if (!REPLAYABLE_STATUSES.contains(saved.status()) || saved.responseJson() == null) {
            throw new IllegalStateException("请假提交结果尚未成功提交");
        }
        return readResponse(saved.responseJson());
    }

    private String writeIntent(BixiUser actor, LeaveRequest intent) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("tenantScope", actor.getTenantId().toString());
        payload.put("actorId", actor.getId());
        payload.put("leaveId", intent.getId());
        payload.put("round", intent.getRound());
        payload.put("applicantId", intent.getApplicantId());
        payload.put("approverId", intent.getApproverId());
        payload.put("startDate", String.valueOf(intent.getStartDate()));
        payload.put("endDate", String.valueOf(intent.getEndDate()));
        payload.put("reason", intent.getReason());
        payload.put("businessKey", intent.getBusinessKey());
        try {
            return json.writeValueAsString(payload);
        }
        catch (java.io.IOException invalid) {
            throw new IllegalArgumentException("请假提交内容无法保存", invalid);
        }
    }

    private String writeResponse(LeaveRequest response) {
        try {
            return json.writeValueAsString(response);
        }
        catch (java.io.IOException invalid) {
            throw new IllegalStateException("请假提交响应无法保存", invalid);
        }
    }

    private LeaveRequest readResponse(String response) {
        try {
            return json.readValue(response, LeaveRequest.class);
        }
        catch (java.io.IOException invalid) {
            throw new IllegalStateException("请假提交响应无法读取", invalid);
        }
    }

    private static String stableCommandId(String tenantScope, Long actorId, String requestId) {
        return UUID.nameUUIDFromBytes(("leave:command:" + tenantScope + ":" + actorId + ":" + requestId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record Saved(String requestHash, int hashVersion, String status, String responseJson) { }

    private static final class RequestCollision extends RuntimeException {
        private final DuplicateKeyException cause;
        private RequestCollision(DuplicateKeyException cause) {
            super(cause);
            this.cause = cause;
        }
        private DuplicateKeyException cause() { return cause; }
    }

    private static final class BusinessCollision extends RuntimeException {
        private BusinessCollision(DuplicateKeyException cause) { super(cause); }
    }

    @FunctionalInterface
    public interface Work {
        LeaveRequest run(String commandId, String requestHash);
    }
}
