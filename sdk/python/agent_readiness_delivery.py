"""M1 product delivery gate; execute only via runAgentReadinessDelivery.

The Agent's default route: hand-written Java, JSON and assets saved through
Core, a hook in the generated main class's user code block, real Gradle, and an
independent packaged-JAR GameTest host. Structured elements are not used.
Negative recovered-record injections are explicitly labelled. No authority is
issued and no Minecraft EULA is accepted.
"""
from __future__ import annotations

import copy
import hashlib
import json
import platform
from pathlib import Path
import shutil
import subprocess
import sys
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
import zipfile
from copperbench_native import NativeApiError, Workspace

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
root = Path(config["workspace"]).parent
evidence = Path(config["evidence"])
fixture = Path(config["fixture"])
record = {"schemaVersion": "1.0", "status": "running", "route": "native_code",
          "layers": ["real_headless", "packaged_server"],
          "fixtureHashes": config["fixtureHashes"], "generatorVersion": config["generatorVersion"],
          "os": config["os"], "javaVersion": config["javaVersion"], "pythonVersion": platform.python_version(),
          "cachePolicy": config["cachePolicy"], "tasks": [], "negativeGates": [], "sessions": [], "writes": []}
started = time.monotonic()

CONTENT = "src/main/java/m1delivery/content/DeliveryContent.java"
RECIPE = "src/main/resources/data/m1_delivery/recipe/discovery_recipe.json"
MODEL = "src/main/resources/assets/m1_delivery/models/item/discovery_item.json"
TEXTURE = "src/main/resources/assets/m1_delivery/textures/item/discovery_item.png"
TESTS = "src/gametest/java/copperbench/acceptance/ContractGameTests.java"
TEST_CONFIG = "copperbench-tests.json"
HOOK = "m1delivery.content.DeliveryContent.register();"
HOOK_BLOCK = "mod init"
CORRECT_BODY = "return (count + 15) / 16;"


def save(name, value):
    path = evidence / name
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_identity():
    cwd = config["cwd"]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=cwd, text=True).strip()
    delta = subprocess.check_output(["git", "diff", "--binary", "HEAD"], cwd=cwd)
    (evidence / "source.patch").write_bytes(delta)
    names = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=cwd, text=True).splitlines()
    hashes = {name: sha(Path(cwd) / name) for name in sorted(names) if (Path(cwd) / name).is_file()}
    return {"head": head, "trackedDeltaSha256": hashlib.sha256(delta).hexdigest(), "untrackedFiles": hashes}


def open_workspace():
    workspace = Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                               startup_timeout=180, request_timeout=120)
    record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id,
                               "openedRevision": workspace.revision})
    return workspace


def inventory():
    return {p.relative_to(root).as_posix(): ("active-lock:" + str(p.stat().st_size)
            if p.name == "workspace.write.lock" else sha(p))
            for p in sorted(root.rglob("*")) if p.is_file()}


def discovery_baseline():
    # JGit's asynchronous filesystem calibration can outlive the Native ready
    # message. Let this observed startup work finish before measuring queries;
    # keep every file in the before/after discovery comparison.
    started = time.monotonic()
    observed_probes = set()
    while True:
        probes = {path.relative_to(root).as_posix()
                  for path in (root / ".mcreator/localHistory").glob(".probe-*")}
        observed_probes.update(probes)
        elapsed = time.monotonic() - started
        receipt = {"status": "waiting" if probes else "ready", "elapsedSeconds": round(elapsed, 3),
                   "observedProbes": sorted(observed_probes), "remainingProbes": sorted(probes)}
        if not probes:
            save("discovery-startup.json", receipt)
            return inventory()
        if elapsed >= 30:
            receipt["status"] = "timed_out"
            save("discovery-startup.json", receipt)
            raise AssertionError("Local history filesystem probes did not finish before discovery")
        time.sleep(0.1)


def terminal(workspace, label, accepted, expected="succeeded", code=None):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"M1 {label}: {task_id}", flush=True)
    result = workspace.wait_task(task_id, timeout=1500, poll_interval=1)
    save(label + ".json", result)
    task = result["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    save("result.json", record)
    assert task["state"] == expected, f"{label}: {result}"
    if code is not None:
        assert any(d["code"] == code for d in result["data"]["diagnostics"]), result
    return result


def create_file(workspace, relative, content):
    result = workspace.write_file(relative, content)
    assert result["status"] == "committed" and result["data"]["changed"], result
    record["writes"].append({"path": relative, "mode": "create", "sha256": result["data"]["sha256"],
                             "recoveryPointId": result.get("recoveryPointId")})
    return result


def edit_file(workspace, relative, change):
    """Read, transform and save through Core with the read's hash; never writes behind Core."""
    current = workspace.read_file(relative)["data"]
    updated = change(current["content"])
    assert updated != current["content"], f"{relative}: edit made no change"
    result = workspace.write_file(relative, updated, expected_sha256=current["sha256"])
    assert result["status"] == "committed", result
    record["writes"].append({"path": relative, "mode": "replace", "sha256": result["data"]["sha256"],
                             "recoveryPointId": result.get("recoveryPointId")})
    return result


def expect_rejected(label, code, action):
    try:
        action()
    except NativeApiError as error:
        assert error.code == code, f"{label}: {error.code} {error}"
        record["negativeGates"].append({"label": label, "code": code, "passed": True})
        save(label + ".json", {"code": error.code, "message": str(error), "details": error.details})
        return
    raise AssertionError(f"{label}: Core accepted a write it must reject")


def add_hook(text):
    newline = "\r\n" if "\r\n" in text else "\n"
    marker = text.index("// Start of user code block " + HOOK_BLOCK)
    indent = text[text.rfind("\n", 0, marker) + 1:marker]
    insert_at = text.index("\n", marker) + 1
    return text[:insert_at] + indent + HOOK + newline + text[insert_at:]


def find_main_class(workspace):
    listed = workspace.query("list_workspace_files", search="Mod.java", limit=200)["data"]["files"]
    for entry in listed:
        if entry["ownership"] != "generated" or not entry["relativePath"].startswith("src/main/java/"):
            continue
        read = workspace.read_file(entry["relativePath"])["data"]
        if HOOK_BLOCK in read.get("userCodeBlocks", []):
            return read
    raise AssertionError(f"No generated main class with a '{HOOK_BLOCK}' user code block: {listed}")


def reject_export(workspace, label, task_id, code, historical=False):
    result = terminal(workspace, label, workspace.export_verified_artifact(task_id, allow_historical=historical),
                      expected="failed", code=code)
    assert "verifiedExport" not in result["data"]["task"], result
    record["negativeGates"].append({"label": label, "code": code, "passed": True})
    return result


def inject_recovered_record(original_id, label, xml, minimum, actual_counts):
    """Negative fixture only: corrupt a copy, preserving the original positive task and report."""
    original = json.loads((root / ".copperbench/task-records" / (original_id + ".json")).read_text(encoding="utf-8"))
    injected = copy.deepcopy(original)
    task_id = str(uuid.uuid4())
    task = injected["task"]
    task["id"] = task_id
    report_path = root / ".copperbench/m1-negative" / (label + ".xml")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(xml, encoding="utf-8")
    verification = task["verification"]
    verification.update({"reportPath": str(report_path), "reportSha256": sha(report_path),
                         "minimumTests": minimum, "status": "passed", **actual_counts})
    (root / ".copperbench/task-records" / (task_id + ".json")).write_text(json.dumps(injected), encoding="utf-8")
    save(label + "-injected-record.json", injected)
    record["negativeGates"].append({"label": label, "layer": "negative_recovered_record_injection",
                                   "taskId": task_id, "rawReportSha256": sha(report_path)})
    return task_id


def export_current(workspace, label, acceptance):
    result = terminal(workspace, label, workspace.export_verified_artifact(acceptance["id"]))
    receipt = result["data"]["task"]["verifiedExport"]
    assert receipt["status"] == "passed_current_input", receipt
    verification = acceptance["verification"]
    assert receipt["artifactSha256"] == verification["artifactSha256"]
    assert receipt["reportSha256"] == verification["reportSha256"]
    assert receipt["sourceSha256"] == verification["sourceSnapshot"]["sha256"]
    destination = Path(receipt["exportDirectory"])
    assert sha(destination / "verified-mod.jar") == receipt["artifactSha256"]
    assert sha(destination / "gametest-results.xml") == receipt["reportSha256"]
    return receipt


def check_packaged_content(jar):
    """The hand-written files and the imported texture are the bytes that ship in the accepted JAR."""
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        expected = {
            "m1delivery/content/DeliveryContent.class": None,
            "assets/m1_delivery/textures/item/discovery_item.png": (fixture / "discovery_item.png").read_bytes(),
            "assets/m1_delivery/models/item/discovery_item.json": None,
            "data/m1_delivery/recipe/discovery_recipe.json": None,
        }
        for name, content in expected.items():
            assert name in names, f"{name} is missing from the accepted JAR"
            if content is not None:
                assert archive.read(name) == content, f"{name} differs from the imported source"
        recipe = json.loads(archive.read("data/m1_delivery/recipe/discovery_recipe.json"))
        assert recipe["result"]["id"] == "m1_delivery:discovery_item", recipe
    return sorted(expected)


try:
    record["productSource"] = source_identity()
    content_source = (fixture / "DeliveryContent.java").read_text(encoding="utf-8")
    assert content_source.count(CORRECT_BODY) == 1
    with open_workspace() as workspace:
        # Discovery: environment, file ownership and the main class's hook, with no writes.
        before = discovery_baseline()
        save("discovery-files-before.json", before)
        environment = workspace.get_workspace_environment()["data"]
        main_class = find_main_class(workspace)
        build_script = workspace.read_file("build.gradle")["data"]["content"]
        mappings = "official_mojang" if "officialMojangMappings" in build_script else "declared_in_build_gradle"
        save("discovery.json", {"mainClass": {key: main_class[key] for key in
                                              ("relativePath", "ownership", "editable", "userCodeBlocks")},
                                "mappings": mappings, "generator": environment.get("generator")})
        after = inventory()
        save("discovery-files-after.json", after)
        changed_paths = sorted(path for path in before.keys() | after.keys()
                               if before.get(path) != after.get(path))
        save("discovery-file-changes.json", changed_paths)
        assert before == after, f"Discovery modified workspace paths: {changed_paths}"
        assert workspace.revision == 0
        assert main_class["ownership"] == "generated" and not main_class["editable"], main_class

        # Authoring: one recovery point, hand-written files, an imported texture and the main-class hook.
        point = workspace.command("create_recovery_point", label="Before agent: M1 native delivery")
        record["agentRecoveryPoint"] = point.get("recoveryPointId")
        create_file(workspace, CONTENT, content_source)
        create_file(workspace, RECIPE, (fixture / "discovery_recipe.json").read_text(encoding="utf-8"))
        create_file(workspace, MODEL, (fixture / "discovery_item_model.json").read_text(encoding="utf-8"))
        imported = workspace.import_asset(fixture / "discovery_item.png", TEXTURE)
        assert imported["status"] == "committed", imported
        save("asset-import.json", imported)
        assert sha(root / TEXTURE) == sha(fixture / "discovery_item.png")
        edit_file(workspace, main_class["relativePath"], add_hook)
        expect_rejected("generated-outside-user-code", "WORKSPACE_SOURCE_READ_ONLY",
                        lambda: edit_file(workspace, main_class["relativePath"],
                                          lambda text: text.replace("LOGGER.info(", "LOGGER.debug(", 1)))
        expect_rejected("create-over-existing-file", "WORKSPACE_SOURCE_CONFLICT",
                        lambda: workspace.write_file(CONTENT, "class Replaced {}"))

        # Tests: Core prepares the host configuration; the Agent adds its cases through Core.
        terminal(workspace, "prepare-game-tests", workspace.prepare_game_tests())
        create_file(workspace, TESTS, (fixture / "ContractGameTests.java").read_text(encoding="utf-8"))

        def configure_tests(text):
            configured = json.loads(text)
            configured["entrypoints"] = ["copperbench.acceptance.ContractGameTests"]
            configured["minimumTests"] = 5
            return json.dumps(configured, indent=2) + "\n"
        edit_file(workspace, TEST_CONFIG, configure_tests)

        preflight = workspace.preview_generation()
        save("preflight.json", preflight)
        assert preflight["data"]["status"] == "ready", "A user-code edit must not conflict with generation: " + str(preflight)
        record["environment"] = workspace.query("get_workspace_environment")["data"]

        terminal(workspace, "initial-build", workspace.build())
        edit_file(workspace, CONTENT, lambda text: text.replace(CORRECT_BODY, "return missing_m1_symbol;"))
        failed = terminal(workspace, "controlled-compile-failure", workspace.build(), "failed")
        diagnostics = failed["data"]["diagnostics"]
        assert any(d["code"] == "JAVA_COMPILE_ERROR" and "DeliveryContent.java" in str(d) and "Line " in str(d)
                   for d in diagnostics), diagnostics
        assert "missing_m1_symbol" in json.dumps(failed, ensure_ascii=False)
        edit_file(workspace, CONTENT, lambda text: text.replace("return missing_m1_symbol;", CORRECT_BODY))
        terminal(workspace, "repaired-build", workspace.build())
        accepted = terminal(workspace, "packaged-acceptance", workspace.run_game_tests())["data"]["task"]
        verify = accepted["verification"]
        assert verify["mode"] == "packaged_jar" and verify["status"] == "passed", verify
        assert verify["acceptanceExecuted"] == 5 and verify["failed"] == 0 and verify["skipped"] == 0, verify
        assert verify["sourceCurrentAtCompletion"] is True
        assert len(verify["cases"]) == 5
        record["packagedContent"] = check_packaged_content(verify["artifactPath"])
        first_export = export_current(workspace, "verified-export", accepted)
        for name, path in [("accepted-mod.jar", verify["artifactPath"]), ("accepted-report.xml", verify["reportPath"])]:
            shutil.copy2(path, evidence / name)
        saved_revision = workspace.revision
        old_process = workspace._process

    assert old_process.poll() == 0, "Native launcher did not close normally"
    with open_workspace() as workspace:
        assert workspace._process.pid != old_process.pid, "Reconnect did not replace the process/transport"
        assert workspace.revision == saved_revision
        recovered = workspace.get_task(accepted["id"])["data"]["task"]
        assert recovered["restoredFromHistory"] is True
        assert recovered["verification"] == accepted["verification"]
        assert workspace.read_file(CONTENT)["data"]["ownership"] == "manual"
        assert HOOK in workspace.read_file(main_class["relativePath"])["data"]["content"]
        assert not list(workspace.list_mod_elements()), "The native route creates no structured elements"
        export_current(workspace, "reopened-verified-export", recovered)
        save("reconnect.json", {"oldPid": old_process.pid, "oldExitCode": old_process.returncode,
                                "newPid": workspace._process.pid, "revision": workspace.revision,
                                "restoredTaskId": recovered["id"], "sourceSha256": verify["sourceSnapshot"]["sha256"]})

        # Real packaged-server insufficient-count failure: five business cases with a six-case minimum.
        def minimum(count):
            def change(text):
                configured = json.loads(text)
                configured["minimumTests"] = count
                return json.dumps(configured, indent=2) + "\n"
            return change
        edit_file(workspace, TEST_CONFIG, minimum(6))
        try:
            insufficient = terminal(workspace, "insufficient-tests", workspace.run_game_tests(), "failed", "GAMETEST_NO_TESTS")
            assert insufficient["data"]["task"]["verification"]["acceptanceExecuted"] == 5
            reject_export(workspace, "insufficient-export", insufficient["data"]["task"]["id"], "VERIFIED_ARTIFACT_UNAVAILABLE")
        finally:
            edit_file(workspace, TEST_CONFIG, minimum(5))

        # Tamper only the isolated evidence files, restore exact bytes and retain copies for inspection.
        for key, label in [("artifactPath", "jar-tamper"), ("reportPath", "report-tamper")]:
            path = Path(verify[key]); original_bytes = path.read_bytes()
            try:
                path.write_bytes(original_bytes + b"\nM1 controlled tamper\n")
                reject_export(workspace, label, accepted["id"], "VERIFIED_EVIDENCE_CHANGED", historical=True)
            finally:
                path.write_bytes(original_bytes)

        skipped_id = inject_recovered_record(accepted["id"], "all-skipped",
                    '<testsuite><testcase name="ignored"><skipped/></testcase></testsuite>', 1,
                    {"discovered": 1, "executed": 0, "passed": 0, "failed": 0, "skipped": 1,
                     "frameworkTests": 0, "acceptanceExecuted": 0,
                     "cases": [{"name": "ignored", "className": "", "status": "skipped", "scope": "acceptance"}]})
        reject_export(workspace, "all-skipped-export", skipped_id, "VERIFIED_ACCEPTANCE_INVALID", historical=True)
        empty_id = inject_recovered_record(accepted["id"], "zero-tests", "<testsuite/>", 1,
                    {"discovered": 0, "executed": 0, "passed": 0, "failed": 0, "skipped": 0,
                     "frameworkTests": 0, "acceptanceExecuted": 0, "cases": []})
        reject_export(workspace, "zero-tests-export", empty_id, "VERIFIED_ACCEPTANCE_INVALID")

        # Recreate a legacy passed record whose six XML nodes are one business identity.
        duplicate_case = copy.deepcopy(verify["cases"][0])
        duplicate_suite = ET.Element("testsuite")
        for _ in range(6):
            ET.SubElement(duplicate_suite, "testcase", {"name": duplicate_case["name"],
                                                       "classname": duplicate_case["className"]})
        duplicate_id = inject_recovered_record(accepted["id"], "duplicate-tests",
                    ET.tostring(duplicate_suite, encoding="unicode"), 6,
                    {"discovered": 6, "executed": 6, "passed": 6, "failed": 0, "skipped": 0,
                     "frameworkTests": 0, "acceptanceExecuted": 6,
                     "cases": [copy.deepcopy(duplicate_case) for _ in range(6)]})
        reject_export(workspace, "duplicate-current-export", duplicate_id, "VERIFIED_ACCEPTANCE_INVALID")

        changed_marker = "\n// Changed input after acceptance.\n"
        edit_file(workspace, CONTENT, lambda text: text + changed_marker)
        reject_export(workspace, "duplicate-historical-export", duplicate_id, "VERIFIED_ACCEPTANCE_INVALID", historical=True)
        reject_export(workspace, "source-changed", accepted["id"], "VERIFICATION_INPUT_CHANGED")
        historical = terminal(workspace, "explicit-historical-export",
                              workspace.export_verified_artifact(accepted["id"], allow_historical=True))
        assert historical["data"]["task"]["verifiedExport"]["status"] == "passed_historical_input"
        reject_export(workspace, "historical-is-not-current", accepted["id"], "VERIFICATION_INPUT_CHANGED")
        edit_file(workspace, CONTENT, lambda text: text.replace(changed_marker, "", 1))
        # A new acceptance binds the final current revision after all controlled source mutations.
        final_task = terminal(workspace, "final-current-acceptance", workspace.run_game_tests())["data"]["task"]
        final_export = export_current(workspace, "final-current-export", final_task)
        shutil.copy2(Path(final_export["exportDirectory"]) / "verified-mod.jar", evidence / "final-verified-mod.jar")
        shutil.copy2(Path(final_export["exportDirectory"]) / "gametest-results.xml", evidence / "final-gametest-results.xml")
        record["finalAcceptance"] = final_task["verification"]
        record["finalExport"] = final_export
        final_revision = workspace.revision

    with open_workspace() as workspace:
        assert workspace.revision == final_revision
        final_restored = workspace.get_task(final_task["id"])["data"]["task"]
        assert final_restored["restoredFromHistory"]
        export_current(workspace, "final-reopen-export", final_restored)
    record["status"] = "passed"
except BaseException as error:
    record["status"] = "failed"
    record["failure"] = {"type": type(error).__name__, "message": str(error), "traceback": traceback.format_exc()}
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result.json", record)
    print(f"M1 delivery {record['status']}: {evidence}", flush=True)
