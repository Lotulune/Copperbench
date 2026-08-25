# Stage 8 Release Preview Alignment

本记录对应 `FR-CLOSE-01` 与 `FR-CLOSE-08`，验证对象是 2026-08-23 从当前工作树导出的 Windows 包。

## 已通过

- `gradlew.bat exportWindows --no-daemon` 成功生成 Windows ZIP、NSIS 安装包和 MSIX。
- 当前 `win64` 布局包含 `copperbench.exe`、JDK/JCEF、`copperbench.jar`、许可证、`user/README.md` 和插件目录。
- 源码 `BundledPluginInventory` 的 11 个第一方插件与导出目录的 11 个 ZIP 同名且完全匹配。
- 包内 `user/README.md` 与源码 `docs/user/README.md` SHA-256 相同。
- 安装包与便携 EXE 均为 `NotSigned`，便携 EXE 嵌入 `-Dcopperbench.productShell=true`。
- 使用隔离目录 `D:\Copperbench-ReleasePreview-2026-08-23` 的 unsigned preview 静默安装、启动存活检查通过；没有触碰已有 `D:\Copperbench-ReleasePreview`。
- 经用户授权后，已对原 `D:\Copperbench-ReleasePreview` 执行安全安装演练替换；静默安装、升级、卸载均通过，演练工作区和 `.copperbench` 用户目录均保留。证据：[`windows11-install-rehearsal.json`](../../evidence/stage-8/2026-08-23/windows11-install-rehearsal.json)。

机器可读证据：[`release-package-alignment.json`](../../evidence/stage-8/2026-08-23/release-package-alignment.json) 与 [`unsigned-release-preview.json`](../../evidence/stage-8/2026-08-23/unsigned-release-preview.json)。

## 保持诚实的残留

`verify-stage-8.ps1` 已完整通过，包含源码、schema、插件、offline、既有机器证据和 Windows 11 静默安装/升级/卸载演练。原有 `D:\Copperbench-ReleasePreview` 的注册安装已按用户授权处理并完成验证；旧目录本身仍保留为未注册文件目录，未继续手动删除其中内容。

G7 已通过：Hyper-V 断网场景下客机 `copperbench.exe` 在 10 秒检查点保持运行（`processStartedWhileDisconnected=true`），安装/升级/卸载和数据保留也均通过。VMware 不属于 G7 必需门禁。
