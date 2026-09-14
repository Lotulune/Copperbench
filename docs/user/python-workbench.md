# Python 工作台

在包含此功能的桌面构建中，导航栏的「Python 工作台」提供脚本编辑器和持久交互控制台。应用管理独立的 CPython 进程，脚本通过原生接口访问当前工程；不需要 MCP、MCP token 或另开工作区。

## 开始使用

1. 打开工程，进入「Python 工作台」。
2. 需要 Python 3.11 或更新版本。默认自动查找解释器；找不到时，点击「选择解释器」选择已有的 Python 可执行文件。也可填写路径。切换解释器后先「重置会话」再运行。
3. 点击「启动会话」，或直接运行脚本。控制台已提供 `cb` 和 `workspace`，无需手工连接。
4. 在「模组元素」中选择一个元素后返回，可以直接读取、修改当前对象：

```python
import copperbench as cb

element = cb.context.active_element
if element:
    print(element.name)
    print(element.fields)
    element.display_name = "共鸣铜铃"
```

编辑器支持打开及另存 `.py` 文件、保留工程独立的本地草稿、执行脚本、异常行定位和代码补全。打开文件不会自动执行。变量、函数、导入的模块在后续运行中保留；切换导航页不会重启解释器。

顶部单独显示解释器状态和脚本结果。例如「就绪 · 脚本失败」表示异常已结束、可以继续输入，不能视为脚本成功。

| 操作 | 快捷键 |
| --- | --- |
| 运行编辑器脚本 | Ctrl+Enter |
| 控制台执行输入 | Enter |
| 控制台换行 | Shift+Enter |
| 代码补全 | Ctrl+空格 |
| 插入四个空格 | Ctrl+Tab |
| 另存脚本 | Ctrl+S |
| 控制台历史 | 上下方向键 |

Tab 保留为键盘焦点导航。输出按流拼接，异常使用不同颜色显示；输出超过保留上限会明确标记截断。

## 对象与上下文

```python
workspace = cb.context.workspace
element = cb.data.elements["copper_bell"]
element.displayName = "铜铃"           # Core 已知的顶层字段可作为属性使用
element["/displayName"] = "共鸣铜铃"   # JSON Pointer，用于嵌套字段等情况
element.update(displayName="精铜铃")  # 多个字段可合并为一次提交

created = cb.data.elements.new("item", "python_item", displayName="脚本物品")
cb.context.active_element = created  # 桌面选择同步变化
print(cb.context.selected_elements)  # 当前界面采用单元素选择
print(cb.context.view)
```

`dir(element)` 展示可访问字段。未知或只读属性赋值会报错，补全和属性检查不会偷偷更新对象的写入 revision。旧对象写入发生冲突时，检查新状态并显式 `element.refresh()` 后再决定是否写入。

`element.fields`、`cb.data.assets` 和 `cb.data.registries` 是读取快照；修改返回的普通字典不会自动提交。持久修改使用元素属性、下标、`update(...)` 或 `cb.ops`。`cb.data.texts.new(name, body)` 提供解释器会话内的文本块，支持 `write()`、`clear()`、`as_string()`；不会自动写文件或替换编辑器草稿。

## Core 操作与自定义操作

```python
print(dir(cb.ops))
print(cb.api_help())
accepted = cb.ops.workspace.validate()
result = workspace.wait_task(accepted["task"]["id"])
print(result["data"]["task"]["state"])
```

`cb.ops` 提供宿主声明的 Core 操作名，以及 `workspace`、`elements`、`tasks`、`history` 等便捷分组。参数和结果沿用 Core 契约；受理任务不等于执行成功。批准、只读限制和冲突检查继续由 Core 执行，脚本不能伪造 `UI` 身份。

```python
class RenameSelected(cb.types.Operator):
    idname = "tools.rename_selected"
    label = "修改选中元素名称"

    @classmethod
    def poll(cls, context):
        return context.active_element is not None

    def execute(self, context, name="脚本命名"):
        context.active_element.display_name = name

cb.utils.register_class(RenameSelected)
cb.ops.tools.rename_selected(name="铜铃")
# 卸载后可重新注册；重置解释器也会清除注册项。
cb.utils.unregister_class(RenameSelected)
```

注册后，自定义操作会显示在工作台侧栏，也可通过 `cb.ops` 调用。重复注册同名操作会报错，避免静默替换已有操作。

## 定时器与变化回调

```python
def report_selection():
    print(cb.context.active_element)
    return None  # 返回秒数表示再次调度，None 表示结束

cb.app.timers.register(report_selection, first_interval=1)

def changed(context):
    print("工程 revision：", context.workspace.revision)

cb.app.handlers.workspace_update.append(changed)
# 也有 selection_update。
# cb.app.handlers.workspace_update.remove(changed)
```

回调在解释器空闲时执行，变化通知可合并，不承诺逐条重放每次底层事件；长时间运行的脚本会延迟回调。单个回调异常会记录到控制台，不阻止其他回调。外部 Python 使用这套接口时，可调用 `cb.app.process_events()` 驱动自己的事件循环。

## 停止、恢复与边界

「停止」或「重置会话」会结束解释器及其所属子进程，清空变量、操作注册、定时器和会话文本。桌面工程继续打开，已提交的修改保留。脚本提交到 Core 的构建或游戏运行任务需在任务面板或通过 `cancel_task()` 单独取消。

脚本每次修改调用立即提交，后续异常不会自动回滚之前的调用。需要原子批量操作时使用现有 Workspace Plan；需要恢复时使用产品的本地历史及审批流程。`input()` 不在工作台提供交互输入，使用控制台变量或操作参数传值。

这是一套面向 Copperbench 工程的应用内脚本接口，采用 `context/data/ops/types/utils/app` 的使用方式；不是 Blender `bpy` 插件的二进制或源码兼容层，也不开放任意内部 Java 对象。解释器由应用管理，但当前构建仍需可用的 Python 运行时；不会静默下载解释器或自动运行工程脚本。
