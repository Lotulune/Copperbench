# 上游来源与同步基线

本文件固定阶段 0 的外部来源，并定义后续同步规则。发行构建只能使用这里或 `compliance/baseline.lock.json` 中记录的不可变 Git 引用，不能直接从滚动分支发布。

## 固定来源

| 组件 | 固定版本 | 提交 | 上游 |
| --- | --- | --- | --- |
| MCreator | `2026.2.33518` | `361429609b772039a3eb9ab81662c25b225f1d0d` | <https://github.com/MCreator/MCreator> |
| Fabric Generator | `26.1.2-2026.2-2.8` | `abfe19329126b679a26baafe5cade5a75d455528` | <https://github.com/Goldorion/Fabric-Generator-MCreator> |
| Fabric 1.21.1 example template | branch `1.21.1` | `a4c6556aeab4eb100f9f0e3c11d44175384796e6` | <https://github.com/FabricMC/fabric-example-mod> |

MCreator 的 tag 对应 2026-08-14 发布的稳定构建；Fabric Generator 的 tag 对应 MCreator 2026.2 与 Minecraft 26.1.2。完整哈希、许可证快照哈希和构建工具版本见 [`compliance/baseline.lock.json`](./compliance/baseline.lock.json)。

阶段 3 的 Fabric 1.21.1 纵向生成器不是对 Goldorion 当前 26.1.x 模板的版本号替换。其 Gradle/Fabric 基线固定到 Fabric 官方 example mod 的上述提交，并锁定 Minecraft `1.21.1`、Loader `0.19.3`、Fabric API `0.116.15+1.21.1`、Loom `1.17.19` 和 Gradle `9.5.1`；每个生成工作区写入 `.copperbench/generator-lock.json`。

## 分支职责

- `upstream/mcreator-2026.2`：只快进或合并来自 MCreator 固定稳定线的提交，不包含产品功能。
- `upstream/fabric-26.1.x-2026.2`：只保存 Fabric Generator 的已审计导入与后续来源提交。
- `integration/<cycle>`：执行上游同步、冲突解决、兼容测试和品牌扫描；不得作为公开发行来源。
- `main`：已通过当前阶段门禁的产品集成线。
- `release/<version>`：只从通过门禁的 `main` 创建；紧急回移必须记录来源提交、原因和验证证据。

产品源码已经导入当前目录，但目录尚未初始化 Git 仓库。以上规则是后续初始化时必须采用的拓扑；创建仓库、分支和提交需要项目所有者显式授权。

## 同步流程

1. 获取上游 tag/提交并校验 `baseline.lock.json`。
2. 在独立集成分支导入，保留上游提交身份与许可证文件。
3. 更新 `CHANGES-FROM-UPSTREAM.md`，记录产品修改、回移和删除的品牌资产。
4. 运行工作区往返、插件兼容、生成器、UI-Core 契约和 Windows 构建门禁。
5. 只有门禁报告全部通过后才能进入 `main` 或发行分支。
6. 回退使用独立 revert 提交；不重写已共享的上游或发行历史。

## 修改与归属

- 不删除原作者版权和文件级许可证头。
- 产品新增文件使用项目许可证与产品版权声明，不伪造上游作者背书。
- 内置 Fabric Generator 保留作者、来源 tag、许可证和修改记录。
- 上游 `net.mcreator` 包名可作为插件兼容标识保留；用户可见名称、Logo、下载地址和支持入口必须替换或明确标注来源。

## 可复现验证

```powershell
pwsh -NoProfile -File .\scripts\stage0\Test-Baseline.ps1 `
  -MCreatorSource C:\src\MCreator `
  -FabricGeneratorSource C:\src\Fabric-Generator-MCreator
```

构建步骤见 [`docs/build/windows-clean-build.md`](./docs/build/windows-clean-build.md)。
