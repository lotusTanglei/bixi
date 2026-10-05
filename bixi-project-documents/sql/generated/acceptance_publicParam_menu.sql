-- Generated permissions for 公共参数配置表; stable IDs are reserved for the Acceptance slice.
INSERT INTO sys_menu
    (id, name, permission, path, parent_id, icon, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (7210, '公共参数配置表', NULL, '/acceptance/publicParam/index', 2000,
     'ele-List', '1', 91, '0', '0', '0', CURRENT_TIMESTAMP, '0', '0', '0', 1);

INSERT INTO sys_menu
    (id, name, permission, parent_id, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (7211, '查看', 'acceptance_public_param_view', 7210, '0', 1, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7212, '新增', 'acceptance_public_param_add', 7210, '0', 2, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7213, '修改', 'acceptance_public_param_edit', 7210, '0', 3, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7214, '删除', 'acceptance_public_param_del', 7210, '0', 4, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7215, '导入', 'acceptance_public_param_import', 7210, '0', 5, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7216, '导出', 'acceptance_public_param_export', 7210, '0', 6, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, create_time)
VALUES
    (1, 7210, CURRENT_TIMESTAMP),
    (1, 7211, CURRENT_TIMESTAMP),
    (1, 7212, CURRENT_TIMESTAMP),
    (1, 7213, CURRENT_TIMESTAMP),
    (1, 7214, CURRENT_TIMESTAMP),
    (1, 7215, CURRENT_TIMESTAMP),
    (1, 7216, CURRENT_TIMESTAMP);
