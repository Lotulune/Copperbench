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

Before the first Playwright run, install Chromium from `ui-shell` with `npx playwright install chromium`. Java tests that create windows require a display; the Linux CI uses Xvfb. Existing PR workflows in [.github/workflows/test.yml](.github/workflows/test.yml) run the required Java, UI and MCP checks. Generator, installed-product and Minecraft gameplay changes need the relevant runtime evidence described in [AGENTS.md](AGENTS.md) and the corresponding existing test guide; a successful build alone does not establish gameplay correctness.

## Prepare a pull request

- Keep each change focused on one problem and preserve unrelated working-tree changes.
- Explain the problem, the resulting behavior, and the checks that support it. Record remaining limits, especially where a test uses a fixture or simulated host.
- Follow the surrounding code and UI conventions. Update the public contract, examples or user guide when behavior changes; retain unknown workspace fields and source ownership rules.
- For a reproduced bug, add a focused regression where it can verify the failure. Use existing tests and fixtures where they cover the behavior.
- Keep JDKs, dependency caches, build outputs, credentials and user workspaces out of commits.

## Upstream and release records

Preserve existing copyright headers, third-party notices and the [project license](LICENSE.txt). Follow [UPSTREAM.md](UPSTREAM.md) for imported or changed upstream code and record the change in [CHANGES-FROM-UPSTREAM.md](CHANGES-FROM-UPSTREAM.md).

[Current downloads](docs/releases/current.md) is the entry point for published platform versions. Update that index when a release is published. Keep previous release notes, source commits, binary hashes and acceptance records tied to the version they describe; a source change or passing test does not publish a package.
