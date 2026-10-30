import {
  AppWindow, Box, Castle, Code2, Coins, Crosshair, Droplets, FileCode2, FlaskConical, Flag, Frame, Gem, Ghost, Gift, Globe,
  Keyboard, Layers, LayoutGrid, Mountain, PackagePlus, PawPrint, Pickaxe, Scroll, Settings2, Shield, SlidersHorizontal,
  Sparkle, Sparkles, Sprout, Swords, Terminal, TreePine, Trophy, Users, WandSparkles, Workflow, type LucideIcon
} from 'lucide-react';

/** One identity plate per element type: the same icon and index-tab tint wherever the element appears. */
export type ElementTone = 'blue' | 'sand' | 'sage' | 'rose' | 'neutral';

const visuals: Record<string, [LucideIcon, ElementTone]> = {
  block: [Box, 'blue'], plant: [Sprout, 'blue'], fluid: [Droplets, 'blue'], structure: [Castle, 'blue'], feature: [TreePine, 'blue'],
  biome: [Mountain, 'blue'], dimension: [Globe, 'blue'],
  item: [Gem, 'sand'], tool: [Pickaxe, 'sand'], armor: [Shield, 'sand'], armortrim: [Sparkle, 'sand'], itemextension: [PackagePlus, 'sand'],
  potion: [FlaskConical, 'sand'], potioneffect: [Sparkles, 'sand'], enchantment: [WandSparkles, 'sand'], tab: [LayoutGrid, 'sand'],
  bannerpattern: [Flag, 'sand'], painting: [Frame, 'sand'],
  procedure: [Workflow, 'sage'], function: [FileCode2, 'sage'], command: [Terminal, 'sage'], code: [Code2, 'sage'], gamerule: [Settings2, 'sage'],
  keybind: [Keyboard, 'sage'], attribute: [SlidersHorizontal, 'sage'], damagetype: [Swords, 'sage'],
  recipe: [Scroll, 'rose'], loottable: [Gift, 'rose'], achievement: [Trophy, 'rose'], villagertrade: [Coins, 'rose'], villagerprofession: [Users, 'rose'],
  livingentity: [PawPrint, 'rose'], specialentity: [Ghost, 'rose'], projectile: [Crosshair, 'rose'],
  gui: [AppWindow, 'neutral'], overlay: [Layers, 'neutral'], particle: [Sparkles, 'neutral']
};

export function elementVisual(type: string): { Icon: LucideIcon; tone: ElementTone } {
  const [Icon, tone] = visuals[type] ?? [Box, 'neutral'];
  return { Icon, tone };
}
