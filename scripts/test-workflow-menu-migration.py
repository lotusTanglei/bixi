#!/usr/bin/env python3
"""Verify menu migration safety using a disposable, network-isolated MySQL container."""

import os
from pathlib import Path
import re
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "bixi-project-documents/sql"
CONTAINER = "bixi-menu-migration-test-" + uuid.uuid4().hex[:12]
IMAGE = os.environ.get("WORKFLOW_TEST_MYSQL_IMAGE", "mysql:8.0.45")


def docker(*args, sql=None, check=True):
    return subprocess.run(
        ["docker", *args], input=sql, text=True, capture_output=True, check=check
    )


def mysql(sql, force=False, check=True):
    args = ["--force"] if force else []
    return docker(
        "exec", "-i", CONTAINER, "mysql", "-h127.0.0.1", "-uroot",
        "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
        *args, sql=sql, check=check
    )


schema = (SQL / "01_init_all_tables.sql").read_text()
constraint_sql = (SQL / "02_add_constraints.sql").read_text()
seed = (SQL / "04_init_data.sql").read_text().splitlines()
migration = (SQL / "migrations/20260921_workflow_menus.sql").read_text()
tables = "\n".join(
    re.search(r"CREATE TABLE `" + table + r"` \(.*?;\n", schema, re.S)[0]
    for table in ("sys_menu", "sys_role", "sys_role_menu")
)
constraints = "\n".join(
    re.search(r"ALTER TABLE \w+\s+ADD CONSTRAINT " + name + r"\b[^;]*;", constraint_sql)[0]
    for name in ("uk_menu_perm", "chk_menu_visible", "fk_role_menu_role", "fk_role_menu_menu")
)
parent = next(line for line in seed if line.startswith("INSERT INTO sys_menu ")
              and "VALUES (5000," in line)
canonical_menus = "\n".join(line for line in seed
                            if line.startswith("INSERT INTO sys_menu ")
                            and re.search(r"VALUES \((501[0-4]|600[0-4]|601[12]|602[123]|603[12]),", line))


def reset(extra=""):
    # Keep canonical constraints enabled and seed the roles referenced by the test grants.
    mysql("DROP DATABASE IF EXISTS menu_migration; CREATE DATABASE menu_migration;"
          "USE menu_migration;\n" + tables + constraints
          + "INSERT INTO sys_role (id) VALUES (1), (7);" + parent + "\n" + extra)


def snapshot():
    return mysql("USE menu_migration; SELECT * FROM sys_menu ORDER BY id;"
                 "SELECT * FROM sys_role_menu ORDER BY role_id,menu_id;").stdout


def apply(force=False):
    return mysql("USE menu_migration;\n" + migration, force=force, check=False)


def rejected_without_changes(label, setup, expected_error):
    # --force verifies that continuing after SQL errors cannot bypass the guard.
    for force in (False, True):
        reset(setup)
        before = snapshot()
        result = apply(force)
        assert expected_error in result.stderr, (label, result.stdout, result.stderr)
        assert force or result.returncode != 0, label
        assert snapshot() == before, label + ": existing data or grants changed"
    print("PASS:", label, "(normal and --force)")


try:
    docker("run", "--detach", "--rm", "--name", CONTAINER, "--network", "none",
           "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", IMAGE)
    for attempt in range(90):
        if mysql("SELECT 1", check=False).returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError("Disposable MySQL did not become ready within 90 seconds")
    print("MySQL:", mysql("SELECT VERSION()").stdout.strip())

    rejected_without_changes(
        "custom route at reserved ID",
        "INSERT INTO sys_menu(id,name,path,parent_id,type) "
        "VALUES(6001,'Custom private menu','/private',-1,'0');",
        "Workflow menu conflict at id 6001",
    )
    for column, value in (
        ("permission", "'custom_manage'"),
        ("parent_id", "5000"),
        ("type", "'0'"),
        ("permission", "'WORKFLOW_DEFINITION_EDIT'"),
    ):
        rejected_without_changes(
            "reserved permission menu changed: " + column + "=" + value,
            canonical_menus + f"UPDATE sys_menu SET {column}={value} WHERE id=6032;",
            "Workflow menu conflict at id 6032",
        )
    rejected_without_changes(
        "custom parent menu",
        "UPDATE sys_menu SET path='/custom' WHERE id=5000;",
        "Workflow menu conflict at id 5000",
    )
    rejected_without_changes(
        "missing parent menu", "DELETE FROM sys_menu WHERE id=5000;",
        "Workflow menus require demo parent menu 5000",
    )
    for column, value in (("path", "'/workflow/task/todo'"),
                          ("permission", "'workflow_task_edit'")):
        rejected_without_changes(
            "route or permission already owned by another ID: " + column,
            f"INSERT INTO sys_menu(id,{column}) VALUES(9000,{value});",
            "Workflow menu identity already used at id 9000",
        )

    reset("INSERT INTO sys_menu(id,name,path) VALUES(9000,'Keep me','/custom');"
          "INSERT INTO sys_role_menu VALUES(7,9000,'2026-01-01');")
    before_custom = mysql("USE menu_migration; SELECT * FROM sys_menu WHERE id=9000;"
                          "SELECT * FROM sys_role_menu WHERE role_id=7;").stdout
    first = apply()
    assert first.returncode == 0, first.stderr
    assert mysql("USE menu_migration; SELECT COUNT(*) FROM sys_menu;"
                 "SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1;").stdout == "19\n17\n"
    assert mysql("USE menu_migration; SELECT * FROM sys_menu WHERE id=9000;"
                 "SELECT * FROM sys_role_menu WHERE role_id=7;").stdout == before_custom
    once = snapshot()
    second = apply()
    assert second.returncode == 0, second.stderr
    assert snapshot() == once, "Repeat run changed data"
    print("PASS: clean install, unrelated custom data preserved, repeat run unchanged")

    reset(canonical_menus + "UPDATE sys_menu SET name='Customized',icon='custom-icon',"
          "sn=87,visible='0',remark='Keep this',create_time='2020-01-01' WHERE id=6001;"
          "DELETE FROM sys_menu WHERE id=5014;")
    customized = mysql("USE menu_migration; SELECT * FROM sys_menu WHERE id=6001;").stdout
    result = apply()
    assert result.returncode == 0, result.stderr
    assert mysql("USE menu_migration; SELECT * FROM sys_menu WHERE id=6001;").stdout == customized
    assert mysql("USE menu_migration; SELECT COUNT(*) FROM sys_menu;"
                 "SELECT COUNT(*) FROM sys_role_menu;").stdout == "18\n17\n"
    print("PASS: compatible customized/partial install preserved and completed")

    rejected_without_changes(
        "grant write failure rolls back inserted menus",
        "CREATE TRIGGER reject_menu_grant BEFORE INSERT ON sys_role_menu FOR EACH ROW "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected grant failure';",
        "Injected grant failure",
    )
finally:
    docker("rm", "--force", "--volumes", CONTAINER, check=False)
