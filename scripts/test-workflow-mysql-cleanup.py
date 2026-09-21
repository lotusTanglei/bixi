#!/usr/bin/env python3
"""Run the real workflow helper with fake Docker/Maven; never touch a daemon."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().with_name('test-workflow-mysql.sh')
CREATED_ID = 'a' * 64


class CleanupTest(unittest.TestCase):
    def run_helper(self, scenario):
        with tempfile.TemporaryDirectory(prefix='workflow-cleanup-test-') as directory:
            temp = Path(directory)
            commands = temp / 'commands.jsonl'
            docker = temp / 'docker'
            docker.write_text('#!' + sys.executable + '\n' + r'''
import json, os, sys
with open(os.environ['WORKFLOW_CLEANUP_TEST_LOG'], 'a') as log:
    log.write(json.dumps(sys.argv[1:]) + '\n')
command = sys.argv[1]
if command == 'run':
    if os.environ['WORKFLOW_CLEANUP_TEST_SCENARIO'] == 'conflict':
        print('Conflict. The container name is already in use.', file=sys.stderr)
        sys.exit(125)
    print('a' * 64)
elif command == 'port':
    print('127.0.0.1:13306')
elif command not in ('exec', 'rm'):
    sys.exit(97)
''')
            docker.chmod(0o755)
            for name, body in [('mvn', 'exit 0'), ('uname', "printf 'Linux\\n'"), ('openssl', "printf '000000000000000000000000000000000000000000000000\\n'")]:
                stub = temp / name
                stub.write_text('#!/bin/sh\n' + body + '\n')
                stub.chmod(0o755)
            env = dict(os.environ, PATH=str(temp) + os.pathsep + os.environ['PATH'],
                       WORKFLOW_CLEANUP_TEST_LOG=str(commands), WORKFLOW_CLEANUP_TEST_SCENARIO=scenario,
                       WORKFLOW_TEST_CLASSES='WorkflowApprovalIntegrationTest')
            result = subprocess.run(['/bin/bash', str(SCRIPT), 'cloud'], env=env,
                                    text=True, capture_output=True, timeout=10)
            calls = [json.loads(line) for line in commands.read_text().splitlines()]
            run = next(call for call in calls if call[0] == 'run')
            self.assertFalse(Path(run[run.index('--env-file') + 1]).exists(), 'Temporary credential file remains after exit')
            return result, calls

    def test_name_conflict_never_removes_existing_container(self):
        result, calls = self.run_helper('conflict')
        self.assertEqual(result.returncode, 125, result.stderr)
        self.assertEqual([call for call in calls if call[0] == 'rm'], [],
                         'Failed creation must not remove the existing same-name container')

    def test_success_cleans_only_returned_container_id(self):
        result, calls = self.run_helper('success')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual([call for call in calls if call[0] == 'rm'],
                         [['rm', '--force', '--volumes', CREATED_ID]])


if __name__ == '__main__':
    unittest.main(verbosity=2)
