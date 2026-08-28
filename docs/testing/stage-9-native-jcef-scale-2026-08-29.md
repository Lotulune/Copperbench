# Stage 9 fixed-hardware native JCEF scale gate - 2026-08-29

This record closes the two Stage 9 fixed-hardware performance gates that were
left open by the Core-only baseline in
[`stage-9-scale-p95-baseline-2026-08-27.md`](./stage-9-scale-p95-baseline-2026-08-27.md).
The acceptance path uses the production React shell hosted by real Windows
JCEF and the real versioned UI-Core bridge; it does not use the Playwright mock
bridge.

## Gate implementation

The opt-in acceptance test is:

```text
dev.copperbench.shell.Stage9NativeJcefScaleGateTest
```

It is enabled by `-Dcopperbench.stage9.scale=true`. The existing Windows
Nightly product-regression job already passes that property, so the native JCEF
gate is automatically part of the recurring Nightly run.

The fixture creates one real in-memory workspace containing exactly 2,000
elements and 10,000 structured references plus a Procedure containing 500
Blockly nodes. It then measures the following through the production JCEF
product shell:

- 20 alternating element-list filter interactions;
- 20 full Creator Data reference refreshes, including UI-Core query, JSON
  transport, JCEF bridge, React state update, and paint;
- 500-node Procedure open;
- 20 Procedure palette-search interactions while the 500-node Blockly canvas
  is live;
- adding a node through the visible palette, saving it through
  `update_procedure`, returning to the element list, and reopening the same
  Procedure to prove the 501st node persisted.

The P95 target remains 300 ms. Procedure open/save/reopen use a 10 second
usability ceiling; the measured values are far below it.

## Fixed-hardware environment

The local release-gate host recorded by the machine evidence was:

- Windows 11 amd64;
- JBR/JDK 25.0.3;
- JCEF 137.0.17 / Chromium 137.0.7151.104;
- 20 logical processors;
- 16,048 MiB physical memory;
- 1440 x 900 CSS-pixel JCEF viewport at devicePixelRatio 1.0.

The machine hostname is intentionally not recorded. The detailed machine
record is
[`evidence/stage-9/2026-08-29/native-jcef-scale.json`](../../evidence/stage-9/2026-08-29/native-jcef-scale.json).

## Two consecutive real-JCEF executions

The first execution after fixing the JCEF UUID issue passed with:

| Metric | Result |
| --- | ---: |
| 2,000-element filter P95 | 5.8 ms |
| 10,000-reference refresh P95 | 77.3 ms |
| 500-node Procedure open | 969.3 ms |
| Procedure search P95 | 7.7 ms |
| Procedure add-node interaction | 15.0 ms |
| Procedure save | 63.6 ms |
| Procedure reopen | 340.4 ms |

The second execution was forced with `--rerun-tasks` so Gradle could not reuse
the previous test result. It also passed:

| Metric | Result |
| --- | ---: |
| 2,000-element filter P95 | 7.1 ms |
| 10,000-reference refresh P95 | 84.3 ms |
| 500-node Procedure open | 1009.3 ms |
| Procedure search P95 | 5.6 ms |
| Procedure add-node interaction | 11.3 ms |
| Procedure save | 83.0 ms |
| Procedure reopen | 348.4 ms |

The committed JSON evidence is the second forced execution and contains all 20
samples for each P95 metric.

## Product defect found by the gate

The first native run reached the 500-node edit step and exposed a real product
bug rather than a performance failure: `ProcedureWorkbench` used
`crypto.randomUUID()` directly. The production shell is served from
`http://mcreator`, where JCEF 137 does not expose `crypto.randomUUID`, so adding
a Procedure node threw `TypeError: crypto.randomUUID is not a function`.

The workbench now uses a UUID-v4-compatible fallback when the Web Crypto helper
is unavailable. The same fixed-hardware gate then completed add -> save ->
close -> reopen successfully on both real executions.

## Gate result

`procedure-500` and `workspace-2000-10000` can be promoted to `passed`:

- the 500-node Procedure remains interactive in the real JCEF product path and
  survives an actual structured edit/save/reopen cycle;
- the 2,000-element / 10,000-reference workspace stays well below the 300 ms
  P95 target through the real UI-Core/JCEF/React path.

This does **not** close `real-jcef-accessibility`: physical 125%/150%/200% DPI,
screen-reader behavior, and the complete manual keyboard audit remain a
separate beta-blocking gate. It also does not replace the final clean Windows
11 Public Beta/RC replay or external-tester gate.
