# 主 LLM → Jev 操作任务

以下是给主 LLM/外部编排器的任务格式示例。推荐通过 `mc_jev_choose` 使用 TypeSafe REST 接入；Python 也可使用 `minecraft_control_bridge.jev.JevClient`。
主 LLM/视觉模块把截图转为结构化状态；Jev 根据状态选择候选动作，编排器通过共享 MCP 连接执行。
主 LLM 保留开发和验收决策，Jev 不直接接收截图。

```json
{
  "task_id": "verify-machine-insertion",
  "operator": "jev",
  "goal": "验证空手与持有铜锭时右键机器的可见反馈",
  "target": {"workspace": "本次测试工作区", "expected_mod_jar_sha256": "主 LLM 提供"},
  "steps": [
    "确认正确游戏窗口和测试存档，观察并返回截图",
    "空手右键机器，返回反馈截图",
    "持有一枚铜锭右键机器，返回反馈截图与日志片段",
    "停止操作，交回所有证据引用"
  ],
  "expected": ["空手反馈与持有铜锭的反馈符合需求", "机器接收物品"],
  "limits": {"maximum_actions": 30, "maximum_seconds": 180},
  "stop_on": ["窗口身份变化", "非预期界面", "游戏崩溃", "无法确认目标", "操作预算耗尽"],
  "return": ["executed_actions", "observations", "evidence_refs", "uncertainties"]
}
```

Jev 只返回候选动作选择、概率和耗时，不生成视觉观察或执行回执。编排器汇总主 LLM 的观察、桥的实际执行结果和不确定性。不得把输入 API 返回成功等同于玩法通过。
主 LLM 对照需求决定通过、失败或未验证，并负责调用 `mc_record_verification`。
需要进一步定位时，由主 LLM 修改代码或修订任务，Jev 不自行扩大验证范围。
