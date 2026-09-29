-- Add applicant-scoped leave recovery permissions without changing existing workflow recovery grants.
INSERT INTO sys_menu (id, name, en_name, permission, path, parent_id, icon, visible, sn, type,
                      keep_alive, embedded, create_time)
SELECT 5015, '请假恢复', 'leaveRecovery', NULL, '/demo/leave/recovery', 5000, 'ele-Refresh', '0', 5, '0',
       '0', '0', CURRENT_TIMESTAMP
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 5015);
INSERT INTO sys_menu (id, name, en_name, permission, path, parent_id, icon, visible, sn, type,
                      keep_alive, embedded, create_time)
SELECT 5016, '查看请假恢复', NULL, 'demo_leave_recovery_view', NULL, 5015, NULL, '0', 1, '1',
       '0', '0', CURRENT_TIMESTAMP
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 5016);
INSERT INTO sys_menu (id, name, en_name, permission, path, parent_id, icon, visible, sn, type,
                      keep_alive, embedded, create_time)
SELECT 5017, '执行请假恢复', NULL, 'demo_leave_recovery_edit', NULL, 5015, NULL, '0', 2, '1',
       '0', '0', CURRENT_TIMESTAMP
 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 5017);
INSERT INTO sys_role_menu (role_id, menu_id, create_time)
SELECT 1, 5015, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 5015);
INSERT INTO sys_role_menu (role_id, menu_id, create_time)
SELECT 1, 5016, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 5016);
INSERT INTO sys_role_menu (role_id, menu_id, create_time)
SELECT 1, 5017, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 5017);
