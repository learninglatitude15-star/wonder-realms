package com.wonderrealms.dragon;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
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

/**
 * The kinds of dragon, each with its own breath, favourite food, egg and gift to its rider: the twelve of the sky
 * islands (and the End), and since 2.0 eight more that live on the ground in the Overworld's wilds and eight in the
 * Overcloud (their breaths, gifts and powers are in {@link DragonKinds}).
 */
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
	ENDER("ender", "Ender Dragon", "Dragon Egg", PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1F), SoundEvents.ENDER_DRAGON_SHOOT, 0),

	// The dragons of the Overworld's wilds, living on the ground in their own biomes.
	/** Gooey breath that bounces foes into the air. Its rider bounces too, and never takes fall damage. Eats slimeballs. Swamps. */
	SLIME("slime", "Slime Dragon", "Slime Egg", ParticleTypes.ITEM_SLIME, SoundEvents.SLIME_JUMP, 0),
	/** Dizzying spores that make mushrooms sprout. Its rider shrugs off poison and hunger. Eats mushrooms. Mushroom fields, dark forests. */
	MUSHROOM("mushroom", "Mushroom Dragon", "Mushroom Egg", new DustParticleOptions(0xC8A888, 1.4F), SoundEvents.FUNGUS_PLACE, 0),
	/** A storm of petals that calms foes and makes animals fall in love. Baby animals near it grow up fast. Eats petals or apples. Cherry groves. */
	BLOSSOM("blossom", "Blossom Dragon", "Blossom Egg", ParticleTypes.CHERRY_LEAVES, SoundEvents.CHERRY_LEAVES_BREAK, 0),
	/** A heavy blast of iron filings. Iron-plated and hard to knock about; its rider is tougher too. Eats iron. Windswept hills, peaks. */
	IRON("iron", "Iron Dragon", "Iron Egg", ParticleTypes.CRIT, SoundEvents.IRON_GOLEM_ATTACK, 0),
	/** Bone shards that weaken foes. The undead leave its rider alone. Eats bones. Deserts, badlands and the pale garden. */
	BONE("bone", "Bone Dragon", "Bone Egg", new ItemParticleOption(ParticleTypes.ITEM, Items.BONE), SoundEvents.SKELETON_AMBIENT, 0),
	/** A lash of vines that drags foes in and snares them. Its rider strikes harder. Eats melon or cocoa beans. Jungles. */
	JUNGLE("jungle", "Jungle Dragon", "Jungle Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.JUNGLE_LEAVES.defaultBlockState()), SoundEvents.VINE_STEP, 0),
	/** A sonic pulse that no armor stops, leaving foes in darkness. Monsters near its rider glow. Eats sculk or echo shards. The Deep Dark. */
	ECHO("echo", "Echo Dragon", "Echo Egg", ParticleTypes.SCULK_SOUL, SoundEvents.WARDEN_SONIC_CHARGE, 5),
	/** Bubbles that float foes helplessly away. Swims as fast as it flies; its rider swims like a dolphin. Eats fish. Beaches. */
	CORAL("coral", "Coral Dragon", "Coral Egg", ParticleTypes.BUBBLE_POP, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0),

	// The dragons of the Overcloud.
	/** A sunbeam that blinds foes, burns the undead and, when ridden, bakes sand into glass. Golden hearts for its rider. Eats sunflowers. */
	SUN("sun", "Sun Dragon", "Sun Egg", new DustParticleOptions(0xFFD040, 1.6F), SoundEvents.BEACON_ACTIVATE, 12),
	/** Moon dust that sends foes to sleep. Its rider falls as gently as on the moon. Eats cookies. */
	MOON("moon", "Moon Dragon", "Moon Egg", new DustParticleOptions(0xC8D4FF, 1.4F), SoundEvents.AMETHYST_BLOCK_CHIME, 6),
	/** Stardust that lights foes up. Lucky star: more experience for its rider's kills. Eats glow berries or glowstone dust. */
	STAR("star", "Star Dragon", "Star Egg", ParticleTypes.FIREWORK, SoundEvents.FIREWORK_ROCKET_TWINKLE, 8),
	/** Shimmering light that heals friends and lights foes up. Its rider gets extra hearts. Eats fish. */
	AURORA("aurora", "Aurora Dragon", "Aurora Egg", new DustParticleOptions(0x50F0B0, 1.5F), SoundEvents.AMETHYST_BLOCK_RESONATE, 7),
	/** Fire that heals the dragon as it burns. It can't burn, and it saves its rider from death. Eats blaze powder. */
	PHOENIX("phoenix", "Phoenix Dragon", "Phoenix Egg", ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE, 10),
	/** A cloudburst that blinds foes and, when ridden, builds clouds to walk on. Arrows can't touch its rider. Eats snowballs. */
	NIMBUS("nimbus", "Nimbus Dragon", "Nimbus Egg", ParticleTypes.FALLING_WATER, SoundEvents.WEATHER_RAIN, 0),
	/** Dazzling gold dust that throws foes back. Its rider can sense treasure. Eats gold. */
	GOLD("gold", "Gold Dragon", "Gold Egg", ParticleTypes.WAX_ON, SoundEvents.NOTE_BLOCK_BELL.value(), 3),
	/** A shimmer that blinks foes away; it blinks out of danger too, and can blink its riders across the sky. Eats ender pearls. */
	PEARL("pearl", "Pearl Dragon", "Pearl Egg", ParticleTypes.PORTAL, SoundEvents.CHORUS_FRUIT_TELEPORT, 4);

	/** Any flower, which is what a Rainbow dragon likes best. */
	private static final TagKey<Item> FLOWERS = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("flowers"));

	/** The kinds that live wild on the sky islands. */
	public static final DragonElement[] WILD = {EMBER, FROST, STORM, VERDANT, TIDE, CRYSTAL, SHADOW, EARTH, HONEY, RAINBOW, AIR};
	/** The kinds that live on the ground in the Overworld's biomes (and the Echo dragon in the Deep Dark). */
	public static final DragonElement[] OVERWORLD_WILDS = {SLIME, MUSHROOM, BLOSSOM, IRON, BONE, JUNGLE, ECHO, CORAL};
	/** The kinds that live in the Overcloud. */
	public static final DragonElement[] OVERCLOUD = {SUN, MOON, STAR, AURORA, PHOENIX, NIMBUS, GOLD, PEARL};

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
			|| (this == ENDER && stack.is(Items.CHORUS_FRUIT))
			|| (this == SLIME && stack.is(Items.SLIME_BALL))
			|| (this == MUSHROOM && (stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM)))
			|| (this == BLOSSOM && (stack.is(Items.PINK_PETALS) || stack.is(Items.APPLE)))
			|| (this == IRON && (stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_NUGGET)))
			|| (this == BONE && stack.is(Items.BONE))
			|| (this == JUNGLE && (stack.is(Items.MELON_SLICE) || stack.is(Items.COCOA_BEANS)))
			|| (this == ECHO && (stack.is(Items.SCULK) || stack.is(Items.ECHO_SHARD)))
			|| ((this == CORAL || this == AURORA) && stack.is(ItemTags.FISHES))
			|| (this == SUN && stack.is(Items.SUNFLOWER))
			|| (this == MOON && stack.is(Items.COOKIE))
			|| (this == STAR && (stack.is(Items.GLOW_BERRIES) || stack.is(Items.GLOWSTONE_DUST)))
			|| (this == PHOENIX && stack.is(Items.BLAZE_POWDER))
			|| (this == NIMBUS && stack.is(Items.SNOWBALL))
			|| (this == GOLD && (stack.is(Items.GOLD_NUGGET) || stack.is(Items.GOLD_INGOT)))
			|| (this == PEARL && stack.is(Items.ENDER_PEARL));
	}

	/** Swims as fast as it flies, and breathes underwater: the Tide and Coral dragons. */
	public boolean swims() {
		return this == TIDE || this == CORAL;
	}

	/** Since 2.0: the dragons of the Overworld's wilds and of the Overcloud, whose ways are in {@link DragonKinds}. */
	public boolean isNewKind() {
		return this.ordinal() > ENDER.ordinal();
	}

	/** The wild kinds have their own eggs; ender dragons come from the End's egg. */
	public boolean hasOwnEgg() {
		return this != ENDER;
	}
}
