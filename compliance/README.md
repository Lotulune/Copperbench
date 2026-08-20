# 合规基线

本目录保存来源、许可、品牌和源码分发证据。它不是法律意见。公开身份与分发政策见 [`BRANDING.md`](./BRANDING.md) 与 [ADR-0015](../docs/adr/0015-github-unsigned-gpl-fork.md)：Copperbench GitHub 未签名 GPL 衍生版。

- [`baseline.lock.json`](./baseline.lock.json)：不可变来源、文件哈希和工具链版本。
- [`branding-assets.lock.json`](./branding-assets.lock.json)：23 个替换资产的上游与当前 SHA-256。
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)：上游与第三方归属入口。
- [`BRANDING.md`](./BRANDING.md)：必须替换、必须保留的品牌项，以及 GitHub 未签名公开分发政策。
- [`SOURCE_DISTRIBUTION.md`](./SOURCE_DISTRIBUTION.md)：GPL 对应源码发布规则。
- [`CHANGES-FROM-UPSTREAM.md`](../CHANGES-FROM-UPSTREAM.md)：相对固定 MCreator/Fabric 来源的产品修改清单。
- [`licenses/`](./licenses/)：从固定提交复制的许可证快照。

机器可读运行结果保存在 `evidence/stage-0/`，其中包括产品产物锁、JBR 逐文件锁、Fabric 内容一致性、测试、启动和打包日志；不得用手工勾选替代。
