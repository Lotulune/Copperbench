export type AssetCategory = 'model' | 'texture' | 'animation' | 'language' | 'sound' | 'resource_pack';
export type AssetValidationStatus = 'ready' | 'draft' | 'warning' | 'error';

/** Stable identifiers mirror the eventual UI-Core asset projection contract. */
export interface AssetRecord {
  readonly id: string;
  readonly name: string;
  readonly category: AssetCategory;
  readonly categoryLabel: string;
  readonly path: string;
  readonly format: string;
  readonly size: string;
  readonly dimensions?: string;
  readonly updatedAt: string;
  readonly source: 'workspace' | 'minecraft' | 'blockbench';
  readonly sourceLabel: string;
  readonly references: readonly string[];
  readonly validation: AssetValidationStatus;
  readonly validationLabel: string;
  readonly description: string;
}

export const ASSET_FIXTURES: readonly AssetRecord[] = [
  {
    id: 'asset:1111111111111111111111111111111111111111111111111111111111111111', name: 'copper_lamp', category: 'model', categoryLabel: '模型',
    path: 'assets/coppertrails/models/block/copper_lamp.bbmodel', format: 'BBMODEL', size: '18.4 KB', dimensions: '16 × 16 × 16 px',
    updatedAt: '2026-08-17T15:42:00Z', source: 'blockbench', sourceLabel: 'Blockbench', references: ['block/copper_lamp', 'item/copper_lamp'],
    validation: 'ready', validationLabel: '已校验', description: '铜灯方块的 Blockbench 源模型，包含方块与物品展示引用。'
  },
  {
    id: 'asset:2222222222222222222222222222222222222222222222222222222222222222', name: 'copper_lamp', category: 'texture', categoryLabel: '纹理',
    path: 'assets/coppertrails/textures/block/copper_lamp.png', format: 'PNG', size: '6.2 KB', dimensions: '16 × 16 px',
    updatedAt: '2026-08-17T15:40:00Z', source: 'workspace', sourceLabel: '工作区', references: ['model:copper_lamp'],
    validation: 'ready', validationLabel: '已校验', description: '铜灯方块的像素纹理，已被模型材质引用。'
  },
  {
    id: 'asset:3333333333333333333333333333333333333333333333333333333333333333', name: 'copper_lamp_idle', category: 'animation', categoryLabel: '动画',
    path: 'assets/coppertrails/animations/copper_lamp_idle.animation.json', format: 'JSON', size: '2.8 KB', dimensions: '12 帧',
    updatedAt: '2026-08-16T08:12:00Z', source: 'blockbench', sourceLabel: 'Blockbench', references: ['model:copper_lamp'],
    validation: 'warning', validationLabel: '需检查', description: '铜灯微光动画，当前检测到一个未绑定的可选骨骼轨道。'
  },
  {
    id: 'asset:4444444444444444444444444444444444444444444444444444444444444444', name: 'zh_cn', category: 'language', categoryLabel: '语言',
    path: 'assets/coppertrails/lang/zh_cn.json', format: 'JSON', size: '1.1 KB',
    updatedAt: '2026-08-15T11:25:00Z', source: 'workspace', sourceLabel: '工作区', references: ['block.coppertrails.copper_lamp', 'item.coppertrails.copper_lamp'],
    validation: 'ready', validationLabel: '已校验', description: '简体中文本地化文本，包含当前工作区公开的元素名称。'
  },
  {
    id: 'asset:5555555555555555555555555555555555555555555555555555555555555555', name: 'copper_chime', category: 'sound', categoryLabel: '声音',
    path: 'assets/coppertrails/sounds/copper_chime.ogg', format: 'OGG', size: '42.7 KB', dimensions: '0:03.20',
    updatedAt: '2026-08-14T19:03:00Z', source: 'workspace', sourceLabel: '工作区', references: ['block/copper_lamp'],
    validation: 'draft', validationLabel: '草稿', description: '铜灯交互音效，尚未在测试客户端中试听确认。'
  },
  {
    id: 'asset:6666666666666666666666666666666666666666666666666666666666666666', name: 'coppertrails_resources', category: 'resource_pack', categoryLabel: '资源包',
    path: 'resourcepacks/coppertrails_resources.zip', format: 'ZIP', size: '74.3 KB',
    updatedAt: '2026-08-13T16:48:00Z', source: 'workspace', sourceLabel: '工作区', references: ['model:copper_lamp', 'texture:copper_lamp', 'language:zh_cn'],
    validation: 'ready', validationLabel: '已校验', description: '可独立启用的资源包导出，包含铜灯模型、纹理及语言文件。'
  }
];
