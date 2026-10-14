# 30–31：Ubuntu 安装 API 验收准备

日期：2026-10-13（日本时间）。状态：**in-progress；云端实际运行 not_run**。

用户已指定 Ubuntu 使用云端验证。本地完成了可评审的验收脚本与 CI 接线，尚未获得此前询问的提交、推送授权，未 dispatch 本候选。Windows 的运行结果不计入 Ubuntu。

[安装验收脚本](../../tools/candidate-evidence/installed_acceptance.py)从冻结候选的 tar/ZIP 解包到新目录，核对应用、启动器、SDK、生成器和样例。Ubuntu 分支检查真实系统为 Ubuntu 24.04 x86_64，并隔离 HOME、XDG、产品目录、JVM user.home 与 Gradle 缓存。产品调用使用安装包内代码；外部仓库仅提供 JSON schema 校验器。

每个加载器独立运行冷缓存创建、故障修复、六项必要业务验收、导出与三次产品进程重连。暖缓存只接受同候选、同平台、同加载器的成功冷记录，并逐文件校验保留载荷。当前/历史导出分别在干净目录通过包内 standalone verifier；篡改包必须拒绝。授权签发使用已授权范围内的安装版 TaskAuthorizationStore fixture，结束时撤销，不代表桌面签发 UI 验收。

[Linux 工作流](../../.github/workflows/stage15-linux-candidate.yml)的 `p263_loader` 选择现在连接到实际安装 API 脚本，单独上传成功或失败记录。选定候选保持不可变，原始十个单元的 `not_run` 表不被改写；新单元由独立回执与 evaluator 复核。默认旧路线保持原入口。包内容及 bootstrap 分发检查补上 Gradle 9.6.0。

本地验证：

- 初次候选工具合计 **31 项测试通过**；29 号实测后补充进程崩溃、清理错误保留、授权恢复和分盘空缓存回归，最新合计 **37 项通过**，其中安装边界 15 项。Linux 环境隔离、冷/暖绑定、解包与失败拒绝仍通过。
- CI 路径选择的 **6 项相关检查通过**。另一次执行该文件的全部 23 项测试时，21 项通过，2 项因 Windows 不允许文件名包含换行/制表符而报错；这两项保留给 Linux CI，不记为通过。
- 使用项目已有 SnakeYAML Engine 解析工作流成功，拒绝重复键并核对 dispatch 与唯一安装验收步骤。无需增加产品依赖。
- Markdown 链接和差异空白检查通过。Python/Node 环境中没有现成的 YAML 解析包，最终检查使用上述 Java 解析器。

原始日志位于 `output/p263-29/candidate-gate-tests.log`、`output/p263-29/harness-boundary-tests-v4.log`、`output/p263-29/ci-path-selection-tests.log` 和 `output/p263-30/workflow-validation.log`。这些准备检查不是安装闭环结果。

30/31 仍需各自在云端构建冻结候选并实际通过冷、暖与离线闭环。34–37 还需对应 GNOME Xorg/Wayland 的真实客户端操作与保存重进；Xvfb 预检不能代替它们。`formalSupportClaim=false`。
