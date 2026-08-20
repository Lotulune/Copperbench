# GPL 对应源码分发规则

每个公开二进制发行物必须能够追溯到同版本的完整对应源码。最低要求如下：

1. 发布不可变源码 tag，并记录所基于的 MCreator/Fabric Generator tag 与提交。
2. 提供构建、测试、打包脚本以及 Gradle wrapper；不得只发布修改补丁。
3. 提供生成该二进制所需的产品源文件、内置插件/生成器源码、UI 源码和 bridge Schema。
4. 原样保留 MCreator `LICENSE.txt`、Section 7 条款、第三方 `license/` 目录、Fabric Generator 许可证和作者归属。
5. 在 GitHub Releases（或同版本源码 tag）和应用“关于/许可证”入口提供源码获取方式、无担保声明和修改说明。不部署独立产品网站。
6. 不重新分发完整且未修改的官方 Minecraft mappings；依赖获取步骤必须遵守上游许可。
7. SBOM、依赖锁文件、源提交和构建证据与二进制使用同一发行版本号。

推荐在同一 GitHub Release 并列提供安装包、源码归档、SBOM、校验和与许可证归档。若通过书面源码要约履行 GPL，必须满足 GPL-3.0 第 6 节要求并保留至少规定期限；项目默认采用 GitHub 网络等价访问，减少分离和失效风险。
