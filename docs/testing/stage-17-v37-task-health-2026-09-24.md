# PRD17 v37 任务终态与健康刷新

v36 真实建模客户端已成功退出，但全局健康仍显示 233 条部分检查警告，而公开 API 已返回 0/0。失败证据见 [v36 连续建模记录](stage-17-v36-model-c-2026-09-24.md)。本次只补 `WorkbenchContext` 的任务终态刷新依赖：取任务 ID、终态和完成时间组成稳定值，适配原生桥原地更新 task map；不因每条日志和进度事件重复查询。

新增 `stage17-health-refresh.spec.ts` 连续 `task_completed` 事件回归，不改变 revision、不导航、不发送 focus，旧代码稳定保持 233 并失败。修复后两种尺寸的健康刷新、返回节点、触发器相关 26 项测试通过，TypeScript 通过。原始日志 `output/stage17-v36/client-health-red.log`、`client-health-green.log`。

## 候选与原生范围

冻结 v37 源 27,205 文件。首次 Gradle 启动和仅指定系统属性的重试均因本机 Unix-domain selector pipe 失败；使用项目既有 IPC agent 的进程级启动参数后，两平台构建 59 秒通过。失败日志和成功日志均在 `output/stage17-v37/`，未修改系统网络/持久环境。随包 22 文件、24 相对链接、642 Schema 引用及冻结源审计通过。

Windows/Linux 主 JAR SHA-256 `59da390ea54a68e8d925b0ca79a1d9de36dc68915310d994dcff62275b9f66e5`。Linux 根与 v36 比较，仅主 JAR 改变，无文件删除，2,178 个 Java class 字节不变。v36 已通过的模型/玩法及场景 B 证据按原范围复用，不声明重新完整测试。

真实 Windows 产品打开同一 model-c-windows 工程，基线 UI 为 233 条部分检查警告。经产品按钮启动任务 `82df948f-732a-4b65-85e4-65b9a9c16010`；运行中 API 也为 233。客户端停留标题页，本轮不进入世界或重复玩法。第一次退出被 FOCUS_LOST 拒绝，已 detach。用户再次明确让出桌面后，按约定完成 Computer Use 焦点恢复并交回 Minecraft MCP，正常点击 Quit Game。

日志有 `Stopping!`，PID 17540 消失，任务 succeeded，公开 API 回零。一次产品截图实际被浏览器遮挡，文件 `completed-client-health.jpg` **不计产品视觉证据**。激活产品后先见“检查中”，随后 `completed-client-visible-zero.jpg/json` 证实总览/健康/底部 0 条、0 错误、0 警告，任务已完成；不将该过程误写为“没有焦点事件的原生证明”。产品关闭重开后依然 0/0，基线已无异常，未继续执行无信息增益的构建。两个产品实例均请求正常关闭，退出日志保留。

本轮结论：任务完成后当前健康和可见 UI 一致，重开一致；无焦点/无 revision 的任务事件触发依据为真实桥接协议回归。后台遮挡期间的精确 UI 收敛时间未验证。Minecraft 会话 `689c0ec1-3ffb-476c-b6c8-4e55861e0bcc`、`e57551e7-4d93-45fe-96d2-29cd2c58d411` 均已 detach。

## Ubuntu 准备和授权

复用 v36 独立目录制作 v37 独立根，仅复制已审计的新 JAR，1,054 个文件逐项 SHA-256 与 Windows staging 根一致后构建 deb。包 `/home/stage17/copperbench-stage17-v37-amd64.deb`，SHA-256 `341465ff72900dd7fe3b764bda88323c1d536db9f6ad7340994d0d5edf8ac359`。准备证明 `output/stage17-v37/linux-preparation-proof.json` 是安装前状态。用户明确授权后，2026-09-24 13:37:46 UTC 安装 v37，替换 v34；v36 未安装。安装后主 JAR 哈希匹配，四个受保护模型/纹理/JAR 文件哈希不变，见 `output/stage17-v37/installation-proof.json`。

已安装入口和随包 SDK 完成[双平台社区插件兼容探测](stage-17-v37-community-2026-09-24.md)。后续 [Ubuntu 连续建模复验](stage-17-v37-linux-model-2026-09-25.md)完成真实 GUI 往返、构建、正常关闭和 SDK 重开，成功构建后 223 条部分警告回到 0。原独立建模客户端尾项仍需由独立报告关闭；安装和工具发现本身不关闭整体门禁。
