#!/usr/bin/env python3
"""Exercise the START migration and uniqueness contracts on disposable MySQL."""

import os
from pathlib import Path
import re
import subprocess
import time
import unittest
import uuid


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

    def test_engine_id_unique_and_business_round_uniqueness_deferred(self):
        self.assert_migrates()
        query("INSERT INTO wf_process_instance(id,process_instance_id,business_table,business_id,business_round) "
              "VALUES(1,'first','demo_leave_request',7,1),(2,'second','demo_leave_request',7,1);")
        result = mysql("USE command_migration; INSERT INTO wf_process_instance(id,process_instance_id) "
                       "VALUES(3,'first');", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("uk_wf_process_instance_id", result.stderr)

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
        mysql("CREATE DATABASE command_canonical; USE command_canonical;" + table_ddl("wf_command"))
        canonical = mysql("USE command_canonical;" + columns).stdout
        self.assertEqual(migrated, canonical)
        self.assertIn("ascii_bin", canonical)


if __name__ == "__main__":
    try:
        unittest.main(verbosity=2)
    finally:
        docker("rm", "--force", "--volumes", CONTAINER, check=False)
