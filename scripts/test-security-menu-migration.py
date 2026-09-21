#!/usr/bin/env python3
"""Check security permission upgrades using a disposable, network-isolated MySQL."""

import os
from pathlib import Path
import re
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "bixi-project-documents/sql"
MIGRATION = SQL / "migrations/20260921_security_permissions.sql"
IMAGE = os.environ.get("SECURITY_TEST_MYSQL_IMAGE", "mysql:8.0.45")
CONTAINER = "bixi-security-migration-test-" + uuid.uuid4().hex[:12]
EXPECTED = {
    1106: ("sys_user_view", 1100),
    1107: ("sys_user_import", 1100),
    1204: ("sys_menu_view", 1200),
    1306: ("sys_role_view", 1300),
    1307: ("sys_role_import", 1300),
    1404: ("sys_dept_view", 1400),
    1405: ("sys_dept_export", 1400),
    1406: ("sys_dept_import", 1400),
    2103: ("sys_log_view", 2100),
    2404: ("sys_client_view", 2400),
    2405: ("sys_client_export", 2400),
    2602: ("sys_token_view", 2600),
    4003: ("sys_system_view", 4002),
    2901: ("sys_notice_view", 2003471392852377602),
    2902: ("sys_notice_add", 2003471392852377602),
    2903: ("sys_notice_edit", 2003471392852377602),
    2904: ("sys_notice_del", 2003471392852377602),
    2905: ("sys_notice_send", 2003471392852377602),
}
IDS = ",".join(str(menu_id) for menu_id in EXPECTED)


def docker(*args, sql=None, check=True):
    result = subprocess.run(["docker", *args], input=sql, text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr)
    return result


def mysql(sql, force=False, check=True):
    return docker(
        "exec", "-i", CONTAINER, "mysql", "-h127.0.0.1", "-uroot",
        "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
        *(["--force"] if force else []), sql=sql, check=check
    )


schema = (SQL / "01_init_all_tables.sql").read_text()
constraint_sql = (SQL / "02_add_constraints.sql").read_text()
seed = (SQL / "04_init_data.sql").read_text().splitlines()
tables = "\n".join(
    re.search(r"CREATE TABLE `" + table + r"` \(.*?;\n", schema, re.S)[0]
    for table in ("sys_menu", "sys_role", "sys_role_menu")
)
constraints = "\n".join(
    re.search(r"ALTER TABLE \w+\s+ADD CONSTRAINT " + name + r"\b[^;]*;", constraint_sql)[0]
    for name in ("uk_menu_perm", "chk_menu_visible", "fk_role_menu_role", "fk_role_menu_menu")
)
menu_seed = {
    int(re.search(r"VALUES \((\d+),", line)[1]): line
    for line in seed if line.startswith("INSERT INTO sys_menu ")
}
old_menus = "\n".join(line for menu_id, line in menu_seed.items() if menu_id not in EXPECTED)
new_menus = "\n".join(menu_seed[menu_id] for menu_id in EXPECTED)
admin_role = next(line for line in seed if line.startswith("INSERT INTO sys_role ")
                  and "VALUES (1," in line)
old_grants = "\n".join(
    line for line in seed if line.startswith("INSERT INTO sys_role_menu ")
    and (match := re.search(r"VALUES \(1, (\d+),", line))
    and int(match[1]) not in EXPECTED
)


def reset(extra=""):
    # Model a populated pre-security-upgrade database, including unrelated custom grants.
    mysql("DROP DATABASE IF EXISTS security_migration; CREATE DATABASE security_migration;"
          "USE security_migration;\n" + tables + constraints + admin_role + old_menus
          + old_grants + "INSERT INTO sys_role(id,code) VALUES(7,'CUSTOM_ROLE');"
          "INSERT INTO sys_menu(id,name,path) VALUES(9000000001,'Keep me','/custom');"
          "INSERT INTO sys_role_menu VALUES(7,9000000001,'2026-01-01');" + extra)


def snapshot(exclude_new=False):
    menu_filter = f" WHERE id NOT IN ({IDS})" if exclude_new else ""
    grant_filter = f" WHERE menu_id NOT IN ({IDS})" if exclude_new else ""
    return mysql("USE security_migration; SELECT * FROM sys_role ORDER BY id;"
                 f"SELECT * FROM sys_menu{menu_filter} ORDER BY id;"
                 f"SELECT * FROM sys_role_menu{grant_filter} ORDER BY role_id,menu_id;").stdout


def apply(force=False):
    # Without an incremental script, an ordinary upgrade leaves old permissions unchanged.
    migration = MIGRATION.read_text() if MIGRATION.exists() else ""
    return mysql("USE security_migration;\n" + migration, force=force, check=False)


def check_permissions():
    rows = mysql("USE security_migration; SELECT m.id,m.permission,m.parent_id,m.type "
                 "FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.id "
                 f"WHERE rm.role_id=1 AND m.id IN ({IDS}) ORDER BY m.id;").stdout
    expected = "".join(f"{menu_id}\t{permission}\t{parent}\t1\n"
                       for menu_id, (permission, parent) in sorted(EXPECTED.items()))
    assert rows == expected, "Existing administrator did not receive exactly the 18 canonical permissions"
    assert mysql("USE security_migration; SELECT COUNT(*) FROM sys_role_menu "
                 f"WHERE role_id<>1 AND menu_id IN ({IDS});").stdout == "0\n"


def rejected_without_changes(label, setup, expected_error):
    for force in (False, True):
        reset(setup)
        before = snapshot()
        result = apply(force)
        assert expected_error in result.stderr, (label, result.stderr)
        assert force or result.returncode != 0, label
        assert snapshot() == before, label + ": data or grants changed on failure"
    print("PASS:", label, "(normal and --force)")


container_id = None
try:
    container_id = docker(
        "run", "--detach", "--rm", "--name", CONTAINER, "--network", "none",
        "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", IMAGE
    ).stdout.strip()
    for attempt in range(90):
        if mysql("SELECT 1", check=False).returncode == 0:
            break
        time.sleep(1)
    else:
        raise RuntimeError("Disposable MySQL did not become ready within 90 seconds")
    print("MySQL:", mysql("SELECT VERSION()").stdout.strip())

    reset()
    unrelated = snapshot(exclude_new=True)
    result = apply()
    assert result.returncode == 0, result.stderr
    check_permissions()
    assert snapshot(exclude_new=True) == unrelated, "Old data or custom grants changed"
    once = snapshot()
    result = apply()
    assert result.returncode == 0, result.stderr
    assert snapshot() == once, "Repeat migration changed data or duplicated grants"
    print("PASS: old database gains all 18 permissions; custom data preserved; repeat unchanged")

    reset(new_menus + "UPDATE sys_menu SET name='Customized',icon='custom-icon',sn=87,"
          "visible='1',remark='Keep this',create_time='2020-01-01' WHERE id=1106;"
          "DELETE FROM sys_menu WHERE id=2905;"
          "INSERT INTO sys_role_menu VALUES(1,1106,'2020-01-01');")
    customized = mysql("USE security_migration; SELECT * FROM sys_menu WHERE id=1106;"
                       "SELECT * FROM sys_role_menu WHERE role_id=1 AND menu_id=1106;").stdout
    result = apply()
    assert result.returncode == 0, result.stderr
    check_permissions()
    assert mysql("USE security_migration; SELECT * FROM sys_menu WHERE id=1106;"
                 "SELECT * FROM sys_role_menu WHERE role_id=1 AND menu_id=1106;").stdout == customized
    print("PASS: partial installation completed without overwriting menu/grant metadata")

    for column, value in (("permission", "'custom_manage'"),
                          ("permission", "'SYS_USER_VIEW'"),
                          ("path", "'/private'"), ("parent_id", "1200"), ("type", "'0'")):
        rejected_without_changes(
            "conflicting reserved menu " + column + "=" + value,
            new_menus + f"UPDATE sys_menu SET {column}={value} WHERE id=1106;",
            "Security menu conflict at id 1106",
        )
    for permission in ("sys_user_view", "SYS_USER_VIEW"):
        rejected_without_changes(
            "permission owned by a different ID: " + permission,
            f"UPDATE sys_menu SET permission='{permission}' WHERE id=9000000001;",
            "Security permission already used at id 9000000001",
        )
    for parent_id in sorted({parent for _, parent in EXPECTED.values()}):
        rejected_without_changes(
            "missing required parent " + str(parent_id),
            f"DELETE FROM sys_menu WHERE id={parent_id};",
            "Security permissions require parent menu " + str(parent_id),
        )
    rejected_without_changes("conflicting parent route",
                             "UPDATE sys_menu SET path='/custom-users' WHERE id=1100;",
                             "Security parent menu conflict at id 1100")
    for setup in ("DELETE FROM sys_role WHERE id=1;",
                  "UPDATE sys_role SET code='CUSTOM_ADMIN' WHERE id=1;"):
        rejected_without_changes("missing or repurposed administrator role", setup,
                                 "Security permissions require role 1 ROLE_ADMIN")
    rejected_without_changes(
        "grant write failure rolls back menu inserts",
        "CREATE TRIGGER reject_security_grant BEFORE INSERT ON sys_role_menu FOR EACH ROW "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected grant failure';",
        "Injected grant failure",
    )
finally:
    if container_id:
        docker("rm", "--force", "--volumes", container_id, check=False)
