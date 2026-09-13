# Fresh-agent acceptance requirements and test plan

Prepared before implementation on 2026-09-11. Scope: three independently authored Fabric 1.21.1 mod projects created with the packaged Copperbench product. Permitted inputs are the packaged product, supplied public documentation, and dependencies/source exposed by these new projects. No Copperbench implementation, prior mod examples, or prior trial results will be read.

## Product-trial method

1. Discover the packaged CLI and generators, attempt noninteractive bootstrap, and report any real permission failure. A local human must issue the scoped task authorization.
2. Create each project through `bootstrap create-workspace`; inspect its real environment and generated files.
3. Implement native Java and resources in the created projects as the public documentation permits.
4. Prepare GameTest templates through the product. Replace the load-only starter with requirement-derived behavioral tests. Use `packaged_jar`, unique mod IDs, and an explicit minimum acceptance-test count.
5. Build and run each mod through the product CLI with streaming output. Retain raw stdout/stderr, exact argument arrays, launch/first-output/completion timestamps, exit code, repairs, cache observations, verification JSON, report and artifact hashes.
6. Separate demonstrated assertions from untested user experience. Do not treat a load test, compiler success, or process exit alone as gameplay acceptance.

## resonance_token

R1. A newly created token stack is inactive. Server-side right-click toggles only that stack between inactive and active.
R2. A successful toggle applies a 20-tick item cooldown. A second use during that cooldown must not change state. At tick 20 the item may toggle again.
R3. The player receives actionbar feedback for the new active/inactive state.
R4. Other stacks remain independent. Unrelated item-stack data, including custom name and an unrelated custom-data field, survives both toggle directions.
R5. State survives item-stack codec serialization and deserialization.

Planned cases: default/toggle and separate stacks; cooldown rejection and exact expiry; both actionbar messages; unrelated-data preservation; active and inactive serialization round trips. Client visual appearance of the actionbar remains a separate manual/client observation unless independently exercised.

## tally_stone

T1. The item places a block with count 0.
T2. Normal right-click increments count by one; 15 is a hard upper bound and repeated clicks at 15 do not wrap or exceed it.
T3. Sneak-right-click resets any count to 0, including already-zero count.
T4. Separate block positions do not share a counter.
T5. Saved block state restores its count through the Minecraft world/chunk serialization boundary. A complete client quit/reopen is separately labeled if not performed.

Planned cases: placement/default and first increment; repeated increment and saturation; sneak reset including zero; two-position independence; persistence using the strongest available real server save/load boundary. Tests must exercise actual block/item interaction entry points, not reimplement the counter logic.

## harvest_ledger

H1. A successful server-side break of `minecraft:wheat` at its maximum age credits the breaking player's total exactly once.
H2. Immature wheat, non-wheat blocks, creative-mode breaks, and unsuccessful/cancelled breaks produce no credit.
H3. Different players have independent totals; multiple valid harvests accumulate.
H4. Totals persist across a real Minecraft persistent-state save/read boundary. Full process/world restart is separately labeled if not performed.
H5. A simple read-only command may expose the current player's total; it must not change totals.

Planned cases: mature survival break and exact-once amount; immature and non-wheat negative cases; creative negative case; failed/cancelled break negative case; independent players and accumulation; persistent-state disk save/read round trip; command reporting where practical. Tests must break blocks through the actual server player interaction manager so Fabric's real break event is exercised.

## Evidence and reporting

Each result records requirement IDs, test names, result (pass/fail/blocked/not_run), evidence location, and limits. Product discovery failures are recorded before any repair or retry. Native Java compilation repairs are normal trial iterations and will be retained with diagnostics. If a product implementation fix is required, stop that path and request a fixed package rather than use internal classpaths or a private workaround.

Known initial cache condition: this is a fresh agent and new mod-project trial, not a claimed cold-machine run. Existing machine-level Gradle/Minecraft caches have not been cleared or inspected. Product-managed cache effects will be reported from observable task logs.

Human interventions: pending legitimate first-use authorization. No approval click or grant creation will be performed by this agent.
