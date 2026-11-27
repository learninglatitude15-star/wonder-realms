package com.wonderrealms.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A sky village: an island about a hundred blocks across, floating high above the ground (or above the Overcloud's
 * continents), with a whole village on it. Where it floats is decided here; what's on it, by {@link SkyVillagePlan}.
 */
public final class SkyVillageStructure extends Structure {
	public static final MapCodec<SkyVillageStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		settingsCodec(instance),
		Codec.BOOL.optionalFieldOf("overcloud", false).forGetter(structure -> structure.overcloud)
	).apply(instance, SkyVillageStructure::new));

	/** How far below and above its ground a village's plan reaches: the underside's tip, and the tallest roof. */
	static final int DOWN = 60, UP = 28;
	/** Keep the underside this far clear of whatever land lies below. */
	private static final int CLEARANCE = 8;

	private final boolean overcloud;

	public SkyVillageStructure(StructureSettings settings, boolean overcloud) {
		super(settings);
		this.overcloud = overcloud;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		WorldgenRandom random = context.random();
		int radius = 44 + random.nextInt(7);
		int reach = radius + 18;
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();
		int maxY = context.heightAccessor().getMaxY();
		// In the Overworld it floats where sky islands do, in sight of the ground; above the Overcloud, higher still.
		int top = this.overcloud ? 250 + random.nextInt(18) : 186 + random.nextInt(28);
		int highest = Math.min(this.overcloud ? 290 : 260, maxY - UP - 2);
		// Rise clear of any mountain (or Overcloud continent) underneath: the underside is deepest in the middle.
		for (int i = -3; i <= 3; i++) {
			for (int j = -3; j <= 3; j++) {
				int sx = x + i * reach / 3;
				int sz = z + j * reach / 3;
				double out = Math.sqrt(i * i + j * j) / 3.0 * reach / radius; // 0 in the middle, 1 at the island's edge
				int depth = out >= 1 ? 0 : 3 + (int) (Math.pow(1 - out, 1.2) * radius * 0.75) + 6;
				int ground = context.chunkGenerator().getFirstOccupiedHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG,
					context.heightAccessor(), context.randomState());
				top = Math.max(top, ground + depth + CLEARANCE);
			}
		}
		if (top > highest) {
			return Optional.empty(); // the land's too high here
		}
		long seed = random.nextLong();
		BlockPos center = new BlockPos(x, top, z);
		return Optional.of(new GenerationStub(center,
			builder -> builder.addPiece(new SkyVillagePiece(center, radius, reach, seed, this.overcloud))));
	}

	@Override
	public StructureType<?> type() {
		return SkyVillages.TYPE;
	}
}
