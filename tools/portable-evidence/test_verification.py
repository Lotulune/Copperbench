"""Independent consumer tests, including forged summaries with synchronized file hashes."""
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

import copperbench_evidence as evidence
from test_collection import fixture


class VerificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source, self.bundle = self.root / "export", self.root / "bundle"
        self.receipt = fixture(self.source, native=True)
        evidence.collect(self.source, self.bundle)

    def synchronize(self, receipt=None, xml=None, *, identity=True):
        """Attack fixture: rewrite all untrusted hashes so raw semantic checks must reject it."""
        receipt = deepcopy(receipt or self.receipt)
        if xml is not None:
            (self.bundle / "gametest-results.xml").write_bytes(xml)
            receipt["reportSha256"] = hashlib.sha256(xml).hexdigest()
        (self.bundle / "verification.json").write_bytes(evidence.json_bytes(receipt))
        manifest = evidence.parse_json((self.bundle / "manifest.json").read_bytes())
        if identity:
            manifest["identity"] = evidence.receipt_identity(receipt)
        manifest["members"] = [evidence.file_entry(name, (self.bundle / name).read_bytes()) for name in evidence.PAYLOADS]
        (self.bundle / "manifest.json").write_bytes(evidence.json_bytes(manifest))

    def reject(self, code, path=None):
        with self.assertRaises(evidence.EvidenceError) as raised:
            evidence.verify(path or self.bundle)
        self.assertEqual(code, raised.exception.code)

    def zip(self, name="bundle.zip", members=None, compression=zipfile.ZIP_STORED):
        path = self.root / name
        if members is None:
            members = [(member, (self.bundle / member).read_bytes()) for member in evidence.LIMITS]
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(path, "w", compression=compression) as archive:
                for member, data in members:
                    archive.writestr(member, data)
        return path

    def test_directory_and_zip_are_read_only_and_have_same_result(self):
        original = {path.name: path.read_bytes() for path in self.bundle.iterdir()}
        result = evidence.verify(self.bundle)
        self.assertEqual("integrity_verified", result["status"])
        self.assertEqual(6, result["rawCounts"]["acceptanceExecuted"])
        self.assertFalse(result["producerAuthenticated"])
        self.assertFalse(result["recipientWorkspaceChecked"])
        self.assertEqual(result, evidence.verify(self.zip(compression=zipfile.ZIP_DEFLATED)))
        self.assertEqual(original, {path.name: path.read_bytes() for path in self.bundle.iterdir()})

    def test_legacy_and_historical_inputs_keep_their_original_scope(self):
        for native, historical in ((False, False), (False, True), (True, True)):
            source = self.root / f"source-{native}-{historical}"
            destination = self.root / f"bundle-{native}-{historical}"
            fixture(source, native=native, historical=historical)
            evidence.collect(source, destination)
            result = evidence.verify(destination)
            self.assertEqual("required_cases_v1" if native else "minimum_only_legacy", result["identity"]["compatibility"])
            self.assertEqual("historical_at_export" if historical else "current_at_export", result["identity"]["inputScope"])

    def test_each_payload_tamper_is_rejected_even_if_member_list_is_rehashed(self):
        for name in evidence.PAYLOADS:
            with self.subTest(member=name):
                original = (self.bundle / name).read_bytes()
                manifest = (self.bundle / "manifest.json").read_bytes()
                (self.bundle / name).write_bytes(original + b" ")
                self.reject("EVIDENCE_HASH_MISMATCH")
                if name != "verification.json":
                    changed = evidence.parse_json(manifest)
                    changed["members"] = [evidence.file_entry(member, (self.bundle / member).read_bytes()) for member in evidence.PAYLOADS]
                    (self.bundle / "manifest.json").write_bytes(evidence.json_bytes(changed))
                    self.reject("EVIDENCE_HASH_MISMATCH")
                (self.bundle / name).write_bytes(original)
                (self.bundle / "manifest.json").write_bytes(manifest)

    def test_repeated_business_identity_is_rejected_with_all_matching_hashes(self):
        xml = ('<testsuite>' + '<testcase name="copperbench_camp_kit:t01"/>' * 6 + '</testsuite>').encode()
        self.synchronize(xml=xml)
        self.reject("EVIDENCE_CASES_DUPLICATE")

    def test_game_resource_duplicate_cannot_hide_behind_another_class(self):
        xml = (self.bundle / "gametest-results.xml").read_bytes().replace(b'</testsuite>',
              b'<testcase classname="other_structure" name="copperbench_camp_kit:t01"/></testsuite>')
        self.synchronize(xml=xml)
        self.reject("EVIDENCE_CASES_DUPLICATE")

    def test_required_case_cannot_be_replaced_with_unrelated_passing_case(self):
        xml = (self.bundle / "gametest-results.xml").read_bytes().replace(b'copperbench_camp_kit:t12', b'copperbench_camp_kit:unrelated')
        self.synchronize(xml=xml)
        self.reject("EVIDENCE_CASES_MISSING")

    def test_skipped_required_case_and_hidden_failures_are_rejected(self):
        original = (self.bundle / "gametest-results.xml").read_bytes()
        for xml, code in ((original.replace(b't12"/>', b't12"><skipped/></testcase>'), "EVIDENCE_CASES_MISSING"),
                          (original.replace(b't12"/>', b't12"><failure/></testcase>'), "EVIDENCE_CASES_FAILED"),
                          (original.replace(b'<testsuite>', b'<testsuite failures="1">'), "EVIDENCE_CASES_FAILED")):
            self.synchronize(xml=xml)
            self.reject(code)

    def test_counts_and_threshold_are_recomputed_from_raw_xml(self):
        changed = deepcopy(self.receipt)
        changed.update(passed=7, acceptanceExecuted=7)
        changed["coverage"]["acceptanceExecuted"] = 7
        changed["requiredCases"]["coverage"]["uniqueExecuted"] = 7
        self.synchronize(changed)
        self.reject("EVIDENCE_COUNTS_INVALID")
        # Legacy evidence has no named gate but must still satisfy its original threshold.
        changed = deepcopy(self.receipt)
        changed.pop("requiredCases")
        changed["environment"].update(generatorId="fabric-1.21.1", minecraftVersion="1.21.1")
        changed.update(minimumTests=7, passed=7, acceptanceExecuted=7)
        changed["coverage"].update(minimumTests=7, acceptanceExecuted=7)
        self.synchronize(changed)
        self.reject("EVIDENCE_CASES_INSUFFICIENT")

    def test_published_manifest_cannot_be_redefined_with_a_new_digest(self):
        changed = deepcopy(self.receipt)
        manifest = changed["requiredCases"]["manifest"]
        manifest["cases"].pop()
        manifest["minimumTests"] = 5
        manifest["sha256"] = evidence.manifest_digest(manifest)
        self.synchronize(changed, identity=False)
        self.reject("EVIDENCE_MANIFEST_INVALID")

    def test_bundle_and_required_binding_tamper_are_rejected(self):
        original = (self.bundle / "manifest.json").read_bytes()
        for field, value in (("sourceSha256", "d" * 64), ("inputScope", "historical_at_export")):
            manifest = evidence.parse_json(original)
            manifest["identity"][field] = value
            (self.bundle / "manifest.json").write_bytes(evidence.json_bytes(manifest))
            self.reject("EVIDENCE_BINDING_INVALID")
        (self.bundle / "manifest.json").write_bytes(original)
        changed = deepcopy(self.receipt)
        changed["requiredCases"]["binding"]["taskId"] = "33333333-3333-4333-8333-333333333333"
        self.synchronize(changed, identity=False)
        self.reject("EVIDENCE_BINDING_INVALID")

    def test_unknown_versions_fail_without_guessing(self):
        original = (self.bundle / "manifest.json").read_bytes()
        manifest = evidence.parse_json(original)
        manifest["bundleVersion"] = "2"
        (self.bundle / "manifest.json").write_bytes(evidence.json_bytes(manifest))
        self.reject("EVIDENCE_VERSION_UNSUPPORTED")
        (self.bundle / "manifest.json").write_bytes(original)
        for field in ("portableEvidenceVersion", "requiredCases"):
            changed = deepcopy(self.receipt)
            if field == "requiredCases":
                changed[field]["schemaVersion"] = "2"
            else:
                changed[field] = "2"
            self.synchronize(changed, identity=False)
            self.reject("EVIDENCE_VERSION_UNSUPPORTED")

    def test_unsafe_zip_names_and_normalized_duplicates_are_rejected(self):
        base = [(name, (self.bundle / name).read_bytes()) for name in evidence.LIMITS]
        for unsafe in ("../verification.json", "/verification.json", "C:/verification.json", "..\\verification.json",
                       "./verification.json", "verification.json ", "a//verification.json"):
            self.reject("EVIDENCE_PATH_INVALID", self.zip(members=[(unsafe, base[0][1]), *base[1:]]))
        for duplicate in ("verification.json", "VERIFICATION.JSON"):
            self.reject("EVIDENCE_PATH_INVALID", self.zip(members=[*base[:3], (duplicate, base[0][1])]))

    def test_manifest_traversal_and_duplicate_members_are_rejected(self):
        original = (self.bundle / "manifest.json").read_bytes()
        for name, code in (("../verified-mod.jar", "EVIDENCE_PATH_INVALID"), ("verification.json", "EVIDENCE_MEMBERS_INVALID")):
            changed = evidence.parse_json(original)
            changed["members"][1]["path"] = name
            (self.bundle / "manifest.json").write_bytes(evidence.json_bytes(changed))
            self.reject(code)

    def test_zip_links_and_special_files_are_rejected(self):
        for mode in (stat.S_IFLNK, stat.S_IFCHR, stat.S_IFDIR):
            info = zipfile.ZipInfo("verified-mod.jar")
            info.create_system = 3
            info.external_attr = (mode | 0o755) << 16
            members = [(info if name == "verified-mod.jar" else name, (self.bundle / name).read_bytes()) for name in evidence.LIMITS]
            self.reject("EVIDENCE_LINK_REJECTED", self.zip(members=members))
        info = zipfile.ZipInfo("verified-mod.jar")
        info.create_system = 0
        info.external_attr = 0x400  # Windows FILE_ATTRIBUTE_REPARSE_POINT.
        members = [(info if name == "verified-mod.jar" else name, (self.bundle / name).read_bytes()) for name in evidence.LIMITS]
        self.reject("EVIDENCE_LINK_REJECTED", self.zip(members=members))

    def test_zip_nul_member_name_cannot_be_truncated_to_a_valid_name(self):
        members = [("verification.jsonX" if name == "verification.json" else name, (self.bundle / name).read_bytes()) for name in evidence.LIMITS]
        path = self.zip(members=members)
        path.write_bytes(path.read_bytes().replace(b"verification.jsonX", b"verification.json\x00"))
        self.reject("EVIDENCE_PATH_INVALID", path)

    def test_directory_symlink_is_rejected(self):
        path = self.bundle / "verified-mod.jar"
        target = self.root / "outside.jar"
        path.rename(target)
        try:
            path.symlink_to(target)
        except OSError as error:
            self.skipTest(f"This host cannot create a link: {error}")
        self.reject("EVIDENCE_LINK_REJECTED")

    def test_directory_and_zip_member_count_and_byte_limits(self):
        (self.bundle / "unexpected.log").write_bytes(b"extra")
        self.reject("EVIDENCE_LIMIT_EXCEEDED")
        self.reject("EVIDENCE_LIMIT_EXCEEDED", self.zip(members=[(p.name, p.read_bytes()) for p in self.bundle.iterdir()]))
        (self.bundle / "unexpected.log").unlink()
        (self.bundle / "gametest-results.xml").write_bytes(b"x" * (evidence.LIMITS["gametest-results.xml"] + 1))
        self.reject("EVIDENCE_LIMIT_EXCEEDED")
        self.reject("EVIDENCE_LIMIT_EXCEEDED", self.zip())

    def test_compression_expansion_is_bounded(self):
        (self.bundle / "gametest-results.xml").write_bytes(b"a" * 100000)
        self.reject("EVIDENCE_LIMIT_EXCEEDED", self.zip(compression=zipfile.ZIP_DEFLATED))

    def test_corrupt_truncated_and_inconsistent_zip_headers_are_rejected(self):
        path = self.zip()
        original = path.read_bytes()
        damaged = bytearray(original)
        index = damaged.index(b"bundleVersion")
        damaged[index] ^= 1
        path.write_bytes(damaged)
        self.reject("EVIDENCE_ZIP_INVALID", path)
        path.write_bytes(original[:-12])
        self.reject("EVIDENCE_ZIP_INVALID", path)
        damaged = bytearray(original)
        damaged[30] ^= 1  # Local filename no longer agrees with its central directory entry.
        path.write_bytes(damaged)
        self.reject("EVIDENCE_ZIP_INVALID", path)

    def test_unsupported_zip_flags_and_compression_are_rejected(self):
        path = self.zip()
        original = path.read_bytes()
        central = original.index(b"PK\x01\x02")
        for offset, value in ((8, 1), (8, 32), (8, 64), (10, 99)):
            changed = bytearray(original)
            struct.pack_into("<H", changed, central + offset, value)
            path.write_bytes(changed)
            self.reject("EVIDENCE_ZIP_INVALID", path)

    def test_json_depth_and_invalid_timestamp_are_rejected(self):
        with self.assertRaises(evidence.EvidenceError) as raised:
            evidence.parse_json(("[" * 65 + "0" + "]" * 65).encode())
        self.assertEqual("EVIDENCE_LIMIT_EXCEEDED", raised.exception.code)
        changed = deepcopy(self.receipt)
        changed["startedAt"] = "2026-99-12T04:01:48Z"
        self.synchronize(changed, identity=False)
        self.reject("EVIDENCE_FORMAT_INVALID")

    def test_xml_limits_and_encoded_dtd_are_rejected(self):
        for xml, code in ((b'<testsuite>' + b'<testcase name="case"/>' * 10001 + b'</testsuite>', "EVIDENCE_LIMIT_EXCEEDED"),
                          (("<testsuite>" * 65 + "</testsuite>" * 65).encode(), "EVIDENCE_XML_INVALID"),
                          ('<!DOCTYPE testsuite [<!ENTITY x "case">]><testsuite><testcase name="&x;"/></testsuite>'.encode("utf-16"), "EVIDENCE_XML_INVALID")):
            with self.assertRaises(evidence.EvidenceError) as raised:
                evidence.raw_report(xml, 1)
            self.assertEqual(code, raised.exception.code)

    def test_shared_identity_vectors_match_the_product_parser(self):
        vectors = json.loads(Path(__file__).with_name("identity-vectors.json").read_text(encoding="utf-8"))["vectors"]
        for vector in vectors:
            with self.subTest(vector=vector["name"]):
                args = (vector["xml"].encode("utf-8"), vector["minimumTests"], vector.get("requiredCases"))
                if vector["expectedOfflineCode"] == "passed":
                    self.assertEqual(vector["acceptanceExecuted"], evidence.raw_report(*args)["acceptanceExecuted"])
                else:
                    with self.assertRaises(evidence.EvidenceError) as raised:
                        evidence.raw_report(*args)
                    self.assertEqual(vector["expectedOfflineCode"], raised.exception.code)

    def test_standalone_cli_in_clean_directory_has_no_project_or_java_dependency(self):
        clean = self.root / "clean"
        clean.mkdir()
        script = clean / "verify.py"
        shutil.copy2(evidence.__file__, script)
        archive = self.zip()
        environment = {**os.environ, "PATH": "", "JAVA_HOME": "", "GRADLE_USER_HOME": str(clean / "no-gradle")}
        command = [sys.executable, "-I", str(script), "verify", str(archive)]
        result = subprocess.run(command, cwd=clean, env=environment, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("integrity_verified", json.loads(result.stdout)["status"])
        archive.write_bytes(archive.read_bytes()[:-1])
        failed = subprocess.run(command, cwd=clean, env=environment, capture_output=True, text=True)
        self.assertEqual(2, failed.returncode)
        self.assertEqual("failed", json.loads(failed.stdout)["status"])
        self.assertEqual({"verify.py"}, {p.name for p in clean.iterdir()})


if __name__ == "__main__":
    unittest.main()
