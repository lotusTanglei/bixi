#!/usr/bin/env python3
"""Exercise the generator template-group migration against a legacy MySQL schema."""

from pathlib import Path
import os
import re
import subprocess
import time
import unittest
import uuid

from local_test_preflight import ensure_local_test_preflight


ROOT = Path(__file__).resolve().parents[1]
SQL_ROOT = ROOT / "bixi-project-documents/sql"
MIGRATION = SQL_ROOT / "migrations/20260924_generator_template_group_uniqueness.sql"
CONTAINER = "bixi-generator-migration-test-" + uuid.uuid4().hex[:12]
IMAGE = os.environ.get("GENERATOR_TEST_MYSQL_IMAGE", "mysql:8.4.3")

DOCKER_TIMEOUT_SECONDS = 30
STARTUP_TIMEOUT_SECONDS = 30
CONTAINER_CLEANUP_TIMEOUT_SECONDS = 5


class EnvironmentBlocked(RuntimeError):
    """The disposable migration environment could not be started or reached."""


def _error_detail(error):
    detail = getattr(error, "stderr", None) or getattr(error, "stdout", None) or str(error)
    lines = [line.strip() for line in str(detail).splitlines() if line.strip()]
    return lines[-1][:240] if lines else "no Docker diagnostic was returned"


def docker(*args, sql=None, check=True, timeout=DOCKER_TIMEOUT_SECONDS):
    try:
        return subprocess.run(["docker", *args], input=sql, text=True,
                              capture_output=True, check=check, timeout=timeout)
    except FileNotFoundError as error:
        raise EnvironmentBlocked("Environment blocked: Docker CLI is unavailable") from error
    except subprocess.TimeoutExpired as error:
        raise EnvironmentBlocked(
            f"Environment blocked: Docker command timed out after {timeout} seconds"
        ) from error


def cleanup_container():
    try:
        docker("rm", "--force", CONTAINER, check=False,
               timeout=CONTAINER_CLEANUP_TIMEOUT_SECONDS)
    except EnvironmentBlocked:
        # A dead daemon cannot remove a container; the exact generated name is
        # retained so the next run can identify it without touching Bixi services.
        pass


def docker_daemon_unavailable(result):
    detail = f"{result.stdout or ''}\n{result.stderr or ''}".lower()
    return any(marker in detail for marker in (
        "cannot connect to the docker daemon",
        "is the docker daemon running",
        "error during connect",
    ))


def mysql(sql, force=False, check=True):
    return docker("exec", "-i", CONTAINER, "mysql", "-h127.0.0.1", "-uroot",
                  "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
                  *( ["--force"] if force else [] ), sql=sql, check=check)


def query(sql):
    return mysql("USE generator_migration;\n" + sql).stdout


def legacy_table_ddl():
    schema = (SQL_ROOT / "01_schema.sql").read_text()
    match = re.search(r"CREATE TABLE `gen_group` \(.*?\n\) ENGINE=.*?;\n", schema, re.S)
    if not match:
        raise RuntimeError("gen_group DDL is missing from the canonical schema")
    ddl = match.group(0)
    ddl = re.sub(r"^\s*`active_group_name`.*\n", "", ddl, flags=re.M)
    ddl = re.sub(r"^\s*UNIQUE KEY `uk_gen_group_active_name`.*\n", "", ddl, flags=re.M)
    ddl = ddl.replace("PRIMARY KEY (`id`) USING BTREE,\n", "PRIMARY KEY (`id`) USING BTREE\n")
    return ddl


def reset(extra=""):
    mysql("DROP DATABASE IF EXISTS generator_migration; CREATE DATABASE generator_migration;"
          "USE generator_migration;\n" + legacy_table_ddl() + extra)


def apply(force=False):
    return mysql("USE generator_migration;\n" + MIGRATION.read_text(), force=force, check=False)


def schema_snapshot():
    return query(
        "SELECT table_name,column_name,column_type,is_nullable,column_default,"
        "generation_expression,extra FROM information_schema.columns "
        "WHERE table_schema=DATABASE() AND table_name='gen_group' ORDER BY ordinal_position;"
        "SELECT table_name,index_name,non_unique,seq_in_index,column_name "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name='gen_group' ORDER BY index_name,seq_in_index;"
    )


class GeneratorMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        ensure_local_test_preflight("single")
        try:
            docker("run", "--detach", "--rm", "--name", CONTAINER, "--network", "none",
                   "-e", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", IMAGE)
            deadline = time.monotonic() + STARTUP_TIMEOUT_SECONDS
            while time.monotonic() < deadline:
                probe = mysql("SELECT 1", check=False)
                if probe.returncode == 0:
                    print("MySQL:", mysql("SELECT VERSION()").stdout.strip(), flush=True)
                    return
                if docker_daemon_unavailable(probe):
                    raise EnvironmentBlocked(
                        f"Environment blocked: Docker daemon is unavailable ({_error_detail(probe)})"
                    )
                time.sleep(1)
            raise EnvironmentBlocked(
                "Environment blocked: disposable MySQL was not ready within "
                f"{STARTUP_TIMEOUT_SECONDS} seconds"
            )
        except subprocess.CalledProcessError as error:
            cleanup_container()
            raise EnvironmentBlocked(
                f"Environment blocked: Docker could not start disposable MySQL ({_error_detail(error)})"
            ) from error
        except EnvironmentBlocked:
            cleanup_container()
            raise
        except Exception:
            cleanup_container()
            raise

    @classmethod
    def tearDownClass(cls):
        cleanup_container()

    def setUp(self):
        reset()

    def assert_migrates(self):
        result = apply()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_existing_schema_migrates_and_repeat_is_unchanged(self):
        query("INSERT INTO gen_group(id,group_name,del_flag) VALUES "
              "(1,'active','0'),(2,'removed','1');")
        self.assert_migrates()
        columns = query("SELECT column_name,extra,generation_expression "
                        "FROM information_schema.columns WHERE table_schema=DATABASE() "
                        "AND table_name='gen_group' AND column_name='active_group_name';")
        self.assertIn("STORED GENERATED", columns)
        self.assertIn("del_flag", columns)
        indexes = query("SELECT index_name,non_unique,column_name FROM information_schema.statistics "
                        "WHERE table_schema=DATABASE() AND table_name='gen_group' "
                        "AND index_name='uk_gen_group_active_name';")
        self.assertIn("uk_gen_group_active_name\t0\tactive_group_name", indexes)
        once = schema_snapshot()
        self.assert_migrates()
        self.assertEqual(schema_snapshot(), once)
        self.assertEqual(query("SELECT id,group_name,active_group_name FROM gen_group ORDER BY id;"),
                         "1\tactive\tactive\n2\tremoved\tNULL\n")

    def test_duplicate_active_names_reject_before_schema_change(self):
        reset("INSERT INTO gen_group(id,group_name,del_flag) VALUES "
              "(1,'duplicate','0'),(2,'duplicate','0');")
        before = schema_snapshot()
        result = apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Duplicate active generator group names", result.stderr)
        self.assertEqual(schema_snapshot(), before)

    def test_incompatible_existing_generated_column_rejects_unchanged(self):
        reset("ALTER TABLE gen_group ADD COLUMN active_group_name varchar(255) "
              "CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL;")
        before = schema_snapshot()
        result = apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("active_group_name is incompatible", result.stderr)
        self.assertEqual(schema_snapshot(), before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
