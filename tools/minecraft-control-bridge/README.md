# Minecraft Control Bridge（MVP）

供外部 Agent 使用的本地桌面控制工具：截图、有限时长键鼠动作、日志读取和证据记录。
支持 Windows 与 Linux X11；不安装 Minecraft 模组，不依赖游戏版本或加载器 API。
**平台后端可用不等于所有游戏版本均已验收**，实测范围见 [VALIDATION.md](VALIDATION.md)。

## 主 LLM、Jev 与桥的分工

- 主 LLM（例如 GPT-6 Astra）：理解需求、开发代码、指定测试场景/预期结果、审查证据并决定是否修复。
- Jev：接收主 LLM 从截图提取的文本/结构化状态，从限定动作中选择下一步；桥执行动作后，把新画面和证据交回主 LLM。
- 桥：执行动作、约束输入生命周期、返回截图与留证；`mc_jev_choose` 可请求 Jev 建议，但不会执行建议或自动判定玩法通过。

### 高效验证（v0.1.2）

**0.2.0 新增 [本地菜单流程](MENU-FLOWS.md)**：校准后使用一次 `mc_menu_flow` 完成进入指定存档、保存回标题或保存退出。中间截图由本地状态机校验并留证，只有终态/异常返回主 LLM；下面逐步观察规则仍适用于手动控制和未校准界面。

- 明确的菜单优先使用已校准的 `mc_menu_flow`；命令和玩法验收仍由主 LLM 决定。Jev 只用于尚未决定的局部选择；“主 LLM 已决定，再问 Jev 确认”只会增加调用。菜单流程使用限定模板匹配，不是通用视觉代理。
- 主 LLM 检查 `mc_step` 返回的图片后可直接据此继续，不必再调用一次 `mc_observe`。仅在过期、窗口变化、图片缺失或状态不确定时重新观察。先完成源码核查，再进入短操作循环。
- 在已观察到的界面中，将确定的小序列放进一批，例如输入命令、回车及短等待；界面是否切换不确定时须在该处停下来观察，不能盲跑跨屏脚本。批次仍限定 2,000 ms / 32 动作。
- 最后一项动作释放输入后，桥使用剩余批次预算补足最多 150 ms 的界面刷新等待；末尾已有 `wait` 时抵扣该等待。它不识别画面是否就绪；较慢的已知切换仍需显式等待。失焦、F8 和父进程断开检查在等待期间继续生效。
- 点击与其他动作统一使用最新截图的 120 秒时效；v0.1.1 的点击残留 30 秒限制已修复。错误详情报告帧龄，动作前拒绝写入 `step_rejected`，不自动重试。
- `mc_doctor` 返回正在运行的 `bridge_version`、`max_frame_age_seconds` 和默认刷新等待。修改源文件后须重连 MCP 才会加载新代码；不要另外启动桌面控制器冒充重连。
- 以端到端耗时衡量效率：回执中的 `before_frame_age_ms` 是决策前帧龄，`input_elapsed_ms` 是动作耗时，`post_action_settle_ms` 是申请的刷新等待预算，`elapsed_ms` 包含等待和截图。结合 `actions.jsonl` 时间戳统计调用、拒绝和输入，单列 Jev 调用；不能仅凭约一秒的 Jev 延迟宣称整体更快。

v0.1.1 提供 `minecraft_control_bridge.jev.JevClient`，已实测 TypeSafe 官方 REST API。
Jev 1.13 仅接收文本，不直接看截图，也不生成自由文本或任意键鼠轨迹。
模型路由、视觉状态提取及自主验证循环仍由外部编排器负责；包内不保存密钥。
主 LLM/外部编排器共享一个 MCP 连接，调用 Jev 选择工具，再审核并执行动作；不要为两个模型同时启动两个桌面控制会话。
`mc_record_verification` 的 `planner` 是调用者声明的角色，不是身份认证边界。

## Jev 接入

推荐使用 MCP 工具 `mc_jev_choose(state, goal, candidates)`，Python `JevClient` 仍可独立使用。
主 LLM 先查看 `mc_observe` 的真实图片，再提供如下参数（`frame_id` 必须替换为真实观察 ID）：

```json
{
  "state": {
    "frame_id": "actual-observation-frame-id",
    "source": "planner_visual_summary",
    "focused": true,
    "observation": "Crosshair targets the test machine; a copper ingot is selected.",
    "uncertainties": []
  },
  "goal": "Insert one copper ingot into the targeted machine.",
  "candidates": {
    "USE": "Briefly press and release the right mouse button",
    "WAIT": "Request a fresh observation before acting"
  }
}
```

结果含 `executed: false` 和传入的 `frame_id`；frame ID 仅用于关联主 LLM 的描述，不代表服务端已验证画面。
主 LLM 核对后才调用 `mc_step`，再检查返回画面。工具不接受密钥参数，也不会初始化桌面控制进程。
缺少密钥返回 `JEV_KEY_REQUIRED`；Jev 不可用时明确报告，可由主 LLM 直接控制或停止，不能假装已使用 Jev。

官方端点固定为 `https://api.typesafe.ai/v1/systemone`，默认模型 `jev-latest`。
密钥通过调用进程的 `TYPESAFE_API_KEY` 环境变量或内存中的构造参数传入，不写入源码、配置示例或证据。
接口只做一次请求，不自动重试计费调用，不跟随重定向；错误只返回状态码，不回显 provider 错误体或请求头。

```python
from minecraft_control_bridge.jev import JevClient

# observed_state 由主 LLM/视觉模块从最近的真实画面产生，包含 frame_id 和来源。
with JevClient() as jev:
    decision = jev.choose(
        observed_state,
        goal="Insert one copper ingot into the targeted machine.",
        candidates={
            "USE": "Briefly press and release the right mouse button",
            "WAIT": "Wait or request a fresh observation",
        },
    )
# 外部编排器核对作用域、画面新鲜度和置信度，再映射到现有 Bridge.step。
# decision 本身不执行输入，不能把 selected action 当作已经完成。
```

`choose` 返回 `choice`、`confidence`、完整概率分布、实际模型 ID、用量和请求耗时。
响应必须匹配候选集、数值有限、分布归一且选择为最高概率项，否则拒绝；不能把未知选项变成系统命令。
置信度没有通用安全阈值。本次实测一次正确选项的置信度仅为 0.69，由主 LLM 核对后执行。
详见 [Jev 实测记录](VALIDATION.md#jev-实测补充v011) 和脱敏的 [API 回执](validation/jev-live-2026-09-22.json)。

## 安装与启动

Python 3.11+。以下命令在本目录执行，依赖安装到项目虚拟环境：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -c requirements-lock.txt -e .
.\.venv\Scripts\python.exe -m minecraft_control_bridge doctor
.\.venv\Scripts\python.exe -m minecraft_control_bridge windows
.\.venv\Scripts\python.exe -m minecraft_control_bridge serve --evidence-dir D:/Trials/evidence
```

Linux：

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -c requirements-lock.txt -e .
.venv/bin/python -m minecraft_control_bridge doctor
.venv/bin/python -m minecraft_control_bridge serve --evidence-dir /home/me/trials/evidence
```

Linux 必须运行在可访问的 X11 桌面中，具有正确的 `DISPLAY`、`XAUTHORITY` 和 XTEST 扩展。
检测到 Wayland 或无显示会话时明确返回错误，不把 XWayland 冒作原生 X11 支持。
不要为工具更改系统权限、运行成管理员/root 或修改系统全局配置。

MCP stdio 配置示例（替换绝对路径）：

```json
{
  "mcpServers": {
    "minecraft-control": {
      "command": "D:/Tools/minecraft-control-bridge/.venv/Scripts/python.exe",
      "args": ["-m", "minecraft_control_bridge", "serve", "--evidence-dir", "D:/Trials/evidence"]
    }
  }
}
```

本地 MCP 连接无需 HTTP 端口或网络凭据；调用 Jev 需要 TypeSafe 密钥。诊断输出走 stderr，stdout 只用于 MCP 协议。
截图以 MCP 原生 `ImageContent` 和结构化元数据返回，模型客户端必须支持图片工具结果。

### 本项目 Codex 自动启动

仓库根目录的 `.codex/config.toml` 已注册 `minecraft-control`，使用
`scripts/Start-MinecraftControlBridge.ps1` 启动现有项目虚拟环境中的 stdio 服务。
打开受信任项目并建立 MCP 连接时由 Codex 启动；不需要提前运行后台服务或手动打开终端窗口。
桌面输入工作进程只在首次调用桌面工具时创建，列出工具或调用 Jev 不接管桌面。
这不是“每次工具调用都重启服务”：同一连接复用服务，结束连接后服务关闭并释放控制。
首次新增配置后，新建会话或重连 MCP；已经运行的会话不会因为写入配置就自动获得新工具。
项目移到其他路径时需同步修改配置中的绝对路径。Linux 直接配置 `.venv/bin/python -m minecraft_control_bridge serve`，保留正确的 X11 环境。

Windows 密钥可用以下命令安全输入，保存到当前用户 `%LOCALAPPDATA%/minecraft-control-bridge/typesafe-key.dpapi`，
使用 Windows DPAPI 加密，不写入仓库或系统全局环境变量：

```powershell
pwsh -NoProfile -File tools/minecraft-control-bridge/scripts/Set-JevKey.ps1
```

启动器优先使用进程环境中的 `TYPESAFE_API_KEY`，否则读取该用户加密凭据。
更新密钥后需重连 MCP。凭据解密失败时给出 stderr 提示，游戏控制工具仍可使用。
不要复制该加密文件给其他账号/机器；Linux 或其他宿主通过运行时环境注入密钥。
`AGENTS.md` 规定使用顺序：诊断 → 选择窗口 → 观察 → 主 LLM 决定短批次 → 执行并检查返回画面 → 留证与断开；只有未解决的局部选择才插入 Jev 建议与审核。
官方配置语义参见 [Codex MCP 文档](https://developers.openai.com/codex/mcp/)。

## 操作接口

Python 使用 `Bridge` 上的同名方法，MCP 名称加 `mc_` 前缀：

| 方法 | 用途 |
|---|---|
| `doctor()` | 初始化后端、报告平台能力；不注入测试按键，不声称游戏已验证 |
| `list_windows()` | 返回 Minecraft/Java 候选窗口、PID、进程启动标识和客户区 |
| `attach(window_id, game_dir=None, focus=True)` | 绑定明确窗口、预留 F8、创建证据目录；Python 还可传 `evidence_dir` |
| `observe(session_id, max_width=1280)` | 截图、时间、`frame_id`、尺寸和焦点状态 |
| `step(session_id, action_id, actions)` | 顺序执行动作，释放输入，返回操作回执与新截图 |
| `menu_flow(session_id, action_id, flow, profile_path, world_name=None, timeout_ms=20000)` | 在同一 worker 中匹配已校准界面并执行菜单导航，返回终态/异常；每批仍受原动作约束 |
| `read_logs(session_id, cursor=None)` | 增量读取 `game_dir/logs/latest.log`，最多 64 KiB，识别轮转/截断 |
| `record_verification(session_id, expectation, verdict, evidence_refs)` | 记录主 LLM 的外部判断，附证据引用 |
| `stop(session_id)` | 中断动作；之后必须 detach/attach 才能继续 |
| `detach(session_id)` | 释放控制、完成哈希清单；不关闭 Minecraft |

此外 MCP 提供 `mc_jev_choose(state, goal, candidates)`，对应独立的 `JevClient.choose`，不是 `Bridge` 方法。

最小 Python 示例：

```python
from minecraft_control_bridge import Bridge

with Bridge() as bridge:
    candidates = bridge.list_windows()["windows"]
    # 从候选中明确选出本次测试的窗口，不能盲选第一个 Java 进程。
    target = next(w for w in candidates if w["window_id"] == "你的窗口ID")
    attached = bridge.attach(target["window_id"], evidence_dir="./evidence", game_dir="/path/to/run")
    sid = attached["session_id"]
    observed = bridge.observe(sid)
    # 由调用方先检查 observed["image"]["path"]，确认是正确的游戏界面。
    result = bridge.step(sid, "walk-001", [
        {"type": "hold", "keys": ["w"], "duration_ms": 250}
    ])
    print(result["status"], result.get("observation"))
    bridge.detach(sid)
```

动作合同：

```json
[
  {"type":"hold", "keys":["w","space"], "buttons":[], "dx":30, "dy":0, "duration_ms":300},
  {"type":"look", "dx":-50, "dy":20, "duration_ms":150},
  {"type":"click", "x":320, "y":240, "frame_id":"当前截图ID", "button":"left", "settle_ms":100, "duration_ms":80},
  {"type":"scroll", "steps":1},
  {"type":"text", "text":"/time query daytime"},
  {"type":"hold", "keys":["enter"], "duration_ms":60},
  {"type":"wait", "duration_ms":100}
]
```

这是动作格式列表，不应在未经观察的界面中整批照抄执行。

- `hold` 支持组合键、组合按钮和同时转向；每项动作结束即释放持有输入，不跨请求保持按键。
- `look`/`hold.dx,dy` 是相对鼠标输入单位，不是角度。游戏灵敏度、原始鼠标输入设置会影响转向幅度。
- `click.x,y` 相对于返回的图片；桥负责缩放、DPI、窗口位置转换。点击前默认等待 100 ms，让游戏更新鼠标位置；可在 0–500 ms 间调整，计入动作预算。
- `text` 只接受 1–128 个可打印 ASCII 字符，不触碰剪贴板，不自动发送回车。X11 依赖当前键盘布局中存在这些字符。
- 支持字母、数字、`space/shift/ctrl/alt/enter/escape/tab/backspace`、方向键、`home/end/delete/pageup/pagedown`、`f1`–`f12`；`f8` 保留给急停。
- 每批最多 32 项，声明时长合计不超过 2000 ms。实际执行另有 2500 ms 墙钟期限，截图时间单独统计。
- 操作前须在 120 秒内观察过目标。点击只能引用最近一张截图；窗口移动/缩放后重新观察。较长推理后应主动再次观察。
- 同一会话重复 `action_id` 且动作相同，返回原回执与原截图，不重放动作；动作不同则拒绝。需要实时画面时另调用 `observe`。

## 会话与输入释放

一次只控制同一桌面的一个窗口。游戏必须在前台、可见且无遮挡，首版只承诺窗口模式。
`attach(focus=True)` 仅尝试聚焦；操作系统拒绝时返回的 `focused` 为 false，须先把游戏放到前台。
不会在每一步自动夺回用户已经切走的焦点。

输入由独立子进程持有，通过专用管道与 MCP/Python 控制端通信。
失焦、F8、显式 stop、动作期限、控制端 EOF/进程退出和执行异常均触发释放；工作进程每约 20 ms 检查一次。
`stop` 使用独立信号路径，不等待当前 `step` 的请求锁。stop 返回 `stop_requested` 时，仍须等待正在执行的回执。
整个进程树被强制杀死、系统崩溃、OS 输入 API 挂起不在软件释放保证内。

常见结果：`completed` 表示输入完成并取得后续截图；`executed_unobserved` 表示输入完成但截图失败；
`interrupted` 表示可能已执行部分动作，必须检查 `completed_actions` 和证据，不能自动重做。
这些状态都不表示模组玩法通过。

## Copperbench 与证据

桥与 Copperbench 生命周期分离。可用既有 SDK 的 `Workspace.run_client()` 启动，再按窗口身份连接。
使用 `Workspace.open()` 时保持该会话存活；见 [examples/copperbench_trial.py](examples/copperbench_trial.py)。
桥不改生成器、工作区源文件或现有 `run_client`/GameTest 合同。

每个会话保存 `session.json`、`actions.jsonl`、PNG、日志片段、`verifications.json`，结束时写 `hashes.json`。
会话前后对显式提供的 `game_dir/mods/*.jar` 做哈希比较；这不覆盖开发模式的整个 classpath，
也不替代 Copperbench 已验证产物的身份检查。自定义启动器把日志写在其他工作目录时，首版需指定对应目录读取日志，
并在测试说明中另行注明实际存档目录。

`record_verification` 只记录外部主 LLM 的判断，不独立验证语义。缺少动作日志与截图引用会降级为 `unverified`。
引用可使用会话内文件的相对或绝对路径；检查范围后统一记录为相对路径。其他会话文件、越界路径和指向会话外的符号链接均拒绝，子目录中同名的 `actions.jsonl` 不算本会话动作日志。
报告应区分场景准备命令、真实键鼠交互、视觉观察、日志事实与模型推断。
保存重进须记录同一存档、实际进程结束、新进程以及非默认状态恢复。

## 验证与扩展

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
# 以下测试会创建并操作自己的可见窗口；勿与其他桌面操作并行运行。
.\.venv\Scripts\python.exe tests/live_desktop.py --output ./evidence/desktop
```

Linux 的桌面测试使用 Xlib 创建独立测试窗口，无需 Tk。Windows 测试窗口禁用自身的 IME 组合输入，
不修改用户输入法设置。测试主体是 OS 后端，不是 Minecraft；游戏证据独立记录。

扩展点为 `backends/` 的窗口、截图和输入实现。Wayland、游戏内遥测、连续视频、战斗控制、
远程公开 HTTP 服务和模型编排都不在本 MVP 内。
