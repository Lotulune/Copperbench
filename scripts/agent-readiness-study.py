#!/usr/bin/env python3
"""Preregister and record M3 studies; never launch agents or replace player review."""
from __future__ import annotations

import argparse
from collections import Counter
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

STAGES = ("discovery", "build_repair", "source_protection", "packaged_delivery",
          "reconnect", "player")
TESTS = {
    "contractgametests.itemstacksurvivesserialization",
    "contractgametests.recipeproducesdeclareditem",
    "contractgametests.reciperejectswrongingredient",
    "contractgametests.registeredstacklimit",
    "contractgametests.repairedmanualcodehasindependentexpectedvalues",
}
KINDS = ("autonomous-agent", "unfamiliar-user")
TERMINAL = ("completed", "failed", "abandoned")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def read_json(path):
    path = Path(path)
    require(path.stat().st_size <= 16 * 1024 * 1024, "JSON record exceeds 16 MiB")
    value = json.loads(path.read_text(encoding="utf-8-sig"),
                       parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON number")))
    require(isinstance(value, dict), "JSON record must be an object")
    return value


def nonempty(value, name):
    require(isinstance(value, str) and bool(value.strip()) and len(value) <= 8192, name + " must be nonempty text")
    return value


def identifier(value):
    require(isinstance(value, str) and re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", value), "Invalid study/attempt ID")
    return value


def timestamp(value):
    nonempty(value, "timestamp")
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.tzinfo is not None, "Timestamps must include a timezone")
    return result


def now():
    return datetime.now(timezone.utc).isoformat()


def absolute_workspace(value):
    nonempty(value, "workspaceRoot")
    # Guest paths are metadata, not paths to resolve against the recorder's host.
    require(PurePosixPath(value).is_absolute() or PureWindowsPath(value).is_absolute(), "workspaceRoot must be absolute")
    value = value.replace("\\", "/").rstrip("/")
    require(".." not in value.split("/"), "workspaceRoot cannot contain '..'")
    return value.casefold() if PureWindowsPath(value).drive else value


def check_inputs(plan):
    for name, entry in plan["inputs"].items():
        require(digest(entry["path"]) == entry["sha256"], "Frozen input changed: " + name)


def register(spec_path, output):
    spec_path, output = Path(spec_path).resolve(strict=True), Path(output).absolute()
    plan = read_json(spec_path)
    require(plan.get("schemaVersion") == "1.0", "Unsupported study schemaVersion")
    identifier(plan.get("studyId"))
    require(plan.get("kind") in KINDS, "Study kind must be autonomous-agent or unfamiliar-user")
    require(isinstance(plan.get("sourceCommit"), str) and re.fullmatch(r"[0-9a-f]{40}", plan["sourceCommit"]),
            "sourceCommit must be a full Git SHA")
    for key in ("operatingSystem", "cachePolicy"):
        nonempty(plan.get(key), key)
    if plan["kind"] == "autonomous-agent":
        require(isinstance(plan.get("agent"), dict), "Freeze agent model and settings")
        nonempty(plan["agent"].get("model"), "agent.model")
        require(isinstance(plan["agent"].get("settings"), dict) and bool(plan["agent"]["settings"]),
                "agent.settings must describe the actual fixed configuration")
    inputs = plan.get("inputs")
    require(isinstance(inputs, dict) and {"candidatePackage", "applicationJar", "taskCard", "recoveryProbe", "gameTests", "testManifest"} <= inputs.keys(),
            "Freeze package, installed application, task card and all three supplied fixture files")
    for name, entry in inputs.items():
        identifier(name.lower())
        require(isinstance(entry, dict) and isinstance(entry.get("sha256"), str)
                and re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]), "Each frozen input needs SHA-256")
        path = Path(nonempty(entry.get("path"), "input path"))
        entry["path"] = str((path if path.is_absolute() else spec_path.parent / path).resolve(strict=True))
    attempts = plan.get("attempts")
    require(isinstance(attempts, list) and all(isinstance(a, dict) for a in attempts), "attempts must be an object array")
    require(len(attempts) >= 20 if plan["kind"] == "autonomous-agent" else 5 <= len(attempts) <= 8,
            "Preregister at least 20 agent attempts or 5-8 unfamiliar users")
    ids, participants, workspaces = set(), set(), set()
    for attempt in attempts:
        key = identifier(attempt.get("id"))
        require(key not in ids, "Duplicate attempt ID")
        ids.add(key)
        participant = identifier(attempt.get("participantId"))
        if plan["kind"] == "unfamiliar-user":
            require(participant not in participants, "Unfamiliar-user participants must be distinct")
        participants.add(participant)
        workspace = absolute_workspace(attempt.get("workspaceRoot"))
        require(all(workspace != other and not workspace.startswith(other + "/") and not other.startswith(workspace + "/")
                    for other in workspaces), "Attempt workspaces must be distinct and non-nested")
        workspaces.add(workspace)
    check_inputs(plan)
    require(not output.exists(), "Study directory already exists; registration cannot replace prior attempts")
    plan["registeredAt"] = now()
    plan["requiredStages"] = list(STAGES)
    output.mkdir(parents=True)
    (output / "plan.json").write_bytes(encoded(plan))
    (output / "plan.sha256").write_text(digest(output / "plan.json") + "\n", encoding="utf-8")
    (output / "events.jsonl").write_text("", encoding="utf-8")
    (output / "evidence").mkdir()
    return {"studyId": plan["studyId"], "registered": len(attempts), "planSha256": digest(output / "plan.json"),
            "started": 0, "directory": str(output)}


@contextmanager
def locked(root):
    root = Path(root).resolve(strict=True)
    lock = root / ".writer-lock"
    try:
        lock.mkdir()
    except FileExistsError:
        raise ValueError("Study writer is active or left a lock; inspect its process before removing the lock") from None
    try:
        (lock / "owner.json").write_bytes(encoded({"pid": os.getpid(), "at": now()}))
        yield root
    finally:
        (lock / "owner.json").unlink(missing_ok=True)
        lock.rmdir()


def load(root):
    root = Path(root).resolve(strict=True)
    require(digest(root / "plan.json") == (root / "plan.sha256").read_text(encoding="utf-8").strip(),
            "Preregistered plan changed")
    plan = read_json(root / "plan.json")
    states = {a["id"]: {**a, "state": "registered", "rescues": []} for a in plan["attempts"]}
    events, execution_ids = [], set()
    for line in (root / "events.jsonl").read_text(encoding="utf-8").splitlines():
        event = json.loads(line)
        require(isinstance(event, dict) and event.get("sequence") == len(events) + 1, "Invalid event sequence")
        require(event.get("attemptId") in states, "Event references an unregistered attempt")
        timestamp(event.get("at"))
        state = states[event["attemptId"]]
        require(state["state"] not in TERMINAL, "Event follows a terminal attempt")
        if event.get("type") == "started":
            require(state["state"] == "registered", "Attempt started twice")
            execution_id = nonempty(event.get("executionId"), "executionId")
            require(execution_id not in execution_ids, "Execution context reused across attempts")
            execution_ids.add(execution_id)
            state.update(state="running", startedAt=event["at"], executionId=execution_id)
        elif event.get("type") == "rescue":
            require(state["state"] == "running", "Rescue before start")
            state["rescues"].append(event)
        elif event.get("type") in TERMINAL:
            require(state["state"] == "running", "Terminal result before start")
            state.update(state=event["type"], completedAt=event["at"], result=event)
        else:
            raise ValueError("Unknown study event type")
        events.append(event)
    return plan, states, events


def append(root, events, event):
    event = {**event, "sequence": len(events) + 1, "at": now()}
    with (root / "events.jsonl").open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(event, ensure_ascii=False, allow_nan=False) + "\n")
        stream.flush()
        os.fsync(stream.fileno())
    return event


def start(root, attempt_id, execution_id):
    with locked(root) as root:
        plan, states, events = load(root)
        require(attempt_id in states and states[attempt_id]["state"] == "registered", "Attempt must be preregistered and not started")
        nonempty(execution_id, "executionId")
        require(not any(s.get("executionId") == execution_id for s in states.values()), "Execution context reused across attempts")
        check_inputs(plan)
        return append(root, events, {"type": "started", "attemptId": attempt_id, "executionId": execution_id})


def rescue(root, attempt_id, actor, detail):
    with locked(root) as root:
        _, states, events = load(root)
        require(attempt_id in states and states[attempt_id]["state"] == "running", "Rescue requires a running attempt")
        return append(root, events, {"type": "rescue", "attemptId": attempt_id,
                                     "actor": nonempty(actor, "actor"), "detail": nonempty(detail, "detail")})


def evidence_path(base, relative):
    require(isinstance(relative, str) and bool(relative) and not Path(relative).is_absolute()
            and not PureWindowsPath(relative).drive and "\\" not in relative, "Evidence paths must be relative with '/' separators")
    path = (base / relative).resolve(strict=True)
    require(path.is_relative_to(base) and path.is_file(), "Evidence escapes its supplied bundle or is not a file")
    return path


def verified_delivery(bundle, record, started_at):
    """Check bytes and Core receipts; behavioral stages still require external review."""
    files = record["delivery"]
    require(isinstance(files, dict) and set(files) == {"verification", "report", "testedJar", "exportedJar", "exportReceipt"},
            "Delivery needs verification, XML report, tested/exported JARs and raw export receipt")
    paths = {key: evidence_path(bundle, value) for key, value in files.items()}
    verification = read_json(paths["verification"])
    require(verification.get("mode") == "packaged_jar" and verification.get("status") == "passed"
            and verification.get("sourceCurrentAtCompletion") is True, "Expected current packaged-JAR acceptance")
    for name in ("minimumTests", "acceptanceExecuted"):
        require(type(verification.get(name)) is int and verification[name] >= 5, "At least five effective business tests are required")
    require(verification["acceptanceExecuted"] >= verification["minimumTests"], "Insufficient effective tests")
    for name in ("processExitCode", "failed", "skipped"):
        require(type(verification.get(name)) is int and verification[name] == 0, "Failed, skipped or incomplete acceptance")
    require(timestamp(verification.get("startedAt")) >= timestamp(started_at), "Verification predates the registered attempt")
    require(timestamp(verification.get("completedAt")) >= timestamp(verification["startedAt"]), "Invalid acceptance time order")
    require(timestamp(verification["completedAt"]) <= timestamp(now()), "Acceptance completion is in the future")
    workspace = absolute_workspace(record["workspaceRoot"])
    require(absolute_workspace(verification.get("artifactPath")).startswith(workspace + "/"),
            "Verification artifact belongs to a different attempt workspace")
    source_hash = verification.get("sourceSnapshot", {}).get("sha256")
    require(isinstance(source_hash, str) and re.fullmatch(r"[0-9a-f]{64}", source_hash), "Missing source snapshot hash")
    require(verification.get("environment", {}).get("generatorId") == "fabric-1.21.1", "Unexpected generator")
    jar_hash, report_hash = digest(paths["testedJar"]), digest(paths["report"])
    require(jar_hash == verification.get("artifactSha256") == digest(paths["exportedJar"]), "Tested and exported JAR bytes differ")
    require(report_hash == verification.get("reportSha256"), "XML report bytes differ")
    xml_bytes = paths["report"].read_bytes()
    require(len(xml_bytes) <= 16 * 1024 * 1024 and b"<!DOCTYPE" not in xml_bytes.upper()
            and b"<!ENTITY" not in xml_bytes.upper(), "Unsupported XML declarations or report size")
    xml = ET.fromstring(xml_bytes)
    cases = list(xml.iter("testcase"))
    names = [case.get("name") for case in cases]
    require(len(names) == len(set(names)) and TESTS <= set(names), "Missing or duplicate independent business test cases")
    require(not any(list(xml.iter(tag)) for tag in ("failure", "error", "skipped")), "XML contains failed or skipped cases")
    declared = verification.get("cases")
    require(isinstance(declared, list) and all(isinstance(case, dict) for case in declared), "Missing Core case evidence")
    accepted = [case["name"] for case in declared if case.get("scope") == "acceptance" and case.get("status") == "passed"]
    require(len(accepted) == len(set(accepted)) == verification["acceptanceExecuted"]
            and TESTS <= set(accepted) and set(accepted) <= set(names), "Core and XML business-test counts disagree")
    with zipfile.ZipFile(paths["testedJar"]) as archive:
        require(json.loads(archive.read("fabric.mod.json")).get("id") == "m1_delivery", "Unexpected delivered mod")
    receipt = read_json(paths["exportReceipt"])
    task = receipt.get("data", {}).get("task", {})
    exported = task.get("verifiedExport", {})
    require(receipt.get("status") == "succeeded" and task.get("state") == "succeeded"
            and exported.get("status") == "passed_current_input", "Expected successful current-input export receipt")
    require(nonempty(verification.get("taskId"), "verification.taskId") == exported.get("taskId")
            and nonempty(verification.get("workspaceId"), "verification.workspaceId") == receipt.get("workspaceId"),
            "Export belongs to a different verification/workspace")
    require(exported.get("artifactSha256") == jar_hash and exported.get("reportSha256") == report_hash
            and exported.get("sourceSha256") == source_hash,
            "Export source or artifact binding disagrees")
    return {"artifactSha256": jar_hash, "reportSha256": report_hash, "taskId": verification["taskId"],
            "acceptanceExecuted": verification["acceptanceExecuted"], "paths": list(files.values())}


def finish(root, attempt_id, record_path):
    record_path = Path(record_path).resolve(strict=True)
    record, bundle = read_json(record_path), record_path.parent
    with locked(root) as root:
        plan, states, events = load(root)
        require(attempt_id in states and states[attempt_id]["state"] == "running", "Finish requires a running attempt")
        state = states[attempt_id]
        require(record.get("kind") == plan["kind"], "Scripted replay or a different study kind cannot count as a trial")
        require(record.get("executionId") == state["executionId"], "Execution context differs from start")
        require(record.get("sourceCommit") == plan["sourceCommit"]
                and record.get("candidateSha256") == plan["inputs"]["candidatePackage"]["sha256"], "Candidate identity differs from registration")
        require(absolute_workspace(record.get("workspaceRoot")) == absolute_workspace(state["workspaceRoot"]),
                "Workspace differs from registration")
        outcome = record.get("outcome")
        require(outcome in TERMINAL, "Outcome must be completed, failed or abandoned")
        nonempty(record.get("reviewer"), "reviewer")
        nonempty(record.get("note"), "note")
        refs = record.get("evidence", [])
        require(isinstance(refs, list) and all(isinstance(ref, str) for ref in refs), "evidence must list relative files")
        delivery = None
        if outcome == "completed":
            check_inputs(plan)
            stages = record.get("stages")
            require(isinstance(stages, dict) and set(stages) == set(STAGES), "Review every fixed task stage separately")
            for stage in stages.values():
                require(isinstance(stage, dict) and stage.get("verdict") == "passed"
                        and isinstance(stage.get("evidence"), list) and stage["evidence"], "Completed trial needs passed, evidenced stages")
                refs.extend(stage["evidence"])
            nonempty(record.get("transcript"), "transcript")
            refs.append(record["transcript"])
            delivery = verified_delivery(bundle, record, state["startedAt"])
            refs.extend(delivery.pop("paths"))
        paths = {ref: evidence_path(bundle, ref) for ref in set(refs)}
        # Retain evidence bytes rather than counting files that may later disappear.
        target = root / "evidence" / attempt_id
        target.mkdir(exist_ok=True)
        retained = []
        for ref, path in sorted({**paths, record_path.name: record_path}.items()):
            sha = digest(path)
            destination = target / (sha + "-" + path.name)
            if not destination.exists():
                shutil.copyfile(path, destination)
            require(digest(destination) == sha == digest(path), "Evidence changed during capture")
            retained.append({"source": ref, "path": destination.relative_to(root).as_posix(), "sha256": sha})
        return append(root, events, {"type": outcome, "attemptId": attempt_id, "note": record["note"],
                                    "reviewer": record["reviewer"], "evidence": retained, "delivery": delivery,
                                    "assessmentSource": "external-reviewer"})


def summary(root):
    root = Path(root).resolve(strict=True)
    plan, states, _ = load(root)
    counts = Counter(state["state"] for state in states.values())
    rows, successes, invalid = [], 0, 0
    for state in states.values():
        issues = []
        for entry in state.get("result", {}).get("evidence", []):
            try:
                path = evidence_path(root, entry["path"])
                if digest(path) != entry["sha256"]:
                    issues.append(entry["path"])
            except (ValueError, OSError):
                issues.append(entry["path"])
        if issues:
            invalid += 1
        unassisted = state["state"] == "completed" and not state["rescues"] and not issues
        successes += int(unassisted)
        rows.append({"id": state["id"], "participantId": state["participantId"], "state": state["state"],
                     "rescues": len(state["rescues"]), "evidenceIssues": issues, "unassistedCompletion": unassisted})
    started = len(states) - counts["registered"]
    closed = not counts["registered"] and not counts["running"] and not invalid
    rate = successes / started if started else None
    return {"studyId": plan["studyId"], "kind": plan["kind"], "registered": len(states), "started": started,
            "denominator": started, "states": dict(counts), "unassistedCompletions": successes,
            "unassistedCompletionRate": rate, "allRegisteredAttemptsFinished": closed,
            "agentTargetMet": (rate >= .8 if closed else None) if plan["kind"] == "autonomous-agent" else None,
            "assessmentSource": "external-reviewer-with-byte-and-receipt-checks",
            "independentlyVerifiedAgentOrUserBehavior": False, "attempts": rows}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    init = commands.add_parser("register")
    init.add_argument("--spec", required=True, type=Path)
    init.add_argument("--output", required=True, type=Path)
    for name in ("start", "rescue", "finish", "summary"):
        command = commands.add_parser(name)
        command.add_argument("--study", required=True, type=Path)
        if name != "summary":
            command.add_argument("--attempt", required=True)
        if name == "start":
            command.add_argument("--execution-id", required=True)
        elif name == "rescue":
            command.add_argument("--actor", required=True)
            command.add_argument("--detail", required=True)
        elif name == "finish":
            command.add_argument("--record", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        if args.command == "register":
            result = register(args.spec, args.output)
        elif args.command == "start":
            result = start(args.study, args.attempt, args.execution_id)
        elif args.command == "rescue":
            result = rescue(args.study, args.attempt, args.actor, args.detail)
        elif args.command == "finish":
            result = finish(args.study, args.attempt, args.record)
        else:
            # Serialize a snapshot with mutations, including evidence attachment.
            with locked(args.study):
                result = summary(args.study)
        print(json.dumps(result, ensure_ascii=False, allow_nan=False))
        return 0
    except (ValueError, OSError, KeyError, TypeError, ET.ParseError, zipfile.BadZipFile) as error:
        print(json.dumps({"status": "rejected", "message": str(error)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
