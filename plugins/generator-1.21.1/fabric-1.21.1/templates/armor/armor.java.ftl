<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2012-2020, Pylo
 # Copyright (C) 2020-2026, Pylo, opensource contributors
 # Copyright (C) 2020-2026, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.
 #
 # Fabric-Generator-MCreator is distributed in the hope that it will be useful,
 # but WITHOUT ANY WARRANTY; without even the implied warranty of
 # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 # GNU General Public License for more details.
 #
 # You should have received a copy of the GNU General Public License
 # along with Fabric-Generator-MCreator. If not, see <https://www.gnu.org/licenses/>.
-->

<#-- @formatter:off -->
<#include "../mcitems.ftl">
<#include "../procedures.java.ftl">
<#include "../triggers.java.ftl">

package ${package}.item;

import java.util.EnumMap;

<@javacompress>
public abstract class ${name}Item extends ArmorItem {

	public static final Holder<ArmorMaterial> ARMOR_MATERIAL = Registry.registerForHolder(
		BuiltInRegistries.ARMOR_MATERIAL,
		ResourceLocation.fromNamespaceAndPath(${JavaModName}.MODID, "${registryname}"),
		new ArmorMaterial(
			Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
				map.put(ArmorItem.Type.BOOTS, ${data.damageValueBoots});
				map.put(ArmorItem.Type.LEGGINGS, ${data.damageValueLeggings});
				map.put(ArmorItem.Type.CHESTPLATE, ${data.damageValueBody});
				map.put(ArmorItem.Type.HELMET, ${data.damageValueHelmet});
				map.put(ArmorItem.Type.BODY, ${data.damageValueBody});
			}),
			${data.enchantability},
			<#if data.equipSound?has_content && data.equipSound.getUnmappedValue()?has_content>
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("${data.equipSound}"))),
			<#else>
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY),
			</#if>
			() -> ${mappedMCItemsToIngredient(data.repairItems)},
			List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(${JavaModName}.MODID, "${data.armorTextureFile}"))),
			${data.toughness}f,
			${data.knockbackResistance}f
		)
	);

	private ${name}Item(ArmorItem.Type type, Item.Properties properties) {
		super(ARMOR_MATERIAL, type, properties);
	}

	<#if data.enableHelmet>
	public static class Helmet extends ${name}Item {

		public Helmet(Item.Properties properties) {
			super(ArmorItem.Type.HELMET, properties.durability(ArmorItem.Type.HELMET.getDurability(${data.maxDamage}))<#if data.helmetImmuneToFire>.fireResistant()</#if><#if data.rarity != "COMMON">.rarity(Rarity.${data.rarity})</#if>
					<@itemAttributeModifiers data.attributeModifiers?filter(e -> e.armorPieces[0]) "helmet" "EquipmentSlotGroup.HEAD" data.damageValueHelmet/>);
		}

		<@addSpecialInformation data.helmetSpecialInformation, "item." + modid + "." + registryname + "_helmet"/>

		<@hasGlow data.helmetGlowCondition/>

		<@piglinNeutral data.helmetPiglinNeutral/>

		<@onArmorTick data.onHelmetTick/>
	}
	</#if>

	<#if data.enableBody>
	public static class Chestplate extends ${name}Item {

		public Chestplate(Item.Properties properties) {
			super(ArmorItem.Type.CHESTPLATE, properties.durability(ArmorItem.Type.CHESTPLATE.getDurability(${data.maxDamage}))<#if data.bodyImmuneToFire>.fireResistant()</#if><#if data.rarity != "COMMON">.rarity(Rarity.${data.rarity})</#if>
					<@itemAttributeModifiers data.attributeModifiers?filter(e -> e.armorPieces[1]) "chestplate" "EquipmentSlotGroup.CHEST" data.damageValueBody/>);
		}

		<@addSpecialInformation data.bodySpecialInformation, "item." + modid + "." + registryname + "_chestplate"/>

		<@hasGlow data.bodyGlowCondition/>

		<@piglinNeutral data.bodyPiglinNeutral/>

		<@onArmorTick data.onBodyTick/>
	}
	</#if>

	<#if data.enableLeggings>
	public static class Leggings extends ${name}Item {

		public Leggings(Item.Properties properties) {
			super(ArmorItem.Type.LEGGINGS, properties.durability(ArmorItem.Type.LEGGINGS.getDurability(${data.maxDamage}))<#if data.leggingsImmuneToFire>.fireResistant()</#if><#if data.rarity != "COMMON">.rarity(Rarity.${data.rarity})</#if>
					<@itemAttributeModifiers data.attributeModifiers?filter(e -> e.armorPieces[2]) "leggings" "EquipmentSlotGroup.LEGS" data.damageValueLeggings/>);
		}

		<@addSpecialInformation data.leggingsSpecialInformation, "item." + modid + "." + registryname + "_leggings"/>

		<@hasGlow data.leggingsGlowCondition/>

		<@piglinNeutral data.leggingsPiglinNeutral/>

		<@onArmorTick data.onLeggingsTick/>
	}
	</#if>

	<#if data.enableBoots>
	public static class Boots extends ${name}Item {

		public Boots(Item.Properties properties) {
			super(ArmorItem.Type.BOOTS, properties.durability(ArmorItem.Type.BOOTS.getDurability(${data.maxDamage}))<#if data.bootsImmuneToFire>.fireResistant()</#if><#if data.rarity != "COMMON">.rarity(Rarity.${data.rarity})</#if>
					<@itemAttributeModifiers data.attributeModifiers?filter(e -> e.armorPieces[3]) "boots" "EquipmentSlotGroup.FEET" data.damageValueBoots/>);
		}

		<@addSpecialInformation data.bootsSpecialInformation, "item." + modid + "." + registryname + "_boots"/>

		<@hasGlow data.bootsGlowCondition/>

		<@piglinNeutral data.bootsPiglinNeutral/>

		<@onArmorTick data.onBootsTick/>
	}
	</#if>

}
</@javacompress>
<#-- @formatter:on -->

<#macro itemAttributeModifiers modifiers armorPart defaultEquipSlot defense>
<#if modifiers?size != 0>
.attributes(
	ItemAttributeModifiers.builder()
	<#-- First add the default armor attributes -->
	.add(Attributes.ARMOR, new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.${armorPart}"), ${defense}, AttributeModifier.Operation.ADD_VALUE), ${defaultEquipSlot})
	<#if data.toughness != 0>.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.${armorPart}"), ${data.toughness}, AttributeModifier.Operation.ADD_VALUE), ${defaultEquipSlot})</#if>
	<#if data.knockbackResistance != 0>.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(ResourceLocation.withDefaultNamespace("armor.${armorPart}"), ${data.knockbackResistance}, AttributeModifier.Operation.ADD_VALUE), ${defaultEquipSlot})</#if>
	<#-- Then add the custom modifiers -->
	<#list modifiers as modifier>
	.add(${modifier.attribute}, new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(${JavaModName}.MODID, "${registryname}_${modifier?index}.${armorPart}"),
			${modifier.amount}, AttributeModifier.Operation.${modifier.operation}),
			<#if modifier.equipmentSlot.getUnmappedValue() == "default">${defaultEquipSlot}<#else>${modifier.equipmentSlot}</#if>)
	</#list>.build()
)
</#if>
</#macro>