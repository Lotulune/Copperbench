# Workspace preferences entry and product icon

The workbench navigation now includes Settings immediately below Help/About. It opens the existing native `PreferencesDialog`, owned by the workspace window, without navigating away or creating a second preferences implementation. Browser previews and hosts without the advertised capability leave the action disabled; invocation failures are shown inline.

The title bar reuses `src/main/resources/net/mcreator/ui/res/icon.png`, the existing product icon. Vite packages the same source asset for browser and installed JCEF rendering. The image is decorative beside the product name and does not interfere with caption dragging.

Validation on 2026-09-14:

- Production localization, TypeScript and Vite build passed.
- Eight Java window/bridge tests passed.
- Twenty-six browser checks passed at 1920 and 1366 widths, including settings availability, host invocation, retained navigation, failure feedback, icon loading and existing native-window gestures.
- Updated the local application's core JAR and launcher, checked their hashes against the build, then reopened an existing workspace. The formal icon and Settings entry were visible; the native preferences dialog opened and Cancel returned to the unchanged workspace.
- The earlier startup Blockbench alignment remains included. No AI/MCP permission behavior or ongoing UI redesign was changed.

Build log: `.tmp/gradle-external/0c02f3d5eba3482bacddbb84794c9d0a.log` in the isolated worktree. Local UI evidence and pre-update binaries are retained under `.tmp/workspace-settings/`; screenshots containing the user's workspace are not included in this commit. A full installer was not rebuilt for this small local application update.
