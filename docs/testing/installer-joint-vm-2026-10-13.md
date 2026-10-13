# 安装双语与项目加载联合虚拟机验收

本轮在本地 Windows 11 和 Ubuntu 24.04 GNOME 虚拟机中联合验收离线安装器、双语界面、PR #103 的项目加载优化及 Blockbench 修复。当前用户安装、双语弹窗、真实工作台、Blockbench、两平台系统升级与卸载均取得分阶段通过证据；验收发现的安装阻断、权限和卸载问题已修复并回查。最终产物为 Windows r14 和 Linux r12，Windows r14 采用最终包生成卸载部件的增量验证。360 静态扫描未发现威胁，厂商复核、实时防护下的安装行为和已记录的工作区关闭异常仍不在通过范围。图标保持现状；本文记录的是验收完成时的状态，当时尚未提交、推送、合并或发布。

## 候选与环境

候选代码位于 `C:/Users/Administrator/.codex/worktrees/joint-vm-acceptance/Minecraft_ModCreator`，基线为 `f5dd87ba6cf26abb0c53814c1db20656dfc3b4ad`（[PR #103](https://github.com/Lotulune/Copperbench/pull/103)）。其上叠加关联任务的 Blockbench 工作树修复和安装双语实现。本轮产品代码修复只写入这个隔离候选。

两台 Hyper-V 虚拟机依次运行，各分配 4 vCPU、4 GiB 启动内存和 6 GiB 内存上限；最后的 Windows r13/r14 安装与卸载回查将上限降到 4 GiB，关机后恢复为 6 GiB。Windows 为 Windows 11 Pro x64 build 26200；Linux 为 Ubuntu 24.04.4 LTS x86_64，分别验证 GNOME Wayland 和 Xorg。安装验收时断开客体网卡，不依赖预装的系统 Java 或 Gradle。

本地证据目录为 `D:/AICoding/Minecraft_ModCreator/output/joint-vm-acceptance`，下文证据文件名均相对此目录。VM 位于 `D:/Hyper-V/UX-Joint-20261013`；最终完整包移至隔离候选的 `output/final-artifacts`。测试凭据不纳入报告或源码补丁。

为保持宿主可用，两台 VM 依次运行。Linux 系统升级的大临时文件放在本轮新增的 16 GiB 数据盘，挂载于 `/home/stage17/Acceptance`，未写入 fstab。通过 GNOME 菜单中的测试入口启动原包脚本，仅为该进程指定临时目录；权限确认由真实 polkit 会话完成。SSH 启动无法找到桌面授权代理的失败记录保留。测试账户凭据也曾因 Windows stdin 的 CRLF 不匹配，已修复并用新的临时凭据实际授权成功；这些属于验收准备，不是产品修复。

为给虚拟磁盘留出空间，旧候选包和传输副本已按 SHA-256 核对后移至 C 盘，未删除：旧包见 `earlier-candidate-relocation.json`、`r10-linux-package-relocation.json`，传输证据见 `transfer-evidence-relocation.json` 和 `fixture-transfer-relocation.json`。原始日志中的旧路径以这些映射为准。

## 桌面验收

| 流程 | 结果与具体范围 | 主要证据 |
| --- | --- | --- |
| Windows 离线当前用户安装 | r5 通过。系统中文默认简中，可手选 English 和 Light；许可弹窗正常；安装至用户目录，无 UAC，首启继承英文 | `windows-installation-r5.json`、`windows-r5-*.png` |
| Windows 注册失败恢复 | r5 通过。以测试标记文件占用开始菜单目录，真实触发失败；程序目录、HKCU 和桌面快捷方式回滚，标记保留；移除测试标记后重试成功 | `windows-registration-rollback.json` |
| Windows 当前用户升级 | r5 升 r7 通过。保留目录、范围、语言和主题；并存的旧系统注册项不变 | `windows-registration-timing-r7.json`、`windows-installation-r7.json` |
| Windows 当前用户卸载 | r7 通过文件和注册清理。122 个源码/资源文件校验一致，工作区、英文偏好和旧系统安装保留；早期卸载语言问题已在 r14 共用确认页完成中英文回查 | `windows-r7-uninstall-verification.json`、`windows-r7-uninstall-status.png` |
| Windows 旧系统安装升级 | 官方 0.1.4 → r10 通过。保留 Program Files、system 范围和旧程序备份；真实 UAC 授权；旧 HKLM 32 位项迁移为唯一的 HKLM 64 位项，HKCU 无安装残留；122 个源码/资源文件保持一致 | `windows-r10-system-upgrade-verification.json`、`windows-r10-system-upgrade-complete.png` |
| Windows 安装器自然退出 | r10 通过。完成页启动应用后，外壳进程自然退出，剩余数 0，未用终止进程代替通过 | `windows-r10-wrapper-exit.json` |
| Windows r12 系统升级回查 | r10 → r12 通过。范围、目录、注册和 122 个源码/资源文件保持一致，英文欢迎页正常，安装器外壳自然退出 | `windows-r12-system-upgrade-verification.json`、`windows-r12-wrapper-exit.json`、`windows-r12-home-english.png` |
| Windows r13 完整系统升级 | r12 → r13 离线完整包升级通过。范围、目录、应用负载和 122 个源码/资源文件保持一致，真实 UAC 授权，完成页关闭后外壳自然退出 | `windows-r13-system-upgrade-verification.json`、`windows-r13-system-upgrade-complete.png`、`windows-r13-wrapper-exit.json` |
| Windows r14 卸载确认页 | 最终完整包的 `/REGISTER` 入口在已通过的 r13 安装上生成实际卸载程序；从普通桌面启动，UAC 后直接进入单一确认页，中英文文案完整。属于部件替换复验，没有重复整包安装 | `windows-r14-uninstaller-refresh.json`、`windows-r14-uninstall-confirm-en.png`、`windows-r14-uninstall-confirm-zh.png` |
| Windows r14 系统卸载 | 实际英文卸载完成。安装目录、HKLM 32/64 位和 HKCU 安装项、公共快捷方式全部清理，卸载进程退出；122 个源码/资源文件、工作区、中文用户偏好和三个旧程序备份保留 | `windows-r14-system-uninstall-verification.json`、`windows-r14-uninstall-final-state.json`、`windows-r14-uninstall-followup.png` |
| Windows r12 中文失败弹窗 | 通过实际偏好设置切换简中并重启，欢迎页与配置失败弹窗中文正确；1024×768 下完整显示，默认焦点为重试，关闭操作返回欢迎页 | `windows-r12-home-chinese.png`、`windows-r12-failure-dialog-chinese.png`、`windows-r12-failure-closed.png` |
| Linux 离线图形路径 | Wayland 和 Xorg 已通过实际文件管理器解压、Run as a Program、向导安装和启动；操作不要求输入终端命令 | `linux-*.png`、`installer-interaction-xorg-r5.log` |
| Linux 卸载和数据保留 | Wayland、Xorg r5 均通过。122 个源码/资源文件、工作区、两个建模副本和中文偏好保留，应用启动入口移除 | `linux-uninstall-verification.json`、`linux-xorg-r5-verification.json` |
| Linux r10 当前用户回查 | Wayland 离线图形安装、向导启动、工作台英文切换、英文 Blockbench 诊断通过；Esc 后焦点返回；英文卸载后，122 个源码/资源文件、工作区和两个建模副本保留 | `linux-r10-verification.json`、`linux-r10-blockbench-english-details.png`、`linux-r10-dialog-closed.png` |
| Linux r12 系统升级 | 官方 0.1.4 Debian 包 → r12 通过。离线、真实 polkit 授权，保留 `/opt/copperbench` 与 dpkg 所有权；普通用户成功读取 `0664 root:root` 安装记录，英文启动通过 | `linux-r12-system-upgrade.json`、`linux-r12-system-upgrade-complete.png`、`linux-r12-home.png` |
| Linux r12 系统卸载 | 从应用菜单进入产品卸载向导、英文确认与 polkit 授权后成功。dpkg 和启动入口移除，122 个源码/资源文件、工作区、两份建模副本及英文偏好保留 | `linux-r12-system-uninstall.json`、`linux-r12-uninstall-english.png` |
| Windows 英文欢迎页和原生引导 | r7 通过。实际安装版欢迎页、Blockbench 引导均显示英文，布局可用 | `windows-r7-home-english.png`、`windows-r7-startup-guide-english.png` |
| Windows 英文 Blockbench 设置 | r7 通过。检测到编辑器 5.1.6；诊断详情、状态和操作均为英文；Esc 关闭后焦点返回触发按钮 | `windows-r7-blockbench-english-details.png`、`windows-r7-dialog-closed.png` |

Windows r14 部件验证前核对最终包、r13 卸载程序和应用 JAR 摘要。实际新卸载程序 SHA-256 为 `97f50795aea4b724783146d6fc723c350f9c6640a00c6c9314ada14cf91bd976`。为查看中文确认页，专用测试 VM 的安装语言记录临时从 `en` 改为 `zh`，取消后恢复 `en` 再实际卸载；个人偏好始终为 `zh`。准备操作、恢复结果和图形操作分别记录，未把准备命令算作用户交互通过。

Linux 的 GNOME 测试用户采用自动登录，没有已解锁的默认密钥环。首次启动出现的系统密钥环创建提示已取消；这不构成密钥存储功能的通过证据。官方 Debian 基线下载摘要为 `0460c27bb26927ef8240265775b6f071a49614119043ef21c4616f285c4512d4`，与公开发行摘要匹配。r11 首次升级暴露权限问题后，重新安装这一官方包再验收 r12，没有通过手动 chmod 冒充产品修复。

Linux r10 安装选择 English，但首启继续使用已有中文用户偏好；应用内切换 English 后写入 `~/.config/copperbench/ui-language.txt`。向导和启动终端退出后，应用以独立会话继续运行，正常关闭后没有残留应用或安装器进程。客体原始回执采用 UTC，Windows 桌面及宿主显示时区不同，应按各自带时区的字段理解时间。

## 项目加载与 Blockbench

实际使用 Overlord 的隔离副本。复制时核对的 142 项由 122 个源码/资源文件和 20 个元数据/缓存/历史文件组成；后续数据保留核对覆盖前者。早期 Linux 性能原始日志把 142 写为 `sourceFilesVerified`，分类更正记录在 `performance-fixture-preparation.json`，原始日志未改写。

Windows r5/r7 安装版正常打开工作区，显示 31 个资产、106 个源码条目、14 个资源引用，源码文件可打开，历史页面可响应。Windows r5 从 Copperbench 启动真实 Blockbench 5.1.6，打开 `cash_hourglass.json` 并显示沙漏模型和贴图。Linux 还验证了建模副本的打开、保存候选和取消任务；未把这些操作扩展为模型回导通过。

完整依赖上下文的独立性能测量如下，单位为毫秒。这些是后端单次操作耗时，不是应用启动时间，也不是两平台之间的严格性能比较。

| 产物与平台 | 资产列表 | 资源关系两次 | 历史状态三次 |
| --- | --- | --- | --- |
| Linux r4 | 43.96 | 397.79 / 273.68 | 68.0 / 17.3 / 11.4 |
| Windows r5 | 80.05 | 1408.21 / 1241.52 | 109.5 / 19.3 / 15.1 |

两组均核验 134 个依赖 JAR、真实 Minecraft 原版资源目录和 `vanilla_resolved`，没有使用过期依赖快路径。只对专用性能副本重绑缓存路径和指纹，没有重新执行 Gradle 依赖解析，也没有削弱产品校验。证据为 `linux-full-catalog-benchmark.log` 和 `windows-full-catalog-benchmark.log`。后续 r7 仅改界面/安装器，这些数字没有被表述为最终包的重新测量。

本轮不涉及 Minecraft 游戏内交互验收。

## 验收发现与修复

1. 安装向导在下拉菜单事件内同步重绘导致 `IndexOutOfBoundsException`。改为事件队列延后重绘，语言/主题切换的实际桌面回归通过。
2. Linux 从向导启动应用后，随启动终端关闭而退出。使用独立会话、关闭标准输入并保留启动日志；实际启动和 PTY 回归通过。
3. Windows 注册阶段因 solid 压缩再次解压整个包。改为普通 LZMA 后，安装 receipt 到开始菜单快捷方式的时间间隔从 r5 的 62,354.7 ms 降为 r7 的 4,531.2 ms。该区间不代表总安装时间，且不是严格同条件基准。
4. Windows 系统安装迁移需要同时处理 32/64 位注册表视图。事务现在备份并在失败时恢复两者，成功后仅迁移与目标目录匹配的旧项；移除 NSIS 中重复的旧项删除。
5. Windows NSIS 外壳在向导退出后残留。r5/r7 已保存真实进程证据；新增自动关闭，r10 已实际验证自然退出。旧测试残留进程的人工清理不计入通过。
6. 英文欢迎页、原生 Blockbench 引导和工作台诊断有漏翻译，已补齐并在 r7 实际安装版确认。Windows 卸载语言显式读取安装选择，r14 中英文实际确认页通过。
7. Linux 卸载文案修正为移除应用并保留工作区，执行时禁用取消；实际 Xorg r5 卸载通过。
8. Gradle 网络错误提示不再建议关闭杀毒软件，改为检查网络、代理、镜像并保留检测信息和日志。
9. Windows r9 在真实系统升级中出现进度文件替换的 `AccessDeniedException`。失败后旧程序、HKLM 32 位注册和 122 个源码/资源文件均保留。r10 对 Windows 短暂文件锁最多重试 10 次、间隔 100 ms；永久失败仍报告错误，保留旧完整进度并清理临时文件。修复前失败、修复后通过的原始日志分别为 `installer-progress-before.log` 和 `installer-progress-after.log`；r9 失败和取消授权证据保留为 `windows-r9-system-upgrade-failure.json`、`windows-r9-uac-cancel-verification.json`。
10. 工作区配置失败弹窗在 Windows 1024×768 下因五个横排长按钮超出屏幕，英文模式还混有中文。r11 将短文案换行、长详情滚动、操作纵排，并在 EDT 显示，默认焦点保留“重试”。Linux 实际英文失败界面完整可见，Esc 返回欢迎页；Windows r12 实际中文失败弹窗也通过。见 `linux-r11-failure-dialog-en.png`、`linux-r11-failure-escape.png`、`windows-r12-failure-dialog-chinese.png`。此失败来自离线且旧缓存引用已移除的用户安装路径，不能算正常工作区打开通过。
11. Linux r11 系统升级原子替换安装记录时将权限变成 `0600 root:root`，普通用户无法读取。r12 在替换已有 POSIX 文件时保留原权限，新建私有请求/进度文件仍保持私有。权限回归修复前失败、后通过，且真实普通用户升级、启动和卸载通过；见 `linux-receipt-permissions-before.log`、`linux-receipt-permissions-after.log`。
12. Windows r12 系统卸载从普通桌面启动，在确认 UAC 后直接退出，原程序、注册和 122 个源码/资源文件未变化。NSIS 提权时重新执行临时 `Un.exe`，丢失真实安装目录；r13 改为提权启动安装目录中的 `uninstall.exe`。修复前证据为 `windows-r12-uninstall-failure.json`；r13 完整包实际升级后成功进入确认页，r14 最终卸载部件进一步完成实际卸载与数据保留核对。该问题仅影响 Windows，Linux r12 结果和产物不变。
13. r13 提权成功后，预先弹出的提醒仍按系统语言显示中文，后续确认页则正确为英文。r14 将关闭应用、保留工作区的提醒合并进原有确认页，在 NSIS 完成语言初始化后显示，去掉重复弹窗。证据为 `windows-r13-uninstall-language-observation.json`、`windows-r13-uninstall-confirmation.png`；r14 从最终完整包刷新实际卸载程序后，中英文页面及真实卸载均通过，见 `windows-r14-acceptance-summary.json`。该增量验证复用摘要未变化的 r13 应用负载，不代表又一次完整 r14 安装。

## 自动化检查

初始联合候选的 UI 单测 30/30、Linux 元数据文件 6/6、Java 定向检查 48 项通过且 1 项 installed Blockbench 检查跳过、Playwright 44/44、生产 UI/i18n 构建通过。

r7 负载重建后，安装事务、原生 Blockbench 引导和语言偏好 Java 检查 16/16 通过。定向浏览器运行最初 26 通过、2 失败，失败原因是新增测试在语言切换后仍定位英文对话框；修正定位后这 2 项通过。原始失败日志保留为 `r7-dialog-regression.log`，补跑为 `r7-dialog-locale-regression.log`。两次合起来覆盖 28 个相关检查，未冒称一次全绿运行。

Windows r10 新增 `InstallerProgressFileTest`，用真实拒绝删除共享的文件句柄验证短锁恢复、永久锁有界失败、旧值保留和临时文件清理，修复后通过。Windows、Linux r10 完整包均成功构建；应用 JAR 与 r7 一致，SHA-256 为 `c57bb3002b3b357e3cc33af0f68d975f11d9480996619f583f473873ad0a2ab6`。

r11 弹窗修复后的生产 UI/i18n 构建、Java 编译和 Windows/Linux 负载导出成功，见 `r11-payload-build.log`。两平台应用 JAR SHA-256 为 `54821b6b5bcca02526f49825b2dc91e1961d20c518f7bde40a2c9765db93ad86`。r12 仅修改独立安装器，复用该负载；POSIX 权限回归和 Windows 文件锁回归均通过，后者见 `windows-r12-progress-test.log`。两平台 r12 完整包构建成功；后续 Windows r13/r14 完整包也构建成功，应用负载清单摘要与 r12 相同。最终 Markdown 链接检查通过；源码差异检查保留两条诊断前缀所需的尾部空格，其余无空白问题，见 `final-source-review.json`。

## 安全告警与交付边界

原始检测为 `HEUR/QVM20.1.AD8F.Malware.Gen`，对应官方 0.1.4 原包 SHA-256 `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`。用户已授权复核，但官方大包入口要求账号，邮件草稿尚未发送，没有厂商复核结论。

宿主 360 安全卫士极速版 15.0.3.1007 完成分阶段扫描，并按摘要关联未变化的内容：

- r10 EXE 外层扫描：1 个文件、0 个压缩包内容、0 个威胁。界面总计显示 2 项，包括同目录 manifest；详细回执记录扫描文件数为 1。证据为 `360-r10-exe-scan-receipt.txt` 与对应截图。
- 展开后的 r10 程序负载和安装器 JAR：扫描文件数 65,323、压缩包 12、安全文件数 34,262、0 个威胁，用时 5 分 26 秒。这些是 360 的统计字段，不能等同于安装包清单条目数。复制前核验 3,188 个程序文件与最终负载清单一致；证据为 `windows-r10-payload-scan-preparation.json`、`360-r10-payload-scan-receipt.txt` 与对应截图。
- r11 最终 EXE 和变化的 3 个程序文件：扫描文件数 18,581、压缩包 1、安全文件数 9,293、0 个威胁。另 3,185 个程序文件与已扫描 r10 内容逐项一致，安装器 JAR 解压后的内容也与 r10 一致；见 `360-r11-verification.json`。
- r12 最终 EXE 与新安装器 JAR：外层扫描文件数 2、压缩包 0、0 个威胁，因此另将安装器的 20 个文件解压后扫描。补扫文件数 22、安全文件数 22、压缩包 0、0 个威胁；界面显示 23 项，包含目录中的 manifest。r12 应用负载清单摘要与 r11 相同。证据为 `windows-r12-scan-preparation.json`、`360-r12-outer-scan-receipt.txt`、`360-r12-expanded-scan-receipt.txt` 和 `360-r12-verification.json`。

- r13 EXE、安装器 JAR 和实际系统安装生成的卸载程序：扫描文件数 4、安全文件数 4、压缩包 0、威胁 0；界面显示 5 项。应用负载清单和安装器 JAR 解压条目分别与已扫描 r12 一致，见 `360-r13-verification.json`。
- 最终 r14 EXE、安装器 JAR 和最终包实际生成的卸载程序：扫描文件数 4、安全文件数 4、压缩包 0、威胁 0，用时 1 秒；界面显示 5 项。应用负载清单及 JAR 解压条目与已扫描 r12 一致。证据为 `360-r14-scan-result.jpg`、`360-r14-scan-details.jpg`、`360-r14-scan-receipt.txt`、`windows-r14-scan-preparation.json`、`360-r14-verification.json`。

上述均为自定义极速扫描，回执列出云查杀本地库、启发式、QEX、QVM II 和鲲鹏引擎，未给出独立的病毒库版本号。扫描副本位于 `D:/Hyper-V` 或 `C:/Copperbench-VM-acceptance`，未命中本机现有仓库目录排除；候选 MD5 与复制回执中的既有哈希白名单不匹配。本轮没有关闭防护或新增白名单。扫描主页曾显示“未开启主动防御服务”，因此结果只作为文件扫描证据，不作为实时防护开启或安装行为通过的证据。完整原始白名单仅保留本地，公开报告及摘录不包含其无关个人路径。

Windows 测试客体中 Defender 开启；读取不到检测记录不能等同于扫描通过，也不能代替 360。图标、付费签名和公开发布未纳入本次变更。

## 最终离线包

完整包位于 `C:/Users/Administrator/.codex/worktrees/joint-vm-acceptance/Minecraft_ModCreator/output/final-artifacts`，均未公开发布：

| 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.4 Windows 64bit r14.exe` | 734,208,536 | `8df8bae3e81f95a5a23157521fb368ba916ff6ebea85095ccfdc067b6412ac01` |
| `Copperbench 0.1.4 Linux x86_64 Setup r12.tar.gz` | 971,117,624 | `bc6fac2ea519c1399144250e9b2eeeebfc25c2cb2a952e05214c8d0aefe45f10` |

构建清单为 `windows-candidate-r14.json`、`linux-candidate-r12.json`，下载与客体传输均核对实际 SHA-256。Windows r13/r14 仅调整卸载提权和确认文案时序，Linux 完整包仍为 r12。最终包哈希不代表全部历史流程都在同一修订上重跑，具体修订与范围见上表。

最终源码归档为证据目录中的 `joint-candidate-r14.patch`，包含相对基线的已跟踪和未跟踪源码变更；`candidate-source-manifest.json` 记录逐文件摘要，`candidate-archive-receipt.json` 记录补丁摘要与应用检查结果。使用临时 Git index 检查补丁可应用到 `f5dd87ba`，未修改真实 index，归档时未提交代码。

验收结束后两台 VM 均为 `Off`，分配内存为 0；宿主可用内存当时为 5.10 GiB，见 `final-vm-state.json`。已核验并删除两份未挂载的 r11/r12 临时传输 ISO，原包及证据保留，见 `obsolete-transfer-iso-cleanup.json`。本轮启动准备时重复挂载的 r14 介质已清为仅一个 DVD；测试登录输入重试和第一次读取到的卸载进行中快照不计入最终通过证据。

## 剩余边界

- 360 原始样本的官方复核尚未提交；本地静态扫描不能替代厂商结论和带实时防护的安装行为复测。
- 失败工作区关闭后，异步预加载回调仍可能记录 `ClosedWatchServiceException`；实际界面能返回欢迎页。该已有生命周期问题未在本轮修复，见 `windows-r10-post-upgrade-startup.log`、`linux-r11-observation.log`。
- 资源关系与历史性能测量对应前述 r4/r5；模型回导、Minecraft 游戏内交互和 GNOME 密钥环未验收。
- 自动审批拒绝了删除本轮 `windows-r10-payload-scan` 临时副本的递归清理，只返回“策略阻止”。该副本保留，未换工具绕过；不影响安装验收和源码交付。
