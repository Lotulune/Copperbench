<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2020-2026, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.
-->

<#-- @formatter:off -->
<#include "../mcitems.ftl">

/*
 *	MCreator note: This file will be REGENERATED on each build.
 */

package ${package}.init;

public class ${JavaModName}Trades {

	public static void load() {
		<@javacompress>
		<#list villagertrades as villagertrade>
			<#list villagertrade.trades as entry>
				<#if villagertrade.isWanderingTrader()>
				net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper.registerWanderingTraderOffers(${entry.level}, factories -> {
					factories.add((trader, random) -> new MerchantOffer(
						new ItemCost(${mappedMCItemToItem(entry.price1)}, ${entry.countPrice1}),
						<#if !entry.price2.isEmpty()>Optional.of(new ItemCost(${mappedMCItemToItem(entry.price2)}, ${entry.countPrice2})),<#else>Optional.empty(),</#if>
						${mappedMCItemToItemStackCode(entry.offer, entry.countOffer)},
						${entry.maxTrades}, ${entry.xp}, ${entry.priceMultiplier}f
					));
				});
				<#else>
				net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper.registerVillagerOffers(${villagertrade.villagerProfession}, ${entry.level}, factories -> {
					factories.add((trader, random) -> new MerchantOffer(
						new ItemCost(${mappedMCItemToItem(entry.price1)}, ${entry.countPrice1}),
						<#if !entry.price2.isEmpty()>Optional.of(new ItemCost(${mappedMCItemToItem(entry.price2)}, ${entry.countPrice2})),<#else>Optional.empty(),</#if>
						${mappedMCItemToItemStackCode(entry.offer, entry.countOffer)},
						${entry.maxTrades}, ${entry.xp}, ${entry.priceMultiplier}f
					));
				});
				</#if>
			</#list>
		</#list>
		</@javacompress>
	}
}
<#-- @formatter:on -->
