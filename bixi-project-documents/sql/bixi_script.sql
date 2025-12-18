/* 脚本主信息表 */
CREATE TABLE script_info (
  id BIGINT NOT NULL COMMENT '脚本ID',
  name VARCHAR(200) NOT NULL COMMENT '脚本名称',
  code VARCHAR(100) NOT NULL COMMENT '脚本编码',
  version VARCHAR(50) COMMENT '版本号',
  type CHAR(1) DEFAULT '0' COMMENT '类型（0DDL 1DML 2其他）',
  risk_level CHAR(1) DEFAULT '0' COMMENT '风险级别（0低 1中 2高）',
  storage_path VARCHAR(255) COMMENT '存储路径',
  checksum VARCHAR(64) COMMENT '校验摘要',
  status CHAR(1) DEFAULT '0' COMMENT '状态（0草稿 1已发布 2已废弃）',
  data_status CHAR(1) DEFAULT '0' COMMENT '数据状态',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_info_code (code)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='脚本主信息表';

/* 现场（环境）信息表 */
CREATE TABLE script_site (
  id BIGINT NOT NULL COMMENT '现场ID',
  name VARCHAR(100) NOT NULL COMMENT '现场名称',
  code VARCHAR(100) NOT NULL COMMENT '现场编码',
  env VARCHAR(20) COMMENT '环境类型（prod、test、dev）',
  region VARCHAR(100) COMMENT '区域/节点',
  owner_id BIGINT COMMENT '主要负责人ID',
  status CHAR(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  data_status CHAR(1) DEFAULT '0' COMMENT '数据状态',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_site_code (code)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='现场信息表';

/* 现场负责人关联表 */
CREATE TABLE script_site_manager (
  id BIGINT NOT NULL COMMENT '主键ID',
  site_id BIGINT NOT NULL COMMENT '现场ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  is_primary CHAR(1) DEFAULT '0' COMMENT '是否主负责人（0否 1是）',
  role_type VARCHAR(20) COMMENT '管理角色（owner、backup、observer）',
  status CHAR(1) DEFAULT '0' COMMENT '状态（0有效 1无效）',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_site_manager (site_id, user_id),
  KEY idx_script_site_manager_site (site_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='现场负责人关联表';

/* 执行计划表（原脚本-现场映射） */
CREATE TABLE script_execution_plan (
  id BIGINT NOT NULL COMMENT '主键ID',
  script_id BIGINT NOT NULL COMMENT '脚本ID',
  site_id BIGINT NOT NULL COMMENT '现场ID',
  plan_status CHAR(1) DEFAULT '0' COMMENT '计划状态（0待计划 1已计划 2已执行 3已取消）',
  exec_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序',
  schedule_time DATETIME COMMENT '计划执行时间',
  priority INT DEFAULT 0 COMMENT '优先级',
  status CHAR(1) DEFAULT '0' COMMENT '状态（0有效 1无效）',
  data_status CHAR(1) DEFAULT '0' COMMENT '数据状态',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_script_execution_plan (script_id, site_id),
  KEY idx_script_execution_plan_script (script_id),
  KEY idx_script_execution_plan_site (site_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='脚本执行计划表';

/* 任务表 */
CREATE TABLE script_task (
  id BIGINT NOT NULL COMMENT '任务ID',
  title VARCHAR(200) NOT NULL COMMENT '任务标题',
  plan_id BIGINT COMMENT '关联计划ID（可选）',
  script_id BIGINT NOT NULL COMMENT '脚本ID',
  site_id BIGINT NOT NULL COMMENT '现场ID',
  assigned_to BIGINT COMMENT '被指派人ID',
  assigner_id BIGINT COMMENT '指派人ID',
  role_type VARCHAR(20) COMMENT '任务角色（executor、reviewer）',
  due_time DATETIME COMMENT '截止时间',
  status CHAR(1) DEFAULT '0' COMMENT '任务状态（0待处理 1进行中 2已完成 3阻塞 4已取消）',
  priority INT DEFAULT 0 COMMENT '优先级',
  remind_enable CHAR(1) DEFAULT '1' COMMENT '开启提醒（0否 1是）',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_script_task_plan (plan_id),
  KEY idx_script_task_script (script_id),
  KEY idx_script_task_site (site_id),
  KEY idx_script_task_assigned (assigned_to)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务表';

/* 任务提醒表 */
CREATE TABLE script_task_reminder (
  id BIGINT NOT NULL COMMENT '提醒ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  remind_time DATETIME NOT NULL COMMENT '提醒时间',
  channel VARCHAR(20) COMMENT '提醒渠道（email、sms、message）',
  status CHAR(1) DEFAULT '0' COMMENT '发送状态（0待发送 1已发送 2发送失败）',
  title VARCHAR(200) COMMENT '提醒标题',
  content VARCHAR(1000) COMMENT '提醒内容',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_script_task_reminder_task (task_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务提醒表';

/* 执行记录表 */
CREATE TABLE script_execution_log (
  id BIGINT NOT NULL COMMENT '记录ID',
  script_id BIGINT NOT NULL COMMENT '脚本ID',
  site_id BIGINT NOT NULL COMMENT '现场ID',
  task_id BIGINT COMMENT '关联任务ID',
  status CHAR(1) DEFAULT '0' COMMENT '执行结果（0成功 1失败 2跳过 3部分成功）',
  executor_id BIGINT COMMENT '实际执行人ID',
  start_time DATETIME COMMENT '开始时间',
  finish_time DATETIME COMMENT '结束时间',
  duration_ms BIGINT COMMENT '耗时（毫秒）',
  log_content LONGTEXT COMMENT '详细日志',
  error_msg VARCHAR(2000) COMMENT '错误信息摘要',
  tenant_id BIGINT COMMENT '租户id',
  create_by BIGINT COMMENT '创建人',
  update_by BIGINT COMMENT '修改人',
  create_time DATETIME NULL DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT NULL COMMENT '修改时间',
  del_flag CHAR(1) DEFAULT '0' COMMENT '删除标志',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_script_exec_log_script (script_id),
  KEY idx_script_exec_log_site (site_id),
  KEY idx_script_exec_log_task (task_id)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='脚本执行记录表';
