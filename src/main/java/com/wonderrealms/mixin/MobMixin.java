package com.wonderrealms.mixin;

import com.wonderrealms.dragon.DragonKinds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The undead can't pick a Bone dragon's rider as a target, nor endermen a Pearl dragon's (see {@link DragonKinds}). */
@Mixin(Mob.class)
public abstract class MobMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity wonderrealms$dragonRidersGoUnseen(LivingEntity target) {
		return target != null && DragonKinds.ignores((Mob) (Object) this, target) ? null : target;
	}
}
