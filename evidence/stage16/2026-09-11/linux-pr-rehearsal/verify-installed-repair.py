#!/usr/bin/env python3
"""Replay public installed-product CLI recovery against a disposable source copy.

This is a maintainer evidence collector, not a fresh-agent or desktop acceptance test.
It never creates an authorization and never opens or manipulates a desktop window.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import signal
import subprocess
import threading
import time


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def invoke(product, arguments, label, evidence, steps, timeout=1200):
    command = [str(product), *map(str, arguments)]
    environment = dict(os.environ)
    removed = [name for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "JAVA_OPTS", "GRADLE_OPTS", "COPPERBENCH_STAGE5_GRADLE_EXECUTABLE") if environment.pop(name, None) is not None]
    started = time.monotonic()
    record = {"label": label, "command": command, "startedAt": now(), "callerRuntimeOptionsRemoved": removed}
    steps.append(record)
    events = queue.Queue()
    messages = []
    with (evidence / (label + ".stderr.log")).open("w", encoding="utf-8") as errors, (evidence / (label + ".jsonl")).open("w", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=evidence, env=environment, stdout=subprocess.PIPE, stderr=errors, text=True, encoding="utf-8", errors="strict", start_new_session=os.name == "posix")
        def collect():
            try:
                for line in process.stdout:
                    events.put(line)
            except Exception as error:
                events.put(error)
            finally:
                events.put(None)
        threading.Thread(target=collect, daemon=True).start()
        try:
            while True:
                require(time.monotonic() - started < timeout, "Public product command timed out: " + label)
                try:
                    line = events.get(timeout=0.2)
                except queue.Empty:
                    continue
                if line is None:
                    break
                if isinstance(line, Exception):
                    raise line
                record.setdefault("firstOutputMs", round((time.monotonic() - started) * 1000))
                output.write(line)
                output.flush()
                messages.append(json.loads(line))
            record["processExitCode"] = process.wait(timeout=15)
        finally:
            if process.poll() is None:
                if os.name == "posix":
                    os.killpg(process.pid, signal.SIGTERM)
                else:
                    process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    if os.name == "posix":
                        os.killpg(process.pid, signal.SIGKILL)
                    else:
                        process.kill()
            record.update(completedAt=now(), elapsedMs=round((time.monotonic() - started) * 1000))
            write(evidence / (label + ".command.json"), record)
    require(messages, "Product returned no JSON: " + label)
    record["result"] = messages[-1]
    print(json.dumps({"step": label, "exitCode": record["processExitCode"], "elapsedMs": record["elapsedMs"]}), flush=True)
    return messages[-1], record["processExitCode"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product", required=True)
    parser.add_argument("--source-workspace", required=True)
    parser.add_argument("--trial-root", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--application-jar", required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    require(re.fullmatch(r"[0-9a-f]{64}", args.candidate_sha256), "Expected full lowercase candidate SHA-256")
    require(re.fullmatch(r"[0-9a-f]{40}", args.source_commit), "Expected full lowercase source commit")
    product = Path(args.product).resolve(strict=True)
    source_file = Path(args.source_workspace).resolve(strict=True)
    source = source_file.parent
    trial = Path(args.trial_root).absolute()
    evidence = Path(args.evidence).absolute()
    require(not trial.exists() and not evidence.exists(), "Both output directories must be new")
    require(not trial.is_relative_to(source) and not evidence.is_relative_to(source), "Outputs must not be inside the source project")
    source_hashes = {}
    for path in source.rglob("*"):
        require(not path.is_symlink(), "Source contains a symbolic link")
        if path.is_file():
            source_hashes[path.relative_to(source).as_posix()] = digest(path)
    require(source_hashes, "Source inventory is empty")
    evidence.mkdir(parents=True)
    shutil.copytree(source, trial)
    steps = []
    summary = {"schemaVersion": "1.0", "kind": "stage16-installed-cli-repair", "status": "running", "startedAt": now(), "candidateSha256": args.candidate_sha256, "candidateSourceCommit": args.source_commit, "productExecutable": str(product), "applicationJarSha256": digest(args.application_jar), "freshAgent": False, "authorizedCreationExercised": False, "desktopAcceptanceExercised": False, "clientBehaviorVerified": False, "cacheState": "Existing guest Gradle and Minecraft caches; new workspace directory, not cold-cache certification", "humanInterventionsDuringReplay": 0, "steps": steps}
    write(evidence / "input-source-hashes.json", source_hashes)
    try:
        result, code = invoke(product, ["bootstrap", "list-generators"], "01-discovery", evidence, steps)
        require(code == 0, "Generator discovery failed")
        unapproved = trial / "unapproved-child"
        result, code = invoke(product, ["bootstrap", "create-workspace", "--generator-id", "fabric-1.21.1", "--mod-name", "Stage16 Approval Probe", "--mod-id", "stage16_approval_probe", "--workspace-folder", unapproved, "--no-prompt", "true"], "02-no-authorization", evidence, steps)
        require(code != 0 and result.get("code") == "USER_APPROVAL_REQUIRED" and not unapproved.exists(), "No-prompt creation did not reject without creating a workspace")
        base = ["headless", "--workspace", trial / source_file.name]
        result, code = invoke(product, [*base, "environment"], "03-environment", evidence, steps)
        require(code == 0, "Installed environment discovery failed")
        summary["environment"] = result.get("data")
        probe = trial / "src/main/java/copperbench/trial/CompileFailureProbe.java"
        require(not probe.exists(), "Reserved repair probe already exists")
        probe.parent.mkdir(parents=True, exist_ok=True)
        probe.write_text("package copperbench.trial; public final class CompileFailureProbe { int value = STAGE16_EXPECTED_COMPILE_FAILURE; }\n", encoding="utf-8")
        result, code = invoke(product, [*base, "build", "--stream", "true"], "04-compile-failure", evidence, steps)
        require(code != 0 and any(item.get("code") == "JAVA_COMPILE_ERROR" and "CompileFailureProbe.java" in str(item.get("path") or "") for item in result.get("diagnostics", [])), "Compile failure did not identify the actual source file")
        probe.write_text("package copperbench.trial; public final class CompileFailureProbe { int value = 16; }\n", encoding="utf-8")
        result, code = invoke(product, [*base, "build", "--stream", "true"], "05-repaired-build", evidence, steps)
        require(code == 0, "Repaired installed-product build failed")
        config = json.loads((trial / "copperbench-tests.json").read_text(encoding="utf-8-sig"))
        require(config["mode"] == "packaged_jar" and config["minimumTests"] >= 1, "Fixture requires packaged-JAR behavioral acceptance")
        result, code = invoke(product, [*base, "run-gametest", "--stream", "true"], "06-accepted", evidence, steps)
        verification = result.get("task", {}).get("verification", {})
        require(code == 0 and verification.get("status") == "passed" and verification.get("sourceCurrentAtCompletion") is True and verification.get("acceptanceExecuted", 0) >= config["minimumTests"], "Installed acceptance did not verify current source and sufficient behavior tests")
        for role, target in (("artifact", "tested-mod.jar"), ("report", "gametest-results.xml")):
            require(digest(verification[role + "Path"]) == verification[role + "Sha256"], "Verified " + role + " changed before archival")
            shutil.copyfile(verification[role + "Path"], evidence / target)
            require(digest(evidence / target) == verification[role + "Sha256"], "Archived " + role + " digest mismatch")
        write(evidence / "verification.json", verification)
        summary["verification"] = verification
        require(all(digest(source / name) == sha for name, sha in source_hashes.items()), "Original source bytes changed")
        summary.update(status="passed", sourceUnchanged=True)
    except Exception as error:
        summary.update(status="failed", error=str(error), errorType=type(error).__name__)
        raise
    finally:
        summary["completedAt"] = now()
        write(evidence / "summary.json", summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
