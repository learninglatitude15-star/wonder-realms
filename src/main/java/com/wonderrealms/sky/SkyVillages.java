package com.wonderrealms.sky;

import com.wonderrealms.WonderRealms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Sky villages: whole islands of houses, farms and dragon stables, far too big to be built by one chunk like a sky
 * island, so they're a structure of their own. The structure type and its piece are registered here;
 * data/wonderrealms/worldgen/{structure,structure_set} place them: {@code sky_village} in the Overworld's sky and
 * {@code overcloud_village} (closer together) above the Overcloud.
 */
public final class SkyVillages {
	public static final StructureType<SkyVillageStructure> TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
		WonderRealms.id("sky_village"), () -> SkyVillageStructure.CODEC);
	public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
		WonderRealms.id("sky_village"), SkyVillagePiece::new);

	public static final ResourceKey<Structure> SKY_VILLAGE = ResourceKey.create(Registries.STRUCTURE, WonderRealms.id("sky_village"));
	public static final ResourceKey<Structure> OVERCLOUD_VILLAGE = ResourceKey.create(Registries.STRUCTURE, WonderRealms.id("overcloud_village"));

	private SkyVillages() {
	}

	public static void init() {
		// The registrations above happen as this class loads.
	}

	/**
	 * Whether a sky village takes up any of the chunks around {@code origin}, so a sky island centred there would run
	 * into it. (The village is planned before any island; the chunks keep a note of the structures reaching them.)
	 */
	static boolean isNear(WorldGenLevel level, BlockPos origin) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Structure sky = structures.getValue(SKY_VILLAGE);
		Structure overcloud = structures.getValue(OVERCLOUD_VILLAGE);
		int chunkX = SectionPos.blockToSectionCoord(origin.getX());
		int chunkZ = SectionPos.blockToSectionCoord(origin.getZ());
		for (int x = chunkX - 1; x <= chunkX + 1; x++) {
			for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
				ChunkAccess chunk = level.getChunk(x, z);
				if (sky != null && !chunk.getReferencesForStructure(sky).isEmpty()
						|| overcloud != null && !chunk.getReferencesForStructure(overcloud).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}
}
