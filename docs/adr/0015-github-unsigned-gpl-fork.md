# ADR-0015：公开身份为 Copperbench，分发仅 GitHub 未签名 GPL 衍生版

- 状态：已接受
- 日期：2026-08-21

## 决定

Copperbench 是公开产品名，不再当作“待替换的临时开发名”。首发公开分发是 GitHub 上的 GPL-3.0 衍生仓库与 GitHub Releases，不部署独立产品网站，不购买域名，不上架应用商店，不购买 Windows Authenticode 证书。

`dev.copperbench.studio` 只是反向 DNS 产品 ID，不表示拥有 `copperbench.studio` 或任何网站。支持入口是公开 GitHub 仓库的 Issues / Releases，而不是自有域名。

本决定不是律师出具的商标意见。项目政策是：遵守 GPL-3.0 与上游归属；不得把 Pylo / MCreator 商标名称或 Logo 当作本产品品牌；Minecraft 仅出现在次要兼容说明中。

## 原因

项目明确不做商业化。商业 GA 才需要的域名、商店身份、商标注册和付费代码签名，对 GitHub 公开衍生版没有收益。未签名安装包会触发 Windows SmartScreen 警告，这是接受的代价。

jsign 7.4 仍留在 Windows 导出配方中。以后若提供 `WIN_CERT_*` 与 `codesign-chain.pem`，构建可以签名；当前发布门禁不要求签名。

## 后果

- G7 不因本决定变为 `passed`。Hyper-V 客户机 GUI 常驻仍未宣称。
- 发布说明以 `PUBLIC_DISTRIBUTION_GITHUB_ONLY` 和 `CODE_SIGNING_UNSIGNED_GITHUB` 记录政策，不再把品牌/域名/证书列为待补机器证据。
- 公开仓库是 <https://github.com/Lotulune/Copperbench>。以后若更换托管地址，必须同步 README、BRANDING 与本 ADR。
- 若以后要做商店上架、独立网站或付费签名，必须新增 ADR 并重开对应门禁。

## 依据

- [`compliance/BRANDING.md`](../../compliance/BRANDING.md)
- [`compliance/SOURCE_DISTRIBUTION.md`](../../compliance/SOURCE_DISTRIBUTION.md)
- 上游 [MCreator 许可与商标说明](https://github.com/MCreator/MCreator/blob/2026.2.33518/README.md#license-and-trademark)
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
