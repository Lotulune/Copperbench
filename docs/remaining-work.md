# Copperbench remaining work

Published versions and package-specific verification are maintained in [current downloads](releases/current.md). See [current status](status.md) for source capabilities and preview boundaries.

## Current maintenance

- Maintain Minecraft, loader, generator and toolchain compatibility; keep user workflows, bilingual guides and regression coverage aligned with the supported versions.
- Independently check the semantics of the historical Fabric Commands remap warning. The [original integrity/runtime follow-up](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/remaining-work.md#historical-integrityruntime-results-and-open-follow-up) retains the reproduced cases and their evidence.
- Revalidate affected source, installation or gameplay paths using the [change-specific checks](status.md#改动后补哪些验收). Existing records keep their source and package identities.

## Additional quality work

- Real JCEF accessibility verification in a known-good Windows accessibility environment.
- Authenticode signing.
- Broader Linux distributions, architectures and installed game-version coverage; macOS and other platform targets require their own implementation and acceptance evidence.

External-agent and unfamiliar-user studies were terminated by the user. They are not pending work or release gates; completed results retain their recorded limits.

Completed stage plans, audits and maintenance narratives are available in the [historical snapshot](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/remaining-work.md). New follow-up belongs on this page only while it remains actionable.
