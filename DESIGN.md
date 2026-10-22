---
name: Copperbench
description: Existing desktop workbench visual system with simple creation extensions.
colors:
  bg-base: "#191a19"
  light-bg-base: "#ffffff"
  bg-surface: "#1c1d1c"
  light-bg-surface: "#ffffff"
  bg-panel: "#242624"
  light-bg-panel: "#f4f4f3"
  bg-card: "#1c1d1c"
  light-bg-card: "#ffffff"
  bg-card-hover: "#2b2d2a"
  light-bg-card-hover: "#f3f4f1"
  bg-input: "#20221f"
  light-bg-input: "#ffffff"
  bg-hover: "rgba(255, 255, 255, 0.04)"
  light-bg-hover: "rgba(0, 0, 0, 0.04)"
  bg-active: "rgba(255, 255, 255, 0.08)"
  light-bg-active: "rgba(0, 0, 0, 0.08)"
  border-subtle: "#30322f"
  light-border-subtle: "#e7e7e4"
  border-focus: "#e2aa7a"
  light-border-focus: "#985029"
  border-active: "#6c7268"
  light-border-active: "#89988b"
  text-main: "#e6e8e2"
  light-text-main: "#242622"
  text-muted: "#b1b6ac"
  light-text-muted: "#696d66"
  text-sub: "#9da596"
  light-text-sub: "#61675c"
  accent-copper: "#e2aa7a"
  light-accent-copper: "#985029"
  accent-copper-hover: "#f2bd90"
  light-accent-copper-hover: "#833e11"
  accent-copper-active: "#d49a68"
  light-accent-copper-active: "#6e330d"
  accent-copper-dim: "rgba(226, 170, 122, 0.1)"
  light-accent-copper-dim: "#f4e9dd"
  accent-copper-fill: "#e2aa7a"
  light-accent-copper-fill: "#985029"
  accent-copper-fill-hover: "#f2bd90"
  light-accent-copper-fill-hover: "#833e11"
  accent-copper-fill-active: "#d49a68"
  light-accent-copper-fill-active: "#6e330d"
  text-on-accent: "#241a12"
  light-text-on-accent: "#ffffff"
  badge-green: "#8bc5a5"
  light-badge-green: "#296745"
  badge-green-bg: "rgba(139, 197, 165, 0.1)"
  light-badge-green-bg: "#e8f2eb"
  badge-amber: "#dfbd73"
  light-badge-amber: "#865d15"
  badge-amber-bg: "rgba(223, 189, 115, 0.1)"
  light-badge-amber-bg: "#faf0d8"
  badge-red: "#f49c96"
  light-badge-red: "#b63737"
  badge-red-bg: "rgba(244, 156, 150, 0.1)"
  light-badge-red-bg: "#fceceb"
  badge-blue: "#94bde8"
  light-badge-blue: "#326398"
  badge-blue-bg: "rgba(148, 189, 232, 0.1)"
  light-badge-blue-bg: "#eaf1f9"
  titlebar-bg: "#161716"
  light-titlebar-bg: "#f7f7f6"
  navrail-bg: "#161716"
  light-navrail-bg: "#f7f7f6"
  modal-bg: "#1c1d1c"
  light-modal-bg: "#ffffff"
  modal-overlay: "rgba(5, 7, 10, 0.75)"
  light-modal-overlay: "rgba(15, 23, 42, 0.5)"
typography:
  headline-workbench:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "26px"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.6px"
  headline-creation:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "21px"
    fontWeight: 650
    lineHeight: 1.25
  title-creation:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.4
  title-preview:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "17px"
    fontWeight: 600
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "13px"
    lineHeight: 1.5
    letterSpacing: "0"
  body-conversation:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "14px"
    lineHeight: 1.8
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "12px"
  label-badge:
    fontFamily: "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"Inter\", \"Segoe UI\", Roboto, \"PingFang SC\", \"Microsoft YaHei\", sans-serif"
    fontSize: "11px"
    fontWeight: 500
    lineHeight: 1.2
  mono-detail:
    fontFamily: "\"JetBrains Mono\", \"Fira Code\", \"Cascadia Code\", Consolas, monospace"
    fontSize: "11px"
    lineHeight: 1.6
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
  full: "9999px"
spacing:
  page-gutter: "clamp(20px, 2.4vw, 40px)"
components:
  creation-primary:
    backgroundColor: "{colors.accent-copper-fill}"
    textColor: "{colors.text-on-accent}"
    rounded: "{rounded.sm}"
    padding: "8px 15px"
  creation-primary-hover:
    backgroundColor: "{colors.accent-copper-fill-hover}"
  creation-secondary:
    backgroundColor: "{colors.bg-surface}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.sm}"
    padding: "8px 14px"
  workbench-primary:
    backgroundColor: "{colors.text-main}"
    textColor: "{colors.bg-surface}"
    rounded: "{rounded.sm}"
    padding: "6px 14px"
  workbench-primary-hover:
    backgroundColor: "{colors.text-muted}"
  workbench-secondary:
    backgroundColor: "{colors.bg-panel}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.sm}"
    padding: "6px 12px"
  button-danger:
    backgroundColor: "{colors.badge-red-bg}"
    textColor: "{colors.badge-red}"
    rounded: "{rounded.sm}"
    padding: "6px 12px"
  input:
    backgroundColor: "{colors.bg-input}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  navigation-item:
    textColor: "{colors.text-muted}"
    rounded: "{rounded.sm}"
    padding: "7px 10px"
    width: "100%"
  navigation-item-selected:
    backgroundColor: "{colors.bg-active}"
    textColor: "{colors.text-main}"
  badge-green:
    backgroundColor: "{colors.badge-green-bg}"
    textColor: "{colors.badge-green}"
    typography: "{typography.label-badge}"
    rounded: "{rounded.full}"
    padding: "2px 8px"
  approval-card:
    backgroundColor: "{colors.bg-surface}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.md}"
    padding: "14px"
  composer:
    backgroundColor: "{colors.bg-input}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.lg}"
---

# Design System: Copperbench

## Overview

Copperbench uses neutral desktop panels, thin dividers, a copper accent, system sans-serif text, and Lucide icons. Density serves workspace editing: compact navigation and toolbars frame the current work, while task content receives the available space.

The simple creation surface extends this existing system with a conversation, composer, and actual asset preview. The professional workbench keeps its established component variants. Product scope and the selected conversational composition remain in PRODUCT.md and the simple-creation surface brief.

**Key Characteristics:**

- Neutral surfaces and thin borders separate navigation, content, and supporting panels.
- Copper marks focused controls, selected simple-mode controls, and simple-mode primary actions.
- Readable Chinese and English labels use the existing system font stack.
- Real workspace assets supply preview imagery; existing icons remain functional cues.

This is an extraction of the implementation on 2026-10-21. Visual authority is [tokens.css](ui-shell/src/styles/tokens.css), the final cascade in [global.css](ui-shell/src/styles/global.css) and [workbench.css](ui-shell/src/styles/workbench.css), and the existing components. [simpleCreation.css](ui-shell/src/components/simpleCreation.css) extends that authority. The stylesheet import order in [App.tsx](ui-shell/src/App.tsx) is global, workbench, motion, then simple creation. Read later and more-specific rules before treating an earlier declaration as the effective design.

The supplied browser-fixture captures were visually checked: light 1600 (`output/simple-creation-validation/ui-light-1600.png`), dark 1366 (`output/simple-creation-validation/ui-dark-1366.png`), compact 500 (`output/simple-creation-validation/ui-compact-500-chromium.png`), running (`output/simple-creation-validation/ui-running.png`), approval (`output/simple-creation-validation/ui-approval.png`), permission blocked (`output/simple-creation-validation/ui-permission-blocked.png`), and new work (`output/simple-creation-validation/ui-new-work.png`). The independent finish reviewer returned **ship** for these seven captures; detector findings (`output/simple-creation-validation/design-findings.json`) are empty. That evidence covers the rendered browser fixtures, not native CLI integration or Minecraft gameplay acceptance. This extension introduced no shipping raster asset.

The later DeepSeek Harness extension adds a fifth provider with a locally bundled blue whale SVG. The selected provider, list options, conversations, and assistant messages use the same icon component. Its model-setup action reads “配置指南” / “Setup guide”. The five-provider light menu (`output/deepseek-harness-validation/menu-light.png`), dark menu (`output/deepseek-harness-validation/menu-dark.png`), and compact window (`output/deepseek-harness-validation/compact-menu.png`) were visually checked; six interaction scenarios passed, including keyboard selection and full option visibility at 500×600. See the [scoped validation record](docs/testing/deepseek-harness-2026-10-21.md) for native CLI results.

The review fixes preserve that composition. Clarifying questions now use native radios or checkboxes, optional custom text, option descriptions, and one explicit submit action; permission buttons keep their immediate allow/deny behavior. Removed-content drafts use the existing connection-note styling with a specific discard action. Completed creation forms offer a new-project action while unfinished forms retain their input. Shared close protection includes chat text, while playtest/export checks distinguish unsent chat from workspace edits. The light question form (`output/simple-creation-fixes/question-light.png`), compact dark form (`output/simple-creation-fixes/question-dark-compact.png`), and draft recovery (`output/simple-creation-fixes/orphan-draft-recovery.png`) were visually checked. The [fix verification](docs/testing/simple-creation-fixes-2026-10-21.md) records the focused regressions and native CLI connection results; no new raster asset ships.

## Colors

The palette pairs a copper accent with neutral surfaces and restrained semantic status colors.

### Primary

The `accent-copper` family supplies text, selection, and focus emphasis. The separate `accent-copper-fill` family pairs with `text-on-accent` on simple-mode primary actions, the active mode switch, and dialog primary actions. The dim accent marks selected work items and user conversation bubbles.

**The Variant Rule.** Keep the implemented action hierarchy: simple-mode primary actions and dialog primary actions use copper; the general professional-workbench primary uses `text-main` over `bg-surface`. The title-bar primary has its own transparent treatment.

### Neutral

Use `bg-base` for the window canvas, `bg-surface` for main content, `bg-panel` for supporting areas, and `bg-input` for editable fields. Navigation and title-bar surfaces use their named tokens. Borders distinguish structural separation, interactive edges, and focus through `border-subtle`, `border-active`, and `border-focus`. Main, muted, and supplementary copy use the three text tokens.

Green, amber, red, and blue badge pairs carry status meaning. The simple-mode error panel uses the red foreground, border, and background pair. A permission failure remains an error with guidance.

### Theme contract

Unprefixed frontmatter color keys copy the default dark `:root` / `[data-theme="dark"]` values; `light-` keys copy the corresponding `[data-theme="light"]` values. Frontmatter component references show the root values. The sidecar snippets use the original CSS custom properties so the active theme supplies the correct pair.

[useTheme.ts](ui-shell/src/hooks/useTheme.ts) resolves the saved `system`, `light`, or `dark` preference, follows the system color-scheme query when appropriate, and applies the resolved theme to the document. Preserve the token pairs together. No additional palette or synthesized tonal scale is established here.

## Typography

All interface text uses the existing sans-serif stack recorded in the frontmatter. Tool details and code use the existing mono stack. Fonts are fallbacks in source; this document does not assert that a particular optional font is installed.

| Role | Observed use |
| --- | --- |
| Workbench headline | Workspace overview heading; the final workbench rule overrides the earlier fluid size. |
| Creation headline | Current work name; reduces to 18px at the 990px breakpoint. |
| Creation title | Conversation heading; reduces to 15px at the 650px breakpoint. |
| Preview title | Asset or preview heading. |
| Body | Base desktop interface and controls. |
| Conversation body | Messages with preserved line breaks and wrapping; assistant content is bounded at 74ch, user bubbles at the smaller of 90% and 65ch. |
| Label and badge | Compact fields, navigation metadata, and status. Secondary metadata commonly uses 11px; composer footnotes use 10px. |
| Mono detail | Expandable tool output; the professional task log uses 12px mono text. |

The hierarchy is role-specific, with weights commonly 500 or 600 and the creation headline at 650. There is no declared modular type ratio or separate display face. Keep the established line lengths and wrapping for task text.

## Layout

The shell fills the window and delegates scrolling to its content regions. The shared title bar is 48px high; the desktop status footer is 32px high. The general workbench uses its existing navigation rail and page-gutter token. The simple surface has its own compact rail and does not inherit the professional rail width.

### Simple creation dimensions

| Width | Implemented layout |
| --- | --- |
| Above 1250px | Outer grid: 210px rail plus remaining content. Conversation/preview grid: `minmax(280px, 1fr) minmax(270px, 35%)`. Header and conversation horizontal padding: 26px; preview: 22px. |
| At or below 1250px | Rail: 180px. Conversation/preview grid: `minmax(280px, 1fr) minmax(250px, 34%)`. Main horizontal padding: 20px; preview: 16px. The visible provider label is hidden while the label association remains. |
| At or below 990px | Rail: 162px. Conversation and preview stack in a scrolling content area. Conversation is 510px minimum and 760px maximum; preview is at least 300px. Preview/back controls provide direct navigation. Header actions wrap. |
| At or below 650px | Rail becomes a 42px disclosure bar. Its expanded content overlays below it, with a two-column work list and four-column utilities. Main content scrolls vertically. The conversation fills the content viewport; preview follows below. Conversation padding is 18px. |

The desktop composer area caps at 62% of its chat region and scrolls internally when connection or recovery guidance grows. Approvals live in the conversation scroll area so their details cannot push the composer off screen. At the smallest breakpoint its cap is 70%. The textarea is 76–220px high normally; at or below 650px it starts at 62px and caps at 110px. The desktop header is at least 82px high; the smallest layout uses 72px.

Messages, conversations, and previews use bounded scrolling and `min-width: 0` / `min-height: 0` to shrink inside the shell. Long task text wraps; work and navigation labels can ellipsize. Common control gaps are 8px, message/avatar separation is 11px, and settings fields are separated by 15px. These are observed component values, not a new universal spacing scale.

### Shared chrome and professional layout

The title-bar grid changes at 1100px and 760px. At 760px its columns are `24px minmax(0, 1fr) auto`; command text and shortcut decoration disappear. Existing professional navigation and inspector rules remain in their own stylesheets. At 760px the page-gutter override is 16px and the professional rail uses 56px; the inspector occupies the available content instead of keeping the adjacent main list visible. Do not apply the simple-mode breakpoints to professional screens.

## Elevation & Depth

Neutral surface changes and thin dividers establish most depth. The simple conversation and preview panes have no floating-card shadow. The composer uses `shadow-sm`; the expanded compact navigation uses `shadow-md`; modal cards and the command palette use `shadow-lg`. Exact dark/light shadow values are in the sidecar, extracted from the source tokens.

Dialogs use the theme overlay, a 4px backdrop blur, and a copper top edge. Focus is visible through the copper outline/border and the existing input focus shadow. The simple-mode focus outline is 2px with a 3px offset. Shared buttons retain their more-specific global 2px outline with a 1px offset; the workbench's generic focus rule supplies a 3px offset where not overridden.

Motion supports disclosure and state. Shared button color/border/shadow transitions last 160ms; simple buttons use 140ms background/color transitions. Navigation disclosure uses the source reveal/dismiss durations and easing. The simple activity spinner rotates in 1s. Reduced-motion rules disable the corresponding disclosure, dialog/menu animations, simple button transitions, and spinner animation. Exact motion values and contexts are recorded in the sidecar.

## Shapes

The shared radius vocabulary is recorded in the frontmatter: small controls use `sm`, status pills use `full`, and supporting simple-mode panels commonly use `md`. The composer uses `lg`. Structural panes stay square and divide with 1px lines.

Preserve the existing component exceptions: dialog cards and dialog action buttons have 2px corners; the mode-switch container has 7px corners with 4px segments; send/stop controls have 9px corners. User message bubbles use `12px 12px 3px 12px`. These shapes describe the current components; they do not add new global radius tokens.

## Components

### Buttons

Simple primary actions have a 38px minimum height, 600 weight, and the frontmatter padding/radius; hover uses the copper fill hover token. Simple secondary actions have an active border, surface fill, and 500 weight. Simple buttons become 48% opaque when disabled.

General workbench primary, secondary, and danger controls have a 36px minimum height. Their base padding and radius appear in the frontmatter. Primary hover uses muted text as the fill; secondary hover uses card-hover fill and an active border; danger uses the existing red treatment. Dialog actions override to a 38px minimum height and their own shape. Use the source disabled rules rather than assuming all button families share one state.

Command approvals show the request reason, working directory and exact command in a bounded scrolling area. Repeated protocol metadata remains expandable under the full-operation disclosure. Approval decisions and the composer stay reachable with long native payloads; question wording and answer controls retain their existing presentation.

### Inputs and composer

Shared fields use a subtle 1px border, input background, 32px minimum height, and the frontmatter padding. Focus changes the border and adds `0 0 0 2px var(--accent-copper-dim)`. Placeholder color uses the supplementary text token. Disabled fields use 60% opacity and the hover background.

The simple composer is an input surface with an active border and small shadow; `:focus-within` owns the focus border and ring for the entire input surface. The textarea has no separate focus border, outline or shadow, so typing does not introduce a divider above the toolbar. Its inner textarea keeps the source sizing, 14px text, 1.6 line-height, and native resize behavior. Backend choice, model selection, sign-in, and send/stop are inside the composer. The toolbar wraps when space is limited. The model picker opens toward the available viewport space, supports search, refresh and manual IDs, and preserves keyboard focus. Switching the model creates a conversation and transfers the current draft. Enter sends, Shift+Enter adds a line, and composition events prevent premature submission during IME entry.

### Navigation and mode switch

Professional navigation uses compact neutral rows: selected rows have active neutral fill and main text, without the older leading copper marker. Simple work selection uses dim copper and a subtle border; selected conversations use active neutral fill.

The title-bar simple/professional switch reserves a stable slot while showing only the current mode at rest. Hover or keyboard focus reveals two equal-size segments; a copper slider moves between them in 280 ms. Pointer departure starts a 1,000 ms close delay that is cancelled by re-entry. Keyboard focus keeps options available until blur or Escape; touch opens on the first tap. Reduced motion retains brief color feedback while removing the spatial transitions. Compact layouts keep equal target sizes. Compact navigation exposes its expanded state, closes on Escape, and returns focus to its toggle.

### Badges, containers, and dialogs

Pill badges use 11px text, 500 weight, 1.2 line-height, 2px by 8px padding, and their status pair. Reuse the neutral badge for non-semantic metadata.

Ordinary delivery keeps the build action and a paired row of “new delivery path” / “open build output folder” actions together. The folder action stays to the right of the path action even when a narrow dialog wraps the build action onto its own line.

A simple approval card uses a focus-colored border, surface fill, 14px padding, and the medium radius. Choices wrap; a free-text request uses an associated input label. Error cards use the red pair and supplementary actions. The existing Dialog component supplies labeled modal semantics through the shared dialog accessibility hook; reuse it for modal work.

### Conversation and work preview

The composer keeps the model and reasoning effort together on one line. All five effort labels remain literal; a nearby localized sentence explains model and service support. Manual model entry is always visible in the menu and independent of catalog loading. Paperclip selection, pasted images and dropped files produce removable draft chips with names, sizes and bounded thumbnails. Attachment errors retain the draft. Default permissions and explicitly confirmed automatic approval use labeled native selects; starting another model conversation restores default permissions. These controls wrap using existing theme tokens without adding an inner textarea divider.

User messages use dim-copper bubbles. Assistant messages use a compact copper avatar and plain content; tool output is disclosed under an expandable summary with bounded mono text. The log keeps `aria-live="off"` and follows the bottom only while the reader remains within the source's 72px threshold.

The appearance/settings tabs use tab semantics and Left/Right/Home/End keyboard handling. Selected tabs have a 2px copper underline. Preview reuses the existing asset component: real textures render pixelated over its 16px checkerboard transparency pattern, with real asset metadata. No asset produces an actionable empty state. The recovery-point entry stays at the bottom of the preview column.

Source and interaction details: [SimpleCreationView.tsx](ui-shell/src/components/SimpleCreationView.tsx), [assetPreview.css](ui-shell/src/components/assetPreview.css), [Dialog.tsx](ui-shell/src/components/Dialog.tsx), and [motion.css](ui-shell/src/styles/motion.css).

## Do's and Don'ts

### Do:

- **Do** reuse the theme tokens and preserve the light/dark/system preference.
- **Do** preserve the simple, professional, title-bar, and dialog button variants documented here.
- **Do** keep focus visible, associate field labels, and retain Chinese IME composition handling.
- **Do** show current state, actionable errors, actual assets, and the existing recovery-point entry.
- **Do** let messages wrap and keep the reader's scroll position when they leave the bottom.

### Don't:

- **Don't** replace the established copper, neutral panels, typography, or icon system during a surface extension.
- **Don't** present generated game scenes, fixture messages, or sample success as actual workspace results.
- **Don't** remove keyboard navigation or force every running output update to focus or scroll the conversation.
- **Don't** invent a global spacing scale, tonal ramp, or display typeface from isolated component values.
