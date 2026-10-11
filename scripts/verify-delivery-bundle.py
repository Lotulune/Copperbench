#!/usr/bin/env python3
"""Offline, read-only consistency check for Copperbench delivery directories and ZIPs (stdlib only)."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
from uuid import UUID
from xml.parsers import expat
import zipfile

LIMITS = {"manifest.json": 256 * 1024, "verification.json": 1024 * 1024,
          "application-identity.json": 64 * 1024, "gametest-results.xml": 8 * 1024 * 1024,
          "verified-mod.jar": 256 * 1024 * 1024}
SHA = re.compile(r"[0-9a-f]{64}")


class InvalidBundle(ValueError):
    pass


class LegacyBundle(InvalidBundle):
    pass


def require(condition, message):
    if not condition:
        raise InvalidBundle(message)


def integer(value, minimum=0, maximum=10000):
    return type(value) is int and minimum <= value <= maximum


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(data):
    def invalid_constant(value):
        raise InvalidBundle(f"Invalid JSON constant: {value}")
    result = json.loads(data.decode("utf-8"), object_pairs_hook=pairs, parse_constant=invalid_constant)
    require(type(result) is dict, "JSON document must be an object")
    return result


def safe_name(name):
    require(type(name) is str and bool(name) and not name.startswith(("/", "\\")), "Absolute or empty path")
    require("\\" not in name and ":" not in name and all(part not in ("", ".", "..") for part in name.split("/")),
            "Unsafe member path")
    require(name in LIMITS, f"Unexpected bundle member: {name}")
    return name.casefold()


def reject_links(path):
    for part in (path, *path.parents):
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not (getattr(info, "st_file_attributes", 0) & 0x400),
                "Symbolic links and reparse points are not allowed")


def read_member(stream, name):
    digest, count, chunks = hashlib.sha256(), 0, []
    while block := stream.read(65536):
        count += len(block)
        require(count <= LIMITS[name], f"Member exceeds size limit: {name}")
        digest.update(block)
        if name != "verified-mod.jar":
            chunks.append(block)
    return {"size": count, "sha256": digest.hexdigest(), "data": b"".join(chunks)}


def members(path):
    path = path.absolute()
    reject_links(path)
    result = {}
    if path.is_dir():
        entries = list(path.iterdir())
        if not (path / "manifest.json").exists() and (path / "verification.json").is_file():
            raise LegacyBundle("Legacy receipt without a versioned manifest; complete bundle consistency is unverified")
        require(len(entries) == len(LIMITS), "Missing or extra bundle members")
        for entry in entries:
            safe_name(entry.name)
            reject_links(entry)
            require(entry.is_file(), "Bundle members must be regular files")
            require(entry.stat().st_size <= LIMITS[entry.name], f"Member exceeds size limit: {entry.name}")
            descriptor = os.open(entry, os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0))
            with os.fdopen(descriptor, "rb") as stream:
                require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), "Not a regular file")
                result[entry.name] = read_member(stream, entry.name)
    else:
        require(path.stat().st_size <= sum(LIMITS.values()) + 1024 * 1024, "Archive exceeds size limit")
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            require(len(entries) <= len(LIMITS), "Too many ZIP members")
            seen = set()
            for entry in entries:
                normalized = safe_name(entry.filename)
                require(normalized not in seen, "Duplicate normalized ZIP member")
                seen.add(normalized)
                mode = entry.external_attr >> 16
                require(stat.S_IFMT(mode) in (0, stat.S_IFREG) and not entry.is_dir(), "Links and special ZIP members are forbidden")
                require(not entry.external_attr & 0x400, "Windows reparse-point ZIP members are forbidden")
                require(not entry.flag_bits & 1, "Encrypted ZIPs are unsupported")
                require(0 <= entry.file_size <= LIMITS[entry.filename], "Expanded member exceeds size limit")
                require(entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), "Unsupported ZIP compression")
                with archive.open(entry) as stream:
                    result[entry.filename] = read_member(stream, entry.filename)
                require(result[entry.filename]["size"] == entry.file_size, "Truncated ZIP member")
    if "manifest.json" not in result and "verification.json" in result:
        raise LegacyBundle("Legacy receipt without a versioned manifest; complete bundle consistency is unverified")
    require(set(result) == set(LIMITS), "Missing or extra bundle members")
    return result


def report_counts(data):
    counts = dict(discovered=0, executed=0, passed=0, failed=0, skipped=0, frameworkTests=0, acceptanceExecuted=0)
    parser = expat.ParserCreate()
    stack, identities = [], set()
    current = None
    reported_failure = False
    nodes = 0

    def reject_dtd(*_):
        raise InvalidBundle("DTD and entities are forbidden in test reports")

    def start(name, attrs):
        nonlocal current, reported_failure, nodes
        nodes += 1
        require(nodes <= 100000 and len(stack) < 64, "XML structure exceeds limits")
        if not stack:
            require(name in ("testsuite", "testsuites"), "Invalid report root")
        if name in ("testsuite", "testsuites"):
            for field in ("errors", "failures"):
                if field in attrs:
                    require(re.fullmatch(r"[0-9]+", attrs[field]) is not None, "Invalid suite failure count")
                    reported_failure |= int(attrs[field]) > 0
        if name == "testcase":
            require(current is None and stack and stack[-1] == "testsuite", "Invalid testcase nesting")
            identity = (attrs.get("classname", ""), attrs.get("name", ""))
            require(identity[1].strip() and identity not in identities, "Empty or duplicate testcase identity")
            identities.add(identity)
            require(len(identities) <= 10000, "Too many testcases")
            current = {"framework": identity[1] == "minecraft:always_pass", "failed": False, "skipped": False}
        elif current is not None:
            if name in ("failure", "error"):
                current["failed"] = True
            elif name == "skipped":
                current["skipped"] = True
        stack.append(name)

    def end(name):
        nonlocal current
        if name == "testcase":
            require(current is not None, "Missing testcase")
            failed, skipped, framework = current["failed"], current["skipped"], current["framework"]
            executed = failed or not skipped
            counts["discovered"] += 1
            counts["executed"] += int(executed)
            counts["failed"] += int(failed)
            counts["passed"] += int(executed and not failed)
            counts["skipped"] += int(skipped and not failed)
            counts["frameworkTests"] += int(framework)
            counts["acceptanceExecuted"] += int(executed and not framework)
            current = None
        stack.pop()

    parser.StartElementHandler, parser.EndElementHandler = start, end
    parser.StartDoctypeDeclHandler = reject_dtd
    parser.EntityDeclHandler = reject_dtd
    parser.ExternalEntityRefHandler = reject_dtd
    parser.Parse(data, True)
    require(not reported_failure and counts["failed"] == 0, "The raw report contains test failures")
    return counts


def verify(path):
    files = members(Path(path))
    manifest = read_json(files["manifest.json"]["data"])
    receipt = read_json(files["verification.json"]["data"])
    identity = read_json(files["application-identity.json"]["data"])
    require(set(manifest) == {"bundleVersion", "taskId", "status", "sourceSha256", "artifactSha256", "reportSha256",
                             "mode", "minimumTests", "applicationIdentitySha256", "files"}, "Unknown or missing manifest fields")
    require(manifest.get("bundleVersion") == "1", "Unsupported bundleVersion")
    require(receipt.get("portableEvidenceVersion") == "1", "Unsupported portableEvidenceVersion")
    require(receipt.get("schemaVersion") == "1.0", "Unsupported receipt schemaVersion")
    require(identity.get("identityVersion") == "1", "Unsupported identityVersion")
    task = receipt.get("taskId")
    require(type(task) is str and str(UUID(task)) == task, "Invalid task identity")
    for key in ("taskId", "status", "sourceSha256", "artifactSha256", "reportSha256", "mode", "minimumTests", "applicationIdentitySha256"):
        require(key in receipt and manifest.get(key) == receipt[key], f"Manifest identity mismatch: {key}")
    require(identity.get("taskId") == task, "Application identity belongs to another task")
    require(receipt.get("status") in ("passed_current_input", "passed_historical_input"), "Unverified input status")
    require(receipt.get("mode") == "packaged_jar", "A packaged-JAR acceptance is required")
    require(type(receipt.get("processExitCode")) is int and receipt["processExitCode"] == 0, "Unsuccessful test process")
    require(receipt.get("environmentSource") == "acceptance_record", "Unknown environment source")
    minimum = receipt.get("minimumTests")
    require(integer(minimum, 1), "Invalid minimumTests")
    require(integer(manifest.get("minimumTests"), 1), "Manifest minimumTests must be an integer")
    require(type(receipt.get("sourceSha256")) is str and SHA.fullmatch(receipt["sourceSha256"]), "Missing input identity")
    require(type(receipt.get("startedAt")) is str, "Missing acceptance time")
    from datetime import datetime
    require(datetime.fromisoformat(receipt["startedAt"].replace("Z", "+00:00")).tzinfo is not None, "Acceptance time needs a timezone")
    for key, name in (("artifactPath", "verified-mod.jar"), ("reportPath", "gametest-results.xml")):
        require(receipt.get(key) == name, f"Unexpected {key}")
    for key, name in (("artifactSha256", "verified-mod.jar"), ("reportSha256", "gametest-results.xml"),
                      ("applicationIdentitySha256", "application-identity.json")):
        require(receipt.get(key) == files[name]["sha256"], f"Actual bytes differ: {name}")
    entries = manifest.get("files")
    require(type(entries) is list and len(entries) == 4, "Manifest must list exactly four evidence files")
    listed = set()
    for entry in entries:
        require(type(entry) is dict and set(entry) == {"path", "size", "sha256"}, "Invalid manifest entry")
        name = entry["path"]
        normalized = safe_name(name)
        require(name != "manifest.json" and normalized not in listed, "Duplicate or recursive manifest entry")
        listed.add(normalized)
        require(type(entry["size"]) is int and entry["size"] == files[name]["size"], f"Size mismatch: {name}")
        require(entry["sha256"] == files[name]["sha256"], f"Digest mismatch: {name}")
    require(listed == set(LIMITS) - {"manifest.json"}, "Manifest omits evidence")
    coverage = report_counts(files["gametest-results.xml"]["data"])
    require(coverage["acceptanceExecuted"] >= minimum, "Insufficient unique business tests")
    for key in ("passed", "failed", "skipped", "acceptanceExecuted"):
        require(type(receipt.get(key)) is int and receipt[key] == coverage[key], f"Recomputed count differs: {key}")
    expected = {"minimumTests": minimum, "acceptanceExecuted": coverage["acceptanceExecuted"], "shortfall": 0, "status": "sufficient"}
    require(receipt.get("coverage") == expected, "Coverage summary differs from the raw report")
    require(type(receipt["coverage"]["minimumTests"]) is int and type(receipt["coverage"]["acceptanceExecuted"]) is int
            and type(receipt["coverage"]["shortfall"]) is int, "Coverage must use integer counts")
    application = identity.get("application")
    require(type(application) is dict, "Invalid application identity")
    require(identity.get("source") in ("acceptance_record", "unavailable"), "Unknown application source")
    if identity["source"] == "unavailable":
        require(not application, "Unavailable application source cannot claim an identity")
    else:
        require(application.get("sourceState") in ("packaged_binary", "development_or_unverified"), "Unknown application source state")
        if application["sourceState"] == "packaged_binary":
            require(type(application.get("applicationSha256")) is str and SHA.fullmatch(application["applicationSha256"]), "Missing application JAR identity")
    return {"status": "consistent", "bundleVersion": "1", "taskId": task, "inputStatusAtExport": receipt["status"],
            "coverage": coverage, "minimumTests": minimum, "applicationIdentitySource": identity["source"],
            "application": application, "provenance": "not_authenticated"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="Delivery directory or ZIP; nothing is extracted or executed")
    args = parser.parse_args(argv)
    try:
        result, code = verify(args.bundle), 0
    except LegacyBundle as error:
        result, code = {"status": "legacy_incomplete", "detail": str(error)}, 2
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile, RuntimeError, expat.ExpatError) as error:
        result, code = {"status": "rejected", "detail": str(error)}, 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return code


if __name__ == "__main__":
    sys.exit(main())
