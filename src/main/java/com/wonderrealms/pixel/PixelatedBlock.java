package com.wonderrealms.pixel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block that has been partly carved pixel by pixel. It looks and feels like whatever it used to be, minus the
 * pixels dug out; the block entity remembers which ones are left and a renderer draws them.
 */
public class PixelatedBlock extends Block implements EntityBlock {
	/** Chipping one pixel is this many times quicker than breaking the whole original block. */
	private static final float PIXEL_SPEEDUP = 8F;

	public PixelatedBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PixelatedBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE; // drawn by PixelatedBlockRenderer
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return level.getBlockEntity(pos) instanceof PixelatedBlockEntity pixels ? pixels.shape() : Shapes.block();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected int getLightDampening(BlockState state) {
		return 0;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof PixelatedBlockEntity pixels) {
			float whole = pixels.source().getDestroyProgress(player, level, pos);
			return com.wonderrealms.TinyWorlds.isTiny(player) ? Math.min(1F, whole * PIXEL_SPEEDUP) : whole;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}
}
