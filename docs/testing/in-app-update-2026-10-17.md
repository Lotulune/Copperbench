# In-app update acceptance — 2026-10-17

The isolated Windows acceptance passed: the updater checked a TLS-protected feed, downloaded the changed application component, verified the payload, closed the original application normally, replaced the same installation, updated its Windows registration, removed the previous directory and restarted at the target version.

This run used two **acceptance candidates**, labelled **0.1.9 → 0.1.10**. Both contain the updater from commit `fc0e7a68`; the second changes version resources and registrar metadata. These are not public releases, and the existing user installation was not replaced. The network source is loopback HTTPS with a process-scoped certificate store. The production registration code was compiled with separate registry and shortcut destinations.

## Observed results

| Check | Result |
| --- | --- |
| Original application shutdown | Exit code `0` |
| Component download | `23,115,496` bytes, only the application ZIP |
| All available component ZIPs | `1,395,509,810` bytes |
| Java / Gradle / library component downloads | None; reused locally |
| Total transfer including feed and inventory | `24,380,007` bytes |
| Installed files compared with the candidate | `4,764`, all SHA-256 values matched |
| Restarted UI and application log | `0.1.10` |
| Windows registration in the application execution context | `0.1.10`, original target directory |
| User additions, external workspace sentinel and locale | Preserved |
| Previous-version and prepared-update directories | Zero remaining |
| First-run tour after upgrade | Not shown again |

The post-restart app also logged its normal shutdown. Its numeric exit code was unavailable to the external observer; no zero exit code is inferred for that process. System-scope UAC and Linux desktop upgrades were not exercised.

## Reproduction and evidence

Run the relevant Gradle tests, `javadoc`, `exportWin64`, `buildWindowsUpdates` and `testClasses`. Then run `python scripts/prepare-in-app-update-acceptance.py --output <new-directory>`. Launch the generated `launch.args` with the installed candidate's `jdk/bin/javaw.exe`, from its installation directory, with `COPPERBENCH_HOME` set to the generated `profile` directory. Use the real update dialog's **Download update** and **Install and restart** buttons. The test driver injects only the HTTPS source; no alternate endpoint is exposed in the production UI.

Check the generated request log, installation receipt, all candidate file hashes, preserved sentinels and the registry key listed in `registry-key.txt`. Remove only that generated test key after validation. On execution hosts that virtualize registry access, inspect it from the same external Windows context as the application.

- [Machine-readable verification and artifact hashes](../../evidence/maintenance/2026-10-17/in-app-update/verification.json)
- [Update available](../../evidence/maintenance/2026-10-17/in-app-update/01-update-available.png)
- [Download and verification complete](../../evidence/maintenance/2026-10-17/in-app-update/02-update-verified.png)
- [Restarted application](../../evidence/maintenance/2026-10-17/in-app-update/03-restarted-0.1.10.png)

The same source also passed 30 focused Java tests (2 POSIX checks skipped on Windows), 68 UI unit tests, 49 affected Playwright cases, 43 release metadata/authorization tests, Javadoc, production UI compilation, complete Windows component packaging and Markdown link validation.
