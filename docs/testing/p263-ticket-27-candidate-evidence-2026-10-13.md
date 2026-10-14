# Ticket 27 — 按范围冻结候选与独立证据门禁

日期：2026-10-13（JST）。本票已完成本地实现、真实执行、证据复核与代码自查。修改尚未提交、合入或发布。

## 编辑器契约兼容

14 号票发现的丰富 editor 响应没有继续冒用冻结 1.0。新增仅用于 editor 的显式 1.1；Native/MCP 不指定版本仍返回严格 1.0 投影，UI 与 SDK 明确选择并验证 1.1。其他操作和 handshake 保持 1.0。原始字段值与权限保留，旧响应移除新版元数据，引用控件投影成旧 text/JSON；block 旧数字字段契约与 item/recipe 的字段契约分别建模。使用方式见[编辑器版本文档](../ai/editor-schema.md)。

两加载器 × item/block/recipe/function/code × 两版本的 **20 份真实 Core 响应**通过对应严格 schema；JCEF round trip 与只读字段通过。额外 **2 份 Native wire、2 份 MCP HTTP/SSE**响应通过完整 schema；不支持的版本被拒绝。没有修改冻结 1.0 schema 来容纳新版字段。

已通过 106 项 Python SDK、22 项 TypeScript SDK、32 项 UI unit、50 项 UI-Core、37 项 Chromium 场景，以及 Javadoc/UI build。前四轮 Java 按类、方法、参数去重是 24 个行为；新增 MCP HTTP 行为另计 1 项。浏览器中文 recipe 编辑画面已检查，属于浏览器证据，不代表原生桌面窗口验收。

## 候选冻结和校验

[候选工具](../../tools/candidate-evidence/README.md)绑定 source commit、HEAD Git tree、实际文件字节/模式清单、构建前后身份、原始命令、应用 JAR、启动器、安装包、Python Native/MCP 与 TypeScript SDK、生成器 ZIP 和样例文件清单。候选 staging 完整复核后才改名发布到新目录；构建/冻结失败保存原始失败，不覆盖旧候选。

维护者按加载器、G1/G2 与系统/桌面选择范围。未选路线保持 unavailable；G1 Xorg/Wayland 独立，G2 Ubuntu 要求同一候选的两种桌面会话。包 helper 当前只准备 G1；G2 必须重新冻结完整五类实现和十三项样例。就绪映射是绑定源码的生产者声明，不是玩法证明。

结果保留四个安装单元、六个客户端单元的 passed/failed/blocked/not_run。即使一个已选范围完成，其他缺项也使整表不成为 passed。安装通过还要调用仓库内可信便携 verifier，从原始 XML/JAR 重算固定清单：G1 六项、G2 十三项；检查当前/历史导出属于同一验收任务与 JAR。客户端必须绑定选定安装回执和相同 JAR，并包含画面、动作、日志、世界身份、进程和验收记录。

工具验证完整性与证据对应关系，不验证生产者身份，不自动识别截图中的玩法，不发布支持声明。脏源码可以留存本地机制演示，但 `qualificationEligible=false`。原始 `run_binding` 防止仅修改外层 candidateId 就换绑旧证据。

22 项候选边界回归通过，包括错摘要、重新计算元数据摘要后的缺失角色/范围、源码变更/执行位、构建失败、旧回执、缺图像角色、不同世界/安装 JAR、G1 冒用 G2、重复原始用例与伪造摘要的 5/6。测试中的截图/动作和包均明确是合成夹具，不计为真实客户端或安装验收。

## CI 与产品打包

Nightly 保留原八个精确生成器项，新增 Fabric/NeoForge 26.3 两条独立业务 job；16 个门禁分别计数。每条新 job 运行 backend → Native 故障修复/重连/导出 → 便携收集 → 离线复核，成功和失败原始 XML、日志、JAR 与回执单独保留。目录选择只接受本次新建的唯一运行，原始文件不能跨到其他会话或授权存储。

保留 `Java tests and Javadoc`、`UI contract, build, and smoke tests`、`MCP conformance` 三个必需名称，M1 工作流未改动。协议、来源和迁移边界仍触发完整受影响检查；PR 与 main push 路由不增加重复运行。11 项定向选择/路由检查及 14 项 CI harness 检查通过。原 CI 选择全集在 Windows 有两项因换行/制表/反斜杠文件名不受支持失败，原始日志保留；未声称这些 POSIX 文件名用例在 Windows 通过。

三个变更工作流由项目已安装的 SnakeYAML Engine 3.0.1 解析，通过 YAML 1.2 语法与重复键检查；不将此结果称为 GitHub 云端运行或完整 Actions lint。

既有 Linux candidate workflow 增加可选 `p263_loader`，默认保留旧路由。选中时冻结 Ubuntu 候选并上传十项初始 not_run 状态；既有 Xvfb 预检不计为 GNOME Xorg/Wayland 玩家验收。本候选云端尚未执行。

产品打包补齐 editor 1.1/扩展 schemas、TypeScript SDK、两加载器 Camp Kit 样例和离线 verifier，避免仅冻结源码中的 SDK 而遗漏交付内容。

## 两条真实业务 job 与 Windows 冻结结果

同一个 runner 脚本已在本机真实执行两条路线，均完整通过 backend、Native、collect、offline 四步。它们是源码业务回归，不冒充 28/29 的安装验收。

| 精确生成器 | Backend 耗时 | Native 耗时 | 固定业务数 | 原始收集文件 |
| --- | ---: | ---: | ---: | ---: |
| fabric-26.3 | 104.044 秒 | 311.570 秒 | 6 | 80 |
| neoforge-26.3 | 87.146 秒 | 342.759 秒 | 6 | 80 |

两条路线均实际经历编译失败/修复、独立宿主六项验收、原 JAR 导出、三个产品进程重连、篡改/重复/当前输入变化拒绝、显式历史导出、真实五项不足六项及拒绝导出，最后恢复六项并重开导出。框架自检另计 1 项。原始结果、JAR/XML 摘要和每步回执见 `output/p263-27/ci-actual-summary.json`；便携文件由可信离线 verifier 重算通过。

实际 Native 运行目录分别为 `output/p263-09/native/delivery-5360907995704740189` 与 `output/p263-10/native/delivery-15507889843739960197`，本次副本独立归档于 27，不改写 09/10 的旧冻结归档。Fabric 在收集边界进一步收紧后又单独重放原始文件收集，未重复游戏验收。

Windows 同时构建 NSIS 安装器和便携 ZIP，Gradle 构建用时 **5 分 42 秒**；构建前后源码身份一致。冻结 11 个产物，候选 ID：

`sha256:40205a6e06bd8af91a3568db63bf84e575b09410cd834f01a179e66ff4547ed8`

| 产物 | SHA-256 |
| --- | --- |
| 应用 JAR | `fd7b87f8f6609beaf7c60209cf7306cb1b217dc3bcf5fc77f08acbbb7de46b9a` |
| 启动器 | `c01105c6c911381f4c7769d105775d4ae1f19cf2a237070852aeccd062dcf364` |
| NSIS 安装器 | `7e35ce53416962b3024a35bc1d3c93102bfc27b93eee05e855c3bb11f704724f` |
| 便携 ZIP | `eb6dee917efd1b2f1fa66b9a52e314914efefef63f758f7ee4f1c417acbc418f` |

包内真实启动器执行只读 `bootstrap list-generators`，**10.554 秒、exit 0**，保留 Fabric/NeoForge 1.21.1 既有基线条目；运行前核对启动器和 JAR 摘要与候选相同。该证明仅覆盖交付包启动/发现及冻结路径。没有运行 NSIS 安装程序，没有把源码会话、只读发现或便携收集记为安装/玩家验收。

候选源为未提交的实际工作树，明确 `dirty=true`。四个安装与六个客户端单元均为 `not_run`，两条 G1 Windows scope 均 `qualificationEligible=false`。最终记录文档在冻结完成后补写；冻结源码清单保留构建当时的原始字节，不反写候选身份。

## Fabric 冷 POM 依赖修复

旧 14 号创建失败缺少 `org.jetbrains:annotations:26.0.2` 的 POM 校验值。实际从 Maven Central 取得 1686 bytes，SHA-256 为 `7ebeceade8dada7cb17eff3e014922c39022d2a378e3e2d2461f28f016ffc7f7`，与资格化缓存字节相同；Mojang 同路径返回 BlobNotFound，未把旧 Gradle repository label 当成下载 URL 证明。

隔离探针先发现普通 `mavenPom()` 会跟随 `.module`，再以 `ignoreGradleMetadataRedirection()` 和全新缓存真实重现 POM 验证失败。只新增已核验 POM SHA 并更新 environment lock 后，又用新空缓存通过，保持 metadata verification 开启。9 项 generator-probes 回归通过。此结果仅覆盖该依赖解析路径，完整安装版冷/暖条件仍由 28–31 实测，NeoForge 冷启动不由此推定通过。

## 失败历史与后续边界

保留 editor 初次编译缺 import、测试重复 UUID/错误文本、SDK 版本验证误扩到其他操作、schema 中间缺口等修复历史。MCP HTTP 初次测试误把 SSE 当成裸 JSON，调整测试解析后通过。候选测试曾分别创建两个带不同时间戳的合成 JAR，被同 JAR 校验正确拒绝；夹具改为两个导出实际复用同一 JAR。新业务 runner 首次被 PowerShell 解析 Windows 盘符属性阻断，改用 JSON 参数数组后继续真实执行。

当前没有可调用的 minecraft-control MCP；主项目配置为 `enabled=false`，并指向隔离 Linux X11 客体。客户端操作需要合适目标的 MCP 连接重载与成功 doctor/list/attach，不能把 shell 启动 stdio 或输入成功当成已验证。用户已指定 Ubuntu 使用云端，本票不把本地编译或旧候选换绑成该环境证据。

源码与证据自查未发现未解决的本票问题。Markdown 链接与差异空白检查通过。稳定归档位于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-27`；安装、云端与客户端结果继续由 28–41 独立记录。
