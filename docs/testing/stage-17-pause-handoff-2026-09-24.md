# PRD17 暂停与续接记录

用户要求暂时收工、明天继续；目标已设为 `paused`。本记录只整理现状，不表示 PRD17 完成。恢复前不要继续构建、安装或验收。

## 用户确认的后续收尾方式

暂停后的只读复核发现旧余项清单重复列入已完成工作。恢复时采用“冻结原 PRD 范围、复用有效证据、只补受影响验收”的方式，不能再次按旧清单全面重做，也不能因此降低原验收要求。

- v31→v32 的产品变更仅四个文件：两处 Fabric 方块名称键、方块创建默认值和对应公开默认说明；其余变化为测试、脚本和文档。按实际影响选择回归。
- 既有场景 B 的 36 部件/14 用例、Code 双 Loader 正负对照、场景 D 多项拒绝保护和 v26 双平台恢复已有成功记录。保留候选归属，通过源码影响核对判断可复用范围；缺失或受影响的部分才补跑。
- 已有 fields、model、delivery 三路独立评估记录。字段与交付已有通过子范围，建模有未闭环部分；先核对并补齐，不预设三路都要从零重做。主 Agent 补验仍不冒充独立 Agent 结果。
- 不自行新增完整编译器级过程分析或全部社区插件认证。PRD 与已公开合同承诺的保存、引用、诊断和兼容边界仍须满足。
- 归并为五组：v32 包与双平台定向复验；真实 JCEF 矩阵；双平台连续建模/客户端；B/D/Code/独立试作缺口；证据和门禁收尾。
- 沟通中的 8～12 小时、剩余约 20%（15%～25%）仅为无新阻断前提下的规划估算，不是已验收百分比或期限承诺。若出现新阻断，具体报告影响，不默默扩展清单。

## 收尾状态

- 已启动的 v32 打包任务自然结束，`BUILD SUCCESSFUL in 1m 24s`，工具会话 76342 已返回退出码 0。没有运行中的待续工具句柄。
- Windows 核对没有本轮匹配的测试客户端、SDK 探针、候选构建或反向代理进程；Minecraft Control 返回空窗口列表。之前的控制会话均已 detach。
- Ubuntu 核对没有产品或 Gradle 测试进程，临时代理端口 23067 不再监听；已安装主 JAR 仍为 v31 的 `c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499`。
- 保留工作区改动、测试世界、缓存、所有历史候选与失败证据。没有提交、推送、删除或替换安装包；没有关闭测试 VM。
- 收尾原始记录：`output/stage17-v32/pause-state.json`、`pause-vm-state.json`、`build.log`。

## 本轮已完成的证据

- [v31 Ubuntu 双 Loader 场景 A](stage-17-v31-linux-scene-a-2026-09-24.md)：Fabric、NeoForge 1.21.1 各六项实际 GameTest、当前输入导出与重开通过，Mod JAR 分别与 Windows 相同。NeoForge 使用经用户批准的临时回环代理；默认直连网络不计通过。
- [v31 Windows Fabric 客户端](stage-17-v31-packaged-client-2026-09-24.md)：同一已测 JAR 的真实右键放置、棕色模型、south 朝向、七钻石方块库存跨进程保存和两次正常退出通过。无真实库存 GUI、同一玩家 UUID 或全部客户端资源的通过声明。
- [方块默认资源源码修复](stage-17-block-visual-defaults-2026-09-24.md)：修正 Fabric 1.20.1/1.21.1 方块名称键；新建方块默认渲染类型 10、材质 `minecraft:stone`，公开默认说明同步。11 项回归全部通过，含八轨实际生成和旧工程差异处理。未静默修改旧工程。

## v32 已准备，尚未验收

- 冻结源目录：`D:/Hyper-V/Stage17-Linux/source-candidate-v32`，27,196 文件，是 dirty 工作树副本，不是新提交或 Git worktree。
- 清单：`D:/Hyper-V/Stage17-Linux/source-manifest-v32.json`，SHA-256 `7a212e0b026a40be31f1e9a33496835a08bdfd6a630986fa967c8dd7f140fcdc`。
- 构建命令：冻结目录的 `scripts/run-gradle-external.ps1 exportWin64 prepareDebLinux64 --offline --max-workers=1`。共享既有 JDK 和 node_modules 的任务目录 junction 已保留，不要递归删除其目标。
- Windows：`D:/Hyper-V/Stage17-Linux/source-candidate-v32/build/export/win64`。
- Linux deb 准备根：`D:/Hyper-V/Stage17-Linux/source-candidate-v32/build/export/linux-deb-root`。
- 两端 `lib/copperbench.jar` SHA-256 均为 `4066f3acb35271579e27d02ea1360920f228d79d0a1413e129c54551267cb32e`。
- 尚未运行 `.tmp/stage17-audit-v32.py`，未传输到 VM、未生成最终 deb 哈希、未安装、未进行 v32 公开 SDK 或客户端验收。打包成功不能替代这些步骤。
- `scripts/stage17-cross-platform-acceptance.py` 与 `scripts/stage17-packaged-client-session.py` 新增 `--expected-application-sha256`，v32 使用上述哈希；默认值仍兼容 v31。v31 原脚本归档到 `output/stage17-v31/harness/`，对应历史证明已改指向该副本，原 SHA 保留。不要重新运行旧 `.tmp/stage17-v31-closeout-evidence.py` 将历史脚本引用重新指回已更新的当前脚本。

## 恢复后的顺序与授权

1. 重新检查当前进程、候选文件与授权状态，先审计 v32 冻结源、随包文档、Schema 及跨平台产物；复用已完成构建，不因中断重新打包。
2. 使用新建的 v32 测试工程完成公开 SDK 场景 A，增加默认方块模型与正确名称键的 JAR 检查，再通过实际客户端验证这两项修复。保留 v31 工程与证据。
3. 准备并校验 Ubuntu 独立候选及最终 deb；完成可独立执行的复验后，若要替换已安装 v31，再就具体包 SHA 请求安装授权。此前安装授权只覆盖特定 v31 包。
4. 继续 PRD17 的真实 JCEF、Blockbench 双平台往返、场景 B/D、受影响八轨与三次独立试作等剩余范围；G17-A/B/C/D 均未关闭。

Windows 产品授权范围为 `D:/Hyper-V/Stage17-Linux/final-acceptance` 的 `create,edit,build,test,run_client`，ID `f81e34c2-babc-4540-b4ed-3775b89bf89c`，到期时间 `2026-09-24T16:03:50Z`（东京时间 9 月 25 日 01:03）。恢复时需实际查询有效性，不把聊天中的许可当成仍有效的签名授权；没有历史恢复、专用服务器或 EULA 授权。

测试 VM 当前 IPv4 为 `172.17.3.24/20`；若 Default Switch 再次换网段，先读取当前状态。固定主机密钥配置仍使用 `D:/Hyper-V/Stage17-Linux/known_hosts` 和历史 `HostKeyAlias=172.20.86.21`。IPv6 后备目标为 `stage17@fe80::0215:5dff:fe00:5f04%37`。已批准的 DHCP 修复已完成，不应在无新证据时重复操作。临时代理记录见 v31 报告；不得由此修改持久网络配置。
