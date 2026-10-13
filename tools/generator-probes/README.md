# Exact Java generator environment probes

These isolated projects qualify the dependencies for tickets 03 and 04. They do
not register a Copperbench target, update the application, launch Minecraft, or
establish gameplay acceptance. Each target has its own lock and build result.

| Input | Fabric 26.3 | NeoForge 26.3 |
| --- | --- | --- |
| Minecraft | 26.3 | 26.3 |
| Workspace Java | JetBrains Runtime 25.0.3+1-b329.124, Java 25 | Same exact runtime, separate role receipt |
| Loader | 0.19.5 | 26.3.0.76 |
| Fabric API | 0.162.0+26.3 | Not applicable |
| Build plugin | Loom 1.17.21 | ModDevGradle 2.0.148 |
| Gradle Wrapper | 9.6.0 | 9.6.0 |
| Resource / data pack format | 97.1 / 121.0 | 97.1 / 121.0 |

The `+26.3` in the Fabric API version is literal release metadata, not a Gradle
wildcard. Direct dependencies have exact versions. Gradle locks the resolved
transitive graph, and verification metadata binds its downloaded artifacts.

Loom 1.17.21 also produces a local merged Minecraft JAR whose ZIP entry order and
directory timestamps vary between empty caches. Fabric 26.3 normalizes only that
exact generated container before classpath resolution: sorted entries, a fixed
1980-01-02 timestamp, STORED entries and no extra ZIP metadata. Entry names and
payload bytes are unchanged. The resulting whole-file SHA-256 is still pinned
in `verification-metadata.xml`; there is no trusted-artifact exception. A content
change, duplicate entry, missing artifact or missing checksum fails configuration
without replacing the original file. Already verified canonical bytes are reused.

The normalizer is shipped with generated workspaces and duplicated byte-for-byte
in this probe. Its source digest is included in the environment lock. After Java
and Gradle setup, run the real Gradle boundary regressions explicitly:

```sh
python tools/generator-probes/integration_loom_normalization.py
```

These checks cover archive ordering/metadata, distinct JVM timezones, warm reuse,
content additions/removals, duplicates and raw-byte tampering after normalization.
They run in the Java CI job and retain logs/fixtures under `build/loom-normalization`.
This regression fixture does not replace installed-product or client acceptance.

The NeoForge cold-cache gate also records both official ModDevGradle 2.0.148
plugin marker POMs: the Gradle Plugin Portal and NeoForge Maven publish different
formatting/descriptive fields but the same exact implementation dependency.
Both original byte digests are pinned; the NeoForge POM matches its published
SHA-256. Cold resolution additionally needs five BOM `.module` files that an
already populated cache did not request. Those bytes match fresh Maven Central
downloads and published checksums (SHA-256 for Groovy/JUnit; SHA-1 for Spring
5.3.39, whose `.sha256` endpoint returns 404). Local SHA-256 values for all five
remain required by Gradle. Source receipts and the cold failure/repaired replay
are retained with ticket 29; replaying a failed cache is not a new cold pass.

## Run a probe

Use Python 3.11 or later, the repository wrapper, and the existing Windows external
Gradle launcher. Cache the original artifacts named in the selected
[Fabric lock](fabric-26.3/environment.lock.json) or
[NeoForge lock](neoforge-26.3/environment.lock.json), using their recorded URLs.
The checker never downloads replacements for missing or mismatched files.

From the repository root, verify the source, downloaded artifacts and both runtime
roles without building:

```powershell
python tools/generator-probes/probe_environment.py --target fabric-26.3 --cache output/p263-environment-discovery --workspace-java-home jdk/jbr25_win_64 --product-java-home jdk/jbr25_win_64 --output output/probe-fabric-inputs
```

Use `--build --freeze-dependencies` for the first qualification build. This writes
the project's `gradle.lockfile` and `gradle/verification-metadata.xml`. Review those
generated files, then copy the build receipt's `dependencyLocks` hashes into the
lock's `dependencyGraph`. A replay uses `--build --offline` and requires the same
graph, source and artifacts. Use a new output directory for each attempt to retain
failed evidence. Substitute `neoforge-26.3` for the independent NeoForge probe.

Results distinguish `inputs_verified`, `graph_frozen`, `qualified`, and `failed`.
Only a successful replay can return `qualified`. A failure returns exit code 1,
the failing component/reason where available, and `build.log` for Gradle failures.
Input verification alone does not claim a successful build. `--lock-file` accepts
an explicit saved manifest for rechecking or negative fixtures.

```powershell
python -m unittest discover -s tools/generator-probes -p 'test_*.py'
```

## Sources and evidence

The Mojang manifest and downloaded client have verified upstream SHA-1 digests;
the locks also record independently measured SHA-256 digests. Pack formats come
from `version.json` inside that verified client, not from an assumed release name.
JBR's archive SHA-512 is supplied by JetBrains. This run reuses the installed
runtime and records its release metadata, launcher digest and JCEF module; it does
not claim a fresh archive install. The two roles are recorded independently even
when the product and workspace use the same installation.

- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Fabric Loader versions for 26.3](https://meta.fabricmc.net/v2/versions/loader/26.3)
- [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml)
- [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
- [Fabric 26.3 migration guidance](https://www.fabricmc.net/2026/09/15/263.html)
- [NeoForge 26.3 migration primer](https://docs.neoforged.net/primer/docs/26.3/)
- [JetBrains Runtime release](https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-25.0.3b329.124)
- [MCreator EAP source](https://github.com/MCreator/MCreator/tree/0a4c3c1ceffe202437e9ea143229fa3bf5e1a07e/plugins/generator-26.3.x)

The maintained Copperbench baseline and the EAP reference commit/license are
recorded separately in the locks. The EAP build uses ModDevGradle 2.0.148 and
NeoForge 26.3.0.71-beta; this probe explicitly selects the observed stable Maven
artifact 26.3.0.76. The probe does not import the EAP host or the community Fabric
plugin. Future structured template adaptation remains in its own tickets.

Official migration pages were fetched with `smart-search fetch <URL> --format
json --output <file>`. Its configured broad-search model returned HTTP 503 during
`smart-search doctor`; the available Tavily fetch route retrieved both pages.
Original dependency bytes and metadata were downloaded separately for checksum
validation. Local discovery receipts live under `output/p263-environment-discovery`.
