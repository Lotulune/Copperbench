# Linux 0.1.13 exact-candidate maintenance authorization

The user explicitly requested formal publication after reviewing the Linux 0.1.13
preparation and successful independent cloud verification. This authorization is
limited to the frozen candidate below. Full installed acceptance remains
`not-repeated`; `formalSupportClaim` remains `false`. Publication itself is pending
signed latest-main promotion and the protected draft-download comparison.

This report is digest-bound by the release authorization. Record subsequent
publication outcomes separately; do not amend this frozen acceptance report.

## Source and CI

- Frozen product source: `464b75a39c34252f61c7d859b8d267dfabba205c`.
- Target release tag: `v0.1.13-linux-stable`.
- Main-push candidate: [39264954357](https://github.com/Lotulune/Copperbench/actions/runs/39264954357), attempt 1.
- Independent consumer: [39320163547](https://github.com/Lotulune/Copperbench/actions/runs/39320163547), harness `ce27327d61f43d0cc050b37e272bd2124f98a60f`.
- [Candidate run](../../evidence/maintenance/2026-10-20/linux-0113/candidate-run.json), [candidate jobs](../../evidence/maintenance/2026-10-20/linux-0113/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-20/linux-0113/candidate-consumer-run.json) and [consumer jobs](../../evidence/maintenance/2026-10-20/linux-0113/candidate-consumer-jobs.json) are retained API receipts.

All thirteen required maintenance candidate steps passed: platform/generator and
candidate/installed-gate contracts; packaging and portable/DEB layout; isolated
headless bootstrap; JCEF X11 startup; bilingual graphical-installer rendering;
NeoForge/Fabric 1.21.1 X11 render preflights; metadata freeze; provenance; artifact
upload. Optional NeoForge 26.3 dependency-graph and opt-in acceptance steps were
skipped and are not newly qualified.

## Frozen bytes and independent verification

Candidate identity: `sha256:d2ab0118a34031866c7660eb37070e2d619ff6f314ac2d80b6fb6c3989a6bfac`.

The [unchanged metadata](../../evidence/maintenance/2026-10-20/linux-0113/consumer/LINUX-CANDIDATE-METADATA.json)
remains `development-not-certified`, `formalSupportClaim=false` and
`exactBinaryPromotionEligible=false`. The independent consumer downloaded and
verified **all 17 files**, totaling 4382944408 bytes.
Its [provenance result](../../evidence/maintenance/2026-10-20/linux-0113/consumer/provenance/provenance-verification.json)
and [verified bundle](../../evidence/maintenance/2026-10-20/linux-0113/consumer/provenance/verified-bundle.json)
bind every downloaded file to the exact repository, main source/ref, producing
workflow, run and attempt. The complete small evidence artifact was downloaded
into this checkout and all ten entries in its
[evidence-hash manifest](../../evidence/maintenance/2026-10-20/linux-0113/consumer/evidence-hashes.json)
were checked. This does not claim another local download of the 4.37 GB original
artifact or local execution of its packages.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.13 Linux x86_64 Setup.tar.gz` | 1116762238 | `d7103fa1267c6e791a7deba8397fc764d45e997dcd41f8db01dd2fa3ea5f464d` |
| `Copperbench 0.1.13 Linux x86_64 Setup.tar.gz.build.json` | 312 | `49698f573ca31f9ec5daa82f2ab1109cfe1822e2a72f3d6cb978c46410a524e1` |
| `Copperbench 0.1.13 Linux x86_64.tar.gz` | 1113775627 | `a142f745943778dd541b2b522749942da138ff03090e8d9949be9f381cb171c6` |
| `LINUX-CANDIDATE-METADATA.json` | 4295 | `fbe51e47198c2bb8d62c686c9a00375c5fc328a7a347d8872ea2827fd2564482` |
| `copperbench-linux.spdx.json` | 5676497 | `3a1c480c23f1053763f80e2717c582c819099c9186d5a0c268a31d0cfbf10e69` |
| `copperbench-update-linux-x64-application.zip` | 24089681 | `c83cfab67ba0c583ef95c7f1dc2f649bc759455b374bf97c48b19b41d2c0d7b3` |
| `copperbench-update-linux-x64-gradle-8.8-bin.zip` | 138068016 | `6c441ca22e52b1c7d1a7227b0644bb8edc2e31ff9c4e3d314ce89df51553f868` |
| `copperbench-update-linux-x64-gradle-9.6.0-bin.zip` | 140757737 | `69fb5b5bf8a76969b13e782ab1fd48a53dc108e9d2895b9799dff9a31e42339e` |
| `copperbench-update-linux-x64-gradle-9.6.1-bin.zip` | 140759308 | `1084357d04fa49bda034238242ed9f4f678b40f8fe53e374e97286d359214f24` |
| `copperbench-update-linux-x64-gradle-9.7.0-bin.zip` | 150386410 | `8df29973eb34ea207d3820d88349a774b92dbf89e6995fb38b56ab12bd78a7dd` |
| `copperbench-update-linux-x64-jdk.zip` | 325235938 | `3658eb3be4f0e0a9d4b7e1a8c3dc37795b6e24cffb36ba6b7238936f8af4efe3` |
| `copperbench-update-linux-x64-jdk21.zip` | 150325720 | `24009fdb64d0ee7c084556705eb5ade730423bac2d2ee64ed6a67f959d685d8d` |
| `copperbench-update-linux-x64-libraries.zip` | 45429171 | `3fa7d7b24f0f8058797a3ef3ebfd5c6039e4195a068b493b502627c280f18775` |
| `copperbench-update-linux-x64.properties` | 866891 | `1075a8ec16eeac9de4a903493bd4428b12a2d47c74d2eeab4289bcf9538435d8` |
| `copperbench_0.1.13_amd64.deb` | 1030797936 | `ae4839c26d372e76e56bf55396993021cf0670b94ffdff639c9d63a4533a9b26` |
| `linux-candidate-manifest.json` | 6708 | `bddb592f8f925acf3749c109a464fc217133eefd8eac7ce0626598350d76bff1` |
| `linux-candidate-sha256.txt` | 1923 | `3b412ebe48fe168c60339b28fe69acddd2319ae02739c429112102acc014db1b` |

The [package inspection](../../evidence/maintenance/2026-10-20/linux-0113/consumer/archive-entry-verification.json)
verified the packaged product version and compared the shipped download index,
Linux installation guide and development guide with the exact frozen source.
The 0.1.13 export configuration omits version-specific 0.1.13 release documents;
later Linux notes were not substituted into the immutable package. Legacy
archive-path/configuration-cache fix markers and 1.20.1 data-pack template bytes
were also checked. The historical consumer result's prose says “All eight”; its
actual verified file array contains 17 entries, and the current harness removes
that stale fixed count. The original evidence remains unchanged.

## Authorization and remaining publication gates

`maintenance-ci-0.1.13` permits only the source/run/tag tuple above and requires
explicit release approval, exact candidate/package digests, successful API CI
receipts and this report's SHA-256. The user's “正式发布吧” instruction authorizes
this scoped maintenance publication after the prior verification report.
The [previous 0.1.9 authorization](../../evidence/maintenance/2026-10-20/linux-0113/previous-019-authorization.json) is preserved.

Signed tags from `.github/release-signers`, latest-main checks, protected
production review, the source-delta allowlist, provenance verification and
byte-for-byte downloaded-draft comparisons remain mandatory. The current cloud
checkout has no configured allowed signing key or agent. An existing allowed
signer must create the release tag after the authorization changes are on main.
The active public download declarations continue to identify Linux 0.1.9 until
0.1.13 publication is observed.

## Validation scope

Preparation passed 47 Linux authorization/metadata tests, 11 synthetic package
verifier tests, 9 cloud acceptance boundary tests, Markdown links, product-status,
workflow YAML and source-delta checks. Synthetic fixtures are not candidate
acceptance. No fresh complete GNOME install/upgrade/uninstall, accessibility,
offline, external-user or player-controlled gameplay acceptance is claimed.
Historical 0.1.9 installed and gameplay records retain their original scope.

After publication, public Setup and DEB lifecycle/Native acceptance can validate
an upgrade from 0.1.9 against the actual released bytes. Those later results must
be recorded separately and do not turn automated cloud checks into full GNOME or
player certification. See the [release notes](../releases/v0.1.13-linux.md).
