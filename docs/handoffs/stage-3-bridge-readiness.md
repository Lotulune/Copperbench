# 阶段 3 桥接集成准备

## 当前边界

- UI-Core `1.0` 已冻结；Java、TypeScript、Schema 和模拟场景使用同一版本。
- `JcefBridgeEndpoint` 把 JSON `handshake/command/query` 映射到 `WorkspaceApplicationService`，并把领域 Event 发到独立 sink。
- `JcefCoreBridgeTransport` 将 endpoint 限定到单一 WebView、主 frame 和工作区 ID；页面加载开始前注入 host，WebView 关闭时注销 router/listener。
- `JcefCoreBridge` 实现 React 的 `CoreBridge`。`ui-shell/src/bridge/index.ts` 仅在完整的 `window.copperbenchHost` 存在时启用它；普通 Vite/Playwright 环境继续使用 `MockCoreBridge`。

## 已完成的 U2 接线

1. `CopperbenchProductShell` 持有已加写锁的 `MCreatorWorkspaceSession`，创建承载生产 UI URL 的 `WebView`。
2. 在显示/`forceLoad()` 前调用 `webView.attachCoreBridge(session.workspaceId(), session.uiEntry())`。不要使用通用 `addJavaScriptBridge` 暴露 endpoint。
3. 保持返回的 `JcefCoreBridgeTransport` 与窗口同生命周期；`WebView.close()` 会自动注销它，手动提前关闭也安全。
4. WebView 已放入工作区窗口；启动握手、初始投影、event、task 刷新和关闭路径不需要组件层业务分支。
5. 实际 JCEF 烟雾测试已从 classpath URL 加载 React DOM 并确认原生 Schema 握手；复现脚本为 `scripts/verify-stage-4-bridge.ps1`。

最小 Java 接线：

```java
WebView webView = new WebView(productUiUrl);
JcefCoreBridgeTransport bridge = webView.attachCoreBridge(session.workspaceId(), session.uiEntry());
webView.forceLoad();
// 窗口关闭时调用 webView.close()；bridge 会随之关闭。
```

注入的浏览器 API 固定为：

```ts
window.copperbenchHost = {
  workspaceId: string,
  invoke(envelopeJson: string): Promise<string>,
  onEvent(listener: (eventJson: string) => void): () => void
};
```

## 集成前约束

- Java 端只接受 Schema `1.0`，握手不兼容时不得发送命令或查询。
- 任务在工作区修订锁之外运行；内容变更仍必须携带 `expectedRevision`。
- UI 不直接打开路径、执行 Gradle 或解析 Java 异常文本。
- G4 已接入无边框 Snap/DPI、旧版插件窗口、真实 renderer 故障与系统框架回退；复现入口为 `scripts/verify-stage-4-bridge.ps1`。
- Fabric 1.21.1 Loom 资产已通过 `D:\Minecraft\.minecraft\assets` 补齐同 SHA-1 的 4 个缺失对象；真实 `runClient` 已捕获 readiness marker 并通过。
