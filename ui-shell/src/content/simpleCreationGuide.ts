import { uiText } from '../i18n';

export const simpleCreationPrompt = () => uiText(
  '在当前工作区创建一个名为 Trail Block 的普通方块，使用内置石头纹理，硬度 3、抗性 6，并提供对应方块物品。请先检查当前生成器和可用字段，通过公共 SDK 保存并构建，告诉我实际结果及下一步如何试玩。',
  'Create a normal block named Trail Block in the current workspace, with the built-in stone texture, hardness 3, resistance 6 and its block item. Inspect the current generator and available fields first. Save and build through the public SDK, then report the actual results and how to playtest.');

export const simpleCreationSteps = () => [
  [uiText('准备助手', 'Prepare an assistant'), uiText('打开“安装与连接助手”，选择已安装的 CLI，或按官方指南安装。完成登录后重新检测，在输入框底部选择模型；列表不可用时可输入模型 ID。CLI 通道检查不代表模型已经可用。', 'Open “Install and connect assistants”. Choose an installed CLI or follow its official setup. Complete sign-in and detect it again, then select a model below the composer or enter its ID. A CLI handshake does not verify the model.')],
  [uiText('新建作品', 'Create a workspace'), uiText('选择“新建作品”，填写名称和新的空目录，选择 Fabric 1.21.1。创建完成后核对作品名称与版本。', 'Select “New creation”, enter a name and a new empty directory, and choose Fabric 1.21.1. Check the resulting name and version.')],
  [uiText('输入需求', 'Send your idea'), uiText('复制下方示例需求，返回创作页面粘贴并发送。根据实际审批内容授权；遇到不清楚的需求先回答助手的问题。', 'Copy the example below, return to creation and send it. Review actual approval details before allowing actions, and answer any clarifying questions.')],
  [uiText('审阅与保存', 'Inspect saved content'), uiText('在作品内容中选择 Trail Block，查看真实元素、名称、硬度和纹理。需要更多字段时切换专业模式。回复中的“完成”不代表内容已经保存。', 'Select Trail Block in Creation content and inspect its actual name, hardness and texture. Switch to Professional for more fields. A reply saying “done” does not establish that content was saved.')],
  [uiText('构建与修复', 'Build and repair'), uiText('从“查看进度”核对构建任务的最终结果。失败时读取诊断，修复模型配置、字段或构建问题后查看新任务。状态刷新不会重新发送需求；继续前先核对已有任务。', 'Use “View task” to inspect the final build result. On failure, read diagnostics, repair the configuration, fields or build and check the new task. Refreshing status does not resend a request; inspect existing tasks before continuing.')],
  [uiText('试玩与保存检查', 'Playtest and check persistence'), uiText('点击“在游戏中试玩”，创建独立测试世界，获得并放置方块。正常退出客户端，再启动新客户端并打开同一世界，检查方块和物品是否保留。', 'Select “Playtest in game”, create a separate test world, obtain and place the block. Exit normally, start a new client and reopen the same world to check the block and inventory.')],
  [uiText('导出与继续', 'Export and continue'), uiText('使用“导出模组”，核对实际产物与导出位置。退出并重开作品后，选择原对话继续。停止助手会保留修改；Core 构建等任务需在任务面板单独查看或停止。', 'Use “Export mod” and inspect the artifact and destination. Reopen the creation and select the original conversation to continue. Stopping the assistant retains changes; inspect or stop Core tasks separately in the task panel.')]
];
