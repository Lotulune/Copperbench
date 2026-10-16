"""Small evidence helpers shared by the published-binary acceptance drivers."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import time


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def save(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def command(argv, log, *, cwd=None, env=None, timeout=900):
    """Retain stdout/stderr and exit status, including failed and timed-out commands."""
    log = Path(log)
    log.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    print("RUN", Path(str(argv[0])).name, log.stem, flush=True)
    receipt = {"argv": [str(arg) for arg in argv], "cwd": str(cwd) if cwd else None}
    try:
        with log.open("wb") as output:
            completed = subprocess.run([str(arg) for arg in argv], cwd=cwd, env=env,
                                       stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
        receipt["exitCode"] = completed.returncode
        require(completed.returncode == 0, f"Command failed ({completed.returncode}); see {log}")
        return log.read_text(encoding="utf-8-sig", errors="replace")
    except Exception as error:
        receipt["error"] = str(error)
        if log.exists():
            print(log.read_text(encoding="utf-8-sig", errors="replace")[-16000:], flush=True)
        raise
    finally:
        receipt["elapsedSeconds"] = time.monotonic() - started
        save(log.with_suffix(log.suffix + ".json"), receipt)
