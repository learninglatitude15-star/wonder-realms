package com.wonderrealms.miner;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every so often a miner picks a bit of rock right beside it and chips away at it for a few seconds: pickaxe
 * swinging, sparks and grit flying. They work the rock at pixel scale, so the block itself is never harmed.
 */
public class MineGoal extends Goal {
	private static final int CHIP_TICKS = 80;

	private final PixelMinerEntity miner;
	private BlockPos rock;
	private int ticks;

	public MineGoal(PixelMinerEntity miner) {
		this.miner = miner;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.miner.getRandom().nextInt(120) != 0) {
			return false;
		}
		BlockPos at = this.miner.blockPosition();
		for (Direction dir : Direction.Plane.HORIZONTAL.shuffledCopy(this.miner.getRandom())) {
			for (BlockPos candidate : new BlockPos[] {at.relative(dir), at.below()}) {
				BlockState state = this.miner.level().getBlockState(candidate);
				if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
					this.rock = candidate;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return this.rock != null && this.ticks < CHIP_TICKS;
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.miner.getNavigation().stop();
		this.miner.setMining(true);
	}

	@Override
	public void stop() {
		this.miner.setMining(false);
		this.rock = null;
	}

	@Override
	public void tick() {
		this.ticks++;
		this.miner.getLookControl().setLookAt(this.rock.getX() + 0.5, this.rock.getY() + 0.5, this.rock.getZ() + 0.5);
		if (this.ticks % 10 == 0 && this.miner.level() instanceof ServerLevel level) {
			BlockState state = level.getBlockState(this.rock);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
				this.miner.getX(), this.miner.getY() + 0.08, this.miner.getZ(), 2, 0.03, 0.02, 0.03, 0.0);
			if (this.ticks % 20 == 0) {
				level.sendParticles(ParticleTypes.CRIT, this.miner.getX(), this.miner.getY() + 0.1, this.miner.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
				level.playSound(null, this.miner.blockPosition(), SoundEvents.STONE_HIT, this.miner.getSoundSource(), 0.15F, 2.0F);
			}
		}
	}
}
