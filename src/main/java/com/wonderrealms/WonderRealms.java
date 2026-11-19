package com.wonderrealms;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * Wonder Realms: worlds beyond the everyday. Tiny Worlds (shrink to insect size, pixel mining, Pixel Miners),
 * dragons (hatch, raise, ride), sky islands and airships.
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
		com.wonderrealms.pixel.PixelMining.init();
		com.wonderrealms.airship.Airships.init();
		com.wonderrealms.sky.SkyIslands.init();
	}
}
