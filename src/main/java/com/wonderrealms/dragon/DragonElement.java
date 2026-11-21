package com.wonderrealms.dragon;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The twelve kinds of dragon, each with its own breath, favourite food, egg and gift to its rider. */
public enum DragonElement {
	/** Fire breath that sets targets alight. Its rider can't be burnt. Eats meat. */
	EMBER("ember", "Ember Dragon", "Ember Egg", ParticleTypes.FLAME, SoundEvents.BLAZE_SHOOT, 3),
	/** Frost breath that slows and freezes. Freezes water underfoot as it walks. Eats fish, or meat. */
	FROST("frost", "Frost Dragon", "Frost Egg", ParticleTypes.SNOWFLAKE, SoundEvents.PLAYER_HURT_FREEZE, 0),
	/** Crackling breath that sometimes calls down lightning. The fastest flier. Eats meat. */
	STORM("storm", "Storm Dragon", "Storm Egg", ParticleTypes.ELECTRIC_SPARK, SoundEvents.TRIDENT_THUNDER.value(), 0),
	/** Spore-laden breath that poisons foes and makes plants burst into growth. Heals its rider. Eats berries, apples or meat. */
	VERDANT("verdant", "Verdant Dragon", "Verdant Egg", ParticleTypes.SPORE_BLOSSOM_AIR, SoundEvents.BREEZE_SHOOT, 0),
	/** A jet of water that bowls foes over and puts out fires. Swims as well as it flies. Eats fish, dried kelp or meat. */
	TIDE("tide", "Tide Dragon", "Tide Egg", ParticleTypes.SPLASH, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0),
	/** A spray of crystal shards that makes foes glow. Its crystal scales are as good as armor. Eats amethyst shards or meat. */
	CRYSTAL("crystal", "Crystal Dragon", "Crystal Egg", new DustParticleOptions(0xC08CFF, 1.4F), SoundEvents.AMETHYST_BLOCK_CHIME, 7),
	/** Breath of shadow that withers and weakens. Its rider sees in the dark. Eats spider eyes or meat. */
	SHADOW("shadow", "Shadow Dragon", "Shadow Egg", ParticleTypes.SQUID_INK, SoundEvents.WITHER_SHOOT, 0),
	/** A blast of grit that staggers foes and, when ridden, tunnels through earth and stone. Its rider mines faster. Eats raw ore or meat. */
	EARTH("earth", "Earth Dragon", "Earth Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), SoundEvents.GRINDSTONE_USE, 0),
	/** Sticky honey breath that gums foes up and fills beehives. Its rider never goes hungry. Eats honeycomb or meat. */
	HONEY("honey", "Honey Dragon", "Honey Egg", new DustParticleOptions(0xFFB020, 1.5F), SoundEvents.HONEY_BLOCK_SLIDE, 4),
	/** A rainbow that dazzles foes, turns sheep every colour, and, when ridden, repaints wool, glass, concrete and terracotta. Eats flowers or meat. */
	RAINBOW("rainbow", "Rainbow Dragon", "Rainbow Egg", new DustParticleOptions(0xFF4040, 1.5F), SoundEvents.NOTE_BLOCK_CHIME.value(), 9),
	/** Gusts of wind that blow foes away and fires out. Its rider turns invisible, and once tamed it's invisible too. Eats feathers or meat. */
	AIR("air", "Air Dragon", "Air Egg", ParticleTypes.CLOUD, SoundEvents.WIND_CHARGE_BURST.value(), 0),
	/** Hatched from the End's Dragon Egg with chorus fruit. Its breath hurts and sets targets floating. Eats chorus fruit or meat. */
	ENDER("ender", "Ender Dragon", "Dragon Egg", PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1F), SoundEvents.ENDER_DRAGON_SHOOT, 0);

	/** Any flower, which is what a Rainbow dragon likes best. */
	private static final TagKey<Item> FLOWERS = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("flowers"));

	/** The kinds that live wild on the sky islands. */
	public static final DragonElement[] WILD = {EMBER, FROST, STORM, VERDANT, TIDE, CRYSTAL, SHADOW, EARTH, HONEY, RAINBOW, AIR};

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
		return stack.is(ItemTags.MEAT)
			|| ((this == FROST || this == TIDE) && stack.is(ItemTags.FISHES))
			|| (this == TIDE && stack.is(Items.DRIED_KELP))
			|| (this == VERDANT && (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.APPLE)))
			|| (this == CRYSTAL && stack.is(Items.AMETHYST_SHARD))
			|| (this == SHADOW && stack.is(Items.SPIDER_EYE))
			|| (this == EARTH && (stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER) || stack.is(Items.RAW_GOLD)))
			|| (this == HONEY && stack.is(Items.HONEYCOMB))
			|| (this == RAINBOW && stack.is(FLOWERS))
			|| (this == AIR && stack.is(Items.FEATHER))
			|| (this == ENDER && stack.is(Items.CHORUS_FRUIT));
	}

	/** The wild kinds have their own eggs; ender dragons come from the End's egg. */
	public boolean hasOwnEgg() {
		return this != ENDER;
	}
}
