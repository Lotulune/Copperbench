# Remaining native/package acceptance task cards

Preparation only. Run these cards after every implementation group is integrated and the parent starts unified verification. Only the parent's existing `node_repl` + `@oai/sky` session supplies native input. The helpers never import a controller, inject DOM, accept EULA or issue authorizations. Freeze the actual final candidate once; supply its identity JSON throughout, and retain each failed attempt/task/source/artifact binding in new `output/minecraft-validation/` folders.

## N: P05 native behavior, startup and process memory

Select bounded cells N01 workspace → Elements navigation, N02 exact search for a known last element and its editable saved fields, N03 asset search for a known last generated texture. Optional N04 opens/searches the graph and checks displayed totals/diagnostics. Prefer a Small native fixture with 20 real elements and 20 fixed PNG assets; record actual element/asset/edge/diagnostic counts and manifest SHA. A smaller existing native workspace can support a labelled behavior cross-check, but does not validate Large asset/graph or equivalent-size timing conclusions. Browser synthetic reference slots/IDs must not be injected into Native.

Use equivalent viewport/scale, locale/theme and cache/prewarm conditions where practical; list differences. The parent may use licensed PNG bytes from `ui-shell/performance/fixtures.mjs` in a NEW isolated workspace's actual mod texture directory, then query its real asset index. Record file/SDK fixture preparation separately from player/native input. Real elements are created through existing product/Public SDK paths and retain IDs/revisions; protected confirmation is not bypassed. N03 checks the final asset pagination/search behavior; the old browser 243.4 ms feedback is advisory, not a native hard target.

1. Read the computer-use skill in the parent. Choose exactly one current returned window matching the verified candidate JVM; confirm PID/start time, executable, product/user/workspace roots. Stop on ambiguity, unexpected dialog or lost focus.
2. Observe and inspect in one cell. Perform ONE state-derived input and refresh immediately in the next. Typing requires a separate current focus observation. Never combine click/focus/type/Enter or reuse old screenshot IDs/indexes after a state change.
3. Time from immediately before the parent controller action to receipt of its refreshed snapshot, using the SAME Node `performance.now()` clock. Inspect that snapshot before a separate human correctness verdict. Controller success is not visual success; no automatic frame or first-paint judgment.
4. Record 1–3 actual repetitions per selected cell for this scoped check, failures included. Low-n p95 stays null. Even with 20 same-condition repetitions, the available percentile is controller dispatch-to-observation, not renderer time or a value comparable with browser 200/500 ms budgets.

Initialize the passive recorder in the parent's persistent Node session:

```js
globalThis.nativeRecordingModule = await import('file:///D:/AICoding/Minecraft_ModCreator/scripts/performance/native-observation.mjs');
globalThis.nativeCandidate = nativeRecordingModule.readCandidateBindings(finalIdentityFile);
globalThis.nativeRecorder = new nativeRecordingModule.NativeObservationRecorder({
  output: 'output/minecraft-validation/completion-20261015/native-observations',
  metadata: { candidate: nativeCandidate, sample: actualNativeFixtureManifest, environment: observedNativeEnvironment }
});
```

Supply actual facts to these three named variables: final identity path; sample counts/hash; OS/session, CPU/RAM, JDK/JBR, viewport/scale, language/theme and cache condition. Do not include connection descriptors/credentials.

After inspecting the current `state`, a one-action/refresh cell can record:

```js
{
  const before = globalThis.state;
  const screenshot = before.screenshots[0];
  globalThis.nativeAttempt = nativeRecorder.begin({
    cellId: 'N01-elements-navigation', operation: 'Elements click through first correct interactive list observation',
    beforeObservation: { windowId: before.window.id, screenshotId: screenshot.id }
  });
  globalThis.state = null;
  try {
    await sky.click({ window: before.window, screenshotId: screenshot.id, x: observedX, y: observedY });
    globalThis.state = await sky.get_window_state({ window: before.window, include_screenshot: true, include_text: true });
    globalThis.targetWindow = state.window;
    nativeRecorder.observed(nativeAttempt, { windowId: state.window.id, screenshotId: state.screenshots[0].id });
    nodeRepl.write('Inspect the refreshed screenshot before judging this observation.');
  } catch (error) {
    nativeRecorder.judge(nativeAttempt, { status: 'interrupted', reason: 'Input/refresh outcome unknown; reobserve before any retry.' });
    throw error;
  }
}
```

`observedX/observedY` must come from the current inspected screenshot. After inspecting the returned snapshot, call `judge(nativeAttempt, {status, reason, evidencePaths})` in a separate cell. Passing requires retained visual/action/observation evidence and the exact expected result. Failed/timeout/unverified records remain in the denominator. Save screenshots for acceptance evidence using the skill contract; do not re-decode them merely for inspection.

For startup use `begin({cellId:'N00-startup', operation:'Parent launch dispatch through usable window observation', startupDispatch:true})` before the parent's EXISTING isolated launch command. End after identifying/observing the usable actual window, then judge separately. Its interval includes dispatch/tool/window enumeration/capture and between-cell coordination; it is not exact product process-start latency. Use n=1–3, p95=null and retain launcher/start/exit receipts. Keep fixture bootstrap/dependency setup distinct.

Read confirmed JVM memory at initial window, idle workspace and after selected cells; select the recorded PID/start/path, never the first Java process:

```powershell
$ErrorActionPreference = 'Stop'
$observedJvm = Get-Process -Id $confirmedJvmPid -ErrorAction Stop
[ordered]@{
  pid = $observedJvm.Id
  processStartedAt = $observedJvm.StartTime.ToUniversalTime().ToString('o')
  executable = $observedJvm.Path
  phase = 'workspace idle after N03'
  workingSetBytes = $observedJvm.WorkingSet64
  privateBytes = $observedJvm.PrivateMemorySize64
} | ConvertTo-Json
```

After confirming identity still matches, pass these values to `nativeRecorder.recordMemory(...)`. They are process point samples, not complete JCEF process-tree RSS, retained heap or a proven peak; no universal memory limit. `report.json` keeps raw attempts, explicit scope, null unknown stages and low-n statistics.

## D: actual dependency-download build

Use a NEW isolated workspace, user/config root and Gradle cache. The harness refuses reused cold cache/profile and previous workspace `.gradle`/`build`. Prepare the fixture through existing product creation, or copy only a specifically identified disposable acceptance fixture's descriptor/elements/source/resources into a new location. Retain source hashes. Exclude `.gradle`, `build`, `run`, user profiles, session credentials, previous task/cache files; preserve established source-ownership data as required by that fixture. Never clean or alter shared cache/user worlds.

`cold-build` uses the frozen package's public `Workspace.open`, runs ONE build and polls its original task. It installs acceptance-only Gradle callbacks and info logging/configuration-cache disablement solely in the NEW isolated Gradle user home. All `GRADLE_USER_HOME`, `COPPERBENCH_GRADLE_USER_HOME` and `copperbench.gradle.user.home` are identical; `user.home`/`COPPERBENCH_HOME` bind the new profile, and `copperbench.gradle.pool` a new isolated directory. Product `GradleDistributionPool` may seed BUNDLED Gradle distributions: record this exclusion, not a dependency download. Actual Gradle-home and resolution-file markers must confirm the intended path; inspect any unexpected external resolution paths.

The independent cold host uses ONE fixed explicit public-SDK argv: package `jdk/bin/java.exe`, `--add-opens=java.base/java.lang=ALL-UNNAMED`, `--enable-native-access=ALL-UNNAMED,jcef`, `-Dcopperbench.productShell=true`, `-cp <product>/lib/*`, `net.mcreator.Launcher`, with cwd at that same package. Its Java executable is checked against the existing frozen `jdk/bin/java.exe` identity. The manifest records entry/argv/runtime SHA separately. The parent tested this entry on candidate 3 through handshake/read/close with no task/mutation (`checks/completion-packaged-stdio-probe-1.json` and `output/minecraft-validation/o3d/stdio-java-probe/receipt.json`). The earlier EXE stdio attempt produced no handshake in that host; retain `checks/completion-cold-download-1.json/.log` and `output/minecraft-validation/o3d/build-1/failure.json` independently, without declaring the EXE globally broken. There is no EXE-to-Java automatic fallback or new configurable launcher mode. Retry only in a NEW fixture/profile/cache/output; desktop build/client still connect to the parent's verified existing app.

Parent writes this config after substituting its ACTUAL final identity and fixture:

```json
{
  "mode": "cold-build",
  "candidateIdentity": "D:/AICoding/Minecraft_ModCreator/output/optimization-2026-10-15/FINAL-CANDIDATE/identity.json",
  "isolationRoot": "D:/AICoding/Minecraft_ModCreator/output/minecraft-validation/completion-20261015-download",
  "workspace": "D:/AICoding/Minecraft_ModCreator/output/minecraft-validation/completion-20261015-download/workspace/first_creation.mcreator",
  "userRoot": "D:/AICoding/Minecraft_ModCreator/output/minecraft-validation/completion-20261015-download/user",
  "gradleCache": "D:/AICoding/Minecraft_ModCreator/output/minecraft-validation/completion-20261015-download/gradle-cache",
  "output": "D:/AICoding/Minecraft_ModCreator/output/minecraft-validation/completion-20261015-download/build-1",
  "generatorId": "fabric-1.21.1",
  "expectedElementName": "trail_block",
  "timeoutSeconds": 1800
}
```

`FINAL-CANDIDATE` is a parent substitution, not an existing artifact claim. Exact command after config preparation:

```powershell
python scripts/performance/acceptance-measurement.py --config output/minecraft-validation/completion-20261015-download/config.json
```

Outputs include manifest, cache-before/after, handshake/environment/fixture-elements, accepted/polls/latest/checkpoint/finished, stages, JAR identities and summary; failures keep failure/cleanup-failure. Stage `elapsedMs` comes from one Gradle-process `System.nanoTime` clock; Python SDK/request/receipt times use `time.monotonic` including IPC and poll delays. Resolution scopes may overlap and include listener file-realization overhead; do not sum them into exclusive phases or call them uninstrumented product timings. Actual download log counts and cache materialization bytes are not HTTP timings/network byte accounting; these unavailable metrics remain null. Wrapper download precedes init loading; unresolved callbacks, bundled distributions/JDK, OS caches, mirror/proxy conditions and host load remain explicit exclusions.

Cold download observation requires matching ACTUAL Gradle home, completed resolution, actual dependency download log and empty pre-run cache/profile. Missing metrics yields `task_succeeded_measurement_incomplete`/exit 2; task success alone is not measured downloading. Network/build/authorization/transport failures retain their original cause/task ID; no retries/fallback/replay. Independent-host timeout cleanup can end in-flight build and is not normal-exit evidence. Desktop timeout retains ownership for parent inspection/resumption.

## R: Windows NeoForge 1.21.1 smoke on exact final bytes

Prepare a new isolated NeoForge 1.21.1 workspace/profile with a real simple block/item and saved-value receipts. Launch the exact final candidate using the parent's existing lifecycle, recording all profile/Gradle-home overrides. Use local normal workspace confirmation; no grant issuance/server/EULA route.

Write two configs for that SAME live desktop/workspace/cache: `mode:'build'`, then `mode:'client'`; both `generatorId:'neoforge-1.21.1'` and actual `expectedElementName`, distinct new output dirs. Set `hostProcessEvidence` to the parent's retained `application-process.json` launch receipt containing actual productRoot/applicationSha256/userRoot/gradleCache; the helper checks these against its final identity and config. Parent still independently identifies the live JVM/window. Client `resumeTaskId` observes an existing UI-started original run_client task without starting a second client. Reuse only identical acceptance init/properties from the first run; different existing files are not overwritten.

```powershell
python scripts/performance/acceptance-measurement.py --config output/minecraft-validation/completion-20261015-neoforge/build-config.json
python scripts/performance/acceptance-measurement.py --config output/minecraft-validation/completion-20261015-neoforge/client-config.json
```

Keep client command and desktop session alive. Parent verifies actual candidate `jdk21` process, PID/start time, game/run directory and original task ID. Parent's single `@oai/sky` controller observes fresh rendered title/menu and, if the affected rendering path needs it, a uniquely named new world/fixture render. This is startup/rendering smoke, not full NeoForge gameplay/persistence certification. Readiness log/screenshot presence alone is insufficient.

Quit through the game menu, one action/refresh at a time; never kill JVM/window or end SDK context before client exits. Helper records terminal/logs/SDK disconnect and artifacts; parent separately verifies process disappearance, normal game Stopping/save logs and normal application lifecycle exit. The helper intentionally keeps `visualBehaviorVerified:false`; parent writes the visual passed/failed/unverified result linked to real screenshots/actions/package/task. On timeout preserve original task ID and reobserve/resume; never replay run_client.

## U: resolve affected restart/data-retention scope

Actual optimization changes did not alter NSIS/installer engine, Windows/Linux launch packaging or `UiLocalePreferences` from the preserved baseline. `platform/export.gradle` broadened docs/SDK payload; C1→C2 altered lock-close ownership. New first-creation reference persistence and editing/session behavior touch saved state, so finish with a FOCUSED application replacement/restart/reopen check. Do not reopen closed installer defects, full install/uninstall, downgrade or official publication.

Use a disposable old-package fixture/profile with known `.copperbench/ui-language.txt`, selected NON-SECRET `.copperbench/userpreferences` values, block name/hardness/resistance and `.copperbench/first-creation.json` binding/display/task state; retain before values/file hashes. Establish this fixture under the old exact package at its FINAL isolated location or reuse the existing isolated path with before evidence. Copying to another absolute path and manually rewriting bindings is not automatic migration. Avoid copying credentials/worlds.

1. Normal-close old package (exit 0/target processes gone). Start final exact package with the SAME isolated profile/workspace paths.
2. Native UI and bundled SDK confirm old language/preference, original IDs, saved fields and first-creation binding/display setting, preserving unrelated ownership. Retain revision/hash/value evidence.
3. Through existing editor change one field, e.g. hardness 3→4, apply ONCE, build ONCE with public SDK (`mode:'build'`). Record task terminal/logs/revision/output JAR SHA and preserved other fields.
4. If proving preference write, change language through product UI. Normal-close/restart final package, reopen same workspace and confirm new field/preference while unrelated known settings remain. Keep actual exits and before/after values, not merely a read-only reopen.

If final integrated byte/source delta has no relevant data/path/persistence impact, parent may record `not_applicable` with that exact reason and reusable old identity. Do not infer impact from git dirty status, or full upgrade acceptance from startup/readback. Original candidate failures retain their original bytes.

## Focused checks: parent only after implementation

```powershell
node --test scripts/tests/native-observation.tests.mjs
python -m unittest discover -s scripts/tests -p 'test_acceptance_measurement.py'
```

These check passive-recording boundaries, low-n/incomplete observations, incremental log safety, clock/scope separation, cache containment and redaction. They never execute Gradle/product/controller/game; D/R/N/U real runs remain separate evidence.
