"""Real filesystem boundaries for collection; fixtures are explicitly synthetic exports."""
from copy import deepcopy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import copperbench_evidence as evidence


def fixture(root, *, native=False, historical=False):
    root.mkdir()
    numbers = (1, 2, 3, 4, 11, 12) if native else range(1, 6)
    cases = [{"className": "", "name": f"copperbench_camp_kit:t{number:02d}"} for number in numbers]
    xml = ("<testsuite>" + "".join(f'<testcase name="{case["name"]}"/>' for case in cases) + "</testsuite>").encode()
    jar = io.BytesIO()
    with zipfile.ZipFile(jar, "w") as archive:
        archive.writestr("fixture.txt", "Synthetic unit test JAR, not a Minecraft acceptance artifact.")
    receipt = {"schemaVersion": "1.0", "taskId": "11111111-1111-4111-8111-111111111111",
               "status": "passed_historical_input" if historical else "passed_current_input", "scope": evidence.SCOPE,
               "artifactPath": "verified-mod.jar", "reportPath": "gametest-results.xml",
               "artifactSha256": hashlib.sha256(jar.getvalue()).hexdigest(), "reportSha256": hashlib.sha256(xml).hexdigest(),
               "passed": len(cases), "failed": 0, "skipped": 0, "acceptanceExecuted": len(cases),
               "environment": {"generatorId": "fabric-26.3" if native else "fabric-1.21.1", "loader": "fabric",
                               "minecraftVersion": "26.3" if native else "1.21.1", "javaRelease": 25 if native else 21},
               "sourceSha256": "a" * 64, "portableEvidenceVersion": "1", "environmentSource": "acceptance_record",
               "mode": "packaged_jar", "minimumTests": len(cases), "startedAt": "2026-10-12T04:01:48.383664800Z",
               "processExitCode": 0, "coverage": {"minimumTests": len(cases), "acceptanceExecuted": len(cases),
                                                  "shortfall": 0, "status": "sufficient"}}
    if native:
        manifest = {"schemaVersion": "1", "id": "camp-kit-native-v1", "identityKind": "gametest_resource",
                    "minimumTests": 6, "cases": cases}
        manifest["sha256"] = evidence.manifest_digest(manifest)
        receipt["requiredCases"] = {"schemaVersion": "1", "manifest": manifest,
            "binding": {"taskId": receipt["taskId"], "workspaceId": "22222222-2222-4222-8222-222222222222",
                        "workspaceRevision": 3, "sourceSha256": receipt["sourceSha256"],
                        "environment": deepcopy(receipt["environment"]), "frozenSha256": "b" * 64},
            "coverage": {"manifestSha256": manifest["sha256"], "required": 6, "minimumTests": 6, "uniqueExecuted": 6,
                         "shortfall": 0, "reasonCode": "GAMETEST_PASSED", "status": "passed", "duplicates": [], "missingCases": []}}
    (root / "verified-mod.jar").write_bytes(jar.getvalue())
    (root / "gametest-results.xml").write_bytes(xml)
    (root / "verification.json").write_bytes(evidence.json_bytes(receipt))
    return receipt


class CollectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "export"
        self.destination = self.root / "delivery"

    def reject(self, code, receipt=None):
        if receipt is not None:
            (self.source / "verification.json").write_bytes(evidence.json_bytes(receipt))
        with self.assertRaises(evidence.EvidenceError) as raised:
            evidence.collect(self.source, self.destination)
        self.assertEqual(code, raised.exception.code)
        self.assertFalse(self.destination.exists())
        self.assertEqual([], list(self.root.glob(".copperbench-evidence-*")))

    def test_preserves_original_bytes_and_copies_only_three_known_members(self):
        receipt = fixture(self.source)
        original = (" \n" + json.dumps(receipt, separators=(",", ":")) + "\n\n").encode()
        (self.source / "verification.json").write_bytes(original)
        (self.source / "unreviewed.log").write_text("private local path and credentials", encoding="utf-8")
        before = {p.name: p.read_bytes() for p in self.source.iterdir()}
        result = evidence.collect(self.source, self.destination)
        self.assertEqual("collected", result["status"])
        self.assertEqual(set(evidence.LIMITS), {p.name for p in self.destination.iterdir()})
        self.assertEqual(original, (self.destination / "verification.json").read_bytes())
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.source.iterdir()})
        manifest = evidence.parse_json((self.destination / "manifest.json").read_bytes())
        for member in manifest["members"]:
            self.assertEqual(member, evidence.file_entry(member["path"], before[member["path"]]))
        self.assertEqual("minimum_only_legacy", manifest["identity"]["compatibility"])
        self.assertNotIn("javaVersion", manifest["identity"]["environment"])
        self.assertNotIn("fabricApiVersion", manifest["identity"]["environment"])

    def test_required_manifest_binds_actual_observations_without_inferred_versions(self):
        receipt = fixture(self.source, native=True)
        result = evidence.collect(self.source, self.destination)
        self.assertEqual("required_cases_v1", result["identity"]["compatibility"])
        self.assertEqual(receipt["requiredCases"]["binding"], result["identity"]["binding"])
        self.assertEqual("8c0ebf3b57be5fa982ef8cf8ea6675966996115754601cd82b3aeb8ac707bfe9",
                         result["identity"]["requiredCaseManifest"]["sha256"])

    def test_historical_scope_is_preserved(self):
        fixture(self.source, historical=True)
        self.assertEqual("historical_at_export", evidence.collect(self.source, self.destination)["identity"]["inputScope"])

    def test_missing_receipt_leaves_no_delivery(self):
        fixture(self.source)
        (self.source / "verification.json").unlink()
        with self.assertRaises(FileNotFoundError):
            evidence.collect(self.source, self.destination)
        self.assertFalse(self.destination.exists())

    def test_api_envelope_cannot_replace_portable_file(self):
        receipt = fixture(self.source)
        self.reject("EVIDENCE_LEGACY_UNSUPPORTED", {"data": {"verifiedExport": receipt}, "status": "succeeded"})

    def test_unknown_version_is_not_guessed(self):
        receipt = fixture(self.source)
        receipt["portableEvidenceVersion"] = "2"
        self.reject("EVIDENCE_VERSION_UNSUPPORTED", receipt)

    def test_legacy_minimum_cannot_be_promoted_to_263(self):
        receipt = fixture(self.source)
        receipt["environment"]["minecraftVersion"] = "26.3"
        self.reject("EVIDENCE_REQUIRED_CASES_MISSING", receipt)

    def test_changed_artifact_or_report_is_rejected(self):
        fixture(self.source)
        for name in ("verified-mod.jar", "gametest-results.xml"):
            with self.subTest(member=name):
                original = (self.source / name).read_bytes()
                (self.source / name).write_bytes(original + b"changed")
                self.reject("EVIDENCE_HASH_MISMATCH")
                (self.source / name).write_bytes(original)

    def test_source_changes_during_copy_cannot_publish(self):
        fixture(self.source)
        original_read = evidence.read_file
        for target in evidence.PAYLOADS:
            with self.subTest(member=target):
                original = (self.source / target).read_bytes()
                calls = 0
                def read(path, limit):
                    nonlocal calls
                    data = original_read(path, limit)
                    if path == self.source / target:
                        calls += 1
                        if calls == 1:
                            path.write_bytes(data + b" ")
                    return data
                with patch.object(evidence, "read_file", side_effect=read):
                    self.reject("EVIDENCE_SOURCE_CHANGED")
                (self.source / target).write_bytes(original)

    def test_partial_copy_and_publish_failure_remove_staging(self):
        fixture(self.source)
        with patch.object(Path, "rename", side_effect=OSError("controlled publish failure")):
            with self.assertRaises(OSError):
                evidence.collect(self.source, self.destination)
        self.assertFalse(self.destination.exists())
        self.assertEqual([], list(self.root.glob(".copperbench-evidence-*")))

    def test_missing_member_after_staging_has_started_leaves_no_delivery(self):
        fixture(self.source)
        (self.source / "gametest-results.xml").unlink()
        with self.assertRaises(FileNotFoundError):
            evidence.collect(self.source, self.destination)
        self.assertFalse(self.destination.exists())
        self.assertEqual([], list(self.root.glob(".copperbench-evidence-*")))

    def test_existing_delivery_cannot_be_overwritten(self):
        fixture(self.source)
        self.destination.mkdir()
        marker = self.destination / "keep"
        marker.write_bytes(b"unrelated")
        with self.assertRaises(evidence.EvidenceError) as raised:
            evidence.collect(self.source, self.destination)
        self.assertEqual("EVIDENCE_DESTINATION_EXISTS", raised.exception.code)
        self.assertEqual(b"unrelated", marker.read_bytes())

    def test_mismatched_task_input_environment_or_manifest_is_rejected(self):
        original = fixture(self.source, native=True)
        for field, replacement in (("taskId", "33333333-3333-4333-8333-333333333333"),
                                   ("sourceSha256", "c" * 64), ("environment", {"loader": "neoforge"})):
            with self.subTest(field=field):
                changed = deepcopy(original)
                changed["requiredCases"]["binding"][field] = replacement
                self.reject("EVIDENCE_BINDING_INVALID", changed)
        changed = deepcopy(original)
        changed["requiredCases"]["manifest"]["cases"][-1]["name"] = "copperbench_camp_kit:t99"
        changed["requiredCases"]["manifest"]["sha256"] = evidence.manifest_digest(changed["requiredCases"]["manifest"])
        self.reject("EVIDENCE_MANIFEST_INVALID", changed)

    def test_unknown_fields_and_local_paths_are_not_bundled(self):
        original = fixture(self.source)
        for field, value in (("exportDirectory", "C:/private"), ("credentials", "private")):
            self.reject("EVIDENCE_FORMAT_INVALID", {**original, field: value})
        changed = deepcopy(original)
        changed["environment"]["javaVersion"] = "C:/private/java"
        self.reject("EVIDENCE_FORMAT_INVALID", changed)
        self.reject("EVIDENCE_PATH_INVALID", {**original, "artifactPath": "../outside.jar"})

    def test_links_are_rejected(self):
        fixture(self.source)
        path = self.source / "verified-mod.jar"
        outside = self.root / "outside.jar"
        path.rename(outside)
        try:
            path.symlink_to(outside)
        except OSError as error:
            self.skipTest(f"This host cannot create a link: {error}")
        self.reject("EVIDENCE_LINK_REJECTED")

    def test_json_ambiguities_are_rejected(self):
        for raw in (b'{"status":"failed","status":"passed"}', b'{"count":NaN}', b'\xff'):
            with self.assertRaises(evidence.EvidenceError):
                evidence.parse_json(raw)

    def test_cli_failure_has_stable_nonzero_json_result(self):
        result = subprocess.run([sys.executable, str(Path(evidence.__file__)), "collect", str(self.source), str(self.destination)],
                                capture_output=True, text=True)
        self.assertEqual(2, result.returncode)
        self.assertEqual("EVIDENCE_SOURCE_INVALID", json.loads(result.stdout)["code"])
        self.assertFalse(self.destination.exists())


if __name__ == "__main__":
    unittest.main()
