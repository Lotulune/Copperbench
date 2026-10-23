#!/usr/bin/env python3
"""Cloud-only lifecycle acceptance of public release assets, with no product rebuild."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import time
import traceback

from common import command, require, save, sha256

REPOSITORY = "Lotulune/Copperbench"
REGISTRY_KEY = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\Copperbench"


def release_version(tag):
    require(re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag), "Use an exact semantic release tag")
    return tag[1:]


def remove_owned_tree(path, root):
    path, root = Path(path), Path(root).resolve(strict=True)
    resolved = path.resolve(strict=True)
    require(resolved != root and root in resolved.parents and not path.is_symlink()
            and not getattr(path, "is_junction", lambda: False)(), "Cleanup path is outside this run")
    shutil.rmtree(path)


def verify_provenance(results, digest, source, workflow, ref, run_id, attempt=None):
    """Require a verified certificate's source and workflow, not only editable predicate fields."""
    expected_workflow = f"https://github.com/{REPOSITORY}/.github/workflows/{workflow}@{ref}"
    prefix = f"https://github.com/{REPOSITORY}/actions/runs/{run_id}/attempts/"
    for entry in results:
        result = entry.get("verificationResult", {})
        certificate = result.get("signature", {}).get("certificate", {})
        invocation = certificate.get("runInvocationURI", "")
        if (certificate.get("sourceRepositoryURI") == f"https://github.com/{REPOSITORY}"
                and certificate.get("sourceRepositoryDigest") == source
                and certificate.get("sourceRepositoryRef") == ref
                and certificate.get("subjectAlternativeName") == expected_workflow
                and certificate.get("runnerEnvironment") == "github-hosted"
                and invocation.startswith(prefix)
                and (attempt is None or invocation == prefix + str(attempt))
                and any(subject.get("digest", {}).get("sha256") == digest
                        for subject in result.get("statement", {}).get("subject", []))):
            return invocation
    raise AssertionError("Verified attestation does not bind the expected artifact, source and workflow run")


def windows_registration():
    import winreg
    result = {}
    for hive_name, hive in [("HKCU", winreg.HKEY_CURRENT_USER), ("HKLM", winreg.HKEY_LOCAL_MACHINE)]:
        for view, flag in [("64", winreg.KEY_WOW64_64KEY), ("32", winreg.KEY_WOW64_32KEY)]:
            try:
                with winreg.OpenKey(hive, REGISTRY_KEY, 0, winreg.KEY_READ | flag) as key:
                    values = {}
                    for name in ["InstallLocation", "DisplayVersion", "UninstallString"]:
                        values[name] = winreg.QueryValueEx(key, name)[0]
                    result[hive_name + "-" + view] = values
            except FileNotFoundError:
                pass
    return result


class Acceptance:
    def __init__(self, args):
        self.args = args
        self.repo = Path(__file__).resolve().parents[2]
        self.root = Path(os.environ["RUNNER_TEMP"]).resolve() / "cba"
        require(not self.root.exists(), "Acceptance root must not already exist")
        self.root.mkdir()
        self.evidence = self.repo / "output/minecraft-validation/cloud-release" / args.variant
        require(not self.evidence.exists(), "Evidence directory must not already exist")
        self.evidence.mkdir(parents=True)
        self.environment = dict(os.environ)
        for name, relative in {"COPPERBENCH_HOME": "preferences", "XDG_CONFIG_HOME": "config", "XDG_DATA_HOME": "data",
                               "XDG_STATE_HOME": "state", "XDG_CACHE_HOME": "cache", "GRADLE_USER_HOME": "gradle",
                               "COPPERBENCH_GRADLE_USER_HOME": "gradle"}.items():
            self.environment[name] = str(self.root / relative)
        self.environment["COPPERBENCH_GRADLE_REUSE_EXTERNAL"] = "false"
        self.environment["PYTHONUTF8"] = "1"
        self.environment["LIBGL_ALWAYS_SOFTWARE"] = "1"
        self.sentinels = {}
        self.bundles = {}
        self.shortcuts = {}
        self.record = {"kind": "public-release-cloud-acceptance", "status": "running", "variant": args.variant,
                       "nativeMode": args.native_mode,
                       "releaseTag": args.release_tag, "previousReleaseTag": args.previous_release_tag,
                       "os": platform.platform(), "runnerOS": os.environ.get("RUNNER_OS"),
                       "harnessCommit": os.environ["GITHUB_SHA"], "workflowRun": os.environ["GITHUB_RUN_ID"],
                       "workflowAttempt": os.environ["GITHUB_RUN_ATTEMPT"], "startedAt": datetime.now(timezone.utc).isoformat(),
                       "releases": {}, "checks": [], "unverified": ["Windows 11 desktop", "MSIX installation", "UAC elevation",
                       "GNOME Wayland/Xorg desktop", "Player controls and world save/reopen"],
                       "uiScope": "Published Swing components and offscreen preview; no human interaction or desktop certification"}
        if os.name == "nt":
            self.record["unverified"].append("Non-ASCII Windows installation paths (external Java test-driver argument encoding)")

    def run(self, argv, label, *, cwd=None, timeout=900):
        return command(argv, self.evidence / (label + ".log"), cwd=cwd, env=self.environment, timeout=timeout)

    def checkpoint(self, name, **data):
        self.record["checks"].append({"name": name, "status": "passed", **data})
        save(self.evidence / "result.json", self.record)
        print("PASS", name, flush=True)

    def sentinel(self, path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode("utf-8"))
        self.sentinels[str(path)] = sha256(path)

    def preserved(self, label):
        for path, digest in self.sentinels.items():
            require(Path(path).is_file() and sha256(path) == digest, "User data changed or disappeared: " + path)
        self.checkpoint(label, preservedFiles=dict(self.sentinels))

    def download(self, tag):
        version = release_version(tag)
        actual_tag = tag if os.name == "nt" else tag + "-linux-stable"
        dest = self.root / "downloads" / tag
        dest.mkdir(parents=True)
        info = json.loads(self.run(["gh", "api", f"repos/{REPOSITORY}/releases/tags/{actual_tag}"], tag + "-release-api"))
        require(info["tag_name"] == actual_tag and not info["draft"] and not info["prerelease"], "Release is not public stable")
        source = json.loads(self.run(["gh", "api", f"repos/{REPOSITORY}/commits/{tag}"], tag + "-source-api"))["sha"]
        metadata_name = "RELEASE-METADATA.json" if os.name == "nt" else "LINUX-CANDIDATE-METADATA.json"
        suffix = ".exe" if os.name == "nt" else (".deb" if self.args.variant == "deb" else ".Setup.tar.gz")
        candidates = [a for a in info["assets"] if a["name"].endswith(suffix)]
        require(len(candidates) == 1, "Release has no unique installer asset")
        package_asset = candidates[0]
        selected = [next(a for a in info["assets"] if a["name"] == metadata_name), package_asset]
        assets = []
        for asset in selected:
            require(Path(asset["name"]).name == asset["name"], "Asset name is not a leaf filename")
            self.run(["gh", "release", "download", actual_tag, "--repo", REPOSITORY, "--pattern", asset["name"],
                      "--dir", dest], tag + "-download-" + ("metadata" if asset["name"] == metadata_name else "package"), timeout=900)
            path = dest / asset["name"]
            digest = sha256(path)
            require(path.stat().st_size == asset["size"] and asset.get("digest") == "sha256:" + digest,
                    "Public asset digest or size mismatch")
            assets.append({"name": asset["name"], "size": asset["size"], "sha256": digest, "url": asset["browser_download_url"]})
        metadata = json.loads((dest / metadata_name).read_text(encoding="utf-8-sig"))
        save(self.evidence / tag / metadata_name, metadata)
        if os.name == "nt":
            require(metadata["tag"] == tag and metadata["commit"] == source and metadata["binarySource"]["commit"] == source,
                    "Windows metadata source does not match the release tag")
            workflow, ref, run_id, attempt = "deploy.yml", "refs/tags/" + tag, metadata["workflowRun"], None
        else:
            require(metadata["productVersion"] == version and metadata["source"]["commit"] == source,
                    "Linux metadata source does not match the release tag")
            role = "deb" if self.args.variant == "deb" else "setup"
            expected = metadata["assets"][role]
            require(expected["name"].replace(" ", ".") == package_asset["name"] and expected["sha256"] == assets[1]["sha256"]
                    and expected["size"] == assets[1]["size"], "Linux package differs from the frozen candidate")
            workflow, ref = "stage15-linux-candidate.yml", "refs/heads/main"
            run_id, attempt = metadata["source"]["workflowRunId"], metadata["source"]["workflowRunAttempt"]
        for index, asset in enumerate(assets):
            output = self.run(["gh", "attestation", "verify", dest / asset["name"], "--repo", REPOSITORY,
                              "--source-digest", source, "--source-ref", ref, "--signer-workflow",
                              f"{REPOSITORY}/.github/workflows/{workflow}", "--deny-self-hosted-runners", "--format", "json"],
                             tag + f"-attestation-{index}", timeout=300)
            results = json.loads(output)
            asset["attestationRun"] = verify_provenance(results, asset["sha256"], source, workflow, ref, run_id, attempt)
        self.record["releases"][tag] = {"publicTag": actual_tag, "sourceCommit": source, "assets": assets}
        self.checkpoint(tag + "-public-package-identity", sourceCommit=source)
        return dest / package_asset["name"]

    def extract(self, tag, archive):
        destination = self.root / "unpacked" / tag
        destination.mkdir(parents=True)
        if os.name == "nt":
            seven = shutil.which("7z") or r"C:\Program Files\7-Zip\7z.exe"
            self.run([seven, "x", "-y", "-o" + str(destination), archive], tag + "-extract", timeout=900)
        else:
            with tarfile.open(archive) as bundle:
                bundle.extractall(destination, filter="data")
        manifests = list(destination.rglob("payload.tsv"))
        require(len(manifests) == 1, "Expected one published payload manifest")
        folder = manifests[0].parent
        setup = folder / ("setup.jar" if os.name == "nt" else "copperbench-setup.jar")
        require(setup.is_file() and (folder / "payload/lib/copperbench.jar").is_file(), "Incomplete installer payload")
        tools = self.root / "tools" / tag
        tools.mkdir(parents=True, exist_ok=True)
        copied = tools / "published-setup.jar"
        shutil.copy2(setup, copied)
        self.bundles[tag] = {"root": destination, "payload": folder / "payload", "manifest": manifests[0], "jar": copied,
                             "registrar": archive, "classes": tools / "classes"}
        self.checkpoint(tag + "-extracted-setup-identity", setupJarSha256=sha256(copied), payloadManifestSha256=sha256(manifests[0]))
        return self.bundles[tag]

    def compile_probe(self, tag):
        bundle = self.bundles[tag]
        bundle["classes"].mkdir(exist_ok=True)
        self.run(["javac", "-cp", bundle["jar"], "-d", bundle["classes"],
                  self.repo / "scripts/cloud-acceptance/InstallerProbe.java",
                  self.repo / "platform/installer/test/InstallerInteractionTest.java"], tag + "-compile-probes")

    def probe(self, tag, arguments, label):
        bundle = self.bundles[tag]
        output = self.run(["java", "-cp", os.pathsep.join([str(bundle["classes"]), str(bundle["jar"])]),
                           "dev.copperbench.installer.InstallerProbe", *arguments], label)
        return json.loads(next(line for line in output.splitlines() if line.startswith('{')))

    def registration(self, target, version):
        if os.name == "nt":
            registration = windows_registration()
            values = registration["HKCU-64"]
            require(Path(values["InstallLocation"]).resolve() == target.resolve() and values["DisplayVersion"] == version
                    and "HKLM-64" not in registration, "Windows registration is incorrect")
            output = self.run(["pwsh", "-NoProfile", "-Command",
                "$ErrorActionPreference = 'Stop'\n$w = New-Object -ComObject WScript.Shell\n"
                "$paths = @((Join-Path ([Environment]::GetFolderPath('Programs')) 'Copperbench/Copperbench.lnk'), "
                "(Join-Path ([Environment]::GetFolderPath('Desktop')) 'Copperbench.lnk'))\n"
                "$paths | ForEach-Object { if (!(Test-Path -LiteralPath $_)) { throw 'Missing shortcut' }; "
                "[PSCustomObject]@{path=$_; target=$w.CreateShortcut($_).TargetPath} } | ConvertTo-Json"],
                "shortcuts-" + version + "-" + target.parent.name)
            shortcuts = json.loads(output)
            require(all(Path(row["target"]).resolve() == target / "copperbench.exe" for row in shortcuts), "Shortcut targets differ")
            self.shortcuts[str(target)] = [row["path"] for row in shortcuts]
            return {"registry": registration, "shortcuts": shortcuts}
        files = [self.root / "data/applications/copperbench.desktop", self.root / "data/applications/copperbench-uninstall.desktop",
                 self.root / "config/copperbench/install-location.txt"]
        require(all(file.is_file() for file in files), "Linux desktop registration is incomplete")
        require(files[-1].read_text(encoding="utf-8").strip() == str(target), "Linux install location is incorrect")
        return {str(file): file.read_text(encoding="utf-8") for file in files}

    def install(self, tag, target, upgrade=False):
        bundle = self.bundles[tag]
        previous_sha = sha256(target / "lib/copperbench.jar") if upgrade else None
        result = self.probe(tag, ["install", bundle["payload"], bundle["manifest"], bundle["jar"], target,
                                  release_version(tag), bundle["registrar"], "upgrade" if upgrade else "fresh"],
                            tag + "-install-" + target.parent.name)
        require(sha256(target / "lib/copperbench.jar") == sha256(bundle["payload"] / "lib/copperbench.jar"),
                "Installed application differs from published payload")
        registration = self.registration(target, release_version(tag))
        require(result.get("backup") is None and result.get("backupCleanup") == "confirmed",
                "Temporary installer backup cleanup was not confirmed")
        if upgrade:
            require(sha256(target / "lib/copperbench.jar") != previous_sha,
                    "Upgrade did not replace the old application")
            result["previousApplicationSha256"] = previous_sha
        self.checkpoint(tag + "-install-" + target.parent.name, result=result, registration=registration,
                        applicationSha256=sha256(target / "lib/copperbench.jar"))

    def uninstall(self, tag, target, label):
        managed = [target / name for name in (target / ".copperbench-files").read_text(encoding="utf-8").splitlines()]
        files = [str(path) for path in managed if path.is_file() or path.is_symlink()]
        save(self.evidence / (label + "-managed-files.json"), files)
        if os.name == "nt":
            self.run([target / "uninstall.exe", "/S"], label, timeout=180)
            deadline = time.monotonic() + 180
            while (any(os.path.lexists(path) for path in files) or windows_registration()) and time.monotonic() < deadline:
                time.sleep(1)
            require(not windows_registration(), "Windows uninstall registration survived removal")
            require(not any(Path(path).exists() for path in self.shortcuts[str(target)]),
                    "Windows application shortcuts survived removal")
        else:
            self.probe(tag, ["uninstall", target], label)
            require(not (self.root / "data/applications/copperbench.desktop").exists()
                    and not (self.root / "data/applications/copperbench-uninstall.desktop").exists()
                    and not (self.root / "config/copperbench/install-location.txt").exists(), "Linux registration survived removal")
        require(not any(os.path.lexists(path) for path in files), "Managed package files survived uninstall")
        self.checkpoint(label, managedFilesRemoved=len(files))
        self.preserved(label + "-data-preservation")

    def native(self, target, label, mode):
        try:
            self.run([sys.executable, self.repo / "scripts/cloud-acceptance/native.py", "--product", target,
                      "--root", self.root / "workspaces" / label, "--evidence", self.evidence / label,
                      "--mode", mode], label, timeout=7200)
        finally:
            for path in (self.root / "workspaces" / label).rglob("*"):
                if path.is_file() and (path.suffix == ".mcreator" or path.parent.name in {"elements", "exports"}):
                    self.sentinels[str(path)] = sha256(path)
        result = json.loads((self.evidence / label / "result.json").read_text(encoding="utf-8"))
        require(result["status"] == "passed", "Native installed-product acceptance failed")
        self.checkpoint(label, nativeResult=str(self.evidence / label / "result.json"))

    def setup_lifecycle(self):
        current, previous = self.args.release_tag, self.args.previous_release_tag
        if os.name == "nt":
            require(not windows_registration(), "Cloud runner already has Copperbench registration")
        archive = self.download(current)
        self.extract(current, archive)
        self.compile_probe(current)
        bundle = self.bundles[current]
        self.run(["java", "-cp", os.pathsep.join([str(bundle["classes"]), str(bundle["jar"])]),
                  "dev.copperbench.installer.InstallerInteractionTest"], "published-installer-components")
        self.run(["java", "-jar", bundle["jar"], "--preview", self.evidence / "installer-previews"], "published-installer-previews")
        self.checkpoint("published-installer-language-theme-components")
        install_name = "Copperbench with spaces" if os.name == "nt" else "Copperbench 安装 path"
        target = self.root / "fresh" / install_name
        self.install(current, target)
        self.sentinel(target / "personal-note.txt", "User-added application file must survive uninstall.\n")
        self.native(target, "fresh-native", "smoke")
        self.uninstall(current, target, "fresh-uninstall")
        remove_owned_tree(bundle["root"], self.root)
        old_archive = self.download(previous)
        self.extract(previous, old_archive)
        self.compile_probe(previous)
        target = self.root / "upgrade" / install_name
        self.install(previous, target)
        self.sentinel(target / "personal-note.txt", "User-added file must survive upgrade and uninstall.\n")
        remove_owned_tree(self.bundles[previous]["root"], self.root)
        old_archive.unlink()
        self.extract(current, archive)
        self.install(current, target, upgrade=True)
        self.preserved("upgrade-data-preservation")
        remove_owned_tree(self.bundles[current]["root"], self.root)
        archive.unlink()
        try:
            self.native(target, "upgraded-native", self.args.native_mode)
        finally:
            self.uninstall(current, target, "upgraded-uninstall")

    def deb_lifecycle(self):
        target = Path("/opt/copperbench")
        require(not target.exists(), "Cloud runner already has a Copperbench directory")
        state = subprocess.run(["dpkg-query", "-W", "-f=${Status}", "copperbench"], capture_output=True, text=True)
        require("install ok installed" not in state.stdout, "Cloud runner already has the DEB package")
        current, previous = self.args.release_tag, self.args.previous_release_tag
        packages = {current: self.download(current), previous: self.download(previous)}

        def install(tag, label):
            self.run(["sudo", "apt-get", "install", "-y", str(packages[tag])], label, timeout=900)
            version = self.run(["dpkg-query", "-W", "-f=${Version}", "copperbench"], label + "-version").strip()
            require(version == release_version(tag), "DEB package version is incorrect")
            require(Path("/usr/bin/copperbench").is_file() and Path("/usr/share/applications/copperbench.desktop").is_file(),
                    "DEB launcher or desktop registration is missing")
            self.checkpoint(label, version=version, applicationSha256=sha256(target / "lib/copperbench.jar"))

        def uninstall(label):
            listing = self.run(["dpkg-query", "-L", "copperbench"], label + "-owned-files").splitlines()
            owned = [path for path in listing if Path(path).is_file() or Path(path).is_symlink()]
            self.run(["sudo", "apt-get", "remove", "-y", "copperbench"], label, timeout=900)
            require(not any(os.path.lexists(path) for path in owned), "DEB-managed files survived removal")
            self.checkpoint(label, managedFilesRemoved=len(owned))
            self.preserved(label + "-data-preservation")

        install(current, "deb-fresh-install")
        note = self.root / "deb-personal-note.txt"
        self.sentinel(note, "DEB removal must preserve unmanaged user data.\n")
        self.run(["sudo", "cp", note, target / "personal-note.txt"], "deb-user-data-preparation")
        self.sentinels[str(target / "personal-note.txt")] = sha256(note)
        self.native(target, "deb-fresh-native", "smoke")
        uninstall("deb-fresh-uninstall")
        install(previous, "deb-previous-install")
        install(current, "deb-upgrade-install")
        self.preserved("deb-upgrade-data-preservation")
        self.native(target, "deb-upgraded-native", "smoke")
        uninstall("deb-upgraded-uninstall")

    def execute(self):
        try:
            self.sentinel(self.root / "preferences/cloud-user-preference.txt", "Keep user preferences across application lifecycle.\n")
            self.sentinel(self.root / "external-project/user-workspace.mcreator", "External workspace preservation sentinel.\n")
            if self.args.variant == "deb":
                self.deb_lifecycle()
            else:
                self.setup_lifecycle()
            self.record["status"] = "passed"
        except Exception as error:
            self.record.update(status="failed", error=str(error), errorType=type(error).__name__)
            (self.evidence / "failure.txt").write_bytes(traceback.format_exc().encode("utf-8"))
            print(traceback.format_exc(), flush=True)
        finally:
            # COPPERBENCH_HOME overrides all platform-specific user directories, including state/logs.
            logs = self.root / "preferences/logs"
            if logs.is_dir():
                shutil.copytree(logs, self.evidence / "application-logs", dirs_exist_ok=True)
            self.record["completedAt"] = datetime.now(timezone.utc).isoformat()
            save(self.evidence / "result.json", self.record)
            save(self.evidence / "evidence-hashes.json", {p.relative_to(self.evidence).as_posix(): sha256(p)
                 for p in sorted(self.evidence.rglob("*")) if p.is_file() and p.name != "evidence-hashes.json"})
            summary = Path(os.environ["GITHUB_STEP_SUMMARY"])
            with summary.open("a", encoding="utf-8", newline="\n") as output:
                output.write(f"### {self.args.variant}: {self.record['status']}\n\n"
                             f"Public {self.args.previous_release_tag} → {self.args.release_tag}; {self.record['os']}.\n\n")
                for check in self.record["checks"]:
                    output.write(f"- PASS: {check['name']}\n")
                if self.record.get("error"):
                    output.write(f"\nFailure: {self.record['error']}\n")
                output.write("\nNot tested: " + "; ".join(self.record["unverified"]) + ".\n")
        return 0 if self.record["status"] == "passed" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--previous-release-tag", required=True)
    parser.add_argument("--variant", choices=["windows-setup", "linux-setup", "deb"], required=True)
    parser.add_argument("--native-mode", choices=["full", "packs"], default="full")
    args = parser.parse_args()
    release_version(args.release_tag)
    release_version(args.previous_release_tag)
    require(args.release_tag != args.previous_release_tag, "Upgrade must use distinct released versions")
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
            and os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "This OS-mutating driver is restricted to disposable hosted CI")
    require((os.name == "nt") == (args.variant == "windows-setup"), "Variant does not match runner OS")
    return Acceptance(args).execute()


if __name__ == "__main__":
    raise SystemExit(main())
