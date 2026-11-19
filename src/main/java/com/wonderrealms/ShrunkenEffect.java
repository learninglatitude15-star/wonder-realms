package com.wonderrealms;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Shrunken: a tenth of your size. Vanilla's scale attribute shrinks the body, hitbox and camera, but not how far you
 * step, jump, walk or reach, so those shrink here too and the world turns into a giant's landscape: a block is a
 * cliff, grass is a jungle. In return you weigh almost nothing, so falls barely hurt, and you can climb walls
 * (see {@code LivingEntityMixin}).
 */
public class ShrunkenEffect extends MobEffect {
	public static final float SCALE = 0.1F;

	public ShrunkenEffect() {
		super(MobEffectCategory.NEUTRAL, 0x8E6CC8);
		this.addAttributeModifier(Attributes.SCALE, WonderRealms.id("shrunken_scale"), SCALE - 1.0, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.MOVEMENT_SPEED, WonderRealms.id("shrunken_speed"), -0.55, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.JUMP_STRENGTH, WonderRealms.id("shrunken_jump"), -0.55, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.STEP_HEIGHT, WonderRealms.id("shrunken_step"), -0.75, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.GRAVITY, WonderRealms.id("shrunken_gravity"), -0.4, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, WonderRealms.id("shrunken_safe_fall"), 8.0, Operation.ADD_VALUE);
		this.addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, WonderRealms.id("shrunken_fall_damage"), -0.9, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.BLOCK_INTERACTION_RANGE, WonderRealms.id("shrunken_reach"), -0.65, Operation.ADD_MULTIPLIED_TOTAL);
		this.addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, WonderRealms.id("shrunken_entity_reach"), -0.65, Operation.ADD_MULTIPLIED_TOTAL);
	}
}
