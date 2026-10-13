#!/usr/bin/env python3
"""Collect and independently verify Copperbench exports using Python's standard library."""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import sys
import tempfile
import unicodedata
import uuid
from xml.parsers import expat
import zipfile
import zlib

BUNDLE_VERSION = "1"
BUNDLE_KIND = "copperbench_verified_export"
SCOPE = "Only the acceptance cases listed in gametest-results.xml; client behavior is not implied."
LIMITS = {"verification.json": 4 * 1024 * 1024, "verified-mod.jar": 256 * 1024 * 1024,
          "gametest-results.xml": 8 * 1024 * 1024, "manifest.json": 4 * 1024 * 1024}
PAYLOADS = ("verification.json", "verified-mod.jar", "gametest-results.xml")
MAX_ZIP_BYTES = sum(LIMITS.values()) + 1024 * 1024
MAX_COMPRESSION_RATIO = 200
ENVIRONMENT_FIELDS = {"generatorId", "loader", "minecraftVersion", "loaderVersion", "javaVersion",
                      "javaRelease", "fabricApiVersion", "pluginId", "pluginVersion"}
RECEIPT_FIELDS = {"schemaVersion", "taskId", "status", "scope", "artifactPath", "reportPath",
                  "artifactSha256", "reportSha256", "passed", "failed", "skipped", "acceptanceExecuted",
                  "environment", "sourceSha256", "portableEvidenceVersion", "environmentSource", "mode",
                  "minimumTests", "startedAt", "processExitCode", "coverage"}


class EvidenceError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def require(condition, code, message):
    if not condition:
        raise EvidenceError(code, message)


def object_fields(value, required, optional=frozenset()):
    require(type(value) is dict and required <= value.keys() <= required | optional,
            "EVIDENCE_FORMAT_INVALID", "Missing or unsupported evidence fields.")


def integer(value, minimum=0, maximum=10000):
    require(type(value) is int and minimum <= value <= maximum,
            "EVIDENCE_FORMAT_INVALID", "Expected a bounded integer.")
    return value


def digest(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value),
            "EVIDENCE_FORMAT_INVALID", "Expected a lowercase SHA-256 identity.")
    return value


def identifier(value):
    try:
        valid = type(value) is str and str(uuid.UUID(value)) == value
    except ValueError:
        valid = False
    require(valid, "EVIDENCE_FORMAT_INVALID", "Expected a canonical UUID.")
    return value


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def same_json(left, right):
    return json.dumps(left, sort_keys=True, ensure_ascii=True) == json.dumps(right, sort_keys=True, ensure_ascii=True)


def java_blank(value):
    # Character.isWhitespace, as used by String.isBlank; NBSP and figure/narrow-NBSP are not blank.
    whitespace = "\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000"
    return all(character in whitespace for character in value)


def parse_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "EVIDENCE_FORMAT_INVALID", "Duplicate JSON field.")
            result[key] = value
        return result

    def constant(_):
        raise EvidenceError("EVIDENCE_FORMAT_INVALID", "Non-finite JSON numbers are unsupported.")

    try:
        result = json.loads(data.decode("utf-8"), object_pairs_hook=pairs, parse_constant=constant)
    except (ValueError, UnicodeError, RecursionError) as error:
        raise EvidenceError("EVIDENCE_FORMAT_INVALID", "Invalid UTF-8 JSON.") from error
    pending = [(result, 1)]
    while pending:
        value, depth = pending.pop()
        require(depth <= 64, "EVIDENCE_LIMIT_EXCEEDED", "JSON nesting exceeds 64 levels.")
        if isinstance(value, dict):
            pending.extend((child, depth + 1) for child in value.values())
        elif isinstance(value, list):
            pending.extend((child, depth + 1) for child in value)
    return result


def safe_path(path):
    """Reject links and Windows junctions before resolving any user-supplied path."""
    path = Path(os.path.abspath(path))
    for part in [*reversed(path.parents), path]:
        try:
            info = part.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(info.st_mode) and not (getattr(info, "st_file_attributes", 0) & 0x400),
                "EVIDENCE_LINK_REJECTED", "Evidence paths must not traverse links or reparse points.")
    return path


def file_identity(info):
    # Windows Python can report creation time in stat().st_ctime but change time in fstat().st_ctime.
    # Compare stable handle/path identity and modification time, then recheck the actual bytes.
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns)


def read_file(path, limit):
    path = safe_path(path)
    before = path.stat(follow_symlinks=False)
    require(stat.S_ISREG(before.st_mode), "EVIDENCE_FILE_INVALID", "Expected a regular evidence file.")
    require(before.st_size <= limit, "EVIDENCE_LIMIT_EXCEEDED", "Evidence member exceeds its byte limit.")
    with path.open("rb") as stream:
        require(file_identity(before) == file_identity(os.fstat(stream.fileno())),
                "EVIDENCE_SOURCE_CHANGED", "Evidence changed before reading.")
        data = stream.read(limit + 1)
        require(len(data) <= limit, "EVIDENCE_LIMIT_EXCEEDED", "Evidence member exceeds its byte limit.")
        require(file_identity(before) == file_identity(os.fstat(stream.fileno())),
                "EVIDENCE_SOURCE_CHANGED", "Evidence changed while reading.")
    require(file_identity(before) == file_identity(safe_path(path).stat(follow_symlinks=False)),
            "EVIDENCE_SOURCE_CHANGED", "Evidence changed after reading.")
    return data


def manifest_digest(manifest):
    """Same length-prefixed UTF-8 protocol and UTF-16 sort order as RequiredCaseManifest."""
    ordered = sorted(manifest["cases"], key=lambda case: (case["className"].encode("utf-16-be"),
                                                        case["name"].encode("utf-16-be")))
    fields = ["1", manifest["id"], manifest["identityKind"], str(manifest["minimumTests"])]
    fields += [value for case in ordered for value in (case["className"], case["name"])]
    result = hashlib.sha256()
    for value in fields:
        data = value.encode("utf-8")
        result.update(struct.pack(">I", len(data)))
        result.update(data)
    return result.hexdigest()


def required_manifest(manifest):
    object_fields(manifest, {"schemaVersion", "id", "identityKind", "minimumTests", "cases", "sha256"})
    require(manifest["schemaVersion"] == "1", "EVIDENCE_VERSION_UNSUPPORTED", "Unsupported required-case manifest version.")
    require(type(manifest["id"]) is str and re.fullmatch(r"[a-z][a-z0-9_-]{0,127}", manifest["id"]),
            "EVIDENCE_FORMAT_INVALID", "Invalid required-case manifest ID.")
    require(manifest["identityKind"] in ("junit_tuple", "gametest_resource"),
            "EVIDENCE_FORMAT_INVALID", "Unsupported testcase identity kind.")
    cases = manifest["cases"]
    require(type(cases) is list and 1 <= len(cases) <= 10000,
            "EVIDENCE_FORMAT_INVALID", "Required cases must be a bounded nonempty list.")
    integer(manifest["minimumTests"], len(cases))
    identities = set()
    for case in cases:
        object_fields(case, {"className", "name"})
        for field in ("className", "name"):
            require(type(case[field]) is str, "EVIDENCE_FORMAT_INVALID", "Testcase identities must be strings.")
            try:
                length = len(case[field].encode("utf-16-be")) // 2
            except UnicodeError as error:
                raise EvidenceError("EVIDENCE_FORMAT_INVALID", "Invalid Unicode testcase identity.") from error
            require(length <= 4096, "EVIDENCE_FORMAT_INVALID", "Required testcase identity is too long.")
        require(not java_blank(case["name"]) and case["name"] != "minecraft:always_pass",
                "EVIDENCE_FORMAT_INVALID", "Required cases must name business tests.")
        if manifest["identityKind"] == "gametest_resource":
            require(not case["className"] and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", case["name"]),
                    "EVIDENCE_FORMAT_INVALID", "GameTest manifests require full resource IDs and empty className.")
        identity = (case["className"], case["name"])
        require(identity not in identities, "EVIDENCE_MANIFEST_INVALID", "Duplicate required testcase identity.")
        identities.add(identity)
    fixed = {"camp-kit-native-v1": (1, 2, 3, 4, 11, 12), "camp-kit-scoped-v1": tuple(range(1, 14))}.get(manifest["id"])
    if fixed is not None:
        expected = {("", f"copperbench_camp_kit:t{number:02d}") for number in fixed}
        require(manifest["identityKind"] == "gametest_resource" and identities == expected
                and manifest["minimumTests"] == len(fixed), "EVIDENCE_MANIFEST_INVALID", "Published Camp Kit manifests cannot be redefined.")
    require(manifest_digest(manifest) == digest(manifest["sha256"]),
            "EVIDENCE_MANIFEST_INVALID", "Required-case identities do not match the manifest digest.")


def receipt_identity(receipt):
    """Validate the supported portable format without inventing absent environment observations."""
    require(type(receipt) is dict, "EVIDENCE_FORMAT_INVALID", "Expected a portable receipt object.")
    require("portableEvidenceVersion" in receipt, "EVIDENCE_LEGACY_UNSUPPORTED",
            "Unversioned receipts lack a supported portable acceptance contract; re-export the original task.")
    require(receipt["portableEvidenceVersion"] == "1", "EVIDENCE_VERSION_UNSUPPORTED", "Unsupported portable evidence version.")
    object_fields(receipt, RECEIPT_FIELDS, {"requiredCases"})
    require(receipt["schemaVersion"] == "1.0" and receipt["mode"] == "packaged_jar"
            and receipt["environmentSource"] == "acceptance_record" and receipt["scope"] == SCOPE,
            "EVIDENCE_FORMAT_INVALID", "Unsupported portable receipt contract.")
    identifier(receipt["taskId"])
    for field in ("sourceSha256", "artifactSha256", "reportSha256"):
        digest(receipt[field])
    require(receipt["artifactPath"] == "verified-mod.jar" and receipt["reportPath"] == "gametest-results.xml",
            "EVIDENCE_PATH_INVALID", "Portable v1 members must use the exporter's canonical relative names.")
    require(receipt["status"] in ("passed_current_input", "passed_historical_input"),
            "EVIDENCE_ACCEPTANCE_INVALID", "Expected a successful selected export.")
    integer(receipt["processExitCode"], 0, 0)
    integer(receipt["failed"], 0, 0)
    for field in ("passed", "skipped", "acceptanceExecuted"):
        integer(receipt[field])
    integer(receipt["minimumTests"], 1)
    require(receipt["minimumTests"] <= receipt["acceptanceExecuted"] <= receipt["passed"],
            "EVIDENCE_ACCEPTANCE_INVALID", "Recorded business execution is insufficient or inconsistent.")
    require(type(receipt["startedAt"]) is str and re.fullmatch(
        r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z", receipt["startedAt"]),
        "EVIDENCE_FORMAT_INVALID", "Expected an original UTC acceptance start time.")
    try:
        datetime.fromisoformat(receipt["startedAt"][:19] + "+00:00")
    except ValueError as error:
        raise EvidenceError("EVIDENCE_FORMAT_INVALID", "Invalid acceptance start time.") from error
    environment = receipt["environment"]
    object_fields(environment, set(), ENVIRONMENT_FIELDS)
    for field, value in environment.items():
        if field == "javaRelease":
            integer(value, 1, 1000)
        else:
            require(type(value) is str and re.fullmatch(r"[A-Za-z0-9_.+ -]{1,128}", value),
                    "EVIDENCE_FORMAT_INVALID", "Environment observations must be version tokens, never paths or logs.")
    expected_coverage = {"minimumTests": receipt["minimumTests"], "acceptanceExecuted": receipt["acceptanceExecuted"],
                         "shortfall": 0, "status": "sufficient"}
    require(same_json(receipt["coverage"], expected_coverage), "EVIDENCE_BINDING_INVALID", "Coverage summary disagrees with the receipt.")
    identity = {"taskId": receipt["taskId"], "sourceSha256": receipt["sourceSha256"], "environment": environment,
                "portableEvidenceVersion": "1", "minimumTests": receipt["minimumTests"],
                "inputScope": "current_at_export" if receipt["status"] == "passed_current_input" else "historical_at_export",
                "compatibility": "minimum_only_legacy"}
    if "requiredCases" in receipt:
        evidence = receipt["requiredCases"]
        object_fields(evidence, {"schemaVersion", "manifest", "binding", "coverage"})
        require(evidence["schemaVersion"] == "1", "EVIDENCE_VERSION_UNSUPPORTED", "Unsupported required-case evidence version.")
        manifest = evidence["manifest"]
        required_manifest(manifest)
        binding = evidence["binding"]
        object_fields(binding, {"taskId", "workspaceId", "workspaceRevision", "sourceSha256", "environment", "frozenSha256"})
        identifier(binding["workspaceId"])
        integer(binding["workspaceRevision"], 0, 2**63 - 1)
        digest(binding["frozenSha256"])
        require(all(same_json(binding[field], receipt[field]) for field in ("taskId", "sourceSha256", "environment"))
                and manifest["minimumTests"] == receipt["minimumTests"],
                "EVIDENCE_BINDING_INVALID", "Required cases belong to different task, input, environment or threshold.")
        expected = {"manifestSha256": manifest["sha256"], "required": len(manifest["cases"]),
                    "minimumTests": manifest["minimumTests"], "uniqueExecuted": receipt["acceptanceExecuted"],
                    "shortfall": 0, "reasonCode": "GAMETEST_PASSED", "status": "passed", "duplicates": [], "missingCases": []}
        require(same_json(evidence["coverage"], expected), "EVIDENCE_BINDING_INVALID", "Required-case coverage is inconsistent.")
        identity.update(compatibility="required_cases_v1", requiredCaseManifest=manifest, binding=binding)
    else:
        require(environment.get("minecraftVersion") != "26.3" and environment.get("generatorId") not in ("fabric-26.3", "neoforge-26.3"),
                "EVIDENCE_REQUIRED_CASES_MISSING", "26.3 evidence cannot be promoted from a legacy minimum-only receipt.")
    return identity


def file_entry(name, data):
    return {"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def collect(source, destination):
    source, destination = safe_path(source), safe_path(destination)
    require(source.is_dir(), "EVIDENCE_SOURCE_INVALID", "Select an actual verified export directory.")
    require(not destination.exists(), "EVIDENCE_DESTINATION_EXISTS", "The delivery directory must not already exist.")
    require(destination.parent.is_dir(), "EVIDENCE_DESTINATION_INVALID", "Create the delivery parent directory first.")
    raw = read_file(source / "verification.json", LIMITS["verification.json"])
    receipt = parse_json(raw)
    identity = receipt_identity(receipt)
    staging = Path(tempfile.mkdtemp(prefix=".copperbench-evidence-", dir=destination.parent))
    try:
        members = []
        for name in PAYLOADS:
            data = raw if name == "verification.json" else read_file(source / name, LIMITS[name])
            entry = file_entry(name, data)
            expected = {"verified-mod.jar": "artifactSha256", "gametest-results.xml": "reportSha256"}.get(name)
            if expected:
                require(entry["sha256"] == receipt[expected], "EVIDENCE_HASH_MISMATCH", "Export bytes disagree with the selected receipt.")
            (staging / name).write_bytes(data)
            members.append(entry)
        # Re-read every source after copying. A racing change cannot leave a completed delivery.
        for member in members:
            name = member["path"]
            require(file_entry(name, read_file(source / name, LIMITS[name])) == member,
                    "EVIDENCE_SOURCE_CHANGED", "Export bytes changed during collection.")
            require(file_entry(name, read_file(staging / name, LIMITS[name])) == member,
                    "EVIDENCE_COPY_CHANGED", "Copied evidence differs from the selected export.")
        manifest = {"bundleVersion": BUNDLE_VERSION, "kind": BUNDLE_KIND, "identity": identity, "members": members}
        manifest_bytes = json_bytes(manifest)
        require(len(manifest_bytes) <= LIMITS["manifest.json"], "EVIDENCE_LIMIT_EXCEEDED", "Bundle manifest exceeds its byte limit.")
        (staging / "manifest.json").write_bytes(manifest_bytes)
        safe_path(destination)
        require(not destination.exists(), "EVIDENCE_DESTINATION_EXISTS", "The delivery directory appeared during collection.")
        staging.rename(destination)
        return {"status": "collected", "bundleVersion": BUNDLE_VERSION, "identity": identity, "members": members}
    finally:
        if staging.exists():
            require(safe_path(staging).resolve().parent == destination.parent.resolve(),
                    "EVIDENCE_PATH_INVALID", "Staging cleanup escaped its delivery parent.")
            shutil.rmtree(staging)


def raw_report(data, minimum, manifest=None):
    """Stream bounded XML; count original producer identities before any display truncation."""
    require(len(data) <= LIMITS["gametest-results.xml"], "EVIDENCE_LIMIT_EXCEEDED", "XML exceeds its byte limit.")
    integer(minimum, 1)
    if manifest is not None:
        required_manifest(manifest)
    parser = expat.ParserCreate()
    stack, rows = [], []
    active = None
    reported_failure = False

    def start(name, attrs):
        nonlocal active, reported_failure
        require(len(stack) < 64, "EVIDENCE_XML_INVALID", "XML nesting exceeds 64 levels.")
        if not stack:
            require(name in ("testsuite", "testsuites"), "EVIDENCE_XML_INVALID", "Expected a JUnit/GameTest suite.")
        if name == "testcase":
            require(stack and stack[-1] == "testsuite" and active is None and not java_blank(attrs.get("name", "")),
                    "EVIDENCE_XML_INVALID", "Testcase needs a nonempty raw name and an immediate testsuite parent.")
            require(len(rows) < 10000, "EVIDENCE_LIMIT_EXCEEDED", "XML exceeds 10000 testcases.")
            active = {"className": attrs.get("classname", ""), "name": attrs["name"], "failed": False, "skipped": False}
        elif active is not None:
            if name in ("failure", "error"):
                active["failed"] = True
            if name == "skipped":
                active["skipped"] = True
        if name in ("testsuite", "testsuites"):
            for key in ("failures", "errors"):
                if key in attrs:
                    require(re.fullmatch(r"[+-]?[0-9]{1,10}", attrs[key]), "EVIDENCE_XML_INVALID", "Invalid suite failure count.")
                    count = int(attrs[key])
                    require(-(2**31) <= count < 2**31, "EVIDENCE_XML_INVALID", "Suite failure count exceeds integer limits.")
                    reported_failure |= count > 0
        stack.append(name)

    def end(name):
        nonlocal active
        if name == "testcase":
            rows.append(active)
            active = None
        stack.pop()

    def reject_declaration(*_):
        raise EvidenceError("EVIDENCE_XML_INVALID", "DTD and entity declarations are not accepted.")

    parser.StartElementHandler, parser.EndElementHandler = start, end
    parser.StartDoctypeDeclHandler = reject_declaration
    parser.EntityDeclHandler = reject_declaration
    parser.ExternalEntityRefHandler = reject_declaration
    try:
        parser.Parse(data, True)
    except (expat.ExpatError, ValueError, LookupError) as error:
        raise EvidenceError("EVIDENCE_XML_INVALID", "Malformed or truncated XML.") from error
    tuples, business, passed_business, manifest_seen = set(), set(), set(), set()
    duplicates = False
    failed = skipped = framework = 0
    for row in rows:
        identity = (row["className"], row["name"])
        duplicates |= identity in tuples
        tuples.add(identity)
        failure = row["failed"]
        skip = row["skipped"] and not failure
        failed += failure
        skipped += skip
        if row["name"] == "minecraft:always_pass":
            framework += 1
            continue
        if manifest is not None and manifest["identityKind"] == "gametest_resource":
            identity = ("", row["name"])
        duplicates |= identity in manifest_seen
        manifest_seen.add(identity)
        if not skip:
            business.add(identity)
            if not failure:
                passed_business.add(identity)
    counts = {"discovered": len(rows), "executed": len(rows) - skipped, "passed": len(rows) - skipped - failed,
              "failed": failed, "skipped": skipped, "frameworkTests": framework, "acceptanceExecuted": len(business)}
    require(not failed and not reported_failure, "EVIDENCE_CASES_FAILED", "Raw XML contains failed tests or suite failures.")
    require(not duplicates, "EVIDENCE_CASES_DUPLICATE", "Raw XML repeats testcase identities; suites and attempts cannot add coverage.")
    if manifest is not None:
        missing = {(case["className"], case["name"]) for case in manifest["cases"]} - passed_business
        require(not missing, "EVIDENCE_CASES_MISSING", "Raw XML did not pass every required business case: "
                + json.dumps(sorted(missing)[:20], ensure_ascii=True))
    require(len(business) >= minimum, "EVIDENCE_CASES_INSUFFICIENT",
            f"Expected at least {minimum} unique executed business cases; found {len(business)}.")
    return counts


def member_name(name):
    require(type(name) is str and 0 < len(name) <= 240 and "\\" not in name and ":" not in name
            and not name.startswith("/") and "\x00" not in name,
            "EVIDENCE_PATH_INVALID", "Evidence members must use safe relative POSIX paths.")
    parts = name.split("/")
    require(all(part and part not in (".", "..") and part[-1] not in (" ", ".") for part in parts),
            "EVIDENCE_PATH_INVALID", "Evidence member has an ambiguous or traversing path.")
    return unicodedata.normalize("NFC", name).casefold()


def member_set(names):
    normalized = set()
    for name in names:
        normalized_name = member_name(name)
        require(normalized_name not in normalized, "EVIDENCE_PATH_INVALID", "Duplicate normalized evidence member.")
        normalized.add(normalized_name)
    require(set(names) == set(LIMITS), "EVIDENCE_MEMBERS_INVALID", "A v1 bundle must contain exactly its four canonical files.")


def zip_index(stream):
    """Bound the central directory before ZipFile allocates member objects. v1 needs no ZIP64."""
    stream.seek(0, os.SEEK_END)
    length = stream.tell()
    require(length <= MAX_ZIP_BYTES, "EVIDENCE_LIMIT_EXCEEDED", "ZIP exceeds the archive byte limit.")
    stream.seek(max(0, length - 65557))
    tail = stream.read(65557)
    index = tail.rfind(b"PK\x05\x06")
    require(index >= 0 and len(tail) - index >= 22, "EVIDENCE_ZIP_INVALID", "Missing or truncated ZIP directory.")
    _, disk, directory_disk, disk_count, count, size, offset, comment = struct.unpack("<4s4H2IH", tail[index:index + 22])
    require(index + 22 + comment == len(tail), "EVIDENCE_ZIP_INVALID", "ZIP end record length is inconsistent.")
    require(disk == directory_disk == 0 and disk_count == count and count <= 4 and size <= 65536,
            "EVIDENCE_LIMIT_EXCEEDED", "ZIP64, split archives or excessive members/directories are unsupported.")
    require(offset + size == length - (22 + comment), "EVIDENCE_ZIP_INVALID", "ZIP directory offsets are inconsistent.")
    stream.seek(0)


def read_bundle(path):
    path = safe_path(path)
    if path.is_dir():
        names = []
        with os.scandir(path) as entries:
            for entry in entries:
                names.append(entry.name)
                require(len(names) <= 4, "EVIDENCE_LIMIT_EXCEEDED", "Too many bundle members.")
                safe_path(path / entry.name)
        member_set(names)
        return {name: read_file(path / name, LIMITS[name]) for name in LIMITS}
    before = path.stat(follow_symlinks=False)
    require(stat.S_ISREG(before.st_mode), "EVIDENCE_FILE_INVALID", "Expected a directory or regular ZIP file.")
    try:
        with path.open("rb") as stream:
            require(file_identity(before) == file_identity(os.fstat(stream.fileno())),
                    "EVIDENCE_SOURCE_CHANGED", "ZIP changed before reading.")
            zip_index(stream)
            with zipfile.ZipFile(stream) as archive:
                entries = archive.infolist()
                require(len(entries) <= 4, "EVIDENCE_LIMIT_EXCEEDED", "Too many ZIP members.")
                # ZipInfo truncates filename at NUL; validate its original decoded name before using the projection.
                member_set([entry.orig_filename for entry in entries])
                content = {}
                for entry in entries:
                    mode = stat.S_IFMT(entry.external_attr >> 16)
                    require(mode in (0, stat.S_IFREG) and not entry.is_dir() and not entry.external_attr & 0x400,
                            "EVIDENCE_LINK_REJECTED", "ZIP links, directories and special files are unsupported.")
                    require(not entry.flag_bits & ~0x80E and entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED),
                            "EVIDENCE_ZIP_INVALID", "Encrypted or unsupported compressed members are rejected.")
                    limit = LIMITS[entry.filename]
                    require(entry.file_size <= limit and entry.file_size <= max(1, entry.compress_size) * MAX_COMPRESSION_RATIO,
                            "EVIDENCE_LIMIT_EXCEEDED", "ZIP member size or compression expansion exceeds its limit.")
                    with archive.open(entry) as member:
                        data = member.read(limit + 1)
                    require(len(data) == entry.file_size and len(data) <= limit,
                            "EVIDENCE_ZIP_INVALID", "ZIP member length is inconsistent.")
                    content[entry.filename] = data
            require(file_identity(before) == file_identity(os.fstat(stream.fileno()))
                    == file_identity(safe_path(path).stat(follow_symlinks=False)),
                    "EVIDENCE_SOURCE_CHANGED", "ZIP changed during verification.")
            return content
    except (zipfile.BadZipFile, EOFError, zlib.error, struct.error, UnicodeError) as error:
        raise EvidenceError("EVIDENCE_ZIP_INVALID", "Corrupt or truncated ZIP input.") from error


def verify(path):
    content = read_bundle(path)
    manifest = parse_json(content["manifest.json"])
    object_fields(manifest, {"bundleVersion", "kind", "identity", "members"})
    require(manifest["bundleVersion"] == BUNDLE_VERSION, "EVIDENCE_VERSION_UNSUPPORTED", "Unsupported bundle version.")
    require(manifest["kind"] == BUNDLE_KIND, "EVIDENCE_FORMAT_INVALID", "Unsupported evidence bundle kind.")
    members = manifest["members"]
    require(type(members) is list and len(members) == 3, "EVIDENCE_MEMBERS_INVALID", "Expected exactly three payload members.")
    names = []
    for member in members:
        object_fields(member, {"path", "bytes", "sha256"})
        name = member["path"]
        member_name(name)
        require(name in PAYLOADS and name not in names, "EVIDENCE_MEMBERS_INVALID", "Invalid or duplicate manifest member.")
        names.append(name)
        integer(member["bytes"], 0, LIMITS[name])
        digest(member["sha256"])
        require(same_json(member, file_entry(name, content[name])), "EVIDENCE_HASH_MISMATCH", "Bundle member size or SHA-256 differs from the manifest.")
    receipt = parse_json(content["verification.json"])
    identity = receipt_identity(receipt)
    require(same_json(identity, manifest["identity"]), "EVIDENCE_BINDING_INVALID", "Bundle identity disagrees with the original portable receipt.")
    for name, field in (("verified-mod.jar", "artifactSha256"), ("gametest-results.xml", "reportSha256")):
        require(hashlib.sha256(content[name]).hexdigest() == receipt[field],
                "EVIDENCE_HASH_MISMATCH", "Payload bytes disagree with the original portable receipt.")
    required = receipt.get("requiredCases", {}).get("manifest")
    counts = raw_report(content["gametest-results.xml"], receipt["minimumTests"], required)
    for field in ("passed", "failed", "skipped", "acceptanceExecuted"):
        require(counts[field] == receipt[field], "EVIDENCE_COUNTS_INVALID", "Raw XML disagrees with recorded " + field + ".")
    return {"status": "integrity_verified", "bundleVersion": BUNDLE_VERSION, "identity": identity,
            "rawCounts": counts, "recipientWorkspaceChecked": False, "producerAuthenticated": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    collect_parser = commands.add_parser("collect", help="Copy the original receipt, JAR and XML into a new delivery directory.")
    collect_parser.add_argument("export_directory", type=Path)
    collect_parser.add_argument("destination", type=Path)
    verify_parser = commands.add_parser("verify", help="Recheck a directory or ZIP offline without extracting it.")
    verify_parser.add_argument("bundle", type=Path)
    args = parser.parse_args(argv)
    try:
        result = collect(args.export_directory, args.destination) if args.command == "collect" else verify(args.bundle)
    except EvidenceError as error:
        print(json.dumps({"status": "failed", "code": error.code, "message": str(error)}))
        return 2
    except OSError as error:
        print(json.dumps({"status": "failed", "code": "EVIDENCE_IO_ERROR", "message": str(error)}))
        return 2
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
