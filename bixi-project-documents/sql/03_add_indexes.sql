-- =====================================================
-- 数据库索引优化脚本
-- 版本: 1.0.0
-- 日期: 2026-03-28
-- 说明: 添加性能优化索引（推荐执行）
-- =====================================================

-- =====================================================
-- 1. 核心系统模块索引优化
-- =====================================================

-- 用户表索引优化
CREATE INDEX idx_user_phone ON sys_user(phone);
CREATE INDEX idx_user_email ON sys_user(email);
CREATE INDEX idx_user_dept_id ON sys_user(dept_id);
CREATE INDEX idx_user_status_del ON sys_user(status, del_flag);
CREATE INDEX idx_user_create_time ON sys_user(create_time);

-- 角色表索引优化
CREATE INDEX idx_sys_role_code_status ON sys_role(code, status, del_flag);
CREATE INDEX idx_sys_role_name_status ON sys_role(name, status, del_flag);
CREATE INDEX idx_role_status_del ON sys_role(status, del_flag);

-- 菜单表索引优化
CREATE INDEX idx_menu_parent_id ON sys_menu(parent_id);
CREATE INDEX idx_menu_visible_status ON sys_menu(visible, status);
CREATE INDEX idx_menu_sn ON sys_menu(sn);

-- 部门表索引优化
CREATE INDEX idx_dept_parent_id ON sys_dept(parent_id);
CREATE INDEX idx_dept_code ON sys_dept(code);
CREATE INDEX idx_dept_status_del ON sys_dept(status, del_flag);

-- 字典表索引优化
CREATE INDEX idx_dict_type_del ON sys_dict(type, del_flag);
CREATE INDEX idx_dict_status_del ON sys_dict(status, del_flag);

-- 字典项表索引优化
CREATE INDEX idx_dict_item_type_del ON sys_dict_item(dict_type, del_flag);
CREATE INDEX idx_dict_item_dict_id ON sys_dict_item(dict_id);

-- 日志表索引优化（重要）
CREATE INDEX idx_log_type_date ON sys_log(type, create_time);
CREATE INDEX idx_log_user_date ON sys_log(create_by, create_time);
CREATE INDEX idx_log_service_id ON sys_log(service_id);

-- 文件表索引优化
CREATE INDEX idx_file_bucket ON sys_file(bucket);
CREATE INDEX idx_file_business ON sys_file(business, source_id);

-- =====================================================
-- 2. 工作流模块索引优化
-- =====================================================

-- 流程定义索引
CREATE INDEX idx_proc_def_key ON wf_process_definition(process_key);
CREATE INDEX idx_proc_def_category ON wf_process_definition(category);
CREATE INDEX idx_proc_def_status ON wf_process_definition(suspension_state);

-- 流程实例索引
CREATE INDEX idx_proc_inst_key ON wf_process_instance(process_key);
CREATE INDEX idx_proc_inst_status ON wf_process_instance(status);
CREATE INDEX idx_proc_inst_start_user ON wf_process_instance(start_user_id, create_time);
CREATE INDEX idx_proc_inst_business ON wf_process_instance(business_key);

-- 审批记录索引
CREATE INDEX idx_approval_proc_time ON wf_approval_record(process_instance_id, approval_time DESC);
CREATE INDEX idx_approval_user_time ON wf_approval_record(approval_user_id, approval_time DESC);
CREATE INDEX idx_approval_type ON wf_approval_record(approval_type);

-- 表单定义索引
CREATE INDEX idx_form_category_status ON wf_form(category, status);
CREATE INDEX idx_form_type ON wf_form(form_type);

-- 表单数据索引
CREATE INDEX idx_form_data_proc ON wf_form_data(process_instance_id);
CREATE INDEX idx_form_data_task ON wf_form_data(task_id);
CREATE INDEX idx_form_data_business ON wf_form_data(business_key);
CREATE INDEX idx_form_data_submit_time ON wf_form_data(submit_time);

-- =====================================================
-- 3. AI 模块索引优化
-- =====================================================

-- 会话表索引
CREATE INDEX idx_session_user_status ON ai_session(user_id, status);
CREATE INDEX idx_session_create_time ON ai_session(create_time);

-- 消息表索引
CREATE INDEX idx_msg_session_time ON ai_message(session_id, create_time DESC);

-- 对话记录索引
CREATE INDEX idx_conv_user_time ON ai_conversation(user_id, create_time DESC);
CREATE INDEX idx_conv_type ON ai_conversation(conversation_type);
CREATE INDEX idx_conv_session ON ai_conversation(session_id);

-- 文档表索引
CREATE INDEX idx_doc_user_status ON ai_document(user_id, vector_status);
CREATE INDEX idx_doc_type ON ai_document(doc_type);
CREATE INDEX idx_doc_create_time ON ai_document(create_time);

-- 向量嵌入索引
CREATE INDEX idx_embedding_doc ON ai_embedding(document_id);
CREATE INDEX idx_embedding_vector ON ai_embedding(vector_id);

-- =====================================================
-- 4. 代码生成器索引优化
-- =====================================================

-- 数据源配置索引
CREATE INDEX idx_ds_type_status ON gen_datasource_config(ds_type, status);

-- 业务表索引
CREATE INDEX idx_gen_table_name ON gen_table(table_name);
CREATE INDEX idx_gen_class_name ON gen_table(class_name);

-- =====================================================
-- 5. 定时任务索引优化
-- =====================================================
-- Quartz 框架已有索引，无需额外添加

-- =====================================================
-- 6. 复合索引优化（覆盖常用查询）
-- =====================================================

-- 用户登录查询覆盖索引
CREATE INDEX idx_user_login_cover ON sys_user(username, password, status, del_flag);

-- 角色权限查询覆盖索引
CREATE INDEX idx_role_perm_cover ON sys_role(code, name, status, del_flag);

-- 日志查询覆盖索引
CREATE INDEX idx_log_query_cover ON sys_log(type, create_time, del_flag);

-- =====================================================
-- 7. 全文索引（可选，用于文本搜索）
-- =====================================================

-- 为文档内容添加全文索引
-- ALTER TABLE ai_document ADD FULLTEXT INDEX ftidx_doc_content (content);

-- 为日志标题添加全文索引
-- ALTER TABLE sys_log ADD FULLTEXT INDEX ftidx_log_title (title);

-- =====================================================
-- 索引使用说明
-- =====================================================

-- 1. 查看表的索引
-- SHOW INDEX FROM sys_user;

-- 2. 分析索引使用情况
-- EXPLAIN SELECT * FROM sys_user WHERE phone = '13800138000';

-- 3. 查看未使用的索引
-- SELECT * FROM sys.schema_unused_indexes WHERE object_schema = 'bixi';

-- 4. 删除未使用的索引（谨慎操作）
-- DROP INDEX idx_unused ON sys_user;

-- =====================================================
-- 索引维护建议
-- =====================================================

-- 1. 定期分析表
-- ANALYZE TABLE sys_user;

-- 2. 定期优化表（重建索引）
-- OPTIMIZE TABLE sys_user;

-- 3. 监控慢查询日志
-- SET GLOBAL slow_query_log = 1;
-- SET GLOBAL long_query_time = 2;

-- =====================================================
-- 执行完成后验证
-- =====================================================

-- 查看所有表
-- SHOW TABLES;

-- 查看表的索引统计
SELECT
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    INDEX_TYPE
FROM
    information_schema.STATISTICS
WHERE
    TABLE_SCHEMA = 'bixi'
ORDER BY
    TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
