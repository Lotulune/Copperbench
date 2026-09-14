# Window dragging and resizing — 2026-09-13

## Change

The React title bar captures a complete pointer gesture (begin/update/end/cancel). The JCEF window bridge delivers CSS screen coordinates to `WindowsWindowChromeController`, which validates the starting hit target and updates native bounds on the EDT. Updates use the initial rectangle and screen displacement, so moving the window does not accumulate viewport-coordinate errors. The final pointer-up displacement is applied even when a short gesture finishes between animation frames.

Eight resize directions preserve their opposite edges and enforce the existing 500 × 600 CSS-pixel minimum. Double-click toggles maximize; dragging a maximized caption restores the window. Native state notifications keep the maximize icon and resize behavior synchronized. Client controls remain clickable. System move/size commands use the Windows default procedure.

The previous outer-HWND hit-test tests could pass without proving that real JCEF mouse input moves the window. A native-message handoff was also tried during diagnosis; a real short drag reached that handoff after the left button was already released. The final implementation does not depend on starting a modal native drag loop while that button remains pressed.

A separate startup blocker in the current working tree was encountered during real JCEF validation: `BlockbenchOnboarding` called `crypto.randomUUID()` on the internal HTTP origin. That call now reuses the existing `safeRandomUUID` compatibility function. Other Blockbench work was preserved.

## Executed verification

- Production UI build (localization, TypeScript, Vite): passed.
- `gradlew test --tests dev.copperbench.window.WindowChromeHitTestTest --tests dev.copperbench.bridge.JcefWindowBridgeTransportTest --offline`: 8 passed.
- `npx playwright test e2e/adaptive-and-frameless.spec.ts --project=chromium --project=compact-1366`: 22 passed.
- `gradlew jar --offline`: passed; output `build/libs/copperbench.jar`.
- `git diff --check`: passed.

Gradle commands used the repository JBR and its already established process-local TCP selector fallback (`-Djdk.net.unixdomain.tmpdir=D:/AICoding/Minecraft_ModCreator/jdk/jbr25_win_64/bin/java.exe`). No persistent environment setting was changed.

## Computer Use results

Windows Computer Use (`@oai/sky`) first reproduced both failures in the installed `testmod2 - Copperbench 0.1.0` window: caption dragging did not change its position and corner dragging did not change its size.

The rebuilt verification process runs `WindowChromeManualMain`: actual production React resources, JCEF 137 native windowed rendering, the window bridge, and the Windows controller at 125% display scale. Its Core workspace is in memory and user data is isolated under `.tmp/window-verification-user`; it does not modify `testmod2`.

| Real mouse/keyboard operation | Observed result |
| --- | --- |
| Caption drag | Origin changed from (312,175) to (412,250), size stayed 1100 × 760 |
| Corner shrink | Size changed from 1100 × 760 to 1020 × 690 |
| Double-click caption, twice | Maximized to 2048 × 1104, then restored to 1100 × 760 |
| All four corners and all four borders | Each changed the appropriate dimensions/origin |
| Outward bottom-right drag | 1019 × 690 grew to 1025 × 695 |
| Large inward corner drag | Clamped to 500 × 600 |
| Maximize button at minimum size | Maximized successfully |
| Drag maximized caption | Restored to 500 × 600 and followed the pointer |
| Subsequent caption drag | Origin changed from (1120,152) to (1195,227) |
| Alt+Space, Escape | Native system menu opened and dismissed |

Dimensions above are Computer Use screenshot logical dimensions; origins are the screen coordinates reported by the tool. Rounding at 125% scale can differ by one logical pixel. The tool rejects drag endpoints outside the initial window, so outward mouse resizing was checked within the resize border.

Machine-readable observations and the minimum-size screenshot are saved under `evidence/window-chrome/2026-09-13/`. The installed application was not replaced. Cross-monitor mixed-DPI dragging and Windows Snap Layout activation were not verified in this run.

Native command reference: [Microsoft, WM_SYSCOMMAND](https://learn.microsoft.com/en-us/windows/win32/menurc/wm-syscommand), retrieved 2026-09-13. An attempted upstream AWT source retrieval failed; conclusions about the failure are based on local code and runtime observations.
