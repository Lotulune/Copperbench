# v37 双平台社区 MCP 兼容复验

用户明确授权在两个专用测试配置中临时加载固定社区插件。实际文件为 jasonjgardner/blockbench-mcp-plugin 1.7.0，来源 commit `b187b4b056f0efafcc573335400ecbb21ad26ecc`，SHA-256 `b97f921968701df4d20103f1e7ab85823341eb2981358392d6f7921eb6d840db`。通过真实 Blockbench 的“从文件加载插件”入口安装，只选择 Allow once；默认用户配置未改变。

| 平台 | 实际产品入口 | 结果 |
|---|---|---|
| Windows | v37 `copperbench.exe` + 随包 Python SDK；EXE SHA-256 `d9fe209ce8da3174648ec979f29bb903215e87bfa2455a31fa6acfc34f27b6eb` | Blockbench 5.1.6；MCP 协议 2025-11-25，97 工具，`tools_available` |
| Ubuntu | 已安装 v37 `/usr/bin/copperbench` + `/opt/copperbench/sdk/python`；JAR SHA-256 `59da390ea54a68e8d925b0ca79a1d9de36dc68915310d994dcff62275b9f66e5` | 相同协议、97 工具、`tools_available`；编辑器检测为 `ready_unverified`，版本为空，不宣称版本识别通过 |

只调用公开 `get_blockbench_environment(probeMcp=true)`，覆盖 initialize、initialized 通知、tools/list，没有调用任何模型工具，不将发现成功作为建模完成。插件按原实现监听 3000 端口，本次连接地址固定为回环 `/bb-mcp`；测试完通过编辑器正常关闭，进程与监听均消失。插件仍只存在于专用测试配置中，下次打开默认配置不会加载它。

Ubuntu 首次探测发生在加载后，返回 `BLOCKBENCH_MCP_UNREACHABLE` / initialization；随后 `ss` 确认 PID 90604 已监听，重新通过同一产品入口探测成功。未改插件或产品网络配置。首次失败回执保留，不能写为首轮即通过。自动更新日志的 DNS 失败与本地 MCP 就绪结果分开记录。

Windows 一次权限按钮 UIA 索引失效后重新观察，再用已聚焦按钮确认。Ubuntu `type_text` 将虚拟机旧剪贴板路径贴入文件选择器；没有打开旧路径，改用可见文件夹逐层选定 `evidence/v37/mcp.js`。两项均为输入工具介入记录，不算产品通过证据。

原始证据位于 `output/stage17-v37/`：`community-probe-windows.json`、`community-probe-linux-initial.json`、`community-probe-linux.json`、两平台 `community-plugin-*.jpg`、`community-windows-cleanup.json`、`community-launch.json`。默认 Preferences 哈希在 Windows 前后均为 `9dae2236c564456c9e0cc24e9b68f4a67781f24aeeb47a359bb53175c22e5191`，Ubuntu 前后均为 `be4b8924ab38e8acf350e6e3b9f1f63a1a94952d8002759acd6946c4d5d0b5de`。

此项补齐 G17-B 原有双平台真实插件兼容子范围；不代表所有社区插件已认证，不关闭 PRD17 全部门禁。
