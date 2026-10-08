#!/usr/bin/env python3
"""Exercise the unmodified Copperbench Native SDK against disposable mod copies.

This measures a source runtime, not installed-product UI or player interactions.
Every SDK response and failure remains in the evidence. A failed mixed-authoring
copy is abandoned; native-only fallback always starts from the original example.
No ownership records, approvals, EULA files or production implementation change.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import traceback
import zipfile

BASE_COMMIT = "b1a69d244ab7337608cc302826ed55d4a707f37f"
EXCLUDED = {".git", ".gradle", ".copperbench", ".mcreator", "__pycache__",
            "build", "out", "run", "runs", "logs", "output", "node_modules"}
BROKEN_SOURCE = ("package dev.chronometer.evaluation;\n"
                 "public final class BrokenProbe { this is deliberately invalid Java }\n")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2, default=str) + "\n",
                         encoding="utf-8")
    temporary.replace(path)


def source_files(root: Path):
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if any(part in EXCLUDED for part in relative.parts) or path.is_symlink():
            continue
        if path.is_file():
            yield path, relative


def source_archive(root: Path, destination: Path) -> dict:
    destination.parent.mkdir(parents=True, exist_ok=True)
    manifest = []
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for path, relative in source_files(root):
            if path.stat().st_size > 5 * 1024 * 1024:
                raise ValueError(f"Unexpectedly large source file: {relative}")
            archive.write(path, str(relative))
            manifest.append({"path": relative.as_posix(), "sha256": digest(path),
                             "bytes": path.stat().st_size})
    return {"sourceDirectory": str(root), "archive": str(destination),
            "sha256": digest(destination), "files": manifest}


def protected_sources(root: Path) -> dict:
    return {relative.as_posix(): digest(path) for path, relative in source_files(root)
            if relative.parts[0] == "src" or relative.as_posix() in {
                "build.gradle", "settings.gradle", "gradle.properties", "copperbench-tests.json"}}


def collect(args, repo: Path) -> None:
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=True)
    report_path = evidence / "trial.json"
    report = json.loads(report_path.read_text()) if report_path.exists() else {
        "schemaVersion": "1.0", "status": "bootstrap_not_completed",
        "assessedProductBase": BASE_COMMIT, "checks": [], "calls": []}
    source = Path(report.get("deliveryWorkspace", str(args.source.resolve())))
    if source.is_dir():
        try:
            manifest = source_archive(source, evidence / "copper-chronometer-source.zip")
            write_json(evidence / "source-manifest.json", manifest)
        except Exception as error:
            report.setdefault("collectionErrors", []).append(str(error))
    logs = sorted((repo / "logs").glob("*.log"), key=lambda p: p.stat().st_mtime, reverse=True)[:8]
    for index, path in enumerate(logs):
        target = evidence / "product-logs" / f"{index:02d}-{path.name}"
        target.parent.mkdir(parents=True, exist_ok=True)
        with path.open("rb") as stream:
            size = path.stat().st_size
            stream.seek(max(0, size - 256 * 1024))
            target.write_bytes(stream.read())
    write_json(report_path, report)


class Trial:
    def __init__(self, args, repo, workspace_type, api_error_type):
        self.args, self.repo = args, repo
        self.Workspace, self.ApiError = workspace_type, api_error_type
        self.started = time.monotonic()
        self.deadline = self.started + args.budget_seconds
        self.evidence = args.evidence.resolve()
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.client = None
        self.last_error = None
        self.call_count = 0
        self.report = {
            "schemaVersion": "1.0", "status": "running",
            "assessedProductBase": BASE_COMMIT,
            "evaluatedCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
            "runUrl": ("https://github.com/" + os.environ.get("GITHUB_REPOSITORY", "Lotulune/Copperbench")
                       + "/actions/runs/" + os.environ.get("GITHUB_RUN_ID", "local")),
            "cachePolicy": "Existing Gradle caches may be restored; not cold-cache and not a speed comparison.",
            "scope": "Real source Native SDK, disposable workspace writes and packaged server-side GameTests.",
            "unverified": ["Installed desktop", "Client input and rendering", "Audio/visual quality", "External-user usability"],
            "authorization": "No task authorization or EULA acceptance is created by this harness.",
            "workspaces": {}, "calls": [], "checks": [], "taskPolls": 0,
        }
        self.save()

    def save(self):
        self.report["elapsedSeconds"] = round(time.monotonic() - self.started, 3)
        write_json(self.evidence / "trial.json", self.report)

    def check(self, phase, name, passed, details=None, status=None):
        result = {"phase": phase, "name": name,
                  "status": status or ("passed" if passed else "failed"), "details": details}
        self.report["checks"].append(result)
        print(json.dumps({"phase": phase, "check": name, "status": result["status"]}), flush=True)
        self.save()
        return passed

    def call(self, phase, name, operation, payload, fn):
        self.call_count += 1
        started = time.monotonic()
        self.last_error = None
        record = {"sequence": self.call_count, "phase": phase, "name": name,
                  "operation": operation, "payload": payload}
        try:
            if started >= self.deadline - 10:
                raise TimeoutError("Evaluation time budget exhausted before starting this operation")
            value = fn()
            record.update(outcome="returned", response=value)
        except Exception as error:
            self.last_error = {"type": type(error).__name__,
                               "code": getattr(error, "code", None), "message": str(error),
                               "details": getattr(error, "details", None)}
            record.update(outcome="exception", error=self.last_error)
            value = None
        record["elapsedSeconds"] = round(time.monotonic() - started, 3)
        path = self.evidence / "calls" / f"{self.call_count:03d}-{phase}-{name}.json"
        write_json(path, record)
        self.report["calls"].append({key: record[key] for key in (
            "sequence", "phase", "name", "operation", "outcome", "elapsedSeconds")})
        self.report["calls"][-1]["evidence"] = str(path.relative_to(self.evidence))
        print(json.dumps({"phase": phase, "operation": operation,
                          "outcome": record["outcome"], "seconds": record["elapsedSeconds"]}), flush=True)
        self.save()
        return value

    def open(self, phase, root):
        self.close()
        self.report["workspaces"][phase] = str(root)
        cp = self.args.classpath_file.resolve().read_text(encoding="utf-8").strip()
        launcher = [self.args.java, "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--enable-native-access=ALL-UNNAMED,jcef", "-cp", cp, "net.mcreator.Launcher"]
        def launch():
            self.client = self.Workspace.open(
                root / "copper_chronometer.mcreator", launcher=launcher, cwd=self.repo,
                startup_timeout=min(120, max(1, self.deadline - time.monotonic() - 10)),
                request_timeout=60)
            return {"workspaceId": self.client.workspace_id, "revision": self.client.revision,
                    "operations": self.client.operations, "launcher": launcher, "cwd": str(self.repo)}
        opened = self.call(phase, "open", "Workspace.open", {"workspace": str(root)}, launch)
        return self.check(phase, "native_session_opened", opened is not None)

    def close(self):
        if self.client is not None:
            self.client.close()
            self.client = None

    def environment(self, phase):
        self.call(phase, "environment", "get_workspace_environment", {},
                  lambda: self.client.query("get_workspace_environment"))
        for element_type in ("item", "recipe", "block", "code", "function"):
            value = self.call(phase, "field-" + element_type, "Workspace.field_contract",
                              {"elementType": element_type},
                              lambda kind=element_type: self.client.field_contract(kind))
            self.check(phase, "field_contract_" + element_type, value is not None)

    def task(self, phase, name, fn, seconds, expected_state="succeeded"):
        operation = {"build_with_injected_error": "build_workspace", "build_after_repair": "build_workspace",
                     "run_game_tests": "run_gametest", "export_verified_artifact": "export_workspace"}.get(name, name)
        payload = {"scope": "workspace"} if operation != "export_workspace" else {
            "verifiedTaskId": self.report.get("acceptanceTaskId"), "allowHistorical": False}
        accepted = self.call(phase, name + "-start", operation,
                             {"expectedRevision": self.client.revision, "payload": payload}, fn)
        if not accepted or accepted.get("status") != "accepted" or not accepted.get("task", {}).get("id"):
            self.check(phase, name, False, {"reason": "Task was not accepted", "error": self.last_error})
            return None
        task_id = accepted["task"]["id"]
        deadline = min(self.deadline - 15, time.monotonic() + seconds)
        after = 0
        all_logs = []
        last = None
        update_path = self.evidence / "tasks" / f"{phase}-{name}-{task_id}.jsonl"
        update_path.parent.mkdir(parents=True, exist_ok=True)
        started = time.monotonic()
        try:
            with update_path.open("w", encoding="utf-8") as updates:
                while time.monotonic() < deadline:
                    result = self.client.get_task(task_id, after)
                    self.report["taskPolls"] += 1
                    data = result["data"]
                    entries = data.get("logs", [])
                    for entry in entries:
                        after = max(after, entry["sequence"])
                        all_logs.append(entry)
                    state = data["task"].get("state")
                    signature = json.dumps(data["task"], sort_keys=True)
                    if entries or signature != last:
                        updates.write(json.dumps(result, ensure_ascii=False) + "\n")
                        updates.flush()
                        last = signature
                    if state not in ("queued", "running"):
                        data["logs"] = all_logs
                        write_json(update_path.with_suffix(".json"), result)
                        self.check(phase, name, state == expected_state,
                                   {"taskId": task_id, "state": state, "expectedState": expected_state,
                                    "elapsedSeconds": round(time.monotonic() - started, 3),
                                    "evidence": str(update_path.with_suffix(".json").relative_to(self.evidence))})
                        return result
                    time.sleep(min(1, max(0.01, deadline - time.monotonic())))
            self.call(phase, name + "-cancel", "cancel_task", {"taskId": task_id},
                      lambda: self.client.cancel_task(task_id))
            self.check(phase, name, False, {"reason": "Operation budget exhausted", "taskId": task_id},
                       status="unverified")
        except Exception as error:
            self.check(phase, name, False, {"taskId": task_id, "code": getattr(error, "code", None),
                                          "message": str(error), "details": getattr(error, "details", None)})
        return None

    def intact(self, phase, root, expected, label):
        changed = [{"path": relative, "expectedSha256": before,
                    "actualSha256": digest(root / relative) if (root / relative).is_file() else None}
                   for relative, before in expected.items()
                   if not (root / relative).is_file() or digest(root / relative) != before]
        return self.check(phase, label, not changed, changed)

    def mixed_probe(self, root):
        phase = "mixed"
        expected = protected_sources(root)
        if not self.open(phase, root):
            return False
        self.environment(phase)
        before = self.client.revision
        invalid = self.call(phase, "invalid-function", "create_mod_element",
                            {"elementType": "function", "name": "chrono_invalid_probe",
                             "initialValues": {"commands": [1]}},
                            lambda: self.client.create_mod_element(
                                elementType="function", name="chrono_invalid_probe",
                                initialValues={"commands": [1]}))
        error = self.last_error
        observed = self.call(phase, "after-invalid", "get_workbench", {}, self.client.get_workspace)
        self.check(phase, "invalid_function_type_rejected_without_revision_change",
                   invalid is None and bool(error) and error.get("code") == "FIELD_TYPE_INVALID"
                   and observed is not None and self.client.revision == before,
                   {"revisionBefore": before, "revisionAfter": self.client.revision, "error": error})
        created = self.call(phase, "create-function", "create_mod_element",
                            {"elementType": "function", "name": "chrono_ready",
                             "initialValues": {"commands": ["say Copper Chronometer ready"]}},
                            lambda: self.client.create_mod_element(
                                elementType="function", name="chrono_ready",
                                initialValues={"commands": ["say Copper Chronometer ready"]}))
        if not created:
            self.check(phase, "structured_function_created", False, self.last_error)
            return False
        self.check(phase, "structured_function_created", created.get("status") == "committed")
        element_id = created.get("data", {}).get("element", {}).get("id")
        current_revision = self.client.revision
        stale_payload = {"elementId": element_id,
                         "changes": [{"path": "/commands", "value": ["say stale overwrite"]}]}
        stale = self.call(phase, "stale-write", "update_mod_element",
                          {"expectedRevision": before, **stale_payload},
                          lambda: self.client.command("update_mod_element",
                                                       expected_revision=before, **stale_payload))
        stale_error = self.last_error
        self.call(phase, "after-stale", "get_workbench", {}, self.client.get_workspace)
        self.check(phase, "stale_revision_rejected",
                   stale is None and bool(stale_error) and stale_error.get("code") == "REVISION_CONFLICT"
                   and self.client.revision == current_revision,
                   {"staleRevision": before, "currentRevision": self.client.revision, "error": stale_error})
        generate = self.task(phase, "generate_workspace", self.client.generate, 420)
        intact = self.intact(phase, root, expected, "manual_sources_after_generation")
        build = None
        if generate and generate["data"]["task"]["state"] == "succeeded" and intact:
            build = self.task(phase, "build_workspace", self.client.build, 480)
        else:
            self.check(phase, "mixed_build", False,
                       "Generation was not successful; this copy will not be used for delivery.",
                       status="unverified")
        usable = bool(build and build["data"]["task"]["state"] == "succeeded")
        usable = self.intact(phase, root, expected, "manual_sources_after_mixed_path") and usable
        self.close()
        if self.open("mixed-reopen", root):
            elements = self.call("mixed-reopen", "elements", "list_mod_elements", {},
                                 lambda: list(self.client.list_mod_elements()))
            element = next((item for item in elements or [] if item.get("name") == "chrono_ready"), None)
            editor = self.call("mixed-reopen", "function-editor", "get_mod_element_editor",
                               {"elementId": element["id"]},
                               lambda: self.client.query("get_mod_element_editor", elementId=element["id"])) if element else None
            body_present = bool(editor and "say Copper Chronometer ready" in json.dumps(editor))
            self.check("mixed-reopen", "function_persisted", body_present,
                       {"present": element is not None, "bodyPreserved": body_present})
        self.close()
        return usable

    def delivery_trial(self, root):
        phase = "delivery"
        expected = protected_sources(root)
        self.report["deliveryWorkspace"] = str(root)
        if not self.open(phase, root):
            return False
        self.environment(phase)
        # build() itself runs the real generator boundary. Native fallback does
        # not add a structured element or forge generator ownership metadata.
        baseline = self.task(phase, "build_workspace", self.client.build, 480)
        if not baseline or baseline["data"]["task"]["state"] != "succeeded":
            self.check(phase, "acceptance", False, "Baseline build failed; GameTest has no valid prerequisite.",
                       status="unverified")
            return False
        if not self.intact(phase, root, expected, "manual_sources_after_baseline"):
            return False
        probe = root / "src/main/java/dev/chronometer/evaluation/BrokenProbe.java"
        if probe.exists():
            raise RuntimeError("Refusing to overwrite an existing BrokenProbe.java")
        probe.parent.mkdir(parents=True, exist_ok=True)
        probe.write_text(BROKEN_SOURCE, encoding="utf-8")
        self.check(phase, "fault_injected", True,
                   {"path": str(probe.relative_to(root)), "sha256": digest(probe),
                    "source": BROKEN_SOURCE, "actor": "evaluation harness, direct source edit"})
        broken = None
        try:
            broken = self.task(phase, "build_with_injected_error", self.client.build, 180,
                               expected_state="failed")
            diagnostics = (broken or {}).get("data", {}).get("diagnostics", [])
            diagnostic_text = json.dumps(diagnostics, ensure_ascii=False)
            self.check(phase, "compile_diagnostic_locates_fault",
                       bool(broken and "BrokenProbe.java" in diagnostic_text),
                       {"diagnostics": diagnostics})
        finally:
            if probe.exists() and probe.read_text(encoding="utf-8") == BROKEN_SOURCE:
                probe.unlink()
                self.check(phase, "fault_removed", True, {"path": str(probe.relative_to(root))})
            elif probe.exists():
                self.check(phase, "fault_removed", False, "Probe bytes changed unexpectedly; preserved for review.")
        repaired = self.task(phase, "build_after_repair", self.client.build, 240)
        if not repaired or repaired["data"]["task"]["state"] != "succeeded":
            return False
        if not self.intact(phase, root, expected, "manual_sources_after_repair"):
            return False
        configuration = json.loads((root / "copperbench-tests.json").read_text(encoding="utf-8"))
        self.check(phase, "packaged_test_configuration",
                   configuration.get("mode") == "packaged_jar" and configuration.get("minimumTests", 0) > 0,
                   configuration)
        if configuration.get("mode") != "packaged_jar" or configuration.get("minimumTests", 0) < 1:
            return False
        acceptance = self.task(phase, "run_game_tests", self.client.run_game_tests, 660)
        task = (acceptance or {}).get("data", {}).get("task", {})
        verification = task.get("verification", {})
        verified = (task.get("state") == "succeeded" and verification.get("status") == "passed"
                    and verification.get("mode") == "packaged_jar"
                    and verification.get("acceptanceExecuted", 0) >= configuration["minimumTests"]
                    and verification.get("sourceCurrentAtCompletion") is True)
        self.check(phase, "packaged_behavior_verified", verified, verification)
        if not verified:
            return False
        acceptance_id = task["id"]
        self.report["acceptanceTaskId"] = acceptance_id
        self.report["verification"] = verification
        for key, name, hash_key in (("artifactPath", "accepted-mod.jar", "artifactSha256"),
                                    ("reportPath", "gametest-results.xml", "reportSha256")):
            source = Path(verification[key])
            if not source.is_file() or source.stat().st_size > 10 * 1024 * 1024 or digest(source) != verification[hash_key]:
                raise RuntimeError(f"Acceptance evidence is missing, too large or changed: {key}")
            destination = self.evidence / "artifacts" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
        exported = self.task(phase, "export_verified_artifact",
                             lambda: self.client.export_verified_artifact(acceptance_id), 90)
        export_receipt = (exported or {}).get("data", {}).get("task", {}).get("verifiedExport", {})
        export_ok = (bool(exported) and exported["data"]["task"]["state"] == "succeeded"
                     and export_receipt.get("artifactSha256") == verification.get("artifactSha256")
                     and export_receipt.get("status") == "passed_current_input")
        self.check(phase, "verified_export_matches_acceptance", export_ok, export_receipt)
        if export_ok:
            directory = Path(export_receipt["exportDirectory"])
            for name in ("verified-mod.jar", "gametest-results.xml", "verification.json"):
                path = directory / name
                if not path.is_file() or path.stat().st_size > 10 * 1024 * 1024:
                    raise RuntimeError(f"Invalid verified-export payload: {name}")
                shutil.copy2(path, self.evidence / "artifacts" / name)
            self.report["verifiedExport"] = export_receipt
        self.close()
        reopen_ok = self.open("delivery-reopen", root)
        if reopen_ok:
            self.call("delivery-reopen", "environment", "get_workspace_environment", {},
                      lambda: self.client.query("get_workspace_environment"))
            remembered = self.call("delivery-reopen", "acceptance-task", "get_task",
                                   {"taskId": acceptance_id},
                                   lambda: self.client.get_task(acceptance_id))
            remembered_task = (remembered or {}).get("data", {}).get("task", {})
            reopen_ok = (remembered_task.get("state") == "succeeded"
                         and remembered_task.get("verification", {}).get("artifactSha256")
                         == verification.get("artifactSha256"))
            self.check("delivery-reopen", "acceptance_persisted", reopen_ok)
            reopen_ok = self.intact("delivery-reopen", root, expected, "manual_sources_preserved") and reopen_ok
        return verified and export_ok and reopen_ok

    def run(self):
        root = self.args.workspace_root.resolve()
        root.mkdir(parents=True, exist_ok=True)
        mixed_root = root / "mixed-structured"
        native_root = root / "native-authoring"
        if mixed_root.exists() or native_root.exists():
            raise RuntimeError("Disposable trial destinations already exist; refusing to overwrite them")
        shutil.copytree(self.args.source.resolve(), mixed_root)
        mixed_ok = False
        try:
            mixed_ok = self.mixed_probe(mixed_root)
        except Exception as error:
            self.check("mixed", "unexpected_exception", False,
                       {"message": str(error), "traceback": traceback.format_exc()})
        finally:
            self.close()
        self.report["mixedWorkflowSucceeded"] = mixed_ok
        if mixed_ok:
            selected = mixed_root
        else:
            shutil.copytree(self.args.source.resolve(), native_root)
            selected = native_root
            self.report["fallback"] = {
                "reason": "Mixed authoring did not finish safely; all failed-copy mutations remain isolated.",
                "source": str(self.args.source.resolve()), "destination": str(native_root),
                "ownershipMetadataModifiedByHarness": False}
        success = False
        try:
            success = self.delivery_trial(selected)
        except Exception as error:
            self.check("delivery", "unexpected_exception", False,
                       {"message": str(error), "traceback": traceback.format_exc()})
        finally:
            self.close()
            self.report["status"] = ("completed" if mixed_ok else "completed_with_mixed_workflow_friction") if success else "incomplete"
            self.save()
            collect(self.args, self.repo)
        return 0 if success else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--workspace-root", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--classpath-file", type=Path)
    parser.add_argument("--java")
    parser.add_argument("--budget-seconds", type=int, default=1320)
    parser.add_argument("--collect-only", action="store_true")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    if args.collect_only:
        collect(args, repo)
        return 0
    if not args.java or not args.classpath_file:
        parser.error("--java and --classpath-file are required for a trial")
    sys.path.insert(0, str(repo / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    trial = Trial(args, repo, Workspace, NativeApiError)
    try:
        return trial.run()
    except Exception as error:
        trial.check("harness", "unexpected_exception", False,
                    {"message": str(error), "traceback": traceback.format_exc()})
        trial.report["status"] = "incomplete"
        trial.close()
        trial.save()
        collect(args, repo)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
