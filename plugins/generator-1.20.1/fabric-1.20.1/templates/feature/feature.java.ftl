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
<#include "../procedures.java.ftl">
<#assign biomeSelector = "includeByKey">
<#assign resourceKey = "ResourceKey">
<#if data.restrictionBiomes?has_content>
	<#list w.filterBrokenReferences(data.restrictionBiomes) as restrictionBiome>
		<#if restrictionBiome?contains("#")>
			<#assign biomeSelector = "tag">
			<#assign resourceKey = "TagKey">
			<#break>
		</#if>
	</#list>
</#if>
package ${package}.world.features;

<#assign mappedFeatureType = "NoOpFeature">
<#assign configuration = "NoneFeatureConfiguration">
<#if featuretype?has_content>
	<#assign mappedFeatureCandidate = generator.map(featuretype, "features")!"">
	<#assign configurationCandidate = generator.map(featuretype, "features", 1)!"">
	<#if mappedFeatureCandidate?trim?matches("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")>
		<#assign mappedFeatureType = mappedFeatureCandidate?trim>
	</#if>
	<#if configurationCandidate?trim?matches("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")>
		<#assign configuration = configurationCandidate?trim>
	</#if>
</#if>

<@javacompress>
public class ${name}Feature extends ${mappedFeatureType} {

	public ${name}Feature() {
		super(${configuration}.CODEC);
	}

	public static final Predicate<BiomeSelectionContext> GENERATE_BIOMES = BiomeSelectors.
	<#if data.restrictionBiomes?has_content>
	${biomeSelector}(
		<#list w.filterBrokenReferences(data.restrictionBiomes) as restrictionBiome>
			${resourceKey}.create(Registries.BIOME, new ResourceLocation("${restrictionBiome?replace("#", "")}"))<#sep>,
		</#list>
	)
	<#else>
	all()
	</#if>;

	<#if hasProcedure(data.generateCondition)>
	@Override public boolean place(FeaturePlaceContext<${configuration}> context) {
		<#-- #4781 - we need to use WorldGenLevel instead of Level, or one can run incompatible procedures in condition -->
		WorldGenLevel world = context.level();
		int x = context.origin().getX();
		int y = context.origin().getY();
		int z = context.origin().getZ();
		if (!<@procedureOBJToConditionCode data.generateCondition/>)
			return false;

		return super.place(context);
	}
	</#if>
}</@javacompress>
<#-- @formatter:on -->
