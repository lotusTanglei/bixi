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

## Verification Notes

- The complete create-table script defines 50 unique tables, including Quartz native scheduler tables.
- `sys_role` is defined with physical columns `id`, `name`, `code`, `description`, `sn`, and `status`.
- `SysUserMapper.xml` selected stale physical role columns before the repair work.
- The initial data uses sentinel root parent ids for menu and department trees.
- SQL execution against a disposable database is tracked separately in the final completion report.
