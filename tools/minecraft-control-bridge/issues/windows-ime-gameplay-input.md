# Windows 中文输入法导致游戏按键无响应（0.3.0 已实现，待游戏复验）

- 状态：2026-09-27 已在 0.3.0 源码实现检测、切换、确认和恢复；当前 MCP 旧 worker 须重连。单元测试与真实 Win32 IME 切换/恢复通过，尚未以新版 worker 在 Minecraft 中重跑。
- 环境：Windows，Minecraft 1.21.1 Fabric，Minecraft Control Bridge 0.2.0。
- 现象：前台窗口与截图有效，菜单鼠标点击及 `text` 输入世界名称成功；进入世界后 `hold(keys=["e"])`、`hold(keys=["escape"])` 回执为 `completed`，但背包/暂停菜单未打开。
- 用户指出当时使用中文输入法，并手动切换至英文输入法。切换后同一桥会话的 `e` 立即打开创造模式背包，随后聊天、移动与刷怪蛋交互正常。这支持输入法相关故障的判断，尚未独立隔离具体输入法版本/组合状态。
- 证据：`output/minecraft-validation/ded90563-7b15-4435-b0d1-0df0c82bb2ee/actions.jsonl`；失败动作 `open-inventory`、`inventory-key`、`check-pause-input`；成功动作 `inventory-english-ime`，截图 `frame-ac52800f-17bc-430e-a351-3c33f38a8f29.png`。
- 当前代码：`backends/windows.py:WindowsBackend.key` 通过 `MapVirtualKeyW` 和扫描码发送按键；`type_character` 使用 Unicode packets。输入回执只说明发送完成，不能证明游戏消费了按键。

已实现与实测：

- `input_language.KeyboardGuard` 发键前检查目标线程；english 优先关闭该窗口 IME 中文转换，preserve 检测风险后不发键。
- 本机只有中文 IME，无单独英文 HKL。状态未知时才尝试已有英文 HKL，不安装布局、不改系统默认值。
- detach 仅在原进程/线程且状态仍为桥设置值时恢复；用户另行切换后不覆盖。
- `output/minecraft-validation/bridge-030-native-ime/ime-result.json` 实测同一目标的 ime_open 为 true → false → true。
- 完整按键测试因桌面未给测试窗口前台焦点而被阻止，见 bridge-030-ime-focused/ime-result.json；未绕过焦点检查，不宣称新版 Minecraft 按键验收已通过。
- 输入语言官方文档由 smart-search fetch 获取；IMC_SETOPENSTATUS 常量与本机 Windows SDK 10.0.26100.0/um/immdev.h 核对。不同 TSF/IME 的状态支持仍须实测，未知/未确认状态停止。

后续实机回归范围：

1. 诊断目标窗口线程的键盘布局、IME 开启状态与组合状态，并在 `mc_doctor` / attach 的适当边界提供明确提示。
2. 在中英文输入法、游戏与菜单上下文中复现；保留焦点/F8 检查，避免将问题误判为失焦或无效键位。
3. 设计可逆且仅作用于目标窗口的处理策略；如需切换输入法，显式说明并恢复，不改系统默认输入法。
4. 验证字母键、Escape、命令文本、按下/释放及异常释放；不得因 `SendInput` 成功就报告游戏交互通过。

当前规避：用户将 Minecraft 切换为英文输入状态并保持前台，再由桥观察并重新执行明确未生效的动作。
