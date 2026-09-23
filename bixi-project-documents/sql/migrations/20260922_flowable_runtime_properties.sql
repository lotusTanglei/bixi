-- Flowable 7.1.0 validates these settings after the engine is built even when
-- schema updates are disabled. Seed the pinned defaults during the maintenance
-- window so concurrent replicas do not race to insert the same property rows.

INSERT INTO ACT_GE_PROPERTY (NAME_, VALUE_, REV_)
SELECT 'cfg.execution-related-entities-count', 'true', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM ACT_GE_PROPERTY
    WHERE NAME_ = 'cfg.execution-related-entities-count'
);

INSERT INTO ACT_GE_PROPERTY (NAME_, VALUE_, REV_)
SELECT 'cfg.task-related-entities-count', 'true', 1
WHERE NOT EXISTS (
    SELECT 1
    FROM ACT_GE_PROPERTY
    WHERE NAME_ = 'cfg.task-related-entities-count'
);
