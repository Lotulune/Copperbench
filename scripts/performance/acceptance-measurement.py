"""Bounded public packaged-SDK build/client observation; never supplies UI input or grants."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
MARKER = "CB_ACCEPTANCE_STAGE "


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def cold_host_entry(product: Path, expected: dict) -> dict:
    java = product / "jdk/bin/java.exe"
    runtime_sha256 = digest(java)
    if runtime_sha256 != expected["jdk/bin/java.exe"]["sha256"]:
        raise ValueError("Frozen candidate bytes changed: jdk/bin/java.exe")
    return {"entry": "bundled-jbr-and-jar-public-sdk-argv",
            "argv": [str(java), "--add-opens=java.base/java.lang=ALL-UNNAMED",
                     "--enable-native-access=ALL-UNNAMED,jcef", "-Dcopperbench.productShell=true",
                     "-cp", str(product / "lib/*"), "net.mcreator.Launcher"],
            "runtime": {"path": "jdk/bin/java.exe", "sha256": runtime_sha256},
            "exeStdioAcceptance": "Not tested by this bundled-Java run; prior EXE stdio failure remains separate evidence. No automatic fallback."}


def child_path(root: Path, value: str) -> Path:
    path = Path(value).resolve()
    if path == root or not path.is_relative_to(root):
        raise ValueError("Acceptance data must remain inside its explicit isolated root")
    return path


def cache_inventory(root: Path) -> dict:
    files = []
    if root.exists():
        for path in sorted(root.rglob("*")):
            if path.is_symlink():
                raise ValueError("The isolated Gradle cache contains an external link")
            if path.is_file():
                files.append({"path": path.relative_to(root).as_posix(), "bytes": path.stat().st_size})
    return {"files": files, "fileCount": len(files), "bytes": sum(item["bytes"] for item in files),
            "scope": "Materialized cache files, not network requests or transferred bytes"}


def sanitized(value, paths=()):
    if isinstance(value, dict):
        return {key: "<redacted>" if re.fullmatch(r"token|password|secret|authorization|api.?key", key, re.I)
                else sanitized(item, paths) for key, item in value.items()}
    if isinstance(value, list):
        return [sanitized(item, paths) for item in value]
    if isinstance(value, str):
        for path, label in sorted(paths, key=lambda pair: len(str(pair[0])), reverse=True):
            for spelling in {str(path), str(path).replace("\\", "/"), str(path).replace("/", "\\")}:
                value = value.replace(spelling, label)
        value = re.sub(r"(https?://)[^/\s]+@", r"\1<redacted>@", value)
        value = re.sub(r"(?i)(Bearer\s+)\S+", r"\1<redacted>", value)
        value = re.sub(r"(?i)((?:token|password|secret|api[_-]?key)=)[^\s&]+", r"\1<redacted>", value)
    return value


class StageCapture:
    """Separate exact Gradle-process durations from SDK receipt arrival times."""
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.started = clock()
        self.cursor = 0
        self.log_count = 0
        self.repeated_logs = 0
        self.events = []
        self.downloads = []
        self.poll_count = 0
        self.last_task = None

    def receive(self, receipt: dict) -> None:
        arrival = (self.clock() - self.started) * 1000
        self.poll_count += 1
        data = receipt["data"]
        self.last_task = data["task"]
        for entry in data["logs"]:
            self.log_count += 1
            if entry["sequence"] <= self.cursor:
                self.repeated_logs += 1
                raise ValueError("Task logs repeated or regressed; the raw poll is retained")
            self.cursor = entry["sequence"]
            line = entry["text"]
            if MARKER in line:
                event = json.loads(line.split(MARKER, 1)[1])
                elapsed = event.get("elapsedMs")
                if elapsed is not None and (not isinstance(elapsed, (int, float)) or not math.isfinite(elapsed) or elapsed < 0):
                    raise ValueError("A Gradle stage contains an invalid monotonic duration")
                self.events.append({"logSequence": entry["sequence"], "sdkObservedAfterMs": arrival, **event})
            if re.search(r"\bDownloading\b", line, re.I):
                category = "dependency" if re.search(r"\.(jar|pom|module)\b|Minecraft", line, re.I) else "wrapper_or_unknown"
                self.downloads.append({"logSequence": entry["sequence"], "sdkObservedAfterMs": arrival,
                                       "category": category, "line": line})

    def summary(self, cache_root: Path, allowed_resolution_roots=()) -> dict:
        homes = [event["gradleUserHome"] for event in self.events if event.get("phase") == "settings"]
        files = {item["path"]: item["bytes"] for event in self.events if event.get("phase") == "resolution"
                 for item in event.get("files", [])}
        allowed = [Path(path).resolve() for path in allowed_resolution_roots] or [cache_root]
        external = [path for path in files if not any(Path(path).resolve().is_relative_to(root) for root in allowed)]
        return {"pollCount": self.poll_count, "receivedLogEntries": self.log_count,
                "repeatedLogEntries": self.repeated_logs, "cursor": self.cursor,
                "reportedGradleUserHomes": homes,
                "gradleHomeConfirmed": bool(homes) and all(Path(home).resolve() == cache_root for home in homes),
                "resolutionCount": sum(event.get("phase") == "resolution" and event.get("event") == "end" for event in self.events),
                "taskStageCount": sum(event.get("phase") == "task" and event.get("event") == "end" for event in self.events),
                "downloadLogCount": len(self.downloads),
                "dependencyDownloadLogCount": sum(item["category"] == "dependency" for item in self.downloads),
                "distinctResolutionFileCount": len(files), "resolutionFileBytes": sum(files.values()),
                "unexpectedExternalResolutionPaths": external,
                "stageEvents": self.events, "downloadObservations": self.downloads,
                "networkTransferDurationMs": None, "networkTransferredBytes": None,
                "timingScopes": {"sdk": "Python time.monotonic: request/receipt/poll observation, including IPC and polling delay",
                                 "gradle": "Stage elapsedMs uses System.nanoTime inside the Gradle process; overlapping resolution scopes are not summed",
                                 "downloads": "Actual download log messages and receipt arrival times; HTTP start/end/byte metrics unavailable",
                                 "wrapper": "Wrapper download precedes init script loading; only download logs cover it",
                                 "ui": "not measured; no desktop controller in this helper"}}


def run(config: dict) -> int:
    isolation = Path(config["isolationRoot"]).resolve()
    allowed = (ROOT / "output/minecraft-validation").resolve()
    if isolation == allowed or not isolation.is_relative_to(allowed):
        raise ValueError("Use an isolated acceptance directory under output/minecraft-validation/")
    workspace = child_path(isolation, config["workspace"])
    profile = child_path(isolation, config["userRoot"])
    cache = child_path(isolation, config["gradleCache"])
    output = child_path(isolation, config["output"])
    locations = [workspace.parent, profile, cache, output]
    for index, first in enumerate(locations):
        for second in locations[index + 1:]:
            if first == second or first.is_relative_to(second) or second.is_relative_to(first):
                raise ValueError("Workspace, profile, cache and evidence output directories must be disjoint")
    if not workspace.is_file() or workspace.suffix != ".mcreator":
        raise ValueError("Prepare an isolated .mcreator workspace through the existing product path first")
    if output.exists():
        raise ValueError("Use a new evidence output directory; attempts are never overwritten")
    mode = config["mode"]
    if mode not in {"cold-build", "build", "client"}:
        raise ValueError("Supported modes: cold-build, build, client")
    timeout = config.get("timeoutSeconds", 1800)
    if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or not 1 <= timeout <= 3600:
        raise ValueError("Choose a bounded task wait of 1 to 3600 seconds")
    if mode == "cold-build":
        if cache.exists() or profile.exists():
            raise ValueError("Cold build needs NEW cache and user/config roots, not reused directories")
        pool = isolation / "distribution-pool"
        if pool.exists() and any(pool.iterdir()):
            raise ValueError("External Gradle distribution pool must be absent or empty for the cold observation")
        if (workspace.parent / ".gradle").exists() or (workspace.parent / "build").exists():
            raise ValueError("Cold workspace must not contain previous Gradle/build caches")
        if (workspace.parent / ".mcreator/generatorGradleCache").exists():
            raise ValueError("Cold workspace must not import a previous generator classpath cache")
        if config.get("resumeTaskId"):
            raise ValueError("A resumed task is not a new cold-cache build sample")

    identity = json.loads(Path(config["candidateIdentity"]).read_text(encoding="utf-8-sig"))
    product = Path(identity["productRoot"]).resolve(strict=True)
    expected = {item["path"]: item for item in identity["files"]}
    for relative in ("copperbench.exe", "lib/copperbench.jar", "sdk/python/copperbench.py", "sdk/python/copperbench_native.py"):
        if digest(product / relative) != expected[relative]["sha256"]:
            raise ValueError(f"Frozen candidate bytes changed: {relative}")
    independent_entry = cold_host_entry(product, expected) if mode == "cold-build" else None
    host_evidence = None
    if mode != "cold-build":
        host_path = Path(config["hostProcessEvidence"]).resolve(strict=True)
        host = json.loads(host_path.read_text(encoding="utf-8-sig"))
        if (Path(host["productRoot"]).resolve() != product
                or host["applicationSha256"] != expected["lib/copperbench.jar"]["sha256"]
                or Path(host["userRoot"]).resolve() != profile or Path(host["gradleCache"]).resolve() != cache):
            raise ValueError("Parent's actual desktop launch receipt differs from the frozen candidate/profile/cache")
        host_evidence = {"path": str(host_path), "sha256": digest(host_path),
                         "scope": "Parent retained launch/process receipt; parent also verifies the live JVM/window before input"}
    output.mkdir(parents=True)
    profile.mkdir(parents=True, exist_ok=True)
    cache.mkdir(parents=True, exist_ok=True)
    paths = [(isolation, "<acceptance-root>"), (product, "<candidate-root>"), (Path.home(), "<original-user-home>")]

    def save(name, value):
        (output / f"{name}.json").write_text(json.dumps(sanitized(value, paths), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    before_cache = cache_inventory(cache)
    save("cache-before", before_cache)
    init = cache / "init.d/acceptance-stage-events.gradle"
    init.parent.mkdir(exist_ok=True)
    init_bytes = Path(__file__).with_name("acceptance-stage-events.init.gradle").read_bytes()
    if init.exists() and init.read_bytes() != init_bytes:
        raise ValueError("Do not replace different isolated stage instrumentation")
    if not init.exists():
        init.write_bytes(init_bytes)
    measurement_properties = cache / "gradle.properties"
    properties = "org.gradle.logging.level=info\norg.gradle.configuration-cache=false\n"
    if measurement_properties.exists() and measurement_properties.read_text(encoding="utf-8") != properties:
        raise ValueError("Do not replace different isolated Gradle user properties")
    if not measurement_properties.exists():
        measurement_properties.write_text(properties, encoding="utf-8")
    # Child process environment only; no persistent/global configuration is changed.
    for key in ("_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "GRADLE_OPTS", "COPPERBENCH_STAGE5_GRADLE_EXECUTABLE"):
        os.environ.pop(key, None)
    os.environ.update(USERPROFILE=str(profile), COPPERBENCH_HOME=str(profile / ".copperbench"),
                      GRADLE_USER_HOME=str(cache), COPPERBENCH_GRADLE_USER_HOME=str(cache),
                      JAVA_TOOL_OPTIONS=f'-Duser.home="{profile}" -Dcopperbench.gradle.user.home="{cache}" -Dcopperbench.gradle.pool="{isolation / "distribution-pool"}"', PYTHONUTF8="1")
    sys.path.insert(0, str(product / "sdk/python"))
    from copperbench import Workspace, ClientMetrics

    capture = StageCapture()
    metrics = ClientMetrics()
    manifest = {"schemaVersion": "1.0", "kind": "packaged-public-sdk-measurement", "mode": mode,
                "candidate": {"archiveSha256": identity["archiveSha256"], "sourceIdentity": identity["sourceIdentity"],
                              "applicationSha256": expected["lib/copperbench.jar"]["sha256"],
                              "executableSha256": expected["copperbench.exe"]["sha256"]},
                "workspaceDescriptorSha256": digest(workspace), "configuredGradleHome": str(cache),
                "hostProcessEvidence": host_evidence,
                "independentHostEntry": independent_entry,
                "status": "running", "visualBehaviorVerified": False,
                "exclusions": ["No UI input, EULA acceptance, grant issuance or shared-cache cleanup",
                               "Wrapper/OS caches, package runtime and host/network load are separate conditions",
                               "Cache materialization is not network byte accounting; unavailable stage metrics remain null",
                               "An SDK receipt alone cannot establish rendered client behavior or normal application exit"]}
    save("manifest", manifest)
    task_id = None
    terminal = None
    session = None
    started = time.monotonic()
    try:
        session = (Workspace.open(workspace, launcher=independent_entry["argv"], cwd=product,
                                  startup_timeout=180, request_timeout=120, metrics=metrics)
                   if mode == "cold-build" else Workspace.connect(workspace, request_timeout=120, metrics=metrics))
        with session:
            save("handshake", session.require_schema())
            environment = session.get_workspace_environment()
            save("environment", environment)
            if environment["data"]["generator"]["id"] != config["generatorId"]:
                raise ValueError("The actual workspace generator differs from the task card")
            elements = list(session.list_mod_elements(fields=["id", "name", "type"], limit=200))
            save("fixture-elements", elements)
            if config.get("expectedElementName") and not any(item["name"] == config["expectedElementName"] for item in elements):
                raise ValueError("Expected real fixture element is missing")
            if config.get("resumeTaskId"):
                task_id = config["resumeTaskId"]
                accepted = session.get_task(task_id)
                kind = accepted["data"]["task"]["kind"]
            else:
                accepted = session.run_client() if mode == "client" else session.build()
                task_id = accepted["task"]["id"]
                kind = accepted["task"]["kind"]
            save("accepted", accepted)
            if kind != ("run_client" if mode == "client" else "build"):
                raise ValueError("The original task kind differs from the requested lifecycle")
            print(json.dumps({"taskId": task_id, "mode": mode, "state": "observing"}), flush=True)
            deadline = time.monotonic() + timeout
            while True:
                receipt = session.get_task(task_id, capture.cursor)
                with (output / "polls.jsonl").open("a", encoding="utf-8") as stream:
                    stream.write(json.dumps(sanitized(receipt, paths), ensure_ascii=False) + "\n")
                capture.receive(receipt)
                save("latest", receipt)
                save("checkpoint", {"taskId": task_id, "afterLogSequence": capture.cursor, "state": capture.last_task["state"]})
                if capture.last_task["state"] not in {"queued", "running"}:
                    terminal = capture.last_task["state"]
                    save("finished", receipt)
                    break
                if time.monotonic() >= deadline:
                    raise TimeoutError("Wait ended without replay. The desktop task may be resumed; independent cold-host cleanup can end its in-flight build and is not normal-exit evidence.")
                time.sleep(1)
        if mode == "client":
            log = workspace.parent / "run/logs/latest.log"
            if log.is_file():
                (output / "client-final.log").write_text(sanitized(log.read_text(encoding="utf-8", errors="replace"), paths), encoding="utf-8")
        if terminal != "succeeded":
            raise RuntimeError(f"Original task ended as {terminal}; retain network/build/authorization causes in raw receipts")
        manifest["status"] = "task_succeeded"
    except Exception as error:
        manifest["status"] = "timeout_unverified" if isinstance(error, TimeoutError) else "failed"
        save("failure", {"type": type(error).__name__, "code": getattr(error, "code", None), "message": str(error),
                         "details": getattr(error, "details", None),
                         "taskId": task_id, "lastKnownState": capture.last_task, "writeReplay": False})
    finally:
        cleanup = "session not opened"
        if session is not None:
            try:
                session.close()
                cleanup = "public SDK close/disconnect completed; independent host normal exit not independently observed"
            except Exception as error:
                save("cleanup-failure", {"type": type(error).__name__, "message": str(error)})
                cleanup = "failed"
                manifest["status"] = "failed"
        after_cache = cache_inventory(cache)
        save("cache-after", after_cache)
        stages = capture.summary(cache, (cache, workspace.parent, product))
        save("stages", stages)
        manifest.update(taskId=task_id, taskTerminalState=terminal,
                        sdkObservationElapsedMs=(time.monotonic() - started) * 1000,
                        requestMetrics=metrics.snapshot(), visualBehaviorVerified=False,
                        hostNormalExit="not independently observed; parent must inspect process and application logs",
                        sdkCleanup=cleanup)
        if mode == "cold-build":
            confirmed = (terminal == "succeeded" and before_cache["fileCount"] == 0 and stages["gradleHomeConfirmed"]
                         and stages["dependencyDownloadLogCount"] > 0 and stages["resolutionCount"] > 0
                         and not stages["unexpectedExternalResolutionPaths"])
            manifest["dependencyDownloadBuildObserved"] = confirmed
            if manifest["status"] == "task_succeeded" and not confirmed:
                manifest["status"] = "task_succeeded_measurement_incomplete"
        jars = list((workspace.parent / "build/libs").glob("*.jar"))
        save("artifacts", [{"path": str(path), "bytes": path.stat().st_size, "sha256": digest(path)} for path in jars])
        save("summary", manifest)
        print(json.dumps({"status": manifest["status"], "taskId": task_id, "taskTerminalState": terminal,
                          "visualBehaviorVerified": False}), flush=True)
    return 0 if manifest["status"] == "task_succeeded" else 2 if manifest["status"] == "task_succeeded_measurement_incomplete" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8-sig"))
    return run(config)


if __name__ == "__main__":
    raise SystemExit(main())
