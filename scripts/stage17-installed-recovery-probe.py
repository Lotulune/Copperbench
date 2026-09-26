"""Prepare a disposable restore fixture, then verify it using a user-issued restore authorization."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "verify"])
    for name in ("product", "launcher", "workspace", "manifest", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--task-authorization", help="Existing user-issued grant with restore capability for this fixture")
    args = parser.parse_args()
    product, target, manifest, output = (getattr(args, name).resolve() for name in ("product", "workspace", "manifest", "output"))
    if output.exists() or product == target or product in target.parents or target in product.parents:
        parser.error("Use a new evidence file and a disposable workspace separate from the product.")
    if manifest == target or target in manifest.parents or target in output.parents:
        parser.error("Keep evidence outside the workspace being restored.")
    if args.mode == "prepare" and (target.exists() or manifest.exists()):
        parser.error("Preparation requires a new fixture and manifest.")
    if args.mode == "verify" and not args.task_authorization:
        parser.error("Verification requires a user-issued restore authorization; this script cannot approve it.")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, NativeApiError
    document = target / "wayfinder_bell.mcreator"
    jar = hashlib.sha256((product / "lib/copperbench.jar").read_bytes()).hexdigest()
    proof = {"scope": "Disposable fixture recovery, authorization guards and durable revisions; no gameplay assertion", "jarSha256": jar, "checks": []}

    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None

    def hashes(paths):
        return {path: digest(target / path) for path in paths}

    def reject(call, expected, paths):
        revision, before = ws.revision, hashes(paths)
        try:
            call()
        except NativeApiError as error:
            assert expected in [d["code"] for d in error.details["diagnostics"]], error.details
            assert ws.revision == revision and hashes(paths) == before
            proof["checks"].append({"code": expected, "revisionAndFilesUnchanged": True})
        else:
            raise AssertionError("Expected restore to be rejected")

    try:
        if args.mode == "prepare":
            shutil.copytree(product / "examples/agent-native/wayfinder-bell", target)
            package = json.loads(document.read_text())["workspaceSettings"]["modElementsPackage"]
            initial = f"package {package}; public class recovery_manual {{ public static final int VALUE = 1; }}\n"
            with Workspace.open(document, launcher=args.launcher.resolve(), cwd=product) as ws:
                block_id = ws.create_mod_element(elementType="block", name="recovery_block", initialValues={"hardness": 3, "resistance": 6})["data"]["element"]["id"]
                code_id = ws.create_mod_element(elementType="code", name="recovery_manual", initialValues={"code": initial})["data"]["element"]["id"]
                source = next((target / "src/main/java").rglob("recovery_manual.java")).relative_to(target).as_posix()
                definition = "elements/recovery_block.mod.json"
                baseline = hashes([source, definition])
                point = ws.command("create_recovery_point", label="PRD17 disposable restore baseline")
                point_id = point["recoveryPointId"]
                assert ws.revision == 2
                ws.update_mod_element(elementId=block_id, changes=[{"path": "/hardness", "value": 5}])
                ws.update_mod_element(elementId=code_id, changes=[{"path": "/code", "value": initial.replace("= 1", "= 2")}])
                ws.create_mod_element(elementType="function", name="after_point", initialValues={"commands": []})
                (target / "restore-fixture-only.txt").write_text("Created after the baseline", encoding="utf-8")
                (target / ".copperbench/restore-untouched.txt").write_text("Excluded marker", encoding="utf-8")
                paths = [document.name, definition, source, "elements/after_point.mod.json", "restore-fixture-only.txt", ".copperbench/restore-untouched.txt"]
                preview = ws.query("preview_recovery_restore", recoveryPointId=point_id)["data"]
                preview_paths = {change["path"] for change in preview["changes"]}
                assert {source, definition, "restore-fixture-only.txt"}.issubset(preview_paths)
                assert ".copperbench/restore-untouched.txt" not in preview_paths
                reject(lambda: ws.command("restore_recovery_point", recoveryPointId=point_id), "USER_APPROVAL_REQUIRED", paths)
                reject(lambda: ws.command("restore_recovery_point", recoveryPointId=point_id, userApproved=True), "USER_APPROVAL_REQUIRED", paths)
                data = {"jarSha256": jar, "workspace": str(target), "pointId": point_id, "blockId": block_id, "codeId": code_id,
                        "source": source, "definition": definition, "paths": paths, "baseline": baseline, "modified": hashes(paths), "revision": ws.revision, "preview": preview}
                assert ws.revision == 5
            # Closing may save routine workspace metadata; record the durable pre-restore state.
            data["modified"] = hashes(paths)
            manifest.parent.mkdir(parents=True, exist_ok=True)
            with manifest.open("x", encoding="utf-8") as stream:
                json.dump(data, stream, indent=2)
            proof.update(status="prepared", requiresRestoreAuthorization=True, manifest=str(manifest), preview=preview)
        else:
            data = json.loads(manifest.read_text(encoding="utf-8"))
            assert data["workspace"] == str(target) and data["jarSha256"] == jar
            with Workspace.open(document, launcher=args.launcher.resolve(), cwd=product, task_authorization_id=args.task_authorization) as ws:
                assert ws.revision == data["revision"] == 5
                assert hashes(data["paths"]) == data["modified"], "Fixture changed after review"
                reject(lambda: ws.command("restore_recovery_point", recoveryPointId=data["pointId"], expected_revision=4), "WORKSPACE_REVISION_CONFLICT", data["paths"])
                reject(lambda: ws.command("restore_recovery_point", recoveryPointId="0" * 40), "RECOVERY_POINT_RESTORE_FAILED", data["paths"])
                restored = ws.command("restore_recovery_point", recoveryPointId=data["pointId"])
                assert restored["status"] == "committed" and ws.revision == 6
                assert hashes(data["baseline"]) == data["baseline"]
                assert not (target / "elements/after_point.mod.json").exists() and not (target / "restore-fixture-only.txt").exists()
                assert (target / ".copperbench/restore-untouched.txt").read_text() == "Excluded marker"
                assert ws.query("get_mod_element_editor", elementId=data["codeId"])["data"]["element"]["ownership"] == "manual"
                points = ws.query("get_history")["data"]["recoveryPoints"]
                safety = next(point for point in points if point["label"] == "Before restoring " + data["pointId"])
                undone = ws.command("restore_recovery_point", recoveryPointId=safety["id"])
                assert undone["status"] == "committed" and ws.revision == 7
                for path, expected in data["modified"].items():
                    if path != document.name:
                        assert digest(target / path) == expected, path
                proof.update(restoredRevision=6, undoRevision=7, safetyPointId=safety["id"], manualSourceRestored=True, ignoredMarkerPreserved=True)
            with Workspace.open(document, launcher=args.launcher.resolve(), cwd=product) as ws:
                assert ws.revision == 7
                assert json.loads((target / data["definition"]).read_text())["definition"]["hardness"] == 5
                assert digest(target / data["source"]) == data["modified"][data["source"]]
                assert ws.query("get_mod_element_editor", elementId=data["codeId"])["data"]["element"]["ownership"] == "manual"
            proof.update(status="passed", reopened=True)
    except Exception as error:
        proof.update(status="failed", error=str(error))
        if isinstance(error, NativeApiError):
            proof["errorDetails"] = error.details
        raise
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("x", encoding="utf-8") as stream:
            json.dump(proof, stream, ensure_ascii=False, indent=2)
        print(json.dumps({"status": proof["status"], "output": str(output)}))


if __name__ == "__main__":
    main()
