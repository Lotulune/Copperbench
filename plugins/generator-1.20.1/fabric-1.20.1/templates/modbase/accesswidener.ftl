accessWidener v2 named

<#if w.getGElementsOfType("biome")?filter(e -> e.spawnBiome || e.spawnInCaves || e.spawnBiomeNether)?size != 0>
accessible class net/minecraft/world/level/levelgen/SurfaceRules$SequenceRuleSource
accessible method net/minecraft/world/level/levelgen/SurfaceRules$SequenceRuleSource <init> (Ljava/util/List;)V
accessible class net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset$SourceProvider
</#if>

<#if w.getGElementsOfType("biome")?filter(e -> e.hasVines() || e.hasFruits())?size != 0>
extendable method net/minecraft/world/level/levelgen/feature/treedecorators/TreeDecoratorType <init> (Lcom/mojang/serialization/Codec;)V
</#if>

<#if w.hasElementsOfType("feature")>
accessible method net/minecraft/world/level/levelgen/feature/ScatteredOreFeature <init> (Lcom/mojang/serialization/Codec;)V
extendable method net/minecraft/world/level/levelgen/feature/TreeFeature place (Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z
</#if>

<#if w.getGElementsOfType('tool')?filter(e -> e.toolType.equals('Fishing rod'))?size != 0>
extendable method net/minecraft/world/entity/projectile/FishingHook shouldStopFishing (Lnet/minecraft/world/entity/player/Player;)Z
</#if>

<#if w.getGElementsOfType('livingentity')?filter(e -> e.spawnInDungeons)?size != 0>
accessible method net/minecraft/world/level/levelgen/feature/MonsterRoomFeature randomEntityId (Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/entity/EntityType;
</#if>

<#if w.getGElementsOfType('block')?filter(e -> e.isSign())?size != 0>
accessible method net/minecraft/world/level/block/state/properties/WoodType register (Lnet/minecraft/world/level/block/state/properties/WoodType;)Lnet/minecraft/world/level/block/state/properties/WoodType;
</#if>

accessible field net/minecraft/world/item/BucketItem content Lnet/minecraft/world/level/material/Fluid;
accessible field net/minecraft/world/level/block/LiquidBlock fluid Lnet/minecraft/world/level/material/FlowingFluid;

accessible method net/minecraft/world/item/CreativeModeTab$Builder type (Lnet/minecraft/world/item/CreativeModeTab$Type;)Lnet/minecraft/world/item/CreativeModeTab$Builder;

# Start of user code block custom AWs
# End of user code block custom AWs
