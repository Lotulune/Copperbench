# 品牌替换与保留清单

- 状态：**公开身份已锁定为 Copperbench；分发渠道是 GitHub 未签名 GPL 衍生版**
- 对应：G0、`FR-COMPAT-01`、[ADR-0015](../docs/adr/0015-github-unsigned-gpl-fork.md)

## 公开产品身份

| 字段 | 值 |
| --- | --- |
| 产品名 | `Copperbench` |
| 版本 | `0.1.0` |
| 产品 ID | `dev.copperbench.studio`（反向 DNS 标识，不是网站） |
| 新增 Java 命名空间 | `dev.copperbench` |
| Publisher | `Copperbench Contributors` |
| 用户目录 | `.copperbench` |
| 公开分发 | GitHub 仓库与 GitHub Releases |
| 产品网站 / 域名 | 无 |
| 应用商店 | 无 |
| Windows 签名 | 未签名（政策如此，不是待补证书） |

阶段 0 名称检索未发现显著的软件同名冲突；该结果不是商标法律意见。原始检索输出保存在 [`copperbench-name-search.json`](../evidence/stage-0/2026-08-16/copperbench-name-search.json)。

GitHub 仓库 URL 在仓库实际创建前不得写入产品文案。

## 已确认边界

1. 上游明确要求自定义发行版不得包含 Pylo 或 MCreator 商标名称和 Logo。
2. Minecraft Usage Guidelines 允许在次要兼容说明中提及 Minecraft，但不允许把 Minecraft 作为主导产品名称。
3. `Minecraft Mod Creator` 因此只保留为 PRD 工作标题；产品外壳与公开仓库使用 Copperbench。
4. 不做商业化：不购买域名、不注册商标、不上架商店、不购买 Authenticode。这是项目政策，不是律师意见。

参考：

- MCreator 固定 tag 的 [README 商标说明](https://github.com/MCreator/MCreator/blob/2026.2.33518/README.md#license-and-trademark)
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
- [ADR-0015](../docs/adr/0015-github-unsigned-gpl-fork.md)

## 已完成替换

| 类别 | 上游位置示例 | 处理 |
| --- | --- | --- |
| 应用名与启动标识 | 启动器、窗口标题、About 与配置改为 Copperbench | `ProductIdentity`、UI 集成测试、启动 JSON |
| Logo、图标与启动图 | 23 个受保护资产全部替换并锁定哈希 | [`branding-assets.lock.json`](./branding-assets.lock.json) |
| Pylo 品牌 | `pylo.svg` 不进入主 JAR；About 只保留文字归属 | 包扫描与主 JAR 条目扫描 |
| 安装器/包标识 | EXE、NSIS、MSIX、快捷方式与文件关联使用 Copperbench | Windows 三种产物构建日志 |
| 默认网络与遥测 | 离线 Web API；更新、新闻、分析和 Discord 自动连接强制关闭 | `ProductIdentity.IMPLICIT_NETWORK_SERVICES_ENABLED=false` |
| 用户目录 | 从 `.mcreator` 隔离为 `.copperbench` | 打包启动证据 |

视觉品牌母版为用户在 2026-08-17 提供的 AI 生成 PNG，已按原始字节保存为 [`copperbench-icon-source.png`](../assets/branding/copperbench-icon-source.png)，SHA-256 为 `8667e93934d9d6a9f641e15d7e183655f3a9de6201fc969a420984a96f454688`。[`New-CopperbenchBrandAssets.py`](../scripts/stage0/New-CopperbenchBrandAssets.py) 只执行透明边界裁切、缩放、格式转换和背景合成，不进行生成式修改，并在每次运行后刷新 23 个派生资产的哈希锁。

## 必须保留或兼容的项

- “基于 MCreator `2026.2.33518` 的独立衍生版本”及上游版权/许可证归属。
- 插件兼容所需的上游版本标识，不伪装成其他官方 MCreator 版本。
- 现有 `net.mcreator` Java 包名和兼容 API 可在内部保留，避免无收益的大规模包重命名破坏插件；它们不得被当作发行品牌。
- 第三方项目、加载器与 Minecraft 的商标仅作兼容性描述，不制作仿官方 Logo 或背书文案。
- GPL-3.0 对应源码与二进制同版本可获取，见 [`SOURCE_DISTRIBUTION.md`](./SOURCE_DISTRIBUTION.md)。

## 公开分发政策（已关闭）

- 最终产品名：Copperbench。
- 域名与商店：不做。
- 签名：GitHub Releases 安装包保持未签名；Windows SmartScreen 可能警告。`jsign` 仅在以后提供证书时可选启用。
- 支持入口：公开 GitHub 仓库。当前仅在来源/许可证上下文链接上游 MCreator。
