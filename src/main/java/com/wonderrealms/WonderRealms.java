package com.wonderrealms;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * Wonder Realms: worlds beyond the everyday. Tiny Worlds (shrink to insect size, pixel mining, Pixel Miners, rhino
 * beetles, dragonflies and grasshoppers to ride, fireflies to catch), dragons (hatch, raise, ride, and the Elder
 * Dragon to defeat), sky islands, sky whales and airships.
 */
public class WonderRealms implements ModInitializer {
	public static final String MOD_ID = "wonderrealms";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		TinyWorlds.init();
		com.wonderrealms.dragon.Dragons.init();
		com.wonderrealms.miner.PixelMiners.init();
		com.wonderrealms.beetle.Beetles.init();
		com.wonderrealms.dragonfly.Dragonflies.init();
		com.wonderrealms.grasshopper.Grasshoppers.init();
		com.wonderrealms.firefly.Fireflies.init();
		com.wonderrealms.whale.SkyWhales.init();
		com.wonderrealms.pixel.PixelMining.init();
		com.wonderrealms.airship.Airships.init();
		com.wonderrealms.sky.SkyIslands.init();
		com.wonderrealms.overcloud.Overcloud.init();
	}
}
