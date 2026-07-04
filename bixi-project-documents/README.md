# Bixi Project Documents

This directory stores project-owned documents and helper files. Agent/tool-specific output outside this directory is intentionally left untouched.

## Directory Map

| Directory | Purpose |
| --- | --- |
| `sql/` | Database schema, seed data, constraints, indexes, and database reference docs. |
| `plans/` | Project execution plans and task breakdowns. |
| `validation/` | Verification reports, audits, and reproducible validation evidence. |
| `tools/` | Local helper scripts for project development or environment startup. |

## Out Of Scope For This Directory

- `.docs/`, `.trae/`, `.claude/`, and `docs/superpowers/` are agent/tool outputs and are not reorganized here.
- Source code modules keep their existing Maven/Vue layout.
- Build outputs such as `target/`, `dist/`, and dependency caches stay outside project document classification.
