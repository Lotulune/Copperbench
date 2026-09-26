# Stage 17：连接恢复与运行复验（2026-09-22）

本轮继续[前次验收清单](stage-17-closeout-2026-09-21.md)。SSH 阻断已解除；Stage 17 整体仍未关闭，不以连接成功或单轨通过替代全部门禁。

## Ubuntu SSH 恢复

宿主 Default Switch 当前为 `172.27.32.0/20`，网关 `172.27.32.1`；Ubuntu 控制台仍显示 `172.20.86.21/20`，默认路由仍经 `172.20.80.1`。来宾网卡为 `eth0`、MAC `00:15:5d:00:5f:04`，NetworkManager 连接 `netplan-eth0` 的 IPv4 方法为 `auto`，SSH 服务 active。此地址与路由不匹配足以解释旧 IPv4 不通，且与用户所述 WinNAT 重启一致；未将其扩大为所有网络故障的共同原因。

通过已观察的 IPv6 本地链路地址 `fe80::215:5dff:fe00:5f04%37` 建立 SSH，继续使用原 `known_hosts` 和 `HostKeyAlias=172.20.86.21`，保持 `StrictHostKeyChecking=yes`。原 ED25519 指纹为 `SHA256:BGlLzkcr0B+9mDjoom3r55YJkiTdXq6ImHtlamAQo7M`，没有接受新主机密钥。

仅在来宾执行 `sudo -n nmcli connection up id netplan-eth0 ifname eth0`，取得新地址 `172.27.34.20/20` 和正确网关。随后独立 IPv4 SSH、SSH 服务、DNS 和 Mojang 版本清单 HTTPS HEAD（200）均通过。没有重启 VM、WinNAT/HNS，没有改 Windows 防火墙、路由、DNS 或全局代理，也没有改来宾网络的持久配置。

只读辅助工具：[Get-Stage17LinuxConnection.ps1](../../scripts/Get-Stage17LinuxConnection.ps1)。它从当前 Hyper-V 网卡和宿主接口计算本测试机的 EUI-64 IPv6 地址，通过固定主机密钥读取 IPv4 并实连验证，不扫描网段或自动修改配置。已在本机实测通过；其他 IPv6 地址生成方式或多网卡情况会明确停止，不能泛化为任意 Linux VM 的自动发现。

```powershell
pwsh -NoProfile -File scripts/Get-Stage17LinuxConnection.ps1
```

原始证据：`.tmp/stage17-network-20260922/guest-before.txt`、`guest-after.txt`、`ipv4-validation.txt`、`connection.json`。`curl` 未安装的尝试没有记为出站验证通过，最终 HTTPS 检查使用 Python 标准库。

## v16 安装与原生读取

v16 主 JAR SHA-256 为 `4f87a1c1d9181fec3f4de3831ac2286ed34a74d688ed1e8c472c652c4edb007e`。Ubuntu 安装归档经过哈希核对，本地开发 deb SHA-256 为 `6780d94b9817db8c415b064078b8d38e56286c996d5126e1cbec67d9894e304b`，不冒充 CI 发行认证。模型、PNG 和旧 Mod JAR 升级前后哈希相同。

安装后的公开 SDK 故障矩阵 11 项通过。通过真实 GNOME 终端启动 `/usr/bin/copperbench`，产品 PID 43898，Wayland 图形探针由既有 verifier 核验通过。旧建模任务 `cb9f033d-0c2a-44b9-9dee-765e1e995ae6` 仍 imported，目标元素 consistent，模型关联 `structured_forge:custom/resonance_forge` 未变。

在来宾显示器 1280×720 和 1920×1080 下观察中文总览：错误卡、健康诊断及状态栏均显示 0 错误。保留原生截图；这仅覆盖零错误场景，不是六夹具、英文或 Windows 150% 缩放矩阵。临时显示模式已恢复原 1024×768。[截图脚本](../../scripts/Get-Stage17VmScreen.ps1)新增可选宽高及缓冲长度校验，实际生成并检查了完整 1920×1080 来宾截图，避免将受宿主屏幕裁切的窗口图冒作全屏证据。

实际向来宾产品发送 Alt+F4 后，04:11:54Z 日志记录 `Exiting Copperbench`，PID 消失。证据目录为 `D:/Hyper-V/Stage17-Linux/guest-evidence-v16`；截图位于其 `screenshots/`。本轮没有重新完成整个建模往返或游戏客户端交互。

## NeoForge 26.1.2 正式通过

在新的 root 复验副本中保留独立评估原六项断言及 `minimumTests=6`，不覆盖旧失败目录。本轮为主 Agent 复验，不能记成新增独立 Agent 会话。

| 启动方式 | 产品任务 | 结果 |
|---|---|---|
| v16 随包 Java + 公开 SDK | `7fcdad33-88ed-4895-987c-5fe3b8655d60` | 六项行为与一项框架自检通过；进程退出 0，产品 succeeded |
| v16 `copperbench.exe` + 随包 SDK | `ebee8945-a085-4e7e-bc90-a19b1809483c` | 同六项行为与框架自检通过；进程退出 0，产品 succeeded |

两轮 Mod JAR SHA-256 均为 `a3e1c2c4bea5ffe4144f6b77199ab60614fbd952c2b6180f8057009cef126027`。EXE 轮 XML SHA-256 为 `ee0dcd528617c2a75b81727324d8eccc705ccb2e2a3e98bfd77b7a4ed7da3b6c`。测试与配置哈希前后相同。证据目录：`D:/Hyper-V/Stage17-Linux/root-eval-v16/neoforge-26.1.2-java-launcher` 和 `neoforge-26.1.2-exe-verified`。

两轮正常复用缓存，并仅对测试进程树设置现有 `127.0.0.1:3067` 代理及本机绕过；不代表默认网络、空缓存或冷启动通过。

### 两次准备失败的分类

初次 EXE 启动在 `JAVA_TOOL_OPTIONS` 中含 `-Dhttp.nonProxyHosts="localhost|127.*|[::1]"` 时异常退出，没有进入正式任务。独立只读矩阵复现：普通属性、代理地址、无内嵌引号的绕过列表正常；带内嵌引号时退出 `3221225477`（`0xC0000005`），尚未定位到原生启动器内部根因。证据 `.tmp/stage17-launcher-option-matrix.json`；未修改全局环境来规避。

去掉内嵌引号后的第一次准备因复验脚本将 EXE 运行来源哈希误与外置 JAR 比较而中止，尚未提交 GameTest。这是测试脚本预期错误。`ApplicationBuildIdentity` 正确报告运行类来源：包装 EXE 的 SHA-256 为 `c393520c51ae8f270c3444d5fa28b773b951d4f0546e6ae6d75053420444928e`；外置 JAR 为 v16 主 JAR 哈希。两归档 2216 个文件内容逐一相同，证明见 `.tmp/stage17-v16-wrapped-jar-proof.json`。修正脚本的身份边界后才获得上述 EXE 正式通过结果。两次失败材料均保留。

## NeoForge 1.20.1：v16 超时与 v17 修复

按用户明确指示，通过 Ubuntu 产品的正式任务授权表单签发新授权。目录限定 `/home/stage17/independent-eval/fields/neoforge1201-v14`，权限仅 `edit,build,test,run_server`，有效期 2 小时，保留用户已明确接受的 EULA 条件。公开列举核验 active、范围与期限；未手工构造签名记录或自行填写绕过批准字段。v17 复验在有效期内完成；授权已于 05:28:29Z 到期。

v16 正式任务 `fbac838f-33dd-4258-9aca-55314e21d42e` 在构建成功后，隔离测试宿主于 03:34:05Z–03:54:06Z 达到原 20 分钟预算，退出 124，六断言 0 执行。停在 `neoFormJoined1.20.1-20230612.114412DownloadAssets`。源快照仍为 `c77b0f9627f9c2432fbf658e154ea6153f4984b07e9158dd8ce90eff81d04cf5`；JAR 为 `bc29a0ad2acf8a940f311a8e9ab9ebb2cc984348e4d05f082e486f50548b040d`。旧 v14 与本次 v16 超时均未改为通过。

此时索引预期 3575 个对象，大小匹配仅 669 个，缺少 510537406 字节；不能把上一轮准备完成推出新宿主必然复用全部下载。实际 NeoGradle `common-7.0.165.jar` 的 `DownloadAssets` / `CachedExecutionBuilder` 字节码显示：资源对象目录作为缓存阶段输出；即使缓存 disabled，也会经过 `executeStage → prepareWorkspace → cleanDirectory`。当前宿主原本已设置 `net.neoforged.gradle.caching.enabled=false`，该设置不足以避免清理。分析材料 `.tmp/stage17-download-assets-bytecode.txt`、`.tmp/stage17-ng-cache-bytecode.txt`。

修复限定在 [GameTestSupport.hostBuild](../../src/main/java/dev/copperbench/generator/GameTestSupport.java)：仅新建的 NeoForge 1.20.1 专用服务端测试宿主禁用客户端 `DownloadAssets` 任务。保留原编译、服务端依赖、GameTest、六断言、报告和 20 分钟预算；不改用户工程客户端任务或第三方插件文件。现有流程/报告/目录隔离回归共 21 项通过，无跳过，证据 `.tmp/stage17-legacy-server-host-regression.log`、`.tmp/stage17-legacy-server-host-xml/`。

v17 冻结 27085 个源文件，Windows 导出及 Linux deb 根目录构建成功；22 份随包文件、24 条本地链接、640 个 Schema 引用检查通过。主 JAR SHA-256：`83d59b7178ab870fb771638b3b6435a61f90d7d82d39acd8fc25536100968ef3`。Ubuntu 本地开发 deb SHA-256：`904f7cd7e3c46cfd80593a4dc7c9123f6712b569ae6165f5dd66b537a86978f7`，已安装，原模型/PNG/JAR 哈希仍不变。

v17 正式任务 `49943606-290d-4182-a5c7-30a4b5532748` 于 04:20:05Z–04:29:46Z 完成，产品 `succeeded`、进程退出 0、六项验收全部通过、框架自检 0、跳过 0。六项覆盖硬度/抗爆性、碰撞高度、库存及堆叠上限、掉落、四向放置、库存序列化；序列化不等于真实玩家存档重进。测试文件 SHA-256 `bd90951d85e924e6bddd75bc7ee72ad4bb58c322d61600c725abd6060d3ac720`、源快照和 `minimumTests=6` 保持原值。

- 已测 JAR SHA-256：`b394a79841f41b53d231fd7006ae88fa9bc9de5b9d7736a2c5aa2c51cf00fb4b`。
- XML SHA-256：`fce65e1bb4196dc5c594994b35e692831ecb6cea7bb55a7709c3a3c45fdea180`。
- 客户端缓存前后均为 670 文件、140178509 字节，内容清单 SHA-256 均为 `89638e0fd1dca7c2e09a4acce89eb8b278ae0c1239035e364eed1bdd814899da`，没有再次清理或下载客户端资源。

关闭并重开公开 SDK 会话后，原任务仍为 `succeeded`。`export_verified_artifact` 导出成功，元数据 `passed_current_input`，导出的 JAR/XML 独立哈希复核与上述记录一致。证据位于 `/home/stage17/evidence/v17/neoforge1201-rerun`，宿主归档 `D:/Hyper-V/Stage17-Linux/guest-evidence-v17`；可信导出副本在其 `d37e6b6e-3c5d-4d3b-8b4f-caaab11bb7bd/`。

## v17 原生启动、退出与清理

通过真实 GNOME 终端启动已安装 v17，PID 48992；Wayland JCEF/MCP 探针核验通过。旧任务仍 imported、元素配置 consistent、模型关联和原资源哈希不变。1024×768 原生截图 `native-startup.png` 观察到总览、健康和状态栏错误数均为 0。向当前来宾产品发送 Alt+F4，05:43:34Z 日志记录 `Exiting Copperbench`，PID 已消失。这是启动/读取/正常退出复验，不计为新的连续建模或客户端玩法闭环。

测试结束后仅停止本轮创建的反向 SSH 代理进程 33456，核对创建时间、目标与转发参数后清理。05:44:37Z 再次独立核验普通 IPv4 SSH 成功。临时显示模式已恢复，不改全局代理或网络配置；证据 `.tmp/stage17-network-20260922/tunnel-closed.json`。

## 仍开放的门禁

两条 NeoForge 正式行为阻断已分别由 v16 26.1.2 与 v17 1.20.1 复验关闭；结合既有六轨报告，八轨各有成功行为证据，但不是同一 v17 候选八轨全部复验，也不是新增独立 Agent 评估。G17-A/B/C/D 仍保持开放：完整字段及嵌套合同审计、安装候选旧工程恢复反例、六夹具语言/缩放/JCEF 矩阵、真实社区插件兼容、同源候选两平台建模/客户端闭环及独立试作收尾仍待完成。旧失败报告、进程代理和缓存条件均保留。

## 后续源码：掉落表嵌套合同

该修复晚于 v17 冻结，不归入上述 v17 安装或 GameTest 证明。真实工作区新反例确认：`weight=1.0000000000000001` 和 `maxCount<minCount` 原先均 `committed`。初始两项失败及 XML 保留在 `.tmp/stage17-loot-contract-red-runtime.log`、`.tmp/stage17-loot-contract-red-xml/`。首次测试编译缺少 IOException 声明另记 `.tmp/stage17-loot-contract-red.log`，不计为产品缺陷。

[LootTableFieldContract](../../src/main/java/dev/copperbench/core/application/LootTableFieldContract.java) 增加数组/对象结构、严格整数/布尔/字符串、范围、枚举、最小最大关系及未知键检查，环境查询提供 `fieldContracts.loottable`。create/update/计划的真实写入边界共用该校验，并在路径预估构造定义之前检查，避免无效列表转成泛化持久化失败。既有默认值保留，未放宽用例或制造成功元数据。

旧上游掉落表读取时只转换池字段的存储拼写，不写文件；更新无关描述后原 min/max 仍保留。含未知池/条目源字段的工程拒绝结构化重写并定位 `FIELD_PRESERVATION_REQUIRES_REVIEW`，原始字节和 revision 均不变。原因是既有 JSON 合并器不能为没有稳定身份的数组对象安全匹配第三方字段；未修改全局合并策略，也未猜测未知字段含义。第一次旧工程测试重开使用了错误的随机 workspace ID，失败保留在 `.tmp/stage17-loot-import-red.log`，随后修正测试会话身份，不将此失败归因产品。

八轨均完成合法输入持久化、实际生成 JSON 数值核对、无效更新与计划拒绝、关闭重开。另有 12 组无效输入，以及两项旧工程保存边界。最终掉落表 11 项、状态投影 1 项、持久化基础 6 项均通过，无跳过；证据 `.tmp/stage17-loot-import-green.log`、同名 `-summary.json` 和 `-xml/`。这些是源码侧字段/生成回归，不是新的八轨游戏行为或安装候选认证。完整其他元素类型、引用和上下文依赖合同仍未全部审计。

最终相关回归扩大到字段、计划、Code 保留、应用服务、投影和持久化共 86 项，全部通过且无跳过；Schema 28 项通过。证据 `.tmp/stage17-loot-final-regression.log`、同名 `-summary.json` / `-xml/` 与 `.tmp/stage17-loot-schema.log`。

## v18 候选与双平台公开接口复验

掉落表修复冻结为 `D:/Hyper-V/Stage17-Linux/source-candidate-v18`，27089 个源文件；Windows 导出及 Linux deb 根目录构建成功（1m16s）。22 份随包文件、24 条本地文档链接、640 个 Schema 引用检查通过，冻结源文件无变化；证明 `D:/Hyper-V/Stage17-Linux/candidate-v18-proof.json`。两平台主 JAR SHA-256 均为 `729496062acf40ae300291b86cceaf2e348921e98e651e198b12801ea84c3e45`。

Windows 导出目录的 `copperbench.exe` 和 Ubuntu 安装后的 `/usr/bin/copperbench` 分别使用各自随包 Python SDK，在从随包 Wayfinder 示例复制的专用工作区复验：公开发现掉落合同；五类非法创建、无效更新、无效计划共七次准确拒绝且 revision 不变；合法值实际存入定义，关闭重开仍为 minRolls=7、maxRolls=9、weight=100、maxCount=4。没有操作原用户 Mod 工作区。证据 `.tmp/stage17-v18-sdk-loot.json` 与 `D:/Hyper-V/Stage17-Linux/guest-evidence-v18/sdk-loot-proof.json`。

Ubuntu v18 本地开发 deb 于 06:15:52Z 安装，包 SHA-256 `6a9e884c5850348a59233250a8b133ba16ca71f0eaea514d161e236d94602483`，原模型/贴图/旧 JAR 哈希仍不变。真实 GNOME 原生启动 PID 50409，Wayland JCEF/MCP 核验通过，旧建模任务 imported、配置 consistent；实际截图保留在 `guest-evidence-v18/native-startup.png`。来宾产品 Alt+F4 后，06:17:37Z 记录 `Exiting Copperbench`，PID 消失。完整证据已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v18`。Windows 本次仍为导出包入口验证，不是安装器认证；v18 未重新执行全部游戏行为或连续建模，不将 v16/v17 结果改写为 v18 通过。

## 后续源码：函数正文合同与兼容字段更新

真实工作区反例确认，`initialValues.commands="say lost"` 原先返回 `committed`，实际写入默认函数正文。失败日志及 XML 保留于 `.tmp/stage17-function-contract-red.log` / `.xml`。

新增 [FunctionFieldContract](../../src/main/java/dev/copperbench/core/application/FunctionFieldContract.java)：`commands` 必须为字符串数组，`code` 必须为字符串，拒绝数值、布尔和 null 的静默转换；两种正文同时存在但不等价时定位 `FIELD_ALIAS_CONFLICT`。创建、更新和计划使用共同写入边界；环境查询新增 `fieldContracts.function`，公开文档说明数组换行、空正文和冲突处理规则。此校验不判断游戏命令语法或执行结果。

第一轮八轨更新用例还暴露出创建后的顶层兼容副本与 `fields.commands` 修改冲突，8 项失败证据保留于 `.tmp/stage17-function-contract-green.log` 和 `.tmp/stage17-function-contract-initial.xml`（历史文件名不代表通过）。现函数的更新及预览按实际修改路径移除旧拼写，显式提供的矛盾值仍拒绝；没有削弱原断言。

最终 62 项 Java 回归全部通过、无跳过：映射 19、掉落表 11、应用服务 24、Stage67 8。八轨均核对合法创建、更新、真实 `.mcfunction` 生成及关闭重开；非法更新和计划保留原定义字节/revision。另覆盖单一 `code` 原文、空命令数组及八类非法输入。Schema 28 项通过。证据 `.tmp/stage17-function-contract-final.log`、同名 `-summary.json` / `-xml/`，以及 `.tmp/stage17-function-schema.log`。

这些改动晚于 v18 冻结，属于源码回归，尚未进入新候选包；不改变 v18 安装/游戏结论，也不代表其他所有元素嵌套合同已审计完毕。

## Windows v18 客户端准备与 Minecraft Control 阻断

本次实际调用 `mc_doctor` 成功，随后 `mc_list_windows` 返回空列表。确认旧客户端进程已不在后，用 v18 导出包 `copperbench.exe` 和随包 Python SDK 打开原测试工程 `D:/Hyper-V/Stage17-Linux/independent-eval/model/workspace/structured_forge.mcreator`。任务 `42c25206-7b6e-483d-acaa-2f8ad65dd77e` 于 10:51:03Z 受理；Gradle 初始化有网络等待，后续正常推进。10:53:00Z 新客户端 PID 35836 启动，命令行明确指向该测试工程的 Loom 配置。公开任务到达 `running / progress=0.7 / task.run_client.rendering`，不视为成功终态。

Minecraft Control 绑定窗口 `5640310`，会话 `35c45b2a-f0f6-4103-a6bd-9754a625e26f`；聚焦未成功，首次 `mc_observe` 返回 `FOCUS_LOST`。依控制约定立即停止交互并 `mc_detach`，没有反复夺取焦点。未取得真实图片，未发送游戏输入，也未在缺少视觉摘要时调用 Jev。因此模型显示、真实右键、存档重进及正常退出全部保持 **unverified**；需要用户将窗口放到前台、保持无遮挡后继续。

准备证据位于 `output/minecraft-validation/stage17-v18-client-first/`，桥哈希清单位于上述会话目录。34 个已记录源码/定义/模型输入文件当前哈希全部不变。原存档目录 `run/saves/PRD17 Scene C v7` 仍存在，本轮尚未进入，不能声称已核对玩家或存档内容。SDK 会话保留以等待交互；本轮辅助脚本初版轮询误读 `status` 而非 `state`，已修正磁盘脚本，当前进程仍执行旧版，需要在游戏真正退出、任务终态落盘后清理辅助会话，不能把辅助进程未结束当成游戏仍在运行。

G17-A/B/C/D 继续开放。下一步包括本轮修复的新候选验证、剩余字段合同审计、安装候选旧工程恢复反例、UI 六夹具/语言/缩放/JCEF 矩阵、真实插件兼容、同源候选双平台建模及客户端闭环、独立试作与复评分；本轮未降低这些门禁。

## Windows v18 真实输入、正常退出与重进补证

用户恢复焦点后，会话 `5545c705-7ec6-4f61-a620-f5d0b3d6f4e3` 实际取得图片，并使用 Jev 1.13 提供过局部建议。主 LLM 审核后执行真实右键，在原世界 `PRD17 Scene C v7` 的 `(-8,64,5)` 放置 `structured_forge:resonance_forge`。画面显示棕色、约 12/16 高的导入模型，F3 确认朝向 west；没有紫黑缺失贴图。物品翻译键和偏大的第一人称显示仍为问题，不宣称美术或国际化全部通过。

`/give` 领取和 `/item replace block -8 64 5 container.0 with minecraft:copper_ingot 7` 为准备命令。保存前 `/data get block -8 64 5 Items` 的截图与日志确认 slot 0、count 7；不把该写入当作真实库存 GUI 操作。该测试方块未启用右键 GUI。重要图片：`frame-2e845ebf-a604-4937-ae96-d3c6f8270e54.png`（放置）、`frame-359243c6-d2cf-4f52-bd0c-2bef65df8d02.png`（位置/朝向）、`frame-72d25ad3-2a2d-43a5-b228-b53523e566ce.png`（7 个铜锭），均在上述会话目录。

游戏菜单保存退出的日志于 11:47:58Z 确认所有维度保存。后续会话 `34c7260d-ee2f-419b-9fe7-b49c13c0ad11` 从标题页发送正常 Alt+F4，12:21:27Z 记录 `Stopping!`，PID 35836 消失；公开任务 `42c25206-7b6e-483d-acaa-2f8ad65dd77e` 于 12:21:33Z 为 `succeeded`。终态和日志保存在 `output/minecraft-validation/stage17-v18-client-first/final.json` 与 `client-final.log`。仅在确认游戏已退出和任务终态后，清理了旧版轮询脚本的 Python PID 38636；没有以杀进程替代游戏正常关闭。

再次通过同一 v18 EXE 与随包 SDK 启动，任务 `de3fab2d-ebe3-497d-b743-a2d91e9fd700`，新客户端 PID 35200，启动时间 12:31:39Z。会话 `37aed952-980d-408c-aa74-41f8fd022bda` 重进同一存档，实际画面仍见原模型，F3 仍显示 `(-8,64,5)` 的 west 朝向。截图 `frame-b593bad2-9232-4c08-b6f6-b97045882783.png`、`frame-8495e018-1a6e-4052-bdff-5a1242822059.png`。首次玩家 Player989，重启后 Player506：这里证明世界方块恢复，不能声称同一玩家 UUID/库存恢复。

第二轮第一次批量只读查询未出现结果，不能按输入回执记通过。后续用户切换焦点，按约定停止并 detach。用户再次恢复后，会话 `824aa16d-2160-4e64-ad1f-5a2897ab80db` 可观察窗口，但数次键盘批次（含独立 Esc）返回 completed 而画面未发生预期变化；按键配置仍是默认聊天键且聊天可见。根因未确定，未绕过焦点检查、未继续盲发命令，已记录 unverified 并断开控制。第二客户端与 SDK 会话保留；**7 个铜锭的跨进程恢复及第二轮正常退出仍待验证**。

34 个模型/定义/源码文件在两轮启动前及当前哈希全部一致。汇总 `output/minecraft-validation/stage17-v18-restart-proof.json`；输入与截图证明范围是开发客户端，不是独立被测 JAR 宿主。原 v18 安装候选范围及全部 G17 门禁保持不变。

## Minecraft Control 桥与 Agent 指令优化

桥源码更新为 0.1.2，修复点击实际 30 秒与描述 120 秒不一致；新增有预算的动作后刷新等待、帧龄与步骤耗时、动作前拒绝留证，保留失焦/F8/去重/进程身份保护。项目 `AGENTS.md` 改为明确动作直接执行、复用步骤返回图片、在确定界面中批量执行短序列、Jev 仅用于有意义的局部选择。37 项桥回归全部通过，无真实桌面操作；证据 `.tmp/mcb-012-final.log`。

现存 MCP worker 未自动加载磁盘改动，doctor 仍为旧结构，需重连后核对 `bridge_version=0.1.2`。本次续验只应用了新的操作策略，不冒称新桥已通过游戏实测。旧会话耗时统计见 `output/minecraft-validation/stage17-control-timing.json`；旧 worker 不记录拒绝和 Jev 调用，端到端时间还含用户中断，未制造提速百分比。

## 新桥重连后的库存恢复与退出验收（本日最新）

用户重启后，实际 `mc_doctor` 返回 `bridge_version=0.1.2`、`max_frame_age_seconds=120`、`post_action_settle_ms=150`。当时旧客户端与 SDK 进程已不在；第二轮没有成功终态或 `Stopping!`，不能推断正常退出。公开 SDK 重开后将旧任务 `de3fab2d-ebe3-497d-b743-a2d91e9fd700` 恢复为 `failed / task.interrupted_unconfirmed / resultObservation=unconfirmed_after_session_loss`，保留原始证据，没有改写为通过。

通过同一 v18 导出包 EXE 和随包 SDK 启动第三轮，任务 `e6dae0b0-aa3e-4df6-898f-f2fdc9ffac71` 于 14:50:06Z 开始；客户端 PID 24592 于 14:50:18Z 启动，命令行核对仍为原测试工作区。桥会话 `05f505b2-eb74-46a2-9d9c-8c1a03776f87` 全程单一控制器。

- 在真实世界列表选择 `PRD17 Scene C v7`；本轮玩家实际为 Player953。
- 只读 `/data get block -8 64 5 Items` 在 14:56:06Z 的画面及日志返回 `[{count: 7, Slot: 0b, id: "minecraft:copper_ingot"}]`。本轮没有任何库存写入，证明最初准备的 7 个铜锭在真实进程重启后仍保留。此前第二轮已目视确认模型和 west 朝向恢复；不把本轮查询描述为新的模型视觉断言。
- 使用游戏菜单 Save and Quit to Title，14:58:37Z 记录所有维度保存；随后点击 Quit Game，15:00:00Z 记录 `Stopping!`。关闭动作回执为 `executed_unobserved / WINDOW_UNAVAILABLE`，这是窗口已关闭后的截图结果。
- PID 24592 消失，产品任务于 15:00:04Z 为 `succeeded`，SDK 辅助脚本自行退出。再开一个只读 SDK 会话，第一轮与第三轮仍为 succeeded，第二轮仍为会话中断未确认。
- 第一轮输入与第三轮结束后的 34 个源码/定义/模型文件哈希全部一致。开发客户端、世界方块库存的范围不变；未证明相同玩家 UUID 数据、真实库存 GUI、独立 JAR 客户端或 Windows 安装器认证。

证据目录为 `output/minecraft-validation/05f505b2-eb74-46a2-9d9c-8c1a03776f87/` 和 `stage17-v18-client-third/`。查询图片 `frame-774006b3-c5c1-458f-bc34-26010aea1074.png`、查询日志 `log-11254f82-526a-4075-b0dd-54b4fc5725de.txt`、退出日志 `log-882f73be-150c-4d0b-8db1-ba0fa7ba9067.txt`，以及 SDK 的 `final.json`、`reopened-tasks.json`、`client-final.log` 均保留。主 LLM 已通过 `mc_record_verification` 留下判断并 detach；会话最终哈希清单已生成。汇总：`output/minecraft-validation/stage17-v18-persistence-completed.json`。

新桥实机的四次点击分别使用 51.99、56.36、71.97、82.08 秒帧龄，全部成功，直接覆盖原先 30 秒残留限制；关闭回执也实际包含 150 ms 刷新等待预算。此前输入无可见效果的问题在本轮未复现，但不能仅凭重启后通过确定其根因。

本轮 7 个动作批次、8 张观察、0 次拒绝、0 次 Jev 调用，桥内输入合计 7.06 秒，步骤含截图合计 7.55 秒，整个控制会话为 572.67 秒（约 9 分 33 秒）。差额主要落在调用间隔，不能归因于 Jev；本轮未调用 Jev，不制造新旧端到端提速百分比。

**已关闭本条 Windows v18 开发客户端的真实放置、世界方块库存跨进程恢复及最终正常退出证据缺口。** 第二轮的中断记录保持原状。G17-A/B/C/D 仍开放：完整字段审核、新函数修复候选验证、旧工程恢复反例、UI/插件/双平台同源建模矩阵及独立试作等未由本条证明替代。

后续桥优化见 [9 月 23 日本地菜单流程验收](minecraft-control-menu-flows-2026-09-23.md)：0.2.0 已实测单请求进入指定世界 8.324 秒、单请求保存退出 1.600 秒，49 项回归通过。该记录只更新控制工具的流程效率，不扩大本阶段玩法或发行候选验收范围。
