-- Generated permissions for 字典表; stable IDs are reserved for the Acceptance slice.
INSERT INTO sys_menu
    (id, name, permission, path, parent_id, icon, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (7200, '字典表', NULL, '/acceptance/dictAggregate/index', 2000,
     'ele-List', '1', 90, '0', '0', '0', CURRENT_TIMESTAMP, '0', '0', '0', 1);

INSERT INTO sys_menu
    (id, name, permission, parent_id, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (7201, '查看', 'acceptance_dict_aggregate_view', 7200, '0', 1, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7202, '新增', 'acceptance_dict_aggregate_add', 7200, '0', 2, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7203, '修改', 'acceptance_dict_aggregate_edit', 7200, '0', 3, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7204, '删除', 'acceptance_dict_aggregate_del', 7200, '0', 4, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7205, '导入', 'acceptance_dict_aggregate_import', 7200, '0', 5, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7206, '导出', 'acceptance_dict_aggregate_export', 7200, '0', 6, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7207, '明细新增', 'acceptance_sys_dict_item_add', 7200, '0', 7, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7208, '明细修改', 'acceptance_sys_dict_item_edit', 7200, '0', 8, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1),
    (7209, '明细删除', 'acceptance_sys_dict_item_del', 7200, '0', 9, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', 1);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id, create_time)
VALUES
    (1, 7200, CURRENT_TIMESTAMP),
    (1, 7201, CURRENT_TIMESTAMP),
    (1, 7202, CURRENT_TIMESTAMP),
    (1, 7203, CURRENT_TIMESTAMP),
    (1, 7204, CURRENT_TIMESTAMP),
    (1, 7205, CURRENT_TIMESTAMP),
    (1, 7206, CURRENT_TIMESTAMP),
    (1, 7207, CURRENT_TIMESTAMP),
    (1, 7208, CURRENT_TIMESTAMP),
    (1, 7209, CURRENT_TIMESTAMP);
