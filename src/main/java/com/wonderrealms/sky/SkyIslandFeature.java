package com.wonderrealms.sky;

import com.mojang.serialization.MapCodec;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
import com.wonderrealms.dragon.ElderDragonEntity;
import com.wonderrealms.dragon.Dragons;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A floating island high in the overworld sky. Each is a few overlapping, ragged lobes with a rocky underside that
 * hangs down to a point, studded with ore, and a few small rocks drifting nearby. Islands come in eight themes:
 * <ul>
 *   <li><b>Meadow</b>: rolling grass, oak and birch, ponds, and waterfalls you can see from the ground.</li>
 *   <li><b>Ember</b>: blackstone and basalt, magma, smoking vents, lava pools and charred trees.</li>
 *   <li><b>Frost</b>: snowfields, ice spikes, frozen ponds and snow-laden spruce, with icicles beneath.</li>
 *   <li><b>Storm</b>: windswept rock and grass, stone spires crowned with lightning rods, chains and copper beneath.</li>
 *   <li><b>Verdant</b>: moss, flowering azalea and petals, spore blossoms and glow berries beneath.</li>
 *   <li><b>Lagoon</b>: sandy shores round a lagoon of seagrass and sea pickles, palms, sea lantern pillars, and
 *       waterfalls; prismarine and glow lichen beneath.</li>
 *   <li><b>Crystal</b>: calcite and great amethyst crystals (some still growing), cherry trees in blossom, and
 *       crystals hanging beneath.</li>
 *   <li><b>Gloom</b>: pale moss and pale oaks trailing moss, eyeblossoms, sculk, soul-lit obelisks and soul fire
 *       vents, over deepslate.</li>
 * </ul>
 * Wild islands hide a chest in a ruined shrine. About one island in five is a dragon roost (a nest round a hoard,
 * guarded by a wild dragon whose element matches its island), about one in five a pillager sky outpost (a
 * watchtower and garrison, a dock with an airship, and pillagers riding dragons), and one in twenty a Sky Citadel
 * (a ruined castle where the Elder Dragon guards its hoard). Sky islands are the only place
 * dragons are found. They float between y 180 and 230, roughly a hundred blocks apart.
 * <p>
 * Everything is built within {@link #REACH} blocks of the middle of the chunk being decorated: the area a feature
 * may build in during world generation.
 */
public record SkyIslandFeature() implements Feature {
	public static final MapCodec<SkyIslandFeature> CODEC = MapCodec.unit(SkyIslandFeature::new);

	private static final ResourceKey<LootTable> ISLAND_LOOT = loot("chests/sky_island");
	private static final ResourceKey<LootTable> HOARD_LOOT = loot("chests/dragon_hoard");
	private static final ResourceKey<LootTable> OUTPOST_LOOT = loot("chests/sky_outpost");
	private static final ResourceKey<LootTable> CITADEL_LOOT = loot("chests/sky_citadel");

	private static final int MIN_Y = 180;
	private static final int Y_SPREAD = 50;
	/** How far from the island's middle anything is built: inside the 3x3 chunks a feature may write to. */
	private static final int REACH = 22;

	private enum Kind { WILD, ROOST, OUTPOST, CITADEL, VILLAGE }

	private static ResourceKey<LootTable> loot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, WonderRealms.id(path));
	}

	@Override
	public MapCodec<SkyIslandFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int top = Math.min(MIN_Y + random.nextInt(Y_SPREAD), level.getMaxY() - 40);
		// Centred on the chunk, so the whole island fits in the area this chunk's decoration may build in.
		BlockPos center = new BlockPos((origin.getX() & ~15) + 8, top, (origin.getZ() & ~15) + 8);
		int roll = random.nextInt(20);
		Kind kind = roll < 4 ? Kind.ROOST : roll < 8 ? Kind.OUTPOST : roll == 19 ? Kind.CITADEL : roll == 18 ? Kind.VILLAGE : Kind.WILD;
		DragonElement guardian = DragonElement.WILD[random.nextInt(DragonElement.WILD.length)];
		Theme theme = switch (kind) {
			case ROOST -> Theme.of(guardian);
			case OUTPOST -> random.nextBoolean() ? Theme.MEADOW : Theme.STORM;
			case CITADEL -> random.nextBoolean() ? Theme.STORM : Theme.GLOOM;
			case VILLAGE -> Theme.MEADOW;
			// Meadows are the commonest wild islands (three in ten); every other theme is one in ten.
			case WILD -> random.nextInt(10) < 3 ? Theme.MEADOW : Theme.ELEMENTAL[random.nextInt(Theme.ELEMENTAL.length)];
		};
		int radius = kind == Kind.OUTPOST ? 9 + random.nextInt(3) : kind == Kind.CITADEL || kind == Kind.VILLAGE ? 13 + random.nextInt(2) : 7 + random.nextInt(5);

		Island island = Island.shape(level, center, radius, theme, random);
		island.build(random);
		switch (kind) {
			case ROOST -> roost(island, guardian, random);
			case OUTPOST -> outpost(island, random);
			case CITADEL -> citadel(island, random);
			case VILLAGE -> village(island, random);
			case WILD -> {
				// Every lagoon island that's big enough has its lagoon.
				if (radius >= 9 && random.nextBoolean() || theme == Theme.LAGOON && radius >= 8) {
					pond(island, random);
				}
				shrine(island, random);
			}
		}
		if (kind != Kind.CITADEL && kind != Kind.VILLAGE) {
			landmarks(island, random); // the castle is a citadel's landmark, and the houses a village's
		}
		trees(island, kind, random);
		int waterfalls = theme == Theme.LAGOON ? 1 + random.nextInt(2) : theme.hasWaterfalls() && random.nextInt(3) == 0 ? 1 : 0;
		for (int i = 0; i < waterfalls; i++) {
			waterfall(island, random);
		}
		island.hangUnderside(random);
		plants(island, random);
		islets(island, random);
		return true;
	}

	// ---- Themes ---------------------------------------------------------------------------------------------

	/** What an island is made of and what grows on it. Roosts take on their dragon's element. */
	private enum Theme {
		MEADOW, EMBER, FROST, STORM, VERDANT, LAGOON, CRYSTAL, GLOOM, CANYON, HIVE, RAINBOW, CLOUD;

		/** The themes besides the plain meadow, one for each wild dragon. */
		static final Theme[] ELEMENTAL = {EMBER, FROST, STORM, VERDANT, LAGOON, CRYSTAL, GLOOM, CANYON, HIVE, RAINBOW, CLOUD};

		static Theme of(DragonElement element) {
			return switch (element) {
				case EMBER -> EMBER;
				case FROST -> FROST;
				case STORM -> STORM;
				case VERDANT -> VERDANT;
				case TIDE -> LAGOON;
				case CRYSTAL -> CRYSTAL;
				case SHADOW -> GLOOM;
				case EARTH -> CANYON;
				case HONEY -> HIVE;
				case RAINBOW -> RAINBOW;
				case AIR -> CLOUD;
				default -> MEADOW;
			};
		}

		boolean hasWaterfalls() {
			return this == MEADOW || this == STORM || this == VERDANT || this == LAGOON || this == HIVE || this == RAINBOW || this == CLOUD;
		}

		BlockState top(RandomSource random) {
			return this.top(random, 0.5F);
		}

		/** The top block of the ground, given how far inside the island it is (lagoon islands have sandy shores). */
		BlockState top(RandomSource random, float inner) {
			float r = random.nextFloat();
			return (switch (this) {
				case MEADOW -> Blocks.GRASS_BLOCK;
				case EMBER -> r < 0.55F ? Blocks.BLACKSTONE : r < 0.8F ? Blocks.BASALT : r < 0.92F ? Blocks.SMOOTH_BASALT : Blocks.MAGMA_BLOCK;
				case FROST -> r < 0.85F ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE;
				case STORM -> r < 0.45F ? Blocks.GRASS_BLOCK : r < 0.7F ? Blocks.STONE : r < 0.85F ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
				case VERDANT -> r < 0.6F ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
				case LAGOON -> inner < 0.4F || r < 0.12F ? Blocks.SAND : Blocks.GRASS_BLOCK;
				case CRYSTAL -> r < 0.6F ? Blocks.GRASS_BLOCK : r < 0.93F ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK;
				case GLOOM -> r < 0.45F ? Blocks.PALE_MOSS_BLOCK : r < 0.75F ? Blocks.PODZOL : r < 0.92F ? Blocks.COARSE_DIRT : Blocks.SCULK;
				case CANYON -> r < 0.6F ? Blocks.RED_SAND : r < 0.8F ? Blocks.COARSE_DIRT : r < 0.92F ? Blocks.TERRACOTTA : Blocks.DYED_TERRACOTTA.orange();
				case HIVE, RAINBOW -> Blocks.GRASS_BLOCK;
				case CLOUD -> r < 0.85F ? Blocks.WOOL.white() : Blocks.SNOW_BLOCK; // soft as a cloud
			}).defaultBlockState();
		}

		/** The layer or two under the top. */
		BlockState soil(RandomSource random) {
			float r = random.nextFloat();
			return (switch (this) {
				case MEADOW -> Blocks.DIRT;
				case EMBER -> r < 0.6F ? Blocks.BLACKSTONE : Blocks.BASALT;
				case FROST -> r < 0.7F ? Blocks.SNOW_BLOCK : Blocks.PACKED_ICE;
				case STORM -> r < 0.5F ? Blocks.DIRT : Blocks.STONE;
				case VERDANT -> r < 0.3F ? Blocks.ROOTED_DIRT : Blocks.DIRT;
				case LAGOON -> Blocks.SANDSTONE; // holds the sand up
				case CRYSTAL -> r < 0.7F ? Blocks.CALCITE : Blocks.DIRT;
				case GLOOM -> r < 0.6F ? Blocks.DIRT : Blocks.COARSE_DIRT;
				case CANYON -> r < 0.5F ? Blocks.TERRACOTTA : Blocks.DYED_TERRACOTTA.orange(); // holds the red sand up
				case HIVE -> r < 0.85F ? Blocks.DIRT : Blocks.ROOTED_DIRT;
				case RAINBOW -> Blocks.DIRT;
				case CLOUD -> Blocks.WOOL.white();
			}).defaultBlockState();
		}

		/** Whether this is the island's own natural ground, where things may grow or be built. */
		boolean isGround(BlockState state) {
			return switch (this) {
				case MEADOW -> state.is(Blocks.GRASS_BLOCK);
				case EMBER -> state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.MAGMA_BLOCK);
				case FROST -> state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.PACKED_ICE);
				case STORM -> state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.STONE) || state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT);
				case VERDANT -> state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.GRASS_BLOCK);
				case LAGOON -> state.is(Blocks.SAND) || state.is(Blocks.GRASS_BLOCK);
				case CRYSTAL -> state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.CALCITE) || state.is(Blocks.AMETHYST_BLOCK);
				case GLOOM -> state.is(Blocks.PALE_MOSS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SCULK);
				case CANYON -> state.is(Blocks.RED_SAND) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.TERRACOTTA) || state.is(Blocks.DYED_TERRACOTTA.orange());
				case HIVE, RAINBOW -> state.is(Blocks.GRASS_BLOCK);
				case CLOUD -> state.is(Blocks.WOOL.white()) || state.is(Blocks.SNOW_BLOCK);
			};
		}

		/** Stone for the underside, richer toward the tip where it's hardest to reach. {@code y} sets a canyon's stripes. */
		BlockState rock(RandomSource random, float height, int y) {
			float roll = random.nextFloat();
			boolean deep = height < 0.35F;
			Block block;
			switch (this) {
				// Badlands rock: bands of terracotta the same at the same height right across, studded with gold.
				case CANYON -> {
					if (roll < 0.04F) {
						return Blocks.GOLD_ORE.defaultBlockState();
					}
					block = roll < 0.07F ? Blocks.IRON_ORE : roll < 0.1F ? Blocks.COPPER_ORE : roll < 0.115F ? Blocks.REDSTONE_ORE
						: deep && roll < 0.125F ? Blocks.DIAMOND_ORE : roll < 0.16F ? Blocks.RED_SANDSTONE : null;
					return block != null ? block.defaultBlockState() : canyonBand(y);
				}
				// Bands of every colour, and the most gems of any island.
				case RAINBOW -> {
					block = roll < 0.04F ? Blocks.LAPIS_ORE : roll < 0.065F ? Blocks.EMERALD_ORE : roll < 0.09F ? Blocks.REDSTONE_ORE
						: roll < 0.11F ? Blocks.GOLD_ORE : roll < 0.125F ? Blocks.AMETHYST_BLOCK : deep && roll < 0.14F ? Blocks.DIAMOND_ORE : null;
					return block != null ? block.defaultBlockState() : rainbowBand(y);
				}
				// White stone under the cloud: calcite and diorite.
				case CLOUD -> block = roll < 0.035F ? Blocks.IRON_ORE : roll < 0.065F ? Blocks.COAL_ORE : roll < 0.085F ? Blocks.LAPIS_ORE
					: roll < 0.095F ? Blocks.EMERALD_ORE : deep && roll < 0.105F ? Blocks.DIAMOND_ORE : roll < 0.35F ? Blocks.CALCITE
					: roll < 0.6F ? Blocks.DIORITE : roll < 0.7F ? Blocks.WOOL.white() : Blocks.STONE;
				// Ordinary stone, with pockets of wax, and rich in coal and gold.
				case HIVE -> block = roll < 0.03F ? Blocks.HONEYCOMB_BLOCK : roll < 0.075F ? Blocks.COAL_ORE : roll < 0.1F ? Blocks.IRON_ORE
					: roll < 0.125F ? Blocks.GOLD_ORE : roll < 0.14F ? Blocks.COPPER_ORE : roll < 0.15F ? Blocks.EMERALD_ORE
					: deep && roll < 0.16F ? Blocks.DIAMOND_ORE : roll < 0.28F ? (deep ? Blocks.TUFF : Blocks.ANDESITE) : Blocks.STONE;
				case EMBER -> block = roll < 0.04F ? Blocks.GILDED_BLACKSTONE : roll < 0.06F ? Blocks.GOLD_ORE : roll < 0.09F ? Blocks.IRON_ORE
					: deep && roll < 0.1F ? Blocks.DIAMOND_ORE : roll < 0.15F ? Blocks.MAGMA_BLOCK : roll < 0.6F ? Blocks.BLACKSTONE : Blocks.BASALT;
				case FROST -> block = roll < 0.04F ? Blocks.COAL_ORE : roll < 0.07F ? Blocks.IRON_ORE : roll < 0.08F ? Blocks.EMERALD_ORE
					: deep && roll < 0.09F ? Blocks.DIAMOND_ORE : deep && roll < 0.1F ? Blocks.LAPIS_ORE : roll < 0.18F ? Blocks.PACKED_ICE
					: roll < 0.21F ? Blocks.BLUE_ICE : roll < 0.28F ? Blocks.CALCITE : Blocks.STONE;
				case STORM -> block = roll < 0.09F ? Blocks.COPPER_ORE : roll < 0.12F ? Blocks.IRON_ORE : roll < 0.13F ? Blocks.EMERALD_ORE
					: deep && roll < 0.14F ? Blocks.DIAMOND_ORE : roll < 0.3F ? (deep ? Blocks.TUFF : Blocks.ANDESITE) : Blocks.STONE;
				// Sea-floor stone, rich in lapis and copper.
				case LAGOON -> block = roll < 0.04F ? Blocks.LAPIS_ORE : roll < 0.08F ? Blocks.COPPER_ORE : roll < 0.11F ? Blocks.IRON_ORE
					: roll < 0.12F ? Blocks.GOLD_ORE : deep && roll < 0.13F ? Blocks.DIAMOND_ORE : roll < 0.3F ? Blocks.PRISMARINE
					: roll < 0.45F ? Blocks.SANDSTONE : Blocks.STONE;
				// A geode turned inside out: calcite and smooth basalt, amethyst, and the most diamonds of any island.
				case CRYSTAL -> block = roll < 0.025F ? Blocks.AMETHYST_BLOCK : deep && roll < 0.03F ? Blocks.BUDDING_AMETHYST
					: roll < 0.06F ? Blocks.IRON_ORE : roll < 0.075F ? Blocks.EMERALD_ORE : deep && roll < 0.095F ? Blocks.DIAMOND_ORE
					: roll < 0.105F ? Blocks.LAPIS_ORE : roll < 0.25F ? Blocks.CALCITE : roll < 0.4F ? Blocks.SMOOTH_BASALT
					: roll < 0.55F ? Blocks.TUFF : Blocks.STONE;
				// Deepslate from the bottom of the world, veined with sculk.
				case GLOOM -> block = roll < 0.04F ? Blocks.DEEPSLATE_COAL_ORE : roll < 0.07F ? Blocks.DEEPSLATE_IRON_ORE
					: roll < 0.09F ? Blocks.DEEPSLATE_REDSTONE_ORE : roll < 0.1F ? Blocks.DEEPSLATE_GOLD_ORE
					: deep && roll < 0.115F ? Blocks.DEEPSLATE_DIAMOND_ORE : roll < 0.18F ? Blocks.SCULK : roll < 0.3F ? Blocks.TUFF
					: roll < 0.42F ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE;
				default -> block = roll < 0.035F ? Blocks.COAL_ORE : roll < 0.065F ? Blocks.IRON_ORE : roll < 0.085F ? Blocks.COPPER_ORE
					: roll < 0.097F ? Blocks.GOLD_ORE : roll < 0.105F ? Blocks.EMERALD_ORE : deep && roll < 0.115F ? Blocks.DIAMOND_ORE
					: deep && roll < 0.123F ? Blocks.LAPIS_ORE : roll < 0.23F ? (deep ? Blocks.TUFF : Blocks.ANDESITE) : Blocks.STONE;
			}
			return block.defaultBlockState();
		}

		/** Building stone for ruins: weathered, in keeping with the island. */
		BlockState ruinStone(RandomSource random) {
			int pick = random.nextInt(4);
			return (switch (this) {
				case EMBER -> pick == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : pick == 1 ? Blocks.BLACKSTONE
					: random.nextInt(8) == 0 ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
				case FROST -> pick == 0 ? Blocks.PACKED_ICE : pick == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
				case STORM -> pick == 0 ? Blocks.CUT_COPPER.weathering().oxidized() : pick == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
				case LAGOON -> pick == 0 ? Blocks.PRISMARINE : pick == 1 ? Blocks.DARK_PRISMARINE : Blocks.PRISMARINE_BRICKS;
				case CRYSTAL -> pick == 0 ? Blocks.CALCITE : pick == 1 ? Blocks.POLISHED_TUFF : Blocks.TUFF_BRICKS;
				case GLOOM -> pick == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : pick == 1 ? Blocks.DEEPSLATE_TILES
					: pick == 2 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_BRICKS;
				case CANYON -> pick == 0 ? Blocks.CUT_RED_SANDSTONE : pick == 1 ? Blocks.CHISELED_RED_SANDSTONE
					: pick == 2 ? Blocks.SMOOTH_RED_SANDSTONE : Blocks.RED_SANDSTONE;
				case HIVE -> pick == 0 ? Blocks.HONEYCOMB_BLOCK : pick == 1 ? Blocks.CUT_SANDSTONE
					: pick == 2 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE;
				case RAINBOW -> pick == 0 ? Blocks.QUARTZ_BRICKS : pick == 1 ? Blocks.QUARTZ_BLOCK
					: pick == 2 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ;
				case CLOUD -> pick == 0 ? Blocks.POLISHED_DIORITE : pick == 1 ? Blocks.DIORITE
					: pick == 2 ? Blocks.QUARTZ_BRICKS : Blocks.SMOOTH_QUARTZ;
				default -> pick == 0 ? Blocks.MOSSY_STONE_BRICKS : pick == 1 ? Blocks.CRACKED_STONE_BRICKS
					: pick == 2 ? Blocks.STONE_BRICKS : Blocks.MOSSY_COBBLESTONE;
			}).defaultBlockState();
		}
	}

	/** A canyon's stripes, in sunset colours: terracotta whose colour depends only on its height. */
	private static final Block[] CANYON_BANDS = {Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.orange(),
		Blocks.DYED_TERRACOTTA.yellow(), Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.white(),
		Blocks.DYED_TERRACOTTA.lightGray(), Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.orange()};

	private static BlockState canyonBand(int y) {
		return CANYON_BANDS[Math.floorMod(y, CANYON_BANDS.length)].defaultBlockState();
	}

	/** A rainbow island's rock: bands of concrete, red to violet, two blocks deep, the same at the same height everywhere. */
	private static final Block[] RAINBOW_BANDS = {Blocks.CONCRETE.red(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.yellow(),
		Blocks.CONCRETE.lime(), Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.blue(), Blocks.CONCRETE.purple()};
	/** The same seven colours in glass, for rainbows and prisms. */
	private static final Block[] RAINBOW_GLASS = {Blocks.STAINED_GLASS.red(), Blocks.STAINED_GLASS.orange(), Blocks.STAINED_GLASS.yellow(),
		Blocks.STAINED_GLASS.lime(), Blocks.STAINED_GLASS.lightBlue(), Blocks.STAINED_GLASS.blue(), Blocks.STAINED_GLASS.purple()};

	private static BlockState rainbowBand(int y) {
		return RAINBOW_BANDS[Math.floorMod(Math.floorDiv(y, 2), RAINBOW_BANDS.length)].defaultBlockState();
	}

	// ---- Shape ----------------------------------------------------------------------------------------------

	/** The island being built: its shape, column by column, its theme, and the level it goes into. */
	private static final class Island {
		private static final int SIZE = REACH * 2 + 1;
		private static final int NONE = Integer.MIN_VALUE;

		final WorldGenLevel level;
		final BlockPos center;
		final int radius;
		final Theme theme;
		private final int[] surface = new int[SIZE * SIZE];
		private final int[] bottom = new int[SIZE * SIZE];
		private final float[] inner = new float[SIZE * SIZE];

		private Island(WorldGenLevel level, BlockPos center, int radius, Theme theme) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.theme = theme;
			Arrays.fill(this.surface, NONE);
		}

		/**
		 * A main mass and two or three lobes around it, with a coastline of broad bays and a finer ragged edge. The
		 * ground rolls gently and rises toward the middle; the underside hangs down in rough cones, deepest under
		 * the middle of each lobe.
		 */
		static Island shape(WorldGenLevel level, BlockPos center, int radius, Theme theme, RandomSource random) {
			Island island = new Island(level, center, radius, theme);
			Noise edge = new Noise(random.nextLong());
			Noise ground = new Noise(random.nextLong());
			int lobes = 2 + random.nextInt(2);
			double[][] blobs = new double[lobes + 1][];
			blobs[0] = new double[] {0, 0, radius * 0.85};
			double start = random.nextDouble() * Math.PI * 2;
			for (int i = 1; i <= lobes; i++) {
				double angle = start + i * Math.PI * 2 / lobes + (random.nextDouble() - 0.5);
				double distance = radius * (0.65 + random.nextDouble() * 0.35);
				blobs[i] = new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance, radius * (0.45 + random.nextDouble() * 0.2)};
			}
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					double wobble = 0.75 + 0.35 * edge.at(dx * 0.18, dz * 0.18) + 0.15 * edge.at(dx * 0.6 + 90, dz * 0.6);
					double inside = 0; // 0 at the edge, 1 at the very middle of a lobe
					for (double[] blob : blobs) {
						inside = Math.max(inside, 1 - Math.sqrt((dx - blob[0]) * (dx - blob[0]) + (dz - blob[1]) * (dz - blob[1])) / (blob[2] * wobble));
					}
					inside -= Math.max(0, Math.max(Math.abs(dx), Math.abs(dz)) - (REACH - 6)) * 0.2; // taper well inside the limit
					if (inside <= 0) {
						continue;
					}
					int i = index(dx, dz);
					int y = center.getY() + (int) Math.round((ground.at(dx * 0.12, dz * 0.12) - 0.5) * 3 + inside * 2);
					int depth = 2 + (int) (Math.pow(inside, 1.3) * radius * 1.6 + edge.at(dx * 0.5 + 50, dz * 0.5) * 4 * inside);
					island.surface[i] = y;
					island.bottom[i] = y - depth;
					island.inner[i] = (float) inside;
				}
			}
			return island;
		}

		private static int index(int dx, int dz) {
			return (dx + REACH) * SIZE + dz + REACH;
		}

		private static boolean inReach(int dx, int dz) {
			return Math.abs(dx) <= REACH && Math.abs(dz) <= REACH;
		}

		boolean has(int dx, int dz) {
			return inReach(dx, dz) && this.surface[index(dx, dz)] != NONE;
		}

		int surface(int dx, int dz) {
			return this.surface[index(dx, dz)];
		}

		void setSurface(int dx, int dz, int y) {
			this.surface[index(dx, dz)] = y;
		}

		/** How far inside the island a column is: 0 at (or beyond) the edge, 1 in the middle of a lobe. */
		float inner(int dx, int dz) {
			return this.has(dx, dz) ? this.inner[index(dx, dz)] : 0F;
		}

		/** The top block of a column. */
		BlockPos ground(int dx, int dz) {
			return this.column(dx, this.surface(dx, dz), dz);
		}

		BlockPos column(int dx, int y, int dz) {
			return new BlockPos(this.center.getX() + dx, y, this.center.getZ() + dz);
		}

		boolean isRim(int dx, int dz) {
			return this.has(dx, dz) && (!this.has(dx + 1, dz) || !this.has(dx - 1, dz) || !this.has(dx, dz + 1) || !this.has(dx, dz - 1));
		}

		/** A column whose top is still the island's natural ground, open to the sky above it. */
		boolean isOpenGround(int dx, int dz) {
			return this.has(dx, dz) && this.theme.isGround(this.get(this.ground(dx, dz))) && this.isAir(this.ground(dx, dz).above());
		}

		void set(BlockPos pos, BlockState state) {
			if (inReach(pos.getX() - this.center.getX(), pos.getZ() - this.center.getZ())) {
				this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		}

		BlockState get(BlockPos pos) {
			return this.level.getBlockState(pos);
		}

		boolean isAir(BlockPos pos) {
			return this.level.isEmptyBlock(pos);
		}

		/** The theme's ground over a layer or two of soil, over stone that gets richer toward the tip. */
		void build(RandomSource random) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					if (!this.has(dx, dz)) {
						continue;
					}
					int top = this.surface(dx, dz);
					int low = this.bottom[index(dx, dz)];
					int soil = this.inner(dx, dz) < 0.25F ? 1 : 2 + random.nextInt(2);
					for (int y = low; y <= top; y++) {
						int below = top - y;
						BlockState state = below == 0 ? this.theme.top(random, this.inner(dx, dz))
							: below <= soil ? this.theme.soil(random)
							: this.theme.rock(random, (float) (y - low) / Math.max(1, top - low), y);
						if (y == low && state.is(Blocks.SAND)) {
							state = Blocks.SANDSTONE.defaultBlockState(); // nothing under it to hold sand up
						} else if (y == low && state.is(Blocks.RED_SAND)) {
							state = Blocks.RED_SANDSTONE.defaultBlockState();
						}
						this.set(this.column(dx, y, dz), state);
					}
				}
			}
		}

		/** Whatever hangs from the underside, mostly out toward the edges: berries, roots, icicles, chains... */
		void hangUnderside(RandomSource random) {
			float chance = this.theme == Theme.VERDANT || this.theme == Theme.GLOOM || this.theme == Theme.HIVE ? 0.22F
				: this.theme == Theme.CANYON || this.theme == Theme.RAINBOW || this.theme == Theme.CLOUD ? 0.18F : 0.14F;
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					if (!this.has(dx, dz) || this.inner(dx, dz) > 0.6F || random.nextFloat() > chance) {
						continue;
					}
					BlockPos tip = this.column(dx, this.bottom[index(dx, dz)] - 1, dz);
					if (!this.isAir(tip)) {
						continue;
					}
					switch (this.theme) {
						case FROST -> {
							int length = 1 + random.nextInt(3);
							for (int i = 0; i < length; i++) {
								this.set(tip.below(i), Blocks.PACKED_ICE.defaultBlockState()); // icicles
							}
						}
						case EMBER -> {
							int length = 1 + random.nextInt(2);
							for (int i = 0; i < length; i++) {
								this.set(tip.below(i), (random.nextInt(4) == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState());
							}
						}
						case STORM -> {
							if (random.nextBoolean()) {
								this.set(tip, Blocks.LIGHTNING_ROD.weathering().oxidized().defaultBlockState()
									.setValue(BlockStateProperties.FACING, Direction.DOWN));
							} else {
								int length = 1 + random.nextInt(3);
								for (int i = 0; i < length; i++) {
									this.set(tip.below(i), Blocks.IRON_CHAIN.defaultBlockState());
								}
								if (random.nextInt(3) == 0) {
									this.set(tip.below(length), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
								}
							}
						}
						case VERDANT -> {
							float pick = random.nextFloat();
							if (pick < 0.15F) {
								this.set(tip, Blocks.SPORE_BLOSSOM.defaultBlockState());
							} else if (pick < 0.35F) {
								this.set(tip, Blocks.HANGING_ROOTS.defaultBlockState());
							} else {
								this.glowBerries(tip, 1 + random.nextInt(5), 0.4F, random);
							}
						}
						case LAGOON -> {
							if (random.nextInt(3) == 0) {
								this.set(tip, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
							} else {
								int length = 1 + random.nextInt(2);
								for (int i = 0; i < length; i++) {
									this.set(tip.below(i), Blocks.PRISMARINE_WALL.defaultBlockState());
								}
								if (random.nextInt(3) == 0) {
									this.set(tip.below(length), Blocks.SEA_LANTERN.defaultBlockState());
								}
							}
						}
						case CRYSTAL -> {
							// Crystals hang point-down, some from a stub of amethyst.
							int length = random.nextInt(3);
							for (int i = 0; i < length; i++) {
								this.set(tip.below(i), Blocks.AMETHYST_BLOCK.defaultBlockState());
							}
							Block crystal = random.nextInt(3) == 0 ? Blocks.LARGE_AMETHYST_BUD : Blocks.AMETHYST_CLUSTER;
							this.set(tip.below(length), crystal.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN));
						}
						case GLOOM -> {
							int length = 1 + random.nextInt(3);
							if (random.nextInt(4) == 0) {
								for (int i = 0; i < length; i++) {
									this.set(tip.below(i), Blocks.IRON_CHAIN.defaultBlockState());
								}
								this.set(tip.below(length), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
							} else {
								for (int i = 0; i < length; i++) {
									this.set(tip.below(i), Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, i == length - 1));
								}
							}
						}
						case CANYON -> {
							// Stalactites of dripstone, some hanging from a stub of dripstone block.
							int stub = random.nextInt(2);
							for (int i = 0; i < stub; i++) {
								this.set(tip.below(i), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
							}
							int length = 1 + random.nextInt(3);
							for (int i = 0; i < length; i++) {
								SpeleothemThickness thickness = i == length - 1 ? SpeleothemThickness.TIP : i == length - 2 ? SpeleothemThickness.FRUSTUM
									: i == 0 ? SpeleothemThickness.BASE : SpeleothemThickness.MIDDLE;
								this.set(tip.below(stub + i), Blocks.POINTED_DRIPSTONE.defaultBlockState()
									.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
									.setValue(BlockStateProperties.SPELEOTHEM_THICKNESS, thickness));
							}
						}
						case RAINBOW -> {
							// Drops of coloured glass, like a rainbow dripping off the edge.
							Block glass = RAINBOW_GLASS[random.nextInt(RAINBOW_GLASS.length)];
							int length = 1 + random.nextInt(3);
							for (int i = 0; i < length; i++) {
								this.set(tip.below(i), glass.defaultBlockState());
							}
						}
						case CLOUD -> {
							// Rain: thin streaks of pale glass falling from the cloud, and lumps of cloud.
							if (random.nextInt(3) == 0) {
								this.set(tip, Blocks.WOOL.white().defaultBlockState());
							} else {
								int length = 2 + random.nextInt(4);
								for (int i = 0; i < length; i++) {
									this.set(tip.below(i), Blocks.STAINED_GLASS_PANE.lightBlue().defaultBlockState());
								}
							}
						}
						case HIVE -> {
							// Wax and honey, and bee nests so full that honey drips from them into the sky below.
							float pick = random.nextFloat();
							if (pick < 0.35F) {
								this.set(tip, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
								if (random.nextBoolean()) {
									beeNest(this, tip.below(), Direction.Plane.HORIZONTAL.getRandomDirection(random), true, random);
								}
							} else if (pick < 0.5F) {
								this.set(tip, Blocks.HONEY_BLOCK.defaultBlockState());
							} else if (pick < 0.65F) {
								this.set(tip, Blocks.HANGING_ROOTS.defaultBlockState());
							} else {
								this.glowBerries(tip, 1 + random.nextInt(3), 0.3F, random);
							}
						}
						default -> {
							if (random.nextInt(3) == 0) {
								this.set(tip, Blocks.HANGING_ROOTS.defaultBlockState());
							} else {
								this.glowBerries(tip, 1 + random.nextInt(4), 0.25F, random);
							}
						}
					}
				}
			}
		}

		private void glowBerries(BlockPos tip, int length, float berryChance, RandomSource random) {
			for (int i = 0; i < length; i++) {
				BlockState vine = i == length - 1
					? Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE)
					: Blocks.CAVE_VINES_PLANT.defaultBlockState();
				this.set(tip.below(i), vine.setValue(CaveVines.BERRIES, random.nextFloat() < berryChance));
			}
		}
	}

	/** Smooth value noise in [0, 1], for ragged coastlines and rolling ground. */
	private record Noise(long seed) {
		double at(double x, double z) {
			int x0 = Mth.floor(x);
			int z0 = Mth.floor(z);
			double fx = x - x0;
			double fz = z - z0;
			double u = fx * fx * (3 - 2 * fx);
			double v = fz * fz * (3 - 2 * fz);
			return Mth.lerp(v, Mth.lerp(u, this.hash(x0, z0), this.hash(x0 + 1, z0)), Mth.lerp(u, this.hash(x0, z0 + 1), this.hash(x0 + 1, z0 + 1)));
		}

		private double hash(int x, int z) {
			long h = this.seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
			h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
			h ^= h >>> 29;
			return (h >>> 11) * 0x1.0p-53;
		}
	}

	// ---- Landmarks ------------------------------------------------------------------------------------------

	/**
	 * Each theme's own features: storm spires, ice spikes, smoking vents and basalt columns, sea lantern pillars,
	 * amethyst crystals, or soul-lit obelisks and soul fire vents.
	 */
	private static void landmarks(Island island, RandomSource random) {
		int count = switch (island.theme) {
			case STORM, FROST, GLOOM -> 2 + random.nextInt(3);
			case EMBER, CRYSTAL -> 3 + random.nextInt(3);
			case LAGOON, HIVE, CLOUD -> 1 + random.nextInt(2);
			case RAINBOW -> 2 + random.nextInt(2);
			case CANYON -> 3 + random.nextInt(3);
			default -> 0;
		};
		for (int attempt = 0; attempt < 40 && count > 0; attempt++) {
			int dx = random.nextInt(island.radius * 2 + 1) - island.radius;
			int dz = random.nextInt(island.radius * 2 + 1) - island.radius;
			if (island.inner(dx, dz) < 0.2F || dx * dx + dz * dz < 30 || !island.isOpenGround(dx, dz)) {
				continue;
			}
			BlockPos ground = island.ground(dx, dz);
			switch (island.theme) {
				case STORM -> spire(island, ground, random);
				case FROST -> iceSpike(island, ground, random);
				case EMBER -> {
					if (count % 2 == 0) {
						vent(island, ground, random);
					} else {
						basaltColumn(island, ground, random);
					}
				}
				case LAGOON -> seaLanternPillar(island, ground, random);
				case CRYSTAL -> crystal(island, ground, random);
				case CANYON -> {
					if (count % 3 == 0) {
						oreOutcrop(island, ground, random);
					} else {
						hoodoo(island, ground, random);
					}
				}
				case HIVE -> {
					if (count == 1 && island.inner(dx, dz) > 0.4F) {
						giantHive(island, ground, random);
					} else {
						hiveStump(island, ground, random);
					}
				}
				case RAINBOW -> {
					if (count == 1) {
						rainbowArch(island, ground, random);
					} else {
						prism(island, ground, random);
					}
				}
				case CLOUD -> {
					if (count == 1) {
						windmill(island, ground, random);
					} else {
						cloudPuff(island, ground, random);
					}
				}
				case GLOOM -> {
					if (count % 2 == 0) {
						soulVent(island, ground, random);
					} else {
						obelisk(island, ground, random);
					}
				}
				default -> {
				}
			}
			count--;
		}
	}

	/**
	 * A rainbow: seven bands of stained glass, red outside to violet inside, arching over the island in a half circle
	 * (and sometimes right off its edge). Solid enough to walk over.
	 */
	private static void rainbowArch(Island island, BlockPos ground, RandomSource random) {
		Direction along = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		int inner = 3;
		for (int u = -inner - RAINBOW_GLASS.length; u <= inner + RAINBOW_GLASS.length; u++) {
			for (int v = 0; v <= inner + RAINBOW_GLASS.length; v++) {
				int band = (int) Math.floor(Math.sqrt(u * u + v * v)) - inner;
				if (band < 0 || band >= RAINBOW_GLASS.length) {
					continue;
				}
				BlockPos pos = ground.above(1 + v).relative(along, u);
				if (island.isAir(pos)) {
					island.set(pos, RAINBOW_GLASS[RAINBOW_GLASS.length - 1 - band].defaultBlockState()); // red on the outside
				}
			}
		}
	}

	/** A prism: a quartz plinth under a column of glass in every colour, with a light shining from the top. */
	private static void prism(Island island, BlockPos ground, RandomSource random) {
		island.set(ground.above(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
		for (int i = 0; i < RAINBOW_GLASS.length; i++) {
			island.set(ground.above(2 + i), RAINBOW_GLASS[RAINBOW_GLASS.length - 1 - i].defaultBlockState());
		}
		island.set(ground.above(2 + RAINBOW_GLASS.length), Blocks.END_ROD.defaultBlockState());
	}

	/**
	 * A windmill: a tower of birch with four sails of white wool turning in the wind (well, not turning: it's a
	 * windmill made of blocks).
	 */
	private static void windmill(Island island, BlockPos ground, RandomSource random) {
		int height = 6;
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
		}
		island.set(ground.above(height + 1), Blocks.BIRCH_SLAB.defaultBlockState());
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction side = facing.getClockWise();
		BlockPos hub = ground.above(height - 1).relative(facing);
		island.set(hub, Blocks.OAK_PLANKS.defaultBlockState());
		// Four arms, each with a sail along one side, like a pinwheel.
		int[][] arms = {{0, 1, 1, 0}, {1, 0, 0, -1}, {0, -1, -1, 0}, {-1, 0, 0, 1}};
		for (int[] arm : arms) {
			for (int i = 1; i <= 3; i++) {
				BlockPos spar = hub.relative(side, arm[0] * i).above(arm[1] * i);
				island.set(spar, Blocks.STRIPPED_BIRCH_LOG.defaultBlockState()
					.setValue(RotatedPillarBlock.AXIS, arm[1] != 0 ? Direction.Axis.Y : side.getAxis()));
				island.set(spar.relative(side, arm[2]).above(arm[3]), Blocks.WOOL.white().defaultBlockState());
			}
		}
	}

	/** A little cloud drifting over the island: a soft, lumpy ball of white wool. */
	private static void cloudPuff(Island island, BlockPos ground, RandomSource random) {
		BlockPos middle = ground.above(7 + random.nextInt(5));
		int width = 2 + random.nextInt(2);
		for (int dx = -width - 1; dx <= width + 1; dx++) {
			for (int dz = -width - 1; dz <= width + 1; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					double d = dx * dx / (double) (width * width) + dz * dz / (double) (width * width) + dy * dy / 2.5;
					if (d <= 1.0 + random.nextFloat() * 0.3) {
						island.set(middle.offset(dx, dy, dz), Blocks.WOOL.white().defaultBlockState());
					}
				}
			}
		}
	}

	/** A hoodoo: a tower of banded rock left standing by the wind, under a cap of harder sandstone that juts out. */
	private static void hoodoo(Island island, BlockPos ground, RandomSource random) {
		int height = 3 + random.nextInt(4);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), canyonBand(ground.getY() + y));
		}
		BlockPos cap = ground.above(height + 1);
		island.set(cap, Blocks.RED_SANDSTONE.defaultBlockState());
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) > 0 && island.isAir(cap.relative(side))) {
				island.set(cap.relative(side), Blocks.RED_SANDSTONE_SLAB.defaultBlockState());
			}
			if (random.nextInt(3) == 0 && island.isAir(ground.above().relative(side))) {
				island.set(ground.above().relative(side), canyonBand(ground.getY() + 1)); // the rubble at its foot
			}
		}
	}

	/** Raw ore breaking out of the ground: an Earth dragon's favourite meal, with a nugget of solid raw metal at its heart. */
	private static void oreOutcrop(Island island, BlockPos ground, RandomSource random) {
		int pick = random.nextInt(5);
		Block raw = pick < 2 ? Blocks.RAW_COPPER_BLOCK : pick < 4 ? Blocks.RAW_IRON_BLOCK : Blocks.RAW_GOLD_BLOCK;
		Block ore = pick < 2 ? Blocks.COPPER_ORE : pick < 4 ? Blocks.IRON_ORE : Blocks.GOLD_ORE;
		island.set(ground, raw.defaultBlockState());
		island.set(ground.above(), ore.defaultBlockState());
		if (random.nextBoolean()) {
			island.set(ground.above(2), ore.defaultBlockState());
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean()) {
				island.set(ground.relative(side), ore.defaultBlockState());
			}
			if (random.nextInt(3) == 0 && island.isAir(ground.above().relative(side))) {
				island.set(ground.above().relative(side), (random.nextBoolean() ? ore : Blocks.RED_SANDSTONE).defaultBlockState());
			}
		}
	}

	/**
	 * A giant beehive: a dome of honeycomb and coiled straw, like an old bee skep, with bee nests set into its sides
	 * and a doorway to crawl in by. Inside, on a floor of honey, the bees keep a little treasure.
	 */
	private static void giantHive(Island island, BlockPos ground, RandomSource random) {
		double[] widths = {2.4, 2.9, 2.9, 2.5, 1.9, 1.1};
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int y = 0; y < widths.length; y++) {
			double width = widths[y];
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > width) {
						continue;
					}
					BlockPos pos = ground.offset(dx, y + 1, dz);
					boolean shell = d > width - 1.1 || y == widths.length - 1;
					if (y == 0 && !shell) {
						island.set(pos.below(), Blocks.HONEY_BLOCK.defaultBlockState()); // the floor
					}
					if (!shell) {
						continue;
					}
					boolean doorway = y < 2 && dx == door.getStepX() * 2 && dz == door.getStepZ() * 2
						|| y < 2 && dx == door.getStepX() * 3 && dz == door.getStepZ() * 3;
					if (doorway) {
						island.set(pos, Blocks.AIR.defaultBlockState());
					} else {
						island.set(pos, y % 2 == 0 ? Blocks.HONEYCOMB_BLOCK.defaultBlockState()
							: Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X));
					}
				}
			}
		}
		// Bee nests let into the dome, facing out, brimming with honey.
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (side != door && random.nextInt(3) > 0) {
				beeNest(island, ground.above(3).relative(side, 2), side, random.nextBoolean(), random);
			}
		}
		chest(island, ground.above(), ISLAND_LOOT, random);
	}

	/** An old oak stump with a bee nest on top. */
	private static void hiveStump(Island island, BlockPos ground, RandomSource random) {
		int height = 1 + random.nextInt(2);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), Blocks.OAK_LOG.defaultBlockState());
		}
		beeNest(island, ground.above(height + 1), Direction.Plane.HORIZONTAL.getRandomDirection(random), random.nextBoolean(), random);
	}

	/** A bee nest, brimming with honey or part full, with two or three bees inside. */
	private static void beeNest(Island island, BlockPos pos, Direction facing, boolean full, RandomSource random) {
		island.set(pos, Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, facing)
			.setValue(BeehiveBlock.HONEY_LEVEL, full ? BeehiveBlock.MAX_HONEY_LEVELS : random.nextInt(BeehiveBlock.MAX_HONEY_LEVELS)));
		if (island.level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
			for (int bees = 2 + random.nextInt(2); bees > 0; bees--) {
				hive.storeBee(BeehiveBlockEntity.Occupant.create(random.nextInt(599)));
			}
		}
	}

	/** A weathered prismarine pillar crowned with a sea lantern, like a lighthouse for dragons. */
	private static void seaLanternPillar(Island island, BlockPos ground, RandomSource random) {
		int height = 2 + random.nextInt(3);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), island.theme.ruinStone(random));
		}
		island.set(ground.above(height + 1), Blocks.SEA_LANTERN.defaultBlockState());
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean() && island.isAir(ground.above().relative(side))) {
				island.set(ground.above().relative(side), Blocks.DARK_PRISMARINE.defaultBlockState());
			}
		}
	}

	/**
	 * A great amethyst crystal: a column of amethyst with clusters growing from its sides and its tip, and often a
	 * budding amethyst at its heart, so the crystals there grow back.
	 */
	private static void crystal(Island island, BlockPos ground, RandomSource random) {
		int height = 2 + random.nextInt(5);
		int heart = random.nextBoolean() ? 1 + random.nextInt(height) : -1;
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), (y == heart ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK).defaultBlockState());
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos face = ground.above(y).relative(side);
				if (random.nextInt(4) == 0 && island.isAir(face)) {
					Block bud = random.nextBoolean() ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD;
					island.set(face, bud.defaultBlockState().setValue(AmethystClusterBlock.FACING, side));
				}
			}
		}
		island.set(ground.above(height + 1), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean() && island.theme.isGround(island.get(ground.relative(side)))) {
				island.set(ground.relative(side), Blocks.CALCITE.defaultBlockState());
			}
		}
	}

	/** A deepslate obelisk with a soul lantern on top and sculk creeping round its foot. */
	private static void obelisk(Island island, BlockPos ground, RandomSource random) {
		int height = 3 + random.nextInt(3);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), y == 1 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : island.theme.ruinStone(random));
		}
		island.set(ground.above(height + 1), Blocks.SOUL_LANTERN.defaultBlockState());
		sculkPatch(island, ground, random);
	}

	/** A soul fire smouldering in the ground, sending up a column of pale smoke you can see from far off. */
	private static void soulVent(Island island, BlockPos ground, RandomSource random) {
		island.set(ground, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
		sculkPatch(island, ground, random);
	}

	private static void sculkPatch(Island island, BlockPos ground, RandomSource random) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) > 0 && island.theme.isGround(island.get(ground.relative(side)))) {
				island.set(ground.relative(side), Blocks.SCULK.defaultBlockState());
			}
		}
	}

	/** A needle of weathered stone banded with copper, crowned with a lightning rod that draws the storm's strikes. */
	private static void spire(Island island, BlockPos ground, RandomSource random) {
		int height = 4 + random.nextInt(7);
		Block[] stones = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.COBBLESTONE};
		int band = 1 + random.nextInt(Math.max(1, height - 2));
		for (int y = 1; y <= height; y++) {
			BlockState block = y == band ? Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState() : stones[random.nextInt(stones.length)].defaultBlockState();
			island.set(ground.above(y), block);
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextBoolean() && island.isAir(ground.above().relative(side))) {
				island.set(ground.above().relative(side), stones[random.nextInt(stones.length)].defaultBlockState()); // a rough footing
			}
		}
		island.set(ground.above(height + 1), Blocks.LIGHTNING_ROD.weathering().weathered().defaultBlockState());
	}

	/** A pillar of packed ice, broad at the foot and tipped with blue ice. */
	private static void iceSpike(Island island, BlockPos ground, RandomSource random) {
		int height = 3 + random.nextInt(5);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), (y == height ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState());
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos foot = ground.above().relative(side);
			if (island.isAir(foot)) {
				island.set(foot, Blocks.PACKED_ICE.defaultBlockState());
				if (height > 4 && random.nextBoolean() && island.isAir(foot.above())) {
					island.set(foot.above(), Blocks.PACKED_ICE.defaultBlockState());
				}
			}
		}
	}

	/** A fumarole: a smouldering vent ringed with magma, sending up a column of smoke you can see for miles. */
	private static void vent(Island island, BlockPos ground, RandomSource random) {
		island.set(ground, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) > 0 && island.theme.isGround(island.get(ground.relative(side)))) {
				island.set(ground.relative(side), Blocks.MAGMA_BLOCK.defaultBlockState());
			}
		}
	}

	private static void basaltColumn(Island island, BlockPos ground, RandomSource random) {
		int height = 1 + random.nextInt(5);
		for (int y = 1; y <= height; y++) {
			island.set(ground.above(y), Blocks.BASALT.defaultBlockState());
		}
	}

	// ---- Trees and plants -----------------------------------------------------------------------------------

	private static void trees(Island island, Kind kind, RandomSource random) {
		int wanted = switch (kind) {
			case WILD -> switch (island.theme) {
				case STORM, CANYON -> random.nextInt(2);
				case CLOUD -> 0; // nothing takes root in a cloud
				case VERDANT, HIVE -> 3 + random.nextInt(3);
				default -> 2 + random.nextInt(island.radius > 9 ? 3 : 2);
			};
			case ROOST -> 1 + random.nextInt(2);
			case OUTPOST -> random.nextInt(2);
			case CITADEL -> 1 + random.nextInt(3);
			case VILLAGE -> 2 + random.nextInt(3);
		};
		int clearing = kind == Kind.WILD ? 3 : kind == Kind.CITADEL ? 11 : kind == Kind.VILLAGE ? 12 : 6; // keep the middle free for the nest, tower or castle
		List<BlockPos> planted = new ArrayList<>();
		for (int attempt = 0; attempt < 40 && planted.size() < wanted; attempt++) {
			int dx = random.nextInt(island.radius * 2 + 1) - island.radius;
			int dz = random.nextInt(island.radius * 2 + 1) - island.radius;
			if (island.inner(dx, dz) < 0.25F || dx * dx + dz * dz < clearing * clearing || !island.isOpenGround(dx, dz)) {
				continue;
			}
			BlockPos ground = island.ground(dx, dz);
			if (planted.stream().anyMatch(other -> other.distSqr(ground) < 25)) {
				continue;
			}
			boolean clear = true;
			for (int y = 1; y <= 10 && clear; y++) {
				clear = island.isAir(ground.above(y));
			}
			if (clear) {
				planted.add(ground);
				tree(island, ground.above(), random);
			}
		}
	}

	/**
	 * A tree in keeping with the island: oak, birch, azalea, snowy spruce, a charred, leafless trunk, a palm, a
	 * cherry in blossom, or a pale oak hung with moss.
	 */
	private static void tree(Island island, BlockPos base, RandomSource random) {
		List<BlockPos> logs = new ArrayList<>();
		Map<BlockPos, BlockState> leaves = new HashMap<>();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		int kind = random.nextInt(10);
		switch (island.theme) {
			case FROST -> {
				log = Blocks.SPRUCE_LOG.defaultBlockState();
				spruce(base, random, logs, leaves);
			}
			case EMBER -> {
				charredTree(island, base, random);
				return;
			}
			case STORM -> {
				log = Blocks.BIRCH_LOG.defaultBlockState();
				column(logs, base, 4 + random.nextInt(2));
				crown(leaves, base.above(logs.size() - 1), 1.6, 1.0, Blocks.BIRCH_LEAVES.defaultBlockState(), null, random);
			}
			case VERDANT -> {
				if (kind < 7) {
					azalea(base, random, logs, leaves, 2.8 + random.nextDouble() * 0.8);
					island.set(base.below(), Blocks.ROOTED_DIRT.defaultBlockState());
				} else {
					column(logs, base, 4 + random.nextInt(3));
					crown(leaves, base.above(logs.size() - 1), 2.6, 0.75, Blocks.OAK_LEAVES.defaultBlockState(), null, random);
				}
			}
			case LAGOON -> {
				log = Blocks.JUNGLE_LOG.defaultBlockState();
				palm(base, random, logs, leaves);
			}
			case CRYSTAL -> {
				log = Blocks.CHERRY_LOG.defaultBlockState();
				column(logs, base, 4 + random.nextInt(2));
				crown(leaves, base.above(logs.size() - 1), 2.8 + random.nextDouble() * 0.5, 0.55, Blocks.CHERRY_LEAVES.defaultBlockState(), null, random);
			}
			case GLOOM -> {
				log = Blocks.PALE_OAK_LOG.defaultBlockState();
				column(logs, base, 5 + random.nextInt(2));
				crown(leaves, base.above(logs.size() - 1), 2.4, 0.8, Blocks.PALE_OAK_LEAVES.defaultBlockState(), null, random);
			}
			case CANYON -> {
				log = Blocks.ACACIA_LOG.defaultBlockState();
				column(logs, base, 4 + random.nextInt(2));
				crown(leaves, base.above(logs.size() - 1), 2.3, 0.45, Blocks.ACACIA_LEAVES.defaultBlockState(), null, random);
			}
			case RAINBOW -> {
				// White birch under a crown of azalea in flower.
				log = Blocks.BIRCH_LOG.defaultBlockState();
				column(logs, base, 4 + random.nextInt(2));
				crown(leaves, base.above(logs.size() - 1), 2.5, 0.75, Blocks.AZALEA_LEAVES.defaultBlockState(),
					Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), random);
			}
			case HIVE -> {
				if (kind < 6) {
					column(logs, base, 4 + random.nextInt(2));
					crown(leaves, base.above(logs.size() - 1), 2.5, 0.75, Blocks.OAK_LEAVES.defaultBlockState(), null, random);
				} else {
					log = Blocks.BIRCH_LOG.defaultBlockState();
					column(logs, base, 5 + random.nextInt(2));
					crown(leaves, base.above(logs.size() - 1), 1.9, 1.1, Blocks.BIRCH_LEAVES.defaultBlockState(), null, random);
				}
			}
			default -> {
				if (kind < 6) {
					column(logs, base, 4 + random.nextInt(3));
					crown(leaves, base.above(logs.size() - 1), 2.6, 0.75, Blocks.OAK_LEAVES.defaultBlockState(), null, random);
				} else if (kind < 8) {
					log = Blocks.BIRCH_LOG.defaultBlockState();
					column(logs, base, 5 + random.nextInt(3));
					crown(leaves, base.above(logs.size() - 1), 1.9, 1.1, Blocks.BIRCH_LEAVES.defaultBlockState(), null, random);
				} else {
					azalea(base, random, logs, leaves, 2.8);
					island.set(base.below(), Blocks.ROOTED_DIRT.defaultBlockState());
				}
			}
		}
		growTree(island, logs, log, leaves);
		if (island.theme == Theme.FROST) {
			// Snow settles on the upper boughs.
			leaves.keySet().forEach(pos -> {
				if (island.get(pos).is(Blocks.SPRUCE_LEAVES) && island.isAir(pos.above()) && random.nextInt(3) > 0) {
					island.set(pos.above(), Blocks.SNOW.defaultBlockState());
				}
			});
		} else if (island.theme == Theme.HIVE && random.nextInt(10) < 7 && logs.size() > 2) {
			// Most of a hive island's trees have a bee nest hanging off the trunk.
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos nest = logs.get(2).relative(side);
			if (island.isAir(nest)) {
				beeNest(island, nest, side, random.nextBoolean(), random);
			}
		} else if (island.theme == Theme.GLOOM) {
			// Pale moss trails from the lowest leaves.
			leaves.keySet().forEach(pos -> {
				if (island.get(pos).is(Blocks.PALE_OAK_LEAVES) && island.isAir(pos.below()) && island.isAir(pos.below(2)) && random.nextInt(3) == 0) {
					int length = 1 + random.nextInt(2);
					for (int i = 1; i <= length; i++) {
						island.set(pos.below(i), Blocks.PALE_HANGING_MOSS.defaultBlockState().setValue(HangingMossBlock.TIP, i == length));
					}
				}
			});
		}
	}

	/** A palm: a slim trunk that bends away from the wind, under a crown of fronds that droop at the tips. */
	private static void palm(BlockPos base, RandomSource random, List<BlockPos> logs, Map<BlockPos, BlockState> leaves) {
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int height = 5 + random.nextInt(3);
		BlockPos pos = base;
		for (int y = 0; y < height; y++) {
			logs.add(pos);
			pos = y == 2 || y == height - 3 ? pos.above().relative(lean) : pos.above();
		}
		BlockPos top = logs.get(logs.size() - 1);
		BlockState leaf = Blocks.JUNGLE_LEAVES.defaultBlockState();
		leaves.put(top.above(), leaf);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					leaves.put(top.offset(dx, 0, dz), leaf);
				}
			}
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			leaves.put(top.relative(side, 2), leaf);
			leaves.put(top.relative(side, 3), leaf);
			leaves.put(top.relative(side, 3).below(), leaf);
		}
	}

	private static void column(List<BlockPos> logs, BlockPos base, int height) {
		for (int y = 0; y < height; y++) {
			logs.add(base.above(y));
		}
	}

	/** A short trunk that leans once, under a wide flowering crown. */
	private static void azalea(BlockPos base, RandomSource random, List<BlockPos> logs, Map<BlockPos, BlockState> leaves, double size) {
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos pos = base;
		for (int y = 0; y < 4; y++) {
			logs.add(pos);
			pos = y == 1 ? pos.above().relative(lean) : pos.above();
		}
		crown(leaves, pos, size, 0.55, Blocks.AZALEA_LEAVES.defaultBlockState(), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), random);
	}

	/** A spruce: tiers of boughs narrowing to a point. */
	private static void spruce(BlockPos base, RandomSource random, List<BlockPos> logs, Map<BlockPos, BlockState> leaves) {
		int height = 7 + random.nextInt(3);
		column(logs, base, height);
		BlockState leaf = Blocks.SPRUCE_LEAVES.defaultBlockState();
		for (int y = 2; y <= height; y++) {
			int fromTop = height - y;
			int r = fromTop == 0 ? 0 : fromTop % 2 == 1 ? 1 : Math.min(2, 1 + fromTop / 3);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) + Math.abs(dz) <= r + (r == 2 ? 1 : 0) && (dx != 0 || dz != 0 || y >= height)) {
						leaves.put(base.offset(dx, y, dz), leaf);
					}
				}
			}
		}
		leaves.put(base.above(height + 1), leaf);
	}

	/** A dead tree burnt black by the island's heat: a bare trunk and a branch or two, no leaves. */
	private static void charredTree(Island island, BlockPos base, RandomSource random) {
		int height = 3 + random.nextInt(3);
		for (int y = 0; y < height; y++) {
			island.set(base.above(y), Blocks.DARK_OAK_LOG.defaultBlockState());
		}
		int branches = 1 + random.nextInt(2);
		for (int i = 0; i < branches; i++) {
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos start = base.above(height - 1 - random.nextInt(2));
			int length = 1 + random.nextInt(2);
			for (int j = 1; j <= length; j++) {
				island.set(start.relative(side, j), Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, side.getAxis()));
			}
			if (random.nextBoolean()) {
				island.set(start.relative(side, length).above(), Blocks.DARK_OAK_LOG.defaultBlockState());
			}
		}
	}

	/** A squashed ball of leaves with a ragged edge; some flowering, if the tree flowers. */
	private static void crown(Map<BlockPos, BlockState> leaves, BlockPos middle, double radius, double squash, BlockState leaf, BlockState flowering, RandomSource random) {
		int r = Mth.ceil(radius);
		double height = radius * squash;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = (dx * dx + dz * dz) / (radius * radius) + dy * dy / (height * height);
					if (d <= 1 - random.nextDouble() * 0.3) {
						leaves.put(middle.offset(dx, dy, dz), flowering != null && random.nextInt(3) == 0 ? flowering : leaf);
					}
				}
			}
		}
	}

	/** Places a tree's logs, then each leaf with its true distance from the wood, so it stays put until the tree is cut. */
	private static void growTree(Island island, List<BlockPos> logs, BlockState log, Map<BlockPos, BlockState> leaves) {
		logs.forEach(pos -> island.set(pos, log));
		Map<BlockPos, Integer> distance = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>(logs);
		logs.forEach(pos -> distance.put(pos, 0));
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int next = distance.get(pos) + 1;
			if (next >= LeavesBlock.DECAY_DISTANCE) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos side = pos.relative(direction);
				if (leaves.containsKey(side) && !distance.containsKey(side)) {
					distance.put(side, next);
					queue.add(side);
				}
			}
		}
		leaves.forEach((pos, leaf) -> {
			Integer d = distance.get(pos);
			if (d != null && d > 0 && island.isAir(pos)) {
				island.set(pos, leaf.setValue(LeavesBlock.DISTANCE, d));
			}
		});
	}

	/** Whatever grows on each theme's open ground. */
	private static void plants(Island island, RandomSource random) {
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY};
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				if (!island.isOpenGround(dx, dz)) {
					continue;
				}
				BlockPos ground = island.ground(dx, dz);
				BlockPos above = ground.above();
				BlockState soil = island.get(ground);
				float roll = random.nextFloat();
				switch (island.theme) {
					case MEADOW -> {
						if (roll < 0.3F) {
							island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
						} else if (roll < 0.34F) {
							island.set(above, Blocks.FERN.defaultBlockState());
						} else if (roll < 0.38F && island.isAir(above.above())) {
							DoublePlantBlock.placeAt(island.level, Blocks.TALL_GRASS.defaultBlockState(), above, Block.UPDATE_CLIENTS);
						} else if (roll < 0.45F) {
							island.set(above, flowers[random.nextInt(flowers.length)].defaultBlockState());
						} else if (roll < 0.49F) {
							island.set(above, petals(Blocks.WILDFLOWERS, random));
						} else if (roll < 0.5F) {
							island.set(above, Blocks.FIREFLY_BUSH.defaultBlockState());
						}
					}
					case VERDANT -> {
						if (roll < 0.25F) {
							island.set(above, Blocks.MOSS_CARPET.defaultBlockState());
						} else if (roll < 0.37F) {
							island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
						} else if (roll < 0.41F) {
							island.set(above, Blocks.FERN.defaultBlockState());
						} else if (roll < 0.45F) {
							island.set(above, Blocks.AZALEA.defaultBlockState());
						} else if (roll < 0.49F) {
							island.set(above, Blocks.FLOWERING_AZALEA.defaultBlockState());
						} else if (roll < 0.56F) {
							island.set(above, petals(Blocks.PINK_PETALS, random));
						} else if (roll < 0.58F && island.isAir(above.above())) {
							DoublePlantBlock.placeAt(island.level, Blocks.TALL_GRASS.defaultBlockState(), above, Block.UPDATE_CLIENTS);
						} else if (roll < 0.59F) {
							island.set(above, Blocks.FIREFLY_BUSH.defaultBlockState());
						}
					}
					case FROST -> {
						if (soil.is(Blocks.SNOW_BLOCK) && roll < 0.7F) {
							island.set(above, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2)));
						}
					}
					case STORM -> {
						if (soil.is(Blocks.GRASS_BLOCK)) {
							if (roll < 0.3F) {
								island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
							} else if (roll < 0.34F && island.isAir(above.above())) {
								DoublePlantBlock.placeAt(island.level, Blocks.TALL_GRASS.defaultBlockState(), above, Block.UPDATE_CLIENTS);
							}
						}
					}
					case LAGOON -> {
						if (soil.is(Blocks.SAND)) {
							if (roll < 0.06F) {
								island.set(above, Blocks.SHORT_DRY_GRASS.defaultBlockState());
							} else if (roll < 0.08F) {
								island.set(above, Blocks.DEAD_BUSH.defaultBlockState());
							}
						} else if (roll < 0.3F) {
							island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
						} else if (roll < 0.36F) {
							island.set(above, Blocks.FERN.defaultBlockState());
						} else if (roll < 0.4F) {
							island.set(above, Blocks.BUSH.defaultBlockState());
						} else if (roll < 0.43F) {
							island.set(above, Blocks.BLUE_ORCHID.defaultBlockState());
						}
					}
					case CRYSTAL -> {
						if (soil.is(Blocks.CALCITE)) {
							// Amethyst buds sprout from the bare calcite.
							Block bud = roll < 0.06F ? Blocks.SMALL_AMETHYST_BUD : roll < 0.09F ? Blocks.MEDIUM_AMETHYST_BUD
								: roll < 0.1F ? Blocks.LARGE_AMETHYST_BUD : null;
							if (bud != null) {
								island.set(above, bud.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
							}
						} else if (soil.is(Blocks.GRASS_BLOCK)) {
							if (roll < 0.25F) {
								island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
							} else if (roll < 0.31F) {
								island.set(above, Blocks.ALLIUM.defaultBlockState());
							} else if (roll < 0.38F) {
								island.set(above, petals(Blocks.PINK_PETALS, random));
							} else if (roll < 0.41F && island.isAir(above.above())) {
								DoublePlantBlock.placeAt(island.level, Blocks.LILAC.defaultBlockState(), above, Block.UPDATE_CLIENTS);
							}
						}
					}
					case GLOOM -> {
						if (soil.is(Blocks.PALE_MOSS_BLOCK)) {
							if (roll < 0.35F) {
								island.set(above, Blocks.PALE_MOSS_CARPET.defaultBlockState());
							} else if (roll < 0.42F) {
								island.set(above, Blocks.CLOSED_EYEBLOSSOM.defaultBlockState()); // they open at night
							}
						} else if (!soil.is(Blocks.SCULK)) {
							if (roll < 0.05F) {
								island.set(above, Blocks.DEAD_BUSH.defaultBlockState());
							} else if (roll < 0.12F) {
								island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
							} else if (roll < 0.128F) {
								island.set(above, Blocks.WITHER_ROSE.defaultBlockState());
							}
						}
					}
					case CANYON -> {
						if (soil.is(Blocks.RED_SAND)) {
							if (roll < 0.025F) {
								cactus(island, above, random);
							} else if (roll < 0.08F) {
								island.set(above, Blocks.DEAD_BUSH.defaultBlockState());
							} else if (roll < 0.16F) {
								island.set(above, Blocks.SHORT_DRY_GRASS.defaultBlockState());
							} else if (roll < 0.19F) {
								island.set(above, Blocks.TALL_DRY_GRASS.defaultBlockState());
							}
						} else if (soil.is(Blocks.COARSE_DIRT) && roll < 0.12F) {
							island.set(above, Blocks.SHORT_DRY_GRASS.defaultBlockState());
						}
					}
					case RAINBOW -> {
						if (roll < 0.35F) {
							island.set(above, HIVE_FLOWERS[random.nextInt(HIVE_FLOWERS.length)].defaultBlockState());
						} else if (roll < 0.42F) {
							island.set(above, petals(Blocks.PINK_PETALS, random));
						} else if (roll < 0.48F) {
							island.set(above, petals(Blocks.WILDFLOWERS, random));
						} else if (roll < 0.58F) {
							island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
						}
					}
					case HIVE -> {
						// A meadow thick with every kind of flower: the bees' larder.
						if (roll < 0.3F) {
							island.set(above, HIVE_FLOWERS[random.nextInt(HIVE_FLOWERS.length)].defaultBlockState());
						} else if (roll < 0.4F) {
							island.set(above, petals(Blocks.WILDFLOWERS, random));
						} else if (roll < 0.52F) {
							island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
						} else if (roll < 0.57F && island.isAir(above.above())) {
							Block tall = switch (random.nextInt(4)) {
								case 0 -> Blocks.SUNFLOWER;
								case 1 -> Blocks.LILAC;
								case 2 -> Blocks.ROSE_BUSH;
								default -> Blocks.PEONY;
							};
							DoublePlantBlock.placeAt(island.level, tall.defaultBlockState(), above, Block.UPDATE_CLIENTS);
						}
					}
					default -> {
					}
				}
			}
		}
	}

	private static final Block[] HIVE_FLOWERS = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
		Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP, Blocks.RED_TULIP, Blocks.WHITE_TULIP, Blocks.BLUE_ORCHID};

	/** A cactus a block or three tall, sometimes in flower, if nothing's crowding it. */
	private static void cactus(Island island, BlockPos base, RandomSource random) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!island.isAir(base.relative(side))) {
				return;
			}
		}
		int height = 1 + random.nextInt(3);
		for (int y = 0; y < height; y++) {
			island.set(base.above(y), Blocks.CACTUS.defaultBlockState());
		}
		if (random.nextInt(3) == 0 && island.isAir(base.above(height))) {
			island.set(base.above(height), Blocks.CACTUS_FLOWER.defaultBlockState());
		}
	}

	private static BlockState petals(Block block, RandomSource random) {
		return block.defaultBlockState()
			.setValue(FlowerBedBlock.AMOUNT, 1 + random.nextInt(4))
			.setValue(FlowerBedBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
	}

	/**
	 * A little pool in the meadow, two deep in the middle: water with lily pads and reeds, a frozen pond on a
	 * Frost island, or a pool of lava on an Ember one.
	 */
	private static void pond(Island island, RandomSource random) {
		Theme theme = island.theme;
		for (int attempt = 0; attempt < 10; attempt++) {
			int cx = random.nextInt(7) - 3;
			int cz = random.nextInt(7) - 3;
			if (island.inner(cx, cz) < 0.5F) {
				continue;
			}
			int r = 2 + random.nextInt(2);
			int level = island.surface(cx, cz);
			List<int[]> cells = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz <= r * r + random.nextInt(3) - 1) {
						cells.add(new int[] {cx + dx, cz + dz, dx * dx + dz * dz < (r - 0.5) * (r - 0.5) ? 2 : 1});
					}
				}
			}
			// The ring around it has to be solid island at least as high as the surface, or it'd spill.
			boolean fits = true;
			for (int dx = -r - 1; dx <= r + 1 && fits; dx++) {
				for (int dz = -r - 1; dz <= r + 1 && fits; dz++) {
					fits = island.inner(cx + dx, cz + dz) >= 0.2F && (island.surface(cx + dx, cz + dz) >= level || isCell(cells, cx + dx, cz + dz));
				}
			}
			if (!fits) {
				continue;
			}
			BlockState liquid = (theme == Theme.EMBER ? Blocks.LAVA : Blocks.WATER).defaultBlockState();
			for (int[] cell : cells) {
				int x = cell[0];
				int z = cell[1];
				int floor = level - cell[2];
				for (int y = Math.min(island.surface(x, z), floor - 1) + 1; y < floor; y++) {
					island.set(island.column(x, y, z), theme.soil(random));
				}
				BlockState bed = theme == Theme.EMBER ? Blocks.BASALT.defaultBlockState()
					: (random.nextInt(3) == 0 ? Blocks.CLAY : theme == Theme.FROST ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState();
				island.set(island.column(x, floor, z), bed);
				for (int y = floor + 1; y <= level; y++) {
					boolean frozen = theme == Theme.FROST && y == level;
					island.set(island.column(x, y, z), frozen ? Blocks.ICE.defaultBlockState() : liquid);
				}
				for (int y = level + 1; y <= island.surface(x, z); y++) {
					island.set(island.column(x, y, z), Blocks.AIR.defaultBlockState());
				}
				island.setSurface(x, z, level);
				if (theme != Theme.EMBER && theme != Theme.FROST && random.nextInt(theme == Theme.VERDANT ? 3 : 6) == 0) {
					island.set(island.column(x, level + 1, z), Blocks.LILY_PAD.defaultBlockState());
				}
				// A lagoon's deep water is full of seagrass, and sea pickles that glow after dark.
				if (theme == Theme.LAGOON && cell[2] == 2) {
					float roll = random.nextFloat();
					if (roll < 0.45F) {
						island.set(island.column(x, floor + 1, z), Blocks.SEAGRASS.defaultBlockState());
					} else if (roll < 0.6F) {
						island.set(island.column(x, floor + 1, z), Blocks.SEA_PICKLE.defaultBlockState()
							.setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(SeaPickleBlock.MAX_PICKLES)).setValue(SeaPickleBlock.WATERLOGGED, true));
					}
				}
			}
			if (theme == Theme.EMBER || theme == Theme.FROST) {
				return;
			}
			for (int[] cell : cells) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int x = cell[0] + side.getStepX();
					int z = cell[1] + side.getStepZ();
					BlockState bank = island.get(island.ground(x, z));
					if (!isCell(cells, x, z) && island.surface(x, z) == level && random.nextInt(4) == 0
							&& (bank.is(Blocks.GRASS_BLOCK) || bank.is(Blocks.SAND)) && island.isAir(island.ground(x, z).above())) {
						int height = 1 + random.nextInt(3);
						for (int y = 1; y <= height; y++) {
							island.set(island.ground(x, z).above(y), Blocks.SUGAR_CANE.defaultBlockState());
						}
					}
				}
			}
			return;
		}
	}

	private static boolean isCell(List<int[]> cells, int x, int z) {
		for (int[] cell : cells) {
			if (cell[0] == x && cell[1] == z) {
				return true;
			}
		}
		return false;
	}

	/** A spring at the island's edge that spills over and falls all the way to the ground: a sign in the sky. */
	private static void waterfall(Island island, RandomSource random) {
		List<int[]> rim = new ArrayList<>();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				if (island.isRim(dx, dz) && island.theme.isGround(island.get(island.ground(dx, dz)))) {
					rim.add(new int[] {dx, dz});
				}
			}
		}
		for (int attempt = 0; attempt < 20 && !rim.isEmpty(); attempt++) {
			int[] spot = rim.get(random.nextInt(rim.size()));
			int y = island.surface(spot[0], spot[1]);
			// It has to spill outward, not across the meadow: every island neighbour stands at least as high.
			boolean outward = true;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int x = spot[0] + side.getStepX();
				int z = spot[1] + side.getStepZ();
				if (island.has(x, z) && island.surface(x, z) < y) {
					outward = false;
				}
			}
			BlockPos spring = island.ground(spot[0], spot[1]);
			if (outward && island.isAir(spring.above())) {
				island.set(spring, Blocks.WATER.defaultBlockState());
				island.level.scheduleTick(spring, Fluids.WATER, 0);
				return;
			}
		}
	}

	/** A few small rocks drifting near the island. */
	private static void islets(Island island, RandomSource random) {
		int count = random.nextInt(4);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = island.radius + 4 + random.nextInt(4);
			int cx = (int) Math.round(Math.cos(angle) * distance);
			int cz = (int) Math.round(Math.sin(angle) * distance);
			if (Math.abs(cx) > REACH - 3 || Math.abs(cz) > REACH - 3 || island.has(cx, cz) || island.inner(cx + 2, cz) > 0
					|| island.inner(cx - 2, cz) > 0 || island.inner(cx, cz + 2) > 0 || island.inner(cx, cz - 2) > 0) {
				continue; // keep clear of the island itself
			}
			BlockPos middle = island.column(cx, island.center.getY() - 4 + random.nextInt(9), cz);
			int size = 1 + random.nextInt(2);
			for (int dx = -size; dx <= size; dx++) {
				for (int dz = -size; dz <= size; dz++) {
					if (dx * dx + dz * dz > size * size + 1) {
						continue;
					}
					int depth = 1 + size - Math.max(Math.abs(dx), Math.abs(dz)) + random.nextInt(2);
					for (int dy = 0; dy <= depth; dy++) {
						BlockPos pos = middle.offset(dx, -dy, dz);
						if (island.isAir(pos)) {
							island.set(pos, island.theme == Theme.CLOUD ? Blocks.WOOL.white().defaultBlockState() // little clouds
								: dy == 0 ? island.theme.top(random) : dy == 1 ? island.theme.soil(random) : island.theme.rock(random, 0.5F, pos.getY()));
						}
					}
				}
			}
		}
	}

	// ---- Wild islands ---------------------------------------------------------------------------------------

	/** A ruined shrine of weathered stone, its chest still waiting in the middle. */
	private static void shrine(Island island, RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			int cx = random.nextInt(7) - 3;
			int cz = random.nextInt(7) - 3;
			if (!island.has(cx, cz)) {
				continue;
			}
			int y = island.surface(cx, cz);
			boolean flat = true;
			for (int dx = -2; dx <= 2 && flat; dx++) {
				for (int dz = -2; dz <= 2 && flat; dz++) {
					flat = island.inner(cx + dx, cz + dz) >= 0.3F && Math.abs(island.surface(cx + dx, cz + dz) - y) <= 1
						&& island.theme.isGround(island.get(island.ground(cx + dx, cz + dz)));
				}
			}
			if (!flat) {
				continue;
			}
			boolean mossy = island.theme == Theme.MEADOW || island.theme == Theme.VERDANT;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos ground = island.ground(cx + dx, cz + dz);
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
						int height = 1 + random.nextInt(3);
						for (int h = 1; h <= height; h++) {
							island.set(ground.above(h), island.theme.ruinStone(random));
						}
						if (random.nextBoolean()) {
							island.set(ground.above(height + 1), Blocks.STONE_BRICK_SLAB.defaultBlockState());
						}
					} else if (random.nextInt(5) > 0) {
						island.set(ground, mossy && random.nextInt(4) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : island.theme.ruinStone(random));
					}
				}
			}
			chest(island, island.ground(cx, cz).above(), ISLAND_LOOT, random);
			return;
		}
		if (island.isAir(island.ground(0, 0).above())) {
			chest(island, island.ground(0, 0).above(), ISLAND_LOOT, random);
		}
	}

	private static void chest(Island island, BlockPos pos, ResourceKey<LootTable> loot, RandomSource random) {
		island.set(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		RandomizableContainer.setBlockEntityLootTable(island.level, random, pos, loot);
	}

	// ---- Dragon roosts --------------------------------------------------------------------------------------

	/** A dragon's nest round a hoard of treasure, built from what's to hand on its island, and the dragon guarding it. */
	private static void roost(Island island, DragonElement element, RandomSource random) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 3.6 || !island.has(dx, dz)) {
					continue;
				}
				BlockPos ground = island.ground(dx, dz);
				if (d > 2.4) {
					island.set(ground.above(), nestRing(island.theme, random));
					if (d > 2.9 && random.nextInt(3) > 0) {
						island.set(ground.above(2), nestRing(island.theme, random));
					}
				} else {
					island.set(ground, nestFloor(island.theme, random));
				}
			}
		}
		chest(island, island.ground(0, 0).above(), HOARD_LOOT, random);
		DragonEntity guard = Dragons.TYPES.get(element).create(island.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (guard != null) {
			BlockPos at = island.ground(1, 1).above();
			guard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360F, 0F);
			guard.setIslandHome(island.ground(0, 0).above(), false);
			island.level.addFreshEntity(guard);
		}
	}

	private static BlockState nestRing(Theme theme, RandomSource random) {
		float r = random.nextFloat();
		return switch (theme) {
			case EMBER -> r < 0.4F ? pillar(Blocks.BONE_BLOCK, random) : r < 0.7F ? Blocks.BLACKSTONE.defaultBlockState() : pillar(Blocks.HAY_BLOCK, random);
			case FROST -> r < 0.6F ? pillar(Blocks.HAY_BLOCK, random) : r < 0.85F ? Blocks.SNOW_BLOCK.defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			case VERDANT -> r < 0.5F ? pillar(Blocks.HAY_BLOCK, random) : r < 0.85F ? Blocks.MOSS_BLOCK.defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			// Driftwood and dried kelp.
			case LAGOON -> r < 0.45F ? pillar(Blocks.STRIPPED_OAK_LOG, random) : r < 0.85F ? Blocks.DRIED_KELP_BLOCK.defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			case CRYSTAL -> r < 0.4F ? Blocks.AMETHYST_BLOCK.defaultBlockState() : r < 0.8F ? Blocks.CALCITE.defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			case GLOOM -> r < 0.5F ? pillar(Blocks.BONE_BLOCK, random) : r < 0.8F ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.PALE_MOSS_BLOCK.defaultBlockState();
			case CANYON -> r < 0.4F ? Blocks.TERRACOTTA.defaultBlockState() : r < 0.75F ? Blocks.DYED_TERRACOTTA.orange().defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			case HIVE -> r < 0.5F ? pillar(Blocks.HAY_BLOCK, random) : r < 0.85F ? Blocks.HONEYCOMB_BLOCK.defaultBlockState() : pillar(Blocks.BONE_BLOCK, random);
			case RAINBOW -> RAINBOW_BANDS[random.nextInt(RAINBOW_BANDS.length)].defaultBlockState();
			case CLOUD -> r < 0.7F ? Blocks.WOOL.white().defaultBlockState() : pillar(Blocks.HAY_BLOCK, random);
			default -> r < 0.85F ? pillar(Blocks.HAY_BLOCK, random) : pillar(Blocks.BONE_BLOCK, random);
		};
	}

	private static BlockState nestFloor(Theme theme, RandomSource random) {
		boolean first = random.nextBoolean();
		return (switch (theme) {
			case EMBER -> first ? Blocks.BLACKSTONE : Blocks.COARSE_DIRT;
			case FROST -> first ? Blocks.SNOW_BLOCK : Blocks.COARSE_DIRT;
			case VERDANT -> first ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT;
			case LAGOON -> first ? Blocks.SAND : Blocks.CLAY;
			case CRYSTAL -> first ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK;
			case GLOOM -> first ? Blocks.SCULK : Blocks.PODZOL;
			case CANYON -> first ? Blocks.RED_SAND : Blocks.COARSE_DIRT;
			case HIVE -> first ? Blocks.HONEYCOMB_BLOCK : Blocks.ROOTED_DIRT;
			case RAINBOW -> first ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
			case CLOUD -> Blocks.WOOL.white();
			default -> first ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT;
		}).defaultBlockState();
	}

	private static BlockState pillar(Block block, RandomSource random) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.getRandom(random));
	}

	// ---- Sky villages ---------------------------------------------------------------------------------------

	/** Half the width of a sky village's fenced green, and how far out from the well its four houses stand. */
	private static final int VILLAGE_HALF = 10, HOUSE_OFFSET = 6;
	/** Job sites: each house gets a different one, so its villager takes up that trade. */
	private static final Block[] JOB_SITES = {Blocks.LECTERN, Blocks.COMPOSTER, Blocks.BARREL, Blocks.SMOKER, Blocks.BLAST_FURNACE,
		Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.BREWING_STAND,
		Blocks.CAULDRON};
	private static final Block[] VILLAGE_CROPS = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};

	/**
	 * A sky village: a fenced green round a well and a bell, four cottages (each with a bed, a job site and a
	 * villager), a field of crops, an iron golem keeping watch, a cat, and sometimes an airship the villagers lend to
	 * travellers. The fence and its gates keep everyone from wandering off the edge.
	 */
	private static void village(Island island, RandomSource random) {
		int floor = island.surface(0, 0);
		int half = VILLAGE_HALF;
		// Level the green, with foundations wherever the island falls away under it.
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int top = island.has(dx, dz) ? island.surface(dx, dz) : floor - 4;
				for (int y = top + 1; y < floor; y++) {
					island.set(island.column(dx, y, dz), y > floor - 3 ? Blocks.DIRT.defaultBlockState() : island.theme.rock(random, 0.5F, y));
				}
				for (int y = floor + 1; y <= top; y++) {
					island.set(island.column(dx, y, dz), Blocks.AIR.defaultBlockState());
				}
				island.set(island.column(dx, floor, dz), Blocks.GRASS_BLOCK.defaultBlockState());
				if (island.has(dx, dz)) {
					island.setSurface(dx, dz, floor);
				}
			}
		}
		// The well: water in a ring of cobblestone under a little roof on posts, a lantern hanging over it.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = island.column(dx, floor, dz);
				boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
				if (dx == 0 && dz == 0) {
					island.set(pos.below(), Blocks.COBBLESTONE.defaultBlockState());
					island.set(pos, Blocks.WATER.defaultBlockState());
				} else {
					island.set(pos, Blocks.COBBLESTONE.defaultBlockState());
					island.set(pos.above(), (corner ? Blocks.COBBLESTONE : Blocks.COBBLESTONE_SLAB).defaultBlockState());
				}
				if (corner) {
					island.set(pos.above(2), Blocks.OAK_FENCE.defaultBlockState());
					island.set(pos.above(3), Blocks.OAK_FENCE.defaultBlockState());
				}
				island.set(pos.above(4), Blocks.COBBLESTONE_SLAB.defaultBlockState());
			}
		}
		island.set(island.column(0, floor + 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		island.set(island.column(2, floor + 1, 2), Blocks.BELL.defaultBlockState()
			.setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		// Four cottages round the well, each with its door facing it and a path up to the door.
		List<Block> jobs = new ArrayList<>(Arrays.asList(JOB_SITES));
		int storehouse = random.nextInt(4);
		int house = 0;
		for (Direction front : Direction.Plane.HORIZONTAL) {
			BlockPos middle = island.column(-front.getStepX() * HOUSE_OFFSET, floor, -front.getStepZ() * HOUSE_OFFSET);
			cottage(island, middle, front, jobs.remove(random.nextInt(jobs.size())), house++ == storehouse, random);
			for (int step = 2; step <= HOUSE_OFFSET - 3; step++) {
				island.set(island.column(-front.getStepX() * step, floor, -front.getStepZ() * step), Blocks.DIRT_PATH.defaultBlockState());
			}
		}
		// A field of crops round a pool of water, edged with logs.
		Block crop = VILLAGE_CROPS[random.nextInt(VILLAGE_CROPS.length)];
		for (int dx = 3; dx <= 9; dx++) {
			for (int dz = 3; dz <= 9; dz++) {
				BlockPos pos = island.column(dx, floor, dz);
				if (dx == 3 || dx == 9 || dz == 3 || dz == 9) {
					island.set(pos, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, dx == 3 || dx == 9 ? Direction.Axis.Z : Direction.Axis.X));
				} else if (dx == 6 && dz == 6) {
					island.set(pos, Blocks.WATER.defaultBlockState());
				} else {
					island.set(pos, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					island.set(pos.above(), crop.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(CropBlock.MAX_AGE + 1)));
				}
			}
		}
		// Lamp posts, so nothing nasty spawns on the green at night.
		for (int[] lamp : new int[][] {{-3, -3}, {3, -3}, {-3, 3}}) {
			BlockPos post = island.column(lamp[0], floor + 1, lamp[1]);
			island.set(post, Blocks.OAK_FENCE.defaultBlockState());
			island.set(post.above(), Blocks.OAK_FENCE.defaultBlockState());
			island.set(post.above(2), Blocks.LANTERN.defaultBlockState());
		}
		// The fence round the green, with two gates and lanterns along it.
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				if (!onVillageFence(dx, dz)) {
					continue;
				}
				BlockPos pos = island.column(dx, floor + 1, dz);
				if (dz == -half && dx == 3 || dz == half && dx == -3) {
					island.set(pos, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, dz < 0 ? Direction.NORTH : Direction.SOUTH));
					continue;
				}
				island.set(pos, Blocks.OAK_FENCE.defaultBlockState()
					.setValue(FenceBlock.NORTH, onVillageFence(dx, dz - 1)).setValue(FenceBlock.SOUTH, onVillageFence(dx, dz + 1))
					.setValue(FenceBlock.WEST, onVillageFence(dx - 1, dz)).setValue(FenceBlock.EAST, onVillageFence(dx + 1, dz)));
				if (Math.floorMod(dx + dz, 5) == 0) {
					island.set(pos.above(), Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		// Its guardian, its cat, and sometimes an airship moored on the green for travellers to borrow.
		spawnVillager(island, EntityTypes.IRON_GOLEM, island.column(6, floor + 1, -6), random);
		spawnVillager(island, EntityTypes.CAT, island.column(-6, floor + 1, -6), random);
		if (random.nextBoolean()) {
			AirshipEntity.spawnVillageShip(island.level, island.column(-6, floor + 1, 6));
		}
	}

	private static boolean onVillageFence(int dx, int dz) {
		return Math.abs(dx) == VILLAGE_HALF && Math.abs(dz) <= VILLAGE_HALF || Math.abs(dz) == VILLAGE_HALF && Math.abs(dx) <= VILLAGE_HALF;
	}

	/**
	 * A cottage five blocks across: an oak frame filled in with planks, windows either side and at the back, a roof of
	 * spruce, a door facing the well, and inside, a bed, a job site, a lantern and the villager who lives there.
	 */
	private static void cottage(Island island, BlockPos middle, Direction front, Block job, boolean store, RandomSource random) {
		Direction right = front.getClockWise();
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				BlockPos base = middle.relative(right, a).relative(front, b);
				island.set(base, Blocks.OAK_PLANKS.defaultBlockState());
				boolean wall = Math.abs(a) == 2 || Math.abs(b) == 2;
				boolean corner = Math.abs(a) == 2 && Math.abs(b) == 2;
				boolean window = Math.abs(a) == 2 && b == 0 || b == -2 && a == 0;
				for (int y = 1; y <= 3; y++) {
					island.set(base.above(y), (!wall ? Blocks.AIR : corner ? Blocks.OAK_LOG : window && y == 2 ? Blocks.GLASS : Blocks.OAK_PLANKS)
						.defaultBlockState());
				}
				island.set(base.above(4), Blocks.SPRUCE_PLANKS.defaultBlockState());
				if (Math.abs(a) <= 1 && Math.abs(b) <= 1) {
					island.set(base.above(5), Blocks.SPRUCE_SLAB.defaultBlockState());
				}
			}
		}
		BlockPos door = middle.relative(front, 2);
		island.set(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, front.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		island.set(door.above(2), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, front.getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		// Inside: the bed along one wall, head to the back; the job site in the far corner; a lantern overhead.
		Block bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.values()[random.nextInt(16)]);
		BlockState bedState = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, front.getOpposite());
		island.set(middle.relative(right, -1).above(), bedState.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		island.set(middle.relative(right, -1).relative(front, -1).above(), bedState.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		island.set(middle.relative(right, 1).relative(front, -1).above(), job.defaultBlockState());
		island.set(middle.above(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		if (store) {
			chest(island, middle.relative(right, 1).above(), ISLAND_LOOT, random);
		}
		spawnVillager(island, EntityTypes.VILLAGER, middle.above(), random);
	}

	/** A villager (or a golem or cat) who lives in a sky village, and stays there. */
	private static void spawnVillager(Island island, EntityType<? extends Mob> type, BlockPos at, RandomSource random) {
		Mob mob = type.create(island.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360F, 0F);
		mob.finalizeSpawn(island.level, island.level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		mob.setPersistenceRequired();
		island.level.addFreshEntity(mob);
	}

	// ---- Sky citadels ---------------------------------------------------------------------------------------

	/** Half the width of a citadel's curtain wall, and how high its walls and towers stand above the courtyard. */
	private static final int CITADEL_HALF = 7, WALL_HEIGHT = 4, TOWER_HEIGHT = 8;

	/**
	 * A ruined castle in the clouds, about one island in twenty: a crumbling curtain wall with a gate and
	 * battlements, a tower at each corner with a lantern on top, and a paved courtyard open to the sky, where the
	 * Elder Dragon guards its hoard.
	 */
	private static void citadel(Island island, RandomSource random) {
		int floor = island.surface(0, 0);
		int half = CITADEL_HALF;
		// Level the ground into a paved courtyard, with foundations wherever the island falls away under the walls.
		for (int dx = -half - 1; dx <= half + 1; dx++) {
			for (int dz = -half - 1; dz <= half + 1; dz++) {
				int top = island.has(dx, dz) ? island.surface(dx, dz) : floor - 4;
				for (int y = top + 1; y < floor; y++) {
					island.set(island.column(dx, y, dz), citadelStone(island.theme, random));
				}
				for (int y = floor + 1; y <= top; y++) {
					island.set(island.column(dx, y, dz), Blocks.AIR.defaultBlockState());
				}
				island.set(island.column(dx, floor, dz), paving(island.theme, random));
				if (island.has(dx, dz)) {
					island.setSurface(dx, dz, floor);
				}
			}
		}
		// The curtain wall, crumbling here and there, with battlements and a gate.
		Direction gate = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				if (Math.abs(dx) != half && Math.abs(dz) != half) {
					continue;
				}
				boolean gateway = gate.getStepZ() != 0 ? dz == gate.getStepZ() * half && Math.abs(dx) <= 1 : dx == gate.getStepX() * half && Math.abs(dz) <= 1;
				int height = random.nextInt(6) == 0 ? WALL_HEIGHT - 1 - random.nextInt(2) : WALL_HEIGHT;
				for (int y = gateway ? 4 : 1; y <= height; y++) {
					island.set(island.column(dx, floor + y, dz), citadelStone(island.theme, random));
				}
				if (height == WALL_HEIGHT && (dx + dz) % 2 == 0) {
					island.set(island.column(dx, floor + WALL_HEIGHT + 1, dz), citadelStone(island.theme, random));
				}
			}
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				tower(island, island.column(sx * half, floor, sz * half), random);
			}
		}
		// The hoard: two chests on a dais of polished blackstone, gold set into its corners, an old skull between them.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Block dais = Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Blocks.GOLD_BLOCK : Blocks.POLISHED_BLACKSTONE_BRICKS;
				island.set(island.column(dx, floor, dz), dais.defaultBlockState());
			}
		}
		chest(island, island.column(-1, floor + 1, 0), CITADEL_LOOT, random);
		chest(island, island.column(1, floor + 1, 0), CITADEL_LOOT, random);
		island.set(island.column(0, floor + 1, 0), Blocks.SKELETON_SKULL.defaultBlockState());
		if (island.theme == Theme.GLOOM) {
			for (int i = 0; i < 6; i++) {
				BlockPos web = island.column(random.nextBoolean() ? half - 1 : 1 - half, floor + 1 + random.nextInt(3), random.nextInt(2 * half - 1) - half + 1);
				if (island.isAir(web)) {
					island.set(web, Blocks.COBWEB.defaultBlockState());
				}
			}
		}
		ElderDragonEntity elder = Dragons.ELDER.create(island.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (elder != null) {
			BlockPos at = island.column(0, floor + 1, 4);
			elder.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360F, 0F);
			elder.setIslandHome(island.column(0, floor + 1, 0), false);
			island.level.addFreshEntity(elder);
		}
	}

	/** A solid corner tower, three blocks across, with merlons at its corners and a lantern on top. */
	private static void tower(Island island, BlockPos base, RandomSource random) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 1; y <= TOWER_HEIGHT; y++) {
					island.set(base.offset(dx, y, dz), citadelStone(island.theme, random));
				}
				if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
					island.set(base.offset(dx, TOWER_HEIGHT + 1, dz), citadelStone(island.theme, random));
				}
			}
		}
		island.set(base.above(TOWER_HEIGHT + 1), (island.theme == Theme.GLOOM ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState());
	}

	/** The citadel's masonry: stone bricks, or deepslate on a Gloom island, cracked and mossy with age. */
	private static BlockState citadelStone(Theme theme, RandomSource random) {
		int pick = random.nextInt(10);
		if (theme == Theme.GLOOM) {
			return (pick < 2 ? Blocks.CRACKED_DEEPSLATE_BRICKS : pick < 3 ? Blocks.DEEPSLATE_TILES : Blocks.DEEPSLATE_BRICKS).defaultBlockState();
		}
		return (pick < 2 ? Blocks.CRACKED_STONE_BRICKS : pick < 4 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
	}

	private static BlockState paving(Theme theme, RandomSource random) {
		boolean worn = random.nextInt(4) == 0;
		if (theme == Theme.GLOOM) {
			return (worn ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
		}
		return (worn ? Blocks.COBBLESTONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
	}

	// ---- Pillager sky outposts ------------------------------------------------------------------------------

	/** A pillager watchtower with a dock and its airship, a garrison, and pillagers on dragons patrolling the island. */
	private static void outpost(Island island, RandomSource random) {
		Tower tower = new Tower(island, island.ground(0, 0), Rotation.getRandom(random));
		tower.levelYard();
		tower.build(random);
		int dockEnd = tower.dock();

		Garrison garrison = new Garrison(island, random);
		garrison.post(EntityTypes.PILLAGER, tower.at(0, 10, 1)); // on the lookout
		garrison.post(EntityTypes.PILLAGER, tower.at(0, 6, 0));
		garrison.post(EntityTypes.PILLAGER, tower.at(0, 1, 0));
		garrison.post(EntityTypes.PILLAGER, tower.at(-3, 1, -3)); // in the yard
		garrison.post(EntityTypes.VINDICATOR, tower.at(4, 1, 1)); // minding the dock
		AirshipEntity.spawnRaiderShip(island.level, tower.at(dockEnd + 3, 1, 0), 2);
		// Up here, pillagers ride wild dragons instead of ravagers.
		BlockPos base = tower.at(0, 1, 0);
		DragonEntity.spawnRaiderMount(island.level, tower.at(-8, 8, 6), base);
		DragonEntity.spawnRaiderMount(island.level, tower.at(5, 12, -8), base);
	}

	/** Posts the outpost's illagers, for good, in the world being generated. */
	private record Garrison(Island island, RandomSource random) {
		void post(EntityType<? extends Mob> type, BlockPos at) {
			Mob mob = type.create(this.island.level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (mob == null) {
				return;
			}
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.random.nextFloat() * 360F, 0F);
			mob.finalizeSpawn(this.island.level, this.island.level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
			mob.setPersistenceRequired();
			this.island.level.addFreshEntityWithPassengers(mob);
		}
	}

	/**
	 * The watchtower, built in its own frame (x toward the dock, y up from the yard) and turned to face whichever
	 * way the island's dock points. A stone-footed ground floor, a railed middle floor, a lookout under a slab roof
	 * and a ladder up the back wall through all three.
	 */
	private record Tower(Island island, BlockPos origin, Rotation rotation) {
		private static final int MID = 5, TOP = 9, ROOF = 13;

		BlockPos at(int x, int y, int z) {
			return this.origin.offset(new BlockPos(x, y, z).rotate(this.rotation));
		}

		void put(int x, int y, int z, BlockState state) {
			this.island.set(this.at(x, y, z), state.rotate(this.rotation));
		}

		/** Island column offsets under a frame position. */
		private int[] column(int x, int z) {
			BlockPos turned = new BlockPos(x, 0, z).rotate(this.rotation);
			return new int[] {turned.getX(), turned.getZ()};
		}

		/** Levels a 9x9 yard around the tower: high ground cut down, low ground built up. */
		void levelYard() {
			int y0 = this.origin.getY();
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					if (!this.island.has(dx, dz)) {
						continue;
					}
					int top = this.island.surface(dx, dz);
					for (int y = top; y > y0; y--) {
						this.island.set(this.island.column(dx, y, dz), Blocks.AIR.defaultBlockState());
					}
					for (int y = top; y < y0; y++) {
						this.island.set(this.island.column(dx, y, dz), Blocks.DIRT.defaultBlockState());
					}
					this.island.set(this.island.column(dx, y0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
					this.island.setSurface(dx, dz, y0);
				}
			}
		}

		void build(RandomSource random) {
			BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
			BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
			BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
			BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
			BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					this.put(x, 0, z, (random.nextInt(4) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
				}
			}
			for (int x = -2; x <= 2; x += 4) {
				for (int z = -2; z <= 2; z += 4) {
					for (int y = 1; y < ROOF; y++) {
						this.put(x, y, z, log);
					}
				}
			}
			for (int y = 1; y < MID; y++) {
				for (int i = -1; i <= 1; i++) {
					BlockState wall = y == 1 ? Blocks.COBBLESTONE.defaultBlockState() : planks;
					this.put(2, y, i, wall);
					this.put(-2, y, i, wall);
					this.put(i, y, 2, wall);
					this.put(i, y, -2, wall);
				}
			}
			this.put(2, 1, 0, Blocks.AIR.defaultBlockState()); // the door, facing the dock
			this.put(2, 2, 0, Blocks.AIR.defaultBlockState());
			this.put(0, 3, 2, fence); // barred windows
			this.put(0, 3, -2, fence);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) != 2 || Math.abs(z) != 2) {
						this.put(x, MID, z, planks);
					}
				}
			}
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (Math.abs(x) != 2 || Math.abs(z) != 2) {
						this.put(x, TOP, z, planks);
					}
					if (Math.abs(x) == 3 || Math.abs(z) == 3) {
						this.put(x, TOP + 1, z, fence);
						this.put(x, ROOF, z, slab);
					} else {
						this.put(x, ROOF, z, planks);
					}
				}
			}
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					this.put(x, ROOF + 1, z, slab);
				}
			}
			for (int y = MID + 1; y < TOP; y++) { // the middle floor: a solid back wall for the ladder, rails elsewhere
				for (int i = -1; i <= 1; i++) {
					this.put(-2, y, i, planks);
				}
			}
			for (int i = -1; i <= 1; i++) {
				this.put(2, MID + 1, i, fence);
				this.put(i, MID + 1, 2, fence);
				this.put(i, MID + 1, -2, fence);
			}
			for (int y = 1; y <= TOP; y++) {
				this.put(-1, y, 0, ladder);
			}
			this.put(1, ROOF - 1, 1, hanging);
			this.put(-1, ROOF - 1, -1, hanging);
			this.put(1, MID - 1, 1, hanging);
			this.put(1, MID + 1, -1, Blocks.LANTERN.defaultBlockState());
			this.put(1, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
			this.put(1, 1, -1, Blocks.FLETCHING_TABLE.defaultBlockState());
			this.put(1, TOP + 1, 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
			RandomizableContainer.setBlockEntityLootTable(this.island.level, random, this.at(1, TOP + 1, 1), OUTPOST_LOOT);
		}

		/** A plank dock from the door out past the island's edge, railed over the drop. Returns how far out it runs. */
		int dock() {
			int y0 = this.origin.getY();
			int end = 4;
			while (end < REACH - 6) {
				int[] c = this.column(end, 0);
				if (!this.island.has(c[0], c[1])) {
					break;
				}
				end++;
			}
			end = Math.min(end + 3, REACH - 6);
			BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
			for (int x = 3; x <= end; x++) {
				for (int z = -2; z <= 2; z++) {
					int[] c = this.column(x, z);
					boolean overGround = this.island.has(c[0], c[1]);
					boolean side = Math.abs(z) == 2;
					this.put(x, 0, z, side ? beam : Blocks.DARK_OAK_PLANKS.defaultBlockState());
					for (int y = 1; y <= 3; y++) {
						this.put(x, y, z, Blocks.AIR.defaultBlockState());
					}
					if (overGround) {
						int top = this.island.surface(c[0], c[1]);
						for (int y = y0 + 1; y <= top; y++) {
							this.island.set(this.island.column(c[0], y, c[1]), Blocks.AIR.defaultBlockState());
						}
						if (side) {
							for (int y = y0 - 1; y > top; y--) {
								this.island.set(this.island.column(c[0], y, c[1]), Blocks.DARK_OAK_LOG.defaultBlockState()); // stilts
							}
						}
						this.island.setSurface(c[0], c[1], Math.min(top, y0));
					} else if (side) {
						this.put(x, 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
					}
				}
			}
			this.put(end, 1, 2, Blocks.DARK_OAK_FENCE.defaultBlockState());
			this.put(end, 2, 2, Blocks.LANTERN.defaultBlockState());
			this.put(end, 1, -2, Blocks.DARK_OAK_FENCE.defaultBlockState());
			this.put(end, 2, -2, Blocks.LANTERN.defaultBlockState());
			return end;
		}
	}
}
