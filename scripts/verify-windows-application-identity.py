#!/usr/bin/env python3
"""Check the real Windows launcher's JAR identity through the read-only doctor.

Uses a minimal local metadata fixture, without opening a workspace session,
downloading dependencies, granting permissions or starting a graphical window.
This packaging regression is separate from installed-product acceptance.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import traceback


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if os.name != "nt":
        parser.error("This check requires the actual Windows executable")
    product = args.product_root.resolve(strict=True)
    output = args.output.resolve()
    if output == product or product in output.parents or output in product.parents:
        parser.error("Evidence and product directories must be separate")
    output.mkdir(parents=True, exist_ok=False)
    started = time.monotonic()
    record = {"status": "running", "kind": "windows-launcher-application-identity",
              "startedAt": datetime.now(timezone.utc).isoformat(), "productRoot": str(product)}
    try:
        launcher = product / "copperbench.exe"
        application = product / "lib/copperbench.jar"
        before = {"launcherSha256": digest(launcher), "applicationSha256": digest(application)}
        record.update(before)
        workspace = output / "workspace with spaces"
        workspace.mkdir()
        metadata = workspace / "identity.mcreator"
        metadata.write_text('{"workspaceSettings":{"currentGenerator":"fabric-1.21.1"}}\n', encoding="utf-8")
        original = metadata.read_bytes()
        profile = output / "unused-profile"
        environment = os.environ.copy()
        environment["COPPERBENCH_HOME"] = str(profile)
        command = [str(launcher), "headless", "--workspace", str(metadata), "doctor"]
        record["command"] = command
        with (output / "doctor.stdout").open("xb") as stdout, (output / "doctor.stderr").open("xb") as stderr:
            process = subprocess.run(command, cwd=product, env=environment, stdout=stdout, stderr=stderr,
                                     timeout=90, creationflags=subprocess.CREATE_NO_WINDOW)
        record["exitCode"] = process.returncode
        if process.returncode != 0:
            raise ValueError("Packaged doctor failed; inspect its retained stdout/stderr")
        envelope = json.loads((output / "doctor.stdout").read_text(encoding="utf-8-sig"))
        if envelope.get("status") != "succeeded" or envelope.get("operation") != "get_workspace_doctor":
            raise ValueError("The launcher did not return a successful doctor response")
        facts = envelope["data"]
        record["observedApplication"] = facts["application"]
        if facts.get("readOnly") is not True or facts.get("networkProbed") is not False:
            raise ValueError("Doctor no longer reports a read-only local observation")
        if facts["application"].get("sourceState") != "packaged_binary":
            raise ValueError("The launcher did not load a packaged application")
        if facts["application"].get("applicationSha256") != before["applicationSha256"]:
            raise ValueError("The running application hash differs from lib/copperbench.jar")
        if metadata.read_bytes() != original or set(workspace.iterdir()) != {metadata} or profile.exists():
            raise ValueError("Doctor changed the fixture or initialized the product profile")
        if digest(launcher) != before["launcherSha256"] or digest(application) != before["applicationSha256"]:
            raise ValueError("Product bytes changed during the check")
        record["workspaceUnchanged"] = True
        record["profileCreated"] = False
        record["status"] = "passed"
    except Exception as error:
        record.update(status="failed", errorType=type(error).__name__, error=str(error))
        (output / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    finally:
        record["completedAt"] = datetime.now(timezone.utc).isoformat()
        record["elapsedSeconds"] = time.monotonic() - started
        (output / "result.json").write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(record))
    return 0 if record["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
