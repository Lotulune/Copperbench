package dev.copperbench.generated.wayfinder_bell;

import dev.copperbench.generated.wayfinder_bell.init.ModBlocks;
import dev.copperbench.generated.wayfinder_bell.init.ModItems;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WayfinderBellMod implements ModInitializer {
	public static final String MOD_ID = "wayfinder_bell";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override public void onInitialize() {
		LOGGER.info("COPPERBENCH_STAGE3_READY");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
