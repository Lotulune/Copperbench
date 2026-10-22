# Copperbench 安全软件告警处理

## 简体中文

如果安全软件报告木马，请先保留告警信息：检测名称、命中文件、Copperbench 版本、发生步骤、检测时间、安全软件与病毒库版本，以及安装包的 SHA-256。截图中可遮盖无关个人信息。

从 [Copperbench 官方发布页](https://github.com/Lotulune/Copperbench/releases)核对版本与校验清单。哈希相同表示文件与发布包一致，不能单独证明告警属于误报。文件被隔离或安装被阻止时，保留检测结果等待复核，不需要为安装关闭安全软件或添加整个目录白名单。

360 的复核入口是[360 软件开放平台误报反馈](https://open.soft.360.cn/report.php)。反馈时附检测名称、确切样本信息和联系邮箱；提交原始命中样本，避免用另行重编译的文件替代。

已收到的 0.1.4 告警为 `HEUR/QVM20.1.AD8F.Malware.Gen`，用户反馈发生于安装时。命中包的 SHA-256 为 `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`。目前已核对其与保存的公开发布摘要一致，尚无 360 复核结论。新版安装器的版本资源和打包流程变更不能直接证明该告警已经消除。

Windows SmartScreen 的“已保护你的电脑”与安全软件给出的具体木马检测是不同类型的提示。当前 GitHub Windows 包没有 Authenticode 签名；不要把木马检测一律解释为未签名提示。

### 应用内更新时的行为防护提示

`copperbench-update-host.exe` 是 Windows 更新助手，会从本次更新的准备目录启动，参与系统授权、生成卸载程序和更新安装登记。替换程序文件是更新的必要操作；“入侵防护”“修改关键应用程序文件”等告警的具体判定仍需要检测规则或日志，不能只凭提示标题断定已经发生入侵或一定属于误报。

0.1.12 的更新路径也会重新创建桌面和开始菜单快捷方式，与已报告的“修改快捷方式”提示对应。从 0.1.13 起，应用内更新保留现有快捷方式（包括用户修改或删除的选择），并避免失败回滚时重写未变化的文件。完整安装器仍创建开始菜单入口，并按用户选择创建桌面快捷方式。[修复验证记录](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/update-helper-2026-10-19.md)覆盖代码检查和实际弹窗显示；这是减少不必要的文件操作，不是安全软件复核结论。

2026-10-19 核查的已安装 0.1.12 助手 SHA-256 为 `cf3a00e602ce7fc4805f934f3abd0d57c07cf00d22d4babeb4926158410aa210`，与公开更新清单一致，签名状态为 `NotSigned`。截图所指临时缓存文件当时已清理，因此这项核对只覆盖已安装副本。助手的文件说明也会明确其更新与卸载维护职责；文件名、图标和说明不代替签名校验。

[Authenticode](https://learn.microsoft.com/en-us/windows-hardware/drivers/install/authenticode)用于验证发布者身份和签名后代码的完整性。签名配置与 360 的具体告警复核是独立事项；当前没有 360 对这组行为提示的复核结论，也没有提交新的申诉。

## English

If security software reports malware, keep the detection name, affected file, Copperbench version, installation step, time, security software and database versions, and the package SHA-256. Redact unrelated personal information from screenshots.

Compare the version and checksum with the [official Copperbench releases](https://github.com/Lotulune/Copperbench/releases). A matching hash establishes that the file matches the published package; it does not by itself establish a false positive. If installation is blocked or a file is quarantined, keep the report for review. Installation does not require disabling security software or excluding an entire directory.

Submit 360 detections through the [360 software review form](https://open.soft.360.cn/report.php), with the exact detection, original sample details and a reply email. Do not substitute a rebuilt file for the sample that triggered the detection.

The reported 0.1.4 detection is `HEUR/QVM20.1.AD8F.Malware.Gen`, during installation. The affected package SHA-256 is `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`. It matches the recorded public release digest, but no 360 review conclusion is available. Changes to installer metadata and packaging are not proof that the detection is resolved.

Windows SmartScreen reputation prompts differ from a specific malware detection. Current GitHub Windows packages are not Authenticode signed; a malware detection must still be reviewed on its own evidence.

### Behavior-protection prompts during in-app updates

`copperbench-update-host.exe` runs from the prepared update directory and participates in elevation, uninstaller creation and installation registration. Replacing application files is necessary for an update. The precise reason for an intrusion or protected-file alert requires the vendor's rule or log; its title alone establishes neither an intrusion nor a false positive.

The 0.1.12 update path also recreated desktop and Start menu shortcuts, matching the reported shortcut-modification prompt. Starting with 0.1.13, in-app updates preserve existing shortcuts, including user modifications or deletions, and avoid rewriting unchanged integration files during rollback. The full installer still creates Start menu entries and offers the desktop shortcut choice. The [validation record](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/update-helper-2026-10-19.md) covers source checks and the displayed dialogs. This removes unnecessary writes; it is not an antivirus review result.

The installed 0.1.12 helper inspected on 2026-10-19 has SHA-256 `cf3a00e602ce7fc4805f934f3abd0d57c07cf00d22d4babeb4926158410aa210`, matching the public update inventory, with Authenticode status `NotSigned`. The temporary file in the screenshots had already been cleaned up, so this comparison covers the installed copy only. Updated version resources identify its update/uninstall maintenance role; names, icons and descriptions do not replace signature verification.

[Authenticode](https://learn.microsoft.com/en-us/windows-hardware/drivers/install/authenticode) verifies publisher identity and integrity since signing. Signing and review of the specific 360 detections are separate matters. No vendor conclusion or new appeal submission is claimed for these behavior prompts.
