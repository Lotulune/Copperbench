# 干净 Windows 11 安装核对清单（朋友机）

主路径已改为本机 Hyper-V，见 [`clean-windows-hyperv-checklist.md`](./clean-windows-hyperv-checklist.md)。本清单仅作备用。

给没有 Visual Studio / IntelliJ / 额外 JDK 的 Windows 11 x64 使用。不要在开发机上勾这项。

## 请带上

- `Copperbench 0.1.0 Windows 64bit.exe`，或同目录的 zip
- 本清单

## 请记录

1. Windows 版本：设置 → 系统 → 关于（例如 24H2 / 26100）
2. 是否 64 位
3. 机器上是否已有 Java、IDE、Git、Android Studio 等开发工具（有的话请写出来）

## 步骤

1. 运行安装器，装到默认目录或你选的目录。不要改系统 PATH。
2. 从开始菜单启动 Copperbench。应出现产品窗口，不应再索要 JDK。
3. 新建一个最简单的模组工作区（Fabric 1.21.1 即可），保存到「文档」下的新文件夹，例如 `Documents\copperbench-friend-test`。
4. 生成工作区。应写出 Java / Gradle 文件，不应崩溃。
5. 关闭软件。确认第 3 步的工作区文件夹还在。
6. 卸载 Copperbench。若询问是否保留设置，保持默认「保留」。
7. 再确认工作区文件夹还在，内容没被删。

## 请交回

- 上面 1–7 每步：成功 / 失败 + 一两句现象
- 失败时的截图或 `用户目录\.copperbench` 下的日志（如果有）
- Windows 版本字符串

不必做：黄金编译、断网构建、签名核验、Windows 10。
