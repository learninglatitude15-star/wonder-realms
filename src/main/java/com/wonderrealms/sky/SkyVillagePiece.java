package com.wonderrealms.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The one piece of a sky village: the whole island. It remembers just enough to plan the village again exactly (where,
 * how big, and its seed), and each chunk it reaches builds its own part of that plan.
 */
public final class SkyVillagePiece extends StructurePiece {
	private final BlockPos center;
	private final int radius;
	private final int reach;
	private final long seed;
	private final boolean overcloud;

	SkyVillagePiece(BlockPos center, int radius, int reach, long seed, boolean overcloud) {
		super(SkyVillages.PIECE, 0, box(center, reach));
		this.center = center;
		this.radius = radius;
		this.reach = reach;
		this.seed = seed;
		this.overcloud = overcloud;
	}

	public SkyVillagePiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(SkyVillages.PIECE, tag);
		this.center = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 200), tag.getIntOr("z", 0));
		this.radius = tag.getIntOr("radius", 46);
		this.reach = tag.getIntOr("reach", this.radius + 18);
		this.seed = tag.getLongOr("seed", 0L);
		this.overcloud = tag.getBooleanOr("overcloud", false);
	}

	/** Everything a village's plan can touch: its island, the drifting islets round it, and its tallest roof. */
	static BoundingBox box(BlockPos center, int reach) {
		return new BoundingBox(center.getX() - reach, center.getY() - SkyVillageStructure.DOWN, center.getZ() - reach,
			center.getX() + reach, center.getY() + SkyVillageStructure.UP, center.getZ() + reach);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("x", this.center.getX());
		tag.putInt("y", this.center.getY());
		tag.putInt("z", this.center.getZ());
		tag.putInt("radius", this.radius);
		tag.putInt("reach", this.reach);
		tag.putLong("seed", this.seed);
		tag.putBoolean("overcloud", this.overcloud);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		SkyVillagePlan.of(this.center, this.radius, this.reach, this.seed, this.overcloud).apply(level, chunkBox);
	}
}
