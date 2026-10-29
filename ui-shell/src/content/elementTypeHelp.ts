import type { ModElementType } from '../types/contract';
import { uiText } from '../i18n';

const descriptions: Record<ModElementType, readonly [string, string]> = {
  block: ['可放置、挖掘的方块。', 'A block players can place and mine.'],
  item: ['可持有和使用的物品。', 'An item players can hold and use.'],
  recipe: ['合成、熔炼等物品配方。', 'Crafting, smelting and other item recipes.'],
  procedure: ['通过可视化节点定义事件行为。', 'Define event behavior with visual nodes.'],
  function: ['按顺序执行游戏命令。', 'Run a sequence of game commands.'],
  loottable: ['控制容器或实体的随机掉落。', 'Control random loot from containers or entities.'],
  achievement: ['设置进度目标、条件和奖励。', 'Set advancement goals, criteria and rewards.'],
  armor: ['玩家可穿戴的盔甲。', 'Armor players can equip.'],
  armortrim: ['盔甲纹饰的图案或材料。', 'Armor trim patterns or materials.'],
  tool: ['挖掘或战斗使用的工具。', 'Tools for mining or combat.'],
  itemextension: ['扩展已有物品的行为。', 'Extend the behavior of existing items.'],
  attribute: ['实体的数值属性。', 'A numeric attribute for entities.'],
  bannerpattern: ['旗帜可使用的图案。', 'A pattern for banners.'],
  command: ['可在聊天或控制台调用的命令。', 'A command available in chat or the console.'],
  damagetype: ['定义伤害来源和规则。', 'Define a damage source and its rules.'],
  enchantment: ['添加装备附魔。', 'Add an enchantment for equipment.'],
  gamerule: ['可在世界中调整的游戏规则。', 'A game rule configurable per world.'],
  keybind: ['将按键与游戏行为关联。', 'Connect a key binding to game behavior.'],
  painting: ['可挂在墙上的画。', 'A painting players can hang on a wall.'],
  particle: ['用于视觉效果的粒子。', 'A particle for visual effects.'],
  potion: ['组合药水效果。', 'Combine effects into a potion.'],
  potioneffect: ['影响实体的持续状态。', 'A lasting status effect for entities.'],
  tab: ['整理创造模式物品分类。', 'Organize items in a creative inventory tab.'],
  villagerprofession: ['村民职业及关联工作站。', 'A villager profession and its workstation.'],
  villagertrade: ['设置村民的交易。', 'Define trades offered by villagers.'],
  biome: ['配置环境、地表和生成内容。', 'Configure a biome’s environment and generation.'],
  dimension: ['独立的世界维度。', 'A separate world dimension.'],
  feature: ['世界生成时放置的地物。', 'A feature placed during world generation.'],
  fluid: ['可流动的液体。', 'A flowing fluid.'],
  plant: ['可生长或装饰的植物。', 'A growing or decorative plant.'],
  structure: ['在世界中生成的结构。', 'A structure generated in the world.'],
  livingentity: ['具有外观和行为的生物。', 'A creature with appearance and behavior.'],
  specialentity: ['特殊用途的实体。', 'An entity with specialized behavior.'],
  projectile: ['可发射的实体。', 'An entity that can be launched.'],
  gui: ['设计游戏内交互界面。', 'Design an interactive in-game screen.'],
  overlay: ['在游戏画面上显示信息。', 'Display information over the game view.'],
  code: ['编写自定义源码。', 'Write custom source code.']
};
export const COMMON_ELEMENT_TYPES: ModElementType[] = ['block', 'item', 'recipe', 'procedure', 'function', 'loottable', 'achievement'];
export const elementTypeDescription = (type: ModElementType) => uiText(...descriptions[type]);
