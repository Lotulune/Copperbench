/**
 * 静态用户指南与使用说明模块。
 *
 * 约束：
 * - 源码来源于仓库 docs/user/README.md。
 * - UI 不得直连文件系统，不得发明打开本地文件的命令或查询。
 * - 界面主要文案采用中文，产品名、版本号、生成器标识等保留英文。
 */

export interface UserGuideSection {
  readonly id: string;
  readonly title: string;
  readonly content: readonly string[];
  readonly table?: {
    readonly headers: readonly string[];
    readonly rows: readonly (readonly string[])[];
  };
  readonly linkView?: 'tracks' | 'elements' | 'history' | 'ai' | 'assets' | 'plugins' | 'new-workspace';
  readonly linkLabel?: string;
}

export interface AboutFact {
  readonly label: string;
  readonly value: string;
  readonly badge?: string;
  readonly badgeType?: 'copper' | 'blue' | 'green' | 'amber';
  readonly description?: string;
}

export interface TrackHonestFact {
  readonly trackName: string;
  readonly minecraftVersion: string;
  readonly statusLabel: string;
  readonly statusCode: 'TRACK_SUPPORTED' | 'TRACK_GENERATE_READY';
  readonly isGolden: boolean;
  readonly notes: string;
}

export const USER_GUIDE_METADATA = {
  sourceDoc: 'docs/user/README.md',
  sourceNotice: '源文档是 docs/user/README.md。本说明为开发测试版说明，不是商店发行手册。产品名 Copperbench 是公开名称。公开分发走 GitHub，安装包未签名。',
  productName: 'Copperbench',
  version: '0.1.0',
  license: 'GPL-3.0-only',
  upstreamOrigin: 'MCreator 2026.2.33518',
  buildStatus: '开发测试版 (Development / Test Build)',
  signingStatus: '未生产签名 (Not Production-Signed)',
  schemaVersion: '1.0'
} as const;

export const ABOUT_FACTS: readonly AboutFact[] = [
  {
    label: '产品名称与版本',
    value: 'Copperbench 0.1.0',
    badge: '0.1.0',
    badgeType: 'copper',
    description: '公开产品名。GitHub GPL 衍生版，采用 UI-Core 1.0 协议'
  },
  {
    label: '开源许可证',
    value: 'GPL-3.0-only',
    badge: 'GPL-3.0-only',
    badgeType: 'green',
    description: '遵循 GNU 通用公共许可证第三版'
  },
  {
    label: '上游基线与衍生关系',
    value: '独立衍生自 MCreator 2026.2.33518',
    badge: '2026.2.33518',
    badgeType: 'blue',
    description: '独立衍生自官方 MCreator 2026.2.33518 发行版'
  },
  {
    label: '构建与发行类型',
    value: '开发测试版 (Development / Test Build)',
    badge: 'Dev/Test',
    badgeType: 'amber',
    description: '供开发者与创作者进行功能与兼容性验证'
  },
  {
    label: '代码签名状态',
    value: '未生产签名 (Not Production-Signed)',
    badge: 'Unsigned',
    badgeType: 'amber',
    description: 'GitHub Releases 安装包不签名；Windows SmartScreen 可能提示。这是分发政策，不是待补证书。'
  }
];

export const TRACK_HONEST_FACTS: readonly TrackHonestFact[] = [
  {
    trackName: '最新 26.2',
    minecraftVersion: '26.2',
    statusLabel: 'Fabric / NeoForge 正式支持',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: 'Fabric 与 NeoForge 均已完成编译与游戏内运行验证。'
  },
  {
    trackName: '前一 26.1',
    minecraftVersion: '26.1',
    statusLabel: 'Fabric / NeoForge 正式支持',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: '钉选 Minecraft 26.1.2，已完成编译与游戏内运行验证。'
  },
  {
    trackName: '维护 1.21.1',
    minecraftVersion: '1.21.1',
    statusLabel: '正式支持（推荐首选）',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: '验证最完整的轨道，新项目优先推荐。'
  },
  {
    trackName: '维护 1.20.1',
    minecraftVersion: '1.20.1',
    statusLabel: 'Fabric / NeoForge 正式支持',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: 'Fabric 与 NeoForge（钉选 1.20.1-47.1.106）均已完成编译与游戏内运行验证。'
  }
];

export const USER_GUIDE_SECTIONS: readonly UserGuideSection[] = [
  {
    id: 'workspace',
    title: '工作区 (Workspace)',
    content: [
      '一个工作区同一时间只使用一个活动生成器（Fabric 或 NeoForge 的某个版本）。',
      '新建、打开、从官方 MCreator 迁入都在产品内完成；迁入会复制到新目录，不改动原工作区。',
      '工作区文件仍使用 .mcreator 扩展名，保持与上游插件生态的兼容。'
    ],
    linkView: 'new-workspace',
    linkLabel: '打开新建工作区表单'
  },
  {
    id: 'version-tracks',
    title: '版本轨道 (Version Tracks)',
    content: [
      '「版本轨道」页集中展示各 Minecraft 版本线的 Fabric / NeoForge 支持状态。',
      '新建工作区时只列出当前可用的生成器，建议选择标记为「正式支持」的轨道。'
    ],
    table: {
      headers: ['轨道', '状态', '说明'],
      rows: [
        ['最新 26.2', '正式支持', 'Fabric 与 NeoForge 均可用'],
        ['前一 26.1', '正式支持', '钉选 Minecraft 26.1.2'],
        ['维护 1.21.1', '正式支持（推荐）', '验证最完整，新项目首选'],
        ['维护 1.20.1', '正式支持', 'Fabric 与 NeoForge 均可用']
      ]
    },
    linkView: 'tracks',
    linkLabel: '查看版本与迁移工作台'
  },
  {
    id: 'mod-elements',
    title: '模组元素 (Mod Elements)',
    content: [
      '可直接可视化创建和编辑：方块、物品、配方、Procedure。',
      '从上游迁入的其他类型元素会保留并只读显示，暂不能在新界面中修改。'
    ],
    linkView: 'elements',
    linkLabel: '进入模组元素工作台'
  },
  {
    id: 'local-history',
    title: '本地历史 (Local History)',
    content: [
      '用「版本 / 恢复点」管理工作区历史，无需了解 Git。',
      '可随时恢复到任一恢复点；已有 Git 远端仓库不会被自动改动。'
    ],
    linkView: 'history',
    linkLabel: '查看本地历史与恢复点'
  },
  {
    id: 'mcp-permissions',
    title: 'MCP 权限 (MCP Permissions)',
    content: [
      '本机 AI 连接分为三档：只读、工作区、完全访问。',
      '删除工作区、导出凭据、对外发布、启用 Java 插件，都必须由你本人确认。'
    ],
    linkView: 'ai',
    linkLabel: '管理 AI 与 MCP 权限'
  },
  {
    id: 'blockbench-assets',
    title: 'Blockbench 与资源包 (Blockbench & Resource Packs)',
    content: [
      '模型和纹理可以在 Blockbench 中编辑后安全导回。',
      '资源包可导出为 ZIP；产品不会自动启动 Minecraft。'
    ],
    linkView: 'assets',
    linkLabel: '打开资产与 Blockbench 集成'
  },
  {
    id: 'loader-migration',
    title: '加载器迁移 (Loader Migration)',
    content: [
      '支持同版本 Fabric ↔ NeoForge 迁移，迁移会生成新副本，原工作区保持只读。',
      '预览报告中的阻断项未处理完之前，请勿视为迁移成功。'
    ],
    linkView: 'tracks',
    linkLabel: '前往跨加载器迁移工具'
  },
  {
    id: 'plugins',
    title: '插件 (Plugins)',
    content: [
      '插件按兼容性分为 A / B / C / X 四级：A 为资源与生成器类，B 为纯逻辑类，C 为带旧版界面的插件，X 为不兼容。',
      'Java 插件默认关闭；启用即视为完全信任其在本机运行。',
      '兼容中心可查看已安装插件清单，以及各上游工具的接入方式。'
    ],
    linkView: 'plugins',
    linkLabel: '查看插件兼容中心'
  },
  {
    id: 'china-network',
    title: '国内网络 (China Network)',
    content: [
      '首次启动会询问你是否在中国大陆，选择后会自动配置国内镜像源。',
      'Gradle 发行版改走华为云镜像，Maven 仓库改走阿里云镜像，Minecraft 库改走 BMCLAPI。',
      '之后可在「偏好设置 → Gradle」中随时开关「使用中国大陆软件源」。',
      '创建工作区时若下载失败，可在失败对话框中直接配置国内源并重试。'
    ]
  },
  {
    id: 'install-uninstall',
    title: '安装与卸载 (Install & Uninstall)',
    content: [
      '仅支持 64 位 Windows 11，Windows 10 无法安装或启动。',
      '卸载默认保留个人设置；你自己选择的工作区目录不会被删除。',
      '安装包未做代码签名，Windows SmartScreen 可能提示「已保护你的电脑」，属预期行为。'
    ]
  }
];
