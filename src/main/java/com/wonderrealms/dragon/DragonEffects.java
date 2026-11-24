package com.wonderrealms.dragon;

import com.wonderrealms.WonderRealms;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;

/** The status effects the 2.0 dragons give: a Moon dragon's sleep, and a Slime dragon's bounce. */
public final class DragonEffects {
	/** Fast asleep: can't move, jump, fly or hurt anything, and forgets what it was after. From a Moon dragon. */
	public static final Holder<MobEffect> ASLEEP = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, WonderRealms.id("asleep"), new Asleep());
	/** Bouncy as a slime: falls don't hurt, and jumps go higher. A Slime dragon's gift to its rider. */
	public static final Holder<MobEffect> BOUNCY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, WonderRealms.id("bouncy"),
		new MobEffect(MobEffectCategory.BENEFICIAL, 0x7EC850) {
		}
			.addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, WonderRealms.id("bouncy_falls"), -1.0, Operation.ADD_MULTIPLIED_TOTAL)
			.addAttributeModifier(Attributes.JUMP_STRENGTH, WonderRealms.id("bouncy_jump"), 0.35, Operation.ADD_MULTIPLIED_TOTAL));

	private DragonEffects() {
	}

	/** Loads the class, registering the effects. */
	static void init() {
	}

	private static final class Asleep extends MobEffect {
		private static final DustParticleOptions DREAM = new DustParticleOptions(0xD8E0FF, 0.9F);

		Asleep() {
			super(MobEffectCategory.HARMFUL, 0x9AA8E0);
			this.addAttributeModifier(Attributes.MOVEMENT_SPEED, WonderRealms.id("asleep_speed"), -1.0, Operation.ADD_MULTIPLIED_TOTAL);
			this.addAttributeModifier(Attributes.FLYING_SPEED, WonderRealms.id("asleep_flying"), -1.0, Operation.ADD_MULTIPLIED_TOTAL);
			this.addAttributeModifier(Attributes.JUMP_STRENGTH, WonderRealms.id("asleep_jump"), -1.0, Operation.ADD_MULTIPLIED_TOTAL);
			this.addAttributeModifier(Attributes.ATTACK_DAMAGE, WonderRealms.id("asleep_attack"), -1.0, Operation.ADD_MULTIPLIED_TOTAL);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return true;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity sleeper, int amplifier) {
			if (sleeper instanceof Mob mob) {
				// Dreaming, it forgets whoever it was after (so a skeleton won't shoot), and a creeper stops hissing.
				mob.setTarget(null);
				mob.getNavigation().stop();
				if (mob instanceof Creeper creeper) {
					creeper.setSwellDir(-1);
				}
			}
			if (sleeper.tickCount % 15 == 0) {
				level.sendParticles(DREAM, sleeper.getX(), sleeper.getY() + sleeper.getBbHeight() + 0.3, sleeper.getZ(), 2, 0.2, 0.15, 0.2, 0);
			}
			return true;
		}
	}
}
