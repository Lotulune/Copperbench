# 本地菜单流程（0.2.0）

`mc_menu_flow` 将已经校准的菜单导航留在本地输入 worker 中执行。一次 MCP 请求内完成截图匹配、短批次输入、等待下一界面；主 LLM 只需检查最终画面或异常，不再为每个按钮等待一轮模型。它不调用 Jev，不识别任意游戏画面，也不作玩法验收结论。

## 调用

先按 README 的既有约定 doctor → list_windows → attach。doctor 必须报告 `bridge_version=0.2.0` 和 `menu_flows`。源文件更新后需要重连 MCP；旧连接不会自动增加工具。

```json
{
  "session_id": "已绑定的会话 ID",
  "action_id": "enter-prd17-001",
  "flow": "enter_world",
  "profile_path": "D:/Trials/prd17-menu/profile.json",
  "world_name": "PRD17 Scene C v7",
  "timeout_ms": 20000
}
```

- `enter_world`：起点为标题或世界列表。打开列表，在搜索框输入精确的目标名称，核对第一个结果的名称模板以及其余列表区域为空，再进入；等待已校准的游戏 HUD 和单人游戏窗口标题。
- `save_to_title`：起点为已校准的游戏 HUD、暂停菜单或标题。必要时按 Escape，匹配保存按钮，保存后等待标题。若已在标题，只返回当前位置，不声称发生了新保存。
- `save_and_quit`：在上一流程后点击已确认的 Quit Game，等待目标窗口消失。`window_closed_unverified` 只说明窗口已关闭，仍须核对进程、游戏日志和 Copperbench 任务终态。

每个流程最多六批输入，每批仍受 2,000 ms / 32 动作约束，总时间 1–25 秒（默认 20 秒，包含等待）。未识别的过渡画面只观察、不输入，直到匹配或超时；已识别但不符合预期的页面立即停止。不会处理创建世界、删除、转换版本、实验功能或备份确认弹窗。失焦、F8、窗口身份/几何变化、父进程断开继续中止控制，不自动抢焦点。

流程不重发已经提交的点击。重复同一 `action_id` 和完全相同请求/模板只返回原回执；换目标或模板须新建 ID。失败后先看截图，不应不断换 ID 重试。

## 一次校准，多次复用

主 LLM 先检查真实截图，确认按钮含义、世界名及坐标。`calibrate_menu_profile.py` **不猜标签、不做 OCR、不发送输入**；它从已审查图片裁取标记并保存哈希。无需每轮重新校准，但视口大小、GUI 缩放、语言、字体/资源包和可见 HUD 模式改变时须重新检查。当前筛选输入仅支持可打印 ASCII 世界名；其他名称使用手动控制。

以下是 854×480、默认英文菜单的格式示例；这些矩形不是所有版本的通用坐标。四张 source 图片必须由当前测试客户端获取并经检查。RGB HUD 标记应选择不会随手持物品/背景变化的界面边缘，至少有三个颜色值；文字标记使用白/灰字的二值遮罩，比较完整前景和背景，不作模糊 OCR 猜测。

```json
{
  "world_name": "PRD17 Scene C v7",
  "capture_size": [854, 480],
  "world_search_box": [230, 48, 392, 32],
  "world_entry": {"box": [228, 103, 230, 20]},
  "empty_below_entry": [225, 176, 455, 175],
  "states": {
    "title": {"source": "D:/Trials/title.png", "markers": {
      "singleplayer": {"box": [355, 225, 145, 25]},
      "quit": {"box": [470, 391, 122, 26]}
    }},
    "world_list": {"source": "D:/Trials/world-list.png", "markers": {
      "heading": {"box": [355, 10, 150, 28]},
      "create": {"box": [480, 383, 215, 28]}
    }},
    "pause": {"source": "D:/Trials/pause.png", "markers": {
      "heading": {"box": [365, 76, 140, 26]},
      "save": {"box": [267, 334, 324, 31]}
    }},
    "playing": {"source": "D:/Trials/playing.png", "title_suffix": " - Singleplayer", "markers": {
      "hud_left": {"box": [331, 436, 81, 6], "mode": "rgb"},
      "hud_right": {"box": [455, 436, 81, 6], "mode": "rgb"}
    }}
  }
}
```

`create` 只是“世界列表”界面的辅助标记，流程不会点击 Create New World。名称模板只包含存档标题，不包含会变化的保存日期；列表下方守卫必须覆盖其余可见结果行。缩放/文字变化或多结果可能导致保守拒绝，这时检查实际画面，不要降低匹配阈值来强行通过。

在桥目录运行：

```powershell
.\.venv\Scripts\python.exe scripts/calibrate_menu_profile.py --spec D:/Trials/reviewed-spec.json --output D:/Trials/prd17-menu
```

输出目录必须不存在，避免覆盖已审查模板。`profile.json` 记录完整来源图片哈希和各标记哈希；加载时核对标记大小、路径边界及哈希。校准后复查四个状态唯一可识别及世界结果唯一。目录中的 PNG 是小范围界面标记；不要把世界存档、第三方 JAR 或凭据加入模板。

## 证据与效率

`actions.jsonl` 保留 `menu_flow_started`、每张本地截图的 `menu_state`、原有逐动作回执及 `menu_flow_finished`。返回值包含 `states`、`steps`、`elapsed_ms`、最终 `observation`；`navigation_only=true` 和 `gameplay_verified=false` 始终保留。

`status=blocked` 时读 `error` 和 `stage`，检查返回图片。`observation_is_last_known=true` 表示最后一次输入后没能取得新图，不能把旧图当当前状态。关闭后 `observation_is_pre_close=true` 明确标识返回图来自关窗之前。

校准与导航时间分开记录，用相同起终点比较单请求耗时和过去的模型往返；本地截图数量不等于模型调用数量。未经实机执行的流程只报告模拟/协议测试通过。新语言/缩放/平台也不能直接套用其他模板的通过记录。
