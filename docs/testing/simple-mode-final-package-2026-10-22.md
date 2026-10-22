# 简洁模式最终候选包首作验收

日期：2026-10-22（Asia/Tokyo）。Windows + Codex 的 SC-04 首作全流程通过，包含本次独立审查修复及最新附件、思考强度、权限、手动模型、模式切换和交付布局。没有提交、推送或发布。

本记录补齐[独立审查报告](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/simple-mode-independent-review-2026-10-22.md)中“最新候选包尚未完整重验”的缺口。其他平台和后端不由这一条结果推定通过。

## 候选身份与环境

| 项目 | 实测对象 |
| --- | --- |
| 产品 | Copperbench 0.1.16，本地 Windows 便携候选包 |
| 构建标识 | `local-windows-0.1.16-final-review-20261022` |
| 源码 | `codex/simple-mode-onboarding`，基线 `21d8fc0148c5decf48589236b5b57b131517d2b0` 加未提交修改 |
| 实际工作树 | `C:/Users/Administrator/.codex/worktrees/simple-creation-merge/Minecraft_ModCreator` |
| 包来源 | 本工作树 `build/export/Copperbench 0.1.16 Windows 64bit.zip`；本地构建，无公开发布 URL，完整来源 URI 保存在 `candidate.json` |
| 运行目录 | `D:/AICoding/CopperbenchAcceptance/sc04-final-review-20261022/candidate/Copperbench0116` |
| 系统与显示 | Windows 11 x64，125% / 120 DPI |
| 应用语言 | 中文首作，待审批时切换 English；英文专业教程、停止、导出和恢复 |
| 助手 | Codex CLI 0.162.1；实际模型目录 8 项，明确选择 `gpt-6.1-sol`、`high`、默认权限 |
| 配置 | 独立 Copperbench profile，复用主机既有 CLI 配置与 Gradle 缓存；没有更改全局 CLI 配置 |
| 工作区 | `D:/AICoding/CopperbenchAcceptance/sc04-final-review-20261022/workspace/final_review_trail.mcreator` |
| 目标 | Fabric 1.21.1 / JDK 21；作品 Final Review Trail；mod ID `final_review_trail` |
| 世界 | `workspace/run/saves/CB_FINAL_20261022`，新建 Creative / Superflat，关闭结构生成 |

运行中的公共 SDK 返回 `sourceState=packaged_binary`，`applicationSha256` 与下列产品 JAR 一致。验收结束、更新报告之前，打包清单中的 91 个文件均与当前工作树匹配；此轮实机验收没有修改产品源码。

| 文件 | SHA-256 |
| --- | --- |
| Windows 候选 ZIP | `85c98869059501c235cb288fdf0d9e5176e023063eddbaf84b0858861d281f0a` |
| 产品 `lib/copperbench.jar` | `d84f2c6094fb89e02066b2275efef2d7b62223240db5712a755a55fa0a29e3b9` |
| `copperbench.exe` | `c0cd5784d40a11a59aa60824be77b2b279452fb042422a74843314f2e1eb20ea` |
| 打包源码清单 | `f848c191f40303ccee6f5f135d08eb4d79e71a2cc62b8b6766fcda4a7f2360a4` |
| 构建及最终导出模组 JAR | `5ea24cd2023a735b84666fc41de4dbb157f6a87207492c574506158ab4ddf35f` |

## 实际流程与结果

| 场景 | 观察及独立核对 | 结果 |
| --- | --- | --- |
| 首启与引导 | 新 profile 完成下载源选择、原生三步首启教程，通过 UI 创建空 Fabric 工作区，完成简洁模式六步教程 | 通过 |
| CLI 准备 | 检测五 CLI；Codex 协议握手后准确显示 CLI 通道可用、模型待验证；实际获取模型目录并选定模型和强度 | 通过 |
| 文件与图片 | 原生选择器加入 `creation-request.txt` 和 `reference.png`，显示文件和缩略图；真实 Codex 正确识别文字标记 `FINAL-REVIEW-8624`、图片文字 `IMAGE-7241` 及白底砖红色正方形 | 通过 |
| 创建与保存 | 助手通过随包公共 SDK 的 `Workspace.connect` 创建、编辑并保存 Trail Block；独立 SDK 回读与实际 Settings 一致 | 通过 |
| 可控失败恢复 | 字符串硬度 `invalid-number` 被 `FIELD_TYPE_INVALID` 拒绝，`canApply=false`；预览前后 revision、元素及检查文件哈希不变；修正为数字后保存并构建 | 通过 |
| 原生审批与连续性 | 只读目录查询产生真实单次审批；待审批期间输入草稿，切换语言及简洁/专业模式，原审批、草稿、工作区及任务保留；英文专业六步教程完成；返回简洁并单次批准后执行成功，草稿仍保留 | 通过 |
| 停止 | 第二个只读审批等待时，点击输入区 Stop；显示完整英文停止语义、回合结束且未执行该请求；用户消息仍为 3 条，无重复发送；已保存元素和成功构建保留，Core 无活动任务 | 通过 |
| 首次游戏 | 应用 Playtest 启动第一客户端；唯一世界中获得目标物品，实际右键放置；石头纹理和 F3 ID 正确 | 通过 |
| 世界持久化 | 正常保存、退出客户端；应用启动新进程，重开同一世界，原方块位置、ID、外观及快捷栏数量保留；再次正常退出 | 通过 |
| 导出 | 普通 Rebuild and export 到新路径；成功终态、实际文件和 SHA-256 相符；输出文件夹按钮与新交付路径按钮同排 | 通过 |
| 退出、重开与继续 | 应用正常退出后，用相同候选/profile 重开确切工作区；原参数、会话选项和四个任务恢复，没有自动执行；明确发送只读继续请求后，模型记住两个附件标记并通过公共 SDK 读出原元素和任务状态 | 通过 |

最后一次只读继续请求受 Codex 沙箱的本机桌面会话发现限制，需要单次批准。检查完整操作仅包含 SDK 查询后批准，查询成功；没有开启自动批准。独立复查确认 revision 仍为 2、Core 任务 ID 未变、无活动任务、导出 JAR 哈希未变。此为已恢复的环境权限限制。

## 保存结果、任务及输入身份

元素 ID：`31f33610-1ae5-4b8e-b155-a4775fa69d78`。内部名称 `trail_block`，显示名称 `Trail Block`，硬度 3、抗性 6、纹理 `minecraft:stone`、对应方块物品启用，最终 revision 2。字段源指纹为 `6e63620306d2e32bf1eea5fc56bd81588a0308f56a0cdc37955187324ace6971`。

| 操作 | 任务 ID | 终态 |
| --- | --- | --- |
| 助手首作构建 | `a05779c4-c494-44ea-ad3b-3acaa078a517` | `succeeded` |
| 首次客户端 | `c34a75e6-66dd-48e5-a054-d92347230abf` | `succeeded` |
| 重开客户端 | `08ab0335-b884-498c-80d9-40c03a9aeb25` | `succeeded` |
| 最终普通导出 | `a0d0625f-9cc0-49f7-8138-4c87c5395620` | `succeeded` |

首作构建期间助手持续更新工作区根目录的 `acceptance-receipt.json`，该文件属于输入快照范围；原构建回执如实标记 `inputUnchangedAtCompletion=false`。因此不把这次构建单独当作固定输入的验收证明。

最终两次 `run_client` 与 `export` 的输入 SHA-256 均为 `3060dac6bd01d59e49372adc74f3364a5676d97aa060150e3c5040c7616c51e6`，三者的开始/结束摘要一致，`noInputEventsObserved=true`、`inputUnchangedAtCompletion=true`。对应原始任务输入回执已复制到证据目录 `task-inputs/`。最终导出任务会重建已保存内容，其产物与首作构建 JAR 的实际 SHA-256 一致。

最终文件：`D:/AICoding/CopperbenchAcceptance/sc04-final-review-20261022/workspace/build/libs/final-review-20261022/trail-block.jar`，43,308 字节，`fabric.mod.json` 中 mod ID 为 `final_review_trail`、版本为 `1.0.0`。这是一条普通导出证据，没有执行独立的 packaged-JAR GameTest，未赋予该文件额外的自动验收声明。

## 游戏操作与正常退出

准备命令仅用于获得测试物品和设置观察方向：`/give @s final_review_trail:trail_block 16`、`/tp @s ~ ~ ~ 0 45`。实际放置使用玩家右键，没有用命令放置方块。两次 F3 均确认 `final_review_trail:trail_block` 位于 `2, -60, 4`，快捷栏为 16 个物品；重开后没有再次 give 或放置。

第一客户端 PID `61104`，第二客户端 PID `28396`，分别绑定本工作区的启动配置和 `run` 目录。Copperbench PID `56868` 在两次客户端完整生命周期内保持运行。每次均先 Save and Quit to Title，再 Quit Game；确认进程退出、日志中玩家/世界/区块保存、`Stopping!` 以及 Core 任务成功终态。

之后 Copperbench launcher/Java PID `47792/56868` 正常退出；用同一候选重开得到 `35312/56308`。恢复与只读继续检查结束后，这两个进程也正常退出。旧的候选 5 窗口和用户世界未被操作。

## 证据位置

原始目录：`output/minecraft-validation/sc04-final-review-20261022/`。

| 证据 | 内容 |
| --- | --- |
| `candidate.json`、`candidate-source-manifest.json`、`candidate-source-recheck.json` | 候选来源、哈希、91 文件源码清单及结束前核对 |
| `actions.jsonl`、编号截图和 `.txt` | 原生应用与游戏的观察、单步操作及操作后刷新 |
| `agent-first-creation-receipt.json`、`initial/`、`build-check/` | 真实首作、无效字段与修正、独立 SDK 回读 |
| `after-stop/`、`after-stop-chat.json` | 停止终态、无重复提交、保留的元素与任务 |
| `126-player-right-click-place-trail-block.jpg`、`127-identify-placed-block-and-coordinates.jpg` | 实际放置、纹理、ID、坐标和物品数量 |
| `137-reopened-world-block-and-inventory.jpg`、`138-verify-persisted-block-identity.jpg` | 新进程重开同一世界的持久化结果 |
| `first-client-*`、`second-client-*`、`world-binding.json`、`task-inputs/` | 进程、世界、任务输入身份、两次原始游戏日志及正常退出 |
| `148-final-export-completed.jpg`、`export-artifact.json`、`export-start/` | 原生导出成功反馈、实际 JAR 与任务 |
| `before-app-restart-chat.json`、`after-app-restart-chat.json` | 重启前后脱敏记录完全一致，未自动执行 |
| `158-readonly-resume-completed.jpg`、`resume-completed-chat.json`、`application-resume-verification.json`、`final-state/` | 明确继续后的真实模型回复、SDK 回读和无修改核对 |
| `application-*-exit.json`、`application-lifecycle.log` | 正常关闭应用的进程回执和生命周期日志摘录 |
| `summary.json`、`evidence-integrity.json` | 已测/未测范围及原始证据摘要 |

本轮由 Codex 操作验收，不是朋友独立完成的体验研究。复用了既有 Gradle 缓存；涉及 UIA 索引失效和中文输入法时改用可见控件的坐标/键盘导航；桌面被其他窗口覆盖或用户按 Esc 时暂停，收到用户继续指令并重新观察后恢复。混入无关窗口的截图已排除并保留排除说明，不用于验收判断。未收集 CLI 凭据或原生会话恢复 ID。

## 范围与剩余未测项

本轮完整通过的是此候选包、Windows、Codex `gpt-6.1-sol/high/default`、Fabric 1.21.1 的简单方块路线。配合[独立审查及回归](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/simple-mode-independent-review-2026-10-22.md)，补齐 SC-04 的主平台/主后端闭环。本轮未发现需要新增产品代码修复的失败。

- Linux、其他 CLI 的同包完整首作、朋友独立体验、全新主机/冷 Gradle 缓存未执行。
- Grok 文件、DSH 模型目录和其他后端协议能力沿用各自[聊天选项验收](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/agent-composer-options.md)的范围；Claude 当前源故障未在本轮重试。没有把历史记录算作这些后端的新包全流程验证。
- 自动批准的传参与确认、容量上限/损坏恢复、手动模型失败路径等边界依据已有真实临时文件服务测试及浏览器回归；未在这条桌面首作中重新填满 100 会话/16 MiB，也未开启主机自动批准。
- 原生文件选择器与图片读取已实测；Windows Explorer 实际拖放和所有后端图片能力未在本轮扩测。
- 应用内既有通用帮助的部分中文标题仍保留；新增简洁首作路线和双模式教程的双语与交互已检查。

原审查的五项修复及相关检查仍有效；本轮只有验收记录更新，最终执行文档链接和 diff 空白检查，不重复无新增变更的代码大套件。跟进心跳 `automation` 已确认保持 `PAUSED`，不再自动开启新一轮审查。
