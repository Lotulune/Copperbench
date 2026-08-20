# 阶段 0 基线执行记录：2026-08-16

- 需求：G0、`FR-COMPAT-01`、`NFR-UI-09`
- 环境：Windows 11 专业版 x64，版本 `10.0.26100`
- Java：JBR/OpenJDK `25.0.3`
- 结论：**本机 G0 按 Windows 11 证据关闭**。2026-08-20 起 Windows 10 不再作为条件项。产品 Git 初始化仍待授权。

## 已验证

- MCreator tag `2026.2.33518` 指向 `361429609b772039a3eb9ab81662c25b225f1d0d`。
- Fabric Generator tag `26.1.2-2026.2-2.8` 指向 `abfe19329126b679a26baafe5cade5a75d455528`。
- 固定 tag 的许可证、Gradle wrapper、构建文件和插件元数据已记录 SHA-256。
- MCreator 使用 Java 25 toolchain 与 Gradle 9.6.0；Fabric Generator 使用 Java 25 与 Gradle 9.7.0。
- MCreator 固定源码的 `license/` 包含 32 份第三方许可/通知文件。
- 干净 Gradle 缓存成功下载并启动 Gradle 9.6.0，`compileJava` 与 `compileTestJava` 成功。
- Fabric Generator 使用独立缓存完成两次 `clean jar`；两次 `generator-fabric-26.1.2-2026.2.zip` 的 SHA-256 均为 `dcee1b6550cebf72e9f8657f1ffca1c6b48563da9caadd2b1a786af58593dd41`。
- 来源级同步/回退演练从 `2026.2.33218` 快进 6 个提交到 `2026.2.33518`，涉及 47 个文件；回退后的 tree `1ccb400f157e93734acbce2acaeec0962822896e` 与导入前一致。
- 产品源码已导入，临时身份固定为 Copperbench `0.1.0` / `dev.copperbench.studio` / `dev.copperbench`。
- 23 个受保护视觉资产全部替换；ZIP 品牌与结构扫描通过。
- 隐式新闻、更新、分析和 Discord 连接关闭，用户目录隔离为 `.copperbench`。
- Windows ZIP、NSIS 安装器和 MSIX 均成功生成；主 JAR Manifest 保留 MCreator core 来源版本。
- 从发行目录启动 `copperbench.exe` 后，内置 `javaw.exe` 主窗口标题为 `Copperbench 0.1.0`，进程正常响应并完成清理。
- 2026-08-17 接收用户提供的 Copperbench 图标母版后，23 个平台品牌资产已重新派生并锁定；ZIP、NSIS、MSIX 与真实发行启动门禁已全部重跑通过。

## G0 测试结果

- 隔离缓存 `clean` + 产品/基础单元测试：6/6 通过，25.602 秒。
- UI 集成测试：29/29 通过，96.171 秒；覆盖 JCEF、对话框、图像编辑器、中英文 locale 以及 Fabric/Addon 模组元素 UI。
- JCEF 实际初始化版本：`137.0.17.1142.68de80bc86de497c8d0632ad0f8fe33625b33bff`。
- 首轮 UI 测试发现 FlatLaf Windows 本机库不在测试 classpath；加入标准 FlatLaf 测试运行时后，原失败用例与完整 UI 组均通过。
- Fabric 可复现源产物与发行包中重打包插件的 archive SHA 不同，但 704 个条目的内容树完全一致：`24d41930b994da61ba2bb944e4681aa7195550dee2c851617eff0e10d7b27d33`。

## Windows 产物

| 产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| ZIP | 324,459,854 | `0e2c033933dd548a6759cad7e543c166b041b7a1f0732dba53bbfee6c07b5471` |
| NSIS EXE | 308,968,559 | `5105cf813ca8e8192333c387325c514dbd1ea5029e2bdb053f627094379bfbba` |
| MSIX | 332,558,292 | `55a80c63a4831f9bb7c64812897609e510b42c54ec8bbf1b583204220ea9ad53` |

EXE 与 MSIX 是未签名开发产物，不得当作公开发布候选。JBR `25.0.3+1-b329.124-jcef` 的 551 文件内容树 SHA-256 为 `01412c80bb06dd91726321cc59cdb13bb7cfebf3362ebdff3ae2169f6b626d69`。

## 条件项与跨阶段诊断

- Windows 10 x64 没有可用 runner，因此相同的干净构建、启动和安装器 smoke 尚未实测；严格 G0 保持条件通过。
- 当前目录不是 Git 仓库。上游来源级同步/回退演练已通过，但创建产品分支和提交需要用户显式授权。
- 加载 Fabric 插件的上游完整 `test` 在旧工作区转换 fixture 遇到 Fabric 不支持的过程块，生成器工作区同步还出现长时间依赖解析。这是 G1/G2 的兼容与生成器矩阵输入，不计作 G0 基础失败，也没有被隐藏。

## 复现命令

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.4\jbr'
$env:GRADLE_USER_HOME='C:\tmp\gradle-g0-copperbench'
$env:JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=3067 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=3067'
.\gradlew.bat --no-daemon --no-build-cache clean test --tests 'dev.copperbench.*' --tests 'net.mcreator.unit.*'
.\gradlew.bat --no-daemon --no-build-cache test --tests 'net.mcreator.integration.ui.*'
.\gradlew.bat --no-daemon exportWindowsZip buildInstallerWin64 buildMsixWin64
.\scripts\stage0\Test-DistributionBrand.ps1 -PackagePath '.\build\export\Copperbench 0.1.0 Windows 64bit.zip'
.\scripts\stage0\New-G0Evidence.ps1
```

机器可读结果见 [`evidence/stage-0/2026-08-16/windows-11-baseline.json`](../../evidence/stage-0/2026-08-16/windows-11-baseline.json)。

固定来源逐文件验证见 [`source-verification.json`](../../evidence/stage-0/2026-08-16/source-verification.json)，可复现 Fabric Generator 产物见 [`artifacts/generator-fabric-26.1.2-2026.2.zip`](../../evidence/stage-0/2026-08-16/artifacts/generator-fabric-26.1.2-2026.2.zip)。最终汇总见 [`windows-11-baseline.json`](../../evidence/stage-0/2026-08-16/windows-11-baseline.json)，产物哈希见 [`product-artifacts.lock.json`](../../evidence/stage-0/2026-08-16/product-artifacts.lock.json)。
