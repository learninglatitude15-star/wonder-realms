package com.wonderrealms.dragon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Elder Blade: a sword forged from the Elder Dragon's scales. Every blow lets loose one of the Elder's four
 * breaths at random: fire that burns, frost that slows and chills, lightning that strikes again, or shadow that
 * withers.
 */
public class ElderBladeItem extends Item {
	public ElderBladeItem(Properties properties) {
		super(properties);
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (!(attacker.level() instanceof ServerLevel level) || !target.isAlive()) {
			return;
		}
		double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.5, z = target.getZ();
		switch (attacker.getRandom().nextInt(4)) {
			case 0 -> { // fire
				target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 15, 0.3, 0.4, 0.3, 0.03);
			}
			case 1 -> { // frost
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), attacker);
				target.setTicksFrozen(Math.min(target.getTicksFrozen() + 60, target.getTicksRequiredToFreeze() + 60));
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 15, 0.3, 0.4, 0.3, 0.03);
			}
			case 2 -> { // lightning
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 20, 0.3, 0.5, 0.3, 0.2);
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
				target.setInvulnerableTime(0);
				target.hurtServer(level, level.damageSources().lightningBolt(), 3F);
			}
			default -> { // shadow
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), attacker);
				level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 10, 0.3, 0.4, 0.3, 0.02);
			}
		}
	}
}
