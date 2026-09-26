# Stage 17：v31 同源候选与两平台过程复验

**整体门禁仍开放。** v31 将 v30 之后的返回值检查、直接上下文诊断、触发器目录、诊断统计与界面中英文改动纳入同一候选。本轮 Windows 使用导出 EXE；Ubuntu 独立候选目录和获用户授权更新后的安装入口均通过公开 SDK 复验。没有新增 JCEF、Blockbench 或游戏验收。

## 候选与来源

- 冻结源：`D:/Hyper-V/Stage17-Linux/source-candidate-v31`，27,188 个文件。非干净工作树快照，未提交或发布。
- `exportWin64 prepareDebLinux64 --offline --max-workers=1` 成功，1 分 36 秒；日志 `.tmp/stage17-v31-build.log`。
- Windows 与 Linux 主 JAR SHA-256 均为 `c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499`。
- 22 份随包文件、24 条文档链接、642 个 Schema 引用通过；冻结源清单中的文件无变化。证据 `D:/Hyper-V/Stage17-Linux/candidate-v31-proof.json`。
- Ubuntu 已准备 `/home/stage17/copperbench-stage17-v31-amd64.deb`，SHA-256：`72be5ed68f5973e8101801064ffcc760070069d4fd30ef5d22d8900e478fc3e4`。
- 用户在当前任务明确批准该 SHA-256 的 v31 包替换 v28。于 `2026-09-23T16:04:08Z` 完成安装，安装目录主 JAR 与上述候选一致；旧 v28 主 JAR 为 `ad62c7a4308a93824590478c0a8c0d7597f6967deda0dece6974e3fcf3b018e3`。

## 公开 SDK 复验

[探针](../../scripts/stage17-installed-procedure-context-probe.py) 只导入被测产品的随包 Python SDK，通过实际产品启动器连接独立工作区副本。两个平台都从此前已生成／构建的 v30 受管工程复制，排除运行目录和构建缓存，不修改原工程。

两平台各通过 11 组检查：

1. 实际触发器目录 ID 唯一，包含 `player_ticks`、`mod_serverload` 和真实坐标依赖，不包含旧界面猜测的三个 ID。
2. 切换到不提供 `x` 的服务端启动触发器时，预览为不可生成、可保存草稿；给出 `PROCEDURE_CONTEXT_MISSING` 及实际节点定位，定义和修订不变。
3. 保存无效草稿并关闭重开后，状态仍无效；当前全局错误增加一条、无效元素一个。
4. 显式生成被该上下文诊断阻断，定义与修订不变。
5. 数值返回节点连接布尔值时保存为无效草稿，编辑器返回 `PROCEDURE_RETURN_TYPE_MISMATCH`。
6. 修复返回连接与触发器后，当前错误和无效元素均归零。
7. 同一修复工程通过产品 `generate` 与 `build` 任务；等待到成功终态，不把 `accepted` 当作成功。
8. 再次关闭重开保持修订 12、`player_ticks` 和元素定义字节，诊断为空。两份源工程输入哈希均未变化。

以上 8 条描述对应探针中 11 个命名断言组，不是 11 项玩法测试。Windows 证明在 `output/stage17-v31/windows-managed-context/`；Ubuntu 独立候选证明在 `/home/stage17/evidence/v31/managed-context-proof/`。

用户批准安装后，通过 `/usr/bin/copperbench` 与 `/opt/copperbench/sdk/python` 在第三份独立副本重跑同一探针，11 组再次全部通过，最终修订仍为 12。安装和探针均确认原共鸣工坊模型、PNG、旧 Mod JAR／种子输入未改变。Ubuntu 两批 SDK 与安装证明已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v31/`，副本在 `output/stage17-v31/linux-installed-context/`；结束后未发现遗留产品 API 或探针进程。

## 保留的失败与授权边界

首次 Windows 探针误将 Wayfinder 示例当成空生成工程：前九组过程检查通过，但修复后生成正确返回 `GENERATION_SOURCE_CONFLICT`。该示例带手写源码与独立注册入口，不能因 `mod_elements` 为空就视为可重建模板。保留 `output/stage17-v31/windows-context/`，没有删除、接管或改写其受保护源码，也没有放宽产品的所有权保护。

随后改用受管种子副本，并要求构建探针显式提供 `--seed`；修正后的验证器、哈希与两端结果单列保存。它是冻结后修正的外部测试脚本，不冒充冻结源清单中的同字节脚本。另有一次种子文档枚举将 `.mcreator` 目录误计为文档，已收紧为文件并保留启动失败日志。

首次安装尝试使用已核对仅涉及 Copperbench 的 apt 计划；`apt-get --no-download` 对本地 deb 返回路径内部错误，安装版本仍为 v28。随后使用 `dpkg --install` 安装同一已核验包，不下载其他包；保留两次日志和计划，没有把首次失败改写为成功。

最初的 `bootstrap list-authorizations` 中 Windows 授权均已失效。用户随后明确授权 `D:/Hyper-V/Stage17-Linux/final-acceptance` 的 24 小时 `create,edit,build,test,run_client` 范围。产品确认界面返回活动授权，截止 `2026-09-24T16:03:50Z`，没有恢复、服务器或 EULA 授权。computer-use 只查询了窗口，没有点击批准按钮；确认完成由产品回执证明，不伪称自动点击。

用该产品签发 ID 调用公开 `bootstrap create-workspace --no-prompt true`，从此前不存在的目录成功创建 Fabric 1.21.1 工程 `D:/Hyper-V/Stage17-Linux/final-acceptance/fabric-1.21.1/stage17_structured.mcreator`，返回 `committed`。这证明新建入口，不等于场景 A 的方块／玩法链已完成。授权及创建回执在 `output/stage17-v31/`。

## 仍需完成

后续已补充 [Windows 双 Loader 场景 A 配置、构建、六项隔离 JAR 行为测试及可信导出](stage-17-v31-scene-a-2026-09-24.md)。真实 JCEF 六夹具／规定语言和缩放、同源候选两平台建模与客户端闭环、其他平台／轨道范围、三次独立试作，以及传递调用／插件完整语义等剩余矩阵继续开放。G17-A/B/C/D、产品状态和发行状态不推进为通过。
