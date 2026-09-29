#!/usr/bin/env python3
"""Exercise the Quartz retry-history migration against a disposable MySQL schema."""

from pathlib import Path
import os
import subprocess
import time
import unittest
import uuid

from local_test_preflight import ensure_local_test_preflight


ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "bixi-project-documents/sql/migrations/20260926_quartz_retry_history.sql"
CONTAINER = "bixi-quartz-migration-test-" + uuid.uuid4().hex[:12]
IMAGE = os.environ.get("QUARTZ_TEST_MYSQL_IMAGE", "mysql:8.4.3")

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


def mysql(sql, check=True):
    return docker("exec", "-i", CONTAINER, "mysql", "-h127.0.0.1", "-uroot",
                  "--default-character-set=utf8mb4", "--batch", "--skip-column-names",
                  sql=sql, check=check)


def query(sql):
    return mysql("USE quartz_migration;\n" + sql).stdout


def legacy_schema():
    return """
CREATE TABLE sys_job (
  id BIGINT NOT NULL,
  misfire_policy VARCHAR(16) NULL,
  tenant_id BIGINT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
CREATE TABLE sys_job_record (
  id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  message VARCHAR(500) NULL,
  create_time DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;
"""


def reset(extra=""):
    mysql("DROP DATABASE IF EXISTS quartz_migration; CREATE DATABASE quartz_migration;"
          "USE quartz_migration;\n" + legacy_schema() + extra)


def apply():
    return mysql("USE quartz_migration;\n" + MIGRATION.read_text(), check=False)


def schema_snapshot():
    return query(
        "SELECT table_name,column_name,column_type,is_nullable,column_default "
        "FROM information_schema.columns WHERE table_schema=DATABASE() "
        "AND table_name IN ('sys_job','sys_job_record') ORDER BY table_name,ordinal_position;"
        "SELECT table_name,index_name,non_unique,seq_in_index,column_name "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name IN ('sys_job','sys_job_record') ORDER BY table_name,index_name,seq_in_index;"
    )


class QuartzMigrationTest(unittest.TestCase):
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

    def test_legacy_schema_migrates_and_repeat_is_unchanged(self):
        self.assertEqual(apply().returncode, 0)
        columns = query(
            "SELECT table_name,column_name,column_type,is_nullable,column_default "
            "FROM information_schema.columns WHERE table_schema=DATABASE() "
            "AND column_name IN ('retry_count','retry_interval_seconds','execution_id',"
            "'attempt','max_attempts','trigger_type','recovered') ORDER BY table_name,column_name;"
        )
        for name in ('retry_count', 'retry_interval_seconds', 'execution_id', 'attempt',
                     'max_attempts', 'trigger_type', 'recovered'):
            self.assertIn(name, columns)
        indexes = query(
            "SELECT index_name,seq_in_index,column_name FROM information_schema.statistics "
            "WHERE table_schema=DATABASE() AND table_name='sys_job_record' "
            "AND index_name IN ('idx_job_record_job_created','idx_job_record_execution') "
            "ORDER BY index_name,seq_in_index;"
        )
        self.assertIn("idx_job_record_job_created\t1\tjob_id", indexes)
        self.assertIn("idx_job_record_job_created\t2\tcreate_time", indexes)
        self.assertIn("idx_job_record_job_created\t3\tid", indexes)
        self.assertIn("idx_job_record_execution\t1\texecution_id", indexes)
        self.assertIn("idx_job_record_execution\t2\tattempt", indexes)
        once = schema_snapshot()
        self.assertEqual(apply().returncode, 0)
        self.assertEqual(schema_snapshot(), once)

    def test_conflicting_existing_definition_rejects_without_schema_change(self):
        reset("ALTER TABLE sys_job ADD COLUMN retry_count BIGINT NOT NULL DEFAULT 0;")
        before = schema_snapshot()
        result = apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Conflicting sys_job.retry_count definition", result.stderr)
        self.assertEqual(schema_snapshot(), before)

    def test_conflicting_index_rejects_without_schema_change(self):
        reset("CREATE INDEX idx_job_record_execution ON sys_job_record(job_id);\n")
        before = schema_snapshot()
        result = apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Conflicting idx_job_record_execution definition", result.stderr)
        self.assertEqual(schema_snapshot(), before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
