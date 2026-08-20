# 阶段 5 G2 NeoForge 验证记录

- 结论：G2 自动化门禁通过，NeoForge 1.21.1 已具备桥接集成条件。
- 环境：Windows 11 x64，仓库 JDK 21.0.12，Gradle 9.6.0。
- 复现脚本：[`verify-stage-5-neoforge.ps1`](../../scripts/verify-stage-5-neoforge.ps1)。

## 已通过

- Gradle wrapper 使用 `https://mirrors.huaweicloud.com/gradle/gradle-9.6.0-bin.zip`；全局 `C:\Users\<user>\.gradle\init.d\aliyun.gradle` 将 Maven Central、Plugin Portal、Google 和 JCenter 的官方入口重写为阿里云镜像，并保留 NeoForge/Minecraft 专用仓库。
- NeoForge 生成器、任务网关、Fabric/NeoForge 路由和统一服务契约测试通过。
- 真实 NeoForge 1.21.1 golden build 通过，产出可加载 JAR。
- 真实 `runClient` 通过；客户端完成资产加载、NeoForge 初始化并输出 `COPPERBENCH_STAGE5_NEOFORGE_READY`。
- 完整 Copperbench 回归：75 项执行，68 项通过，7 项平台/原生 smoke 跳过；其余无失败。

## 下载与缓存

- PCL2 目录 `D:\Minecraft\.minecraft` 已被检查；其 `assets\indexes\32.json` 与本次 1.21.1 的 `17.json` 不同，因此没有冒充复用。
- 精确 `17.json` 的 SHA-1 为 `e5cb391af96038a8af638bf7cc7f2be44bc4b843`；NeoForge 资产对象按官方哈希校验后存入管理员 Gradle 缓存。
- 缺失对象通过国内 BMCLAPI `https://bmclapi2.bangbang93.com/assets/<prefix>/<hash>` 预填充，最终索引覆盖率为 3911/3911；构建工具仍执行官方哈希校验。

## 保留风险

- 首次运行若没有资产缓存，NeoForge 会下载约 1 GB 的 1.21.1 客户端资源，耗时取决于网络；国内源配置和缓存预填充脚本只影响下载路径，不改变版本锁定。
- `gradle test` 不带过滤器还会包含上游 `net.mcreator.integration` 的长时间 Tooling/UI 集成套件；阶段证据使用 Copperbench 过滤测试和显式 G2 门禁。
