# SDK contract and source-diagnostic alignment, 2026-10-11

Implemented on `codex/sdk-contract-alignment`, based on merged main
`da3157b580f560cfd0d184b29f23dff73eeec657`. This record describes local source,
backend and browser verification; it does not claim a new installed package or
Minecraft player replay.

## Corrected behavior

- Native Python `field_contract()` now uses
  `NATIVE_FIELD_CONTRACT_UNAVAILABLE` and supplies `availableTypes`,
  `reason=type_not_advertised`, `nextAction`, the original `environment`,
  generator and existing-element inspection guidance. The change matches the
  reconciled SDK behavior in `e408d0c3`, shared by PRs #98/#99. Migration from
  the earlier readiness draft is documented in [SDK readiness](../../sdk/python/READINESS.md).
- Empty or whitespace-only metadata names are invalid responses for all three
  discovery entry points. Errors retain the raw Core envelope and do not close
  the session or create a probe element.
- Native Python, Python MCP, TypeScript and UI preserve double braces literally.
  All four use the [same diagnostic fixtures](../../sdk/tests/diagnostic-rendering.json),
  including nested/unbalanced braces and single-pass substituted values.
- Generation conflicts keep their structured lists and expose individual safe
  diagnostic paths. Readable Java files offer a captured source preview and the
  existing source-editor navigation. Unsafe, unlocated, absent, oversized or
  otherwise unreadable paths do not advertise a preview. File ownership and
  source bytes remain unchanged; source location checks do not grant write access.

The complete discovery, reference paging, generation preflight, doctor and
trusted-delivery additions from PR #100 remain in place. The older branch's
partial field discovery was not substituted for them. Both cancelled research
work packages remain terminated.

## Verification

| Check | Actual result |
| --- | --- |
| Python SDK | 79 passed, including the seven subprocess-based cases brought over from the aligned branch |
| Exact aligned-branch SDK tests | Both test files extracted unchanged from `e408d0c3`: 45 passed against this SDK; the review's pre-fix run recorded eight failures including subcases |
| Shared TypeScript SDK suite | 16 passed |
| UI logic and diagnostic rendering | 30 passed |
| Java task gateway, source protection and preflight | 42 passed, zero failed/skipped across five focused classes |
| New generation-conflict regression | Four cases passed; the pre-fix control failed three and passed the unlocated legacy control |
| Javadoc | Passed with the Java run |
| Headless Chromium interactions | Four passed: existing compiler-source navigation, language-aware source/element navigation, new conflict-source navigation, and language changes without repeating a failed source read |
| Production UI build and localization | Passed after the final copy update |

The new Java regression covers multiple conflict paths, Java/resource/unlocated
targets, traversal/absolute paths, missing and oversized files, unrelated-source
rejection, bounded Unicode presentation, retained raw paths, unchanged source
bytes/revision, and diagnostic locations after task-history restoration.
The source-preview assertion is against the live task's captured bytes; restoring
diagnostic locations alone does not claim a persisted source-preview cache.

Browser checks used a private loopback port and headless Chromium. The conflict
preview screenshot was inspected: the diagnostic path, View source action,
preview and source-editor navigation are visible and usable. The UI bridge uses
controlled fixtures; real backend preview behavior is checked separately by the
Java tests. No host focus or VM interaction was used.

Logs, raw Java XML, aligned test inputs and the browser screenshot are retained
under `build/sdk-contract-fix/`. Initial temporary Playwright configuration and
argument errors were corrected before executing the cases. One existing browser
expectation still named the old Generated source preview label; it was updated
for the neutral Source preview label, and all four cases then passed. Those
earlier attempts remain recorded.

Repository Markdown links, product status and whitespace checks passed after
the final documentation update. Remote CI, commit, push and merge are separate
from the local verification recorded here.
