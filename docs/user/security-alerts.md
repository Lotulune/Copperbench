# Copperbench 安全软件告警处理

## 简体中文

如果安全软件报告木马，请先保留告警信息：检测名称、命中文件、Copperbench 版本、发生步骤、检测时间、安全软件与病毒库版本，以及安装包的 SHA-256。截图中可遮盖无关个人信息。

从 [Copperbench 官方发布页](https://github.com/Lotulune/Copperbench/releases)核对版本与校验清单。哈希相同表示文件与发布包一致，不能单独证明告警属于误报。文件被隔离或安装被阻止时，保留检测结果等待复核，不需要为安装关闭安全软件或添加整个目录白名单。

360 的复核入口是[360 软件开放平台误报反馈](https://open.soft.360.cn/report.php)。反馈时附检测名称、确切样本信息和联系邮箱；提交原始命中样本，避免用另行重编译的文件替代。

已收到的 0.1.4 告警为 `HEUR/QVM20.1.AD8F.Malware.Gen`，用户反馈发生于安装时。命中包的 SHA-256 为 `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`。目前已核对其与保存的公开发布摘要一致，尚无 360 复核结论。新版安装器的版本资源和打包流程变更不能直接证明该告警已经消除。

Windows SmartScreen 的“已保护你的电脑”与安全软件给出的具体木马检测是不同类型的提示。当前 GitHub Windows 包没有 Authenticode 签名；不要把木马检测一律解释为未签名提示。

## English

If security software reports malware, keep the detection name, affected file, Copperbench version, installation step, time, security software and database versions, and the package SHA-256. Redact unrelated personal information from screenshots.

Compare the version and checksum with the [official Copperbench releases](https://github.com/Lotulune/Copperbench/releases). A matching hash establishes that the file matches the published package; it does not by itself establish a false positive. If installation is blocked or a file is quarantined, keep the report for review. Installation does not require disabling security software or excluding an entire directory.

Submit 360 detections through the [360 software review form](https://open.soft.360.cn/report.php), with the exact detection, original sample details and a reply email. Do not substitute a rebuilt file for the sample that triggered the detection.

The reported 0.1.4 detection is `HEUR/QVM20.1.AD8F.Malware.Gen`, during installation. The affected package SHA-256 is `33a6cbcc8e3442d4334f9a53de971ad1266dae024ee7af7de955e90bf6657633`. It matches the recorded public release digest, but no 360 review conclusion is available. Changes to installer metadata and packaging are not proof that the detection is resolved.

Windows SmartScreen reputation prompts differ from a specific malware detection. Current GitHub Windows packages are not Authenticode signed; a malware detection must still be reviewed on its own evidence.
