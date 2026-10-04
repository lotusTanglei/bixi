-- Generated permissions for 字典表; review the parent menu before applying.
SET @generated_menu_id = UUID_SHORT();
INSERT INTO sys_menu
    (id, name, permission, path, parent_id, icon, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (@generated_menu_id, '字典表', NULL, '/acceptance/dictAggregate/index', 2000,
     'ele-List', '1', 90, '0', '0', '0', CURRENT_TIMESTAMP, '0', '0', '0', NULL);

INSERT INTO sys_menu
    (id, name, permission, parent_id, visible, sn, keep_alive, embedded, type,
     create_time, del_flag, status, data_status, tenant_id)
VALUES
    (UUID_SHORT(), '查看', 'acceptance_dict_aggregate_view', @generated_menu_id, '0', 1, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
    (UUID_SHORT(), '新增', 'acceptance_dict_aggregate_add', @generated_menu_id, '0', 2, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
    (UUID_SHORT(), '修改', 'acceptance_dict_aggregate_edit', @generated_menu_id, '0', 3, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
    (UUID_SHORT(), '删除', 'acceptance_dict_aggregate_del', @generated_menu_id, '0', 4, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
	    (UUID_SHORT(), '导入', 'acceptance_dict_aggregate_import', @generated_menu_id, '0', 5, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
	    (UUID_SHORT(), '导出', 'acceptance_dict_aggregate_export', @generated_menu_id, '0', 6, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL);

INSERT INTO sys_menu
	    (id, name, permission, parent_id, visible, sn, keep_alive, embedded, type,
	     create_time, del_flag, status, data_status, tenant_id)
VALUES
	    (UUID_SHORT(), '明细新增', 'acceptance_sys_dict_item_add', @generated_menu_id, '0', 7, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
	    (UUID_SHORT(), '明细修改', 'acceptance_sys_dict_item_edit', @generated_menu_id, '0', 8, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL),
	    (UUID_SHORT(), '明细删除', 'acceptance_sys_dict_item_del', @generated_menu_id, '0', 9, '0', '0', '1', CURRENT_TIMESTAMP, '0', '0', '0', NULL);
