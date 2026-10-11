# Copperbench 开发环境

## 前置条件

- Windows 11 x64 用于桌面宿主和打包验证；普通 Java 测试可在 Linux CI 运行。
- JDK 25，推荐 JetBrains Runtime with JCEF。
- Node.js 22、npm、Git 和 PowerShell 7。
- 首次构建需要访问 Gradle、Maven 和 Minecraft 依赖源。

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

按本次改动选择下面的命令；[贡献指南](../../CONTRIBUTING.md#choose-verification-for-the-change)列出了各层对应的检查。`gradlew test` 已通过资源任务构建 UI，同一工作区不需要紧接着再运行一次 `npm run build`；仅改前端、没有运行 Gradle 时，仍应执行前端生产构建。PR 的自动分流规则见[CI 检查选择](../../CONTRIBUTING.md#how-ci-selects-checks)。

```powershell
.\gradlew.bat --no-daemon test javadoc
npm test --prefix ui-core
npm run build --prefix ui-shell
Push-Location ui-shell
npx playwright test e2e/scenarios.spec.ts e2e/new-workspace.spec.ts
Pop-Location
node scripts/verify-markdown-links.mjs
```

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
npx --prefix ui-shell playwright install chromium
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
