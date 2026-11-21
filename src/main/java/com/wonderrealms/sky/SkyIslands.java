package com.wonderrealms.sky;

import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Sky islands: the feature type is registered here; data/wonderrealms/worldgen/{feature,placed_feature}/sky_island
 * configure it (rarity and spread), and every overworld biome gets it.
 */
public final class SkyIslands {
	public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, WonderRealms.id("sky_island"));

	private SkyIslands() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, WonderRealms.id("sky_island"), SkyIslandFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.SURFACE_STRUCTURES, PLACED);
		// Villagers high in the sky can only mean a sky village.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getY() > VILLAGE_Y && player.level().dimension() == Level.OVERWORLD
						&& !player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16)).isEmpty()) {
					WonderAdvancements.award(player, "sky_village");
				}
			}
		});
	}

	/** Sky islands float from y 180 up; anyone among villagers above this is in a sky village. */
	private static final int VILLAGE_Y = 160;
}
