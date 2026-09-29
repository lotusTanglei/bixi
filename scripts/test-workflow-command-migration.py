#!/usr/bin/env python3
"""Exercise the START migration and uniqueness contracts on disposable MySQL."""

import os
from pathlib import Path
import re
import subprocess
import time
import unittest
import uuid

from local_test_preflight import ensure_local_test_preflight


ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "bixi-project-documents/sql"
CONTAINER = "bixi-command-migration-test-" + uuid.uuid4().hex[:12]
IMAGE = os.environ.get("WORKFLOW_TEST_MYSQL_IMAGE", "mysql:8.0.45")


def docker(*args, sql=None, check=True):
    return subprocess.run(["docker", *args], input=sql, text=True,
                          capture_output=True, check=check)


def mysql(sql, force=False, check=True):
    return docker("exec", "-i", CONTAINER, "mysql", "-h127.0.0.1", "-uroot",
                  "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
                  *(["--force"] if force else []), sql=sql, check=check)


def query(sql):
    return mysql("USE command_migration;\n" + sql).stdout


def table_ddl(name):
    schema = (SQL / "01_init_all_tables.sql").read_text()
    return re.search(r"CREATE TABLE `" + name + r"` \(.*?;\n", schema, re.S)[0]


def reset(extra=""):
    # Reconstruct the immediately preceding stage-one schema. All old columns stay intact.
    legacy = re.sub(r"^.*`start_request_id`.*\n", "", table_ddl("wf_process_instance"), flags=re.M)
    legacy = legacy.replace("UNIQUE KEY `uk_wf_process_instance_id`", "KEY `idx_process_instance_id`")
    mysql("DROP DATABASE IF EXISTS command_migration; CREATE DATABASE command_migration;"
          "USE command_migration;\n" + legacy + extra)


def apply(force=False):
    return mysql("USE command_migration;\n" +
                 (SQL / "migrations/20260921_workflow_stage2_start.sql").read_text(),
                 force=force, check=False)


def reset_business_occurrence(extra=""):
    schema = re.sub(r"^.*`business_owner`.*\n", "", table_ddl("wf_process_instance"), flags=re.M)
    schema = re.sub(r"^\s*UNIQUE KEY `uk_wf_process_business_round`.*\n", "", schema, flags=re.M)
    mysql("DROP DATABASE IF EXISTS command_migration; CREATE DATABASE command_migration;"
          "USE command_migration;\n" + schema + extra)


def apply_business_occurrence(force=False):
    return mysql("USE command_migration;\n" +
                 (SQL / "migrations/20260924_workflow_business_occurrence.sql").read_text(),
                 force=force, check=False)


def reset_leave_command(extra=""):
    mysql("DROP DATABASE IF EXISTS command_migration; CREATE DATABASE command_migration;"
          "USE command_migration;\n" + extra)


def apply_leave_command(force=False):
    return mysql("USE command_migration;\n" +
                 (SQL / "migrations/20260923_demo_leave_command.sql").read_text(),
                 force=force, check=False)


def reset_form_menus(extra=""):
    mysql("DROP DATABASE IF EXISTS command_migration; CREATE DATABASE command_migration;"
          "USE command_migration;\n" + table_ddl("sys_menu") + table_ddl("sys_role") +
          table_ddl("sys_role_menu") +
          "INSERT INTO sys_menu (id,name,path,parent_id,type) "
          "VALUES (6000,'Workflow','/workflow',0,'0');"
          "INSERT INTO sys_role (id,name,code,del_flag) "
          "VALUES (1,'Administrator','ROLE_ADMIN','0');" + extra)


def apply_form_menus(force=False):
    return mysql("USE command_migration;\n" +
                 (SQL / "migrations/20260924_workflow_form_menus.sql").read_text(),
                 force=force, check=False)


def form_menu_snapshot():
    return query("SELECT id,name,permission,path,parent_id,type,del_flag,status "
                 "FROM sys_menu WHERE id BETWEEN 6006 AND 6009 OR id BETWEEN 6051 AND 6054 "
                 "ORDER BY id;"
                 "SELECT role_id,menu_id FROM sys_role_menu "
                 "WHERE role_id=1 AND (menu_id BETWEEN 6006 AND 6009 OR menu_id BETWEEN 6051 AND 6054) "
                 "ORDER BY menu_id;")


def leave_command_schema_snapshot():
    return query("SELECT table_name,column_name,column_type,is_nullable,column_default,collation_name "
                 "FROM information_schema.columns WHERE table_schema=DATABASE() "
                 "AND table_name='demo_leave_command' ORDER BY ordinal_position;"
                 "SELECT table_name,index_name,non_unique,seq_in_index,column_name "
                 "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                 "AND table_name='demo_leave_command' ORDER BY index_name,seq_in_index;")


def schema_snapshot():
    return query("SELECT table_name,column_name,column_type,is_nullable,column_default,collation_name "
                 "FROM information_schema.columns WHERE table_schema=DATABASE() "
                 "ORDER BY table_name,ordinal_position;"
                 "SELECT table_name,index_name,non_unique,seq_in_index,column_name "
                 "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                 "ORDER BY table_name,index_name,seq_in_index;")


def insert_command(actor=1, request="00000000-0000-4000-8000-000000000001", scope="default",
                   operation="START", terminal=None):
    # Test values are fixed/locally generated; no runtime application secrets are used.
    terminal_value = "NULL" if terminal is None else "'" + terminal + "'"
    return mysql("USE command_migration; INSERT INTO wf_command "
                 "(id,tenant_scope,actor_id,actor_name,request_id,source_owner,operation,"
                 "request_hash,status,terminal_task_id,created_at) VALUES "
                 f"('{uuid.uuid4()}','{scope}',{actor},'migration-test','{request}','public',"
                 f"'{operation}','{'a' * 64}','SUCCEEDED',{terminal_value},UTC_TIMESTAMP(6));",
                 check=False)


class CommandMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        ensure_local_test_preflight("single")
        docker("run", "--detach", "--rm", "--name", CONTAINER, "--network", "none",
               "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", IMAGE)
        for _ in range(90):
            if mysql("SELECT 1", check=False).returncode == 0:
                print("MySQL:", mysql("SELECT VERSION()").stdout.strip(), flush=True)
                return
            time.sleep(1)
        raise RuntimeError("Disposable MySQL did not become ready within 90 seconds")

    def setUp(self):
        reset()

    def assert_migrates(self):
        result = apply()
        self.assertEqual(result.returncode, 0, result.stderr)

    def assert_rejected_unchanged(self, setup):
        for force in (False, True):
            with self.subTest(force=force):
                reset(setup)
                before = schema_snapshot()
                data = query("SELECT * FROM wf_process_instance ORDER BY id;")
                result = apply(force)
                self.assertIn("ERROR", result.stderr, (result.stdout, result.stderr))
                if not force:
                    self.assertNotEqual(result.returncode, 0)
                self.assertEqual(schema_snapshot(), before, "Rejected migration changed schema")
                self.assertEqual(query("SELECT * FROM wf_process_instance ORDER BY id;"), data)

    def test_clean_repeat_preserves_history_and_pending_commands(self):
        query("INSERT INTO wf_process_instance (id,process_instance_id,status,business_id,business_round) "
              "VALUES (1,'legacy-completed','completed',99,1), (2,'legacy-running','running',100,NULL);")
        old = query("SELECT id,process_instance_id,status,business_id,business_round "
                    "FROM wf_process_instance ORDER BY id;")
        self.assert_migrates()
        self.assertEqual(insert_command().returncode, 0)
        once = schema_snapshot()
        commands = query("SELECT * FROM wf_command ORDER BY id;")
        self.assert_migrates()
        self.assertEqual(schema_snapshot(), once)
        self.assertEqual(query("SELECT * FROM wf_command ORDER BY id;"), commands)
        self.assertEqual(query("SELECT id,process_instance_id,status,business_id,business_round "
                               "FROM wf_process_instance ORDER BY id;"), old)
        self.assertEqual(query("SELECT COUNT(*) FROM wf_process_instance WHERE start_request_id IS NULL;"), "2\n")

    def test_duplicate_engine_ids_reject_before_any_schema_change(self):
        self.assert_rejected_unchanged(
            "INSERT INTO wf_process_instance(id,process_instance_id) VALUES(1,'duplicate'),(2,'duplicate');")

    def test_wrong_same_named_index_rejected(self):
        for definition in ("KEY uk_wf_process_instance_id(process_instance_id)",
                           "UNIQUE KEY uk_wf_process_instance_id(start_user_id)"):
            self.assert_rejected_unchanged("ALTER TABLE wf_process_instance ADD " + definition + ";")

    def test_engine_id_unique_and_business_round_uniqueness_is_a_later_migration(self):
        self.assert_migrates()
        query("INSERT INTO wf_process_instance(id,process_instance_id,business_table,business_id,business_round) "
              "VALUES(1,'first','demo_leave_request',7,1),(2,'second','demo_leave_request',7,1);")
        result = mysql("USE command_migration; INSERT INTO wf_process_instance(id,process_instance_id) "
                       "VALUES(3,'first');", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("uk_wf_process_instance_id", result.stderr)

    def test_business_occurrence_migration_is_repeatable_and_owner_scoped(self):
        reset_business_occurrence(
            "ALTER TABLE wf_process_instance ADD UNIQUE KEY uk_wf_process_business_round"
            "(tenant_id,business_table,business_id,business_round);"
            "INSERT INTO wf_process_instance"
            "(id,process_instance_id,process_key,business_table,business_id,business_round,tenant_id) VALUES"
            "(1,'first','demo_leave_approval','demo_leave_request',7,1,1),"
            "(2,'other-occurrence','demo_leave_approval','demo_leave_request',8,1,2),"
            "(3,'public-one','public',NULL,NULL,NULL,1),"
            "(4,'public-two','public',NULL,NULL,NULL,1);")
        first = apply_business_occurrence()
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(query("SELECT business_owner FROM wf_process_instance "
                               "WHERE business_id IS NOT NULL ORDER BY id;"), "upms\nupms\n")
        once = schema_snapshot()
        second = apply_business_occurrence()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(schema_snapshot(), once)

        other_owner = mysql(
            "USE command_migration; INSERT INTO wf_process_instance"
            "(id,process_instance_id,process_key,business_owner,business_table,business_id,business_round,tenant_id) "
            "VALUES(5,'other-owner','demo_leave_approval','hr','demo_leave_request',7,1,1);",
            check=False)
        self.assertEqual(other_owner.returncode, 0, other_owner.stderr)
        duplicate = mysql(
            "USE command_migration; INSERT INTO wf_process_instance"
            "(id,process_instance_id,process_key,business_owner,business_table,business_id,business_round,tenant_id) "
            "VALUES(6,'duplicate','demo_leave_approval','upms','demo_leave_request',7,1,2);", check=False)
        self.assertNotEqual(duplicate.returncode, 0)
        self.assertIn("uk_wf_process_business_round", duplicate.stderr)

    def test_business_occurrence_migration_rejects_duplicates_without_changes(self):
        for force in (False, True):
            with self.subTest(force=force):
                reset_business_occurrence(
                    "ALTER TABLE wf_process_instance ADD UNIQUE KEY uk_wf_process_business_round"
                    "(tenant_id,business_table,business_id,business_round);"
                    "INSERT INTO wf_process_instance"
                    "(id,process_instance_id,process_key,business_table,business_id,business_round,tenant_id) VALUES"
                    "(1,'first','demo_leave_approval','demo_leave_request',7,1,1),"
                    "(2,'duplicate','demo_leave_approval','demo_leave_request',7,1,2);")
                before = schema_snapshot()
                data = query("SELECT * FROM wf_process_instance ORDER BY id;")
                result = apply_business_occurrence(force)
                self.assertIn("Duplicate workflow business occurrences", result.stderr)
                if not force:
                    self.assertNotEqual(result.returncode, 0)
                self.assertEqual(schema_snapshot(), before)
                self.assertEqual(query("SELECT * FROM wf_process_instance ORDER BY id;"), data)

    def test_business_occurrence_migration_rejects_unknown_owner_without_changes(self):
        reset_business_occurrence(
            "INSERT INTO wf_process_instance"
            "(id,process_instance_id,process_key,business_table,business_id,business_round,tenant_id) VALUES"
            "(1,'unknown','custom_process','custom_table',7,1,1);")
        before = schema_snapshot()
        data = query("SELECT * FROM wf_process_instance;")
        result = apply_business_occurrence()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("manual owner reconciliation", result.stderr)
        self.assertEqual(schema_snapshot(), before)
        self.assertEqual(query("SELECT * FROM wf_process_instance;"), data)

    def test_business_occurrence_migration_rejects_wrong_named_index(self):
        for definition in (
                "KEY uk_wf_process_business_round(tenant_id,business_table,business_id,business_round)",
                "UNIQUE KEY uk_wf_process_business_round(tenant_id,business_id,business_round)"):
            with self.subTest(definition=definition):
                reset_business_occurrence("ALTER TABLE wf_process_instance ADD " + definition + ";")
                before = schema_snapshot()
                result = apply_business_occurrence()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("incompatible definition", result.stderr)
                self.assertEqual(schema_snapshot(), before)

    def test_request_scope_does_not_include_operation(self):
        self.assert_migrates()
        self.assertEqual(insert_command().returncode, 0)
        duplicate = insert_command(operation="COMPLETE")
        self.assertNotEqual(duplicate.returncode, 0)
        self.assertIn("uk_wf_command_request", duplicate.stderr)
        self.assertEqual(insert_command(actor=2).returncode, 0)
        self.assertEqual(insert_command(scope="other-scope").returncode, 0)

    def test_terminal_task_is_unique_across_requests_and_null_is_repeatable(self):
        self.assert_migrates()
        self.assertEqual(insert_command(actor=1, terminal="task-1").returncode, 0)
        duplicate = insert_command(actor=2, terminal="task-1")
        self.assertNotEqual(duplicate.returncode, 0)
        self.assertIn("uk_wf_command_terminal_task", duplicate.stderr)
        self.assertEqual(insert_command(actor=3).returncode, 0)
        self.assertEqual(insert_command(actor=4).returncode, 0)

    def test_migrated_command_columns_match_canonical_schema(self):
        self.assert_migrates()
        columns = ("SELECT column_name,column_type,is_nullable,column_default,collation_name "
                   "FROM information_schema.columns WHERE table_schema=DATABASE() "
                   "AND table_name='wf_command' ORDER BY ordinal_position;")
        migrated = query(columns)
        mysql("DROP DATABASE IF EXISTS command_canonical; CREATE DATABASE command_canonical;"
              "USE command_canonical;" + table_ddl("wf_command"))
        canonical = mysql("USE command_canonical;" + columns).stdout
        self.assertEqual(migrated, canonical)
        self.assertIn("ascii_bin", canonical)

    def test_leave_command_clean_repeat_preserves_commands(self):
        reset_leave_command()
        first = apply_leave_command()
        self.assertEqual(first.returncode, 0, first.stderr)
        query("INSERT INTO demo_leave_command "
              "(command_id,tenant_scope,actor_id,actor_name,client_request_id,operation,leave_id,round,"
              "request_hash,payload_json,status,created_at) VALUES "
              "('00000000-0000-4000-8000-000000000001','1',11,'migration-test',"
              "'00000000-0000-4000-8000-000000000002','START',42,1,'" + "b" * 64 +
              "','{}','ACCEPTED',UTC_TIMESTAMP(6));")
        schema = leave_command_schema_snapshot()
        data = query("SELECT * FROM demo_leave_command;")
        second = apply_leave_command()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(leave_command_schema_snapshot(), schema)
        self.assertEqual(query("SELECT * FROM demo_leave_command;"), data)

    def test_leave_command_columns_and_indexes_match_canonical_schema(self):
        reset_leave_command()
        self.assertEqual(apply_leave_command().returncode, 0)
        migrated = leave_command_schema_snapshot()
        mysql("DROP DATABASE IF EXISTS command_canonical; CREATE DATABASE command_canonical;"
              "USE command_canonical;" + table_ddl("demo_leave_command"))
        canonical = mysql("USE command_canonical;"
                          "SELECT table_name,column_name,column_type,is_nullable,column_default,collation_name "
                          "FROM information_schema.columns WHERE table_schema=DATABASE() "
                          "AND table_name='demo_leave_command' ORDER BY ordinal_position;"
                          "SELECT table_name,index_name,non_unique,seq_in_index,column_name "
                          "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                          "AND table_name='demo_leave_command' ORDER BY index_name,seq_in_index;").stdout
        self.assertEqual(migrated, canonical)

    def test_leave_command_incompatible_existing_table_is_rejected_unchanged(self):
        for force in (False, True):
            with self.subTest(force=force):
                reset_leave_command("CREATE TABLE demo_leave_command ("
                                    "command_id CHAR(36) NOT NULL PRIMARY KEY, legacy_value VARCHAR(32));"
                                    "INSERT INTO demo_leave_command VALUES "
                                    "('00000000-0000-4000-8000-000000000001','preserve-me');")
                before = leave_command_schema_snapshot()
                data = query("SELECT * FROM demo_leave_command;")
                result = apply_leave_command(force)
                self.assertIn("ERROR", result.stderr, (result.stdout, result.stderr))
                if not force:
                    self.assertNotEqual(result.returncode, 0)
                self.assertEqual(leave_command_schema_snapshot(), before)
                self.assertEqual(query("SELECT * FROM demo_leave_command;"), data)

    def test_form_menu_migration_is_repeatable_and_preserves_matching_custom_presentation(self):
        reset_form_menus("INSERT INTO sys_menu "
                         "(id,name,permission,path,parent_id,icon,type,del_flag,status) VALUES "
                         "(6006,'Customized forms',NULL,'/workflow/form/index',6000,'custom-icon','0','0','0');")
        first = apply_form_menus()
        self.assertEqual(first.returncode, 0, first.stderr)
        once = form_menu_snapshot()
        self.assertEqual(query("SELECT name,icon FROM sys_menu WHERE id=6006;"),
                         "Customized forms\tcustom-icon\n")
        self.assertEqual(query("SELECT COUNT(*) FROM sys_menu "
                               "WHERE id BETWEEN 6006 AND 6009 OR id BETWEEN 6051 AND 6054;"), "8\n")
        self.assertEqual(query("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=1 AND "
                               "(menu_id BETWEEN 6006 AND 6009 OR menu_id BETWEEN 6051 AND 6054);"), "8\n")
        second = apply_form_menus()
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertEqual(form_menu_snapshot(), once)

    def test_form_menu_identity_conflict_rejects_without_partial_rows_or_grants(self):
        for conflict in (
                "INSERT INTO sys_menu (id,name,path,parent_id,type) "
                "VALUES (6008,'Conflict','/custom/version',6000,'0');",
                "INSERT INTO sys_menu (id,name,path,parent_id,type) "
                "VALUES (7000,'Conflict','/workflow/form/version',6000,'0');"):
            with self.subTest(conflict=conflict):
                reset_form_menus(conflict)
                before = form_menu_snapshot()
                result = apply_form_menus()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Workflow form menu", result.stderr)
                self.assertEqual(form_menu_snapshot(), before)


if __name__ == "__main__":
    try:
        unittest.main(verbosity=2)
    finally:
        docker("rm", "--force", "--volumes", CONTAINER, check=False)
