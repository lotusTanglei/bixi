# Bixi Completion Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify and complete unfinished or inconsistent Bixi implementation chains, with database schema/document consistency as the first-class validation target, then fix confirmed AI-related frontend/backend contract gaps and run end-to-end regression checks.

**Architecture:** Treat the work as three independently verifiable tracks: database contract cleanup, AI-related incomplete-chain repair, and full-system verification. Each track starts with a failing or contradictory check, then applies the smallest compatible fix, then records the verification command and expected result.

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis Plus, MySQL SQL scripts, Vue 3.5, TypeScript, Vite, Maven, npm.

---

## File Structure

- Modify: `bixi-project-documents/sql/README.md`
  - Correct table counts, script inventory, execution order, and known module coverage.
- Modify: `bixi-project-documents/sql/DATABASE.md`
  - Align schema descriptions with actual table definitions and remove inaccurate universal field claims.
- Modify: `bixi-project-documents/sql/DATA_DICTIONARY.md`
  - Align `sys_role` and generator table field names with current SQL/entities.
- Modify: `bixi-project-documents/sql/02_add_constraints.sql`
  - Fix or defer self-referential foreign keys that conflict with root `parent_id` values.
- Modify: `bixi-project-documents/sql/03_add_indexes.sql`
  - Replace stale column names and avoid duplicate/wrong indexes.
- Modify: `bixi-module/bixi-upms-biz/src/main/resources/mapper/SysUserMapper.xml`
  - Replace stale `sys_role` column names in user role joins.
- Modify or add tests under: `bixi-module/bixi-upms-biz/src/test/`
  - Add mapper contract verification for `SysUserMapper.xml` SQL column names.
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/service/impl/VectorStoreServiceImpl.java`
  - Replace empty retrieval implementation with real similarity search backed by `ai_embedding`.
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller/AiController.java`
  - Align chat, stream, document, and model endpoints with frontend API usage or deliberately update frontend to backend contract.
- Modify: `bixi-module/bixi-ai-api/src/main/java/com/lotus/bixi/ai/dto/ChatDTO.java`
  - Support frontend `content` input or migrate frontend to `message`.
- Modify: `bixi-ui/src/api/ai/*.ts`
  - Align routes and payload field names with backend.
- Modify: `bixi-ui/src/views/ai/**`
  - Align displayed document fields and chat request payloads.
- Modify or add tests under: `bixi-module/bixi-ai-biz/src/test/`
  - Cover chat DTO binding, vector search, document endpoints, and stream endpoint routing.
- Modify or add frontend checks under: `bixi-ui/src/`
  - Add type-level or component-level checks if the project already has a test runner configured; otherwise run production build as the minimum verification.

---

## Phase 1: Database Contract Validation And Fixes

### Task 1: Capture The Current Database Drift

**Files:**
- Create: `bixi-project-documents/validation/database-contract-audit.md`

- [ ] **Step 1: Create a focused audit document**

Add this structure:

```markdown
# Database Contract Audit

## Verified Inputs

- `bixi-project-documents/sql/01_init_all_tables.sql`
- `bixi-project-documents/sql/02_add_constraints.sql`
- `bixi-project-documents/sql/03_add_indexes.sql`
- `bixi-project-documents/sql/04_init_data.sql`
- `bixi-project-documents/sql/README.md`
- `bixi-project-documents/sql/DATABASE.md`
- `bixi-project-documents/sql/DATA_DICTIONARY.md`
- Java entities and MyBatis mapper XML files that reference SQL tables.

## Findings

| Area | Current Problem | Impact | Fix Task |
| --- | --- | --- | --- |
| Table count | Docs say 36 tables, init script contains 50 unique tables | Deployment docs are unreliable | Task 2 |
| Missing docs | README references files that do not exist | Operators follow broken instructions | Task 2 |
| `sys_role` fields | SQL/entity use `name/code/description`; docs/scripts/mapper use `role_name/role_code/role_desc` | Runtime SQL errors | Task 3 |
| Index script | `gen_datasource_config(db_type, status)` uses a missing column | Optional index script fails | Task 4 |
| FK constraints | Root `parent_id` values `0` and `-1` conflict with self-FKs | Constraint script may fail | Task 5 |
```

- [ ] **Step 2: Verify no unsupported claims remain in the audit**

Run:

```bash
rg -n "[T]BD|TO[D]O|待确[认]|稍[后]|以[后]" bixi-project-documents/validation/database-contract-audit.md
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add bixi-project-documents/validation/database-contract-audit.md
git commit -m "docs: capture database contract audit"
```

### Task 2: Fix Database Documentation Inventory

**Files:**
- Modify: `bixi-project-documents/sql/README.md`
- Modify: `bixi-project-documents/sql/DATABASE.md`
- Modify: `bixi-project-documents/sql/DATA_DICTIONARY.md`

- [ ] **Step 1: Update table count and script inventory**

In `README.md`, replace the stale summary with:

```markdown
## 数据库脚本总览

当前完整建表脚本 `01_init_all_tables.sql` 包含 50 张表，其中包含系统基础表、代码生成表、表单表、工作流表、AI 表、定时任务表以及 Quartz 原生表。

推荐执行顺序：

1. 创建数据库：`CREATE DATABASE bixi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;`
2. 执行 `01_init_all_tables.sql`
3. 执行 `04_init_data.sql`
4. 执行修正后的 `02_add_constraints.sql`
5. 执行修正后的 `03_add_indexes.sql`

本目录未包含 `update_generator_templates.sql` 或 `UPDATE_TEMPLATES_GUIDE.md`，不要在部署步骤中引用这两个文件。
```

- [ ] **Step 2: Replace inaccurate universal-field wording**

In `DATABASE.md` and `README.md`, replace claims equivalent to "all tables include tenant_id/del_flag/audit fields" with:

```markdown
业务主表通常包含租户、逻辑删除和审计字段；中间关系表、Quartz 原生表和部分配置表按实际用途保留精简字段。
```

- [ ] **Step 3: Align `DATA_DICTIONARY.md` role fields**

Replace `sys_role` field rows so the dictionary uses these names:

```markdown
| id | bigint | 主键 |
| name | varchar(64) | 角色名称 |
| code | varchar(64) | 角色编码 |
| description | varchar(255) | 角色描述 |
| sn | int | 排序 |
| status | char(1) | 状态 |
```

- [ ] **Step 4: Verify docs no longer mention missing files or stale role fields**

Run:

```bash
rg -n "update_generator_templates|UPDATE_TEMPLATES_GUIDE|role_name|role_code|role_desc|所有表.*tenant_id|所有表.*del_flag" bixi-project-documents/sql
```

Expected: no output, except SQL comments only if they explicitly describe a migration from old fields to current fields.

- [ ] **Step 5: Commit**

```bash
git add bixi-project-documents/sql/README.md bixi-project-documents/sql/DATABASE.md bixi-project-documents/sql/DATA_DICTIONARY.md
git commit -m "docs: align database documentation with schema"
```

### Task 3: Fix `sys_role` Runtime SQL Contract

**Files:**
- Modify: `bixi-module/bixi-upms-biz/src/main/resources/mapper/SysUserMapper.xml`
- Modify: `bixi-project-documents/sql/02_add_constraints.sql`
- Modify: `bixi-project-documents/sql/03_add_indexes.sql`

- [ ] **Step 1: Update mapper role column references**

In `SysUserMapper.xml`, replace stale selected columns:

```xml
r.id AS role_id,
r.name AS role_name,
r.code AS role_code,
r.description AS role_desc
```

Keep result aliases unchanged if downstream DTOs expect `roleName`, `roleCode`, or `roleDesc`.

- [ ] **Step 2: Update constraint script role references**

In `02_add_constraints.sql`, replace:

```sql
ALTER TABLE sys_role ADD CONSTRAINT uk_sys_role_code UNIQUE (role_code);
```

with:

```sql
ALTER TABLE sys_role ADD CONSTRAINT uk_sys_role_code UNIQUE (code);
```

- [ ] **Step 3: Update index script role references**

In `03_add_indexes.sql`, replace stale role indexes with:

```sql
CREATE INDEX idx_sys_role_code_status ON sys_role(code, status, del_flag);
CREATE INDEX idx_sys_role_name_status ON sys_role(name, status, del_flag);
```

Do not create `idx_role_code` if `01_init_all_tables.sql` already defines that index name.

- [ ] **Step 4: Verify stale role columns are gone from executable files**

Run:

```bash
rg -n "role_name|role_code|role_desc" bixi-module bixi-project-documents/sql/02_add_constraints.sql bixi-project-documents/sql/03_add_indexes.sql
```

Expected: no executable SQL or mapper references to missing physical columns. Aliases like `AS role_name` are acceptable only in mapper `SELECT` clauses.

- [ ] **Step 5: Commit**

```bash
git add bixi-module/bixi-upms-biz/src/main/resources/mapper/SysUserMapper.xml bixi-project-documents/sql/02_add_constraints.sql bixi-project-documents/sql/03_add_indexes.sql
git commit -m "fix: align role SQL columns with schema"
```

### Task 4: Fix Generator Index Script

**Files:**
- Modify: `bixi-project-documents/sql/03_add_indexes.sql`

- [ ] **Step 1: Replace missing datasource column**

Replace:

```sql
CREATE INDEX idx_gen_datasource_config_type_status ON gen_datasource_config(db_type, status);
```

with:

```sql
CREATE INDEX idx_gen_datasource_config_type_status ON gen_datasource_config(ds_type, status);
```

- [ ] **Step 2: Verify index columns exist in create-table script**

Run:

```bash
rg -n "CREATE TABLE `gen_datasource_config`|`ds_type`|`db_type`" bixi-project-documents/sql/01_init_all_tables.sql bixi-project-documents/sql/03_add_indexes.sql
```

Expected: `gen_datasource_config` contains `ds_type`; no index on `gen_datasource_config(db_type, status)` remains.

- [ ] **Step 3: Commit**

```bash
git add bixi-project-documents/sql/03_add_indexes.sql
git commit -m "fix: correct generator datasource indexes"
```

### Task 5: Make Foreign Key Constraints Executable

**Files:**
- Modify: `bixi-project-documents/sql/02_add_constraints.sql`
- Modify: `bixi-project-documents/sql/README.md`

- [ ] **Step 1: Decide root parent strategy**

Use one of these two concrete strategies:

```markdown
Chosen strategy: Keep root parent ids as sentinel values and do not add self-referential FKs for `sys_menu.parent_id` and `sys_dept.parent_id`.
```

This is the lower-risk choice because existing data already uses `sys_menu.parent_id = -1` and `sys_dept.parent_id = 0`.

- [ ] **Step 2: Remove or comment conflicting self-FKs**

In `02_add_constraints.sql`, remove or comment these constraints:

```sql
ALTER TABLE sys_menu ADD CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id);
ALTER TABLE sys_dept ADD CONSTRAINT fk_sys_dept_parent FOREIGN KEY (parent_id) REFERENCES sys_dept(id);
```

Add this explanation above the removed section:

```sql
-- sys_menu and sys_dept use sentinel parent_id values for roots (`-1` and `0`).
-- Self-referential foreign keys are intentionally not added here because they
-- would reject existing root records in 04_init_data.sql.
```

- [ ] **Step 3: Document the constraint behavior**

In `README.md`, add:

```markdown
`sys_menu.parent_id` 和 `sys_dept.parent_id` 使用根节点哨兵值，因此约束脚本不为这两个字段创建自引用外键。
```

- [ ] **Step 4: Verify conflicting FKs are gone**

Run:

```bash
rg -n "fk_sys_menu_parent|fk_sys_dept_parent" bixi-project-documents/sql/02_add_constraints.sql
```

Expected: no executable `ALTER TABLE ... FOREIGN KEY` statements for those two names.

- [ ] **Step 5: Commit**

```bash
git add bixi-project-documents/sql/02_add_constraints.sql bixi-project-documents/sql/README.md
git commit -m "fix: make database constraints match root data"
```

---

## Phase 2: AI-Related Incomplete Chain Repair

### Task 6: Align AI Chat Payload Contract

**Files:**
- Modify: `bixi-module/bixi-ai-api/src/main/java/com/lotus/bixi/ai/dto/ChatDTO.java`
- Modify: `bixi-ui/src/views/ai/chat/index.vue`
- Modify: `bixi-ui/src/views/ai/knowledge/index.vue`
- Modify: `bixi-ui/src/api/ai/chat.ts`

- [ ] **Step 1: Choose one request field**

Use `message` as the canonical backend and frontend field. This keeps the current backend DTO stable.

- [ ] **Step 2: Update frontend chat payloads**

Replace request objects like:

```ts
{
  content: inputValue.value,
  sessionId: currentSessionId.value
}
```

with:

```ts
{
  message: inputValue.value,
  sessionId: currentSessionId.value
}
```

- [ ] **Step 3: Keep `ChatDTO` strict**

Confirm `ChatDTO` contains:

```java
@NotBlank(message = "消息内容不能为空")
private String message;
```

Do not add a duplicate `content` field unless backwards compatibility is required by an external API.

- [ ] **Step 4: Verify no stale AI chat payload field remains**

Run:

```bash
rg -n "content:" bixi-ui/src/views/ai bixi-ui/src/api/ai
```

Expected: no chat request payload still sends `content` to `/ai/chat` or `/ai/rag`.

- [ ] **Step 5: Commit**

```bash
git add bixi-module/bixi-ai-api/src/main/java/com/lotus/bixi/ai/dto/ChatDTO.java bixi-ui/src/views/ai/chat/index.vue bixi-ui/src/views/ai/knowledge/index.vue bixi-ui/src/api/ai/chat.ts
git commit -m "fix: align AI chat payload contract"
```

### Task 7: Align AI Routes

**Files:**
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller/AiController.java`
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller/AiSessionController.java`
- Modify: `bixi-ui/src/api/ai/chat.ts`
- Modify: `bixi-ui/src/api/ai/config.ts`
- Modify: `bixi-ui/src/api/ai/document.ts`

- [ ] **Step 1: Prefer backend-compatible frontend route changes where behavior already exists**

Update stream API from:

```ts
url: '/ai/stream'
```

to:

```ts
url: '/ai/stream/chat'
```

and use the HTTP method expected by the backend.

- [ ] **Step 2: Add missing backend document routes only if UI depends on them**

If frontend screens require page/list/upload, add controller methods:

```java
@GetMapping("/documents/page")
public R<IPage<DocumentVO>> pageDocuments(DocumentPageQuery query) {
    return R.ok(aiService.pageDocuments(query));
}

@GetMapping("/documents/list")
public R<List<DocumentVO>> listDocuments(DocumentQuery query) {
    return R.ok(aiService.listDocuments(query));
}

@PostMapping("/documents/upload")
public R<DocumentVO> uploadDocument(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "docType", required = false) String docType) {
    return R.ok(aiService.uploadDocument(file, docType));
}
```

Create the query/service methods in the same package style used by existing AI services.

- [ ] **Step 3: Add or align model list route**

Either update frontend to use `/ai/config` if it only needs config, or add:

```java
@GetMapping("/models")
public R<List<AiModelVO>> listModels() {
    return R.ok(aiSessionService.listModels());
}
```

Use whichever model/config abstraction already exists in `AiSessionController`.

- [ ] **Step 4: Verify route inventory**

Run:

```bash
rg -n "/ai/(stream|models|documents/page|documents/list|documents/upload)" bixi-ui/src bixi-module/bixi-ai-biz/src/main/java
```

Expected: every frontend route has a matching backend route with compatible method and payload.

- [ ] **Step 5: Commit**

```bash
git add bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/controller bixi-ui/src/api/ai
git commit -m "fix: align AI frontend and backend routes"
```

### Task 8: Implement Real Vector Similarity Search

**Files:**
- Modify: `bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai/service/impl/VectorStoreServiceImpl.java`
- Modify or create mapper/service files for `ai_embedding` if they do not exist.
- Test: `bixi-module/bixi-ai-biz/src/test/java/com/lotus/bixi/ai/service/VectorStoreServiceImplTest.java`

- [ ] **Step 1: Write a failing test for non-empty search**

Create a test that inserts or mocks two embeddings and verifies the closest document is returned first:

```java
@Test
void similaritySearchReturnsClosestDocumentFirst() {
    SearchDTO dto = new SearchDTO();
    dto.setQuery("refund policy");
    dto.setTopK(1);

    List<DocumentVO> results = vectorStoreService.similaritySearch(dto);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTitle()).isEqualTo("Refund Policy");
}
```

- [ ] **Step 2: Run the focused failing test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl bixi-module/bixi-ai-biz -Dtest=VectorStoreServiceImplTest test
```

Expected: FAIL because current `similaritySearch` returns an empty list.

- [ ] **Step 3: Implement minimal retrieval**

Implement this behavior:

```java
public List<DocumentVO> similaritySearch(SearchDTO dto) {
    Assert.notNull(dto, "搜索参数不能为空");
    Assert.hasText(dto.getQuery(), "搜索内容不能为空");

    int topK = Optional.ofNullable(dto.getTopK()).filter(value -> value > 0).orElse(5);
    List<Float> queryVector = embeddingModel.embed(dto.getQuery());
    List<AiEmbedding> candidates = aiEmbeddingMapper.selectReadyEmbeddings(dto.getKnowledgeId());

    return candidates.stream()
            .map(candidate -> toScoredDocument(candidate, cosineSimilarity(queryVector, parseVector(candidate.getEmbedding()))))
            .filter(item -> item.score() >= Optional.ofNullable(dto.getThreshold()).orElse(0.0))
            .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
            .limit(topK)
            .map(ScoredDocument::document)
            .toList();
}
```

Adapt names to existing mapper/entity names after reading the current AI-related code.

- [ ] **Step 4: Run focused AI tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -pl bixi-module/bixi-ai-biz -Dtest=VectorStoreServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add bixi-module/bixi-ai-biz/src/main/java/com/lotus/bixi/ai bixi-module/bixi-ai-biz/src/test/java/com/lotus/bixi/ai
git commit -m "feat: implement AI vector similarity search"
```

### Task 9: Align AI Document Fields

**Files:**
- Modify: `bixi-ui/src/views/ai/document/components/DocumentTable.vue`
- Modify: `bixi-ui/src/api/ai/document.ts`
- Modify: `bixi-module/bixi-ai-api/src/main/java/com/lotus/bixi/ai/vo/DocumentVO.java`

- [ ] **Step 1: Use backend field names in the UI**

Replace displayed frontend fields:

```vue
{{ row.name }}
{{ row.type }}
```

with:

```vue
{{ row.title }}
{{ row.docType }}
```

- [ ] **Step 2: Update frontend TypeScript document type**

Use this shape:

```ts
export interface AiDocument {
  id: string
  title: string
  content?: string
  source?: string
  docType?: string
  vectorStatus?: string
  createTime?: string
}
```

- [ ] **Step 3: Verify stale UI fields are gone**

Run:

```bash
rg -n "row\\.name|row\\.type|\\bname:.*document|\\btype:.*document" bixi-ui/src/views/ai/document bixi-ui/src/api/ai/document.ts
```

Expected: no stale document display field references.

- [ ] **Step 4: Commit**

```bash
git add bixi-ui/src/views/ai/document/components/DocumentTable.vue bixi-ui/src/api/ai/document.ts
git commit -m "fix: align AI document fields"
```

---

## Phase 3: Full Verification

### Task 10: Run Backend Compile And Tests

**Files:**
- No source changes unless verification reveals a real project bug.

- [ ] **Step 1: Compile all backend modules**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run backend tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
```

Expected: PASS. If Mockito/ByteBuddy agent attach still fails, document it as a test environment blocker and run the affected test module with the required JVM agent configuration instead of marking business logic as passing.

- [ ] **Step 3: Commit only if test configuration is changed**

```bash
git add pom.xml bixi-module/*/pom.xml
git commit -m "test: stabilize backend test runtime"
```

### Task 11: Run Frontend Build

**Files:**
- No source changes unless verification reveals a real UI build bug.

- [ ] **Step 1: Build frontend**

Run:

```bash
npm run build:prod
```

from `bixi-ui`.

Expected: production build succeeds without TypeScript or route import errors.

- [ ] **Step 2: Commit only if build fixes are needed**

```bash
git add bixi-ui
git commit -m "fix: resolve frontend production build issues"
```

### Task 12: Validate SQL Executability Against MySQL

**Files:**
- Modify SQL only if execution reveals a script bug.

- [ ] **Step 1: Run scripts against a disposable database**

Use a local MySQL instance or container. Execute:

```bash
mysql -uroot -p -e "DROP DATABASE IF EXISTS bixi_verify; CREATE DATABASE bixi_verify DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p bixi_verify < bixi-project-documents/sql/01_init_all_tables.sql
mysql -uroot -p bixi_verify < bixi-project-documents/sql/04_init_data.sql
mysql -uroot -p bixi_verify < bixi-project-documents/sql/02_add_constraints.sql
mysql -uroot -p bixi_verify < bixi-project-documents/sql/03_add_indexes.sql
```

Expected: every command exits with status 0.

- [ ] **Step 2: Count tables**

Run:

```bash
mysql -uroot -p -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'bixi_verify';"
```

Expected: `50`.

- [ ] **Step 3: Commit SQL execution fixes if needed**

```bash
git add bixi-project-documents/sql
git commit -m "fix: make database scripts executable"
```

### Task 13: Produce Final Completion Report

**Files:**
- Create: `bixi-project-documents/validation/completion-report.md`

- [ ] **Step 1: Record exact verification results**

Use this format:

```markdown
# Completion Report

## Backend

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests compile`: PASS
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`: PASS

## Frontend

- `npm run build:prod`: PASS

## Database

- `01_init_all_tables.sql`: PASS
- `04_init_data.sql`: PASS
- `02_add_constraints.sql`: PASS
- `03_add_indexes.sql`: PASS
- Verified table count: 50

## Fixed In This Plan

- Database docs now match schema inventory.
- SQL scripts no longer reference stale columns.
- `SysUserMapper.xml` role joins use current `sys_role` columns.
- AI chat/document routes and payloads are aligned.
- Vector similarity search returns real results.

## Remaining Known Limitations

- Add only items proven by verification, with file paths and exact symptoms.
```

- [ ] **Step 2: Verify report contains no vague placeholders**

Run:

```bash
rg -n "[T]BD|TO[D]O|待确[认]|以[后]|稍[后]|看情[况]" bixi-project-documents/validation/completion-report.md
```

Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add bixi-project-documents/validation/completion-report.md
git commit -m "docs: add completion validation report"
```

---

## Execution Notes

- Work in small commits so database, AI contracts, and verification results can be reviewed independently.
- Do not normalize unrelated formatting or refactor unrelated modules.
- If a file has user changes during execution, inspect and preserve those changes.
- If MySQL is not available locally, stop at Task 12 and ask whether to use Docker, an existing database, or a user-provided connection string.

## Self-Review

- Spec coverage: The plan covers the user's requested project completeness verification, placeholder detection, database build-table documentation, confirmed AI-related incomplete chains, and final regression checks.
- Placeholder scan: The plan intentionally avoids unresolved placeholder language in executable steps.
- Type consistency: The plan standardizes AI chat on `message`, AI document display on `title/docType`, and role physical columns on `name/code/description`.
