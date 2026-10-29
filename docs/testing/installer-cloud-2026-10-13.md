# 安装与双语云端验收记录

本轮完成了受限云端执行器可运行的检查，**完整桌面安装验收仍未通过**。前端构建、28 项前端单测、30 项发行元数据测试及独立安装向导编译通过；Java 测试仍有 9 项受挂载查询错误影响，完整 Linux 安装包、浏览器回归和目标桌面流程尚未验收。

记录日期为 2026 年 10 月 13 日（Asia/Tokyo）；最终云端回执时间为 `2026-10-12T15:40:17.166200+00:00`。结果对应未发布的源码快照，不改变[本地候选验收记录](installer-ux-2026-10-12.md)或当前公开版本。

## 源码与证据核对

- 基线提交：`b1a69d244ab7337608cc302826ed55d4a707f37f`。
- 原始验收包：`output/ux-implementation/Copperbench-cloud-acceptance-20261012.zip`，3281149 字节，SHA-256 为 `e96eb12be98884f9f469e43b3330d90706f12e28ed5d29016bbf10e282d0222b`。
- ZIP 读取限制已通过无损文本载体和原始 PNG 解决，无需另行上传。云端最终回执记录实际 checkout 在补跑前后均为 50/50 源码摘要匹配。
- 本机将最终回执中 50 个唯一文件路径、大小、预期摘要和实际摘要逐项与原始 manifest 对照，确认全部一致，且基线与实际 HEAD 一致。此核对依据云端回执中的实际文件摘要，不是本机直接访问远端文件系统。
- 首次云端报告存在 35 个已有 checkout 文件未覆盖的问题，该轮测试结果已作废。保留 `cloud-acceptance-checkpoint-20261013.json` 及其失效标记；以下仅采用修正覆盖后的结果。原覆盖未完整生效的具体机制尚未确定。

## 有效结果

| 检查 | 实际结果 | 证据范围 |
| --- | --- | --- |
| 前端构建 | 通过 | 修正覆盖后的云端汇总；最终补跑未重复 |
| 前端单元测试 | 28/28 通过 | 最终补跑原始日志，退出码 0 |
| 发行元数据回归 | 30/30 通过 | 修正覆盖后的云端汇总；最终补跑未重复 |
| Java 定向测试 | 19 项：10 通过、9 失败 | 9 项失败均报告 `Mount point not found`；修正覆盖后遗漏的语言优先级用例已通过 |
| 独立安装向导编译 | 通过 | 最终补跑原始日志，退出码 0；仅向导 JAR |
| 完整 Linux 离线安装包 | 未生成 | Gradle 在产品构建任务执行前阻塞 |
| 浏览器端到端回归 | 启动阻塞 | 最后单 worker 重试首项浏览器启动失败，35 项未运行 |
| Windows 和 Ubuntu GNOME 桌面安装 | 未验证 | 缺少目标桌面会话及原生操作能力 |
| 360 扫描与复核 | 未验证 | 云端无 360；原始样本申诉尚未发送、没有复核结论 |

最终补跑命令为 `npm test --prefix ui-shell` 和 `node platform/installer/build.mjs --platform linux --version 0.1.4 --compile-only`，在云端 checkout 执行。安装向导 JAR 为 2206995 字节，SHA-256 为 `b61588d6851fcb4be8cb61152315ce3ac5b2af86a37ea0d9dca21764e3a89ad4`。它不包含完整应用负载，不能作为完整离线安装包交付。

## 环境阻碍与剩余验收

云端为 Ubuntu 24.04.3 x86_64 受限执行器。`/etc/mtab` 指向不存在的 `/proc/self/mounts`，`/proc/self/mountinfo` 也不存在；多个候选目录的 Java FileStore 探针均失败，未找到可迁移的正常挂载点。没有伪造挂载数据或绕过生产空间校验。

Playwright 1.62.1 所需 revision 1234 的 Chromium 与 headless shell 均在启动时以 `-5 / SIGTRAP` 退出。共享库检查无缺失，原生 stderr 为空；DBus Unix socket bind 和 strace ptrace 返回 `EPERM`。启用 sandbox、单 worker 重试仍失败，现有证据不能确定 SIGTRAP 的精确内部原因。

完整验收仍需具备正常挂载信息的 Linux 构建环境、Ubuntu 24.04 GNOME Wayland/Xorg 会话，以及 Windows 11 测试环境，完成安装、升级、启动、卸载和相应系统权限交互。360 原始样本复核与最终候选扫描仍单独待完成。浏览器测试或文件生命周期演练不能替代这些系统操作。

本轮未为适配云端修改生产代码，未提交、推送或发布。图标继续保留现状。

## 证据入口

下列三个历史本地 JSON 文件未包含在本次检出中；路径相对当时的仓库根目录。

- 最终原始 JSON 回执，历史路径 `output/ux-implementation/cloud-final-reverification-20261013.json`：22094 字节，SHA-256 为 `730bf79cb7e84170ae8ae5206f7e000c6a8792c2e7c4f30e0e544db6d8b1506b`，包含 50 个文件摘要和两项最终补跑的原始日志。
- 本机独立清单核对，历史路径 `output/ux-implementation/cloud-manifest-independent-check-20261013.json`；交接状态回执，历史路径 `output/ux-implementation/cloud-acceptance-receipt.json`。
- [云端交接 Page](https://chatgpt.com/space/page_9e25000785848191b950f2e8c7c9dd18)，云端任务为“验收 Copperbench 安装器与双语体验”。

`output/` 中的材料为本地证据；最终原始 JSON 同时附于交接 Page。
