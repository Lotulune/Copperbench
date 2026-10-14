# 安装与双语体验验收记录

2026 年 10 月 12 日的本地候选已包含统一图形安装向导、简中与英文偏好衔接、原生与网页弹窗修复，以及安全告警处理指南。Windows 完整安装包已构建成功；Windows 系统注册和 UAC 全流程、Ubuntu GNOME 实机安装与 360 复测尚未验收。此记录不改变当前公开发行版或发布授权。

后续实际桌面检查、验收中发现的问题及修复结果见[本地联合虚拟机验收](installer-joint-vm-2026-10-13.md)；本页保留初始候选的历史状态。

## 实现范围

- Windows 11 x64 与 Ubuntu 24.04 LTS x86_64 共用独立 Swing 向导，提供中英、深浅主题、许可、安装位置、进度、取消与完成启动。保留现有图标，采用铜色控件与方块世界风景。
- 离线包自带 Java；安装向导不启动工作台、JCEF、Python 或 MCP。新装默认当前用户，升级保留已有目录和安装范围。
- 应用语言按已保存偏好、安装选择、系统语言的顺序读取。语言广播保留编辑中的草稿；原生提示、恢复提示和关闭确认采用双语按钮与可换行内容。
- 冲突弹窗提供继续编辑、查看最新与失败重试；读取失败不会显示为刷新成功，也不会重置模拟工作区和新建元素。
- 安装引擎校验逐文件摘要与收据，暂存后替换；直接升级保留旧目录，注册失败尝试恢复。卸载按清单移除程序并保留用户文件。用户放入安装目录的工作区会阻止升级，随包示例不会被误判为用户工作区。

实现与恢复边界见 [安装器说明](../../platform/installer/README.md)，用户操作见 [图形安装指南](../user/graphical-installation.md)和[安全告警处理](../user/security-alerts.md)。

## Windows 构建产物

产物为本地候选，虽然产品版本仍为 0.1.4，但字节内容与已发布、被 360 报告的原始样本不同，不能互相代替复核或验收。

| 项目 | 结果 |
| --- | --- |
| 文件 | `build/export/Copperbench 0.1.4 Windows 64bit.exe` |
| 大小 | 730368791 字节 |
| SHA-256 | `af42d29f9bec8d4c8f98069646fd4bc1dfd2b5bd482e0c1b387844f398aa187f` |
| PE FileVersion | `0.1.4.0` |
| PE ProductVersion | `0.1.4` |
| 负载清单条目 | 3435 |
| 负载清单 SHA-256 | `a0ca06d73440ec4ea381d6d555a3bb07e8f55e333279375c8b0674b22c86bd52` |
| 构建结果 | `buildInstallerWin64` 成功，14 分 23 秒 |

构建回执为上述文件旁的 `.build.json`，完整日志位于 `output/ux-implementation/final-installer-build.log`。实际文件摘要与回执一致。NSIS 3.12 官方 ZIP 的固定摘要及下载校验写入构建脚本；默认新安装器不使用旧 LockedList 插件。

## 已验证

| 检查 | 实际结果与边界 | 本地证据 |
| --- | --- | --- |
| Java 定向回归 | 19 项通过：安装引擎 12、语言偏好 3、浏览器恢复 4；0 失败、0 跳过 | `build/test-results/test/TEST-dev.copperbench.*.xml`，`output/ux-implementation/final-java-export.log` |
| 双语弹窗与 JCEF 桥 | 最终 18 项通过：语言匹配和显式偏好、广播保留草稿、冲突操作、刷新失败及投影读取失败等 | `output/ux-implementation/bridge-dialog-tests.log` |
| 前端构建 | TypeScript、词条检查与 Vite 通过，最终 Windows 导出包含其产物 | `output/ux-implementation/final-java-export.log`，`final-installer-build.log` |
| 完整 Windows 负载演练 | 文件安装、升级、卸载与用户数据保留通过，耗时 125.10 秒；未注册系统、未启动应用 | `output/ux-implementation/full-payload-rehearsal-fixed/result.json` |
| 最终 EXE 辅助模式 | `/S` 返回 2；用户与系统 `/LOCATIONS` 均返回 0 并写出正确的开始菜单和桌面路径；未执行注册 | `output/ux-implementation/nsis-helper-checks/result.json` |
| 安装器视觉 | Swing 实渲染中英、深浅四张图；中文深色与英文浅色已检查，无文字截断 | `output/ux-implementation/installer-{zh,en}-{dark,light}.png` |
| 冲突弹窗视觉 | 中英截图已生成 | `output/ux-implementation/conflict-{zh,en}.png` |

`final-java-export.log` 中 Java 测试通过后，首次导出曾因重复用户文档失败；修复打包 include 后，最终完整构建通过。完整负载演练也曾发现随包 `.mcreator` 示例被误判的问题，修复后通过；失败日志保留，不计为成功证据。

## 尚未验收的范围

- Windows 干净测试账号中的完整图形安装、启动、升级、系统权限请求、系统卸载与注册回滚。文件引擎演练和 EXE 辅助模式不能代替这些步骤。
- Ubuntu 24.04 GNOME Wayland / Xorg 的完整安装、启动、升级和卸载。本机没有可用的目标 VM 或 WSL；Linux 构建、执行权限与 Xvfb 预览检查已接入 CI，但此次未运行该 CI。
- GNOME 从归档提取后实际出现的“作为程序运行”菜单与权限交互。启动脚本可通过鼠标操作，GNOME 可能显示终端窗口；不承诺直接双击运行。
- Linux 旧 Debian 系统包升级使用 `pkexec dpkg --install`，保留包管理器归属；当前未实现旧 Debian 包自动回滚。
- 最终候选包的 360 扫描与原始包的人工复核。版本资源修复及更换安装流程不证明检测已经消除。

## 360 复核状态

用户报告在安装已发布 Windows 0.1.4 包时出现 `HEUR/QVM20.1.AD8F.Malware.Gen`。原始包为 722632845 字节，SHA-256 为 `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`，与保存的官方发布摘要一致。该核对只确认样本一致性。

用户已授权提交。尚未取得提交回执：官方匿名表单要求图片与短信验证码，文件上限为 512M；支持大包链接的入口要求登录，用户没有 360 账号。[360 开放平台首页](https://open.soft.360.cn/)列出的申诉邮箱为 `opensoft@360.cn`。已整理包含原始下载链接、告警文字和校验值的邮件；本次没有可用邮箱发送连接，邮件等待用户发送。

原始取证、提交状态与待发送邮件保存在 `output/security-review/360-20261012/`。联系邮箱仅保存在本地提交材料中，不写入仓库文档。
