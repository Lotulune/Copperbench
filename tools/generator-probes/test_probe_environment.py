import copy
import hashlib
from pathlib import Path
import tempfile
import unittest

from probe_environment import ProbeFailure, validate_lock, verify_dependency_graph, verify_inputs


class ProbeInputTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.cache = self.root / "cache"
        self.project = self.root / "project"
        self.cache.mkdir()
        self.project.mkdir()
        (self.cache / "library.jar").write_bytes(b"artifact")
        (self.project / "build.gradle").write_bytes(b"source")
        self.lock = {
            "schemaVersion": 1, "generatorId": "fabric-26.3",
            "versions": {"minecraft": "26.3", "gradle": "9.6.0", "loader": "0.19.5",
                         "buildPlugin": "1.17.21", "fabricApi": "0.162.0+26.3"},
            "generatorSource": {"commit": "1" * 40},
            "projectFiles": {"build.gradle": hashlib.sha256(b"source").hexdigest()},
            "artifacts": [{"file": "library.jar", "size": 8, "url": "https://example.invalid/library.jar",
                           "localSha256": hashlib.sha256(b"artifact").hexdigest(),
                           "upstreamSha1": hashlib.sha1(b"artifact").hexdigest()}],
        }

    def assert_rejected(self, code, action):
        with self.assertRaises(ProbeFailure) as context:
            action()
        self.assertEqual(code, context.exception.code)

    def test_frozen_release_build_metadata_is_not_a_dynamic_version(self):
        self.assertEqual({"library.jar": self.lock["artifacts"][0]["localSha256"]},
                         verify_inputs(self.lock, self.cache, self.project))

    def test_dynamic_or_ranged_versions_are_rejected(self):
        for version in ("+", "1.17.+", "1.17-SNAPSHOT", "latest.release", "^26.3", "[26.3,)"):
            with self.subTest(version=version):
                lock = copy.deepcopy(self.lock)
                lock["versions"]["buildPlugin"] = version
                self.assert_rejected("DYNAMIC_VERSION_FORBIDDEN", lambda: validate_lock(lock))

    def test_missing_artifact_does_not_create_or_download_a_replacement(self):
        (self.cache / "library.jar").unlink()
        self.assert_rejected("DEPENDENCY_MISSING", lambda: verify_inputs(self.lock, self.cache, self.project))
        self.assertEqual([], list(self.cache.iterdir()))

    def test_same_size_tampering_is_rejected(self):
        (self.cache / "library.jar").write_bytes(b"tampered")
        self.assert_rejected("DEPENDENCY_DIGEST_MISMATCH", lambda: verify_inputs(self.lock, self.cache, self.project))

    def test_upstream_and_local_digests_are_independently_checked(self):
        self.lock["artifacts"][0]["upstreamSha1"] = "0" * 40
        self.assert_rejected("UPSTREAM_DIGEST_MISMATCH", lambda: verify_inputs(self.lock, self.cache, self.project))

    def test_source_changes_invalidate_the_frozen_probe(self):
        (self.project / "build.gradle").write_bytes(b"changed")
        self.assert_rejected("PROBE_SOURCE_CHANGED", lambda: verify_inputs(self.lock, self.cache, self.project))

    def test_path_escape_and_duplicate_artifacts_are_rejected(self):
        for name in ("../outside.jar", "/outside.jar", "C:/outside.jar", "a//library.jar", "a\\library.jar"):
            with self.subTest(name=name):
                lock = copy.deepcopy(self.lock)
                lock["artifacts"][0]["file"] = name
                self.assert_rejected("LOCK_PATH_INVALID", lambda: validate_lock(lock))
        self.lock["artifacts"].append(copy.deepcopy(self.lock["artifacts"][0]))
        self.assert_rejected("LOCK_ARTIFACT_DUPLICATE", lambda: validate_lock(self.lock))

    def test_unknown_lock_version_is_rejected(self):
        self.lock["schemaVersion"] = 2
        self.assert_rejected("LOCK_VERSION_UNSUPPORTED", lambda: validate_lock(self.lock))

    def test_neoforge_linux_graph_is_required_and_tampering_is_rejected(self):
        self.lock["generatorId"] = "neoforge-26.3"
        self.lock["dependencyGraph"] = {}
        for name in ("gradle.lockfile", "gradle/verification-metadata.xml", "gradle/linux.lockfile"):
            path = self.project / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"frozen")
            self.lock["dependencyGraph"][name] = hashlib.sha256(b"frozen").hexdigest()
        verify_dependency_graph(self.lock, self.project)
        (self.project / "gradle/linux.lockfile").write_bytes(b"edited")
        self.assert_rejected("DEPENDENCY_GRAPH_CHANGED", lambda: verify_dependency_graph(self.lock, self.project))
        (self.project / "gradle/linux.lockfile").unlink()
        self.assert_rejected("DEPENDENCY_GRAPH_UNLOCKED", lambda: verify_dependency_graph(self.lock, self.project))

    def test_dependency_graph_must_be_frozen_and_unchanged(self):
        self.assert_rejected("DEPENDENCY_GRAPH_UNLOCKED", lambda: verify_dependency_graph(self.lock, self.project))
        self.lock["dependencyGraph"] = {}
        for name in ("gradle.lockfile", "gradle/verification-metadata.xml"):
            path = self.project / name
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(b"frozen")
            self.lock["dependencyGraph"][name] = hashlib.sha256(b"frozen").hexdigest()
        verify_dependency_graph(self.lock, self.project)
        (self.project / "gradle.lockfile").write_bytes(b"edited")
        self.assert_rejected("DEPENDENCY_GRAPH_CHANGED", lambda: verify_dependency_graph(self.lock, self.project))


if __name__ == "__main__":
    unittest.main()
