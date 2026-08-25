# Stage 8 Offline Claim Alignment

本记录对应 `FR-CLOSE-07`。这里的“离线”只指 Gradle `--offline`，不等同于操作系统断网。

## 证据结论

缓存预热后，以下七条轨道有 `--offline` 构建成功证据，因此进入 `ReleaseManifest.claims.offlineBuildClaimed`：

| 生成器 | 证据 |
| --- | --- |
| `fabric-26.2` | [`fabric-262-compile.json`](../../evidence/stage-8/2026-08-20/fabric-262-compile.json) |
| `neoforge-26.2` | [`neoforge-262-compile.json`](../../evidence/stage-8/2026-08-20/neoforge-262-compile.json) |
| `fabric-26.1.2` | [`fabric-261-compile.json`](../../evidence/stage-8/2026-08-20/fabric-261-compile.json) |
| `neoforge-26.1.2` | [`neoforge-261-compile.json`](../../evidence/stage-8/2026-08-20/neoforge-261-compile.json) |
| `fabric-1.21.1` | [`offline-cached-build.json`](../../evidence/stage-8/2026-08-23/offline-cached-build.json) |
| `neoforge-1.21.1` | [`offline-cached-build.json`](../../evidence/stage-8/2026-08-23/offline-cached-build.json) |
| `fabric-1.20.1` | [`fabric-1201-compile.json`](../../evidence/stage-8/2026-08-19/fabric-1201-compile.json) |

`neoforge-1.20.1` 的 offline exit code 为 `1`，没有进入正式离线列表，并保留 `OFFLINE_BUILD_NEOFORGE_1201_NOT_CLAIMED` 限制：[`neoforge-1201-compile.json`](../../evidence/stage-8/2026-08-19/neoforge-1201-compile.json)。

## 自动门禁

`ReleaseManifestTest` 对 fixture、离线列表顺序和 NeoForge 1.20.1 排除项做一致性断言；`Stage8G7GateTest` 检查 release query 暴露七条离线声明。可复现探针：

```text
pwsh -NoProfile -File .\scripts\verify-stage-8-offline-build.ps1
```

机器可读汇总见 [`offline-build-claims.json`](../../evidence/stage-8/2026-08-23/offline-build-claims.json)。
