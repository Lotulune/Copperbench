# Copperbench 开发环境

[English](development-setup.en.md) · [贡献者一页入口](contributor-quickstart.md) · [贡献与验证选择](../../CONTRIBUTING.md#choose-verification-for-the-change)

第一次贡献先按[一页入口](contributor-quickstart.md)选择改动层；本页用于补齐对应环境和排查启动问题。

## 前置条件

| 改动层 | 所需环境 |
| --- | --- |
| 文档与状态检查 | Node.js 22；Git 用于变更发现。新计划入口还需 Python 3.11+，不需要 JCEF 或显示环境。 |
| Java／共享合同／产品构建 | JDK 25 和 Gradle Wrapper；资源任务构建 UI，还需 Node.js 22、npm 和已准备的 UI 依赖。 |
| 浏览器 UI | Node.js 22、npm；Playwright 场景另需 Chromium。 |
| 原生桌面／安装包 | JBR 25 with JCEF、目标平台及可用显示会话；Windows 脚本需 PowerShell 7。Linux 普通 Java 测试可在 CI 运行，窗口测试另需显示环境。 |
| Python SDK | Python 3.11+；真实 Core／桌面集成还需产品启动器及相应 Java／显示环境。 |

纯文档不要求完整桌面环境。首次依赖准备／构建可能访问 Gradle、Maven 和 Minecraft 源，由开发者明确执行，不自动改代理、安装软件或清全局缓存。

仓库自带 Gradle Wrapper。所有命令必须使用 `gradlew.bat`（Windows）或 `./gradlew`（Linux），不要依赖系统安装的 Gradle。

## 初始化

```powershell
git clone https://github.com/Lotulune/Copperbench.git
Set-Location Copperbench
npm ci --prefix ui-core
npm ci --prefix ui-shell
```

设置当前终端的 JDK 25 后执行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jbr-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
```

## 日常验证

本轮源码增加了薄入口，复用 `scripts/ci/select_checks.py`，不维护第二套路由。默认只列 JSON 计划与环境缺项。

计划也包含 CI 的选择器与发布历史回归；UI/合同选择加入性能工具/源码身份及原生观察记录单元测试，Python 选择加入贡献者入口与验收测量工具的回归。缺少相应 Python、Git 或 Node 时保留明确阻塞项；这些检查不自动安装依赖、操作桌面或进行性能采样。先查看计划：

```powershell
python scripts/verify-changed.py --paths docs/user/first-creation.md
python scripts/verify-changed.py --base origin/main --dry-run
```

`--paths` 与 `--base` 二选一；无完整 diff／未知路径时保守全选。环境准备好后才显式执行：

```powershell
python scripts/verify-changed.py --paths docs/user/first-creation.md --run --allow-downloads --output output/contributor-validation/my-change-01
```

`--run` 默认阻止可能下载的 Gradle；`--allow-downloads` 才允许调用既有 Wrapper。工具不会替你 `npm ci`、安装 Chromium 或修改系统设置；缺项只阻塞依赖它的检查。输出须是仓库 `output/` 或 `build/` 内的新目录。报告保留实际命令、退出码、日志、源码摘要及可获得的测试数（缺失为 `null`）。执行失败／超时返回 `1`，环境阻塞返回 `2`，纯计划返回 `0`。详见[贡献者验证入口](../../scripts/performance/README.md#contributor-verification)。这是源码开发工具，不是发布包中的产品 doctor。

按[一页入口](contributor-quickstart.md)选择本次检查；[贡献指南](../../CONTRIBUTING.md#choose-verification-for-the-change)保留各层的手动命令。完成全部实现后统一执行所选计划，已由计划运行的定向检查不再重复。`gradlew test` 已通过资源任务构建 UI，同一份输入不需要紧接着再运行一次 `npm run build`；仅改前端、没有运行 Gradle 时，计划会执行一次前端生产构建。PR 的自动分流规则见[CI 检查选择](../../CONTRIBUTING.md#how-ci-selects-checks)。

如果当前 Windows 终端由受限的宿主进程启动，Java NIO 可能无法创建
Gradle daemon 所需的本地回环管道，并报 `Unable to establish loopback
connection`。这不是 Maven/Gradle 镜像的网络错误。仓库提供了独立进程
入口，将 Gradle 放到不继承宿主进程作业限制的 Windows 进程中运行：

```powershell
pwsh -NoProfile -File .\scripts\run-gradle-external.ps1 --offline help
pwsh -NoProfile -File .\scripts\run-gradle-external.ps1 --no-daemon test --tests dev.copperbench.shell.Stage9NativeJcefAccessibilityTest
```

该入口使用仓库自带的 JBR 25，不修改系统代理、Winsock 或网络适配器；
日志保存在 `.tmp\gradle-external\`。

首次运行 Playwright 前需要安装 Chromium：

```powershell
Push-Location ui-shell
npx playwright install chromium
Pop-Location
```

启动产品外壳：

```powershell
.\gradlew.bat runProductShell
```

## 本地数据

- Copperbench 设置和共享 Gradle 缓存：`%USERPROFILE%\.copperbench`
- 构建输出：`build/`
- UI 输出：`ui-shell/dist/`

不要提交 JDK、缓存、构建输出、签名证书或工作区用户数据。完整的隔离构建方法见 [Windows 干净构建基线](./windows-clean-build.md)。

## 工作区 Gradle 缓存

产品的工作区初始化、Core 构建和 doctor 使用同一缓存目录解析规则，优先级为
显式 JVM 属性 `copperbench.gradle.user.home`、`COPPERBENCH_GRADLE_USER_HOME`、
`GRADLE_USER_HOME`，最后是产品缓存目录下的 `gradle`。
相对路径在产品进程的工作目录下转为绝对路径，后续切换到工作区执行不会改变其含义。
这些配置不改变构建 Copperbench 本身的 Gradle Wrapper 参数。

默认仍会复用其他本地缓存及安装包内已有的 Gradle distribution。需要隔离下载时，
为启动的产品进程设置 `COPPERBENCH_GRADLE_REUSE_EXTERNAL=false`，并指定全新的
Gradle 缓存目录；该选项只阻止跨目录借用，保留所选目录自身的复用能力。
`true` 恢复默认行为，其他非空值会被拒绝。程序不会清空任何已有缓存。

`get_workspace_environment.execution.gradle` 会报告 `userHome`、`userHomeSource`
和 `reuseExternalDistributions`，doctor 使用同一后端事实。它们说明实际配置，
不证明缓存内容完整或下载已经通过摘要校验。冷/热缓存安装回放及记录方式见
[M3 固定任务卡](../testing/agent-readiness-m3-task-card.md#cold-and-warm-gradle-cache-runs)。
