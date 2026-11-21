package com.wonderrealms.firefly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A glass jar of fireflies: a soft light that never needs fuel. Now and then one slips out of the lid. */
public class FireflyJarBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(4.5, 0, 4.5, 11.5, 10.5, 11.5);

	public FireflyJarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos)
			? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.FIREFLY, pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.25 + random.nextDouble() * 0.4,
				pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0, 0, 0);
		}
	}
}
