--1、组织机构表
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept(
 `id` BIGINT NOT NULL  COMMENT '部门ID' ,
 `name` VARCHAR(50)   COMMENT '部门名称' ,
 `code` VARCHAR(50)   COMMENT '部门编码' ,
 `sn` INT NOT NULL DEFAULT 0 COMMENT '排序' ,
 `leader` BIGINT COMMENT '负责人' ,
 `create_by` BIGINT COMMENT '创建人' ,
 `update_by` BIGINT COMMENT '修改人' ,
 `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
 `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
 `del_flag` CHAR(1)  DEFAULT '0' COMMENT '删除标志' ,
 `status` CHAR(1)  DEFAULT '0' COMMENT '状态（0正常 1停用）' ,
 `data_status` CHAR(1)  DEFAULT '0' COMMENT '数据状态（用来标识数据状态，可用于割接，特殊数据处理）' ,
 `parent_id` BIGINT   COMMENT '父级部门ID' ,
 `tenant_id` BIGINT   COMMENT '租户id' ,
 `remark` VARCHAR(500)   COMMENT '备注' ,
 PRIMARY KEY (id)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci  COMMENT = '组织机构表';

-- 2、字典表

-- 3、字典项表