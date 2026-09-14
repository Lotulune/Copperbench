# Copperbench Python API

Python 3.11+。`Workspace` 直接调用产品 Core，可连接桌面当前工作区，也可独立打开工作区；`CopperbenchClient` 提供可选的 MCP 调用方式。

桌面内可直接使用导航栏的「Python 工作台」：持久控制台、脚本编辑与执行、当前选择、代码补全、异常定位和停止/重启，详见 [Python 工作台使用说明](../../docs/user/python-workbench.md)。源码仓库中的文档路径为 `docs/user/python-workbench.md`，发行包 SDK 可参考本页接口说明。

## 安装与打开工作区

在包含本功能的 Copperbench 构建中，SDK 位于 `sdk/python`。也可使用源码中的同名目录：

```powershell
python -m pip install ./sdk/python
```

无需安装第三方运行时依赖。也可以把该目录加入脚本的模块搜索路径，直接导入。

## 连接桌面当前工作区

先在支持此功能的 Copperbench 中打开工作区，再在同一个操作系统用户下运行：

```python
from copperbench import Workspace

with Workspace.connect(r"D:\ModProjects\bell\bell.mcreator") as workspace:
    bell = workspace.elements["copper_bell"]  # 精确名称或稳定 ID
    print(bell["/displayName"])
    bell.update(displayName="铜铃")
    bell["/displayName"] = "共鸣铜铃"
    # 新建与批量字段更新同样经过 Core 校验和持久化。
    item = workspace.elements.new("item", "python_item", displayName="脚本物品")
```

这条路径复用桌面已打开的工作区、任务和 Core，不重新获取写锁，不启动 Java 子进程，也不需要 MCP 或 MCP token。修改通过桌面现有事件通道同步。退出 `with` 只断开此脚本，桌面工作区和任务继续运行；桌面关闭则连接失效，不会自动切换到独立写入。

通用元素检查器在没有草稿时自动刷新外部修改；存在草稿时保留输入并暂停提交，提供“丢弃草稿并加载最新内容”。界面保存使用读取时的 revision，避免静默覆盖脚本已提交的修改。

本地原生协议使用 `127.0.0.1` 随机端口，凭据在操作系统用户目录的 `.copperbench/python-sessions` 中自动发现，使用 POSIX 权限或 Windows ACL 限制访问，不写进项目。凭据随桌面会话启动生成，正常关闭时删除。连接本身不赋予 `UI` 身份，批准规则仍生效。

元素句柄记录读取时的 revision。其他脚本或桌面修改之后，旧句柄写入会得到 `REVISION_CONFLICT`；确认新状态后显式调用 `bell.refresh()` 再决定是否修改。字段读取会刷新该句柄，其他对象的读取不会偷偷刷新它。`update(...)` 一次提交多个顶层字段；下标使用 Core 的 JSON Pointer，赋值立即提交。

桌面工作台会管理独立 CPython 进程，自动绑定 `cb.context`、`cb.data`、`cb.ops`、`cb.types`、`cb.utils` 和 `cb.app`。外部脚本可 `import copperbench as cb`，再调用 `cb.use_workspace(workspace)` 绑定同样的对象接口。它不把 CPython 嵌入 Java 进程，也不开放任意内部 Java 对象。

## 独立打开工作区

```python
from copperbench import Workspace

with Workspace.open(
    r"D:\ModProjects\bell\bell.mcreator",
    launcher=r"D:\Apps\Copperbench\copperbench.exe",
) as workspace:
    print(workspace.workspace_id, workspace.revision)
    for element in workspace.list_mod_elements():
        print(element["name"], element["type"])
```

Linux 的 `launcher` 使用发行包内的 `copperbench.sh`。省略时，从 `COPPERBENCH_EXECUTABLE` 或 PATH 中查找 `copperbench`。开发构建可以传入完整参数列表，例如 `[java, "-cp", classpath, "net.mcreator.Launcher"]`，并用 `cwd` 指定发行资源根目录。

`Workspace.open` 启动一个持续运行的本地 Java 核心进程，通过私有 stdin/stdout 管道直接调用 Core。它不创建 HTTP 端口，不启动 MCP，不读取 MCP 连接文件，也不要求 MCP token。Java 仍使用 Copperbench 自带的运行时；这不是把整个 Java 引擎嵌入 CPython 内存空间。

独立打开采用独占写锁。如果桌面编辑器或另一个脚本已打开该工作区，会返回 `WORKSPACE_WRITE_LOCKED`。需要操作桌面当前工作区时，使用上面的 `Workspace.connect(...)`，也可选择 MCP 客户端。

## 修改与任务

```python
created = workspace.create_mod_element(
    elementType="item",
    name="copper_bell",
    initialValues={"displayName": "铜铃"},
)

# 返回的是核心结果，保留 diagnostics、revision 和 task 等结构。
accepted = workspace.build()
if accepted["status"] == "accepted":
    result = workspace.wait_task(accepted["task"]["id"])
    if result["data"]["task"]["state"] != "succeeded":
        raise RuntimeError(result["data"])
```

以上调用须放在 `Workspace.open(...)` 或 `Workspace.connect(...)` 的会话内部。`build()` / `generate()` / `validate()` / `run_client()` / `run_server()` / `run_game_tests()` 返回任务受理结果；受理不等于成功。`get_task()`、`wait_task()` 和 `cancel_task()` 用于跟进任务；取消成功返回 `cancelled`。`wait_task()` 返回终态及等待期间收集的日志，其超时只结束等待，任务仍可查询或取消。独立会话关闭时会释放工作区并关闭其任务执行器，需运行客户端时应保持独立会话存活；连接桌面的脚本断开不关闭桌面任务。

所有 Core 操作还可以通过统一入口访问：

```python
registries = workspace.query("list_workspace_registries")
workspace.command(
    "create_registry_entry",
    registry="variables",
    entry={"name": "bell_count", "dataType": "number", "scope": "global"},
)
```

`query()` / `command()` 的操作名和 payload 字段沿用 UI-Core 契约，未承诺未实现的任意内部 Java 方法。常用便捷方法包括元素创建/更新、过程更新、生成/构建/校验、客户端/服务器、GameTest 和任务查询。完整操作名见源码 `UiCore.Operation`。

## 版本、授权与错误

每次修改默认使用当前会话最后观察到的 revision，也可显式传 `expected_revision=...`。冲突不会自动重试或覆盖；先检查 `NativeApiError.details`，重新读取状态，再决定后续操作。一次网络或进程故障不触发自动重放写操作。

普通本地工作区操作使用 `HEADLESS / WORKSPACE` 核心权限。创建工作区、历史恢复等受保护操作仍执行现有批准规则，不能通过 `userApproved=True` 伪造桌面用户。需要任务授权时，在打开会话时传入用户签发的 `task_authorization_id`；该 ID 自动附在核心声明支持任务授权的命令中。撤销授权和取消任务不依赖仍然有效的授权。服务器 EULA 规则保留。

```python
from copperbench import NativeApiError

try:
    workspace.build(expected_revision=0)
except NativeApiError as error:
    print(error.code, error.details)
```

独立进程启动等待默认 120 秒，单次 API 等待默认 30 秒，可通过 `startup_timeout` / `request_timeout` 调整。请求超时覆盖发送阻塞和响应等待；其他线程也可以调用 `close()` 中断等待。超时、错位响应或断管会关闭当前连接，防止把迟到的结果误当成下一个请求的结果；失败前是否已写入，需要重新连接或打开工作区核对。独立进程协议独占 stdout，核心日志按产品日志设置保存。

## MCP 兼容

现有 `CopperbenchClient.from_workspace(path, token=...)` 保持原行为，仍连接桌面 MCP 服务。选择 `Workspace.open` 不会改变 MCP 的认证、权限或协议，也不会自动启动桌面应用。

## 开发验证

`python -m unittest discover -s sdk/python` 运行 Python 传输与兼容性测试。
`gradlew runNativePythonSmoke` 创建隔离测试工作区，让真实外部 Python 进程通过产品启动器执行元素创建、校验任务、冲突和锁检查；随后连接仍持有写锁的产品会话，验证对象字段修改、跨连接冲突、桌面事件通知、重连和持久化，全程不开 MCP。桌面事件验证使用同一条 Core 订阅边界，未替代实际窗口的视觉验收。
可用 `-PnativePythonExecutable=<python路径>` 指定 Python。Windows 可通过项目的 `scripts/run-gradle-external.ps1 runNativePythonSmoke` 运行。测试输出保留测试工作区路径供排查。
