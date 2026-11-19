package com.wonderrealms.sky;

import com.wonderrealms.WonderRealms;
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
	}
}
