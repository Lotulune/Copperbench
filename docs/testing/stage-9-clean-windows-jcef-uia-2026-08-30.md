# Stage 9 clean Windows JCEF UIA investigation - 2026-08-30

This record captures the clean-Windows follow-up for the beta-blocking
`real-jcef-accessibility` gate. The gate remains **blocked**. The purpose of
this run was to determine whether the empty Chromium UI Automation subtree was
caused by Copperbench launch flags, selective Chromium UIA activation, or the
JetBrains JCEF Java binding.

## Fixed environment

- clean Windows 11 Hyper-V guest `Copperbench-G7`;
- Copperbench workspace `guigatedelta` opened through the normal installed
  Windows application;
- JCEF `137.0.17.1142.68de80bc86de497c8d0632ad0f8fe33625b33bff`;
- CEF `137.0.17` / Chromium `137.0.7151.104`;
- Windows Narrator running during every final probe;
- windowed JCEF rendering, not OSR;
- UI Automation inspected from the real Copperbench `SunAwtFrame` downward,
  without filtering descendants to the Java process ID.

The probe was hardened during this investigation so it now records both the
UIA ControlView tree and RawView tree, each node's process ID, and the relevant
native child-window classes. This removed two false-negative risks from the
older probe: cross-process Chromium descendants and provider nodes that only
appear in RawView.

## Findings

The production baseline already used
`--force-renderer-accessibility=complete`. Narrator could see the Copperbench
top-level window, but the Chromium subtree exposed no actionable controls and
the probe reported `buttonCount=0`.

Several bounded A/B experiments were then run against the same guest and
workspace:

| Variant | Observable result |
| --- | --- |
| baseline WR + forced renderer accessibility | Chromium container/document can be reached, but no DOM buttons |
| `UiaProvider` enabled | native `RootView` / `WidgetDelegateView` provider nodes appear, still no buttons |
| CEF `SetAccessibilityState(STATE_ENABLED)` through a version-locked C API experiment | call succeeds for the active browsers, still no buttons |
| CEF accessibility state + `UiaProvider` | RawView reaches `Chrome_RenderWidgetHostHWND -> Document`, still no DOM descendants |
| same combination with Chromium `SelectiveUIAEnablement` disabled | identical terminal result: `rawButtonCount=0` |

After the experiments were removed, the guest was restored to the normal
`main` product code and the same hardened probe was run again. That baseline
reported `rawElementCount=7` and `rawButtonCount=0`; the final experimental
combination reported `rawElementCount=9` and `rawButtonCount=0`. In other
words, the experimental provider flags made additional Chromium native wrapper
nodes visible, but did not expose a single DOM control.

The final experiment's real process arguments were:

```text
--force-renderer-accessibility=complete
--enable-features=UiaProvider
--disable-features=...,SelectiveUIAEnablement,...
```

The same run logged successful native CEF accessibility activation for browser
IDs 1 and 2. Despite that, the RawView tree stopped at an unnamed
`ControlType.Document` and exposed zero buttons. The exact machine result is
[`clean-windows11-jcef-uia-final-experiment.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-final-experiment.json),
with the relevant CEF log lines in
[`clean-windows11-jcef-uia-final-experiment.log.txt`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-final-experiment.log.txt).
The restored-main comparison is
[`clean-windows11-jcef-uia-main-baseline.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-main-baseline.json),
with its actual launch arguments in
[`clean-windows11-jcef-uia-main-baseline.log.txt`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-main-baseline.log.txt).

## Binding boundary confirmed

The bundled JCEF 137 Java module was inspected directly. It does not expose
`CefBrowserHost::SetAccessibilityState`, and it does not contain the CEF
`CefAccessibilityHandler` Java binding. The underlying native CEF export
`cef_browser_host_get_browser_by_identifier` is present, which made the
version-locked C API experiment possible, but that experiment did not make the
renderer DOM available to Windows UIA.

This means the remaining failure is not usefully addressed by adding more
Chromium command-line switches in Copperbench. The product already has a
complete DOM/ARIA/keyboard contract in real JCEF, while the clean guest proves
that the platform provider still does not project those DOM descendants into
Windows UIA.

## WM_GETOBJECT activation ruled out

The hardened probe was extended to enumerate Chromium/CEF/D3D HWNDs across the
entire interactive Windows session rather than only descendants of the
Copperbench `SunAwtFrame`. This exposed a hidden
`Chrome_RenderWidgetHostHWND` owned by the same Copperbench `javaw` process even
though that HWND is not a child of the visible frame hierarchy. The UIA
`Document` itself still reports `NativeWindowHandle=0`.

Three runs from the same final probe implementation then compared explicit
screen-reader activation behavior:

| Probe mode | Hidden same-process renderer found | `WM_GETOBJECT` delivered | `rawButtonCount` |
| --- | --- | --- | ---: |
| `none` | yes | not attempted | 0 |
| `chromium-test` (`wParam=OBJID_CLIENT`, `lParam=1`) | yes | yes | 0 |
| `uia-v2` (`wParam=0`, `lParam=1`) | yes | yes | 0 |

Both synthetic messages were delivered through `SendMessageTimeout` to the
exact renderer HWND selected by the current Copperbench process ID. Neither
changed the RawView tree or exposed any DOM button. This rules out the narrower
hypothesis that the clean guest only failed because Chromium never received a
Windows screen-reader detection / `WM_GETOBJECT` activation message.

The raw machine results are:

- [`clean-windows11-jcef-uia-wmgetobject-none.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-wmgetobject-none.json)
- [`clean-windows11-jcef-uia-wmgetobject-chromium-test.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-wmgetobject-chromium-test.json)
- [`clean-windows11-jcef-uia-wmgetobject-uia-v2.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-wmgetobject-uia-v2.json)

The probe keeps this activation synthetic and opt-in: its default
`-WmGetObjectMode none` remains a pure observation of real Narrator behavior.
The two diagnostic modes are retained only to make this eliminated hypothesis
reproducible.

## Decision

Do **not** merge the experimental CEF-offset bridge or the extra Chromium
feature switches. They did not close the gate and the hard-coded CEF ABI
offsets would create unnecessary maintenance and crash risk.

Retain the improved clean-Windows probe and this failure evidence. Treat the
missing Windows platform accessibility projection as a JCEF/CEF integration
blocker requiring one of the following before Public Beta:

1. a JCEF build/version that exposes the required CEF accessibility API and
   demonstrates a populated Windows UIA subtree; or
2. an upstream/native integration patch with maintainable bindings, followed
   by the same clean-guest Narrator/UIA acceptance run.

Physical 150%/175%/200% DPI verification, screen-reader interoperability, and
the complete manual keyboard audit remain required even after the provider
subtree issue is resolved.
