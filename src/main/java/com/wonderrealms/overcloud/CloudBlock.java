package com.wonderrealms.overcloud;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cloud: soft underfoot. Anything standing on top stays up, and landing on it never hurts, but sneak and you sink
 * slowly through. Inside a cloud you drift as if wading, and the Overcloud's whole sea of them is how you get home:
 * sink to the bottom and you fall back into the Overworld's sky.
 */
public class CloudBlock extends Block {
	/** The fastest anything sinks through a cloud, in blocks a tick, and how much clouds slow sideways drifting. */
	private static final double SINK_SPEED = 0.35, DRAG = 0.8;

	public CloudBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		// Drift down gently: a sea of clouds takes a couple of seconds to sink through, not a moment.
		Vec3 motion = entity.getDeltaMovement();
		entity.setDeltaMovement(motion.x * DRAG, Math.max(motion.y, -SINK_SPEED), motion.z * DRAG);
		entity.resetFallDistance();
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		entity.resetFallDistance(); // a soft landing, from any height
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false; // to mobs (which never sneak) clouds are ground: walk on top, never path through as if air
	}
}
