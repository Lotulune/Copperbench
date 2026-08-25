# Windows 干净构建基线

## 环境

- Windows 10/11 x64。
- JDK 25；`java` 与 `javac` 必须报告主版本 25。
- Git 与 PowerShell 7（`pwsh`）。
- 至少 8 GB 可用内存；上游测试配置最高使用 4 GB 堆。
- 首次联网构建允许访问 Gradle/Maven/Minecraft 依赖源；离线门禁在阶段 8 使用已缓存依赖单独验证。

不要依赖开发者的默认 Gradle 缓存。每次证据运行使用一个新的绝对路径作为 `GRADLE_USER_HOME`。

## 获取并校验来源

```powershell
git clone --branch 2026.2.33518 --recurse-submodules https://github.com/MCreator/MCreator.git C:\src\mcreator-baseline
git clone --branch 26.1.2-2026.2-2.8 https://github.com/Goldorion/Fabric-Generator-MCreator.git C:\src\fabric-generator-baseline

pwsh -NoProfile -File .\scripts\stage0\Test-Baseline.ps1 `
  -MCreatorSource C:\src\mcreator-baseline `
  -FabricGeneratorSource C:\src\fabric-generator-baseline `
  -OutputPath .\evidence\stage-0\baseline-source-verification.json
```

## 构建与测试

```powershell
pwsh -NoProfile -File .\scripts\stage0\Invoke-CleanWindowsBuild.ps1 `
  -SourcePath C:\src\mcreator-baseline `
  -JdkHome 'C:\Program Files\Java\jdk-25' `
  -GradleUserHome C:\build-cache\mcreator-2026.2.33518 `
  -OutputPath .\evidence\stage-0\windows-clean-build.json
```

在需要本机 HTTP 代理时显式传入 `-ProxyUri http://127.0.0.1:PORT`。脚本只为子进程设置 Java 代理属性，不修改系统代理或全局环境变量。

## 启动与打包

G0 基础与 UI 门禁分别运行，生成器转换/编译矩阵在 G1/G2 单独执行：

```powershell
.\gradlew.bat --no-daemon --no-build-cache clean test `
  --tests 'dev.copperbench.*' --tests 'net.mcreator.unit.*'
.\gradlew.bat --no-daemon --no-build-cache test `
  --tests 'net.mcreator.integration.ui.*'
.\gradlew.bat --no-daemon exportWindowsZip
.\gradlew.bat --no-daemon buildInstallerWin64
.\gradlew.bat --no-daemon buildMsixWin64
```

启动验收必须记录窗口出现、打开/关闭、JCEF 初始化、日志位置和进程清理。打包验收必须扫描安装包中的 MCreator/Pylo 用户可见资产、许可证入口、默认网络调用和源版本元数据。

Windows 打包固定使用 JetBrains Runtime with JCEF `25.0.3+1-b329.124`。逐文件 SHA-256 清单见 [`jbr25-windows-runtime-lock.json`](../../evidence/stage-0/2026-08-16/jbr25-windows-runtime-lock.json)。

## 当前结果与已知问题

2026-08-16 的 Windows 11/JBR 25.0.3 隔离缓存运行通过 6 项产品/基础测试和 29 项 UI 集成测试。JCEF 冷启动等待从 5 秒调整为 30 秒；FlatLaf 标准包加入测试运行时后，Windows 本机窗口库可正常加载。

加载 Fabric 插件后运行上游完整 `test` 会在旧工作区转换 fixture 遇到 Fabric 不支持的过程块，并在生成器工作区同步阶段出现长时间依赖解析。这些用例属于 G1/G2 兼容和生成器矩阵，原始日志保存在 `copperbench-windows-11-clean-test.log`。Windows 10 实机门禁尚未运行。
