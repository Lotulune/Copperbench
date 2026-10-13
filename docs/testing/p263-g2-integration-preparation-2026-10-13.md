# P263 G2 integration inputs

The new `examples/camp-kit-scoped` fixtures combine T01–T13 against a single
structured Camp Kit JAR for each loader. The installed Native harness creates
and edits all five element kinds, generates the workspace, introduces and repairs
a real compile error, checks independent packaged-JAR tests, reopens persisted
definitions and runs the existing export/tamper/missing-case protections.

`package_candidate.py --level G2` selects the scoped fixture and freezes a new
candidate. Its status remains pending acceptance. Ubuntu G2 requires both Xorg
and Wayland client cells from that same candidate. It does not inherit G1 passes.

The integrated tests share one 200-tick Trail Kit fuel contract. Recipe and loot
reloads are queued on the server executor; function effects use a separate world
position from the persisted pillar. The independent host imports no generated
mod constants or source. Raw host runtime verification is still required.

The [GNOME environment probe](https://github.com/Lotulune/Copperbench/actions/runs/38356323070)
successfully started real Xorg with GNOME Shell and the native GNOME Wayland
compositor on Ubuntu 24.04. Wayland reported a native renderer and a 1280x720
virtual monitor. Both exposed the Mutter RemoteDesktop and ScreenCast interfaces.
This was interface/environment qualification only: no Minecraft player input or
saved-world acceptance was performed by that probe.

Relevant checks: 38 candidate/installation boundary tests and 106 Python SDK
tests passed. The cancellation, preview and original-eight-generator regressions
and Javadoc passed; detailed XML is retained under
`output/p263-remaining/cancellation-and-legacy-test-results/`.

Tickets 32–41 remain open until their actual candidate-bound requirements pass.

## Integrated runtime results and follow-up

The [second Fabric G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38360248775)
passed both installed Native executions: cold 311.923 seconds from an empty
dependency cache and warm 254.760 seconds. Both ran all thirteen required cases,
the intentional compile error and repair, cancellation, missing-case and tamper
rejections, reopening, and independent portable checks. The workflow failed in
the final cell aggregator because it still required three Native processes;
G2 correctly adds a fourth process for structured element creation. The original
run remains failed and its raw evidence is preserved under
`output/p263-remaining/cloud/fabric-g2-attempt-2/`.

The [second NeoForge G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38360256142)
built and repaired the generated mod but failed compilation of the independent
host: the integrated block fixture had omitted `WorldReopenChecks`. The existing
independently exercised helper is now included. These failures do not establish
a G2 support claim.

The corrected aggregator validates the exact G1/G2 process count, distinct PIDs
and clean exits, retains the five structured contracts and element receipts,
and links the five element checks to actual thirteen-case evidence. All 39
candidate/installation boundary checks passed. Fresh candidate runs retain their
own source and artifact identities; earlier failures are not relabeled.

The [GNOME adapter run](https://github.com/Lotulune/Copperbench/actions/runs/38362118611)
passed on both real Xorg and native Wayland. It captured the desktop and reserved
and released F8 without sending input. A task-local bounded request queue now
keeps the installed Native lifecycle alive while returning each action receipt
and screenshot for planner inspection. It uses the existing bridge engine and
does not claim that an MCP connection was registered. Actual client qualification
remains pending.

The [third Fabric G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38362518685)
subsequently produced a passing installed cell for candidate
`sha256:ab57577c493f017baa3079bc33a82265b144f6f7eeb55a8fe5dda000e937f4be`.
Its mod JAR is `d77a9e64dad8913d72089ac3264675c0d21d26723f5f925ef149f8c09907fe6a`;
the installed cell SHA-256 is
`a09c4747c06bf331335d8220f5f50c7633ca103f358de3e93003ae346fffbb2e`.
Four current/historical bundles independently passed in a clean local directory
and both tampered bundles were rejected. Xorg/Wayland player cells are pending.

The [third NeoForge G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38362120404)
passed actual cold (362.614 seconds) and warm (188.373 seconds) Native acceptance,
including all thirteen cases and negative protections. It still contained the
old three-process cell assertion and failed only in aggregation. Its four
portable bundles also independently passed and both tampered bundles were
rejected. Fresh client runners use the corrected external aggregation harness
and retain its separate source/hash identity.

## Repaired candidates and independent review

The subsequent NeoForge cold run exposed a real shared-recipe race: the reload
changed `camp_shaped` to four outputs before the baseline test checked two.
The four fixtures now wait for all four baseline completions before applying
the single reload. The baseline deliberately waits 40 ticks, beyond the old
20-tick assumption. Original output-count assertions remain unchanged.

| Candidate | Installed run | Result |
| --- | --- | --- |
| Fabric G2 `sha256:5af68d4a7690efa0038675bab96987d888bb624f1b8550cfcb74a1a14006203c` | [38369155468](https://github.com/Lotulune/Copperbench/actions/runs/38369155468) | Cold/warm thirteen cases and negative gates passed |
| NeoForge G2 `sha256:25c777914cbea3ce14fe59c0f9a03a79613d7c03cd3f7631ebd480ec894121d8` | [38371185225](https://github.com/Lotulune/Copperbench/actions/runs/38371185225) | Cold/warm thirteen cases and negative gates passed |
| NeoForge G1 `sha256:65dbd37b779ff7259273c498f0830646809ef6506f9b2b30d296b0ccfacc24e6` | [38371179293](https://github.com/Lotulune/Copperbench/actions/runs/38371179293) | Cold/warm installed gate passed |

Each row was independently reviewed in a new local directory with the standalone
verifier and Python isolation: four current/historical bundles passed and two
tampered bundles were rejected. Evidence resides in
`output/p263-remaining/{fabric-g2-order-fixed,neoforge-g2-order-fixed,neoforge-g1-runtime-fixed}-offline-review/`.
The NeoForge candidates also include `DevLaunch:1.0.2` in Linux `runtimeClasspath`
dependency locking; its already verified component checksum was not weakened.

The actual Fabric Wayland attempt
[38368061083](https://github.com/Lotulune/Copperbench/actions/runs/38368061083)
showed a native Minecraft window, but reviewed clicks and Tab did not change
the welcome screen. Its verdict is **unverified**. Control detached before
cancellation; cancellation is not normal player save-and-quit. No world was
created. Original screenshots, receipts and failure records remain intact.

The lightweight real GTK probe
[38377906115](https://github.com/Lotulune/Copperbench/actions/runs/38377906115)
passed complete text and click callbacks on GNOME Xorg. Wayland delivered only
`bc123` and no pointer callback. Raw events show keyboard focus arriving with
the first attempted key. The task adapter is being corrected to establish the
headless seat's input capabilities before clients bind; no acceptance claim
depends on this hypothesis. The earlier unpressed-release initialization was
removed after Mutter rejected it. The
[Mutter session implementation](https://raw.githubusercontent.com/GNOME/mutter/gnome-46/src/backends/meta-remote-desktop-session.c)
confirms that release cannot initialize a missing device. Source retrieval used
`smart-search fetch`; its main search provider was unavailable, while exact
source fetching succeeded. Locked bridge dependencies now run in a disposable
project venv, including the previously missing Xorg capture dependency `mss`.

Windows disk capacity is sufficient again. New G1/G2 packages are building in
[38377909493](https://github.com/Lotulune/Copperbench/actions/runs/38377909493).
The server runner only builds packages; acceptance will run on the actual
Windows 11 workstation. Tickets 32–41 remain in progress.

The corrected seat setup subsequently passed both real desktop input probes in
[38378300288](https://github.com/Lotulune/Copperbench/actions/runs/38378300288):
each received exactly `abc123` and one button callback. Wayland advertised two
task-created virtual devices before the test client connected; creation sent no
key/button/motion events. Xorg retained its existing input devices. The raw
callbacks, screenshots and actions are retained under
`output/p263-remaining/cloud/gnome-input-probe-4/`. This establishes the adapter's
measured input delivery, while Minecraft player acceptance remains separate.
