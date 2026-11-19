package com.wonderrealms.dragon;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Breathe on the current target when it's in range and in sight, keeping it in view for the whole stream. */
public class DragonBreathGoal extends Goal {
	private static final double GROUND_RANGE = 10, AIR_RANGE = 12;

	private final DragonEntity dragon;

	public DragonBreathGoal(DragonEntity dragon) {
		this.dragon = dragon;
		this.setFlags(EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.dragon.getTarget();
		if (target == null || !target.isAlive() || this.dragon.isOrderedToSit() || this.dragon.getControllingPassenger() != null
				|| !this.dragon.canBreathe()) {
			return false;
		}
		double range = this.dragon.isFlying() ? AIR_RANGE : GROUND_RANGE;
		return this.dragon.distanceToSqr(target) < range * range && this.dragon.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.dragon.getTarget();
		return this.dragon.isBreathing() && target != null && target.isAlive();
	}

	@Override
	public void start() {
		LivingEntity target = this.dragon.getTarget();
		if (target == null) {
			return;
		}
		this.dragon.getLookControl().setLookAt(target, 30F, 30F);
		this.dragon.breathe(target.getEyePosition().subtract(this.dragon.position().add(0, this.dragon.getBbHeight() * 0.7, 0)), false);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.dragon.getTarget();
		if (target != null) {
			this.dragon.getLookControl().setLookAt(target, 30F, 30F);
		}
	}
}
