---
name: Copperbench
description: The Notebook — white sheets on a cool desk, pen blue for the action that matters.
colors:
  desk: "#14171d"
  light-desk: "#eceef1"
  sheet: "#1d2129"
  light-sheet: "#ffffff"
  sheet-well: "#232833"
  light-sheet-well: "#f6f7f9"
  sheet-edge: "#2a303a"
  light-sheet-edge: "#e0e3e8"
  input: "#191c23"
  light-input: "#ffffff"
  border-subtle: "#2c323d"
  light-border-subtle: "#e2e5ea"
  border-active: "#454d5c"
  light-border-active: "#c8cdd6"
  ink: "#e7eaf0"
  light-ink: "#1c2230"
  ink-muted: "#b4bbc8"
  light-ink-muted: "#4c5566"
  ink-sub: "#9aa2b2"
  light-ink-sub: "#667082"
  pen: "#8ea6ff"
  light-pen: "#2747a8"
  pen-fill: "#4a68d8"
  light-pen-fill: "#2747a8"
  pen-fill-hover: "#5a77e3"
  light-pen-fill-hover: "#1f3a8c"
  pen-wash: "rgba(142, 166, 255, 0.14)"
  light-pen-wash: "#e8edfa"
  text-on-pen: "#ffffff"
  dot: "#2a303b"
  light-dot: "#d6dae1"
  tab-blue: "rgba(142, 170, 242, 0.16)"
  light-tab-blue: "#dfe8f8"
  tab-sand: "rgba(230, 200, 110, 0.16)"
  light-tab-sand: "#f5ecc8"
  tab-sage: "rgba(120, 196, 152, 0.16)"
  light-tab-sage: "#dcebe1"
  tab-rose: "rgba(240, 150, 130, 0.16)"
  light-tab-rose: "#f6e0da"
  state-green: "#86d3a8"
  light-state-green: "#22704a"
  state-amber: "#e9c578"
  light-state-amber: "#855a0c"
  state-red: "#f3a097"
  light-state-red: "#b5362d"
  brand-copper: "#d07a4a"
  light-brand-copper: "#c8643a"
typography:
  headline-page:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI Variable\", \"Segoe UI\", -apple-system, BlinkMacSystemFont, \"PingFang SC\", \"Microsoft YaHei UI\", \"Microsoft YaHei\", sans-serif"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "-0.02em"
  headline-view:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "21px"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  title:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "15px"
    fontWeight: 650
    lineHeight: 1.4
  body:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.55
  body-conversation:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "14px"
    lineHeight: 1.75
  label:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "12px"
    fontWeight: 600
  label-badge:
    fontFamily: "\"Segoe UI Variable Text\", \"Segoe UI\", \"PingFang SC\", \"Microsoft YaHei UI\", sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: 1.35
  mono:
    fontFamily: "\"Cascadia Code\", \"JetBrains Mono\", \"Fira Code\", Consolas, monospace"
    fontSize: "11.5px"
    lineHeight: 1.6
rounded:
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "14px"
  full: "9999px"
spacing:
  gutter: "12px"
  control-gap: "8px"
  section: "34px"
components:
  button-primary:
    backgroundColor: "{colors.pen-fill}"
    textColor: "{colors.text-on-pen}"
    rounded: "{rounded.sm}"
    padding: "7px 14px"
    height: "34px"
  button-primary-hover:
    backgroundColor: "{colors.pen-fill-hover}"
  button-secondary:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "7px 14px"
    height: "34px"
  button-ghost-icon:
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.sm}"
    size: "32px"
  input:
    backgroundColor: "{colors.input}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "6px 11px"
    height: "34px"
  sheet:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.lg}"
  card:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.md}"
    padding: "16px"
  nav-item:
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.sm}"
    padding: "7px 10px"
    height: "36px"
  nav-item-selected:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.ink}"
  badge:
    typography: "{typography.label-badge}"
    rounded: "{rounded.xs}"
    padding: "2px 8px"
  dialog:
    backgroundColor: "{colors.sheet}"
    rounded: "{rounded.xl}"
---

# Design System: Copperbench

## Overview

**Creative North Star: "The Notebook"**

Copperbench is a well-kept notebook on a cool desk. Navigation, the title bar and the status line sit directly on the desk; anything the player works on is a white sheet laid on top of it. A 16px dot grid marks the places where ideas are composed, pen blue is reserved for the one action that matters on a screen, and a fine pen underline draws beneath whatever is selected. The world is mature and quiet: Minecraft content supplies the colour, the interface supplies order.

Density follows the task. Simple creation is airy, with two sheets (conversation and preview) and a desk-level title. The professional workbench is denser: folder tabs on the sheet's top strip, compact rows and cards, and the same controls everywhere. Light is the reference rendition; dark is blue-black ink paper, never brown-black. The user rejected a children's-block palette, a murky oxidised dark, and a yellow highlighter behind text.

This system replaced the copper workbench on 2026-10-30. Copper survives only as the brand mark in the app icon and the workspace plate glyph.

**Key Characteristics:**
- Desk and sheet are the only two levels; panels inside a sheet use its soft well, not another card.
- Pen blue is the single action colour; state colours appear only where they carry meaning.
- Every element type has one identity plate (icon on an index-tab tint) used in every list, card and editor header.
- Motion is short and functional: press feedback, sliding segmented thumbs, sheets rising into place, the pen underline drawing in.
- Healthy states are quiet; problems keep their words.

## Colors

A restrained notebook palette: cool neutrals for desk and paper, one pen-blue accent, and four muted index-tab tints that identify element families.

### Primary
- **Pen Blue** (light #2747a8 / dark #8ea6ff as text, #4a68d8 as fill): primary buttons, the send button, focus rings, selected tab icons, links, the selected segment's icon and the pen underline. Never decoration.

### Secondary
- **Index-tab tints** (blue, sand, sage, rose): element-type plates and badges. Blue is blocks and world, sand is items and equipment, sage is logic and data, rose is recipes, progression and entities. A tint always pairs with its own ink.

### Neutral
- **Desk** (light #eceef1 / dark #14171d): window ground behind the title bar, rails and status line.
- **Sheet** (light #ffffff / dark #1d2129): every work surface, dialog, menu and selected rail row.
- **Sheet well** (light #f6f7f9 / dark #232833): canvases, editor wells and grouped panels inside a sheet.
- **Ink** (light #1c2230), **Ink muted** (#4c5566), **Ink sub** (#667082): main, secondary and supporting text; each passes 4.5:1 on its surface.
- **Dot** (light #d6dae1): the 16px composing grid.

### Named Rules
**The One Pen Rule.** Pen blue appears on at most one filled control per region. Everything else is a white sheet button or a ghost icon.

**The Two Levels Rule.** Desk, then sheet. A third raised level inside a sheet is a soft well, never a bordered card on a card.

**The Brand Mark Rule.** Copper (#c8643a) belongs to the logo and the workspace glyph only; it is not an interface accent.

## Typography

**Display Font:** Segoe UI Variable Text (with Segoe UI, PingFang SC and Microsoft YaHei UI)
**Body Font:** the same system stack
**Label/Mono Font:** Cascadia Code (with JetBrains Mono, Consolas) for identifiers, paths and logs only

**Character:** One sturdy system sans carries every role through size and weight; Chinese and English set together without a second display face.

### Hierarchy
- **Page headline** (700, 28px, 1.3, -0.02em): the overview's workspace name.
- **View headline** (700, 21–22px, 1.25): simple-creation title, view titles, element list title.
- **Title** (650, 15–16px, 1.4): section headings, preview heading, dialog titles (17px).
- **Body** (400, 13px, 1.55): controls and interface text. Conversation text is 14px at 1.75 with a 74ch measure.
- **Label** (600, 12px): list group labels and field labels. Badges are 11px at 600.

### Named Rules
**The Quiet Label Rule.** No eyebrows or kickers above headings; a heading carries itself.

## Layout

The shell is a 48px desk title bar, a desk rail (232px professional, 236px simple) and a 34px desk status line; the work area is one sheet inset 12px from the right edge. In professional mode the sheet's top strip (sheet well) holds folder tabs, workspace actions and the Agent toggle; the Agent panel is a second sheet beside it. Simple creation puts the title and the playtest/export actions on the desk, with the conversation sheet and preview sheet below at a 12px gap (`1fr` / `34%`).

Breakpoints follow the incumbent contract: 1250px and 990px narrow the simple rail and stack the preview under the conversation; 650px collapses the simple rail into a disclosure; 760px shrinks the professional rail to icons and the title bar to brand / switch / tools; 540px hides the frame toggle so both mode labels fit; 420px hides search and shortens the language select. Common gaps are 8px between controls, 12px between sheets and 34px between overview sections.

## Elevation & Depth

Depth is two-level and soft. Sheets sit on the desk with a 1px edge and a long, low-opacity shadow; menus and dialogs lift further. Shadows always carry offset and blur; there are no zero-blur block shadows.

### Shadow Vocabulary
- **Sheet** (`0 0 0 1px var(--sheet-edge), 0 1px 2px rgba(20,30,50,.05), 0 18px 40px -26px rgba(20,30,50,.34)`): work sheets, the selected rail row's lift.
- **Press** (`0 0 0 1px var(--sheet-edge), 0 1px 1px rgba(20,30,50,.06)`): segmented thumbs and selected rows.
- **Menu** (`0 10px 26px -12px rgba(20,30,50,.3), 0 1px 2px rgba(20,30,50,.06)`): popovers, notices, hovered cards.
- **Dialog** (`0 30px 64px -24px rgba(20,30,50,.4), 0 2px 6px rgba(20,30,50,.07)`): dialogs and the command palette, over a 6px blurred overlay.

### Named Rules
**The Rest Is Flat Rule.** Cards rest on a 1px edge; the menu shadow appears only on hover or when something floats.

## Shapes

Corners are restrained and nested: controls 6px, cards and plates 8px, sheets 12px, dialogs 14px. Inner segments subtract their padding (a 3px-padded 8px track holds a 5px thumb). Pills (`9999px`) are kept for the agent status chip and dots. Folder tabs round only their top corners (8px). Callouts are tinted fields with no coloured side stripe.

## Components

### Buttons
- **Shape:** gently rounded (6px), 34px tall.
- **Primary:** pen fill with white text, a faint inner highlight and a pen-tinted soft shadow; hover deepens the fill.
- **Secondary:** white sheet with a 1px edge; hover raises the edge to the active border.
- **Ghost icon:** 32px, transparent until hover; editor back buttons nudge their arrow 2px left on hover.
- **Press:** every control scales to 0.965 for 110ms; large rows and cards scale to 0.985.

### Chips and badges
- **Style:** 11px, 600 weight, 4px corners, tint background with its own ink, no border.
- **State:** "supported" collapses to a check mark; exceptions (preview, unavailable) keep their words.

### Cards / Containers
- **Corner Style:** 8px for cards, 12px for sheets.
- **Background:** sheet on a sheet-well field, or sheet well inside a sheet.
- **Shadow Strategy:** edge at rest, menu shadow and a 2px lift on hover.
- **Internal Padding:** 16px cards, 18–24px sheets.

### Inputs / Fields
- **Style:** 1px border, input background, 6px corners, 34px tall; the border darkens on hover.
- **Focus:** pen border plus a 3px pen-tinted ring. Fields embedded in a composite (search boxes, the composer) let the container take the ring.
- **Error / Disabled:** red ink for invalid help text, which appears only when the value is invalid; disabled fields at 60% opacity.

### Navigation
- **Rail:** rows 36px with 6px corners; hover fills with the active tint; the selected row becomes a lifted white sheet with a pen-blue icon and a pen underline drawn under its label.
- **Mode switch:** a collapsible two-segment track. At rest it shows only the active mode on a white thumb; on hover or focus it widens to both options, and the thumb slides 280ms between "简洁创作" and "专业工作台".
- **Tabs:** folder tabs on the sheet's top strip; the active tab is white and joins the sheet below; close buttons appear on hover.

### Element identity plate
The signature component: a 30–38px square with 8px corners showing the element type's icon on its index-tab tint. It appears in the simple rail, overview, element cards, the create dialog and every specialist editor header.

### Composer
A sheet with a 1px active border and 16px soft shadow at the foot of the dot-grid conversation; focus swaps to the pen border and ring. The provider choice sits inside it; the send button is a pen-filled 36px square that turns neutral when disabled.

## Do's and Don'ts

### Do:
- **Do** keep chrome on the desk and work on sheets; inside a sheet, group with the soft well.
- **Do** use the element identity plate wherever an element is listed or edited.
- **Do** keep healthy states silent: a green dot, a check mark or a number, with the full sentence available to assistive technology.
- **Do** run motion through the shared tokens (`--ease-out` cubic-bezier(0.16,1,0.3,1); 110 / 160 / 220 / 320ms) and remove spatial entrances under reduced motion.

### Don't:
- **Don't** put a yellow highlighter or any filled marker behind text; selection is the pen underline.
- **Don't** add big-number metric tiles; counts read inline as label and number.
- **Don't** use coloured side stripes, zero-blur offset shadows, gradient text or glass as decoration.
- **Don't** reintroduce copper as an interface accent or a brown-black dark theme.
- **Don't** repeat a name the surrounding chrome already shows (a breadcrumb under its own tab, a view title under the desk title).
