-- Bind recovery audit replay identity to the tenant that performed the action.
DELIMITER $$
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_recovery_tenant_scope_20260929$$
CREATE PROCEDURE bixi_migrate_workflow_recovery_tenant_scope_20260929()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit'
        AND column_name = 'tenant_scope') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN tenant_scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER id;
  END IF;

  DROP TEMPORARY TABLE IF EXISTS bixi_recovery_tenant_candidates_20260929;
  CREATE TEMPORARY TABLE bixi_recovery_tenant_candidates_20260929 (
    audit_id BIGINT NOT NULL,
    tenant_scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    KEY idx_recovery_tenant_candidate (audit_id, tenant_scope)
  ) ENGINE=InnoDB;

  INSERT INTO bixi_recovery_tenant_candidates_20260929 (audit_id, tenant_scope)
  SELECT a.id, CASE JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.tenantScope'))
           WHEN 'default' THEN '1'
           ELSE JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) END
    FROM wf_recovery_audit a
    JOIN reliable_outbox o ON o.source_owner = a.owner AND o.event_id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND JSON_TYPE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) = 'STRING'
     AND (JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) = 'default'
       OR JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) REGEXP '^[1-9][0-9]{0,18}$')
  UNION ALL
  SELECT a.id, CASE JSON_UNQUOTE(JSON_EXTRACT(i.payload_json, '$.tenantScope'))
           WHEN 'default' THEN '1'
           ELSE JSON_UNQUOTE(JSON_EXTRACT(i.payload_json, '$.tenantScope')) END
    FROM wf_recovery_audit a
    JOIN reliable_inbox i ON i.target_owner = a.owner AND i.event_id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND JSON_TYPE(JSON_EXTRACT(i.payload_json, '$.tenantScope')) = 'STRING'
     AND (JSON_UNQUOTE(JSON_EXTRACT(i.payload_json, '$.tenantScope')) = 'default'
       OR JSON_UNQUOTE(JSON_EXTRACT(i.payload_json, '$.tenantScope')) REGEXP '^[1-9][0-9]{0,18}$')
  UNION ALL
  SELECT a.id, CASE c.tenant_scope WHEN 'default' THEN '1' ELSE c.tenant_scope END
    FROM wf_recovery_audit a
    JOIN wf_command c ON c.id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND (c.tenant_scope = 'default' OR c.tenant_scope REGEXP '^[1-9][0-9]{0,18}$')
  UNION ALL
  SELECT a.id, CASE t.tenant_scope WHEN 'default' THEN '1' ELSE t.tenant_scope END
    FROM wf_recovery_audit a
    JOIN wf_business_task t ON t.operation_id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND (t.tenant_scope = 'default' OR t.tenant_scope REGEXP '^[1-9][0-9]{0,18}$')
  UNION ALL
  SELECT a.id, CASE c.tenant_scope WHEN 'default' THEN '1' ELSE c.tenant_scope END
    FROM wf_recovery_audit a
    JOIN demo_leave_command c ON c.command_id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND (c.tenant_scope = 'default' OR c.tenant_scope REGEXP '^[1-9][0-9]{0,18}$')
  UNION ALL
  SELECT a.id, CAST(b.tenant_id AS CHAR)
    FROM wf_recovery_audit a
    JOIN demo_leave_booking b ON b.operation_id = a.event_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '') AND b.tenant_id > 0
  UNION ALL
  SELECT a.id, CAST(u.tenant_id AS CHAR)
    FROM wf_recovery_audit a
    JOIN sys_user u ON u.id = a.actor_id
   WHERE (a.tenant_scope IS NULL OR a.tenant_scope = '')
     AND a.request_id IS NULL AND u.tenant_id > 0;

  UPDATE wf_recovery_audit a
  LEFT JOIN (
    SELECT audit_id,
           CASE WHEN COUNT(DISTINCT tenant_scope) = 1 THEN MIN(tenant_scope)
                ELSE 'legacy-unresolved' END AS tenant_scope
      FROM bixi_recovery_tenant_candidates_20260929
     GROUP BY audit_id
  ) resolved ON resolved.audit_id = a.id
     SET a.tenant_scope = COALESCE(resolved.tenant_scope, 'legacy-unresolved')
   WHERE a.tenant_scope IS NULL OR a.tenant_scope = '';
  DROP TEMPORARY TABLE bixi_recovery_tenant_candidates_20260929;
  ALTER TABLE wf_recovery_audit
    MODIFY COLUMN tenant_scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER id;

  IF EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit'
        AND index_name = 'uk_wf_recovery_request') THEN
    ALTER TABLE wf_recovery_audit DROP INDEX uk_wf_recovery_request;
  END IF;
  ALTER TABLE wf_recovery_audit
    ADD UNIQUE KEY uk_wf_recovery_request (tenant_scope, owner, action, request_id);
END$$
CALL bixi_migrate_workflow_recovery_tenant_scope_20260929()$$
DROP PROCEDURE bixi_migrate_workflow_recovery_tenant_scope_20260929$$
DELIMITER ;
