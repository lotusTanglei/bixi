package com.lotus.bixi.workflow.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Separate proxy: collision rollback completes before the facade reads a committed winner. */
@Service
@ConditionalOnWorkflowEnabled
public class WorkflowCommandTransaction {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WorkflowCommandTransaction(DataSource source, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(source);
        this.json = json;
    }

    record Saved(String requestHash, int hashVersion, String status, WorkflowCommandVO result) { }
    static class RequestKeyCollision extends RuntimeException {
        RequestKeyCollision(DuplicateKeyException cause) { super(cause); }
    }
    static class TerminalTaskCollision extends RuntimeException {
        TerminalTaskCollision(DuplicateKeyException cause) { super(cause); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Saved find(String tenantScope, Long actorId, String requestId) {
        return jdbc.query("""
                SELECT request_hash, hash_version, status, operation, resource_id, process_instance_id,
                       result_code, completed_at, response_json FROM wf_command
                WHERE tenant_scope = ? AND actor_id = ? AND request_id = ?
                """, (rs, row) -> {
            LocalDateTime completed = rs.getObject("completed_at", LocalDateTime.class);
            return new Saved(rs.getString("request_hash"), rs.getInt("hash_version"), rs.getString("status"),
                    new WorkflowCommandVO(requestId, rs.getString("operation"), rs.getString("resource_id"),
                            rs.getString("process_instance_id"), rs.getString("result_code"),
                            completed == null ? null : completed.toInstant(ZoneOffset.UTC), readTree(rs.getString("response_json"))));
        }, tenantScope, actorId, requestId).stream().findFirst().orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean hasSucceededTerminalTask(String tenantScope, Long actorId, String taskId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM wf_command
                WHERE tenant_scope = ? AND actor_id = ? AND terminal_task_id = ? AND status = 'SUCCEEDED'
                """, Integer.class, tenantScope, actorId, taskId);
        return count != null && count > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public <T> T executeNew(WorkflowCommandExecutor.CommandInput input, Class<T> resultType, Supplier<T> work) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO wf_command (id, tenant_scope, actor_id, actor_name, request_id, source_owner,
                        operation, resource_id, request_hash, hash_version, status, terminal_task_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EXECUTING', ?, ?)
                    """, id, input.tenantScope(), input.actorId(), input.actorName(), input.requestId(), input.sourceOwner(),
                    input.operation(), input.resourceId(), input.requestHash(), WorkflowRequestHasher.HASH_VERSION,
                    input.terminalTaskId(), LocalDateTime.now(ZoneOffset.UTC));
        } catch (DuplicateKeyException collision) {
            String constraint = String.valueOf(collision.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (constraint.contains("uk_wf_command_request")) throw new RequestKeyCollision(collision);
            if (constraint.contains("uk_wf_command_terminal_task")) {
                throw new TerminalTaskCollision(collision);
            }
            throw collision;
        }
        T result = work.get();
        String snapshot = write(result);
        String processId = result instanceof ProcessInstanceVO process ? process.getProcessInstanceId()
                : result instanceof WorkflowCommandExecutor.CommandResult command ? command.processInstanceId()
                : input.processInstanceId();
        int changed = jdbc.update("""
                UPDATE wf_command SET status = 'SUCCEEDED', response_json = ?, result_code = 'SUCCESS',
                    process_instance_id = ?, completed_at = ? WHERE id = ? AND status = 'EXECUTING'
                """, snapshot, processId, LocalDateTime.now(ZoneOffset.UTC), id);
        if (changed != 1) throw new IllegalStateException("流程命令结果未保存");
        return decode(readTree(snapshot), resultType);
    }

    <T> T decode(JsonNode value, Class<T> resultType) {
        try { return json.treeToValue(value, resultType); }
        catch (java.io.IOException e) { throw new IllegalStateException("流程命令结果不可读", e); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("流程命令结果不可保存", e); }
    }
    private JsonNode readTree(String value) {
        if (value == null) return null;
        try { return json.readTree(value); }
        catch (java.io.IOException e) { throw new IllegalStateException("流程命令结果不可读", e); }
    }
}
