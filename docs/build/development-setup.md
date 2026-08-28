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
