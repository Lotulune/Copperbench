"""Installed-run provenance regressions; synthetic fixtures are not installed-product acceptance."""
import copy
from contextlib import redirect_stderr
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

SCRIPT = Path(__file__).parents[1] / "verify-agent-readiness-installed.py"
spec = importlib.util.spec_from_file_location("installed_readiness", SCRIPT)
installed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installed)


class InstalledCacheEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.cache = self.root / "isolated cache"
        self.identity = {"sourceCommit": "a" * 40, "candidateSha256": "b" * 64,
                         "applicationSha256": "c" * 64, "launcherSha256": "d" * 64,
                         "sdkSha256": "e" * 64, "fixtureHashes": {"fixture": "f" * 64}}

    def successful_cold_record(self):
        cache, _, _ = installed.prepare_cache("cold", self.cache, None, self.identity)
        payload = self.cache / "caches/modules-2/files-2.1/example/module/1.0/hash/module.jar"
        payload.parent.mkdir(parents=True)
        payload.write_bytes(b"synthetic dependency payload")
        output = self.root / "cold-result"
        output.mkdir()
        manifest = output / "cache-after.json"
        manifest.write_text(json.dumps(installed.cache_inventory(self.cache)), encoding="utf-8")
        cache.update(effectiveHomeVerified=True, afterManifestSha256=installed.digest(manifest))
        record = {**self.identity, "kind": "m3-scripted-installed-replay", "status": "passed", "cache": cache}
        path = output / "result.json"
        path.write_text(json.dumps(record), encoding="utf-8")
        return path, payload

    def test_cold_requires_new_empty_home_without_clearing_existing_data(self):
        cache, environment, before = installed.prepare_cache("cold", self.cache, None, self.identity)
        self.assertEqual("created_empty_before_product_launch", cache["initialCondition"])
        self.assertEqual(0, before["files"])
        self.assertEqual({}, before["artifacts"])
        self.assertEqual(str(self.cache), environment["COPPERBENCH_GRADLE_USER_HOME"])
        self.assertEqual(str(self.cache), environment["GRADLE_USER_HOME"])
        self.assertEqual("false", environment["COPPERBENCH_GRADLE_REUSE_EXTERNAL"])
        sentinel = self.cache / "keep.txt"
        sentinel.write_text("keep", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "new cache directory"):
            installed.prepare_cache("cold", self.cache, None, self.identity)
        self.assertEqual("keep", sentinel.read_text(encoding="utf-8"))

    def test_warm_reuses_proven_cold_payloads_and_preserves_mutable_daemon_logs(self):
        prior, _ = self.successful_cold_record()
        daemon = self.cache / "daemon/log.out"
        daemon.parent.mkdir()
        daemon.write_text("A running daemon updated its log", encoding="utf-8")
        cache, environment, before = installed.prepare_cache("warm", self.cache, prior, self.identity)
        self.assertEqual("retained_payloads_match_successful_cold_run", cache["initialCondition"])
        self.assertEqual(installed.digest(prior), cache["warmFromSha256"])
        self.assertEqual(1, len(before["artifacts"]))
        self.assertEqual("false", environment["COPPERBENCH_GRADLE_REUSE_EXTERNAL"])
        self.assertEqual("A running daemon updated its log", daemon.read_text(encoding="utf-8"))

    def test_warm_rejects_changed_candidate_payload_and_manifest(self):
        prior, payload = self.successful_cold_record()
        changed = {**self.identity, "sdkSha256": "0" * 64}
        with self.assertRaisesRegex(ValueError, "identity changed: sdkSha256"):
            installed.prepare_cache("warm", self.cache, prior, changed)
        payload.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "payload changed"):
            installed.prepare_cache("warm", self.cache, prior, self.identity)
        manifest = prior.parent / "cache-after.json"
        manifest.write_text("{}", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "manifest changed"):
            installed.prepare_cache("warm", self.cache, prior, self.identity)
        self.assertEqual(b"changed", payload.read_bytes())

    def test_retained_or_failed_runs_cannot_be_promoted_to_verified_cold_provenance(self):
        prior, _ = self.successful_cold_record()
        original = installed.read_record(prior)
        for change in ({"status": "failed"}, {"cache": {"mode": "retained"}},
                       {"cache": {**original["cache"], "effectiveHomeVerified": False}}):
            with self.subTest(change=change):
                prior.write_text(json.dumps({**original, **change}), encoding="utf-8")
                with self.assertRaises(ValueError):
                    installed.prepare_cache("warm", self.cache, prior, self.identity)

    def test_runtime_must_match_installed_bytes_selected_home_and_external_reuse_policy(self):
        cache, _, _ = installed.prepare_cache("cold", self.cache, None, self.identity)
        record = {**self.identity, "cache": cache}
        observed = {"status": "succeeded", "data": {
            "application": {"sourceState": "packaged_binary", "applicationSha256": self.identity["applicationSha256"]},
            "execution": {"gradle": {"userHome": str(self.cache), "userHomeSource": "COPPERBENCH_GRADLE_USER_HOME",
                                     "reuseExternalDistributions": False}}}}
        installed.check_running_environment(observed, record)
        self.assertTrue(record["cache"]["effectiveHomeVerified"])
        mutations = (
            lambda value: value["data"]["application"].update(applicationSha256="0" * 64),
            lambda value: value["data"]["application"].update(sourceState="development_or_unverified"),
            lambda value: value["data"]["execution"]["gradle"].update(userHome=str(self.root / "wrong")),
            lambda value: value["data"]["execution"]["gradle"].update(reuseExternalDistributions=True),
            lambda value: value["data"]["execution"]["gradle"].pop("reuseExternalDistributions"),
        )
        for mutate in mutations:
            changed = copy.deepcopy(observed)
            mutate(changed)
            with self.subTest(observed=changed), self.assertRaises(ValueError):
                installed.check_running_environment(changed, record)

    def test_retained_mode_keeps_its_unknown_initial_cache_state(self):
        cache, environment, before = installed.prepare_cache("retained", None, None, self.identity)
        self.assertEqual("unverified_existing_environment", cache["initialCondition"])
        self.assertEqual({}, environment)
        self.assertIsNone(before)
        with self.assertRaises(ValueError):
            installed.prepare_cache("retained", self.cache, None, self.identity)

    def test_output_cache_and_workspace_cannot_overlap_product_or_each_other(self):
        product = self.root / "product"
        for paths in ([self.root / "workspace", self.root / "workspace/cache"], [product / "cache"]):
            with self.subTest(paths=paths), self.assertRaises(ValueError):
                installed.disjoint_paths(paths, [product])
        installed.disjoint_paths([self.root / "workspace", self.cache, self.root / "output"], [product])

    def test_cache_evidence_rejects_links_without_reading_external_payloads(self):
        self.cache.mkdir()
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "secret.jar").write_bytes(b"never traverse")
        link = self.cache / "linked"
        try:
            link.symlink_to(outside, target_is_directory=True)
        except OSError as error:
            self.skipTest(f"Host cannot create directory symlinks: {error}")
        with self.assertRaisesRegex(ValueError, "linked entry"):
            installed.cache_inventory(self.cache)
        self.assertEqual(b"never traverse", (outside / "secret.jar").read_bytes())

    def test_optimized_python_cannot_disable_delivery_assertions(self):
        arguments = ["--product-root", str(self.root), "--candidate-package", str(self.root / "package"),
                     "--candidate-sha256", "a" * 64, "--application-sha256", "c" * 64, "--source-commit", "b" * 40,
                     "--workspace-folder", str(self.root / "workspace"), "--task-authorization", "fixture",
                     "--fixture", str(self.root / "fixture"), "--output", str(self.root / "output")]
        result = subprocess.run([sys.executable, "-O", str(SCRIPT), *arguments], capture_output=True, text=True, timeout=10)
        self.assertEqual(2, result.returncode)
        self.assertIn("Run without Python -O", result.stderr)
        self.assertFalse((self.root / "output").exists())

    def test_wrong_installed_application_is_rejected_before_sdk_import_or_workspace_creation(self):
        product = self.root / "product"
        (product / "lib").mkdir(parents=True)
        (product / "lib/copperbench.jar").write_bytes(b"wrong installed candidate")
        package = self.root / "candidate.zip"
        package.write_bytes(b"frozen package")
        fixture = self.root / "fixture"
        fixture.mkdir()
        arguments = ["--product-root", str(product), "--candidate-package", str(package),
                     "--candidate-sha256", installed.digest(package), "--application-sha256", "0" * 64,
                     "--source-commit", "b" * 40, "--workspace-folder", str(self.root / "workspace"),
                     "--task-authorization", "fixture", "--fixture", str(fixture), "--output", str(self.root / "output")]
        result = subprocess.run([sys.executable, str(SCRIPT), *arguments], capture_output=True, text=True, timeout=10)
        self.assertEqual(2, result.returncode)
        self.assertIn("does not match the frozen candidate hash", result.stderr)
        self.assertFalse((self.root / "output").exists())
        self.assertFalse((self.root / "workspace").exists())

    def test_bootstrap_failure_retains_cache_evidence_and_restores_the_harness_environment(self):
        product = self.root / "product"
        (product / "lib").mkdir(parents=True)
        application = product / "lib/copperbench.jar"
        application.write_bytes(b"synthetic application")
        launcher = product / ("copperbench.exe" if sys.platform == "win32" else "copperbench.sh")
        launcher.write_bytes(b"never launch this fixture")
        sdk = product / "sdk/python/copperbench_native.py"
        sdk.parent.mkdir(parents=True)
        sdk.write_bytes(b"synthetic SDK")
        package = self.root / "candidate.zip"
        package.write_bytes(b"synthetic package")
        fixture = self.root / "fixture"
        fixture.mkdir()
        for name in ("recovery_probe.java", "ContractGameTests.java", "copperbench-tests.json"):
            (fixture / name).write_text("synthetic fixture", encoding="utf-8")
        output = self.root / "output"
        arguments = [str(SCRIPT), "--product-root", str(product), "--candidate-package", str(package),
                     "--candidate-sha256", installed.digest(package), "--application-sha256", installed.digest(application),
                     "--source-commit", "b" * 40, "--workspace-folder", str(self.root / "workspace"),
                     "--task-authorization", "fixture", "--fixture", str(fixture), "--output", str(output),
                     "--cache-mode", "cold", "--gradle-user-home", str(self.cache)]

        def fail_bootstrap(*args, **kwargs):
            self.assertEqual(str(self.cache), os.environ["COPPERBENCH_GRADLE_USER_HOME"])
            self.assertEqual("false", os.environ["COPPERBENCH_GRADLE_REUSE_EXTERNAL"])
            raise OSError("synthetic bootstrap failure")

        with patch.object(sys, "argv", arguments), patch.object(sys, "path", list(sys.path)), \
                patch.dict(sys.modules, {"copperbench_native": SimpleNamespace(__file__=str(sdk))}), \
                patch.dict(os.environ, {"GRADLE_USER_HOME": "original caller cache"}), \
                patch.object(installed.subprocess, "run", side_effect=fail_bootstrap), redirect_stderr(io.StringIO()):
            before_environment = dict(os.environ)
            self.assertEqual(1, installed.main())
            self.assertEqual(before_environment, dict(os.environ))
        result = installed.read_record(output / "result.json")
        self.assertEqual("failed", result["status"])
        self.assertEqual("cold", result["cache"]["mode"])
        self.assertFalse(result["cache"]["effectiveHomeVerified"])
        self.assertEqual(0, installed.read_record(output / "cache-before.json")["files"])
        self.assertEqual(installed.digest(output / "cache-after.json"), result["cache"]["afterManifestSha256"])
        self.assertTrue((output / "failure.txt").is_file())
        self.assertTrue(self.cache.is_dir())


if __name__ == "__main__":
    unittest.main()
