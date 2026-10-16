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


def workspace_document(root):
    files = [path for path in Path(root).glob("*.mcreator") if path.is_file()]
    require(len(files) == 1, f"Expected one workspace document, found: {[p.name for p in files]}")
    return files[0]


def observed_gradle(environment, expected_home):
    gradle = environment.get("execution", {}).get("gradle")
    if gradle is None and environment.get("generator", {}).get("id") in {
            "datapack-1.20.1", "datapack-1.21.1", "datapack-26.1.x"}:
        # Released data-pack adapters expose host identity but no Gradle environment projection.
        return None
    require(isinstance(gradle, dict), "Mod adapter did not report its Gradle execution environment")
    require(Path(gradle["userHome"]).resolve() == Path(expected_home).resolve()
            and gradle["userHomeSource"] == "COPPERBENCH_GRADLE_USER_HOME"
            and gradle["reuseExternalDistributions"] is False, "Gradle cache isolation is not effective")
    return gradle


def command(argv, log, *, cwd=None, env=None, timeout=900):
    """Retain stdout/stderr and exit status, including failed and timed-out commands."""
    log = Path(log)
    log.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    print("RUN", Path(str(argv[0])).name, log.stem, flush=True)
    errors = log.with_suffix(".stderr.log")
    receipt = {"argv": [str(arg) for arg in argv], "cwd": str(cwd) if cwd else None, "stderrFile": str(errors)}
    try:
        with log.open("wb") as output, errors.open("wb") as stderr:
            completed = subprocess.run([str(arg) for arg in argv], cwd=cwd, env=env,
                                       stdout=output, stderr=stderr, timeout=timeout)
        receipt["exitCode"] = completed.returncode
        require(completed.returncode == 0, f"Command failed ({completed.returncode}); see {log}")
        return log.read_text(encoding="utf-8-sig", errors="replace")
    except Exception as error:
        receipt["error"] = str(error)
        for path in [log, errors]:
            if path.exists():
                print(path.read_text(encoding="utf-8-sig", errors="replace")[-16000:], flush=True)
        raise
    finally:
        receipt["elapsedSeconds"] = time.monotonic() - started
        save(log.with_suffix(log.suffix + ".json"), receipt)
