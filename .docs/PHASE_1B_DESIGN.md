# 第一阶段 B 设计文档：租户与数据权限

**状态：** B1-B8 核心实现、门禁与 cloud/single 运行态黑盒验收完成；完整双租户负向矩阵仍是后续补充项
**日期：** 2026-09-22
**前置依赖：** 第一阶段 A（认证、授权与审计）已完成

## 1. 目标

在 1A 已定版的身份/权限/审计主体之上，建立可信租户上下文和数据访问规则。所有后续模块（生成器、工作流、AI、通知）都必须在此底座上运行。

### 1.1 交付清单

| 编号 | 能力 | 模块位置 | 验收标准 |
| --- | --- | --- | --- |
| B1 | 租户主数据 + 上下文 | `bixi-common-core` + `bixi-upms` | 租户 ID 从认证主体推导，不能由请求参数伪造 |
| B2 | MyBatis 行隔离 | `bixi-common-mybatis` | 所有带 `tenant_id` 的表自动过滤，INSERT 自动填充 |
| B3 | Redis 键隔离 | `bixi-common-core` + 各业务模块 | 用户/菜单/字典/角色/Token 缓存按租户分片 |
| B4 | 租户生命周期 | `bixi-upms-biz` | 开户、切换、停用；停用后所有访问拒绝 |
| B5 | 组织 DataScope | `bixi-common-mybatis` + `bixi-upms` | 本人/本部门/下级部门/全部四种范围，服务端强制执行 |
| B6 | 字段脱敏 | `bixi-common-core` | `@Sensitive` 注解，接口/导出/日志三处一致 |
| B7 | 敏感词 DFA | `bixi-common-core` + `bixi-upms` | 自定义词库实时生效，命中审计 |
| B8 | 报文加解密 | `bixi-common-core` + `bixi-auth` | 前后端 AES 互通，错误密文不落入业务层 |

### 1.2 明确不做

- 不引入分库分表（ShardingSphere）——留到第三阶段 B
- 不做跨租户数据迁移工具
- 不做租户级别的独立域名/SSL
- 不做移动端适配

## 2. 现状分析

### 2.1 已有基础

- **Schema**：37/53 张表已有 `tenant_id` 列（`bigint` 或 `varchar(32/64)`）
- **Entity**：`BaseEntity.tenantId`（`String` 类型）已被所有业务实体继承
- **代码生成器**：`CommonColumnFiledEnum` 已识别 `tenant_id` 为通用列
- **前端**：`request.ts` 已有 AES 加解密能力（`Enc-Flag` header）
- **网关**：`RateLimiterConfiguration` 已有 `X-Tenant-Id` header 读取（仅用于限流）

### 2.2 缺失项

| 缺失 | 影响 |
| --- | --- |
| 无 `sys_tenant` 主数据表 | 无法管理租户生命周期 |
| 无 `TenantContextHolder` | 无法在业务层获取当前租户 |
| `BixiUser` 不携带 `tenantId` | 认证主体无法传递租户信息 |
| 无 MyBatis `TenantLineInnerInterceptor` | SQL 层无租户过滤 |
| `MetaObjectHandler` 不填充 `tenantId` | INSERT 不自动绑定租户 |
| Redis 键无租户维度 | 跨租户缓存污染 |
| 无 `@DataScope` | 数据权限只有客户端 deptId 过滤 |
| 无 `@Sensitive` 注解 | API 响应不脱敏 |
| 无 DFA 敏感词过滤 | 用户输入无过滤 |
| 后端无通用加解密 Filter | 前端加密能力未对接 |

### 2.3 类型不一致问题

`tenant_id` 在 SQL 中有三种类型：
- `sys_*` 核心表：`bigint`
- `ai_*`、`wf_*`：`varchar(32)`
- `biz_demo_*`：`varchar(64)`

`BaseEntity.tenantId` 为 `String`。

**决策**：统一为 `bigint`（与核心 sys 表一致）。将 `BaseEntity.tenantId` 改为 `Long`。AI/工作流/demo 表的 `tenant_id` 列在 1B 收口时统一迁移为 `bigint`。

## 3. 详细设计

### 3.1 B1：租户主数据 + 上下文

#### 3.1.1 `sys_tenant` 表

```sql
CREATE TABLE sys_tenant (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '租户ID',
    name        VARCHAR(128) NOT NULL COMMENT '租户名称',
    code        VARCHAR(64)  NOT NULL COMMENT '租户编码（唯一）',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
    contact     VARCHAR(64)  NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话',
    domain      VARCHAR(256) NULL COMMENT '绑定域名（预留）',
    expire_time DATETIME     NULL COMMENT '过期时间（NULL=永不过期）',
    max_user_count INT       NULL DEFAULT -1 COMMENT '最大用户数（-1=不限）',
    remark      VARCHAR(512) NULL COMMENT '备注',
    create_by   VARCHAR(64)  NULL COMMENT '创建人',
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)  NULL COMMENT '修改人',
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    del_flag    CHAR(1)      NULL DEFAULT '0' COMMENT '删除标记（0正常 1已删）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_tenant_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';
```

初始数据：`id=1, name='默认租户', code='default', status='0'`。现有所有 `tenant_id IS NULL` 的数据统一归入租户 1。

#### 3.1.2 `TenantContextHolder`

位置：`bixi-common/bixi-common-core/src/main/java/com/lotus/bixi/common/core/context/TenantContextHolder.java`

```java
public final class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_ID = new InheritableThreadLocal<>();

    public static void set(Long tenantId) { TENANT_ID.set(tenantId); }
    public static Long get() { return TENANT_ID.get(); }
    public static void clear() { TENANT_ID.remove(); }
}
```

使用 `InheritableThreadLocal` 以支持 `@Async` 和线程池任务传播（线程池需配合 `TaskDecorator`）。

#### 3.1.3 `BixiUser` 扩展

在 `BixiUser` 中增加 `tenantId` 字段（`Long` 类型），从 `SysUser.tenantId` 传入。

认证流程中：
1. `BixiUserDetailsService.getUserDetails()` 构建 `BixiUser` 时设置 `tenantId`
2. Token  introspection 时从 `BixiUser` 恢复 `TenantContextHolder`

#### 3.1.4 租户上下文 Filter

位置：`bixi-common/bixi-common-security` 中新增 `TenantContextFilter`（`OncePerRequestFilter`，`@Order(HIGHEST_PRECEDENCE + 10)`）

```
从 SecurityContext 获取 Authentication → BixiUser → tenantId → TenantContextHolder.set()
请求结束后 clear
```

Cloud 模式：网关将 `X-Tenant-Id` 写入 header 作为辅助校验（与 Token 中的 tenantId 比对，不一致则拒绝）。

#### 3.1.5 租户状态校验

`TenantContextFilter` 中查询 `sys_tenant` 状态：
- `status='1'`（停用）→ 返回 403 + `tenant_disabled`
- `expire_time` 已过期 → 返回 403 + `tenant_expired`

查询结果缓存 5 分钟（按 tenantId 键）。

### 3.2 B2：MyBatis 行隔离

#### 3.2.1 注册 `TenantLineInnerInterceptor`

在 `MybatisAutoConfiguration` 中，将 `TenantLineInnerInterceptor` 注册到 `PaginationInnerInterceptor` **之前**（MyBatis-Plus 要求顺序：多租户 → 分页）。

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new BixiTenantLineHandler()));
    interceptor.addInnerInterceptor(new BixiPaginationInnerInterceptor());
    return interceptor;
}
```

#### 3.2.2 `BixiTenantLineHandler`

```java
public class BixiTenantLineHandler implements TenantLineHandler {
    // 白名单：不需要租户过滤的表
    private static final Set<String> IGNORE_TABLES = Set.of(
        "sys_tenant", "QRTZ_", "reliable_outbox", "reliable_inbox"
    );

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.get();
        if (tenantId == null) throw new TenantNotSetException();
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 超级管理员（tenantId == null 或特殊标记）可忽略
        // 白名单表忽略
        // 关联表（sys_user_role 等）忽略——通过主表间接过滤
    }
}
```

**白名单策略**：
- `sys_tenant` 本身不过滤
- `QRTZ_*` Quartz 框架表不过滤
- `reliable_outbox/inbox` 使用 `source_owner/target_owner` 自行隔离
- `sys_user_role`、`sys_role_menu`、`sys_user_post` 等关联表不过滤（通过主表 WHERE 间接隔离）
- `wf_command` 使用 `tenant_scope` 列名，需单独处理

#### 3.2.3 `MetaObjectHandler` 自动填充

在 `MybatisPlusMetaObjectHandler.insertFill()` 中增加：

```java
this.strictInsertFill(metaObject, "tenantId", Long.class, TenantContextHolder.get());
```

#### 3.2.4 超级管理员旁路

`tenantId == 1` 的超级管理员（`ROLE_SUPER_ADMIN`）可查询所有租户数据——通过 `TenantLineHandler.ignoreInsert()` 和 `getTenantId()` 中的判断实现。

**注意**：超级管理员的写操作仍然必须带 `tenantId`，防止脏数据。

### 3.3 B3：Redis 键隔离

#### 3.3.1 策略

在 `CacheConstants` 中新增工具方法：

```java
public static String tenantKey(String prefix, Long tenantId) {
    return prefix + "TENANT:" + tenantId + ":";
}
```

#### 3.3.2 需要租户隔离的键

| 原键 | 新键模式 | 说明 |
| --- | --- | --- |
| `user_details` | `user_details:TENANT:{tenantId}:{username}` | 用户详情 |
| `menu_details` | `menu_details:TENANT:{tenantId}` | 菜单树 |
| `dict_details` | `dict_details:TENANT:{tenantId}:{dictKey}` | 字典 |
| `role_details` | `role_details:TENANT:{tenantId}:{roleId}` | 角色 |
| `token::access_token` | 不变 | Token 本身已按 token value 唯一 |
| `LOGIN_FAIL_KEY:` | `LOGIN_FAIL_KEY:TENANT:{tenantId}:{username}` | 登录失败 |
| `SMS_CODE_KEY:` | `SMS_CODE_KEY:TENANT:{tenantId}:{mobile}` | 短信验证码 |
| `SMS_RATE_LIMIT_KEY:` | `SMS_RATE_LIMIT_KEY:TENANT:{tenantId}:{mobile}` | 短信限频 |

#### 3.3.3 修改点

- `SysUserDetailsServiceImpl`：缓存键加租户前缀
- `SysMenuServiceImpl`：缓存键加租户前缀
- `SysDictService`：缓存键加租户前缀
- `BixiDaoAuthenticationProvider`：LOGIN_FAIL_KEY 加租户前缀（从 `TenantContextHolder.get()` 获取）
- `SysMobileServiceImpl`：SMS keys 加租户前缀

### 3.4 B4：租户生命周期

#### 3.4.1 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/tenant` | `tenant_add` | 创建租户 |
| PUT | `/tenant` | `tenant_edit` | 修改租户 |
| DELETE | `/tenant/{id}` | `tenant_del` | 逻辑删除 |
| GET | `/tenant/page` | `tenant_view` | 分页列表 |
| PUT | `/tenant/{id}/status` | `tenant_edit` | 切换停用/启用 |

#### 3.4.2 停用传播

停用租户时：
1. 更新 `sys_tenant.status = '1'`
2. 清除该租户所有 Redis 缓存（`keys TENANT:{id}:*` → DEL）
3. 后续该租户请求被 `TenantContextFilter` 拦截返回 403

#### 3.4.3 租户切换

超级管理员可通过 `X-Tenant-Id` header 切换到其他租户视图（仅查看，不修改）。切换后：
- `TenantContextHolder.set(targetTenantId)`
- MyBatis 过滤按目标租户
- 审计日志记录原始管理员 + 目标租户

### 3.5 B5：组织 DataScope

#### 3.5.1 `@DataScope` 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
    String deptAlias() default "dept";   // SQL 中部门表别名
    String userAlias() default "user";   // SQL 中用户表别名
}
```

#### 3.5.2 DataScope 类型

角色表增加 `data_scope` 字段（`CHAR(1)`）：

| 值 | 含义 | SQL 条件 |
| --- | --- | --- |
| `1` | 全部 | 无附加条件 |
| `2` | 本部门及下级 | `dept.id IN (本部门 + 递归下级)` |
| `3` | 本部门 | `dept.id = 用户部门ID` |
| `4` | 本人 | `user.id = 当前用户ID` |

#### 3.5.3 拦截器

`DataScopeInterceptor`（MyBatis `Interceptor`，非 `InnerInterceptor`）：
1. 检查方法上是否有 `@DataScope`
2. 获取当前用户的角色中最大 `data_scope`
3. 根据 scope 类型生成 SQL 片段
4. 通过 JSqlParser 注入到原有 SQL 的 WHERE 中

**与租户拦截器的关系**：租户过滤先执行（所有数据），DataScope 后执行（在租户内进一步缩小范围）。

#### 3.5.4 默认值

未标注 `@DataScope` 的接口默认按 `data_scope=4`（本人）处理——最小权限原则。

### 3.6 B6：字段脱敏

#### 3.6.1 `@Sensitive` 注解

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {
    SensitiveType value();
}

public enum SensitiveType {
    PHONE,      // 138****0000
    ID_CARD,    // 110***********1234
    BANK_CARD,  // 6222 **** **** 1234
    EMAIL,      // a***@example.com
    PASSWORD,   // ******
    NAME        // 张*
}
```

#### 3.6.2 `SensitiveSerializer`

`JsonSerializer<String>` 实现，根据 `SensitiveType` 执行不同脱敏规则。

#### 3.6.3 应用

- `SysUser.phone` → `@Sensitive(SensitiveType.PHONE)`
- `SysUser.email` → `@Sensitive(SensitiveType.EMAIL)`
- `SysUser.password` → `@Sensitive(SensitiveType.PASSWORD)`（已有序列化排除，双保险）

#### 3.6.4 日志脱敏

`SysLogUtils` 中的 `SENSITIVE_FIELDS` 集合保留，与 `@Sensitive` 注解互补：
- 注解处理 API 响应
- 集合处理审计日志中的请求参数

### 3.7 B7：敏感词 DFA

#### 3.7.1 存储

新增 `sys_sensitive_word` 表：

```sql
CREATE TABLE sys_sensitive_word (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    word        VARCHAR(128) NOT NULL COMMENT '敏感词',
    category    VARCHAR(64)  NULL COMMENT '分类',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    tenant_id   BIGINT       NOT NULL,
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    CHAR(1)      NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sensitive_word (word, tenant_id, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词表';
```

#### 3.7.2 DFA 引擎

`SensitiveWordEngine`：
- 启动时从 DB 加载所有启用词库，构建 DFA Trie
- 按租户隔离：每个租户一棵 Trie（LRU 缓存，最多 100 个租户）
- 提供 `containsSensitiveWord(text)` 和 `replaceSensitiveWords(text)` 方法
- 词库变更时通过 Redis pub/sub 通知所有节点重新加载

#### 3.7.3 应用点

- `@SensitiveWordCheck` 注解：标注在 Controller 方法参数上
- AOP 切面拦截，命中则抛出 `SensitiveWordException` → 返回 400

### 3.8 B8：报文加解密

#### 3.8.1 协议

前端已有实现（`request.ts`）：
- 请求：`Enc-Flag: aes` header + body 为 `{ "encryption": "<AES ciphertext>" }`
- 响应：body 为 `{ "encryption": "<AES ciphertext>" }` 时自动解密

AES 密钥：前端硬编码（与 `PasswordEncoderFilter` 使用同一 `encodeKey`）。

#### 3.8.2 后端 Filter

`EncryptionFilter`（`OncePerRequestFilter`，`@Order(HIGHEST_PRECEDENCE + 30)`）：

**请求解密**：
1. 检查 `Enc-Flag` header
2. 读取 body → 解析 `{ "encryption": "..." }` → AES 解密 → 替换 `HttpServletRequest` 的 `InputStream`

**响应加密**：
1. 检查请求有 `Enc-Flag` header
2. 包装 `HttpServletResponse`，捕获输出
3. 将输出 AES 加密 → 写回 `{ "encryption": "..." }`

#### 3.8.3 安全约束

- 仅对声明了 `Enc-Flag` 的请求加解密，不影响普通请求
- 解密失败返回 400 + `decrypt_error`，不落入业务层
- 密钥从配置读取（`bixi.encrypt.key`），不硬编码

## 4. 文件清单

### 4.1 新增文件

| 文件 | 模块 | 说明 |
| --- | --- | --- |
| `TenantContextHolder.java` | `bixi-common-core` | 租户 ThreadLocal |
| `TenantNotSetException.java` | `bixi-common-core` | 异常 |
| `TenantContextFilter.java` | `bixi-common-security` | 租户上下文 Filter |
| `BixiTenantLineHandler.java` | `bixi-common-mybatis` | MyBatis 租户处理器 |
| `DataScope.java` (annotation) | `bixi-common-mybatis` | DataScope 注解 |
| `DataScopeInterceptor.java` | `bixi-common-mybatis` | DataScope SQL 注入 |
| `DataScopeType.java` | `bixi-common-mybatis` | 枚举 |
| `Sensitive.java` (annotation) | `bixi-common-core` | 脱敏注解 |
| `SensitiveType.java` | `bixi-common-core` | 脱敏类型枚举 |
| `SensitiveSerializer.java` | `bixi-common-core` | Jackson 序列化 |
| `SensitiveWordEngine.java` | `bixi-common-core` | DFA 引擎 |
| `SensitiveWordCheck.java` | `bixi-common-core` | 注解 |
| `SensitiveWordAspect.java` | `bixi-common-core` | AOP 切面 |
| `EncryptionFilter.java` | `bixi-common-security` | 加解密 Filter |
| `SysTenant.java` | `bixi-upms-api` | 租户实体 |
| `SysTenantController.java` | `bixi-upms-biz` | 租户管理接口 |
| `SysTenantService.java` | `bixi-upms-biz` | 租户服务 |
| `SysTenantMapper.java` | `bixi-upms-biz` | 租户 Mapper |
| `SysSensitiveWord.java` | `bixi-upms-api` | 敏感词实体 |
| `SysSensitiveWordController.java` | `bixi-upms-biz` | 敏感词管理 |
| `SysSensitiveWordService.java` | `bixi-upms-biz` | 敏感词服务 |

### 4.2 修改文件

| 文件 | 修改内容 |
| --- | --- |
| `BaseEntity.java` | `tenantId` 类型 `String` → `Long` |
| `BixiUser.java` | 增加 `tenantId` 字段 |
| `MybatisAutoConfiguration.java` | 注册 `TenantLineInnerInterceptor` |
| `MybatisPlusMetaObjectHandler.java` | INSERT 填充 `tenantId` |
| `CacheConstants.java` | 增加租户键工具方法 |
| `SysUserDetailsServiceImpl.java` | 缓存键加租户前缀，构建 BixiUser 传 tenantId |
| `BixiDaoAuthenticationProvider.java` | LOGIN_FAIL_KEY 加租户前缀 |
| `SysMobileServiceImpl.java` | SMS keys 加租户前缀 |
| `SysUser.java` | phone/email 加 `@Sensitive` |
| `SysMenuServiceImpl.java` | 缓存键加租户前缀 |
| `WebSecurityConfiguration.java` | 注册 `TenantContextFilter`、`EncryptionFilter` |
| `01_init_all_tables.sql` | 新增 `sys_tenant`、`sys_sensitive_word`；统一 `tenant_id` 类型 |
| `04_init_data.sql` | 新增默认租户数据 |
| `SysRole.java` | 增加 `dataScope` 字段 |
| `bixi_form.sql` / `bixi_ai.sql` / `bixi_workflow.sql` | `tenant_id` 类型统一为 `bigint` |

## 5. 测试计划

### 5.1 单元测试

| 组件 | 测试类 | 用例 |
| --- | --- | --- |
| `TenantContextHolder` | `TenantContextHolderTest` | set/get/clear、线程隔离、InheritableThreadLocal 传播 |
| `BixiTenantLineHandler` | `BixiTenantLineHandlerTest` | 正常过滤、白名单表跳过、tenantId 未设置抛异常 |
| `DataScopeInterceptor` | `DataScopeInterceptorTest` | 四种 scope 类型 SQL 生成、无注解不注入 |
| `SensitiveSerializer` | `SensitiveSerializerTest` | 六种脱敏类型的正向/空值/短字符串 |
| `SensitiveWordEngine` | `SensitiveWordEngineTest` | DFA 构建、命中检测、替换、多租户隔离 |
| `EncryptionFilter` | `EncryptionFilterTest` | 解密成功/失败、加密响应、无 Enc-Flag 透传 |
| `TenantContextFilter` | `TenantContextFilterTest` | 正常传播、停用租户拒绝、过期租户拒绝 |

### 5.2 集成测试

- 两租户同用户名隔离：创建租户 A 和 B，各有 `alice` 用户，验证列表/详情/写入/Redis 完全隔离
- DataScope 越权：用户 A（本部门）查询，传入其他部门 deptId 参数，验证被服务端过滤
- 停用租户：停用租户 B 后，B 的用户刷新 Token 返回 403

### 5.3 双模验收

- cloud 专用环境：`scripts/acceptance.mjs` 与 `scripts/security-acceptance.mjs` 均通过（HTTP `28380`，Workflow enabled，真实 MySQL/Redis/RabbitMQ/Nacos）。
- single 专用环境：同一组业务与安全黑盒均通过（HTTP `18380`，Workflow enabled）。
- `make architecture-check`：通过

## 6. 实施顺序

1. **B1 租户上下文**（基础，其他所有项依赖）
2. **B2 MyBatis 行隔离**（数据隔离核心）
3. **B3 Redis 键隔离**（缓存隔离）
4. **B4 租户生命周期**（管理接口）
5. **B5 DataScope**（数据权限）
6. **B6 字段脱敏**（独立模块）
7. **B7 敏感词 DFA**（独立模块）
8. **B8 报文加解密**（独立模块）
9. **测试 + 门禁**

## 7. 风险与决策

| 风险 | 缓解 |
| --- | --- |
| `tenant_id` 类型迁移可能影响已有数据 | 先 ALTER 再 UPDATE NULL → 1；开发阶段无生产数据 |
| `InheritableThreadLocal` 在线池中泄漏 | 配合 `TaskDecorator` 在任务结束时 clear |
| DataScope SQL 注入可能破坏复杂查询 | 使用 JSqlParser 而非字符串拼接；白名单表不过滤 |
| 加解密 Filter 影响性能 | 仅对声明 `Enc-Flag` 的请求生效；大文件上传不走加密 |
| 超级管理员旁路可能被滥用 | 超级管理员切换租户的审计日志独立记录 |

## 8. 当前实施状态与验证证据

截至 2026-09-22，B1-B8 的核心代码、SQL、聚焦回归测试和 cloud/single 运行态黑盒已落地，cloud/single 共用同一套 UPMS 业务实现。补充收口包括：只读租户切换写保护、敏感词事务提交后刷新与 Redis Pub/Sub、租户定向 Redis SCAN 清理，以及窄包扫描场景下的 common-core 自动配置和 cloud 内部租户状态 Feign 契约。以下门禁已实际执行并通过：

- `make architecture-check`
- `make runtime-config-check`
- `make backend-cloud-ci`
- `make backend-single-ci`
- `make frontend-ci`
- `git diff --check`
- `codegraph sync .`

本轮定向复核（2026-09-22）：common-core 租户缓存/脱敏/敏感词/AES 测试 9/9、common-mybatis 租户行隔离/DataScope/审计填充测试 6/6、common-security 租户上下文/Token 状态/密码策略测试 55/55、UPMS 租户生命周期/短信/敏感词事务与通知测试 27/27，均 `BUILD SUCCESS`；恢复管理切片另有 Workflow 4/4、UPMS 8/8、Rabbit listener 2/2。

以下项目仍作为明确限制，不随本轮黑盒通过标记为完成：

- 完整双租户负向场景（同用户名/手机号、跨租户读写、停用租户和加密错误）的独立自动化矩阵尚未补齐；当前黑盒已覆盖租户上下文、数据权限、停用/撤销相关路径。
- 敏感词词库变更的 Redis pub/sub 跨节点刷新真实多节点验收；当前已在事务提交后刷新本地引擎并发布租户级消息。
- 租户停用后的 Redis 精确索引真实 Redis 验收；当前实现使用 SCAN 按租户命名空间清理，不再全局清空缓存。
- `DataScope` 未标注 Mapper 的默认 SELF 策略仍需结合所有查询场景补回归，当前保持显式标注才注入，避免误伤系统查询。

### 2026-09-22 双模运行态收口

- single：业务黑盒覆盖 CRUD、审计、请假工作流 `APPROVED/REJECTED/CANCELED`、幂等/冲突、审批人和数据权限；安全黑盒覆盖 CSRF、浏览器 OAuth、Bearer/session 隔离、27 个管理接口越权、通知隔离和 Token 撤销，全部通过。
- cloud：同一业务与安全黑盒在 Gateway → Auth/UPMS/Workflow → MySQL/Redis/RabbitMQ/Nacos 链路全部通过；Workflow 使用真实 Flowable 表和 Rabbit transport。
- cloud 启动收口：为专用数据库一次性显式初始化 Flowable 表后恢复 `WORKFLOW_SCHEMA_UPDATE=false`；专用数据库卷的 OAuth runtime secrets 已按部署脚本重放，未修改通用 SQL 或提交运行凭据。
- 验收命令：`BIXI_ENV_FILE=/tmp/bixi-reliable-cloud.env ... node ./scripts/acceptance.mjs` 与 `security-acceptance.mjs` 均返回 `passed`；single 结果同样已记录。

### 2026-09-21 门禁复核

- `make backend-cloud-ci`：28 个 cloud reactor 模块全部 `BUILD SUCCESS`；未配置专属外部依赖时，MySQL/RabbitMQ 集成测试按环境条件跳过，其余测试无失败。
- `make backend-single-ci`：22 个 single reactor 模块全部 `BUILD SUCCESS`；同样只跳过未配置外部依赖的集成测试。
- `make frontend-ci`：`npm ci`、ESLint 和生产构建全部通过。
- `make reliable-rabbit-test`：真实 MySQL 8.0.45 与 RabbitMQ 4 实例下 5 项端点/坏消息测试、broker 重启前后两阶段测试全部通过。
- 为避免默认 CI 在没有外部依赖时误报失败，Rabbit 集成测试增加 `OUTBOX_TEST_JDBC_URL` 环境条件；带真实依赖的专用脚本仍会执行这些测试。
