-- Additive/idempotent migration. Existing instances keep NULL unless explicitly reconciled.
-- Business notification must not depend on Flowable's optional history variable storage.
SET @workflow_round_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance' AND column_name = 'business_round'),
    'SELECT 1',
    'ALTER TABLE wf_process_instance ADD COLUMN business_round INT DEFAULT NULL COMMENT ''业务申请轮次'' AFTER business_id'
);
PREPARE workflow_round_statement FROM @workflow_round_ddl;
EXECUTE workflow_round_statement;
DEALLOCATE PREPARE workflow_round_statement;
