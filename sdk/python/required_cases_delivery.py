"""Real product acceptance-contract probe; fixture scope is the existing five-case Fabric 1.21.1 M1."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import time
import traceback

from copperbench_native import NativeApiError, Workspace

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
evidence = Path(config["evidence"])
started = time.monotonic()
record = {"status": "running", "scope": "required_cases_v1_on_existing_fabric_1.21.1_m1", "tasks": [], "sessions": []}


def save(name, data):
    (evidence / (name + ".json")).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def manifest(identifier, names):
    # Expectations are the five fixed fixture methods, not collected from the observed report.
    cases = sorted([{"className": "fabric-gametest-api-v1:empty", "name": "contractgametests." + name} for name in names],
                   key=lambda case: (case["className"].encode("utf-16-be"), case["name"].encode("utf-16-be")))
    fields = ["1", identifier, "junit_tuple", str(len(cases))]
    for case in cases:
        fields.extend([case["className"], case["name"]])
    digest = hashlib.sha256()
    for field in fields:
        encoded = field.encode("utf-8")
        digest.update(struct.pack(">I", len(encoded)))
        digest.update(encoded)
    return {"schemaVersion": "1", "id": identifier, "identityKind": "junit_tuple", "minimumTests": len(cases),
            "cases": cases, "sha256": digest.hexdigest()}


def open_workspace():
    workspace = Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                               startup_timeout=180, request_timeout=120)
    record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id})
    return workspace


def terminal(workspace, label, accepted, state="succeeded"):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"Required cases {label}: {task_id}", flush=True)
    observed = workspace.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, observed)
    task = observed["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == state, observed
    assert "requiredCaseEvidence" not in task
    return task


names = ["itemstacksurvivesserialization", "recipeproducesdeclareditem", "reciperejectswrongingredient",
         "registeredstacklimit", "repairedmanualcodehasindependentexpectedvalues"]
required = manifest("m1-required-v1", names)
save("requested-manifest", required)
try:
    with open_workspace() as workspace:
        catalog = workspace.required_case_manifests()
        save("catalog", catalog)
        assert [(item["id"], len(item["cases"])) for item in catalog] == [("camp-kit-native-v1", 6), ("camp-kit-scoped-v1", 13)]
        for label, action in [
            ("unknown-version", lambda: workspace.acceptance("catalog", supported_versions=["99"])),
            ("unnegotiated-command", lambda: workspace.command("run_gametest", requiredCases=required)),
            ("changed-digest", lambda: workspace.run_required_cases({**required, "sha256": "0" * 64})),
        ]:
            try:
                action()
                raise AssertionError(label + " unexpectedly succeeded")
            except NativeApiError as error:
                save(label, {"code": error.code, "details": error.details})
        submitted = copy.deepcopy(required)
        accepted = workspace.run_required_cases(submitted)
        submitted["cases"].clear()
        passed = terminal(workspace, "required-pass", accepted)
        task_id = passed["id"]
        proof = workspace.get_required_case_evidence(task_id)
        save("before-reconnect", proof)
        frozen = proof["requiredCases"]
        assert frozen["binding"]["manifest"] == required
        assert frozen["coverage"]["missingCases"] == []
        assert frozen["coverage"]["uniqueExecuted"] == 5
        assert frozen["status"] == "passed"
    with open_workspace() as workspace:
        restored = workspace.get_required_case_evidence(task_id)
        save("after-reconnect", restored)
        assert restored["requiredCases"] == frozen
        exported = terminal(workspace, "required-export", workspace.export_required_case_artifact(task_id))
        directory = Path(exported["verifiedExport"]["exportDirectory"])
        portable = json.loads((directory / "verification.json").read_text(encoding="utf-8"))
        save("portable-receipt", portable)
        assert portable["requiredCases"]["manifest"] == required
        assert portable["requiredCases"]["coverage"]["missingCases"] == []
        assert hashlib.sha256((directory / "verified-mod.jar").read_bytes()).hexdigest() == passed["verification"]["artifactSha256"]
        # The real host still executes five distinct passing cases; one independently required identity is absent.
        missing = manifest("m1-missing-v1", names[:-1] + ["requiredbehaviornotimplemented"])
        failed = terminal(workspace, "required-missing", workspace.run_required_cases(missing), "failed")
        assert failed["verification"]["acceptanceExecuted"] == 5
        missing_proof = workspace.get_required_case_evidence(failed["id"])
        save("missing-evidence", missing_proof)
        assert missing_proof["requiredCases"]["coverage"]["reasonCode"] == "GAMETEST_REQUIRED_CASES_MISSING"
        assert len(missing_proof["requiredCases"]["coverage"]["missingCases"]) == 1
        terminal(workspace, "missing-export-rejected", workspace.export_required_case_artifact(failed["id"]), "failed")
    assert record["sessions"][0]["pid"] != record["sessions"][1]["pid"]
    assert record["sessions"][0]["workspaceId"] == record["sessions"][1]["workspaceId"]
    checked = subprocess.run(["node", "ui-core/scripts/validate-required-cases-evidence.mjs", str(evidence)],
                             cwd=config["cwd"], capture_output=True, text=True, encoding="utf-8")
    (evidence / "wire-schema-validation.log").write_text(checked.stdout + checked.stderr, encoding="utf-8")
    assert checked.returncode == 0, "Actual wire responses failed schema validation: " + checked.stderr
    save("wire-schema-validation", json.loads(checked.stdout))
    record["status"] = "passed"
except BaseException as error:
    record["status"] = "failed"
    record["error"] = str(error)
    record["traceback"] = traceback.format_exc()
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    print(f"Required-case Native probe {record['status']}: {evidence}", flush=True)
