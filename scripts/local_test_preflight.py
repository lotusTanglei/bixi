"""Shared resource gate for Python test harnesses that create Docker resources."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def ensure_local_test_preflight(mode: str = "single") -> None:
    """Run the read-only shell gate unless an explicit bypass was requested."""
    # Keep the bypass deliberately strict across shell and Python entrypoints:
    # only the documented literal value true may skip a resource gate.
    if os.environ.get("BIXI_LOCAL_PREFLIGHT_SKIP", "").strip().lower() == "true":
        print(
            "WARNING: BIXI_LOCAL_PREFLIGHT_SKIP=true; local resource preflight was explicitly bypassed (only the exact value true skips it).",
            file=sys.stderr,
        )
        return

    environment = os.environ.copy()
    environment["BIXI_LOCAL_PREFLIGHT_FAIL_ON_CONFLICT"] = "true"
    # Disposable Python fixtures are resource-creating harnesses. Protect a
    # personal Docker VM by default while still allowing a CI operator to make
    # an explicit false override for a separately budgeted environment.
    environment["BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE"] = os.environ.get(
        "BIXI_LOCAL_PREFLIGHT_FAIL_ON_STORAGE_PRESSURE", "true"
    )
    result = subprocess.run(
        ["bash", str(ROOT / "scripts/local-test-preflight.sh"), mode],
        check=False,
        env=environment,
    )
    if result.returncode:
        print(
            f"Environment blocked: local resource preflight failed (status={result.returncode}); "
            "no Docker resources were created.",
            file=sys.stderr,
        )
        raise SystemExit(result.returncode)
