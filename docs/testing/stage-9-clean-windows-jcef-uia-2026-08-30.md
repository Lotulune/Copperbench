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

## Direct renderer provider boundary confirmed

The hidden same-process `Chrome_RenderWidgetHostHWND` was then inspected
directly with `AutomationElement.FromHandle` instead of reaching Chromium only
through the visible `SunAwtFrame` provider tree. This distinguishes a populated
renderer provider that is merely detached from JCEF/AWT from a renderer HWND
that never materializes the DOM UIA tree in the first place.

The restored `main@905af04f` baseline produced exactly one node from the
renderer HWND:

- class `Chrome_RenderWidgetHostHWND`;
- `ControlType.Document`;
- name `Chrome Legacy Window`;
- `rendererDirectElementCount=1`;
- `rendererDirectButtonCount=0`.

That result is recorded in
[`clean-windows11-jcef-uia-renderer-direct-none.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-renderer-direct-none.json).
It rules out the hypothesis that a complete DOM provider already exists at the
renderer HWND but is only disconnected from the AWT/JCEF parent tree.

One final bounded CEF 137 experiment then removed the remaining ambiguity about
automatic accessibility enablement. A temporary, version-locked C API bridge
was packaged into both the Launch4j EXE and application JAR because the EXE is
the first classpath entry. The bridge verified CEF `137.0.17`, validated the C
struct sizes, and called `SetAccessibilityState(STATE_ENABLED)` through the CEF
browser host. The application log confirms successful activation for browser
IDs 1 and 2.

Even after those successful calls, direct UIA inspection of the active hidden
renderer HWND was unchanged: one `Chrome Legacy Window` Document node and zero
buttons. The machine result and activation log are:

- [`clean-windows11-jcef-uia-renderer-direct-cef137-enabled.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-renderer-direct-cef137-enabled.json)
- [`clean-windows11-jcef-uia-renderer-direct-cef137-enabled.log.txt`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef-uia-renderer-direct-cef137-enabled.log.txt)

The temporary ABI bridge was removed immediately after the experiment, and the
guest EXE/JAR were restored byte-for-byte to the `main@905af04f` artifacts.

This narrows the beta blocker further than the earlier JCEF-binding diagnosis.
The missing subtree is not caused by a missing Java method, missing
`WM_GETOBJECT` activation, or failure to attach an already-populated renderer
provider to AWT. With the bundled CEF 137 runtime in JCEF's forced
`CEF_RUNTIME_STYLE_ALLOY` windowed path, the Windows platform provider itself
does not expose the DOM accessibility subtree from the renderer HWND after
`STATE_ENABLED`.

## JCEF / CEF 150 runtime A/B also remains blocked

The next bounded experiment tested whether the provider defect had already
been fixed upstream in a current JetBrains Runtime instead of adding more
Chromium switches to Copperbench. The candidate was
`jbrsdk_jcef-25.0.4.1-windows-x64-b583.48.zip` with SHA-256
`5BD74BFD267B1CD289D0252F08849509DA5EEE60504A39B71DC1C2D7A9E00C3A`.
It reports JBR `25.0.4.1+1-b583.48-jcef`, JCEF/CEF `150.0.14`, Chromium
`150.0.7871.129`, and JCEF API `1.21`.

Copperbench required a temporary source adaptation because JCEF 150 adds the
new `CefResourceHandler.open/read/skip` callbacks. That candidate compiled and
the existing `Stage9NativeJcefAccessibilityTest` passed under real JCEF 150,
so the runtime could initialize the product shell and preserve the existing
DOM/ARIA/keyboard contracts before the clean-guest comparison was attempted.

The runtime was then deployed to an isolated
`C:\Copperbench-G9-JCEF150` directory on the same clean Windows 11 guest. The
original `C:\Copperbench-G9` installation and workspace were not overwritten.
With Narrator running and no synthetic `WM_GETOBJECT`, the newer runtime did
change the native provider shell:

- the main RawView grew to 9 nodes;
- `RootView` and `CefNativeContentsView` appeared;
- the hidden renderer became a visible RawView node named
  `Chrome Legacy Window` with class `Chrome_RenderWidgetHostHWND`;
- direct `AutomationElement.FromHandle(rendererHwnd)` traversal grew from the
  CEF 137 single node to two nodes (`Chrome_RenderWidgetHostHWND` plus the
  unnamed `Document`).

However, neither path exposed any DOM control:
`rawButtonCount=0` and `rendererDirectButtonCount=0`. The raw result is
[`clean-windows11-jcef150-uia-none.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef150-uia-none.json).

One final experiment removed the remaining ambiguity about automatic
accessibility state on CEF 150. The exact CEF source for this runtime is commit
`7c1aa68455db1f1fad159c2b83070ad318212b3d`. A temporary FFM bridge was locked
to CEF/Chromium major 150 and validated the runtime C structures before making
any call: `cef_browser_t=208` bytes and `cef_browser_host_t=592` bytes. Comparing
the exact CEF 137 -> 150 interface shows that the extra 8 bytes are the new
tail `SetAxViewportCollapse` function, so `SetAccessibilityState` remains at
offset 504. The host-side real-JCEF test logged a successful native
`STATE_ENABLED` call after those checks and still passed.

The same bridge build and the same b583.48 runtime were then tested on the
clean guest. The provider tree was unchanged from the JCEF 150 run without the
explicit call: 9 RawView nodes, two direct renderer nodes, and zero buttons.
The machine result is
[`clean-windows11-jcef150-uia-state-enabled.json`](../../evidence/stage-9/2026-08-30/clean-windows11-jcef150-uia-state-enabled.json),
with candidate hashes, runtime versions, ABI checks, and both results summarized
in
[`jcef150-runtime-accessibility-ab.json`](../../evidence/stage-9/2026-08-30/jcef150-runtime-accessibility-ab.json).

The JCEF 150 source adaptation and temporary FFM bridge were removed after the
experiment. The current product runtime therefore remains on its existing
locked JBR/JCEF build: upgrading to b583.48 would add a runtime migration and
API compatibility change without closing the Public Beta accessibility gate.

## Decision

Do **not** merge the experimental CEF-offset bridge or the extra Chromium
feature switches. They did not close the gate and the hard-coded CEF ABI
offsets would create unnecessary maintenance and crash risk.

Retain the improved clean-Windows probe and this failure evidence. Treat the
missing Windows platform accessibility projection as a CEF 137 Alloy/JCEF
runtime blocker requiring one of the following before Public Beta:

1. a newer JCEF/CEF runtime in which the windowed Alloy renderer exposes a
   populated Windows UIA subtree under the same clean-guest probe (the tested
   JCEF/CEF `150.0.14` / Chromium `150.0.7871.129` candidate is **not** such a
   runtime); or
2. an upstream CEF/Chromium fix for the Alloy Windows platform-provider path,
   followed by the same clean-guest Narrator/UIA acceptance run; or
3. a deliberate rendering-architecture change such as OSR plus a maintained
   native accessibility-provider implementation, treated as a separate large
   engineering project rather than a launch-flag workaround.

Physical 150%/175%/200% DPI verification, screen-reader interoperability, and
the complete manual keyboard audit remain required even after the provider
subtree issue is resolved.
