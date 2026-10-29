# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Copperbench serves Minecraft Java Edition players creating mods and developers who need visual editors, source editing, and local automation. The simple creation surface is for players without programming experience. The existing professional workbench remains available for detailed editing.

## Product Purpose

Create, change, build, playtest, and export a Minecraft work through one shared desktop workspace. A mode switch changes the interface, not the workspace or running task.

## Operating Context

The React interface runs inside the existing Java/JCEF desktop application. Existing Core, generation, task lifecycle, source ownership, and recovery-point contracts remain authoritative. The browser preview cannot execute a host CLI.

## Confirmed Simple Creation Scope

- New users start in simple creation; existing users retain the professional workbench and subsequent mode choices are remembered.
- A conversation uses one host-installed backend: Codex CLI, Claude Code, Grok Build, Google Antigravity CLI, or DeepSeek Harness (`dsh`). Installation and authentication follow each CLI's official flow and existing local configuration.
- After the player submits a goal, the agent may edit, build, and repair. Required approvals and clarification questions are displayed. Playtest and export remain explicit player actions.
- The surface contains the current work, conversations, genuine texture/work metadata, a small set of real editing controls, and an entry to existing recovery points. Full game-scene rendering and per-agent-turn undo are outside this change.
- Antigravity uses its preconfigured permission rules. Denied operations show actionable guidance and can be retried after the player handles the permissions.
- Changing modes or minimizing does not stop work. Exiting stops the agent after any busy-task confirmation; conversation history is retained and reopening never silently resumes execution.
- New works show a recommended Minecraft/loader combination that can be changed before creation. Existing works keep their own target.

## Brand Commitments

Retain Copperbench's existing copper accent, light/dark/system themes, typography, icons, and desktop conventions. Use concise Chinese and English action labels. Display real state and actionable failures; generation imagery is a discussion reference, not shipping game-preview content.

## Evidence on Hand

The user approved the conversational composition, then narrowed previews to real assets and recovery to the existing mechanism. Existing code supplies workspace editing, native Python access, task lifecycle, asset preview, and recovery points. CLI protocol research and local help establish candidate interfaces; actual integration success is established only by the implementation's tests and runtime receipts.

## Accessibility & Inclusion

Maintain keyboard access, visible focus, labeled inputs, readable contrast in both themes, Chinese IME composition, and operation at the desktop shell's supported narrow sizes. Running output must not repeatedly steal focus or force a reader back to the bottom.
