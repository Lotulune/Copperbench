#!/usr/bin/env python3
"""Installed Native Core acceptance; fixture preparation is separate from product sessions.

No task grant, Minecraft control bridge, synthetic player input or source-built product is used.
"""
from __future__ import annotations

import argparse
import copy
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import sys
import time
import traceback
import zipfile

from common import command, require, save, sha256, workspace_document, observed_gradle


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--mode", choices=["smoke", "full"], required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[2]
    product, root, evidence = args.product.resolve(), args.root.resolve(), args.evidence.resolve()
    require(not root.exists(), "Native acceptance requires a new workspace root")
    root.mkdir(parents=True)
    evidence.mkdir(parents=True, exist_ok=True)
    sdk = product / "sdk/python"
    sys.path.insert(0, str(sdk))
    import copperbench_native
    require(Path(copperbench_native.__file__).resolve() == sdk / "copperbench_native.py",
            "The SDK must come from the installed package")
    suffix = ".exe" if os.name == "nt" else ""
    java = product / f"jdk/bin/java{suffix}"
    launcher = product / ("copperbench.exe" if os.name == "nt" else "copperbench.sh")
    fixture = repo / "src/test/java/dev/copperbench/headless/Stage15GraphicalWorkspaceFixture.java"
    classes = root / "fixture-classes"
    classes.mkdir()
    classpath = os.pathsep.join([str(product / "lib/copperbench.jar"), str(product / "lib/*")])
    record = {"kind": "published-installed-native-acceptance", "status": "running", "mode": args.mode,
              "startedAt": datetime.now(timezone.utc).isoformat(), "product": str(product),
              "applicationSha256": sha256(product / "lib/copperbench.jar"),
              "launcherSha256": sha256(launcher), "sdkSha256": sha256(sdk / "copperbench_native.py"),
              "fixtureSha256": sha256(fixture), "sessions": [], "tasks": [], "checks": [],
              "preparation": "Existing test-only workspace fixture compiled against installed libraries; no approval grant issued",
              "playerInteractionTested": False, "playerWorldPersistenceTested": False}
    workspaces = []

    def persist():
        save(evidence / "result.json", record)

    def create(generator, mod_id):
        workspace_root = root / mod_id
        text = command([java, "--add-opens=java.base/java.lang=ALL-UNNAMED", "-cp",
                        str(classes) + os.pathsep + classpath,
                        "dev.copperbench.headless.Stage15GraphicalWorkspaceFixture",
                        workspace_root, generator, "Cloud " + mod_id, mod_id],
                       evidence / f"prepare-{mod_id}.log", cwd=product, timeout=420)
        result = next((json.loads(line) for line in text.splitlines()
                       if line.startswith('{') and '"status"' in line), {})
        require(result.get("status") == "committed", "Fixture did not create a workspace")
        document = workspace_document(workspace_root)
        workspaces.append(workspace_root)
        save(evidence / f"prepare-{mod_id}.json", {"generator": generator, "workspaceFile": str(document),
                                                  "fixtureSha256": record["fixtureSha256"], "result": result})
        return document

    @contextmanager
    def session(path):
        workspace = copperbench_native.Workspace.open(path, launcher=launcher, cwd=product,
                                                      request_timeout=120, startup_timeout=180)
        process = workspace._process
        current = {"pid": process.pid, "workspace": str(path)}
        record["sessions"].append(current)
        try:
            result = workspace.query("get_workspace_environment")
            save(evidence / f"environment-{process.pid}.json", result)
            application = result["data"]["application"]
            require(application["sourceState"] == "packaged_binary" and
                    application["applicationSha256"] == record["applicationSha256"],
                    "The running application differs from the installed package")
            gradle = observed_gradle(result["data"], os.environ["COPPERBENCH_GRADLE_USER_HOME"])
            current.update(identity=application, gradle=gradle)
            persist()
            yield workspace
        finally:
            workspace.close()
            current["exitCode"] = process.poll()
            persist()
            require(current["exitCode"] == 0, "Native product host did not exit normally")

    def terminal(workspace, label, accepted, expected="succeeded", diagnostic=None):
        save(evidence / f"{label}-accepted.json", accepted)
        require(accepted["status"] == "accepted", label + " was not accepted")
        task_id = accepted["task"]["id"]
        print("TASK", label, task_id, flush=True)
        with (evidence / f"{label}-updates.jsonl").open("w", encoding="utf-8", newline="\n") as updates:
            def update(value):
                updates.write(json.dumps(value, ensure_ascii=False) + "\n")
                updates.flush()
            result = workspace.wait_task(task_id, timeout=1800, poll_interval=2, on_update=update)
        save(evidence / f"{label}.json", result)
        task = result["data"]["task"]
        record["tasks"].append({"label": label, "id": task_id, "state": task["state"], "expected": expected})
        persist()
        require(task["state"] == expected, f"{label}: expected {expected}, received {task['state']}")
        if diagnostic:
            require(any(row["code"] == diagnostic for row in result["data"]["diagnostics"]),
                    label + " lacks the expected diagnostic")
        return result

    def code_update(workspace, element_id, source):
        editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
        fields = [field for section in editor["sections"] for field in section.get("fields", [])]
        fingerprints = [{"path": f["path"], "value": f.get("value")} for f in fields
                        if f["path"].startswith("/sourceFingerprints/")]
        require(any(f["path"] == "/sourceFingerprints/$primary" for f in fingerprints),
                "Code editor did not return its source fingerprint")
        workspace.update_mod_element(elementId=element_id, changes=[*fingerprints, {"path": "/code", "value": source}])

    def custom_export(path, workspace, expected_task, label):
        project = path.parent
        script = project / "build.gradle"
        with script.open("a", encoding="utf-8", newline="\n") as output:
            output.write('\n// Cloud acceptance: exercise a non-default declared archive destination.\n'
                         'tasks.withType(org.gradle.api.tasks.bundling.AbstractArchiveTask).configureEach {\n'
                         '    destinationDirectory = layout.buildDirectory.dir("cloud-archives")\n}\n')
        libs = project / "build/libs"
        if libs.exists():
            libs.rename(project / "build/previous-libs")
        target = project / "exports" / (label + ".jar")
        target.parent.mkdir(exist_ok=True)
        result = terminal(workspace, label, workspace.command("export_workspace", output=target.relative_to(project).as_posix()))
        task_id = result["data"]["task"]["id"]
        receipt_path = project / ".copperbench/task-runs/export" / task_id / "archive-output.json"
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        artifact = Path(receipt["artifactPath"]).resolve(strict=True)
        require(receipt["archiveTask"] == expected_task and receipt["taskId"] == task_id,
                "Export did not use the selected archive task")
        require(artifact.parent == (project / "build/cloud-archives").resolve() and not libs.exists(),
                "Export did not exercise an absent build/libs with a custom archive directory")
        require(sha256(target) == sha256(artifact) == receipt["sha256"], "Exported bytes differ from the declared archive")
        save(evidence / f"{label}-archive.json", receipt)
        record["checks"].append({"name": label, "status": "passed", "archive": receipt,
                                 "buildLibsAbsent": True, "exportSha256": sha256(target)})

    def datapack(generator, mod_id):
        path = create(generator, mod_id)
        with session(path) as workspace:
            created = workspace.create_mod_element(elementType="function", name="cloud_ping")
            element_id = created["data"]["element"]["id"]
            editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
            fields = [field for section in editor["sections"] for field in section.get("fields", [])]
            code = [field for field in fields if field["path"].rsplit("/", 1)[-1] == "code"]
            require(len(code) == 1, "Function editor must expose one code field")
            workspace.update_mod_element(elementId=element_id,
                                         changes=[{"path": code[0]["path"], "value": "say Cloud acceptance"}])
            target = path.parent / "exports" / (mod_id + ".zip")
            target.parent.mkdir(exist_ok=True)
            terminal(workspace, mod_id + "-export", workspace.command("export_workspace", output=target.relative_to(path.parent).as_posix()))
            with zipfile.ZipFile(target) as archive:
                require("pack.mcmeta" in archive.namelist(), "Data pack has no metadata")
                functions = [name for name in archive.namelist() if name.endswith("/cloud_ping.mcfunction")]
                require(len(functions) == 1 and b"say Cloud acceptance" in archive.read(functions[0]),
                        "The exported data pack lacks the edited function")
            record["checks"].append({"name": generator + "-zip", "status": "passed", "sha256": sha256(target)})

    started = time.monotonic()
    try:
        persist()
        command([product / f"jdk/bin/javac{suffix}", "-cp", classpath, "-d", classes, fixture],
                evidence / "fixture-compile.log", cwd=product)
        if args.mode == "smoke":
            datapack("datapack-1.21.1", "cloud_smoke")
        else:
            path = create("fabric-1.21.1", "m1_delivery")
            fixture_dir = repo / "examples/agent-readiness/m1-delivery"
            tests = path.parent / "src/gametest/java/copperbench/acceptance/ContractGameTests.java"
            tests.parent.mkdir(parents=True)
            shutil.copy2(fixture_dir / "ContractGameTests.java", tests)
            shutil.copy2(fixture_dir / "copperbench-tests.json", path.parent / "copperbench-tests.json")
            source = (fixture_dir / "recovery_probe.java").read_text(encoding="utf-8")
            require(source.count("return (count + 15) / 16;") == 1, "Recovery fixture changed")
            record["gameTestFixtureHashes"] = {p.name: sha256(p) for p in fixture_dir.iterdir() if p.is_file()}
            with session(path) as workspace:
                item, recipe = workspace.discover_field_contract("item"), workspace.discover_field_contract("recipe")
                require(item["complete"] and recipe["complete"], "Incomplete item or recipe discovery contract")
                save(evidence / "field-discovery.json", {"item": item, "recipe": recipe})
                workspace.create_mod_element(**item["minimalExample"])
                payload = copy.deepcopy(recipe["minimalExample"])
                payload["initialValues"]["recipeReturnStack"] = "CUSTOM:discovery_item"
                workspace.create_mod_element(**payload)
                code = workspace.create_mod_element(elementType="code", name="recovery_probe", initialValues={"code": source})
                code_id = code["data"]["element"]["id"]
                terminal(workspace, "fabric-initial-build", workspace.build())
                code_update(workspace, code_id, source.replace("return (count + 15) / 16;", "return missing_m1_symbol;"))
                failed = terminal(workspace, "fabric-controlled-failure", workspace.build(), "failed", "JAVA_COMPILE_ERROR")
                require("missing_m1_symbol" in json.dumps(failed), "Compile diagnostic lost the broken symbol")
                code_update(workspace, code_id, source)
                terminal(workspace, "fabric-repaired-build", workspace.build())
                accepted = terminal(workspace, "fabric-packaged-gametest", workspace.run_game_tests())["data"]["task"]
                verification = accepted["verification"]
                require(verification["status"] == "passed" and verification["mode"] == "packaged_jar"
                        and verification["acceptanceExecuted"] == 5 and verification["failed"] == 0
                        and verification["skipped"] == 0 and verification["sourceCurrentAtCompletion"] is True,
                        "Packaged GameTest acceptance did not pass all five checks")
                save(evidence / "verification.json", verification)
                exported = terminal(workspace, "fabric-verified-export", workspace.export_verified_artifact(accepted["id"]))["data"]["task"]["verifiedExport"]
                require(exported["status"] == "passed_current_input" and exported["artifactSha256"] == verification["artifactSha256"]
                        and sha256(Path(exported["exportDirectory"]) / "verified-mod.jar") == verification["artifactSha256"],
                        "Verified export differs from the tested JAR")
                revision, previous_pid = workspace.revision, workspace._process.pid
            with session(path) as workspace:
                restored = workspace.get_task(accepted["id"])["data"]["task"]
                require(workspace._process.pid != previous_pid and workspace.revision == revision
                        and restored["restoredFromHistory"] is True and restored["verification"] == verification,
                        "Reopened task history or revision did not persist")
                require({"discovery_item", "discovery_recipe", "recovery_probe"} <=
                        {e["name"] for e in workspace.list_mod_elements()}, "Reopened elements did not persist")
                reopened = terminal(workspace, "fabric-reopened-export", workspace.export_verified_artifact(accepted["id"]))["data"]["task"]["verifiedExport"]
                require(reopened["artifactSha256"] == verification["artifactSha256"], "Reopened export identity changed")
                record["checks"].append({"name": "workspace-and-task-persistence", "status": "passed", "revision": revision})
                # This deliberate build-input change occurs only after the exact-input verification assertions.
                custom_export(path, workspace, ":remapJar", "fabric-custom-export")
            path = create("neoforge-1.21.1", "cloud_neoforge")
            with session(path) as workspace:
                workspace.create_mod_element(**workspace.discover_field_contract("item")["minimalExample"])
                terminal(workspace, "neoforge-build", workspace.build())
                custom_export(path, workspace, ":jar", "neoforge-custom-export")
            for generator, mod_id in [("datapack-1.20.1", "cloud_dp1201"), ("datapack-1.21.1", "cloud_dp1211"),
                                      ("datapack-26.1.x", "cloud_dp261")]:
                datapack(generator, mod_id)
        record["status"] = "passed"
    except Exception as error:
        record.update(status="failed", error=str(error), errorType=type(error).__name__)
        (evidence / "failure.txt").write_bytes(traceback.format_exc().encode("utf-8"))
        print(traceback.format_exc(), flush=True)
    finally:
        # Stop only the test's isolated Gradle daemons before the real uninstaller checks processes.
        for project in workspaces:
            wrapper = project / ("gradlew.bat" if os.name == "nt" else "gradlew")
            if not wrapper.is_file() or not (project / ".gradle").is_dir():
                continue
            try:
                environment = dict(os.environ, JAVA_HOME=str(product / "jdk21"))
                command([wrapper, "--stop"], evidence / f"stop-gradle-{project.name}.log", cwd=project,
                        env=environment, timeout=180)
            except Exception as error:
                record.setdefault("cleanupErrors", []).append(str(error))
                record["status"] = "failed"
            task_records = project / ".copperbench/task-records"
            if task_records.is_dir():
                shutil.copytree(task_records, evidence / "task-records" / project.name, dirs_exist_ok=True)
        record.update(completedAt=datetime.now(timezone.utc).isoformat(), elapsedSeconds=time.monotonic() - started)
        persist()
    return 0 if record["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
