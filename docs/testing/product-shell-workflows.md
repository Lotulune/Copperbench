# Product Shell 工作流验收

新外壳复用已有领域能力，并按编辑任务组织入口。导航、标签页和命令搜索属于展示状态，不改变 Workspace Revision。

| 工作流 | 新界面入口 | 业务边界 |
| --- | --- | --- |
| 可视化创作 | 模组元素 | 复用现有元素字段、Procedure / GUI 编辑器和保存命令 |
| Manual Source | 源码；总览的源码入口；任务诊断中的文件位置 | Core 列出、读取和保存文件，校验 Workspace Revision 与 SHA-256 |
| Generated Source | 源码中的只读文件 | 由 Core 判断归属；修改仍回到其所属元素或生成器 |
| 资产 | 资产与模型；关系图中的资产节点 | 沿用资产投影、引用、导入预览、移动和 Blockbench 往返 |
| 构建与验证 | 标题栏动作和底部任务面板 | 沿用现有任务、日志、诊断与审批能力 |
| 本地恢复 | 本地历史 | 源码保存创建恢复点，沿用现有恢复机制 |
| 偏好设置 | 设置 | 读取并保存现有应用偏好，不作为工作区业务对象 |

“源码入口”展示 Core 从代码和元数据得到的入口、注册调用和资源标识证据，附文件与行号。它不是新的 Mod Element，也不声称能把任意手写代码完整还原成可视化功能。没有按工作区名称定义的功能卡片。

## 自动化验收

```powershell
npm test --prefix ui-shell
npm test --prefix ui-core
npm run build --prefix ui-shell
```

在 `ui-shell` 目录运行聚焦浏览器测试：

```powershell
npx playwright test e2e/source-workbench.spec.ts e2e/settings.spec.ts e2e/product-shell.spec.ts e2e/relationship-graph.spec.ts --project=chromium --project=compact-1366
```

浏览器夹具验证交互和桥接请求。磁盘写入、外部修改冲突、恢复点及设置文件保存还需要相应 Java 测试和真实 Core 会话；不能仅凭 MockCoreBridge 测试宣称通过。

草稿、关闭保护、命令搜索和资产源码入口的回归在 `ui-shell/e2e/uiux-regressions.spec.ts`。Windows 桌面验收使用当前源码编译出的生产前端、JCEF、原生窗口桥和真实工作区：

```powershell
.\gradlew.bat --no-daemon test --tests dev.copperbench.shell.WorkbenchDraftsJcefTest '-Dcopperbench.stage4.jcefSmoke=true'
```

该用例在 `output/uiux-fix-20261007/native/<run-id>/` 创建独立工作区、运行时目录、截图和 `receipt.json`。验收包括函数草稿跨页面保留、保存落盘、普通元素未保存数量、原生“继续编辑 / 关闭并放弃”对话框、搜索滚动和资产源码路径。它通过定向 JCEF DOM 事件与真实 Swing 控件执行交互；截图来自目标渲染器和目标对话框，不向当前前台的其他应用发送输入。没有执行 Minecraft、原生拖动缩放或安装包验收。

## 使用真实 Core 的本地浏览器预览

`scripts/preview-product-shell.py` 把构建后的 `ui-shell/dist` 接到 Python SDK 启动的真实 Java Core。它接受任意 `.mcreator` 工作区，不扫描文件、不替 Core 计算引用，也不会伪造命令成功。

预览会写入指定工作区；验收应使用独立副本。用独立 `--runtime-home` 隔离应用设置、缓存和日志。预览仅监听 `127.0.0.1`，会话请求校验来源和随机令牌，API 日志只记录结果元数据。

先编译当前 Java 源码，将 Gradle `sourceSets.main.runtimeClasspath.asPath` 导出到一个文本文件，再构建前端。例如：

```powershell
python scripts/preview-product-shell.py --workspace D:/preview/workspace/example.mcreator --product 'C:/Program Files/Copperbench' --runtime-home D:/preview/runtime-home --classpath-file D:/preview/runtime-classpath.txt --port 5175
```

`--product` 指向已安装发行目录，提供插件和运行时资源；classpath 必须包含当前仓库编译结果。编译完成后再启动：预览会把目录形式的 classes / resources 固定为本次会话的 JAR，避免后续编译更换类文件影响正在运行的 Core。服务启动后会打印实际 URL 和工作区位置。

浏览器预览用于源码、设置和投影交互验收。原生文件选择器、完整原生偏好窗口、桌面窗口控制、插件窗口和 Minecraft 生命周期仍需在桌面宿主验证。预览沿用 Headless 权限边界，不绕过原生用户审批。SDK 不向此预览推送桌面异步事件，任务状态由现有查询轮询更新。
