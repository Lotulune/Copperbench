"""Check frozen probe inputs and optionally build an isolated loader project.

The receipt describes dependency qualification, not Copperbench or gameplay acceptance.
Uses only the Python standard library and the repository's existing Gradle launcher.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from datetime import datetime, timezone


class ProbeFailure(ValueError):
    def __init__(self, code: str, detail: str):
        super().__init__(detail)
        self.code = code


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def member(root: Path, name: str) -> Path:
    if not isinstance(name, str):
        raise ProbeFailure("LOCK_PATH_INVALID", str(name))
    relative = PurePosixPath(name)
    if (not name or relative.is_absolute() or ".." in relative.parts
            or "\\" in name or ":" in name or str(relative) != name):
        raise ProbeFailure("LOCK_PATH_INVALID", name)
    result = root / relative
    if not result.resolve().is_relative_to(root.resolve()) or result.is_symlink():
        raise ProbeFailure("LOCK_PATH_INVALID", name)
    return result


def validate_lock(lock: dict) -> None:
    if not isinstance(lock, dict) or lock.get("schemaVersion") != 1:
        raise ProbeFailure("LOCK_VERSION_UNSUPPORTED", "Expected environment lock schema 1")
    if lock.get("generatorId") not in ("fabric-26.3", "neoforge-26.3"):
        raise ProbeFailure("TARGET_UNSUPPORTED", str(lock.get("generatorId")))
    versions = lock.get("versions", {})
    required = {"minecraft", "gradle", "loader", "buildPlugin"}
    if lock["generatorId"].startswith("fabric-"):
        required.add("fabricApi")
    if not isinstance(versions, dict) or not required.issubset(versions) or versions["minecraft"] != "26.3":
        raise ProbeFailure("VERSIONS_INCOMPLETE", "Exact Minecraft and loader toolchain pins are required")
    for component, value in versions.items():
        if (not isinstance(value, str) or not re.fullmatch(r"[0-9][0-9A-Za-z.+_-]*", value)
                or value.endswith("+") or "SNAPSHOT" in value.upper() or value.lower().startswith("latest")):
            raise ProbeFailure("DYNAMIC_VERSION_FORBIDDEN", f"{component}: {value}")
    if not re.fullmatch(r"[0-9a-f]{40}", lock.get("generatorSource", {}).get("commit", "")):
        raise ProbeFailure("SOURCE_IDENTITY_MISSING", "A fixed generator source commit is required")
    artifacts = lock.get("artifacts", [])
    if not isinstance(artifacts, list) or not artifacts or not isinstance(lock.get("projectFiles"), dict) or not lock["projectFiles"]:
        raise ProbeFailure("LOCK_INPUTS_MISSING", "Artifact and project source digests are required")
    names = set()
    for artifact in artifacts:
        if not isinstance(artifact, dict):
            raise ProbeFailure("LOCK_ARTIFACT_INVALID", "Artifact descriptor must be an object")
        name = artifact.get("file", "")
        member(Path.cwd(), name)
        if name in names:
            raise ProbeFailure("LOCK_ARTIFACT_DUPLICATE", name)
        names.add(name)
        if (not re.fullmatch(r"[0-9a-f]{64}", artifact.get("localSha256", ""))
                or not isinstance(artifact.get("size"), int) or artifact["size"] < 1
                or not artifact.get("url", "").startswith("https://")):
            raise ProbeFailure("LOCK_ARTIFACT_INVALID", name)


def verify_inputs(lock: dict, cache: Path, project: Path) -> dict:
    validate_lock(lock)
    checked = {}
    for artifact in lock["artifacts"]:
        path = member(cache, artifact["file"])
        if not path.is_file():
            raise ProbeFailure("DEPENDENCY_MISSING", artifact["file"])
        if path.stat().st_size != artifact["size"] or sha256(path) != artifact["localSha256"]:
            raise ProbeFailure("DEPENDENCY_DIGEST_MISMATCH", artifact["file"])
        if artifact.get("upstreamSha1"):
            with path.open("rb") as stream:
                if hashlib.file_digest(stream, "sha1").hexdigest() != artifact["upstreamSha1"]:
                    raise ProbeFailure("UPSTREAM_DIGEST_MISMATCH", artifact["file"])
        checked[artifact["file"]] = artifact["localSha256"]
    for name, expected in lock["projectFiles"].items():
        path = member(project, name)
        if not path.is_file() or sha256(path) != expected:
            raise ProbeFailure("PROBE_SOURCE_CHANGED", name)
    return checked


def dependency_graph_files(lock: dict) -> tuple[str, ...]:
    files = ("gradle.lockfile", "gradle/verification-metadata.xml")
    return files + (("gradle/linux.lockfile",) if lock.get("generatorId") == "neoforge-26.3" else ())


def verify_dependency_graph(lock: dict, project: Path) -> None:
    for name in dependency_graph_files(lock):
        expected = lock.get("dependencyGraph", {}).get(name)
        path = member(project, name)
        if not expected or not path.is_file():
            raise ProbeFailure("DEPENDENCY_GRAPH_UNLOCKED", "Freeze and review the dependency graph before replay")
        if sha256(path) != expected:
            raise ProbeFailure("DEPENDENCY_GRAPH_CHANGED", name)


def inspect_java(home: Path, expected: dict, role: str) -> dict:
    try:
        values = dict(line.strip().split("=", 1) for line in (home / "release").read_text(encoding="utf-8").splitlines()
                      if "=" in line)
        values = {key: value.strip('"') for key, value in values.items()}
        executable = home / "bin" / ("java.exe" if os.name == "nt" else "java")
        result = {"role": role, "vendor": values.get("IMPLEMENTOR"),
                  "runtimeVersion": values.get("JAVA_RUNTIME_VERSION"), "javaSha256": sha256(executable),
                  "jcef": "jcef" in values.get("MODULES", "").split()}
    except OSError as error:
        raise ProbeFailure("JAVA_MISSING", f"{role}: {error}") from error
    for key in ("vendor", "runtimeVersion", "javaSha256"):
        if result[key] != expected.get(key):
            raise ProbeFailure("JAVA_IDENTITY_MISMATCH", f"{role}: {key}")
    if role == "product" and not result["jcef"]:
        raise ProbeFailure("PRODUCT_JCEF_MISSING", "The product runtime does not declare JCEF")
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("fabric-26.3", "neoforge-26.3"), required=True)
    parser.add_argument("--lock-file", type=Path, help="Inspect an explicit saved lock instead of the project's default")
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--workspace-java-home", type=Path, required=True)
    parser.add_argument("--product-java-home", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--freeze-dependencies", action="store_true")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args(argv)
    project = Path(__file__).resolve().parent / args.target
    repository = project.parents[2]
    args.output.mkdir(parents=True, exist_ok=True)
    receipt = {"schemaVersion": 1, "generatorId": args.target, "startedAt": datetime.now(timezone.utc).isoformat(),
               "status": "failed", "scope": "dependency_qualification", "offline": args.offline}
    try:
        lock_path = args.lock_file or project / "environment.lock.json"
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
        if lock["generatorId"] != args.target:
            raise ProbeFailure("TARGET_MISMATCH", "Lock target differs from the requested target")
        receipt["lockSha256"] = sha256(lock_path)
        receipt["artifacts"] = verify_inputs(lock, args.cache, project)
        for name, expected in lock["wrapperFiles"].items():
            path = member(repository, name)
            if not path.is_file() or sha256(path) != expected:
                raise ProbeFailure("GRADLE_WRAPPER_CHANGED", name)
        receipt["workspaceJava"] = inspect_java(args.workspace_java_home, lock["workspaceJava"], "workspace")
        receipt["productJava"] = inspect_java(args.product_java_home, lock["productJava"], "product")
        if args.build:
            if os.name == "nt":
                command = ["pwsh", "-NoProfile", "-File", str(repository / "scripts/run-gradle-external.ps1")]
            else:
                command = [str(repository / "gradlew")]
            command += ["--no-daemon", "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx1024m",
                        "--project-dir", str(project), "build", "--rerun-tasks"]
            if args.offline:
                command.append("--offline")
            if args.freeze_dependencies:
                command += ["--write-locks", "--write-verification-metadata", "sha256"]
            else:
                verify_dependency_graph(lock, project)
            environment = os.environ.copy()
            environment["JAVA_HOME"] = str(args.workspace_java_home.resolve())
            environment["PATH"] = str(args.workspace_java_home.resolve() / "bin") + os.pathsep + environment.get("PATH", "")
            with (args.output / "build.log").open("w", encoding="utf-8") as log:
                completed = subprocess.run(command, cwd=repository, env=environment, stdout=log, stderr=subprocess.STDOUT)
            receipt["buildExitCode"] = completed.returncode
            receipt["rerunTasks"] = True
            if completed.returncode:
                raise ProbeFailure("BUILD_FAILED", "See build.log for the exact unresolved component or compiler error")
            receipt["artifactAfterBuild"] = verify_inputs(lock, args.cache, project)
            jars = [path for path in (project / "build/libs").glob("*.jar") if not path.name.endswith("-sources.jar")]
            if len(jars) != 1:
                raise ProbeFailure("BUILD_ARTIFACT_MISSING", "Expected exactly one probe mod JAR")
            receipt["probeJar"] = {"file": jars[0].name, "sha256": sha256(jars[0])}
            receipt["dependencyLocks"] = {name: sha256(project / name)
                                          for name in dependency_graph_files(lock)}
            receipt["status"] = "graph_frozen" if args.freeze_dependencies else "qualified"
        else:
            receipt["status"] = "inputs_verified"
    except (ProbeFailure, OSError, ValueError, KeyError, TypeError) as error:
        receipt["reasonCode"] = error.code if isinstance(error, ProbeFailure) else "PROBE_INPUT_INVALID"
        receipt["detail"] = str(error)
    receipt["finishedAt"] = datetime.now(timezone.utc).isoformat()
    (args.output / "result.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: receipt[key] for key in ("generatorId", "status", "reasonCode", "detail") if key in receipt},
                     ensure_ascii=False))
    return 1 if receipt["status"] == "failed" else 0


if __name__ == "__main__":
    sys.exit(main())
