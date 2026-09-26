# Stage 17：v31 Ubuntu 场景 A 重放

Ubuntu 已安装 v31 通过 Fabric、NeoForge 1.21.1 的生成、构建、六项隔离 JAR GameTest、当前输入导出和关闭重开复验。两轨最终 Mod JAR 分别与 Windows 场景 A 的被测 JAR 字节相同。整体 PRD17 门禁仍开放。

## 输入与结果

入口为 `/usr/bin/copperbench` 和 `/opt/copperbench/sdk/python`，主 JAR SHA-256 为 `c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499`。使用[公开 SDK 重放脚本](../../scripts/stage17-cross-platform-acceptance.py)，没有修改产品或测试宿主以跳过 Gradle 任务。

输入来自[Windows 场景 A](stage-17-v31-scene-a-2026-09-24.md)的实际源码快照清单：Fabric 52 文件、NeoForge 37 文件。传输包 SHA-256 为 `d3863b4c2109a0007988adfb06791e6a897b68af3a63c0a8051573687a7ad029`；打包前逐文件核对 Windows 记录，解包后再次核对。这是已有配置工程的跨平台重放，不是 Ubuntu 从空目录创建或真实 Blockbench 编辑试作。

| 项目 | Fabric 1.21.1 | NeoForge 1.21.1 |
|---|---|---|
| 最终验收任务 | `d4f1880c-3961-48bf-b030-30498893945c` | `ea4d0acd-1c54-489a-a383-e2d89d971ab5` |
| GameTest 区间（UTC） | 2026-09-23 16:48:10～16:49:13 | 2026-09-23 17:22:23～17:22:45 |
| 行为用例 | 6 通过、0 失败、0 跳过、0 框架自检 | 6 通过、0 失败、0 跳过、0 框架自检 |
| Mod JAR SHA-256 | `a7d9ccd4732ce460ef3f282038683ba50e6aa5f43c4d1c8e69b24cea6f6849d5` | `2eec83039c0635bfa0689db5d50dbed623b538739c58c64fdd3c0390f308eec6` |
| 当前输入导出任务 | `f99074f6-1a31-4e3c-a4cc-bfa41761a631` | `011392e1-3a63-48c4-a701-9c8a44446f16` |

六项用例检查硬度/抗爆/0.75 格碰撞、默认完整碰撞、放置与旋转、三槽库存及序列化、破坏掉落七个钻石、模型与 PNG 资源。两轨均为 `sourceCurrentAtCompletion=true`、进程退出 0、`passed_current_input`；导出 JAR/XML 与该任务验收记录相符，关闭重开后记录仍可读取，工作区修订保持 5。

NeoForge 初次生成后，七个生成 Java 文件、工作区元数据及 `.mcreator/setupInfo` 与初始传输清单发生变化；重试脚本逐项记录，未冒称最终源码快照与 Windows 字节相同。定义字段保持不变，最终被测 JAR 则确实相同。最终 Linux NeoForge 快照为 36 文件，SHA-256 `84cf37c8d087230a195b7de6198d76f5b7a5f1ed0cb5a44df2907023a8c9fe2a`。

## 网络、缓存与保留失败

NeoForge 的生成和构建在第一轮已成功，后续 GameTest 准备阶段遇到以下问题。所有失败轮均未执行验收用例，不能写成“六项用例失败”。

| 保留目录（`output/stage17-v31/` 下） | 观察结果 |
|---|---|
| `scene-a-linux-neoforge-first` | `downloadAssets` 获取索引时 DNS/连接失败 |
| `scene-a-linux-neoforge-assets-retry` | `prepareGameTestServerRun` 缺 NeoForge moddev 配置，下载失败 |
| `scene-a-linux-neoforge-probe-error` | 脚本读取已不存在的 setupInfo 失败，未提交产品任务；修复脚本的缺失文件分类后再试 |
| `scene-a-linux-neoforge-config-retry` | 缓存文件存在，但 Gradle 仓库 HEAD 请求仍因 DNS 失败 |
| `scene-a-linux-neoforge-network-restored` | DHCP 修复后 DNS 和资源下载恢复；仓库 HTTPS HEAD 连接重置 |
| `scene-a-linux-neoforge-proxy-ipv6-failure` | SSH 的 IPv6 限制阻止连接宿主 IPv4 回环代理；改用已修复的 VM IPv4 后预检返回 200 |
| `scene-a-linux-neoforge` | 临时回环代理条件下六项测试、导出、重开通过 |

缓存补全仅复用宿主已有且校验过的数据：资源索引 17 的 SHA-1 为 `76d7a97b9e0778fda3b14e474f012450ca0de1bb`，3888 个唯一对象逐一核对大小和 SHA-1，共复制 3889 个缺失文件；另复制一个缺失的 `neoforge-21.1.232-moddev-config.json`，SHA-256 `f3b27379c21e44eb3998c50d8f1da1acd3e4c486ef69f8fbacda75c971280af4`。没有覆盖既有缓存或替换 Gradle 元数据。这些条件意味着本轮不证明空缓存构建。

用户明确授权后，仅重连测试 VM 的 `eth0` / `netplan-eth0` DHCP。连接 UUID 为 `626dd384-8b3d-3690-9511-192b2c79b3fd`，MAC 为 `00:15:5d:00:5f:04`。旧地址 `172.27.34.20/20` 与宿主当前网段不匹配；重连后地址为 `172.17.3.24/20`、网关与 DNS 为 `172.17.0.1`。选定的持久 IPv4 配置字段前后逐字节相同。

VM 对仓库解析出的五个 IPv4 地址均出现 HTTPS 连接重置；宿主现有 `127.0.0.1:3067` 代理访问同一文件返回 200。再次获得用户明确授权后，本轮 SSH 将 VM 的 `127.0.0.1:23067` 临时转发到该代理，仅为测试进程树追加 Java 代理参数。测试完成后 SSH 退出，核对 VM 的 23067 端口不再监听；没有修改持久代理配置。因此 NeoForge 通过结果不代表 VM 默认直连网络已通过。

## 证据与范围

成功材料在 `output/stage17-v31/scene-a-linux-fabric/` 和 `scene-a-linux-neoforge/`，各自包含公开回执、原始日志、被测 JAR/XML、源清单、导出目录与重开读回。完整副本另存 `D:/Hyper-V/Stage17-Linux/guest-evidence-v31/`。网络配置前后、缓存来源、失败轮及通过轮的哈希清单见 `output/stage17-v31/linux-scene-a-proof.json`。

本报告只证明列出的服务端行为与交付身份。客户端视觉/输入、真实 Blockbench 往返、完整语言/JCEF 矩阵、其他轨道、场景 B/C/D 和独立试作仍需各自验收；不得因本轮双平台双 Loader 六用例通过而关闭整体门禁。
