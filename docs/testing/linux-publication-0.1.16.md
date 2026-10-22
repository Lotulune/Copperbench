# Linux 0.1.16 publication

[Linux v0.1.16-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.16-linux-stable) was published at **2026-10-22T06:29:21Z**. Signed release tag `v0.1.16-linux-stable` points to `ec9a70e1e18ff78cebf4c276211363baaca77371`; the unchanged binary source is `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`.

[PR #150](https://github.com/Lotulune/Copperbench/pull/150) authorizes only the fixed candidate `sha256:efae25788a134e82951fb8fea4eef612af79113322cacb5b2bff6bbeae54812f` from main [producer run 39723695135](https://github.com/Lotulune/Copperbench/actions/runs/39723695135). The [independent consumer](https://github.com/Lotulune/Copperbench/actions/runs/39725586361) verified all 17 files, provenance and package content including the UI license notice byte-for-byte. The digest-bound [authorization report](maintenance-linux-0.1.16.md) and its source receipts remain unchanged.

The [promotion workflow](https://github.com/Lotulune/Copperbench/actions/runs/39731584177) verified the allowed-signer tag, latest main, source delta, every frozen asset and provenance, and the complete downloaded draft before publication. Attempt 1 uploaded all 18 draft assets but failed during download with an HTTP/2 `PROTOCOL_ERROR`; independent API size/digest checks matched all 18 expected files. The targeted rerun retained the same tag/source/frozen bytes and completed the full draft comparison and publication.

## Public verification

All 18 public asset sizes and API SHA-256 values match the frozen candidate plus the signed authorization. The downloaded update descriptor is version 0.1.16; all eight component names, sizes and digests match public assets. Public metadata, authorization, checksum and descriptor downloads were byte-hashed against the API records. Full frozen payload/draft downloads were performed by the independent consumer and promotion jobs; this does not claim another local download or execution of every Linux package.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Graphical Setup](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/Copperbench.0.1.16.Linux.x86_64.Setup.tar.gz) | 1117006347 | `9d1d74dc066a11d3724912209bd7eb5c47a06a036405ca04dbca4c9713e325c4` |
| [Debian package](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/copperbench_0.1.16_amd64.deb) | 1030963012 | `96cd39fba71273981e6b8accea18ba73b67f5c2213077b3a0aba1bc9fcd08ebb` |
| [Portable archive](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/Copperbench.0.1.16.Linux.x86_64.tar.gz) | 1114023203 | `b27084b1a7875ba50a6c4e11306892a0e1f932624ba4d6b8b435b99dc5a1bf2b` |

Public retrieval: [checksum list](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/linux-candidate-sha256.txt), [candidate metadata](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/LINUX-CANDIDATE-METADATA.json), [release authorization](https://github.com/Lotulune/Copperbench/releases/download/v0.1.16-linux-stable/LINUX-RELEASE-AUTHORIZATION.json). Source provenance binds producer run 39723695135 and `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`; the signed release tag separately binds the authorization at `ec9a70e1e18ff78cebf4c276211363baaca77371`. Space-to-dot filename normalization does not change bytes.

Raw public API and downloaded metadata verification are retained locally under `output/release-0.1.16/public/linux/`. No new full GNOME/Wayland installed upgrade/uninstall, cold/offline or player-controlled gameplay certification is claimed; `formalSupportClaim=false`. Historical platform evidence and the [Windows + Codex candidate workflow](simple-mode-final-package-2026-10-22.md) keep their original scope. [Current downloads](../releases/current.md) lists both public platforms.
