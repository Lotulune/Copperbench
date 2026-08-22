/**
 * 中文词条表（主语言，2026-08-17 产品决策）。
 * key 与 ui-core 合同 LocalizedText.key 及 mock 生成投影的 key 一一对应；
 * 新增合同 key 时必须同步补齐词条，缺失时 UI 回退英文 fallback。
 */
export const zh: Record<string, string> = {
  /* ---- 诊断 (diagnostic.*) ---- */
  'diagnostic.field_value_out_of_range': '硬度必须在 {min} 到 {max} 之间。',
  'diagnostic.permission_profile_denied': '需要工作区写入权限才能执行构建。',
  'diagnostic.workspace_revision_conflict': '此编辑器打开后工作区已被其他写入者修改，为避免覆盖，本次提交未生效。',
  'diagnostic.external_process_exited': 'Minecraft 以退出码 {exitCode} 意外退出，请检查崩溃日志。',
  'diagnostic.ui_core_schema_incompatible': 'UI 与 Java Core 没有共同支持的协议版本。',

  /* ---- 诊断动作 (action.*) ---- */
  'action.open_field': '定位问题字段',
  'action.open_logs': '查看日志',
  'action.refresh': '查看最新改动',
  'action.request_permission': '申请提升权限',

  /* ---- 能力与帮助 ---- */
  'capability.active_loader_unsupported_field': '该 NeoForge 字段已保留，但在 Fabric 活动生成器下不可编辑。',
  'field.loader_specific_preserved': '该值已保留，但当前加载器下不生效。',

  /* ---- 编辑器投影 (editor.* / field.* / material.*) ---- */
  'editor.general': '通用属性',
  'editor.behavior': '物理与挖掘特性',
  'editor.block_behavior': '物理与行为特性',
  'field.name': '内部标识符',
  'field.displayName': '显示名称',
  'field.hardness': '硬度',
  'field.material': '材质',
  'field.flammable': '可燃',
  'field.fire_spread_speed': '火焰蔓延速度',
  'material.wood': '木材',
  'material.stone': '石材',
  'material.metal': '金属',

  /* ---- 任务阶段 (task.*) ---- */
  'task.starting': '正在启动任务…',
  'task.resolving_dependencies': '正在解析依赖…',
  'task.compiling': '正在编译生成源码…',
  'task.completed': '任务完成',
  'task.cancelled': '任务已取消',
  'task.client_exited': 'Minecraft 测试客户端意外退出',

  /* ---- 场景标题 (scenario.*)，测试托盘用 ---- */
  'scenario.ready': '就绪工作区',
  'scenario.empty_workspace': '空白工作区',
  'scenario.loading_workbench': '加载中',
  'scenario.validation_failed': '字段校验失败',
  'scenario.permission_denied': '权限被拒绝',
  'scenario.revision_conflict': '版本并发冲突',
  'scenario.partial_capability': '加载器差异字段',
  'scenario.offline': '离线工作模式',
  'scenario.build_running': '构建任务执行中',
  'scenario.external_process_exited': '外部进程异常退出',
  'scenario.bridge_recovery': '渲染进程崩溃恢复',
  'scenario.schema_incompatible': '协议版本不兼容',
  'scenario.element_created': '元素创建并提交',

  /* ---- U3 版本轨道状态与原因代码 (status.* / reason.*) ---- */
  'status.supported': '正式支持',
  'status.preview': '技术预览',
  'status.unavailable': '暂不可用',
  'status.coincides': '并轨共用',
  'reason.TRACK_SUPPORTED': 'Copperbench 官方完全支持，包含 Golden 构建与 runClient 验证。',
  'reason.TRACK_GENERATE_READY': '第一方纵向切片已可生成，但尚未声明 Golden 编译证据。',
  'reason.TRACK_GOLDEN_PENDING': '包含内置生成器插件，但尚未声明第一方 Golden 构建证据。',
  'reason.TRACK_COINCIDES_WITH_FIXED': '该稳定轨与固定定点轨重合，共用同一第一方生成器。',
  'reason.VERSION_TRACK_GENERATOR_MISSING': '未提供对应第一方或内置生成器插件。',

  /* ---- U3 迁移结果分组 (disposition.*) ---- */
  'disposition.supported': '完全支持',
  'disposition.substitute': '等价替换',
  'disposition.lost': '丢失 / 降级',
  'disposition.blocked': '阻断',
  'disposition.manual': '需手动处理',

  /* ---- U3 诊断 ---- */
  'diagnostic.migration_confirmation_required': '用户必须确认迁移差异后方可执行。',
  'diagnostic.migration_incomplete': '该目标加载器处于技术预览或未完全支持状态，尚未完成自动迁移。',
  'diagnostic.user_approval_required': '迁入上游工作区将创建新副本，需要用户显式确认。',
  'diagnostic.permission_denied': '迁入上游工作区需要桌面 Full Access 权限。',

  /* ---- 新建工作区诊断 (diagnostic.*) ---- */
  'diagnostic.user_approval_required_workspace': '创建工作区会写入新文件夹，需要用户确认。',
  'diagnostic.mod_name_invalid': '模组名称不能为空，且不能超过 64 个字符。',
  'diagnostic.mod_id_invalid': '模组 ID 必须为 2-32 位小写字母、数字或下划线，且以小写字母开头。',
  'diagnostic.package_name_invalid': 'Java 包名无效，须为小写段以点号分隔（例如 net.example.mymod）。',
  'diagnostic.workspace_folder_required': '必须提供工作区文件夹路径。',
  'diagnostic.workspace_folder_outside_root': '工作区文件夹必须位于建议的工作区根目录之下。',
  'diagnostic.workspace_folder_not_empty': '目标文件夹已存在且不为空，请选择一个空文件夹。',
  'diagnostic.unsupported_generator': '所选生成器不在第一方支持列表中。',
  'diagnostic.generator_not_installed': '所选生成器插件未安装或未加载。',
  'diagnostic.workspace_create_failed': '工作区文件写入失败，请检查磁盘权限与路径。',
  'diagnostic.bridge_transport_failed': '与 Java Core 的通信失败，工作区未创建。'
};
