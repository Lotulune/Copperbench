# Stage 9 external tester protocol

This protocol closes `FR-BETA-03` only after five non-core developers submit
machine-verifiable, anonymous records for the same **signed public Preview
candidate**. Verbal feedback and maintainer-run virtual machines do not count.

## Tester flow

1. Download the Windows **EXE installer** from the selected public Preview
   candidate and verify its SHA-256 against release metadata.
2. Record Windows version/build, x64 architecture, and any preinstalled
   developer tools. Do not record a user name, computer name, email, or absolute
   user path.
3. Install Copperbench and either create a workspace or import an upstream one.
4. Create a first-party element and complete one workspace build.
5. Induce one safe validation/build failure, inspect the structured diagnostic,
   and export the default redacted diagnostic bundle.
6. Create a recovery point, make a reversible edit, and restore the recovery
   point.
7. Uninstall Copperbench and verify the workspace remains present.
8. Review the JSON record for personal data, assign a random anonymous
   `tester-xxxxxxxx` ID, and place it under
   `evidence/stage-9/external-testers/`.

Do not enable the optional workspace-file attachment unless the tester has
reviewed those files and explicitly agrees to share their contents.

The machine record must therefore contain both `diagnosticInspected=passed` and
`diagnosticBundleExported=passed`; the verifier rejects records that omit the
bundle-export step.

## Evidence rule

Each record must validate against
[`external-tester-evidence.schema.json`](../../schemas/external-tester-evidence.schema.json).
All five records must use the same source commit and installer SHA-256, contain
all required task results as `passed`, and contain no P0/P1 issue. The
Public-Beta `--require-complete` path additionally requires `packageType=exe` so
the five humans exercise the same installer that G9.5 validates. Run:

```text
node scripts/verify-external-tester-evidence.mjs
node scripts/verify-external-tester-evidence.mjs --require-complete `
  --expected-commit <40-character-source-SHA> `
  --expected-installer-sha256 <64-character-installer-SHA256>
```

The first command reports current progress without failing an ordinary preview
build. The second is the Public Beta promotion gate and fails until 5/5 valid
records exist for the explicitly selected commit and installer hash. That exact
candidate EXE is subsequently reused byte-for-byte by the Beta publication
contract rather than replaced by a fresh rebuild; see
[Public Beta exact-binary promotion contract](./beta-exact-binary-promotion-2026-08-30.md).
The signed Beta release-source gate invokes this verifier again with
`--require-complete` and the declared candidate commit/EXE hash, so changing a
record or pointing the release at a different candidate closes the gate again.
