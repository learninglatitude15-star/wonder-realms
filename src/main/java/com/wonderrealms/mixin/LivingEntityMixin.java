package com.wonderrealms.mixin;

import com.wonderrealms.TinyWorlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tiny creatures climb walls like bugs, and bugs hit them harder (see {@link TinyWorlds}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void wonderrealms$tinyClimbing(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!cir.getReturnValueZ() && self.horizontalCollision && !self.isSpectator() && TinyWorlds.isTiny(self)) {
			cir.setReturnValue(true);
		}
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float wonderrealms$bugsHitHarder(float amount, ServerLevel level, DamageSource source, float originalAmount) {
		return amount * TinyWorlds.damageMultiplier((LivingEntity) (Object) this, source);
	}
}
