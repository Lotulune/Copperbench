# 第三方通知基线

## MCreator

- 来源：<https://github.com/MCreator/MCreator>
- 固定 tag：`2026.2.33518`
- 固定提交：`361429609b772039a3eb9ab81662c25b225f1d0d`
- 基础许可：`GPL-3.0-only`。标准正文位于根目录 `LICENSE.txt`；上游 Section 7 额外许可、模板例外、商标与 mappings 通知位于 `LICENSE-ADDITIONAL-TERMS.md`。
- 归属：Copyright 2020 Pylo and contributors。

本产品是 MCreator 的独立衍生分支，不受 Pylo、MCreator、Mojang 或 Microsoft 官方认可。发行包不得包含 Pylo/MCreator 商标名称或 Logo，但应在“关于/许可证”中说明派生来源。

MCreator 固定源码包含 `license/` 下 32 份第三方许可/通知文件。首次源码导入和每次上游同步必须原样保留该目录，并由打包测试确认它进入发行物的许可证入口。

## MCreator Fabric Generator

- 来源：<https://github.com/Goldorion/Fabric-Generator-MCreator>
- 固定 tag：`26.1.2-2026.2-2.8`
- 固定提交：`abfe19329126b679a26baafe5cade5a75d455528`
- 作者/归属：Goldorion、NerdyPuzzle、Spectrall 及贡献者。
- 仓库许可证据：固定 tag 的 `LICENSE` 与 `src/main/resources/LICENSE` 均为同一 GPL-3.0 文本，SHA-256 为 `84771a42604a9b025460094c46fd64768e319b557b7dffdaf80a0137a9f2f243`。

MCreator 插件页面曾显示 LGPLv3，与固定仓库文件不一致。产品只依据被导入提交中的实际许可证文件，保留冲突记录，不把插件页元数据当作再许可依据。

## Minecraft mappings

上游声明部分生成器使用 Microsoft 官方 mappings。完整且未修改的 mappings 不得随源码或安装包重新分发；构建/生成流程必须保留上游提示与适用的 Minecraft EULA 链接。

## 构建与运行时组件

固定 MCreator 源码的 `license/` 目录包含 Apache Commons、Log4j、Blockly、FlatLaf、FreeMarker、Gradle、Gson、Guava、JGit、JNA、OpenJDK、RSyntaxTextArea、SnakeYAML Engine 等依赖的许可或通知。此文件仅作为阶段 0 索引，不替代完整许可证正文，也不表示未来 JCEF/React 依赖已完成审计。

## 修改记录要求

- 任何修改后的上游文件必须在项目变更记录中注明修改日期与目的。
- 发行物必须同时提供本产品源码、构建脚本、依赖锁定信息和许可证入口。
- 不得删除第三方版权、作者列表、文件头或无担保声明。
