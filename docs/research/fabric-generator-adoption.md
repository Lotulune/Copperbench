# Fabric 生成器内置调研

- 调研日期：2026-08-16
- 结论：可以作为第一方内置生成器采用，但必须固定提交并完成许可与能力审计

## 已确认

- Fabric 生成器仍是独立社区项目，并未进入 MCreator 主仓库。
- 当前仓库目标版本为 Minecraft 26.1.2，对应 MCreator 2026.2。
- 生成器复用了官方 NeoForge 生成器的代码，且部分 Forge 专属能力仍被禁用。
- 目标分支的 `LICENSE` 与 README 声明 GPL-3.0；MCreator 插件页面却显示 LGPLv3，发布元数据存在冲突。
- 仓库要求分发修改版本时说明变更，且不得暗示原作者背书。

## 采用要求

1. 固定首次导入的提交 SHA，不直接依赖下载页中的滚动 ZIP。
2. 保存目标提交的 `LICENSE`、README、版权信息和来源 URL。
3. 扫描模板与其他文件的独立许可头，特别关注来自官方 NeoForge 生成器的部分。
4. 建立 Fabric/NeoForge 通用模组元素能力差异表，不能把禁用字段静默丢弃。
5. 将社区生成器升级转化为上游变更审查，不自动覆盖本产品维护分支。
6. 对可独立复用的修复优先提交回原项目，降低长期分叉成本。

## 依据

- [MCreator Fabric Generator 仓库](https://github.com/Goldorion/Fabric-Generator-MCreator)
- [目标版本分支 LICENSE](https://github.com/Goldorion/Fabric-Generator-MCreator/blob/26.1.x-2026.2/LICENSE)
- [MCreator 官方插件页面](https://mcreator.net/plugin/64512/mcreator-fabric-generator)
