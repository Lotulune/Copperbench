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
  readonly linkView?: 'tracks' | 'elements' | 'history' | 'ai' | 'assets' | 'plugins';
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
  license: 'GPL-3.0',
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
    value: 'GPL-3.0',
    badge: 'GPL-3.0',
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
    notes: '编译 + runClient 已宣称。Fabric 走未混淆 Loom。'
  },
  {
    trackName: '前一 26.1',
    minecraftVersion: '26.1',
    statusLabel: 'Fabric / NeoForge 正式支持',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: '钉选 Minecraft 26.1.2。编译 + runClient 已宣称。'
  },
  {
    trackName: '维护 1.21.1',
    minecraftVersion: '1.21.1',
    statusLabel: '正式支持，有黄金构建 / runClient',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: 'Copperbench 官方完全支持，包含 Golden 构建与 runClient 验证（新项目优先首选）'
  },
  {
    trackName: '维护 1.20.1',
    minecraftVersion: '1.20.1',
    statusLabel: 'Fabric / NeoForge 正式支持',
    statusCode: 'TRACK_SUPPORTED',
    isGolden: true,
    notes: 'Fabric 1.20.1 与 NeoForge 1.20.1（钉选 1.20.1-47.1.106）均有编译和 runClient 证据。'
  }
];

export const USER_GUIDE_SECTIONS: readonly UserGuideSection[] = [
  {
    id: 'workspace',
    title: '工作区 (Workspace)',
    content: [
      '一个工作区同一时间只有一个活动生成器（Fabric 或 NeoForge 的某一个版本）。',
      '创建、打开、从官方 MCreator 迁入都走同一套 Java 服务。迁入会复制到新目录，并保留未知字段。',
      '工作区文件扩展名仍是 .mcreator，以便兼容上游插件。用户设置在 %USERPROFILE%\\.copperbench。'
    ]
  },
  {
    id: 'version-tracks',
    title: '版本轨道 (Version Tracks)',
    content: [
      '四轨并进矩阵：新项目优先用 Fabric 1.21.1。',
      '正式支持：Fabric/NeoForge 26.2、26.1.2、1.21.1 与 1.20.1。新项目优先用 Fabric 1.21.1。'
    ],
    table: {
      headers: ['轨道', '状态', '原因代码 / 说明'],
      rows: [
        ['最新 26.2', 'Fabric / NeoForge 正式支持', 'TRACK_SUPPORTED'],
        ['前一 26.1', 'Fabric / NeoForge 正式支持', 'TRACK_SUPPORTED'],
        ['维护 1.21.1', '正式支持，有黄金构建 / runClient', 'TRACK_SUPPORTED (推荐首选)'],
        ['维护 1.20.1', 'Fabric / NeoForge 正式支持', 'TRACK_SUPPORTED']
      ]
    },
    linkView: 'tracks',
    linkLabel: '查看版本与迁移工作台'
  },
  {
    id: 'mod-elements',
    title: '模组元素 (Mod Elements)',
    content: [
      '第一方纵向切片：方块 (Block)、物品 (Item)、配方 (Recipe)、Procedure。四轨 Fabric/NeoForge 均如此。',
      '迁入的上游类型（如 livingentity、GUI）会保留并只读列出，不能在新 UI / MCP 里创建或更新。'
    ],
    linkView: 'elements',
    linkLabel: '进入模组元素工作台'
  },
  {
    id: 'local-history',
    title: '本地历史 (Local History)',
    content: [
      '用「版本 / 恢复点」而不是 Git 术语。',
      '已有远端仓库不会被自动改写。恢复会回到一致快照。'
    ],
    linkView: 'history',
    linkLabel: '查看本地历史与恢复点'
  },
  {
    id: 'mcp-permissions',
    title: 'MCP 权限 (MCP Permissions)',
    content: [
      '本机 MCP 三档：只读 (Read Only)、工作区 (Workspace)、完全访问 (Full Access)。',
      '删除工作区、导出凭据、对外发布、启用 Java 插件必须你亲自确认。',
      '安全准则：AI 不能替你打开 Java 插件。'
    ],
    linkView: 'ai',
    linkLabel: '管理 AI 与 MCP 权限'
  },
  {
    id: 'blockbench-assets',
    title: 'Blockbench 与资源包 (Blockbench & Resource Packs)',
    content: [
      '模型和纹理可以往返 Blockbench。',
      '资源包可以导出 ZIP，并准备到 run/resourcepacks。',
      '产品不会自动启动 Minecraft。Fabric 1.21.1 测试客户端已验证 ResourceManager 会加载该包。'
    ],
    linkView: 'assets',
    linkLabel: '打开资产与 Blockbench 集成'
  },
  {
    id: 'loader-migration',
    title: '加载器迁移 (Loader Migration)',
    content: [
      '只做同版本 Fabric ↔ NeoForge 的安全拷贝。',
      '源工作区只读。',
      '预览报告里的阻断项没清完，不要当成迁移成功。'
    ],
    linkView: 'tracks',
    linkLabel: '前往跨加载器迁移工具'
  },
  {
    id: 'plugins',
    title: '插件 (Plugins)',
    content: [
      '插件兼容分级标准：',
      '• A：资源/生成器模板',
      '• B：不碰 Swing 的 Java 逻辑',
      '• C：Swing 界面，走旧版窗口',
      '• X：拒绝或不兼容',
      'Java 插件默认关闭，启用即完全本机信任。',
      '兼容中心展示已安装插件清单，以及上游工具是走新 UI、旧版窗口，还是明确不支持。'
    ],
    linkView: 'plugins',
    linkLabel: '查看插件兼容中心'
  },
  {
    id: 'install-uninstall',
    title: '安装与卸载 (Install & Uninstall)',
    content: [
      '仅支持 64 位 Windows 11（build 22000 及以上）。Windows 10 会在安装器和启动时被拒绝。',
      '默认打开新产品外壳。旧版 Swing 工作区用 -Dcopperbench.productShell=false。',
      '卸载默认保留 .copperbench 设置。',
      '你自己选的工作区目录不会被卸载删除。',
      'GitHub 安装包没有 Authenticode 签名。Windows SmartScreen 可能提示“已保护你的电脑”，这是预期行为。'
    ]
  }
];
