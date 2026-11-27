package com.wonderrealms.sky;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where an island is built. A sky island goes straight into the world, all at once, by the chunk it's centred on. A
 * sky village is far too big for that: it's planned whole on a buffer first, then built a chunk at a time as the
 * world generates (see {@link SkyVillagePlan}).
 */
interface IslandCanvas {
	void set(BlockPos pos, BlockState state);

	BlockState get(BlockPos pos);

	/**
	 * Something to do once the block at {@code pos} is really in the world: fill a chest, start a spring flowing,
	 * put bees in a nest, or bring someone to live there.
	 */
	void then(BlockPos pos, Consumer<WorldGenLevel> action);

	/** Straight into the world being generated. */
	record World(WorldGenLevel level) implements IslandCanvas {
		@Override
		public void set(BlockPos pos, BlockState state) {
			this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}

		@Override
		public BlockState get(BlockPos pos) {
			return this.level.getBlockState(pos);
		}

		@Override
		public void then(BlockPos pos, Consumer<WorldGenLevel> action) {
			action.accept(this.level);
		}
	}
}
