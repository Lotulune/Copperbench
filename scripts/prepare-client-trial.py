#!/usr/bin/env python3
"""Prepare a fresh product workspace that loads only verified external mod JARs.

This checks artifact identity. Client behavior still needs actual input and observation.
Uses the public packaged bootstrap entry, never product implementation classes.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_verification(path):
    path = Path(path).resolve(strict=True)
    if path.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("Verification input is too large")
    text = path.read_text(encoding="utf-8-sig").strip()
    try:
        document = json.loads(text)
    except json.JSONDecodeError:
        document = json.loads(text.splitlines()[-1])
    verification = document.get("task", {}).get("verification", document)
    if (verification.get("status") != "passed"
            or verification.get("mode") != "packaged_jar"
            or verification.get("sourceCurrentAtCompletion") is not True
            or verification.get("processExitCode") != 0
            or verification.get("failed") != 0
            or verification.get("acceptanceExecuted", 0) < verification.get("minimumTests", 1)):
        raise ValueError("A passed, current-source packaged-JAR verification is required")
    environment = verification.get("environment", {})
    if environment.get("generatorId") != "fabric-1.21.1":
        raise ValueError("This client trial currently supports Fabric 1.21.1 only")
    artifact = Path(verification["artifactPath"]).resolve(strict=True)
    report = Path(verification["reportPath"]).resolve(strict=True)
    if digest(artifact) != verification["artifactSha256"] or digest(report) != verification["reportSha256"]:
        raise ValueError("The tested JAR or server report has changed")
    with zipfile.ZipFile(artifact) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
    if not re.fullmatch(r"[a-z][a-z0-9_.-]{1,63}", str(metadata.get("id", ""))) or metadata["id"] == "client_trial_host":
        raise ValueError("Tested mod identity conflicts with the client host")
    return {
        "modId": metadata["id"], "sourceArtifact": str(artifact),
        "artifactSha256": verification["artifactSha256"],
        "sourceSnapshotSha256": verification["sourceSnapshot"]["sha256"],
        "serverVerification": str(path), "serverVerificationSha256": digest(path),
        "serverReportSha256": verification["reportSha256"],
        "serverAcceptanceExecuted": verification["acceptanceExecuted"],
    }


def check(manifest_path):
    manifest_path = Path(manifest_path).resolve(strict=True)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    root = manifest_path.parent
    expected = {entry["deployedFile"] for entry in manifest["artifacts"]}
    actual = {str(path.relative_to(root)).replace("\\", "/") for path in (root / "run/mods").glob("*.jar")}
    if actual != expected:
        raise ValueError("The client mod directory no longer matches the frozen artifact set")
    for entry in manifest["artifacts"]:
        deployed = (root / entry["deployedFile"]).resolve(strict=True)
        if not deployed.is_relative_to(root) or digest(deployed) != entry["artifactSha256"]:
            raise ValueError("A deployed client artifact changed or escaped the host")
    return {"status": "passed", "scope": "client_artifact_identity_only",
            "clientBehaviorVerified": False, "artifacts": len(expected),
            "manifestSha256": digest(manifest_path)}


def prepare(product, root, grant, reports):
    root = Path(root).absolute()
    if root.exists():
        raise ValueError("Client host must be a new directory; existing workspaces are not overwritten")
    artifacts = [read_verification(path) for path in reports]
    if len({entry["modId"] for entry in artifacts}) != len(artifacts):
        raise ValueError("Duplicate mod IDs in the client artifact set")
    command = [str(Path(product).resolve(strict=True)), "bootstrap", "create-workspace",
               "--generator-id", "fabric-1.21.1", "--mod-name", "Client Trial Host",
               "--mod-id", "client_trial_host", "--workspace-folder", str(root),
               "--task-authorization", grant, "--no-prompt", "true"]
    completed = subprocess.run(command, capture_output=True, encoding="utf-8", errors="strict", timeout=1200)
    if completed.returncode:
        sys.stderr.write(completed.stderr)
        sys.stderr.write(completed.stdout)
        raise ValueError(f"Product bootstrap failed with exit {completed.returncode}; no approval was synthesized")
    created = json.loads(completed.stdout)
    if created.get("status") != "committed":
        raise ValueError("Product did not confirm workspace creation")
    mods = root / "run/mods"
    mods.mkdir(parents=True, exist_ok=False)
    for entry in artifacts:
        target = mods / (entry["modId"] + "-verified.jar")
        shutil.copyfile(entry["sourceArtifact"], target)
        if digest(target) != entry["artifactSha256"]:
            raise ValueError("Artifact changed during deployment")
        entry["deployedFile"] = target.relative_to(root).as_posix()
    manifest = {
        "schemaVersion": "1.0", "kind": "packaged-client-trial",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "generatorId": "fabric-1.21.1", "workspaceFile": created["data"]["workspaceFile"],
        "productExecutable": str(Path(product).resolve()), "productExeSha256": digest(product),
        "artifacts": artifacts, "clientBehaviorVerified": False,
        "nextStep": "Use the product run-client operation. Record actual input, client feedback, normal save/quit and rejoin, then check artifact hashes again.",
    }
    manifest_path = root / "client-trial.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return {**check(manifest_path), "status": "prepared", "manifestPath": str(manifest_path),
            "workspaceFile": manifest["workspaceFile"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", metavar="MANIFEST")
    parser.add_argument("--product")
    parser.add_argument("--workspace-folder")
    parser.add_argument("--task-authorization")
    parser.add_argument("--verification", action="append", default=[])
    args = parser.parse_args()
    try:
        if args.check:
            result = check(args.check)
        else:
            if not all([args.product, args.workspace_folder, args.task_authorization, args.verification]):
                parser.error("Provide --product, --workspace-folder, --task-authorization and at least one --verification")
            result = prepare(args.product, args.workspace_folder, args.task_authorization, args.verification)
        print(json.dumps(result, ensure_ascii=False))
    except (ValueError, OSError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(json.dumps({"status": "failed", "message": str(error)}, ensure_ascii=False))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
