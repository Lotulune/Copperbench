# 本地菜单流程（0.3.0）

`mc_menu_flow` 在本地 input worker 中完成截图匹配、短批次输入、等待下一界面，主 LLM 只看最终画面或异常。MCP 默认 `controller="jev"`：本地匹配结果转成带 frame_id、source=local_reviewed_profile、模板哈希和目标的结构化状态，Jev 只选 PROCEED/WAIT/STOP，不看图、不生成坐标。建议返回后重新截图、复核同样的状态和目标，才执行固定动作。网络等待期间不按住输入，继续检查 F8、焦点和断连。Jev 错误、超时或 WAIT 均停止，不静默回退。

固定路线可显式用 `controller="deterministic"` 跳过网络开销。Python SDK 保持此默认值以兼容已有调用。两种路径都不识别任意游戏画面，也不作玩法验收结论。

## 调用

先按 README 的既有约定 doctor → list_windows → attach。新版 doctor 应报告 bridge_version=0.3.0、menu_controllers 和 create_world。更新源码后需要重连 MCP。

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

进入/保存/退出最多六批输入，显式创建最多十二批。每批最多 2,000 ms / 32 动作，总时间 1–25 秒（默认 20 秒，含 Jev 等待）。未知画面只观察，不输入；不符合预期的已知页面立即停止。创建须使用下面的 v2 模板。不会处理删除、覆盖、转换版本、实验功能或备份确认弹窗。失焦、F8、窗口身份/几何变化及断连仍停止，不自动抢焦点。

`wait_for_world` 只观察加载，不输入。创建提交后若加载超时，返回 `pending / creation_submitted_loading`、`creation_submitted=true` 和 `next_flow=wait_for_world`。随后只等待，不重发创建。

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

### 创建世界的 v2 校准

调用必须同时提供 `flow="create_world"`、精确 world_name、与模板完全相同的 creation_settings。设置只支持 game_mode=survival|creative、world_type=default|superflat、allow_commands 布尔、generate_structures 布尔，不由 Jev 自选。

在校准 spec 中增加 creation、empty_world_list 和每个创建表单状态的真实已审查截图，脚本自动输出 schema 2。creation.steps 为 2–9 步固定路线：第一步 set_world_name，中间仅 click，最后且仅一次 submit。状态名唯一且以 create_ 开头，每个状态提供 2–8 个能区分界面的 markers。点击 role 必须是本状态 marker；第一步 name_box 来自实图。最终状态必须有完整预期世界名称的 world_name marker。

结构示意（省略的 states 与所有区域须按实际截图填写，不可猜坐标）：

```json
{
  "creation": {
    "settings": {"game_mode":"creative", "world_type":"superflat", "allow_commands":true, "generate_structures":false},
    "steps": [
      {"state":"create_initial", "action":"set_world_name", "name_box":[218,96,415,40]},
      {"state":"create_named", "action":"click", "role":"mode"},
      {"state":"create_hardcore", "action":"click", "role":"mode"},
      {"state":"create_creative", "action":"click", "role":"world_tab"},
      {"state":"create_default", "action":"click", "role":"world_type"},
      {"state":"create_flat", "action":"click", "role":"structures"},
      {"state":"create_flat_no_structures", "action":"click", "role":"game_tab"},
      {"state":"create_ready", "action":"submit", "role":"create"}
    ]
  },
  "empty_world_list":[225,95,450,256]
}
```

世界列表先筛选目标名，确认完整结果区域为空，才新建。首次启动可能从标题直接到创建表单，此时要求附带 game_dir 且 saves 中没有存档。创建专用模板可省略 world_entry（世界尚未存在），其列表源图必须是已审查的空筛选结果。以后自动进入该存档，须从真实保存后的列表补做 world_entry 校准，不伪造名字图片。

模板设置是主 LLM 对实际截图的声明，不是 OCR 推断；语言、缩放、主题、布局或世界名改变需重新校准。首次观察仍必要，后续操作不再逐步返回主模型。

新版回执另有 jev_calls 和 timings，分别记录总耗时、输入/截图/Jev 耗时及调用数。优先复用 step 图；Jev 网络等待后重新截图。模拟菜单耗时不代表真实游戏加载速度。

`actions.jsonl` 保留 `menu_flow_started`、每张本地截图的 `menu_state`、原有逐动作回执及 `menu_flow_finished`。返回值包含 `states`、`steps`、`elapsed_ms`、最终 `observation`；`navigation_only=true` 和 `gameplay_verified=false` 始终保留。

`status=blocked` 时读 `error` 和 `stage`，检查返回图片。`observation_is_last_known=true` 表示最后一次输入后没能取得新图，不能把旧图当当前状态。关闭后 `observation_is_pre_close=true` 明确标识返回图来自关窗之前。

校准与导航时间分开记录，用相同起终点比较单请求耗时和过去的模型往返；本地截图数量不等于模型调用数量。未经实机执行的流程只报告模拟/协议测试通过。新语言/缩放/平台也不能直接套用其他模板的通过记录。
