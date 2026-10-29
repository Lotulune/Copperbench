---
version: 1
slug: "ui-shell-src-app-tsx"
primary_target: "ui-shell/src/App.tsx"
related_targets: ["ui-shell/src/styles/notebook.css","ui-shell/src/components/SimpleCreationView.tsx"]
---

# Copperbench desktop shell — surface brief

Scope: the whole ui-shell (simple creation, professional workbench, specialist editors, dialogs, menus, status). Visitor mode: Operate.
Audience: Minecraft Java players without programming experience (simple creation) and mod developers (professional workbench), Chinese and English, at a desktop PC.
Job: describe or edit a work, see what changed, playtest, export. Standard desktop controls and the existing navigation model stay.
Constraints: React inside JCEF; light/dark/system themes; keyboard access, visible focus, IME composition; real workspace assets only.
User steer (2026-10-30): mature, not childish, not murky-dark; motion crisp and smooth (150–350 ms); unnecessary interface text removed. After the first preview: no yellow highlighter behind text; fewer and smaller rounded corners.

## Direction contract

THESIS: The workbench is a well-kept notebook: white sheets on a cool grey desk, a dot grid where ideas are composed, fountain-pen blue for the one action that matters, and a fine pen underline beneath what is selected. It refuses grey IDE panels with one scattered accent.

OWN-WORLD: Desk #eceef1 (dark: blue-black #14171d); sheets #ffffff (dark #1d2129) with a 1px cool edge and a soft long shadow; 16px dot grid #d6dae1 in composing areas; pen blue #2747a8 for primary actions, focus and the 1.5px selection underline; muted index-tab tints (blue, sand, sage, rose) on element identity plates and badges; ink #1c2230. Corners 6 / 8 / 12 / 14 px. System UI sans (Segoe UI Variable / PingFang / YaHei); mono only for code and ids.

STORY: The player opens their work, talks to the assistant, sees each element marked by its identity plate and the selection drawn with the pen, then playtests and exports with one clear blue action.

FIRST VIEWPORT: 48px title bar on the desk (logo, centred segmented mode switch with a sliding thumb, tools). Left rail on the desk: workspace plate, new-work sheet button with an open icon, element rows with identity plates, conversations, utilities. Title, pen-blue Playtest and Export sit on the desk above two sheets, because the title names both columns (adaptation recorded 2026-10-30): the dot-grid conversation sheet with its composer, and the preview sheet with segmented tabs and checker stage.

FORM: Stationery notebook, #4 of my ordered grounded list; seed:39cfd318df601b57. Signature interaction: the pen underline draws left-to-right under the newly selected label (≈380 ms ease-out), replacing the first build's yellow highlighter at the user's request; views rise in as sheets; controls press to 0.965.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
