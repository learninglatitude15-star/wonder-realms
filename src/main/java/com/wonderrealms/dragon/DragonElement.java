package com.wonderrealms.dragon;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
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
	/**
	 * Since 2.1. Its scales take on the colour of whatever it stands on, and when it keeps still it can't be told from
	 * the ground. A dazzling spray of colours that leaves foes blinded and baffled. Monsters only notice its rider up
	 * close. Its power disguises it as a different kind of dragon. Eats spider eyes or sweet berries. Savannas, birch forests.
	 */
	CHAMELEON("chameleon", "Chameleon Dragon", "Chameleon Egg", new DustColorTransitionOptions(0x7CD64A, 0xFF6FB4, 1.4F), SoundEvents.FROG_TONGUE, 0),

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
	PEARL("pearl", "Pearl Dragon", "Pearl Egg", ParticleTypes.PORTAL, SoundEvents.CHORUS_FRUIT_TELEPORT, 4),

	// Since 2.1: thirty more (their ways are in MoreDragons). More of the Overworld's wilds...
	/** Freezing breath. Its rider never feels the cold. Deep Freeze. Eats fish. Ice spikes, frozen peaks. */
	ICE("ice", "Ice Dragon", "Ice Egg", ParticleTypes.SNOWFLAKE, SoundEvents.POWDER_SNOW_STEP, 0),
	/** A flurry of snowballs that knocks foes back. Its rider leaps like a snow hare. Snow Army. Eats pumpkin pie. Snowy plains and taiga. */
	SNOW("snow", "Snow Dragon", "Snow Egg", ParticleTypes.ITEM_SNOWBALL, SoundEvents.SNOW_GOLEM_SHOOT, 0),
	/** Ink that blinds. Swims; its rider breathes underwater and sees in the deep. Whirlpool. Eats ink sacs. Lairs on the deep ocean floor. */
	ABYSS("abyss", "Abyss Dragon", "Abyss Egg", ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT, 3),
	/** Glowing spores that light foes up. Its rider sees in the dark. Glow Burst. Eats glow berries. Lairs in lush caves. */
	GLOW("glow", "Glow Dragon", "Glow Egg", ParticleTypes.GLOW, SoundEvents.GLOW_SQUID_SQUIRT, 10),
	/** A spray of stone spikes. Anything that hits its rider is spiked back. Stalactite Rain. Eats pointed dripstone. Lairs in dripstone caves. */
	DRIPSTONE("dripstone", "Dripstone Dragon", "Dripstone Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DRIPSTONE_BLOCK.defaultBlockState()),
		SoundEvents.POINTED_DRIPSTONE_LAND, 0),
	/** Sticky mud that bogs foes down. Its rider's blows slow foes. Mud Slide. Eats mud. Mangrove swamps. */
	MUD("mud", "Mud Dragon", "Mud Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), SoundEvents.MUD_STEP, 0),
	/** Venom that poisons. Its rider's blows poison too. Venom Cloud. Eats poisonous potatoes. Swamps, sparse jungle. */
	VENOM("venom", "Venom Dragon", "Venom Egg", new DustParticleOptions(0x8CE03C, 1.3F), SoundEvents.SPIDER_AMBIENT, 0),
	/** A gust of chaff that leaves foes hungry. Villagers give its rider bargains. Harvest Moon. Eats wheat or bread. Plains. */
	HARVEST("harvest", "Harvest Dragon", "Harvest Egg", new ItemParticleOption(ParticleTypes.ITEM, Items.WHEAT), SoundEvents.CROP_PLANTED, 0),
	/** A blast of sand that blinds. Its rider's blows blind foes. Sandstorm. Eats cactus. Deserts. */
	SAND("sand", "Sand Dragon", "Sand Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), SoundEvents.SAND_FALL, 0),
	/** A fearsome roar. Creepers and phantoms keep away from its rider. Pounce. Eats fish. Jungles. */
	TIGER("tiger", "Tiger Dragon", "Tiger Egg", ParticleTypes.CRIT, SoundEvents.RAVAGER_ROAR, 0),
	/** A snarling bite that weakens. Its rider's wolves fight harder. Howl: wolves join the fight. Eats bones. Taiga. */
	FANG("fang", "Fang Dragon", "Fang Egg", ParticleTypes.POOF, SoundEvents.POLAR_BEAR_WARNING, 0),
	// ...the first dragons of the Nether, all fireproof...
	/** Molten spray that burns and sticks. Its rider can't burn. Eruption. Eats magma cream. Nether wastes, basalt deltas. */
	MAGMA("magma", "Magma Dragon", "Magma Egg", ParticleTypes.LAVA, SoundEvents.MAGMA_CUBE_JUMP, 6),
	/** Crimson spores that weaken and starve. Piglins and hoglins leave its rider alone. Crimson Snare. Eats crimson fungus. */
	CRIMSON("crimson", "Crimson Dragon", "Crimson Egg", ParticleTypes.CRIMSON_SPORE, SoundEvents.WEEPING_VINES_PLACE, 3),
	/** Warping spores that send foes reeling a few blocks off. Its rider is quick. Warp Field. Eats warped fungus. */
	WARPED("warped", "Warped Dragon", "Warped Egg", ParticleTypes.WARPED_SPORE, SoundEvents.ENDERMAN_TELEPORT, 3),
	/** Soul fire. Its rider heals a little with every blow. Soul Harvest. Eats soul sand. Soul sand valleys. */
	SOUL("soul", "Soul Dragon", "Soul Egg", ParticleTypes.SOUL_FIRE_FLAME, SoundEvents.SOUL_ESCAPE.value(), 6),
	/** Ash and cinders that blind. Its rider is hard as stone. Basalt Burst. Eats coal. Basalt deltas. */
	BASALT("basalt", "Basalt Dragon", "Basalt Egg", ParticleTypes.LARGE_SMOKE, SoundEvents.BASALT_BREAK, 0),
	/** Fire. Its rider's blows set foes alight. Fireball Volley. Eats blaze rods. Nether wastes. */
	BLAZE("blaze", "Blaze Dragon", "Blaze Egg", ParticleTypes.SMALL_FLAME, SoundEvents.BLAZE_SHOOT, 8),
	/** A mournful breath that weakens. Ghasts and blazes leave its rider alone. Wail. Eats ghast tears. Soul sand valleys. */
	GHAST("ghast", "Ghast Dragon", "Ghast Egg", new DustParticleOptions(0xF4F4F4, 1.6F), SoundEvents.GHAST_WARN, 0),
	// ...of the End...
	/** Void breath that drags foes in. Its rider falls gently. Black Hole. Eats eyes of ender. The End's outer islands. */
	VOID("void", "Void Dragon", "Void Egg", ParticleTypes.REVERSE_PORTAL, SoundEvents.ENDER_EYE_DEATH, 0),
	/** Chorus spores that hop foes about. Its rider springs high. Chorus Launch. Eats chorus fruit. End highlands. */
	CHORUS("chorus", "Chorus Dragon", "Chorus Egg", new DustParticleOptions(0xB48CD8, 1.4F), SoundEvents.CHORUS_FLOWER_GROW, 2),
	/** Shulker sparks that float foes up. Its rider has a shell's toughness. Shell Up. Eats popped chorus fruit. End highlands. */
	SHULKER("shulker", "Shulker Dragon", "Shulker Egg", ParticleTypes.END_ROD, SoundEvents.SHULKER_SHOOT, 4),
	/** Stardust that dazzles. Its rider is lucky. Supernova. Eats amethyst shards. End midlands. */
	NEBULA("nebula", "Nebula Dragon", "Nebula Egg", new DustColorTransitionOptions(0x6A4CFF, 0xFF6AD8, 1.5F), SoundEvents.ENCHANTMENT_TABLE_USE, 6),
	// ...more of the Overcloud...
	/** Hailstones. Anything that hits its rider is chilled to a crawl. Hailstorm. Eats snowballs or ice. Storm Heights. */
	HAIL("hail", "Hail Dragon", "Hail Egg", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), SoundEvents.GLASS_BREAK, 0),
	/** Darkness itself. Its rider grows strong at night. Eclipse. Eats golden carrots. Dragon Peaks. */
	ECLIPSE("eclipse", "Eclipse Dragon", "Eclipse Egg", new DustParticleOptions(0x2A1840, 1.6F), SoundEvents.BEACON_DEACTIVATE, 0),
	/** Prismatic light that dazzles. Anything that hits its rider is dazzled. Prism Beam. Eats prismarine crystals. Crystal Reaches. */
	PRISM("prism", "Prism Dragon", "Prism Egg", new DustColorTransitionOptions(0x7CF0FF, 0xFF7CE8, 1.3F), SoundEvents.AMETHYST_CLUSTER_BREAK, 7),
	/** A gale that tosses foes. Its rider runs like the wind. Tailwind. Eats feathers. Sunlit Meadows. */
	ZEPHYR("zephyr", "Zephyr Dragon", "Zephyr Egg", ParticleTypes.SMALL_GUST, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0),
	/** A ghostly chill. Phantoms leave its rider alone, who sees in the dark. Night Terror. Eats phantom membrane. Storm Heights. */
	PHANTOM("phantom", "Phantom Dragon", "Phantom Egg", new DustParticleOptions(0x6AA0E8, 1.4F), SoundEvents.PHANTOM_SWOOP, 0),
	// ...and three found only on the sky islands.
	/** A crackle of redstone that numbs. Its rider works fast. Overload. Eats redstone. */
	REDSTONE("redstone", "Redstone Dragon", "Redstone Egg", new DustParticleOptions(0xFF2020, 1.5F), SoundEvents.REDSTONE_TORCH_BURNOUT, 5),
	/** A whirr of cogs that slows foes. Time runs fast for its rider. Time Stop. Eats copper. */
	CLOCKWORK("clockwork", "Clockwork Dragon", "Clockwork Egg", new DustParticleOptions(0xD8A848, 1.2F), SoundEvents.COMPARATOR_CLICK, 0),
	/** A song that sets foes dancing. Its music heals its rider. Dance Party. Eats cookies or melon. */
	MELODY("melody", "Melody Dragon", "Melody Egg", ParticleTypes.NOTE, SoundEvents.NOTE_BLOCK_HARP.value(), 0);

	/** Any flower, which is what a Rainbow dragon likes best. */
	private static final TagKey<Item> FLOWERS = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("flowers"));

	/** The kinds that live wild on the sky islands. */
	public static final DragonElement[] WILD = {EMBER, FROST, STORM, VERDANT, TIDE, CRYSTAL, SHADOW, EARTH, HONEY, RAINBOW, AIR};
	/** The kinds that live on the ground in the Overworld's biomes (and the Echo dragon in the Deep Dark). */
	public static final DragonElement[] OVERWORLD_WILDS = {SLIME, MUSHROOM, BLOSSOM, IRON, BONE, JUNGLE, ECHO, CORAL, CHAMELEON,
		ICE, SNOW, ABYSS, GLOW, DRIPSTONE, MUD, VENOM, HARVEST, SAND, TIGER, FANG};
	/** The kinds that live in the Overcloud. */
	public static final DragonElement[] OVERCLOUD = {SUN, MOON, STAR, AURORA, PHOENIX, NIMBUS, GOLD, PEARL, HAIL, ECLIPSE, PRISM, ZEPHYR, PHANTOM};
	/** Since 2.1, the dragons of the Nether. */
	public static final DragonElement[] NETHER = {MAGMA, CRIMSON, WARPED, SOUL, BASALT, BLAZE, GHAST};
	/** Since 2.1, the dragons of the End's outer islands. */
	public static final DragonElement[] END = {VOID, CHORUS, SHULKER, NEBULA};
	/** Since 2.1, kinds found only guarding sky islands. */
	public static final DragonElement[] SKY = {REDSTONE, CLOCKWORK, MELODY};

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
			|| (this == CHAMELEON && (stack.is(Items.SPIDER_EYE) || stack.is(Items.SWEET_BERRIES)))
			|| (this == ICE || this == TIGER) && stack.is(ItemTags.FISHES)
			|| this == SNOW && stack.is(Items.PUMPKIN_PIE)
			|| this == ABYSS && (stack.is(Items.INK_SAC) || stack.is(Items.GLOW_INK_SAC))
			|| this == GLOW && (stack.is(Items.GLOW_BERRIES) || stack.is(Items.GLOW_LICHEN))
			|| this == DRIPSTONE && (stack.is(Items.POINTED_DRIPSTONE) || stack.is(Items.CLAY_BALL))
			|| this == MUD && (stack.is(Items.MUD) || stack.is(Items.MANGROVE_PROPAGULE))
			|| this == VENOM && (stack.is(Items.POISONOUS_POTATO) || stack.is(Items.FERMENTED_SPIDER_EYE))
			|| this == HARVEST && (stack.is(Items.WHEAT) || stack.is(Items.BREAD))
			|| this == SAND && stack.is(Items.CACTUS)
			|| this == FANG && stack.is(Items.BONE)
			|| this == MAGMA && stack.is(Items.MAGMA_CREAM)
			|| this == CRIMSON && stack.is(Items.CRIMSON_FUNGUS)
			|| this == WARPED && stack.is(Items.WARPED_FUNGUS)
			|| this == SOUL && (stack.is(Items.SOUL_SAND) || stack.is(Items.SOUL_SOIL))
			|| this == BASALT && (stack.is(Items.COAL) || stack.is(Items.CHARCOAL))
			|| this == BLAZE && stack.is(Items.BLAZE_ROD)
			|| this == GHAST && stack.is(Items.GHAST_TEAR)
			|| this == VOID && stack.is(Items.ENDER_EYE)
			|| this == CHORUS && stack.is(Items.CHORUS_FRUIT)
			|| this == SHULKER && stack.is(Items.POPPED_CHORUS_FRUIT)
			|| this == NEBULA && stack.is(Items.AMETHYST_SHARD)
			|| this == HAIL && (stack.is(Items.SNOWBALL) || stack.is(Items.ICE) || stack.is(Items.PACKED_ICE))
			|| this == ECLIPSE && stack.is(Items.GOLDEN_CARROT)
			|| this == PRISM && stack.is(Items.PRISMARINE_CRYSTALS)
			|| this == ZEPHYR && stack.is(Items.FEATHER)
			|| this == PHANTOM && stack.is(Items.PHANTOM_MEMBRANE)
			|| this == REDSTONE && stack.is(Items.REDSTONE)
			|| this == CLOCKWORK && (stack.is(Items.COPPER_INGOT) || stack.is(Items.CLOCK))
			|| this == MELODY && (stack.is(Items.COOKIE) || stack.is(Items.MELON_SLICE))
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
		return this == TIDE || this == CORAL || this == ABYSS;
	}

	/** Can't burn: the Phoenix, and every dragon of the Nether. */
	public boolean fireproof() {
		return this == PHOENIX || this == MAGMA || this == CRIMSON || this == WARPED || this == SOUL || this == BASALT || this == BLAZE
			|| this == GHAST;
	}

	/** Since 2.1: the thirty in {@link MoreDragons}. */
	public boolean isMore() {
		return this.ordinal() > PEARL.ordinal();
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
