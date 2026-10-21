"""Plan or run local contributor checks using the existing CI selector.

The default is a reviewable plan. No dependency installation, cache cleanup,
global configuration change, performance sampling, or native app acceptance is
performed by this entry point. Gradle downloads require --allow-downloads.
"""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
SELECTOR = ROOT / "scripts" / "ci" / "select_checks.py"
spec = importlib.util.spec_from_file_location("contributor_ci_selection", SELECTOR)
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)


def git(root, *arguments):
    return subprocess.run(["git", *arguments], cwd=root, check=True, capture_output=True,
                          timeout=60, env={**os.environ, "GIT_OPTIONAL_LOCKS": "0"}).stdout


def changed_paths(root, base=None):
    """Include both sides of moves, staged/unstaged deletions, and untracked files."""
    paths = []
    if base:
        base_sha = git(root, "rev-parse", "--verify", f"{base}^{{commit}}").decode("ascii").strip()
        head_sha = git(root, "rev-parse", "HEAD").decode("ascii").strip()
        paths.extend(selection.changed_paths(root, {
            "pull_request": {"base": {"sha": base_sha}, "head": {"sha": head_sha}},
        }))
    for raw in (
        git(root, "diff", "--no-ext-diff", "--no-textconv", "--ignore-submodules=none",
            "--no-renames", "--name-only", "-z", "HEAD", "--"),
        git(root, "ls-files", "--others", "--exclude-standard", "-z"),
    ):
        if raw and not raw.endswith(b"\0"):
            raise ValueError("Incomplete changed-file list")
        paths.extend(path.decode("utf-8", errors="surrogateescape") for path in raw.split(b"\0")[:-1])
    return sorted(set(paths))


def version(executable, *arguments):
    try:
        result = subprocess.run([executable, *arguments], capture_output=True, text=True,
                                timeout=10, creationflags=0x08000000 if os.name == "nt" else 0)
        if result.returncode != 0:
            return None
        return (result.stdout or result.stderr).strip().splitlines()[0]
    except (OSError, subprocess.SubprocessError, IndexError):
        return None


def capability(available, reason, **details):
    return {"available": bool(available), "reason": None if available else reason, **details}


def java_environment(root):
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append((Path(os.environ["JAVA_HOME"]), "JAVA_HOME"))
    for name in ("jbr25_win_64", "jbr25_linux_64", "jbr25_osx_64"):
        candidates.append((root / "jdk" / name, "repository JBR"))
    java = shutil.which("java")
    if java:
        candidates.append((Path(java).resolve().parent.parent, "PATH"))
    for home, origin in candidates:
        release = home / "release"
        if not release.is_file():
            continue
        text = release.read_text(encoding="utf-8")
        match = re.search(r'^JAVA_VERSION="([^"]+)"', text, re.MULTILINE)
        if not match or not match.group(1).startswith("25."):
            continue
        if not (home / "bin" / ("java.exe" if os.name == "nt" else "java")).is_file():
            continue
        jcef = any((home / name).exists() for name in (
            "jmods/jcef.jmod", "lib/jcef.jar", "bin/jcef.dll", "lib/libjcef.so", "lib/libjcef.dylib",
        ))
        return home, capability(True, "", version=match.group(1), source=origin), capability(
            jcef, "The selected JDK lacks JCEF; use JBR 25 with JCEF for native Java window tests.",
            scope="Filesystem presence only; native launch remains unverified.")
    return None, capability(False, "JDK 25 was not found in JAVA_HOME, repository JBR, or PATH."), capability(
        False, "JCEF cannot be checked until a JDK 25 is selected.")


def has_chromium(root):
    configured = os.environ.get("PLAYWRIGHT_BROWSERS_PATH")
    if configured == "0":
        cache = root / "ui-shell/node_modules/playwright-core/.local-browsers"
    elif configured:
        cache = Path(configured).expanduser()
    elif os.name == "nt":
        cache = Path(os.environ.get("LOCALAPPDATA", "")) / "ms-playwright"
    elif platform.system() == "Darwin":
        cache = Path.home() / "Library/Caches/ms-playwright"
    else:
        cache = Path(os.environ.get("XDG_CACHE_HOME", str(Path.home() / ".cache"))) / "ms-playwright"
    if not cache.is_dir():
        return False
    executable_paths = ("chrome-win/chrome.exe", "chrome-win64/chrome.exe", "chrome-linux/chrome",
                        "chrome-linux64/chrome", "chrome-headless-shell-win64/headless_shell.exe",
                        "chrome-headless-shell-linux64/headless_shell", "chrome-mac/Chromium.app/Contents/MacOS/Chromium")
    return any(any((directory / name).is_file() for name in executable_paths)
               for directory in cache.glob("chromium*") if directory.is_dir())


def inspect_environment(root):
    tools = {}
    for name, executable, arguments, minimum in (
        ("node", "node", ("--version",), 22), ("npm", "npm", ("--version",), 0),
        ("pwsh", "pwsh", ("-NoProfile", "-Command", "$PSVersionTable.PSVersion.ToString()"), 7),
    ):
        path = shutil.which(executable)
        text = version(path, *arguments) if path else None
        match = re.search(r"(?:^v?)(\d+)", text or "")
        valid = bool(match and int(match.group(1)) >= minimum)
        tools[name] = capability(valid, f"Install/configure {executable} {minimum or ''} manually and rerun the plan.", version=text)
    tools["python"] = capability(sys.version_info >= (3, 11), "Python 3.11 or later is required.", version=platform.python_version())
    tools["git"] = capability(shutil.which("git"), "Git is required to resolve the changed-file list and source identity.")
    java_home, tools["jdk"], tools["jcef"] = java_environment(root)
    display = bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))
    if os.name == "nt":
        # Read the calling process's window station, without launching a window.
        import ctypes
        from ctypes import wintypes

        class StationFlags(ctypes.Structure):
            _fields_ = [("inherit", wintypes.BOOL), ("reserved", wintypes.BOOL), ("flags", wintypes.DWORD)]

        user32 = ctypes.WinDLL("user32", use_last_error=True)
        user32.GetProcessWindowStation.restype = wintypes.HANDLE
        user32.GetUserObjectInformationW.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p,
                                                    wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)]
        flags = StationFlags()
        needed = wintypes.DWORD()
        display = bool(user32.GetUserObjectInformationW(user32.GetProcessWindowStation(), 1,
                      ctypes.byref(flags), ctypes.sizeof(flags), ctypes.byref(needed)) and flags.flags & 1)
    tools["display"] = capability(display, "Java window tests need a display; select an existing desktop or an explicitly prepared Xvfb session.",
                                  scope="A display/session indicator is present; operability is unverified.")
    tools["windows"] = capability(os.name == "nt", "Windows MCP/filesystem checks require a Windows host or the existing Windows CI job.")
    tools["ui-core-dependencies"] = capability((root / "ui-core/node_modules/ajv/package.json").is_file(),
                                               "ui-core dependencies are missing; run npm ci --prefix ui-core manually.")
    tools["ui-shell-dependencies"] = capability(all((root / f"ui-shell/node_modules/{name}/package.json").is_file()
                                                   for name in ("typescript", "vite", "react")),
                                                "ui-shell dependencies are missing; run npm ci --prefix ui-shell manually.")
    tools["playwright"] = capability((root / "ui-shell/node_modules/@playwright/test/cli.js").is_file(),
                                    "The repository Playwright dependency is missing; install ui-shell dependencies manually.")
    tools["chromium"] = capability(has_chromium(root), "Playwright Chromium is missing; install the version matching ui-shell manually.",
                                  scope="Cached executable presence only; Playwright launch will validate compatibility.")
    return tools, java_home


def make_plan(paths, environment, allow_downloads=False, windows=None):
    route = selection.select_checks(paths)
    checks = route["checks"]
    windows = os.name == "nt" if windows is None else windows
    wrapper = "gradlew.bat" if windows else "./gradlew"
    commands = []

    def add(identifier, argv, requirements, reason, *, download=False, depends=(), manual=None, cwd="."):
        gaps = [f"{name}: {environment[name]['reason']}" for name in requirements if not environment[name]["available"]]
        if download and not allow_downloads:
            gaps.append("May download Wrapper/Gradle/Maven dependencies; review the plan and opt in with --allow-downloads.")
        if manual:
            gaps.append(manual)
        item = {"id": identifier, "argv": argv, "cwd": cwd, "reason": reason,
                "requirements": requirements, "mayDownloadDependencies": download, "dependsOn": list(depends),
                "status": "blocked" if gaps else "planned", "environmentGaps": gaps, "exitCode": None,
                "testCounts": None, "limitation": "Counts will be reported only when the command emits a recognizable test summary."}
        commands.append(item)
        return item

    add("ci-selection", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_ci_selection.py"],
        ["python", "git"], "The existing CI always tests check selection and failure handling.")
    add("agent-readiness-unit", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_agent_readiness*.py"],
        ["python"], "The existing CI always tests installed-run provenance and study evidence accounting.")
    add("markdown-links", ["node", "scripts/verify-markdown-links.mjs"], ["node"], "The existing CI always checks repository links.")
    add("product-release-history", ["node", "--test", "scripts/tests/product-release-history.tests.mjs"],
        ["node"], "The existing CI always tests product release-history declarations.")
    add("product-status", ["node", "scripts/verify-product-status.mjs"], ["node"], "The existing CI always checks product status declarations.")
    if checks["ui"] or checks["contract"]:
        add("performance-tooling", ["node", "--experimental-strip-types", "--test",
                                   "scripts/tests/performance-statistics.tests.mjs",
                                   "scripts/tests/performance-fixtures.tests.mjs",
                                   "scripts/tests/performance-runner.tests.mjs",
                                   "scripts/tests/source-identity.tests.mjs",
                                   "scripts/tests/native-observation.tests.mjs"],
            ["node", "git"], "CI selected measurement-tooling and source-identity regression tests; these do not collect native acceptance or performance samples.")
    if checks["python"]:
        add("contributor-unit", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_verify_changed.py"],
            ["python"], "CI selected contributor verification planning and reporting tests.")
        add("acceptance-measurement-unit", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_acceptance_measurement.py"],
            ["python"], "CI selected acceptance-measurement planning and collection unit tests; native execution remains separate.")
        for identifier, directory in (("portable-evidence-unit", "tools/portable-evidence"),
                                      ("candidate-evidence-unit", "tools/candidate-evidence"),
                                      ("generator-probes-unit", "tools/generator-probes"),
                                      ("ci-runner-unit", "scripts/ci")):
            add(identifier, [sys.executable, "-m", "unittest", "discover", "-s", directory, "-p", "test_*.py"],
                ["python"], "CI selected portable/scoped evidence, generator-probe and request-runner boundary tests.")
    if checks["contract"]:
        add("ui-contract", ["npm", "test", "--prefix", "ui-core"], ["node", "npm", "ui-core-dependencies"], "CI selected shared UI contract checks.")
    java = None
    if checks["java"]:
        java = add("java", [wrapper, "--no-daemon", "test", "javadoc"],
                   ["jdk", "jcef", "display", "node", "npm", "ui-shell-dependencies"] + (["pwsh"] if windows else []),
                   "CI selected Java checks. processResources already builds the UI shell.", download=True)
        add("loom-normalization", [sys.executable, "tools/generator-probes/integration_loom_normalization.py"],
            ["python", "jdk"] + (["pwsh"] if windows else []),
            "CI selected strict local Minecraft JAR normalization through real Gradle fixture builds.",
            download=True, depends=(java["id"],))
    ui_build = None
    if checks["ui"]:
        add("ui-unit", ["npm", "test", "--prefix", "ui-shell"], ["node", "npm", "ui-shell-dependencies"], "CI selected production bridge/localization tests.")
        if java and java["status"] == "planned":
            ui_build = java["id"]
        else:
            ui_build = add("ui-build", ["npm", "run", "build", "--prefix", "ui-shell"],
                           ["node", "npm", "ui-shell-dependencies"], "Build UI once when a runnable Gradle check is not already doing it.")["id"]
        add("ui-smoke", ["node", "node_modules/@playwright/test/cli.js", "test", "e2e/scenarios.spec.ts",
                         "e2e/new-workspace.spec.ts", "e2e/sdk-connection-security.spec.ts", "e2e/jcef-bridge.spec.ts",
                         "e2e/mcp-runtime.spec.ts", "--project=chromium"],
            ["node", "ui-shell-dependencies", "playwright", "chromium"],
            "Run the same five browser fixture smoke specs selected by CI; native acceptance is separate.", depends=(ui_build,), cwd="ui-shell")
        if route["ui_full_e2e"]:
            add("ui-full-e2e", ["node", "node_modules/@playwright/test/cli.js", "test", "--project=chromium"],
                ["node", "ui-shell-dependencies", "playwright", "chromium"],
                "The shared CI selector requires the full Chromium suite for this change.", depends=(ui_build,), cwd="ui-shell")
    if checks["python"]:
        add("python-sdk", [sys.executable, "-m", "unittest", "discover", "-s", "sdk/python", "-p", "test_*.py"],
            ["python"], "CI selected Python SDK unit tests.")
    if checks["typescript"]:
        add("typescript-sdk", ["npm", "run", "test:sdk", "--prefix", "ui-shell"],
            ["node", "npm", "ui-shell-dependencies"], "CI selected TypeScript SDK tests.")
        if not checks["ui"]:
            add("sdk-connection-security", ["node", "node_modules/@playwright/test/cli.js", "test",
                                           "e2e/sdk-connection-security.spec.ts", "--project=chromium"],
                ["node", "ui-shell-dependencies", "playwright"], "Reuse CI's connection-file security checks, which do not launch Chromium.", cwd="ui-shell")
    if checks["windows"]:
        if not (windows and java and java["status"] == "planned"):
            add("windows-filesystem", [wrapper, "--no-daemon", "test", "--tests", "dev.copperbench.core.workspace.WorkspaceFilesTest",
                                       "--tests", "dev.copperbench.core.application.PreviewAccessTest",
                                       "--tests", "dev.copperbench.core.workspace.mcreator.VersionCopyExecutionTest",
                                       "--tests", "dev.copperbench.shell.Stage9NativeJcefAccessibilityTest",
                                       "--tests", "dev.copperbench.shell.Stage10NativeJcefDiagnosticsBundleTest",
                                       "--tests", "dev.copperbench.history.LegacyHistoryRuntimeFilesTest",
                                       "--tests", "dev.copperbench.assets.ResourcePackExportServiceTest",
                                       "--tests", "dev.copperbench.migration.LoaderMigrationServiceTest",
                                       "--tests", "dev.copperbench.migration.UpstreamWorkspaceImportServiceTest",
                                       "--tests", "dev.copperbench.gradle.GradleCachePolicyTest",
                                       "--tests", "dev.copperbench.gradle.GradleCacheProcessTest",
                                       "--tests", "dev.copperbench.generator.fabric.Fabric1211ProcessRunnerTest"],
                ["windows", "jdk", "jcef", "display", "node", "npm", "pwsh", "ui-shell-dependencies"],
                "CI selected Windows filesystem boundaries and native accessibility.", download=True)
        add("windows-nsis-setup", ["pwsh", "-NoProfile", "-File", "scripts/test-nsis-setup.ps1"],
            ["windows", "pwsh"], "CI selected NSIS setup and partial-cache recovery.", download=True,
            manual="Manual/CI step: this setup regression provisions NSIS tool archives and is separate from local source checks.")
        windows_export = add("windows-package-identity-build", [wrapper, "--no-daemon", "exportWin64"],
            ["windows", "pwsh", "jdk", "jcef", "node", "npm", "ui-shell-dependencies"],
            "CI builds the Windows launcher before checking its application identity.", download=True,
            manual="Manual/CI release-packaging step: requires the Windows packaging toolchain and a frozen product identity.")
        add("windows-application-identity", [sys.executable, "scripts/verify-windows-application-identity.py",
                                             "--product-root", "build/export/win64", "--output", "build/windows-application-identity"],
            ["windows", "python"], "CI selected the built Windows launcher's application identity check.",
            depends=(windows_export["id"],))
        for identifier, script in (("release-source-harness", "verify-release-source.tests.ps1"),
                                   ("windows-harness", "Stage9CleanWindowsHarness.tests.ps1")):
            add(identifier, ["pwsh", "-NoProfile", "-File", f"scripts/tests/{script}"], ["windows", "pwsh"], "Reuse the existing Windows script regression.")
        add("candidate-metadata", ["node", "--test", "scripts/tests/linux-candidate-metadata.tests.mjs"], ["node"], "Reuse CI's candidate identity regression.")
        add("eval-contracts", [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_eval_contracts.py"],
            ["python"], "CI selected protocol evaluation scope and compatible report validation.")
        add("mcp-conformance", ["pwsh", "-NoProfile", "-File", "scripts/verify-mcp-conformance.ps1", "-OutputDirectory", "build/mcp-conformance-results"],
            ["windows", "pwsh", "jdk", "node", "npm", "ui-shell-dependencies"], "CI selected Windows MCP conformance.", download=True,
            manual="Manual/CI step: the existing script runs npx --yes for an external conformance CLI; this entry never installs that tool automatically.")
    return {"schemaVersion": "1.0", "kind": "contributor-verification", "selectionSource": "scripts/ci/select_checks.py",
            "paths": paths, "selection": route, "environment": environment, "commands": commands,
            "status": "planned", "coverage": {
                "browser": "Synthetic fixtures; full Chromium is included when selected by CI, otherwise affected paths outside smoke need focused cases.",
                "native": "not_run", "gameplay": "not_run", "performance": "Dedicated opt-in entry; not included in contributor checks."},
            "uiBuildReuse": "Gradle test -> processResources -> buildUiShell; no separate UI production build when runnable Java verification covers it."}


def test_counts(text):
    # Node's default spec reporter uses "ℹ"; its TAP reporter uses "#".
    # Strip terminal presentation only, then retain emitted counts verbatim.
    text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", text).replace("\r\n", "\n").replace("\r", "\n")
    python = re.search(r"Ran (\d+) tests?", text)
    if python:
        return {"tests": int(python.group(1)), "source": "command output"}
    node = re.findall(r"^(?:#|\u2139\ufe0f?)[ \t]+(tests|suites|pass|fail|cancelled|skipped|todo)[ \t]+(\d+)[ \t]*$",
                      text, re.MULTILINE)
    if any(name == "tests" for name, _ in node):
        # Multiple summaries can belong to separate invocations or nested output;
        # do not invent a combined total or silently select the first/last run.
        if len({name for name, _ in node}) != len(node):
            return None
        names = {"pass": "passed", "fail": "failed"}
        return {**{names.get(name, name): int(value) for name, value in node}, "source": "command output"}
    playwright = re.search(r"(\d+) passed", text)
    if playwright:
        failed = re.search(r"(\d+) failed", text)
        skipped = re.search(r"(\d+) skipped", text)
        return {"passed": int(playwright.group(1)), "failed": int(failed.group(1)) if failed else 0,
                "skipped": int(skipped.group(1)) if skipped else 0, "source": "command output"}
    return None


def execution_argv(argv, root):
    """Avoid Windows batch-file shell interpolation; values remain literal."""
    argv = list(argv)
    if argv[0] == "npm":
        npm = shutil.which("npm")
        if os.name == "nt":
            cli = Path(npm).parent / "node_modules/npm/bin/npm-cli.js" if npm else None
            if not cli or not cli.is_file():
                raise OSError("The configured npm CLI could not be located.")
            return [shutil.which("node") or "node", str(cli), *argv[1:]]
        argv[0] = npm or "npm"
    elif argv[0] == "gradlew.bat":
        def literal(value):
            return "'" + str(value).replace("'", "''") + "'"
        invocation = "& " + " ".join(literal(value) for value in [root / "gradlew.bat", *argv[1:]])
        return [shutil.which("pwsh") or "pwsh", "-NoProfile", "-Command", invocation + "; exit $LASTEXITCODE"]
    elif argv[0] in ("node", "pwsh"):
        argv[0] = shutil.which(argv[0]) or argv[0]
    return argv


def run_bounded(argv, cwd, environment, timeout_seconds):
    """A timeout reclaims only the process group/tree started for this check."""
    process = subprocess.Popen(argv, cwd=cwd, env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               text=True, start_new_session=os.name != "nt",
                               creationflags=(0x08000000 | 0x00000200) if os.name == "nt" else 0)
    try:
        stdout, stderr = process.communicate(timeout=timeout_seconds)
        return subprocess.CompletedProcess(argv, process.returncode, stdout, stderr)
    except subprocess.TimeoutExpired:
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, timeout=15)
        else:
            os.killpg(process.pid, signal.SIGKILL)
        stdout, stderr = process.communicate(timeout=15)
        raise subprocess.TimeoutExpired(argv, timeout_seconds, output=stdout, stderr=stderr)


def source_identity(root, environment):
    if not environment["node"]["available"] or not environment["git"]["available"]:
        return {"status": "not_run", "reason": "Full source hashing requires Node and Git."}
    try:
        result = subprocess.run(["node", "scripts/evidence/source-identity.mjs", str(root)], cwd=root,
                                capture_output=True, text=True, check=True, timeout=120)
        return {"status": "recorded", **json.loads(result.stdout)}
    except (OSError, ValueError, subprocess.SubprocessError):
        return {"status": "failed", "reason": "The shared source identity helper failed; see a direct helper run for diagnostics."}


def run_plan(plan, root, output, java_home, timeout_seconds):
    plan["sourceBefore"] = source_identity(root, plan["environment"])
    environment = dict(os.environ)
    if java_home:
        environment["JAVA_HOME"] = str(java_home)
        environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    statuses = {}
    for command in plan["commands"]:
        if command["status"] == "blocked":
            statuses[command["id"]] = "blocked"
            continue
        if any(statuses.get(name) != "passed" for name in command["dependsOn"]):
            command.update(status="blocked", environmentGaps=["A required build command did not pass; the smoke check was not run."])
            statuses[command["id"]] = "blocked"
            continue
        start = time.monotonic_ns()
        log = output / f"{command['id']}.log"
        print(f"Running {command['id']}", file=sys.stderr, flush=True)
        try:
            argv = execution_argv(command["argv"], root)
            result = run_bounded(argv, root / command["cwd"], environment, timeout_seconds)
            text = result.stdout + result.stderr
            log.write_text(text, encoding="utf-8")
            command.update(status="passed" if result.returncode == 0 else "failed", exitCode=result.returncode,
                           testCounts=test_counts(text))
        except subprocess.TimeoutExpired as error:
            def decoded(value):
                return value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value or ""
            log.write_text(decoded(error.stdout) + decoded(error.stderr), encoding="utf-8")
            command.update(status="timeout", exitCode=None)
        except OSError as error:
            log.write_text(type(error).__name__, encoding="utf-8")
            command.update(status="blocked", environmentGaps=["The selected executable could not start."], exitCode=None)
        command["durationMs"] = (time.monotonic_ns() - start) / 1_000_000
        command["log"] = log.name
        statuses[command["id"]] = command["status"]
        (output / "report.json").write_text(json.dumps(plan, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    plan["sourceAfter"] = source_identity(root, plan["environment"])
    plan["sourceStable"] = (plan["sourceBefore"].get("status") == "recorded"
                            and plan["sourceAfter"].get("status") == "recorded"
                            and all(plan["sourceBefore"][key] == plan["sourceAfter"][key]
                                    for key in ("commit", "sourceSha256", "dirtyDeltaSha256")))
    results = set(statuses.values())
    plan["status"] = "failed" if results & {"failed", "timeout"} else "blocked" if "blocked" in results else "passed"
    if not plan["sourceStable"]:
        plan["status"] = "failed" if plan["sourceAfter"].get("status") == "recorded" else "blocked"
        plan["sourceLimitation"] = "Source identity could not be confirmed stable during verification."
    return 1 if plan["status"] == "failed" else 2 if plan["status"] == "blocked" else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    changed = parser.add_mutually_exclusive_group()
    changed.add_argument("--paths", nargs="+", help="Explicit repository-relative paths; routed only by the CI selector")
    changed.add_argument("--base", help="Compare the base commit/ref to HEAD, including current working-tree changes")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--run", action="store_true", help="Execute runnable planned commands and retain local logs")
    mode.add_argument("--dry-run", action="store_true", help="Print the plan without executing verification (default)")
    parser.add_argument("--allow-downloads", action="store_true", help="Permit existing Gradle dependency resolution; never install tools")
    parser.add_argument("--output", type=Path, help="New report directory under repository output/ or build/")
    parser.add_argument("--timeout-seconds", type=int, default=1800)
    args = parser.parse_args()
    if args.timeout_seconds <= 0:
        parser.error("--timeout-seconds must be positive")
    if args.paths and any(Path(path).is_absolute() or path.startswith(("/", "\\")) or ".." in path.replace("\\", "/").split("/") for path in args.paths):
        parser.error("--paths must be repository-relative and must not contain parent traversal")
    try:
        paths = args.paths if args.paths is not None else changed_paths(ROOT, args.base)
        path_reason = None
    except (OSError, ValueError, subprocess.SubprocessError):
        paths = []
        path_reason = "The complete Git comparison failed; the existing selector conservatively selects all checks."
    environment, java_home = inspect_environment(ROOT)
    plan = make_plan(paths, environment, args.allow_downloads)
    if path_reason:
        plan["pathResolutionLimitation"] = path_reason
    output = None
    if args.output or args.run:
        output = (args.output or ROOT / "output/contributor-validation" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])).resolve()
        if not any(output.is_relative_to(ROOT / name) for name in ("output", "build")):
            parser.error("--output must resolve inside repository output/ or build/")
        if output.exists():
            parser.error("--output must be new; existing reports are preserved")
        output.mkdir(parents=True)
    code = run_plan(plan, ROOT, output, java_home, args.timeout_seconds) if args.run else 0
    if output:
        (output / "report.json").write_text(json.dumps(plan, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    print(json.dumps(plan, indent=2, ensure_ascii=True))
    return code


if __name__ == "__main__":
    sys.exit(main())
