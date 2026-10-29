import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("acceptance_measurement", Path(__file__).parents[1] / "performance/acceptance-measurement.py")
measurement = importlib.util.module_from_spec(spec)
spec.loader.exec_module(measurement)


def receipt(lines, state="running"):
    return {"data": {"task": {"id": "original-task", "kind": "build", "state": state},
                     "logs": [{"sequence": index, "text": line} for index, line in lines]}}


class AcceptanceMeasurementTest(unittest.TestCase):
    def test_cold_entry_uses_only_hash_checked_bundled_jbr_and_exact_public_sdk_argv(self):
        with tempfile.TemporaryDirectory() as directory:
            product = Path(directory).resolve()
            java = product / "jdk/bin/java.exe"
            java.parent.mkdir(parents=True)
            java.write_bytes(b"frozen runtime fixture")
            runtime_hash = measurement.digest(java)
            entry = measurement.cold_host_entry(product, {"jdk/bin/java.exe": {"sha256": runtime_hash}})
            self.assertEqual([str(java), "--add-opens=java.base/java.lang=ALL-UNNAMED",
                              "--enable-native-access=ALL-UNNAMED,jcef", "-Dcopperbench.productShell=true",
                              "-cp", str(product / "lib/*"), "net.mcreator.Launcher"], entry["argv"])
            self.assertEqual(runtime_hash, entry["runtime"]["sha256"])
            self.assertEqual("jdk/bin/java.exe", entry["runtime"]["path"])
            self.assertNotIn(str(product / "copperbench.exe"), entry["argv"])
            self.assertIn("No automatic fallback", entry["exeStdioAcceptance"])

    def test_changed_bundled_runtime_rejects_the_cold_entry_without_returning_launcher(self):
        with tempfile.TemporaryDirectory() as directory:
            product = Path(directory).resolve()
            java = product / "jdk/bin/java.exe"
            java.parent.mkdir(parents=True)
            java.write_bytes(b"original runtime fixture")
            expected = {"jdk/bin/java.exe": {"sha256": measurement.digest(java)}}
            java.write_bytes(b"changed runtime fixture")
            with self.assertRaisesRegex(ValueError, "Frozen candidate bytes changed: jdk/bin/java.exe"):
                measurement.cold_host_entry(product, expected)

    def test_exact_gradle_durations_and_download_receipt_arrivals_remain_separate(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory).resolve()
            times = iter([10, 15])
            capture = measurement.StageCapture(clock=lambda: next(times))
            settings = {"phase": "settings", "event": "observed", "gradleUserHome": str(cache)}
            resolve = {"phase": "resolution", "event": "end", "id": 1, "elapsedMs": 250, "fileCount": 2}
            capture.receive(receipt([(1, measurement.MARKER + json.dumps(settings)),
                                     (2, "Downloading https://example.invalid/module.pom to isolated-cache"),
                                     (3, measurement.MARKER + json.dumps(resolve))], "succeeded"))
            report = capture.summary(cache)
            self.assertTrue(report["gradleHomeConfirmed"])
            self.assertEqual(1, report["resolutionCount"])
            self.assertEqual(1, report["dependencyDownloadLogCount"])
            self.assertEqual(5000, report["stageEvents"][1]["sdkObservedAfterMs"])
            self.assertEqual(250, report["stageEvents"][1]["elapsedMs"])
            self.assertIsNone(report["networkTransferDurationMs"])
            self.assertIsNone(report["networkTransferredBytes"])

    def test_resolution_receipts_reveal_external_cache_borrowing(self):
        with tempfile.TemporaryDirectory() as directory:
            cache = Path(directory).resolve() / "isolated"
            borrowed = str(Path(directory).resolve() / "shared/module.jar")
            event = {"phase": "resolution", "event": "end", "id": 1, "elapsedMs": 1,
                     "files": [{"path": borrowed, "bytes": 8}]}
            capture = measurement.StageCapture()
            capture.receive(receipt([(1, measurement.MARKER + json.dumps(event))]))
            report = capture.summary(cache)
            self.assertEqual([borrowed], report["unexpectedExternalResolutionPaths"])
            self.assertEqual(8, report["resolutionFileBytes"])

    def test_duplicate_logs_wrong_cache_and_bad_stage_duration_never_become_confirmed(self):
        capture = measurement.StageCapture()
        capture.receive(receipt([(1, "original log")]))
        with self.assertRaisesRegex(ValueError, "repeated"):
            capture.receive(receipt([(1, "original log")]))
        self.assertEqual(1, capture.repeated_logs)
        with tempfile.TemporaryDirectory() as directory:
            report = capture.summary(Path(directory).resolve())
            self.assertFalse(report["gradleHomeConfirmed"])
        invalid = measurement.MARKER + json.dumps({"phase": "task", "event": "end", "elapsedMs": -1})
        with self.assertRaisesRegex(ValueError, "invalid monotonic"):
            measurement.StageCapture().receive(receipt([(1, invalid)]))

    def test_scoped_paths_and_materialization_counts_do_not_claim_network_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            with self.assertRaises(ValueError):
                measurement.child_path(root, str(root.parent / "shared-cache"))
            cache = measurement.child_path(root, str(root / "cache"))
            cache.mkdir()
            (cache / "module.jar").write_bytes(b"fixture bytes")
            inventory = measurement.cache_inventory(cache)
            self.assertEqual(1, inventory["fileCount"])
            self.assertEqual(13, inventory["bytes"])
            self.assertIn("not network", inventory["scope"])

    def test_retained_diagnostics_redact_credentials_but_preserve_stage_and_counts(self):
        original = {"token": "private", "operation": "build_workspace", "count": 3,
                    "line": "Downloading https://user:private@example.invalid/a.jar?token=private C:/acceptance/cache"}
        sanitized = measurement.sanitized(original, [(Path("C:/acceptance"), "<acceptance-root>")])
        self.assertEqual("<redacted>", sanitized["token"])
        self.assertEqual("build_workspace", sanitized["operation"])
        self.assertEqual(3, sanitized["count"])
        self.assertNotIn("private", json.dumps(sanitized))
        self.assertIn("Downloading", sanitized["line"])


if __name__ == "__main__":
    unittest.main()
