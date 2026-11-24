package com.wonderrealms.dragon;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * An Echo dragon's lair, down in the Deep Dark: a nest of sculk ringed with bone and deepslate on a cave floor, with
 * sculk veins creeping up the walls around it, and the dragon itself, guarding it. Placed on cave floors in the Deep
 * Dark (data/wonderrealms/worldgen/placed_feature/echo_lair).
 */
public record EchoLairFeature() implements Feature {
	public static final MapCodec<EchoLairFeature> CODEC = MapCodec.unit(EchoLairFeature::new);
	/** The nest's radius, and the open space a grown dragon needs above it. */
	private static final int RADIUS = 3, HEADROOM = 3;

	@Override
	public MapCodec<EchoLairFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos floor = origin.below();
		if (!level.getBlockState(floor).isSolid() || !roomy(level, origin)) {
			return false;
		}
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > RADIUS + 0.6) {
					continue;
				}
				BlockPos ground = surface(level, floor.offset(dx, 0, dz));
				if (ground == null) {
					continue;
				}
				if (d > RADIUS - 0.6) {
					// The rim: bone and broken deepslate.
					BlockState rim = random.nextInt(3) == 0
						? Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z)
						: (random.nextBoolean() ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
					if (level.getBlockState(ground.above()).isAir()) {
						level.setBlock(ground.above(), rim, 2);
					}
				} else {
					level.setBlock(ground, Blocks.SCULK.defaultBlockState(), 2);
				}
			}
		}
		// Sculk veins creep across the floor and walls round about.
		for (int i = 0; i < 40; i++) {
			BlockPos pos = origin.offset(random.nextInt(13) - 6, random.nextInt(5) - 1, random.nextInt(13) - 6);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			for (Direction face : Direction.values()) {
				BlockPos against = pos.relative(face);
				if (level.getBlockState(against).isFaceSturdy(level, against, face.getOpposite())) {
					level.setBlock(pos, Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true), 2);
					break;
				}
			}
		}
		DragonEntity dragon = Dragons.TYPES.get(DragonElement.ECHO).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (dragon != null) {
			dragon.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, random.nextFloat() * 360F, 0F);
			dragon.setIslandHome(origin, false); // it guards its lair as a roost's dragon guards its island
			level.addFreshEntity(dragon);
		}
		return true;
	}

	/** Room for a grown dragon to stand: clear air above the floor, a few blocks across. */
	private static boolean roomy(WorldGenLevel level, BlockPos origin) {
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, HEADROOM - 1, 1))) {
			if (!level.getBlockState(pos).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** The floor block near {@code pos} (one up or down), with air above it; or null. */
	private static BlockPos surface(WorldGenLevel level, BlockPos pos) {
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos at = pos.above(dy);
			if (level.getBlockState(at).isSolid() && level.getBlockState(at.above()).isAir()) {
				return at;
			}
		}
		return null;
	}
}
