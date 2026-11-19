package com.wonderrealms.sky;

import com.mojang.serialization.MapCodec;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A floating island high in the overworld sky. Each is a few overlapping, ragged lobes of rolling meadow with a
 * rocky underside that hangs down to a point, studded with ore and trailing glow berries and roots, and a few
 * small rocks drifting nearby. Some have a pond; some a spring that spills off the edge in a waterfall you can see
 * from the ground; wild ones hide a chest in a ruined shrine.
 * <p>
 * Some islands are claimed: about one in five is a dragon roost (a hay nest round a hoard, guarded by a wild
 * dragon), and about one in five is a pillager sky outpost (a watchtower and its garrison, a dock with an airship,
 * and pillagers riding dragons). Sky islands are the only place dragons are found. They float between y 180 and
 * 230, roughly a hundred blocks apart.
 * <p>
 * Everything is built within {@link #REACH} blocks of the middle of the chunk being decorated: the area a feature
 * may build in during world generation.
 */
public record SkyIslandFeature() implements Feature {
	public static final MapCodec<SkyIslandFeature> CODEC = MapCodec.unit(SkyIslandFeature::new);

	private static final ResourceKey<LootTable> ISLAND_LOOT = loot("chests/sky_island");
	private static final ResourceKey<LootTable> HOARD_LOOT = loot("chests/dragon_hoard");
	private static final ResourceKey<LootTable> OUTPOST_LOOT = loot("chests/sky_outpost");

	private static final int MIN_Y = 180;
	private static final int Y_SPREAD = 50;
	/** How far from the island's middle anything is built: inside the 3x3 chunks a feature may write to. */
	private static final int REACH = 22;

	private enum Kind { WILD, ROOST, OUTPOST }

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
		Kind kind = roll < 4 ? Kind.ROOST : roll < 8 ? Kind.OUTPOST : Kind.WILD;
		int radius = kind == Kind.OUTPOST ? 9 + random.nextInt(3) : 7 + random.nextInt(5);

		Island island = Island.shape(level, center, radius, random);
		island.build(random);
		switch (kind) {
			case ROOST -> roost(island, random);
			case OUTPOST -> outpost(island, random);
			case WILD -> {
				if (radius >= 9 && random.nextBoolean()) {
					pond(island, random);
				}
				shrine(island, random);
			}
		}
		trees(island, kind, random);
		if (random.nextInt(3) == 0) {
			waterfall(island, random);
		}
		island.hangUnderside(random);
		plants(island, random);
		islets(island, random);
		return true;
	}

	// ---- Shape ----------------------------------------------------------------------------------------------

	/** The island being built: its shape, column by column, and the level it goes into. */
	private static final class Island {
		private static final int SIZE = REACH * 2 + 1;
		private static final int NONE = Integer.MIN_VALUE;

		final WorldGenLevel level;
		final BlockPos center;
		final int radius;
		private final int[] surface = new int[SIZE * SIZE];
		private final int[] bottom = new int[SIZE * SIZE];
		private final float[] inner = new float[SIZE * SIZE];

		private Island(WorldGenLevel level, BlockPos center, int radius) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			Arrays.fill(this.surface, NONE);
		}

		/**
		 * A main mass and two or three lobes around it, each with a ragged edge. The ground rolls gently and rises
		 * toward the middle; the underside hangs down in a rough cone, deepest under the middle.
		 */
		static Island shape(WorldGenLevel level, BlockPos center, int radius, RandomSource random) {
			Island island = new Island(level, center, radius);
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
					// Coastline wobble: broad bays plus a finer ragged edge.
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

		/** Grass over a couple of layers of dirt, over stone that gets richer toward the tip. */
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
						BlockState state = below == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: below <= soil ? Blocks.DIRT.defaultBlockState()
							: rock(random, (float) (y - low) / Math.max(1, top - low));
						this.set(this.column(dx, y, dz), state);
					}
				}
			}
		}

		/** Glow berries and roots trailing from the underside, mostly out toward the edges. */
		void hangUnderside(RandomSource random) {
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					if (!this.has(dx, dz) || this.inner(dx, dz) > 0.6F || random.nextFloat() > 0.14F) {
						continue;
					}
					BlockPos tip = this.column(dx, this.bottom[index(dx, dz)] - 1, dz);
					if (!this.isAir(tip)) {
						continue;
					}
					if (random.nextInt(3) == 0) {
						this.set(tip, Blocks.HANGING_ROOTS.defaultBlockState());
						continue;
					}
					int length = 1 + random.nextInt(4);
					for (int i = 0; i < length; i++) {
						BlockState vine = i == length - 1
							? Blocks.CAVE_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE)
							: Blocks.CAVE_VINES_PLANT.defaultBlockState();
						this.set(tip.below(i), vine.setValue(CaveVines.BERRIES, random.nextInt(4) == 0));
					}
				}
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

	/** Stone for the underside, richer toward the tip where it's hardest to reach. */
	private static BlockState rock(RandomSource random, float height) {
		float roll = random.nextFloat();
		boolean deep = height < 0.35F;
		if (roll < 0.035F) {
			return Blocks.COAL_ORE.defaultBlockState();
		} else if (roll < 0.065F) {
			return Blocks.IRON_ORE.defaultBlockState();
		} else if (roll < 0.085F) {
			return Blocks.COPPER_ORE.defaultBlockState();
		} else if (roll < 0.097F) {
			return Blocks.GOLD_ORE.defaultBlockState();
		} else if (roll < 0.105F) {
			return Blocks.EMERALD_ORE.defaultBlockState();
		} else if (deep && roll < 0.115F) {
			return Blocks.DIAMOND_ORE.defaultBlockState();
		} else if (deep && roll < 0.123F) {
			return Blocks.LAPIS_ORE.defaultBlockState();
		} else if (roll < 0.23F) {
			return deep ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	// ---- Nature ---------------------------------------------------------------------------------------------

	private static void trees(Island island, Kind kind, RandomSource random) {
		int wanted = switch (kind) {
			case WILD -> 2 + random.nextInt(island.radius > 9 ? 3 : 2);
			case ROOST -> 1 + random.nextInt(2);
			case OUTPOST -> random.nextInt(2);
		};
		int clearing = kind == Kind.WILD ? 3 : 6; // keep the middle free for the nest or the tower
		List<BlockPos> planted = new ArrayList<>();
		for (int attempt = 0; attempt < 40 && planted.size() < wanted; attempt++) {
			int dx = random.nextInt(island.radius * 2 + 1) - island.radius;
			int dz = random.nextInt(island.radius * 2 + 1) - island.radius;
			if (island.inner(dx, dz) < 0.25F || dx * dx + dz * dz < clearing * clearing) {
				continue;
			}
			BlockPos ground = island.ground(dx, dz);
			if (!island.get(ground).is(Blocks.GRASS_BLOCK) || planted.stream().anyMatch(other -> other.distSqr(ground) < 25)) {
				continue;
			}
			boolean clear = true;
			for (int y = 1; y <= 8 && clear; y++) {
				clear = island.isAir(ground.above(y));
			}
			if (clear) {
				planted.add(ground);
				tree(island, ground.above(), random);
			}
		}
	}

	/** An oak with a round crown, a slim birch, or an azalea leaning under a wide flowering crown. */
	private static void tree(Island island, BlockPos base, RandomSource random) {
		List<BlockPos> logs = new ArrayList<>();
		Map<BlockPos, BlockState> leaves = new HashMap<>();
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		int kind = random.nextInt(10);
		if (kind < 6) {
			int height = 4 + random.nextInt(3);
			for (int y = 0; y < height; y++) {
				logs.add(base.above(y));
			}
			crown(leaves, base.above(height - 1), 2.6, 0.75, Blocks.OAK_LEAVES.defaultBlockState(), null, random);
		} else if (kind < 8) {
			log = Blocks.BIRCH_LOG.defaultBlockState();
			int height = 5 + random.nextInt(3);
			for (int y = 0; y < height; y++) {
				logs.add(base.above(y));
			}
			crown(leaves, base.above(height - 1), 1.9, 1.1, Blocks.BIRCH_LEAVES.defaultBlockState(), null, random);
		} else {
			Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos pos = base;
			for (int y = 0; y < 4; y++) {
				logs.add(pos);
				pos = y == 1 ? pos.above().relative(lean) : pos.above();
			}
			crown(leaves, pos, 2.8, 0.55, Blocks.AZALEA_LEAVES.defaultBlockState(), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), random);
			island.set(base.below(), Blocks.ROOTED_DIRT.defaultBlockState());
		}
		growTree(island, logs, log, leaves);
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

	/** Grass, ferns and flowers over the open meadow, and here and there a bush the fireflies like. */
	private static void plants(Island island, RandomSource random) {
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.LILY_OF_THE_VALLEY};
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				if (!island.has(dx, dz)) {
					continue;
				}
				BlockPos ground = island.ground(dx, dz);
				BlockPos above = ground.above();
				if (!island.get(ground).is(Blocks.GRASS_BLOCK) || !island.isAir(above)) {
					continue;
				}
				float roll = random.nextFloat();
				if (roll < 0.3F) {
					island.set(above, Blocks.SHORT_GRASS.defaultBlockState());
				} else if (roll < 0.34F) {
					island.set(above, Blocks.FERN.defaultBlockState());
				} else if (roll < 0.38F && island.isAir(above.above())) {
					DoublePlantBlock.placeAt(island.level, Blocks.TALL_GRASS.defaultBlockState(), above, Block.UPDATE_CLIENTS);
				} else if (roll < 0.45F) {
					island.set(above, flowers[random.nextInt(flowers.length)].defaultBlockState());
				} else if (roll < 0.49F) {
					island.set(above, Blocks.WILDFLOWERS.defaultBlockState()
						.setValue(FlowerBedBlock.AMOUNT, 1 + random.nextInt(4))
						.setValue(FlowerBedBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
				} else if (roll < 0.5F) {
					island.set(above, Blocks.FIREFLY_BUSH.defaultBlockState());
				}
			}
		}
	}

	/** A little pond in the meadow, two deep in the middle, on clay and sand, with lily pads and reeds. */
	private static void pond(Island island, RandomSource random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			int cx = random.nextInt(7) - 3;
			int cz = random.nextInt(7) - 3;
			if (island.inner(cx, cz) < 0.5F) {
				continue;
			}
			int r = 2 + random.nextInt(2);
			int water = island.surface(cx, cz);
			List<int[]> cells = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz <= r * r + random.nextInt(3) - 1) {
						cells.add(new int[] {cx + dx, cz + dz, dx * dx + dz * dz < (r - 0.5) * (r - 0.5) ? 2 : 1});
					}
				}
			}
			// The ring around the water has to be solid island at least as high as the water, or it'd spill.
			boolean fits = true;
			for (int dx = -r - 1; dx <= r + 1 && fits; dx++) {
				for (int dz = -r - 1; dz <= r + 1 && fits; dz++) {
					fits = island.inner(cx + dx, cz + dz) >= 0.2F && (island.surface(cx + dx, cz + dz) >= water || isCell(cells, cx + dx, cz + dz));
				}
			}
			if (!fits) {
				continue;
			}
			for (int[] cell : cells) {
				int x = cell[0];
				int z = cell[1];
				int floor = water - cell[2];
				for (int y = Math.min(island.surface(x, z), floor - 1) + 1; y < floor; y++) {
					island.set(island.column(x, y, z), Blocks.DIRT.defaultBlockState());
				}
				island.set(island.column(x, floor, z), (random.nextInt(3) == 0 ? Blocks.CLAY : Blocks.SAND).defaultBlockState());
				for (int y = floor + 1; y <= water; y++) {
					island.set(island.column(x, y, z), Blocks.WATER.defaultBlockState());
				}
				for (int y = water + 1; y <= island.surface(x, z); y++) {
					island.set(island.column(x, y, z), Blocks.AIR.defaultBlockState());
				}
				island.setSurface(x, z, water);
				if (random.nextInt(6) == 0) {
					island.set(island.column(x, water + 1, z), Blocks.LILY_PAD.defaultBlockState());
				}
			}
			for (int[] cell : cells) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int x = cell[0] + side.getStepX();
					int z = cell[1] + side.getStepZ();
					if (!isCell(cells, x, z) && island.surface(x, z) == water && random.nextInt(4) == 0
							&& island.get(island.ground(x, z)).is(Blocks.GRASS_BLOCK) && island.isAir(island.ground(x, z).above())) {
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
				if (island.isRim(dx, dz) && island.get(island.ground(dx, dz)).is(Blocks.GRASS_BLOCK)) {
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

	/** A few small rocks drifting near the island, some with a tuft of grass. */
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
							island.set(pos, dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState() : dy == 1 ? Blocks.DIRT.defaultBlockState() : rock(random, 0.5F));
						}
					}
					if (random.nextInt(3) == 0 && island.isAir(middle.offset(dx, 1, dz))) {
						island.set(middle.offset(dx, 1, dz), Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
	}

	// ---- Wild islands ---------------------------------------------------------------------------------------

	/** A ruined shrine of mossy stone in the meadow, its chest still waiting in the middle. */
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
						&& island.get(island.ground(cx + dx, cz + dz)).is(Blocks.GRASS_BLOCK);
				}
			}
			if (!flat) {
				continue;
			}
			BlockState[] stones = {Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
				Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos ground = island.ground(cx + dx, cz + dz);
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					if (corner) {
						int height = 1 + random.nextInt(3);
						for (int h = 1; h <= height; h++) {
							island.set(ground.above(h), stones[random.nextInt(stones.length)]);
						}
						if (random.nextBoolean()) {
							island.set(ground.above(height + 1), Blocks.STONE_BRICK_SLAB.defaultBlockState());
						}
					} else if (random.nextInt(5) > 0) {
						island.set(ground, random.nextInt(4) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : stones[random.nextInt(stones.length)]);
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

	/** A dragon's nest: a ring of hay strewn with bones round a hoard of treasure, and the wild dragon guarding it. */
	private static void roost(Island island, RandomSource random) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 3.6 || !island.has(dx, dz)) {
					continue;
				}
				BlockPos ground = island.ground(dx, dz);
				if (d > 2.4) {
					island.set(ground.above(), random.nextInt(7) == 0 ? pillar(Blocks.BONE_BLOCK, random) : pillar(Blocks.HAY_BLOCK, random));
					if (d > 2.9 && random.nextInt(3) > 0) {
						island.set(ground.above(2), pillar(Blocks.HAY_BLOCK, random));
					}
				} else {
					island.set(ground, (random.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState());
				}
			}
		}
		chest(island, island.ground(0, 0).above(), HOARD_LOOT, random);
		DragonElement[] wild = {DragonElement.EMBER, DragonElement.FROST, DragonElement.STORM};
		DragonEntity guard = Dragons.TYPES.get(wild[random.nextInt(wild.length)]).create(island.level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (guard != null) {
			BlockPos at = island.ground(1, 1).above();
			guard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360F, 0F);
			guard.setIslandHome(island.ground(0, 0).above(), false);
			island.level.addFreshEntity(guard);
		}
	}

	private static BlockState pillar(Block block, RandomSource random) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.getRandom(random));
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
