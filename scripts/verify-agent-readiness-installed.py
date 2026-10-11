#!/usr/bin/env python3
"""Replay the fixed M3 task through an installed product and its bundled SDK.

This is a scripted installed-product check, not an autonomous-agent or user-study
success-rate measurement. It does not issue task approvals or operate Minecraft.
"""
from __future__ import annotations

import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import traceback
import uuid


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(root):
    result = {}
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        # Windows exclusively locks the live writer lease. Track its presence and
        # size, as the Core preflight tests do; hash every other workspace file.
        result[relative] = ({"writerLeaseBytes": path.stat().st_size}
                            if relative == ".copperbench/workspace.write.lock" else digest(path))
    return result


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_record(path):
    require(path.stat().st_size <= 16 * 1024 * 1024, "Evidence JSON exceeds 16 MiB")
    value = json.loads(path.read_text(encoding="utf-8-sig"))
    require(isinstance(value, dict), "Evidence JSON must be an object")
    return value


def disjoint_paths(writable, inputs):
    for index, path in enumerate(writable):
        for other in [*writable[index + 1:], *inputs]:
            require(path != other and path not in other.parents and other not in path.parents,
                    f"Writable run directories must not overlap one another or input files: {path}, {other}")


def cache_inventory(home):
    """Hash retained dependency/distribution payloads, excluding mutable daemon and resolution indexes."""
    result = {"userHome": str(home), "directoryPresent": home.is_dir(), "files": 0, "artifacts": {}}
    if not home.exists():
        return result
    require(home.is_dir() and not home.is_symlink() and not getattr(home, "is_junction", lambda: False)(),
            "Cache home must be a real directory")
    for directory, directories, files in os.walk(home, followlinks=False):
        for name in [*directories, *files]:
            path = Path(directory) / name
            require(not path.is_symlink() and not getattr(path, "is_junction", lambda: False)(),
                    f"Cache evidence cannot follow a linked entry: {path}")
        for name in sorted(files):
            path = Path(directory) / name
            relative = path.relative_to(home).as_posix()
            result["files"] += 1
            if relative.startswith("caches/modules-2/files-2.1/") or (
                    relative.startswith(("wrapper/dists/", "caches/fabric-loom/"))
                    and path.suffix.lower() in {".jar", ".zip", ".pom", ".module", ".tiny"}):
                result["artifacts"][relative] = {"bytes": path.stat().st_size, "sha256": digest(path)}
    result["artifacts"] = dict(sorted(result["artifacts"].items()))
    result["scope"] = "Dependency payloads and Gradle/Minecraft archives; excludes mutable resolution indexes and daemon logs"
    return result


def prepare_cache(mode, home, warm_from, identity):
    if mode == "retained":
        require(home is None and warm_from is None, "Choose cold or warm mode to select a cache home")
        return {"mode": mode, "initialCondition": "unverified_existing_environment"}, {}, None
    require(home is not None and home.is_absolute(), "Cold/warm mode requires an absolute --gradle-user-home")
    home = home.resolve()
    if mode == "cold":
        require(warm_from is None, "Cold mode does not accept --warm-from")
        require(not home.exists(), "Cold mode requires a new cache directory; existing caches are never cleared")
        home.mkdir(parents=True)
        before = cache_inventory(home)
        require(before["files"] == 0 and not any(home.iterdir()), "Cold cache was not empty before launch")
        condition = "created_empty_before_product_launch"
        previous = None
    else:
        require(warm_from is not None, "Warm mode requires --warm-from pointing to a successful cold result.json")
        previous = read_record(warm_from)
        require(previous.get("kind") == "m3-scripted-installed-replay" and previous.get("status") == "passed",
                "Warm input must be a successful installed replay")
        for key, value in identity.items():
            require(previous.get(key) == value, "Warm replay input identity changed: " + key)
        cache = previous.get("cache", {})
        require(cache.get("mode") == "cold" and cache.get("effectiveHomeVerified") is True
                and cache.get("initialCondition") == "created_empty_before_product_launch",
                "Warm input has no verified cold-cache provenance")
        require(Path(cache.get("userHome", "")).resolve() == home, "Warm replay must reuse the recorded cold cache")
        prior_manifest = warm_from.parent / "cache-after.json"
        require(digest(prior_manifest) == cache.get("afterManifestSha256"), "Cold cache manifest changed")
        expected = read_record(prior_manifest).get("artifacts", {})
        require(isinstance(expected, dict) and bool(expected), "Cold result contains no retained dependency payloads")
        before = cache_inventory(home)
        for relative, metadata in expected.items():
            require(before["artifacts"].get(relative) == metadata, "Retained cold-cache payload changed: " + relative)
        condition = "retained_payloads_match_successful_cold_run"
    result = {"mode": mode, "userHome": str(home), "initialCondition": condition,
              "effectiveHomeVerified": False, "externalDistributionReuse": False,
              "scope": "Gradle user home and a new workspace; product preferences and network configuration are retained"}
    if previous is not None:
        result["warmFrom"] = str(warm_from)
        result["warmFromSha256"] = digest(warm_from)
    return result, {"COPPERBENCH_GRADLE_USER_HOME": str(home), "GRADLE_USER_HOME": str(home),
                    "COPPERBENCH_GRADLE_REUSE_EXTERNAL": "false"}, before


def check_running_environment(envelope, record):
    data = envelope.get("data", {})
    application = data.get("application", {})
    require(envelope.get("status") == "succeeded" and application.get("sourceState") == "packaged_binary",
            "The running product must report its packaged application identity")
    require(application.get("applicationSha256") == record["applicationSha256"],
            "The running product does not match the installed application JAR")
    gradle = data.get("execution", {}).get("gradle", {})
    cache = record["cache"]
    if cache["mode"] != "retained":
        require(isinstance(gradle.get("userHome"), str) and Path(gradle["userHome"]).resolve() == Path(cache["userHome"]),
                "The running product did not use the selected Gradle cache")
        require(gradle.get("userHomeSource") == "COPPERBENCH_GRADLE_USER_HOME"
                and gradle.get("reuseExternalDistributions") is False,
                "The running product cannot confirm external distribution reuse is disabled")
        cache["effectiveHomeVerified"] = True
    return {"application": application, "gradle": gradle}


def prepare_import_fixture(template, root):
    """Prepare an empty imported fixture; this does not test bootstrap or issue authority."""
    document = read_record(template)
    require(document.get("mod_elements") == []
            and document.get("workspaceSettings", {}).get("currentGenerator") == "fabric-1.21.1",
            "Imported fixture requires an empty Fabric 1.21.1 workspace template")
    require(not root.exists(), "Imported fixture destination must be new")
    document["workspaceSettings"].update(modid="m1_delivery", modName="M1 Delivery",
                                        modElementsPackage="net.mcreator.m1_delivery")
    document["dev.copperbench"] = {"schemaVersion": 1, "workspaceId": str(uuid.uuid4()), "revision": 0}
    root.mkdir(parents=True)
    workspace_file = root / "m1_delivery.mcreator"
    workspace_file.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    return workspace_file


def generate_fixture_base(product, workspace_file, output, save):
    """Use packaged generator templates to finish test input before opening the public SDK."""
    source = Path(__file__).resolve().parents[1] / "src/test/java/dev/copperbench/headless/InstalledDeliveryWorkspaceFixture.java"
    copied_source = output / source.name
    shutil.copy2(source, copied_source)
    classes = output / "fixture-classes"
    classes.mkdir()
    suffix = ".exe" if sys.platform == "win32" else ""
    classpath = os.pathsep.join([str(product / "lib/copperbench.jar"), str(product / "lib/*")])
    commands = [
        ("fixture-compile", [str(product / ("jdk/bin/javac" + suffix)), "-cp", classpath,
                             "-d", str(classes), str(copied_source)]),
        ("fixture-generate", [str(product / ("jdk/bin/java" + suffix)), "-Xmx512m",
                              "--add-opens=java.base/java.lang=ALL-UNNAMED",
                              "--enable-native-access=ALL-UNNAMED,jcef", "-cp", str(classes) + os.pathsep + classpath,
                              "dev.copperbench.headless.InstalledDeliveryWorkspaceFixture", str(workspace_file),
                              str(output / "fixture-runtime.json")]),
    ]
    for label, command in commands:
        started = time.monotonic()
        with (output / (label + ".stdout")).open("wb") as stdout, (output / (label + ".stderr")).open("wb") as stderr:
            result = subprocess.run(command, cwd=product, stdout=stdout, stderr=stderr, timeout=240, check=False)
        save(label + "-receipt", {"exitCode": result.returncode, "elapsedSeconds": time.monotonic() - started})
        require(result.returncode == 0, label + " failed; raw output retained")
    return {"preparerSha256": digest(copied_source), "runtimeReceipt": read_record(output / "fixture-runtime.json"),
            "preparedFiles": inventory(workspace_file.parent)}


def discovery_baseline(root, save, timeout=30):
    # A newly imported file starts the legacy history backend asynchronously.
    # Observe its initial commit and filesystem calibration before measuring the
    # discovery reads; the subsequent full before/after comparison remains strict.
    started = time.monotonic()
    first = inventory(root)
    previous = first
    stable = 0
    history = root / ".mcreator/localHistory"
    while True:
        probes = list(history.glob(".probe-*"))
        head_ready = any(path.is_file() and re.fullmatch(r"[0-9a-f]{40}", path.read_text().strip())
                         for path in (history / "refs/heads").glob("*"))
        observed = inventory(root)
        stable = stable + 1 if observed == previous and head_ready and not probes else 0
        elapsed = time.monotonic() - started
        if stable >= 2 or elapsed >= timeout:
            save("discovery-startup", {"status": "ready" if stable >= 2 else "timed_out",
                 "elapsedSeconds": elapsed, "initialHistoryCommitObserved": head_ready,
                 "changedPathsBeforeQueries": sorted(path for path in first.keys() | observed.keys()
                                                    if first.get(path) != observed.get(path)),
                 "remainingProbes": [path.name for path in probes], "before": first, "after": observed})
            require(stable >= 2, "Imported fixture history startup did not finish before discovery")
            return observed
        previous = observed
        time.sleep(0.25)


def verify_conflict_copy(root, workspace_name, conflict_root, open_workspace, save, terminal):
    # A warm copy generates during the edit itself. Omit its generator import
    # cache so the managed edit is genuinely deferred until the build task.
    shutil.copytree(root, conflict_root,
                    ignore=shutil.ignore_patterns(".gradle", ".mcreator", "build", "run", ".copperbench"))
    sources = list(conflict_root.glob("src/main/java/**/item/discovery_itemItem.java"))
    assert len(sources) == 1, "Expected one generated fixture item source"
    protected = sources[0]
    with open_workspace(conflict_root / workspace_name) as workspace:
        item_id = next(element["id"] for element in workspace.list_mod_elements()
                       if element["name"] == "discovery_item")
        updated = workspace.update_mod_element(elementId=item_id, changes=[{"path": "/stackSize", "value": 15}])
        save("conflict-managed-edit", updated)
        ready = workspace.preview_generation()
        save("conflict-before-external-edit", ready)
        assert ready["data"]["status"] == "ready", ready
        assert ready["data"]["dependenciesRequired"] is True, "Conflict fixture must have a cold generator cache"
        protected.write_bytes(protected.read_bytes() + b"\n// M3 external edit: preserve these exact bytes.\n")
        external_hash = digest(protected)
        before = inventory(conflict_root)
        preflight = workspace.preview_generation()
        save("conflict-preflight", preflight)
        assert preflight["data"]["status"] == "conflicted", preflight
        assert inventory(conflict_root) == before, "Conflict preflight modified the copy"
        assert any(conflict["relativePath"] == protected.relative_to(conflict_root).as_posix()
                   and conflict["reasonCode"] == "SOURCE_CHANGED"
                   for conflict in preflight["data"]["conflicts"]), preflight
        terminal(workspace, "conflict-build", workspace.build(), "failed", "GENERATION_SOURCE_CONFLICT")
        assert digest(protected) == external_hash, "Rejected build changed the external edit"
    save("external-edit-preserved", {"path": str(protected), "sha256": external_hash, "unchanged": True})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product-root", required=True, type=Path)
    parser.add_argument("--candidate-package", required=True, type=Path)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--application-sha256", required=True, help="Application JAR hash frozen from the selected candidate")
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--workspace-folder", required=True, type=Path)
    parser.add_argument("--task-authorization")
    parser.add_argument("--workspace-template", type=Path,
                        help="Import a fresh empty fixture instead of testing authorized bootstrap creation")
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache-mode", choices=("retained", "cold", "warm"), default="retained")
    parser.add_argument("--gradle-user-home", type=Path)
    parser.add_argument("--warm-from", type=Path, help="Successful cold run's result.json; required for warm mode")
    parser.add_argument("--cache-policy", default="", help="Operator note only; never establishes cache state")
    args = parser.parse_args()
    if not args.workspace_template and not args.task_authorization:
        parser.error("Bootstrap creation requires --task-authorization; fixture imports require --workspace-template")
    if not __debug__:
        parser.error("Run without Python -O: the existing delivery assertions must remain enabled")
    product = args.product_root.resolve(strict=True)
    package = args.candidate_package.resolve(strict=True)
    fixture = args.fixture.resolve(strict=True)
    template = args.workspace_template.resolve(strict=True) if args.workspace_template else None
    root = args.workspace_folder.resolve()
    output = args.output.resolve()
    conflict_root = root.with_name(root.name + "-conflict")
    cache_home = args.gradle_user_home
    warm_from = args.warm_from.resolve(strict=True) if args.warm_from else None
    if args.cache_mode != "retained" and (cache_home is None or not cache_home.is_absolute()):
        parser.error("Cold/warm mode requires an absolute --gradle-user-home")
    if cache_home is not None:
        cache_home = cache_home.resolve()
    try:
        disjoint_paths([root, conflict_root, output, *([cache_home] if cache_home else [])],
                       [product, package, fixture, *([template] if template else [])])
    except ValueError as error:
        parser.error(str(error))
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        parser.error("--source-commit must be a full Git SHA")
    if digest(package) != args.candidate_sha256.lower():
        parser.error("Candidate package hash does not match")
    if digest(product / "lib/copperbench.jar") != args.application_sha256.lower():
        parser.error("Installed application JAR does not match the frozen candidate hash")
    if root.exists() or conflict_root.exists() or output.exists():
        parser.error("Workspace, conflict-copy and output directories must all be new")
    launcher = product / ("copperbench.exe" if sys.platform == "win32" else "copperbench.sh")
    if not launcher.is_file():
        parser.error("Installed product launcher is missing")
    sys.path.insert(0, str(product / "sdk/python"))
    import copperbench_native
    if Path(copperbench_native.__file__).resolve().parent != product / "sdk/python":
        raise RuntimeError("The SDK must come from the installed candidate")
    output.mkdir(parents=True)
    shutil.copy2(__file__, output / "harness.py")
    started = time.monotonic()
    record = {
        "schemaVersion": "1.0", "status": "running", "kind": "m3-scripted-installed-replay",
        "startedAt": datetime.now(timezone.utc).isoformat(), "sourceCommit": args.source_commit,
        "candidatePackage": str(package), "candidateSha256": digest(package),
        "productRoot": str(product), "launcherSha256": digest(launcher),
        "applicationSha256": digest(product / "lib/copperbench.jar"),
        "sdkPath": copperbench_native.__file__, "sdkSha256": digest(copperbench_native.__file__),
        "harnessSha256": digest(output / "harness.py"),
        "workspace": str(root), "tasks": [], "sessions": [],
        "fixtureHashes": {name: digest(fixture / name) for name in
                          ("recovery_probe.java", "ContractGameTests.java", "copperbench-tests.json")},
        "autonomousAgentSuccessMeasured": False, "playerBehaviorVerified": False,
        "sourceCommitEvidence": "operator_declared; binary hashes are checked independently",
        "workspaceOrigin": "imported_generated_fixture" if template else "authorized_bootstrap",
        "bootstrapCreateVerified": False,
        "cachePolicy": args.cache_mode, "cacheNote": args.cache_policy,
        "cache": {"mode": args.cache_mode, "initialCondition": "not_prepared",
                  "requestedHome": str(cache_home) if cache_home else None},
    }
    frozen_inputs = {package: record["candidateSha256"], launcher: record["launcherSha256"],
                     product / "lib/copperbench.jar": record["applicationSha256"],
                     Path(copperbench_native.__file__): record["sdkSha256"],
                     **{fixture / name: value for name, value in record["fixtureHashes"].items()}}
    if template:
        record["workspaceTemplateSha256"] = digest(template)
        frozen_inputs[template] = record["workspaceTemplateSha256"]
    environment_before = {}

    def save(name, value):
        (output / (name + ".json")).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    def cli(label, arguments, timeout=1500):
        then = time.monotonic()
        with (output / (label + ".stdout")).open("w", encoding="utf-8") as stdout, \
                (output / (label + ".stderr")).open("w", encoding="utf-8") as stderr:
            completed = subprocess.run([str(launcher), *arguments], cwd=product, stdout=stdout,
                                       stderr=stderr, timeout=timeout, check=False)
        save(label + "-receipt", {"exitCode": completed.returncode, "elapsedSeconds": time.monotonic() - then})
        if completed.returncode:
            raise RuntimeError(f"{label} failed with exit {completed.returncode}; see preserved stdout/stderr")
        return json.loads((output / (label + ".stdout")).read_text(encoding="utf-8"))

    def open_workspace(path):
        workspace = copperbench_native.Workspace.open(path, launcher=launcher, cwd=product,
                    task_authorization_id=args.task_authorization, startup_timeout=180, request_timeout=120)
        try:
            observed = workspace.query("get_workspace_environment")
            save(f"session-{workspace._process.pid}-environment", observed)
            identity = check_running_environment(observed, record)
        except BaseException:
            workspace.close()
            raise
        record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id,
                                   "path": str(path), "revision": workspace.revision, "identity": identity})
        return workspace

    def terminal(workspace, label, accepted, expected="succeeded", code=None):
        assert accepted["status"] == "accepted", accepted
        task_id = accepted["task"]["id"]
        print(f"M3 {label}: {task_id}", flush=True)
        result = workspace.wait_task(task_id, timeout=1500, poll_interval=1)
        save(label, result)
        task = result["data"]["task"]
        record["tasks"].append({"label": label, "id": task_id, "state": task["state"]})
        save("result", record)
        assert task["state"] == expected, f"{label}: {task['state']} != {expected}"
        if code:
            assert any(d["code"] == code for d in result["data"]["diagnostics"]), result
        return result

    def update_code(workspace, element_id, text):
        editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
        fingerprints = [{"path": field["path"], "value": field.get("value")}
                        for section in editor["sections"] for field in section.get("fields", [])
                        if field["path"].startswith("/sourceFingerprints/")]
        assert any(field["path"] == "/sourceFingerprints/$primary" for field in fingerprints)
        workspace.update_mod_element(elementId=element_id,
                                     changes=[*fingerprints, {"path": "/code", "value": text}])

    try:
        save("result", record)
        identity = {key: record[key] for key in ("sourceCommit", "candidateSha256", "applicationSha256",
                                                 "launcherSha256", "sdkSha256", "fixtureHashes")}
        cache, overrides, before_cache = prepare_cache(args.cache_mode, cache_home, warm_from, identity)
        record["cache"] = cache
        if before_cache is not None:
            save("cache-before", before_cache)
            record["cache"]["beforeManifestSha256"] = digest(output / "cache-before.json")
        environment_before = {key: os.environ.get(key) for key in overrides}
        os.environ.update(overrides)
        save("result", record)
        if template:
            workspace_file = prepare_import_fixture(template, root)
            preparation = generate_fixture_base(product, workspace_file, output, save)
            save("fixture-preparation", {"kind": "imported_generated_fixture", "templateSha256": digest(template),
                 "workspaceFile": str(workspace_file), "bootstrapCreateVerified": False,
                 "authorityIssued": False, **preparation})
        else:
            created = cli("create", ["bootstrap", "create-workspace", "--generator-id", "fabric-1.21.1",
                "--mod-name", "M1 Delivery", "--mod-id", "m1_delivery", "--workspace-folder", str(root),
                "--task-authorization", args.task_authorization, "--no-prompt", "true"])
            assert created["status"] == "committed", created
            workspace_file = Path(created["data"]["workspaceFile"]).resolve(strict=True)
            record["bootstrapCreateVerified"] = True
        assert workspace_file.parent == root.resolve()
        before = inventory(root)
        save("doctor", cli("doctor", ["headless", "--workspace", str(workspace_file), "doctor"], timeout=180))
        assert inventory(root) == before, "Doctor modified the workspace"
        save("doctor-nonmutation", {"scope": "workspace file hashes; writer lease presence and size",
                                   "files": len(before), "unchanged": True})
        tests = root / "src/gametest/java/copperbench/acceptance/ContractGameTests.java"
        tests.parent.mkdir(parents=True)
        shutil.copy2(fixture / "ContractGameTests.java", tests)
        shutil.copy2(fixture / "copperbench-tests.json", root / "copperbench-tests.json")
        correct_code = (fixture / "recovery_probe.java").read_text(encoding="utf-8")
        assert correct_code.count("return (count + 15) / 16;") == 1
        with open_workspace(workspace_file) as workspace:
            before = discovery_baseline(root, save) if template else inventory(root)
            revision = workspace.revision
            item = workspace.discover_field_contract("item")
            recipe = workspace.discover_field_contract("recipe")
            assert item["complete"] and recipe["complete"]
            assert item == workspace.field_contract("item") and recipe == workspace.field_contract("recipe")
            references = workspace.field_reference_options("recipe", "blocksitems", search="Items.STICK", limit=1)
            assert references["data"]["options"][0]["value"] == "Items.STICK"
            after = inventory(root)
            save("discovery-nonmutation", {"beforeRevision": revision, "afterRevision": workspace.revision,
                 "changedPaths": sorted(path for path in before.keys() | after.keys()
                                        if before.get(path) != after.get(path)), "before": before, "after": after})
            assert before == after and workspace.revision == revision, "Discovery wrote workspace state"
            save("discovery", {"item": item, "recipe": recipe, "references": references})
            workspace.create_mod_element(**item["minimalExample"])
            payload = copy.deepcopy(recipe["minimalExample"])
            payload["initialValues"]["recipeReturnStack"] = "CUSTOM:discovery_item"
            workspace.create_mod_element(**payload)
            code = workspace.create_mod_element(elementType="code", name="recovery_probe", initialValues={"code": correct_code})
            code_id = code["data"]["element"]["id"]
            preflight = workspace.preview_generation()
            save("preflight", preflight)
            assert preflight["data"]["status"] == "ready", preflight
            save("environment", workspace.query("get_workspace_environment"))
            terminal(workspace, "initial-build", workspace.build())
            update_code(workspace, code_id, correct_code.replace("return (count + 15) / 16;", "return missing_m1_symbol;"))
            failed = terminal(workspace, "controlled-compile-failure", workspace.build(), "failed", "JAVA_COMPILE_ERROR")
            assert "missing_m1_symbol" in json.dumps(failed)
            assert any("recovery_probe.java" in str(d) and "Line " in str(d) for d in failed["data"]["diagnostics"])
            update_code(workspace, code_id, correct_code)
            terminal(workspace, "repaired-build", workspace.build())
            accepted = terminal(workspace, "packaged-acceptance", workspace.run_game_tests())["data"]["task"]
            verification = accepted["verification"]
            assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
            assert verification["acceptanceExecuted"] == 5 and verification["failed"] == 0 and verification["skipped"] == 0
            assert verification["sourceCurrentAtCompletion"] is True
            save("verification", verification)
            exported = terminal(workspace, "verified-export", workspace.export_verified_artifact(accepted["id"]))["data"]["task"]["verifiedExport"]
            assert exported["status"] == "passed_current_input"
            assert exported["artifactSha256"] == verification["artifactSha256"]
            assert digest(Path(exported["exportDirectory"]) / "verified-mod.jar") == verification["artifactSha256"]
            saved_revision = workspace.revision
            old_process = workspace._process
        assert old_process.poll() == 0, "Native launcher did not close normally"
        with open_workspace(workspace_file) as workspace:
            recovered = workspace.get_task(accepted["id"])["data"]["task"]
            assert workspace._process.pid != old_process.pid and workspace.revision == saved_revision
            assert recovered["restoredFromHistory"] is True and recovered["verification"] == verification
            assert {"discovery_item", "discovery_recipe", "recovery_probe"} <= {e["name"] for e in workspace.list_mod_elements()}
            terminal(workspace, "reopened-verified-export", workspace.export_verified_artifact(accepted["id"]))
            save("reconnect", {"oldPid": old_process.pid, "oldExitCode": old_process.returncode,
                 "newPid": workspace._process.pid, "revision": saved_revision, "restoredTaskId": accepted["id"]})
        verify_conflict_copy(root, workspace_file.name, conflict_root, open_workspace, save, terminal)
        for path, expected in frozen_inputs.items():
            require(digest(path) == expected, "Candidate or fixture input changed during the run: " + str(path))
        record.update(status="passed", workspaceFile=str(workspace_file), verificationFile=str(output / "verification.json"))
    except Exception as error:
        record.update(status="failed", errorType=type(error).__name__, error=str(error))
        (output / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
        print(f"M3 failed; evidence: {output}", file=sys.stderr, flush=True)
    except KeyboardInterrupt:
        record.update(status="abandoned", errorType="KeyboardInterrupt", error="Interrupted by the operator")
    finally:
        for key, value in environment_before.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        if record["cache"].get("userHome"):
            try:
                after_cache = cache_inventory(Path(record["cache"]["userHome"]))
                save("cache-after", after_cache)
                record["cache"]["afterManifestSha256"] = digest(output / "cache-after.json")
                if record["status"] == "passed":
                    require(bool(after_cache["artifacts"]), "Successful isolated build retained no dependency payloads")
            except Exception as error:
                record["cacheEvidenceError"] = str(error)
                if record["status"] == "passed":
                    record.update(status="failed", errorType=type(error).__name__, error=str(error))
        record.update(completedAt=datetime.now(timezone.utc).isoformat(), elapsedSeconds=time.monotonic() - started)
        save("result", record)
        save("evidence-hashes", {p.name: digest(p) for p in sorted(output.iterdir()) if p.is_file() and p.name != "evidence-hashes.json"})
    return 0 if record["status"] == "passed" else 130 if record["status"] == "abandoned" else 1


if __name__ == "__main__":
    raise SystemExit(main())
