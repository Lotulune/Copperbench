# Performance measurements

The dedicated browser entry uses the repository's Playwright dependency and the actual React UI. It runs separately from normal `ui-shell/e2e` tests. Its synthetic MockCoreBridge workspace is isolated and permits queries only.

```sh
node scripts/performance/measure.mjs --dry-run
node scripts/performance/measure.mjs --output output/performance/baseline
node scripts/performance/measure.mjs --cells small:navigation,medium:element-search,large:graph-search --locale en --output output/performance/english
```

Use `--browser-executable PATH` to select an already installed Chromium, `--headed` for a visible browser, or `--port 5180` when the default loopback port 5179 is occupied. Dependencies and browsers must already exist; this entry does not install them or reuse a server from another source tree. Output directories must be new and inside repository `output/` or `build/`.

The default nine cells cover Small/Medium navigation and element search, Medium editor opening, and Large element/asset search and graph opening/reveal. Each cell gets two explicit warmups and collects at least 20 valid repetitions, with a default cap of 25 attempts. `--repetitions`, `--warmups`, `--max-attempts` and `--timeout-ms` control these values. Failures and timeouts remain in the raw attempt denominator and make the collection fail even if 20 successful samples were eventually obtained.

Small/Medium/Large contain exactly 20/200/2000 elements, 100/1000/10000 directed references and 20/200/2000 generated PNG assets. Every tenth element intentionally has one missing target to exercise diagnostics. Fixture construction is deterministic; the report retains the actual fixture, PNG bytes, counts and SHA-256. These are UI projections, not native Minecraft projects.

`report.json` records successful raw arrays, attempt counts, median, maximum and nearest-rank p95 (`ceil(0.95*n)` in sorted order). No p95 or budget verdict is published below 20 valid samples. Individual cell JSON files retain warmups, failures, timeouts, interrupted records, request counts and optional Chromium JS heap observations. The runner preserves source manifests, including tracked and non-ignored added files, dirty deltas, fixture hashes and the selected browser hash.

Timing scopes are explicit:

- Renderer input-to-usable uses `performance.now()` from the received input event to the correct interactive result across two animation frames. Graph opening requires loaded state, the actual SVG/workspace node and the fixture's complete node total.
- Renderer feedback ends at the first visible operation feedback on an animation frame.
- Runner end-to-end uses a single monotonic process clock and includes Playwright dispatch, observation and readback overhead.
- Simulated bridge timing covers MockCoreBridge handling and response serialization. Java Core, JCEF, dependency downloads, builds, product cold startup and native process memory remain unmeasured.

The 200 ms feedback and 500 ms end-to-end budgets are advisory observations. A successful collection records correctness and valid samples; it does not certify performance against every budget. Native/package measurements require their own existing acceptance lifecycle and artifact identity. No cloud telemetry or user workspace content is collected.

```sh
node scripts/performance/compare.mjs output/performance/baseline/report.json output/performance/candidate/report.json
node scripts/performance/measure.mjs --baseline output/performance/baseline/report.json --output output/performance/candidate
```

Comparison requires matching machine/runtime, browser, viewport, locale/theme, fixture and cache conditions. More than 20% p95 deterioration produces `review_required`, requiring a repeat under those conditions; it is not an automatic regression verdict. Missing conditions, nonfinite p95 and low sample counts cannot become a comparable result. No product optimization is selected without a measured bottleneck.

The [native/package task cards](ACCEPTANCE-TASKS.md) prepare P05 behavior/startup/memory observations, a real isolated dependency-download build, a final-package NeoForge 1.21.1 rendering smoke, and the affected restart/data-retention check. `native-observation.mjs` is a passive recorder for the parent's single controller; `acceptance-measurement.py` uses the frozen package's public SDK. Neither supplies desktop input or converts receipts into visual verdicts. They are preparation tools, not completed native acceptance.

Focused tooling checks, after implementation is complete:

```sh
node --experimental-strip-types --test scripts/tests/performance-statistics.tests.mjs scripts/tests/performance-fixtures.tests.mjs scripts/tests/performance-runner.tests.mjs scripts/tests/source-identity.tests.mjs
python -m unittest discover -s scripts/tests -p 'test_verify_changed.py'
node --test scripts/tests/native-observation.tests.mjs
python -m unittest discover -s scripts/tests -p 'test_acceptance_measurement.py'
```

## Contributor verification

```sh
python scripts/verify-changed.py --paths README.md
python scripts/verify-changed.py --base origin/main --dry-run
python scripts/verify-changed.py --paths ui-shell/e2e/scenarios.spec.ts --run --output output/contributor-validation/ui
python scripts/verify-changed.py --run --allow-downloads
```

The default prints a JSON plan without running checks or writing reports. `--paths` accepts explicit repository-relative paths; `--base` compares a branch/ref through the existing merge-base selector and includes current staged, unstaged and untracked files. Without either option it selects the current working-tree changes. All path routing comes from `scripts/ci/select_checks.py`; unknown paths or an incomplete Git comparison select all existing check groups.

Every planned command lists its reason, requirements and environment gaps. Missing JDK/JCEF/display requirements affect native Java checks, not documentation checks. `--run` executes runnable commands, keeps command exit codes, observable test counts and raw logs, and records source identity before/after. A failed build blocks its browser smoke prerequisite. Runnable Gradle verification already builds the UI through `processResources`; the entry avoids a second production UI build.

Existing Gradle tasks can download Wrapper/Maven dependencies, so they remain blocked unless `--allow-downloads` is explicit. No tools/dependencies are installed automatically, no global caches are cleared, and no proxy/global configuration is changed. The existing MCP conformance script uses `npx --yes` for an external CLI and is listed as a manual/CI step even with download opt-in. It cannot silently become a pass.

Execution defaults to a fresh `output/contributor-validation/<UTC>-<id>/report.json`. `--output` chooses a new directory under repository `output/` or `build/`. Old evidence is preserved. Exit codes: `0` for a printed plan or a complete passing execution, `1` for failed/timeout checks or changed tested source, and `2` for environment/manual gaps. Counts unavailable from a command remain `null`; browser fixtures, native acceptance and gameplay retain separate scopes.

## 中文用法

[原生／最终包任务卡](ACCEPTANCE-TASKS.md)明确 P05、启动／进程内存、隔离下载构建、NeoForge 渲染抽查及受影响重开保留范围。原生输入仅由父代理的单一控制器执行；记录器只记派发到观察时间，低样本不出 p95。SDK 回执、Gradle 阶段和人工视觉结论分开；新缓存不证明实际下载，缺少下载／解析标记时保留不完整测量。

性能入口：`node scripts/performance/measure.mjs --dry-run` 先列出选定单元，随后运行 `node scripts/performance/measure.mjs --output output/performance/baseline`。默认每个单元预热两次、取得至少 20 次有效重复；失败、超时和原始数组全部保存。三档 fixture 的数量与摘要可核对。报告只证明声明的浏览器界面范围，Core、原生 JCEF、包启动、依赖下载和游戏行为保持未测；预算缺样本时为 `null`。

贡献者入口：`python scripts/verify-changed.py --paths README.md` 默认只输出计划；`--base origin/main` 复用现有 CI 的比较与路由。明确加 `--run` 才执行，可能下载 Gradle 依赖的任务还需 `--allow-downloads`。环境缺项只阻塞相关命令，既有 MCP 外部工具安装保持手工/CI 项。新报告保存在 `output/contributor-validation/`，有失败返回 `1`，有环境或手工缺项返回 `2`；未执行不会变成产品通过。
