# Simple creation mode

Status: implemented. Scoped validation and environment limits are recorded in [the verification report](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/simple-creation-2026-10-21.md).

## User flow

Choose or create a work, connect an installed CLI, describe the desired change, inspect the result, and explicitly playtest or export. Each conversation has one backend. A new backend starts a separate conversation in the same work. Existing conversations remain accessible.

New users enter simple mode. An explicit saved preference takes priority; existing preferences identify returning professional-workbench users. Mode switching preserves editors, chat drafts, conversation state, and native tasks.

## Execution contract

The Java workspace owns the CLI service; a JCEF reload or React mode change does not own the process. A trusted main-frame bridge exposes bounded requests and snapshots. Provider adapters handle native structured protocols, approvals, interruption, and session IDs. Conversations are saved privately under the workspace's `.copperbench` directory, without credentials. Reopening shows saved history and waits for user input.

The host's native Python SDK is the supported route into the currently open Core workspace. Agent instructions name the installed SDK and workspace file, require actual field/revision discovery, and preserve generated/manual ownership. No workspace tokens are copied into prompts or configuration.

Antigravity's noninteractive protocol does not support runtime approval responses. Its existing permission policy applies; denied operations are displayed as blocked actions with guidance, even when the CLI exits with zero. A successful process exit alone is not a completed creation.

DeepSeek Harness uses the official host-installed `dsh --profile acp`, inheriting the user's existing Harness model configuration. Its ACP adapter shares transport handling with Grok but uses `session/resume`, verifies the advertised resume capability, and acknowledges `session/close` before stdin EOF. ID-only permission requests resolve against prior tool-call details; missing details cancel the request. Rejected permissions, RPC errors, and session persistence errors remain failures even if the prompt returns `end_turn`. Individual tool errors remain in the transcript without overriding a later completed turn, since retries use new tool-call IDs. The installed Harness also maps some blocked/aborted states to `end_turn`, so this receipt is never product acceptance. EOF releases every pending RPC, including a close request registered after the turn result. Model setup follows `dsh web` → Settings → Models, with a setup-guide action instead of a nonexistent login command.

Harness 0.2.1-rc.2 emits a non-JSON account-catalog success log on stdout during session creation. The adapter recognizes that specific line format and retains it as tool detail. Other non-JSON output remains a protocol error.

## Interface contract

- Clear simple/professional switch in the title bar.
- Current work and conversation navigation on the left; conversation and composer in the center; actual work assets and concise editable fields on the right.
- Backend selection, detection, setup/login guidance, errors, busy state, stop, approvals, and free-text clarifications are visible where relevant.
- Questions preserve their single/multiple selection and custom-answer contract, option descriptions, and one explicit submit action. Permission buttons retain one-shot decisions. Native approval validation maps selected local IDs separately from custom text and refuses invalid or duplicate selections.
- Unsent chat drafts participate in close protection. New, unbound chat text follows the first explicitly selected backend. Removed elements retain their parameter drafts with a visible discard action; drafts for other elements remain intact. Playtesting with unsaved editor input requires an explicit choice to use saved content.
- An unfinished workspace form stays mounted across navigation. A completed form is reset on the next visit or through “Create another project”, including its namespace, location, receipt and creation confirmation.
- Backend choices, the selected backend, conversation entries, and assistant avatars use locally bundled provider identity SVGs alongside names. The selector is a keyboard-accessible combobox/listbox rendered outside the scroll container; Escape/outside click closes it, and the menu stays inside narrow viewports. Monochrome marks adapt to dark mode; colored marks retain their original artwork. Asset origins and license notices are in `ui-shell/src/assets/cli/README.md`.
- Reuse existing workspace creation, recovery points, playtest, export, settings, and task details. No substitute snapshots, fake 3D game renders, or sample success messages in production.
- Preserve the project's light/dark/system themes and compact-window behavior.

## Direction contract

**THESIS:** A player describes a work, sees its real assets, and chooses when to play it. The interface presents the creation task directly.

**OWN-WORLD:** Existing neutral desktop panels, thin dividers, copper selection and primary action, existing sans-serif typography and Lucide icons. Reuse project tokens.

**STORY:** Select a work and CLI, explain the desired change, follow progress or answer a question, inspect the work, then playtest/export.

**FIRST VIEWPORT:** A slim title bar carries the mode switch. A compact left rail leads to works and conversations. The chat is the primary input, with a bottom composer. The right pane presents actual textures and editable work data; playtest/export remain clear actions.

**FORM:** User-selected conversational desktop composition, extended inside the established workbench. Code-led implementation; the generated reference is a composition reference only, superseded by the user's real-preview and existing-recovery decisions. No concept-seed round is needed for this specified extension.

**FINISH:** unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Verification scope

Batch compilation, localization, focused protocol/lifecycle tests, and UI interaction/rendering checks after implementation. Validate actual installed backends separately from fixtures. Record authentication or environment failures as limits, never as successful CLI integration. The existing Minecraft lifecycle remains authoritative for any launched test client.
