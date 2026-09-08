# Experimental generator / plugin extension boundary

This directory documents the **experimental** extension-authoring boundary used
for Stage 14 conformance work. It is intentionally not described as a stable
third-party SDK or ABI.

The current contract is metadata-first:

- `capability-manifest.schema.json` describes the minimum manifest shape;
- `fixtures/minimal-generator-capabilities.json` is the conformance fixture;
- `compatibilityLevel` must be `experimental` and `extensionApiVersion` must
  use an `experimental-*` version;
- the manifest declares the Copperbench Core schema range it was checked
  against instead of assuming forward compatibility;
- incompatible or malformed plugins are rejected by the existing plugin-loader
  boundary and surfaced through its failed-plugin diagnostics; setting
  `MCREATOR_PLUGINS_DEV` remains an explicit development override, not a
  compatibility guarantee.

The manifest does **not** grant new runtime privileges and does not bypass
workspace permission, revision, source-integrity, or recovery semantics. A
future stable extension SDK requires a separate ADR defining compatibility
cycles and breaking-change policy.

The fixture is verified by `ExperimentalExtensionManifestTest` in the normal
Java regression suite.
