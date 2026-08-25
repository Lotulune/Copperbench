# Copperbench 项目评估与改进建设性建议

**评估日期：2026 年 8 月 25 日**
**评估对象：`Lotulune/Copperbench` 主分支**

## 一、评估说明

本次检查覆盖了：

- 项目 README、PRD、ADR、架构和用户文档
- Java 核心、React/JCEF 产品外壳、MCP 与 headless 入口
- Fabric、NeoForge 和资源包生成器
- 项目自带测试记录与机器可读证据
- GitHub Actions、Releases 和仓库公开状态

本次未完成一次从空环境克隆后的全量本地构建，原因是执行环境未能解析 GitHub 主机。因此，以下结论明确区分：

1. **代码中确实存在的功能**
2. **项目维护者提供的测试证据**
3. **GitHub 公共 CI 已独立验证的结果**
4. **普通用户当前实际能够下载安装的结果**

这一区分非常重要：Copperbench 当前的代码和内部验证水平，明显高于它的公共发布与可复现水平。

---

## 二、核心结论

Copperbench 已经不是简单的 MCreator 换皮分支，而是在尝试把 MCreator 重构为一套同时面向可视化创作者、AI 客户端和自动化脚本的模组开发工作台。

它当前最突出的优势是：

- UI、MCP、headless 共用同一套 Java 领域服务
- 有修订号、事务、恢复点、单写者锁和审计记录
- Fabric 与 NeoForge 四条版本轨道已经形成
- MCP 是真实实现，不只是规划文档
- 新产品外壳、Blockly Procedure、资源管理和 Blockbench 往返已有实现
- 项目测试文档对“已完成、开发预览、尚未验证”区分得比较诚实

系统架构规定 React/JCEF、MCP 和 headless 不能分别实现不同的工作区规则，并统一经过应用服务、写锁、生成器、历史和任务系统。这是该项目最有价值的设计决定之一。

但它目前最大的短板并不是缺少更多功能，而是：

> **代码实现、测试证据、GitHub CI、公开文档和用户可下载版本没有形成一致的发布闭环。**

当前更适合定位为：

**高级开发预览版 / Internal Alpha**

尚不适合称为面向普通用户的公开 Beta，也不能完全替代正式 MCreator。

---

## 三、综合评分

以下评分是基于当前仓库状态的评审判断，不是项目官方指标。

| 维度 | 评分 | 评价 |
|---|---:|---|
| 产品方向 | 8.5/10 | 差异化明显，不是无意义重复造轮子 |
| 系统架构 | 8.5/10 | UI、AI、CLI 共用领域层，设计成熟 |
| 核心功能实现 | 7/10 | 主创作链路已有较多真实实现 |
| 模组元素完整度 | 6/10 | 常用基础类型可用，高级类型不足 |
| AI 架构友好度 | 8.5/10 | MCP、Schema、预览、修订和审计都较完整 |
| AI 开发者体验 | 5.5/10 | 缺少 SDK、示例、版本策略和公开接入文档 |
| 产品内操作易用度 | 6.5/10 | 工作流设计合理，但仍缺少系统化可用性验证 |
| 公共下载安装体验 | 2/10 | 当前 Release 没有实际安装包 |
| 公共可复现性 | 3/10 | 主分支 CI 没有正常覆盖 |
| 综合公开成熟度 | **5.8/10** | 有技术实力的高级 Alpha |

---

# 四、功能运行情况

## 4.1 已有证据较充分的功能

项目维护者留下的 Stage 8 证据显示，以下八套空工作区生成器均完成过真实 Gradle 构建，并生成了 JAR：

- Fabric 26.2
- NeoForge 26.2
- Fabric 26.1.2
- NeoForge 26.1.2
- Fabric 1.21.1
- NeoForge 1.21.1
- Fabric 1.20.1
- NeoForge 1.20.1

同一份证据还覆盖了真实资产查询、资源引用、资源包工作区创建和 ZIP 导出，并包含新建工作区的 Playwright 测试。

Stage 9 的维护者测试记录显示，当前开发树已经实现：

- Procedure 结构化 IR
- Blockly Procedure 编辑工作台
- 未知节点载荷保留
- 变量、标签、语言键的稳定 ID 和引用统计
- Function、Loot Table、Advancement 的第一方 CRUD
- 专用服务端、datagen 和 GameTest 受管任务
- datagen 暂存、差异预览、确认发布和失败回滚

记录中的测试结果包括：

- `compileJava` 通过
- `ui-core` 15/15
- UI 构建通过
- 48 个定向 Java 测试无失败，3 个环境跳过
- 既有 UI E2E 108/108
- Stage 9 E2E 6/6

这些证据说明核心实现并非只有界面原型，工作区读写、任务、Procedure 和注册表功能已经具备实际运行基础。

Windows 打包证据还显示，维护者环境曾成功生成 NSIS 安装包、ZIP 和 MSIX，并演练了安装、升级、卸载和用户数据保留。

## 4.2 尚不能据此认定为完全稳定的部分

目前仍未关闭的验证包括：

- 500 节点 Procedure 可用性和性能
- 2,000 个元素、10,000 条引用查询的 P95 性能
- 正式键盘操作与可访问性审计
- Stage 9 新类型在八套生成器上的黄金编译
- 所有版本轨道的专用服务端 readiness
- 真实 JCEF 宿主下的完整 Stage 9 测试
- 最新源码对应的干净 Windows 11 虚拟机验证

项目自己的剩余工作清单也明确将这些列为未关闭门禁。

此外，Blockly 懒加载包约为 792 KiB，目前只是产生体积警告。对大型 Procedure 首次打开速度、内存占用和编辑响应仍需要实际测量。

## 4.3 公共 CI 当前无法证明主分支稳定

这是现阶段最严重的工程问题之一。

仓库默认分支是 `main`，但 `.github/workflows/test.yml` 只监听：

- `master`
- `feature/*`
- `dev/*`

因此，直接提交到 `main` 或面向 `main` 的普通 PR 不会触发该测试工作流。

GitHub Actions 公开记录当前只有两次定时任务：

- Crowdin Pull 成功
- Javadoc 工作流失败

没有看到当前主分支的完整 Build and Test 成功记录。

Javadoc 任务本身成功生成了文档，但部署阶段失败，说明文档生成能力存在，公开部署链路尚未接通。

### 功能运行结论

| 问题 | 结论 |
|---|---|
| 核心代码是否只是空壳 | 不是 |
| 维护者环境中能否构建和运行 | 有较强证据表明可以 |
| Stage 9 功能是否全部稳定 | 否，仍属于开发预览 |
| 当前主分支是否被公共 CI 持续验证 | 否 |
| 普通用户当前能否直接下载最新可执行版本 | 基本不能 |
| 是否可以作为日常主力工具 | 目前仅建议开发者和测试者使用 |

---

# 五、从模组开发者角度看还欠缺什么

## 5.1 已经具备的价值

当前新 UI、MCP 和 headless 第一方可编辑的元素包括：

- Block
- Item
- Recipe
- Procedure
- Function
- Loot Table
- Advancement

同时已经具备变量、标签、语言键、资源管理、Blockbench 往返、工作区历史、加载器迁移、客户端运行、服务端运行、datagen 和 GameTest 等能力。

四条版本轨道和 Fabric/NeoForge 双加载器方向也很实用。对于需要同时维护旧版 1.20.1、1.21.1 和较新版本的开发者，Copperbench 的版本矩阵是有吸引力的。

## 5.2 当前最影响实际开发的功能缺口

### 1. 高价值模组元素覆盖不足

当前未进入第一方完整编辑范围的类型仍很多。优先级较高的包括：

- Living Entity 与生物 AI
- GUI、菜单和容器
- Armor、Tool、Weapon
- Biome、Dimension、Structure 和 World Generation
- Potion Effect、Particle、Sound
- Key Binding
- 网络包与客户端/服务端同步
- 自定义渲染
- Capability、Component 或数据附件
- Command 和事件监听

迁入的其他上游类型虽然可以保留，但多数只能只读显示，不能通过新 UI 或 MCP 创建和修改。

建议不要一次性追求“支持 MCreator 全部类型”，而应根据真实模组中的出现频率建立覆盖顺序：

1. Entity
2. GUI/Menu
3. Tool/Armor
4. Worldgen/Structure
5. Effect/Particle
6. Networking/Keybind
7. 高级渲染与扩展 API

### 2. Stage 9 三种数据元素编辑深度不足

Function、Loot Table 和 Advancement 当前虽已具备 CRUD，但项目文档明确表示仍需补充：

- Function 命令级诊断与函数标签
- Loot Table 的池、条目、条件和函数专用编辑器
- Advancement criteria 编辑
- Advancement 父级循环防护

这几种类型现在更像“结构化数据可写”，还没有达到成熟可视化编辑器水平。

### 3. 本地化工具仍然偏基础

语言注册表目前有稳定 ID、CRUD、引用和重命名预览，但缺少：

- CSV 导入导出
- JSON 导入导出
- 主语言与回退语言
- 缺失键报告
- 重复键报告
- 参数占位符一致性检查
- 多语言完成度统计

对于准备发布到 Modrinth 或 CurseForge 的模组，这部分会直接影响生产效率。

### 4. Procedure 缺少大型工程开发能力

目前已有 Blockly 工作台，但大型 Procedure 还需要：

- 节点搜索和快速跳转
- 小地图
- 折叠区域
- 可复用过程与参数化调用
- 断点或执行路径可视化
- 未使用变量和不可达节点检查
- 生成代码与 Procedure 节点的双向定位
- 运行时异常回映到具体节点
- 大图自动布局
- 结构化差异比较

500 节点门禁尚未完成，也说明当前还不能确认大型 Procedure 的实际体验。

### 5. Java 与高级构建配置入口不足

仓库中尚未看到一套面向普通创作者的成熟一方体验，用于管理：

- Maven 依赖
- Mod 依赖
- Mixin
- Access Widener / Access Transformer
- Gradle 属性
- JVM 参数
- 数据生成器配置
- 自定义源码目录
- 加载器条件源码
- 依赖冲突诊断

Copperbench 已保留 Manual Source 作为高级出口，但如果缺少结构化依赖和构建设置，用户仍会频繁回到 IDE 或直接修改 Gradle。

### 6. GameTest 更像运行入口，而非创作工具

建议增加：

- GameTest 创建向导
- 测试结构选择和保存
- 断言模板
- 一键重新运行失败用例
- 测试结果与模组元素关联
- 失败场景截图或世界快照
- CI 可复用的 JSON/JUnit 输出

### 7. 插件生态需要从“兼容设计”转为“可验证生态”

项目已经设计了 A/B/C/X 四级插件兼容模型，这是正确方向。A 类为资源和生成器，B 类为非 Swing Java 逻辑，C 类进入旧版 Swing 窗口，X 类明确拒绝。

但还需要：

- 第一方插件 SDK
- 稳定扩展点清单
- API 版本和弃用策略
- 示例插件
- 插件开发模板
- 每个版本自动跑代表性插件矩阵
- 已验证插件公开列表
- 插件崩溃隔离与诊断包

### 模组开发者视角结论

Copperbench 已经可以支持“基础方块、物品、配方、Procedure 和部分数据驱动内容”的受控开发流程，但暂时不适合复杂生物、GUI、世界生成或深度客户端交互模组作为唯一工具。

它当前更像：

> **一个架构先进、AI 能力突出的 MCreator 基础创作子集，而不是完整的 MCreator 替代品。**

---

# 六、是否对 AI 开发友好

## 6.1 架构层面非常友好

Copperbench 的 AI 架构明显优于多数传统桌面模组工具。

真实 MCP 工具目录已经包含：

- 获取工作区
- 列出和读取模组元素
- 创建、更新和删除元素
- 预览元素变更
- 读取、预览和提交 Procedure
- 读取引用图
- 管理变量、标签和语言键
- 预览重命名影响
- 创建工作区
- 校验、生成、构建和导出
- 运行客户端、服务端、datagen 和 GameTest
- 获取任务日志与状态
- 创建恢复点
- 管理资产

工具使用 JSON Schema，写操作需要 `expectedRevision`，并区分预览和提交。这些设计能显著减少 AI 覆盖他人修改或直接写坏工作区的风险。

MCP 服务是真实的本机 HTTP Streamable MCP 服务，监听 `/mcp`，只绑定 `127.0.0.1`，设置请求超时，并使用令牌和权限档位。

安全模型还规定：

- Read Only、Workspace、Full Access 三级权限
- 强制恢复点和审计记录
- 删除工作区、导出凭据、外部发布、启用 Java 插件必须由用户确认
- 令牌按工作区隔离
- 拒绝通配 CORS
- 修订冲突不能静默覆盖
- 日志需要脱敏

这是一套相对成熟的本地 AI 控制面设计。

## 6.2 AI 开发者体验尚未产品化

当前“底层适合 AI”不等于“第三方 AI 开发者容易接入”。

主要欠缺如下。

### 1. 缺少 AI Developer Kit

建议新增独立目录：

```text
docs/ai/
sdk/
examples/
schemas/
```

至少提供：

- MCP 启动与连接说明
- 令牌获取流程
- 权限档位说明
- 完整工具目录
- 自动生成的 JSON Schema
- TypeScript 客户端示例
- Python 客户端示例
- Claude Desktop 配置示例
- VS Code / Codex 类客户端示例
- 从创建方块到构建 JAR 的完整示例
- 错误码和重试策略

### 2. MCP 列表分页能力不足

当前 `list_mod_elements` 在 MCP 适配层强制使用：

- `page = 1`
- `pageSize = 200`

这与项目准备验证的 2,000 元素工作区目标不匹配。大型项目中的 AI 无法可靠遍历全部元素。

建议所有列表工具统一支持：

- `cursor`
- `limit`
- `sort`
- `filter`
- `fields`
- `includeSummary`
- `nextCursor`

并避免 AI 为了获取少量字段而读回完整元素载荷。

### 3. 缺少通用原子变更计划

目前单工具已有预览和修订控制，但复杂 AI 任务通常会同时涉及：

- 创建元素
- 修改 Procedure
- 新增语言键
- 添加纹理引用
- 更新标签
- 运行校验

建议提供统一能力：

```text
plan_workspace_changes
preview_workspace_plan
apply_workspace_plan
```

一个计划应包含：

- 多个有序操作
- 语义差异
- 受影响引用
- 预计生成文件
- 所需权限
- 是否需要用户确认
- 统一幂等键
- 统一回滚点

这样可以避免 AI 执行到一半失败后留下逻辑上不完整的项目。

### 4. 长任务应支持事件，而不仅是轮询

当前 AI 可以通过 `get_task` 查询任务，但构建、服务端和 Minecraft 启动可能持续很长时间。

建议支持：

- 任务进度事件
- 日志流
- 诊断事件
- 用户确认请求事件
- 任务取消
- 重连后恢复订阅
- 最终 JUnit 或结构化结果

### 5. 缺少明确的协议版本策略

当前 MCP 服务报告版本 `0.1.0`，但还需要正式定义：

- 工具集版本
- Schema 版本
- 最低客户端版本
- Capability negotiation
- 字段弃用周期
- 兼容性测试基线
- 新旧版本工具并存策略

### 6. 缺少公开 AI 评测集

建议建立 `ai-evals`，至少覆盖：

1. 创建一个可食用物品
2. 创建方块并绑定 Procedure
3. 重命名变量并修复引用
4. 修改 Loot Table
5. 运行构建并根据错误自动修复
6. 制造并处理修订冲突
7. 尝试越权访问并确认被拒绝
8. datagen 预览后取消
9. datagen 预览后发布
10. 恢复到 AI 修改前的恢复点

`build.gradle` 已存在 MCP conformance server 入口，但当前公共 Actions 没有持续运行这项验证。

### 7. 需要补充 AI 特有安全威胁模型

现有本机权限模型较好，但还应补充：

- 工作区文本中的 Prompt Injection
- 模组描述或语言文件诱导 AI 调用工具
- 恶意插件伪造诊断
- 构建日志包含不可信指令
- AI 自动读取外部文件
- 工具结果过大导致上下文截断
- AI 重复提交导致非幂等修改

## 6.3 AI 友好度结论

| 层面 | 评价 |
|---|---|
| AI 架构 | 很好 |
| 本地权限与安全 | 很好 |
| 结构化修改能力 | 较好 |
| 大型项目支持 | 一般 |
| 第三方接入文档 | 不足 |
| SDK 和示例 | 不足 |
| 公开兼容性验证 | 不足 |

**结论：Copperbench 对 AI“底层非常友好”，但对 AI 开发者“尚未开箱即用”。**

---

# 七、从普通用户角度看易用度如何

## 7.1 产品内工作流方向合理

用户文档中已经包含一些很有价值的易用性设计：

- 新建和迁入工作区走统一流程
- 保留 `.mcreator` 兼容性
- 使用“版本 / 恢复点”而不是直接暴露 Git 术语
- AI 操作有权限档位和明确审批
- datagen 必须预览后才能发布
- Java 插件默认关闭
- 支持国内 Gradle、Maven Central 和 Minecraft 库镜像
- 不强制账户
- 默认不发送遥测
- 资源包准备不会自动启动 Minecraft
- 可以退回旧版 Swing 工作区

这些设计说明项目对非 Java 创作者的风险和网络问题有实际考虑。

## 7.2 当前最大的用户问题：实际上无法正常下载

当前唯一的 `v0.1.0` Release 仍是草稿，`published_at` 为空。

Release 说明声称应提供：

- Windows EXE
- Windows ZIP

但 GitHub API 中实际附加的只有：

- `SHA256SUMS.txt`
- `LICENSE.txt`

没有安装包，也没有 ZIP。

这意味着即使产品内部操作已经相对完整，普通用户仍无法走完最基本的：

> 找到项目 → 下载 → 安装 → 新建工作区

因此，当前“产品内易用度”与“端到端用户易用度”差距非常大。

## 7.3 Release 与源码状态已经发生漂移

当前草稿 Release 仍然声称：

- G7 尚未通过
- 第一方元素只有 Block、Item、Recipe、Procedure

但当前源码文档表示：

- G7 已通过
- Stage 9 已增加 Function、Loot Table、Advancement 开发预览

这会导致用户无法判断下载包、源码和文档分别处于什么状态。

## 7.4 包体积和签名问题会显著影响首次体验

项目机器可读证据中的包体积约为：

- 安装包：603 MB
- ZIP：775 MB

并且二进制未签名。

未签名意味着普通 Windows 用户会遇到 SmartScreen 警告；超大包体则会提高下载失败、更新成本和国内网络使用成本。

项目可以保留离线完整包，但建议同时提供：

- **Full Offline**：包含 JDK、JCEF、Gradle 发行版和常用缓存
- **Standard**：包含运行时但不预置全部构建缓存
- **Portable**：便携 ZIP
- 后续可选的差分更新包

由于项目明确暂不购买 Authenticode，可以通过以下方式尽量补偿信任问题：

- SHA-256
- SBOM
- SLSA provenance
- 构建提交号
- VirusTotal 结果
- 可复现构建说明
- 下载页明确展示签名状态
- 首次启动说明为什么会出现 SmartScreen

## 7.5 文档入口存在直接断链

README 指向：

```text
docs/build/windows-clean-build.md
```

但当前主分支的 `docs` 目录中没有 `build` 子目录，该文件直接返回 404。

这会直接阻断贡献者和高级用户建立开发环境。

## 7.6 还需要补充的用户功能

### 首次启动向导

建议首次启动引导完成：

1. 系统要求检查
2. 磁盘空间检查
3. 网络区域与镜像选择
4. Gradle 缓存位置
5. 示例工作区选择
6. AI 权限说明
7. 插件安全说明
8. 第一次构建测试

### System Doctor

提供一键检测：

- Windows 版本
- JCEF 是否可用
- 磁盘和内存
- 工作区目录权限
- Gradle 缓存状态
- Fabric 和 NeoForge 仓库连通性
- 国内镜像可用性
- Java 插件状态
- MCP 端口与客户端连接
- Windows Defender 或杀毒软件拦截

并允许一键导出脱敏诊断包。

### 明确展示功能支持状态

在新 UI 中，每一种元素和操作都应显示：

- 正式支持
- 开发预览
- 只读
- 旧版编辑器可用
- 当前生成器不支持
- 当前版本不支持

对于只读类型，应直接提供：

> 在旧版编辑器中打开

而不是让用户进入页面后才发现无法保存。

### 崩溃恢复与问题报告

建议提供：

- 上次异常退出恢复
- 最近恢复点
- 日志脱敏
- 环境摘要
- 工作区最小复现导出
- 一键生成 GitHub Issue 内容
- 用户明确同意后附加诊断文件

### 用户视角结论

Copperbench 的产品内交互设计有较好的基础，但端到端使用体验目前被发布链路拖累。

可以概括为：

> **打开之后可能比预期好用，但普通用户目前很难安全地获得并打开它。**

---

# 八、工程与发布层面的关键风险

## 8.1 主分支 CI 失效

默认分支为 `main`，测试工作流监听 `master`。这是必须立即修复的 P0 问题。

## 8.2 发布工作流仍是上游 MCreator 工作流

当前 `deploy.yml` 会检查仓库名称。如果不是官方 MCreator 仓库，就主动 `exit 1`。

它还包含：

- Linux、Windows、macOS 全平台任务
- MCreator 官方签名和云密钥
- MCreator 官网下载链接
- MCreator changelog 链接

这与 Copperbench 当前“Windows 11、GitHub、未签名”的发布范围完全不匹配。

应将该文件移到：

```text
.github/workflows/upstream-deploy-reference.yml.disabled
```

或删除，并创建 Copperbench 自己的 Windows 发布流程。

## 8.3 发行证据来自脏工作树

2026 年 8 月 23 日的包对齐证据明确记录：

```text
worktreeChangesPresent: true
```

即该安装包不是从完全干净、可复现的 Git 提交构建。

这会造成：

- 无法确定二进制准确对应哪些源码
- Tag 与安装包内容可能不一致
- 后续无法可靠复现相同哈希
- 用户无法审核变更

正式发布流程必须在检测到脏工作树时失败。

## 8.4 项目状态存在多个真相来源

目前状态信息分别存在于：

- README
- PRD
- PRD-NEXT
- PRD-STAGE-9
- remaining-work
- user README
- ReleaseManifest
- Release 草稿
- 测试记录
- Evidence JSON

这些文件部分已经产生状态漂移。

建议建立单一机器可读源：

```text
product-status.json
release-manifest.json
capability-matrix.json
```

然后自动生成：

- README 功能表
- 用户文档支持表
- MCP 查询结果
- Release Notes
- GitHub Pages 状态页

## 8.5 GitHub 未正确识别许可证

仓库自述为 GPL-3.0 衍生项目，但 GitHub API 当前显示许可证为 `NOASSERTION`。

建议检查：

- 根目录标准 `LICENSE`
- SPDX 标识
- `package.json`
- Gradle 项目元数据
- Release 附件
- 上游附加许可说明与 GPL 主许可的关系

---

# 九、建议改进路线图

## P0：先建立可信的构建与发布闭环

在继续大规模扩展模组元素前，应优先完成以下事项。

### P0-1：修复主分支 CI

CI 至少应包含：

- Java 编译和单元测试
- `ui-core` 测试
- `ui-shell` 构建
- 快速 Playwright 测试
- MCP conformance
- Schema 兼容测试
- 文档链接检查
- 许可证与品牌检查
- 测试报告上传

所有 Gradle 命令统一使用：

```text
./gradlew
gradlew.bat
```

不要混用 runner 上的系统 `gradle`。

大型生成器构建、Minecraft 启动和 Windows JCEF 测试可放到 nightly 或手动门禁，不必让每个 PR 都承担全部成本。

### P0-2：创建 Copperbench 专用 Windows 发布工作流

发布工作流应：

1. 仅允许从 Tag 或手动批准触发
2. 验证工作树和提交干净
3. 运行必要测试
4. 构建 EXE、ZIP 和 MSIX
5. 生成 SHA-256
6. 生成 SBOM
7. 生成 provenance
8. 附带许可证和源码提交信息
9. 上传所有文件到同一个 GitHub Release
10. 验证 Release 中实际存在二进制后才能发布

### P0-3：发布与当前源码一致的新预览版

当前 `v0.1.0` 草稿应重新生成或废弃。

新预览版必须明确：

- 对应 Git 提交
- 正式支持与开发预览元素
- G7 和 Stage 9 实际状态
- 支持的版本矩阵
- 未签名说明
- 已知限制
- 安装包与 ZIP 的实际下载文件
- 哈希和构建证明

### P0-4：修复文档入口

至少新增或恢复：

```text
docs/build/windows-clean-build.md
docs/build/development-setup.md
docs/build/release-process.md
docs/user/getting-started.md
docs/user/troubleshooting.md
docs/ai/getting-started.md
```

并在 CI 中运行 Markdown 链接检查。

---

## P1：完成现有创作闭环，而不是立即扩范围

### P1-1：关闭 Stage 9 当前门禁

- Function 专用诊断和标签
- Loot Table 专用结构化编辑器
- Advancement criteria 和循环检查
- 语言 CSV/JSON 导入导出
- 八生成器黄金编译
- 全轨专用服务端测试
- 大工作区性能门禁
- JCEF 和 Windows 11 干净环境验证

### P1-2：发布 AI Developer Kit

交付：

- 工具文档
- Schema
- 示例客户端
- 连接配置
- 权限说明
- 错误码
- 版本策略
- AI 评测集

### P1-3：补充 MCP 大型项目能力

- Cursor 分页
- 字段投影
- 任务事件
- 原子批量计划
- 幂等操作
- 会话客户端列表
- 一键吊销
- Capability negotiation

### P1-4：完善首次使用体验

- 首次启动向导
- System Doctor
- 示例模组
- 10 分钟入门教程
- 一键诊断包
- 功能支持状态徽标

### P1-5：建立插件兼容矩阵

固定若干代表性 A/B/C 类插件，在每次更新上游后自动验证：

- 安装
- 启动
- 打开工作区
- 执行插件功能
- 关闭
- 升级
- 插件移除
- 异常隔离

---

## P2：扩大模组开发能力与生态

在发布闭环和现有 Stage 9 稳定后，再逐步增加：

- Living Entity
- GUI/Menu
- Tool/Armor
- Worldgen/Structure
- Effect/Particle
- Networking/Keybind
- GameTest 创作器
- 依赖和 Mixin 管理
- 生成源码与节点调试映射
- 语义差异和外部 Git 协作
- Full Offline 与 Standard 双发行包
- 可选更新检查
- 完整键盘、屏幕阅读器和高 DPI 支持

---

# 十、建议创建的 GitHub Issues

| Issue | 标题 | 核心验收条件 |
|---|---|---|
| CI-001 | 修复 `main` 分支构建与测试门禁 | `main` push/PR 自动触发，核心测试全部可见 |
| CI-002 | 增加 MCP conformance 和 Schema 兼容测试 | 每次 PR 验证工具 Schema 和版本兼容 |
| REL-001 | 替换上游 MCreator 发布工作流 | 不再检查官方仓库，不包含 MCreator 官网链接 |
| REL-002 | 建立干净 Tag 的 Windows 可复现发布 | 脏工作树直接失败，Release 附带二进制、哈希、SBOM |
| DOC-001 | 修复失效构建文档和全仓链接 | README 无 404，CI 自动检查链接 |
| STATUS-001 | 建立单一机器可读功能状态源 | README、用户文档和 Release Notes 自动生成 |
| DEV-001 | 完成 Stage 9 八生成器黄金矩阵 | 七类元素在八套生成器中完成规定构建验证 |
| DEV-002 | 深化 Function、Loot Table、Advancement 编辑器 | 不再只提供通用字段 CRUD |
| AI-001 | 发布 Copperbench AI Developer Kit | TS/Python 示例可连接并完成创建、预览、构建 |
| AI-002 | MCP 增加分页、事件和批量计划 | 2,000 元素工作区可完整遍历，多步操作可原子提交 |
| UX-001 | 增加首次启动向导和 System Doctor | 新用户能完成环境检查和首个构建 |
| UX-002 | 显示正式、预览、只读和旧版支持状态 | 用户在编辑前即可知道能力边界 |
| QA-001 | 完成大型 Procedure、引用性能和 a11y 门禁 | 满足项目定义的性能目标并完成键盘审计 |
| PLUGIN-001 | 建立 A/B/C 插件自动兼容矩阵 | 每次上游升级有机器可读插件报告 |

---

# 十一、公开 Beta 的建议准入条件

Copperbench 达到以下条件后，才建议对外称为 Public Beta：

1. `main` 分支所有必需检查持续通过
2. GitHub Release 中实际存在可下载 EXE、ZIP 或 MSIX
3. 二进制来自干净 Tag，附带提交号、哈希、SBOM 和 provenance
4. README、用户文档和 Release Notes 不再互相矛盾
5. Stage 9 支持范围在八套生成器上完成规定验证
6. 专用服务端、datagen 和 GameTest 有全轨结果
7. 500 节点 Procedure 和 10,000 引用性能门禁通过
8. 真实 JCEF 与干净 Windows 11 安装测试通过
9. MCP conformance、安全和并发测试进入 CI
10. 新用户可根据入门文档在不修改源码的情况下完成首个模组构建
11. 所有只读或不支持类型在 UI 中有明确提示和旧版出口
12. 全仓文档链接检查无错误
13. 有公开 Issue 模板、诊断包和 Beta 反馈流程
14. 至少完成一轮外部测试者的真实工作区验证

---

# 十二、最终评价

## 对模组开发者

Copperbench 已经能支持基础且结构化的模组创作，并在版本轨道、资源管理、历史、AI 自动化方面展现出明显优势。

但高级模组元素覆盖、数据编辑器深度、大型 Procedure、依赖配置和调试体验仍不足，因此尚不能全面替代 MCreator 与 IDE 组合。

## 对 AI 开发

Copperbench 的架构非常适合 AI：

- 真实 MCP
- 结构化 IR
- Schema
- 预览与提交分离
- 修订冲突
- 恢复点
- 审计
- 权限分级

这些基础比单纯让 AI 修改生成源码可靠得多。

当前欠缺的主要是 AI 开发者产品化：SDK、示例、分页、事件、批量事务、版本兼容和公开评测。

## 对普通用户

产品内部已经有较合理的交互和安全设计，但公共下载安装、文档入口、发行一致性和签名信任问题严重拉低了整体易用度。

现阶段最大的问题不是“用户会不会使用”，而是“用户能不能可靠地获得一个与当前源码一致的版本”。

## 总体建议

Copperbench 值得继续开发，且不存在明显的“闭门造车、最终只是无用轮子”问题。它真正有区分度的部分是：

> **将可视化模组开发、结构化 AI 自动化、版本历史和多加载器工作流放入同一个受控领域模型。**

下一阶段不应优先继续堆积更多功能，而应暂时冻结范围，按以下顺序推进：

1. **修复 CI**
2. **重做发布链路**
3. **发布真实可下载预览版**
4. **统一功能状态和文档**
5. **关闭 Stage 9 当前门禁**
6. **发布 AI Developer Kit**
7. **再扩展高价值模组元素**

只要先补齐“可信构建—持续验证—公开发行—用户反馈”这一闭环，Copperbench 就有机会从技术上有趣的分支，转变为真正可持续使用的独立模组开发工具。
