# Contributing to Copperbench

Copperbench is an independent derivative of MCreator. Use this repository's [issue tracker](https://github.com/Lotulune/Copperbench/issues) for Copperbench bugs and proposals, and open pull requests against **`main`**. Upstream source and attribution records are in [UPSTREAM.md](UPSTREAM.md).

Bug reports are useful contributions. Include the Copperbench version, operating system and desktop session, generator, steps to reproduce, and the expected and actual result. The bug-report form accepts Windows 11 and Ubuntu 24.04 GNOME Wayland/Xorg. Review diagnostic bundles before attaching them; include only information needed to reproduce the problem.

## Run from source

Use JDK 25 (JetBrains Runtime with JCEF for the desktop shell), Node.js 22, npm and Git. Python SDK tests require Python 3.11 or later. PowerShell scripts require PowerShell 7. See the [development setup guide](docs/build/development-setup.md) for JDK configuration and Windows launcher troubleshooting.

From the repository root:

```sh
npm ci --prefix ui-core
npm ci --prefix ui-shell
./gradlew runProductShell
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`. Use the repository's Gradle Wrapper throughout. Initial dependency downloads require network access.

## Choose verification for the change

Run the checks for the affected layer. A documentation correction needs link and content checks; it does not require an unrelated gameplay replay. Report exactly what ran, what passed or failed, and what the environment prevented you from checking.

| Changed layer | Useful local checks |
| --- | --- |
| Documentation | `node scripts/verify-markdown-links.mjs`; check current download links against [GitHub Releases](https://github.com/Lotulune/Copperbench/releases). |
| Java implementation | Run the relevant class with Gradle, for example `./gradlew --no-daemon test --tests dev.copperbench.bridge.JcefWindowBridgeTransportTest`. Use `./gradlew --no-daemon test` for a broader Java regression when needed. Public Java API changes also use `./gradlew javadoc`. |
| Shared UI-Core schemas | `npm test --prefix ui-core` |
| UI implementation | `npm test --prefix ui-shell` and `npm run build --prefix ui-shell`, then the affected Playwright spec. The existing startup smoke checks are `npm run test:e2e --prefix ui-shell -- e2e/scenarios.spec.ts e2e/new-workspace.spec.ts --project=chromium`. |
| Python SDK | `python -m unittest discover -s sdk/python -p 'test_*.py'`; native process and desktop integration checks are documented in [sdk/python/README.md](sdk/python/README.md). |
| TypeScript SDK | After `npm ci --prefix ui-shell`, run `npm run test:sdk --prefix ui-shell`; it uses the UI shell's TypeScript development dependency. |
| MCP transport or tool contracts | `pwsh -NoProfile -File ./scripts/verify-mcp-conformance.ps1 -OutputDirectory build/mcp-conformance-results`; consult [MCP setup](docs/ai/getting-started.md) and the affected Java tests. |
| Product status or release declarations | `node scripts/verify-product-status.mjs`; verify publication and package-specific acceptance separately. |

Before the first Playwright run, install Chromium from `ui-shell` with `npx playwright install chromium`. Java tests that create windows require a display; the Linux CI uses Xvfb. Generator, installed-product and Minecraft gameplay changes need the relevant runtime evidence described in [AGENTS.md](AGENTS.md) and the corresponding existing test guide; a successful build alone does not establish gameplay correctness.

## How CI selects checks

[Build and test](.github/workflows/test.yml) runs once for each PR update. Pushes to `main` and manual runs keep the full Java, UI and Windows MCP regression. Feature branches use the PR run rather than a second push-triggered run; use a draft PR or a manual run for a branch that is not ready for review.

The three required check names remain **Java tests and Javadoc**, **UI contract, build, and smoke tests**, and **MCP conformance**. PRs use the following routing:

| PR changes | Checks that run |
| --- | --- |
| Recognized repository guides and `docs/**/*.md` | Markdown links and product status; no JDK, npm dependencies, Chromium, or Windows runner. |
| Python MCP client `sdk/python/copperbench.py` or Python unit tests | Python SDK tests plus the always-on repository checks. |
| TypeScript SDK | TypeScript SDK tests and the existing connection-file security regression plus repository checks; installs the UI shell development dependencies, without a UI build or Chromium. |
| Shared MCP fixtures or `sdk/protocol.md` | Both SDK test suites plus repository checks. |
| Allowlisted ordinary editors, translation dictionaries, local CSS, and `ui-shell/tests/` | UI contract tests, production build, bridge/localization tests, and the five Chromium smoke specs, including desktop MCP permission selection. |
| `ui-shell/e2e/**`, Playwright configuration, or shared UI mock fixtures | The same UI checks with the full Chromium suite instead of only the five smoke specs. |
| UI-Core test/validation code | UI contract tests plus repository checks. |
| Java, native Python integration, startup/native UI, shared schemas, production build/dependency configuration, CI itself, or any unrecognized path | Full Java, frontend and Windows MCP regression. |

Mixed changes take the union of their checks. Full regression plans, including `main` and manual runs, also run the full Chromium suite and Python SDK tests on both Ubuntu and Windows. A missing or ambiguous Git comparison selects the full regression. If the selector itself fails or emits invalid outputs, the required checks fail; they only skip after a successful explicit decision. The workflow is always triggered for PRs so a documentation-only change does not leave a required check permanently pending. Its Actions summary lists the selected checks and Chromium coverage.

The small allowlist is in [scripts/ci/select_checks.py](scripts/ci/select_checks.py). Inspect a proposed change locally with:

```sh
python scripts/ci/select_checks.py --paths README.md
python scripts/ci/select_checks.py --paths sdk/python/copperbench.py sdk/tests/mcp-client-reliability.json
python -m unittest discover -s scripts/tests -p 'test_ci_selection.py'
```

For ordinary UI code changes covered by smoke-only routing, run the affected Playwright cases locally as well. Changes to any e2e spec, fixture or shared browser configuration automatically receive the full Chromium suite in PR CI. The daily [Nightly product gates](.github/workflows/nightly.yml) keep the full Chromium suite, Java scale regression and eight generator tracks. Nightly and Windows release tests already build the UI through Gradle's `processResources → buildUiShell` dependency; they do not need another explicit `npm run build` in the same job.

[Linux candidate validation](.github/workflows/stage15-linux-candidate.yml) keeps its separate package, JCEF and Minecraft render checks. PRs that only change allowlisted editors, dictionaries, local styles or frontend tests skip that workflow; native integration, startup and packaging changes retain it. `main` uses broader package-input coverage and manual runs remain available. A passing candidate workflow still does not replace installed-product or gameplay acceptance.

## Prepare a pull request

- Keep each change focused on one problem and preserve unrelated working-tree changes.
- Explain the problem, the resulting behavior, and the checks that support it. Record remaining limits, especially where a test uses a fixture or simulated host.
- Follow the surrounding code and UI conventions. Update the public contract, examples or user guide when behavior changes; retain unknown workspace fields and source ownership rules.
- For a reproduced bug, add a focused regression where it can verify the failure. Use existing tests and fixtures where they cover the behavior.
- Keep JDKs, dependency caches, build outputs, credentials and user workspaces out of commits.

## Upstream and release records

Preserve existing copyright headers, third-party notices and the [project license](LICENSE.txt). Follow [UPSTREAM.md](UPSTREAM.md) for imported or changed upstream code and record the change in [CHANGES-FROM-UPSTREAM.md](CHANGES-FROM-UPSTREAM.md).

[Current downloads](docs/releases/current.md) is the entry point for published platform versions. Update that index when a release is published. Keep previous release notes, source commits, binary hashes and acceptance records tied to the version they describe; a source change or passing test does not publish a package.
