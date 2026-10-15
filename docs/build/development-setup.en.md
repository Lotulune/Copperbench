# Copperbench development setup

[简体中文](development-setup.md) · [Contributing](../../CONTRIBUTING.md)

## Prepare the affected layer

| Layer | Environment |
| --- | --- |
| Documentation/status | Node.js 22; Git for change discovery. The new plan entry uses Python 3.11+. No JCEF/display needed. |
| Java/shared contracts/product build | JDK 25 and repository Gradle Wrapper. Resource tasks build UI, requiring Node.js 22/npm and prepared UI dependencies. |
| Browser UI | Node.js 22/npm; prepare Chromium for selected Playwright cases. |
| Native desktop/packages | JBR 25 with JCEF, target platform and available display. Windows scripts use PowerShell 7. Linux Java tests can run in CI; window tests need a display. |
| Python SDK | Python 3.11+; real Core/desktop tests need the launcher and relevant Java/display environment. |

Documentation does not require all optional layers. Initial preparation/builds can contact Gradle, Maven and Minecraft sources. Use the repository's `gradlew.bat` (Windows) or `./gradlew` (Linux), never system Gradle.

## Initialize product development

```powershell
git clone https://github.com/Lotulune/Copperbench.git
Set-Location Copperbench
npm ci --prefix ui-core
npm ci --prefix ui-shell
$env:JAVA_HOME = 'C:\path\to\jbr-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
.\gradlew.bat runProductShell
```

The assignments affect this terminal. Linux uses its JDK environment and `./gradlew runProductShell`. When selected browser cases need Chromium:

```powershell
Push-Location ui-shell
npx playwright install chromium
Pop-Location
```

## Review verification before execution

This unreleased source helper reuses `scripts/ci/select_checks.py`; it is not a product workspace doctor. By default it prints JSON plan/environment gaps only.

The plan also retains CI's check-selection and product-release-history regressions. UI/contract selections include performance-tooling/source-identity/native-observation unit tests; Python selections include contributor-entry and acceptance-measurement regressions. Missing Python, Git or Node leaves explicit blocked commands. These checks do not install dependencies, operate the desktop or collect performance samples. Preview the plan with:

```sh
python scripts/verify-changed.py --paths docs/user/first-creation.md
python scripts/verify-changed.py --base origin/main --dry-run
```

Choose `--paths` or `--base`, not both. Base mode adds staged/unstaged/untracked changes to the merge-base diff. Unknown paths or an incomplete comparison conservatively select all checks. After reviewing the plan and preparing dependencies:

```sh
python scripts/verify-changed.py --run --allow-downloads --output output/contributor-validation/my-change-01
```

`--run` without `--allow-downloads` blocks Gradle tasks that can download. The helper never installs npm dependencies/Chromium, changes proxies, elevates or clears caches. Missing tools/display block only dependent checks. Reports use a new directory inside repository `output/` or `build/`, retaining commands, exit codes, logs, source hashes and available counts (`null` if absent). Failed/timed-out execution returns `1`, blocked execution `2`, plan-only `0`.

See the [complete helper contract](../../scripts/performance/README.md#contributor-verification) and [CI selection](../../CONTRIBUTING.md#how-ci-selects-checks). Gradle `processResources → buildUiShell` already builds production UI; avoid immediately repeating that build on unchanged inputs. UI-only work without Gradle still needs its selected production build/interactions. Runtime changes use isolated evidence in [AGENTS.md](../../AGENTS.md); compiling alone does not establish gameplay.

## Restricted Windows launcher and data

If a restricted host prevents Java NIO daemon loopback pipes (`Unable to establish loopback connection`), use the existing independent-process entry:

```powershell
pwsh -NoProfile -File .\scripts\run-gradle-external.ps1 --offline help
pwsh -NoProfile -File .\scripts\run-gradle-external.ps1 --no-daemon test --tests dev.copperbench.shell.Stage9NativeJcefAccessibilityTest
```

This specific workaround uses repository JBR 25 and `.tmp/gradle-external/` logs; it does not alter proxies, Winsock or adapters, and does not repair missing dependencies. Product settings/shared Gradle cache use `%USERPROFILE%\.copperbench`; outputs use `build/` and `ui-shell/dist/`. Keep runtimes, caches, credentials, certificates and user workspaces out of commits. The [Windows clean-build guide](windows-clean-build.md) retains its own scope.
