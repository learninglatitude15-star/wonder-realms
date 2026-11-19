package com.wonderrealms.dragon;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/** The four kinds of dragon, each with its own breath, favourite food and egg. */
public enum DragonElement {
	/** Fire breath that sets targets alight. Eats meat. */
	EMBER("ember", "Ember Dragon", "Ember Egg", ParticleTypes.FLAME, SoundEvents.BLAZE_SHOOT, 3),
	/** Frost breath that slows and freezes. Eats fish, or meat. */
	FROST("frost", "Frost Dragon", "Frost Egg", ParticleTypes.SNOWFLAKE, SoundEvents.PLAYER_HURT_FREEZE, 0),
	/** Crackling breath that sometimes calls down lightning. Eats meat. */
	STORM("storm", "Storm Dragon", "Storm Egg", ParticleTypes.ELECTRIC_SPARK, SoundEvents.TRIDENT_THUNDER.value(), 0),
	/** Hatched from the End's Dragon Egg with chorus fruit. Its breath hurts and sets targets floating. Eats chorus fruit or meat. */
	ENDER("ender", "Ender Dragon", "Dragon Egg", net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1F), SoundEvents.ENDER_DRAGON_SHOOT, 0);

	public final String id;
	public final String dragonName;
	public final String eggName;
	public final ParticleOptions breathParticle;
	public final SoundEvent breathSound;
	/** Light the egg gives off. */
	public final int eggLight;

	DragonElement(String id, String dragonName, String eggName, ParticleOptions breathParticle, SoundEvent breathSound, int eggLight) {
		this.id = id;
		this.dragonName = dragonName;
		this.eggName = eggName;
		this.breathParticle = breathParticle;
		this.breathSound = breathSound;
		this.eggLight = eggLight;
	}

	public boolean isFood(ItemStack stack) {
		return stack.is(ItemTags.MEAT) || (this == FROST && stack.is(ItemTags.FISHES))
			|| (this == ENDER && stack.is(net.minecraft.world.item.Items.CHORUS_FRUIT));
	}

	/** Ember, frost and storm dragons have their own eggs and live in the wild; ender dragons come from the End's egg. */
	public boolean hasOwnEgg() {
		return this != ENDER;
	}
}
