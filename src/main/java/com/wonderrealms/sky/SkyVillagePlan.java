package com.wonderrealms.sky;

import com.wonderrealms.airship.AirshipEntity;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
import com.wonderrealms.dragon.Dragons;
import com.wonderrealms.sky.SkyIslandFeature.Island;
import com.wonderrealms.sky.SkyIslandFeature.Theme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The plan of a sky village, drawn whole on a {@link VillageBuffer} and then built a chunk at a time. On an island
 * about a hundred blocks across: a paved plaza with a well, a bell and market stalls; four roads out to the edge, lit
 * by lamp posts; along them cottages and houses (a bed, a job site and a villager for each bed), a smithy, a library,
 * farms, an animal pen and a lookout tower; and, the pride of every sky village, two great dragon stables, each with a
 * stall for a dragon either side of the aisle, a hayloft above and the tack room at the back. A plank dock runs off the
 * end of the longest road, with the village's airship moored at it, and a fence runs all round the edge so nobody
 * wanders off. There's open meadow left between it all, for the village to grow into.
 */
final class SkyVillagePlan {
	private static final ResourceKey<LootTable> STABLE_LOOT = SkyIslandFeature.loot("chests/dragon_stable");

	/** What each column of the island is for. Each only ever moves up this list. */
	private static final byte FREE = 0, CLEAR = 1, ROAD = 2, BUILT = 3;
	/** Roads stop, and buildings keep back, this far in from the edge (see {@link Island#inner}). */
	private static final float ROAD_INNER = 0.1F, PLOT_INNER = 0.08F;

	/** Plans kept to hand: the world builds a village a chunk at a time, some forty chunks in all. */
	private static final int KEPT = 6;
	private static final Map<Long, VillageBuffer> PLANS = new LinkedHashMap<Long, VillageBuffer>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, VillageBuffer> eldest) {
			return this.size() > KEPT;
		}
	};

	private final Island island;
	private final VillageBuffer buffer;
	private final RandomSource random;
	private final boolean overcloud;
	private final int reach;
	private final byte[] use;
	private final List<Road> roads = new ArrayList<>();
	private final List<Block> jobs = new ArrayList<>();
	/** Where the stables' dragons live, and so where a golem keeps watch. */
	private final List<BlockPos> stalls = new ArrayList<>();
	private int floor;
	private int cottages;

	/** The plan of the village these settings describe: the same every time, so every chunk builds its part of one village. */
	static VillageBuffer of(BlockPos center, int radius, int reach, long seed, boolean overcloud) {
		long key = seed * 31 + center.asLong();
		synchronized (PLANS) {
			VillageBuffer plan = PLANS.get(key);
			if (plan != null) {
				return plan;
			}
		}
		VillageBuffer plan = new SkyVillagePlan(center, radius, reach, seed, overcloud).draw();
		synchronized (PLANS) {
			PLANS.putIfAbsent(key, plan);
			return PLANS.get(key);
		}
	}

	private SkyVillagePlan(BlockPos center, int radius, int reach, long seed, boolean overcloud) {
		this.buffer = new VillageBuffer(SkyVillagePiece.box(center, reach));
		this.random = RandomSource.create(seed);
		this.overcloud = overcloud;
		this.reach = reach;
		this.island = Island.grand(this.buffer, center, radius, reach, Theme.MEADOW, this.random);
		this.use = new byte[(2 * reach + 1) * (2 * reach + 1)];
	}

	private VillageBuffer draw() {
		this.island.build(this.random);
		this.plaza();
		this.roads();
		Road harbour = this.roads.stream().max(Comparator.comparingInt(Road::length)).orElseThrow();
		BlockPos mooring = this.dock(harbour);
		// The buildings that matter most go down first, each beside a road, facing it.
		this.place(Plot.STABLE, true);
		this.place(Plot.STABLE, true);
		this.place(Plot.SMITHY, false);
		this.place(Plot.LIBRARY, false);
		this.place(Plot.TOWER, true);
		this.place(Plot.PEN, false);
		this.place(Plot.FARM, false);
		this.place(Plot.FARM, false);
		for (int i = 0; i < 3; i++) {
			this.place(Plot.HOUSE, false);
		}
		for (int i = 0; i < 7; i++) {
			this.place(Plot.COTTAGE, false);
		}
		this.place(Plot.FARM, false);
		this.lamps();
		this.market();
		for (int i = 2 + this.random.nextInt(2); i > 0; i--) {
			SkyIslandFeature.waterfall(this.island, this.random);
		}
		this.rimFence();
		this.trees();
		SkyIslandFeature.plants(this.island, this.random);
		this.island.hangUnderside(this.random);
		SkyIslandFeature.islets(this.island, this.random);
		this.residents(mooring);
		return this.buffer;
	}

	// ---- Columns --------------------------------------------------------------------------------------------

	private boolean inside(int dx, int dz) {
		return Math.abs(dx) <= this.reach && Math.abs(dz) <= this.reach;
	}

	private byte use(int dx, int dz) {
		return this.inside(dx, dz) ? this.use[(dx + this.reach) * (2 * this.reach + 1) + dz + this.reach] : BUILT;
	}

	private void mark(int dx, int dz, byte kind) {
		if (this.inside(dx, dz)) {
			int i = (dx + this.reach) * (2 * this.reach + 1) + dz + this.reach;
			this.use[i] = (byte) Math.max(this.use[i], kind);
		}
	}

	/** Brings a column's ground to height {@code y}, built up with dirt or cut down, and tops it with {@code top}. */
	private void level(int dx, int dz, int y, BlockState top) {
		if (!this.island.has(dx, dz)) {
			return;
		}
		int was = this.island.surface(dx, dz);
		for (int h = was + 1; h < y; h++) {
			this.island.set(this.island.column(dx, h, dz), h > y - 3 ? Blocks.DIRT.defaultBlockState() : this.island.theme.rock(this.random, 0.5F, h));
		}
		for (int h = y + 1; h <= was; h++) {
			this.island.set(this.island.column(dx, h, dz), Blocks.AIR.defaultBlockState());
		}
		this.island.set(this.island.column(dx, y, dz), top);
		this.island.setSurface(dx, dz, y);
	}

	private void set(BlockPos pos, Block block) {
		this.island.set(pos, block.defaultBlockState());
	}

	private void set(BlockPos pos, BlockState state) {
		this.island.set(pos, state);
	}

	// ---- The plaza ------------------------------------------------------------------------------------------

	/** The middle of the village: a round paved plaza, its well, its bell and lamps at the mouth of each road. */
	private void plaza() {
		this.floor = this.island.surface(0, 0);
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 7.6) {
					continue;
				}
				float r = this.random.nextFloat();
				Block paving = d > 6.6 ? Blocks.DIRT_PATH : r < 0.4F ? Blocks.COBBLESTONE : r < 0.7F ? Blocks.STONE_BRICKS
					: r < 0.82F ? Blocks.MOSSY_COBBLESTONE : r < 0.92F ? Blocks.CRACKED_STONE_BRICKS : Blocks.DIRT_PATH;
				this.level(dx, dz, this.floor, paving.defaultBlockState());
				this.mark(dx, dz, BUILT);
			}
		}
		// The well: water in a ring of cobblestone under a little roof on posts, a lantern hanging over it.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = this.island.column(dx, this.floor, dz);
				boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
				if (dx == 0 && dz == 0) {
					this.set(pos.below(), Blocks.COBBLESTONE);
					this.set(pos, Blocks.WATER);
				} else {
					this.set(pos, Blocks.COBBLESTONE);
					this.set(pos.above(), corner ? Blocks.COBBLESTONE : Blocks.COBBLESTONE_SLAB);
				}
				if (corner) {
					this.set(pos.above(2), Blocks.OAK_FENCE);
					this.set(pos.above(3), Blocks.OAK_FENCE);
				}
				this.set(pos.above(4), Blocks.COBBLESTONE_SLAB);
			}
		}
		this.set(this.island.column(0, this.floor + 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		this.set(this.island.column(2, this.floor + 1, 2), Blocks.BELL.defaultBlockState()
			.setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		for (int[] lamp : new int[][] {{2, 6}, {-2, -6}, {6, -2}, {-6, 2}}) {
			this.lampPost(this.island.column(lamp[0], this.floor + 1, lamp[1]));
		}
	}

	/** Three market stalls round the plaza under awnings of coloured wool, each with its trade's job site for a counter. */
	private void market() {
		Block[] counters = {Blocks.BARREL, Blocks.SMOKER, Blocks.LOOM, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.COMPOSTER};
		for (int[] spot : new int[][] {{4, -4}, {-4, -4}, {-4, 4}}) {
			Block awning = Blocks.WOOL.pick(DyeColor.values()[this.random.nextInt(16)]);
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					BlockPos base = this.island.column(spot[0] + a, this.floor + 1, spot[1] + b);
					if (Math.abs(a) == 1 && Math.abs(b) == 1) {
						this.set(base, Blocks.OAK_FENCE);
						this.set(base.above(), Blocks.OAK_FENCE);
					}
					this.set(base.above(2), awning);
				}
			}
			this.set(this.island.column(spot[0], this.floor + 1, spot[1]), counters[this.random.nextInt(counters.length)]);
		}
	}

	private void lampPost(BlockPos base) {
		this.set(base, Blocks.OAK_FENCE);
		this.set(base.above(), Blocks.OAK_FENCE);
		this.set(base.above(2), Blocks.LANTERN);
	}

	// ---- Roads and the dock ---------------------------------------------------------------------------------

	/** A road from the plaza out toward the edge: {@code at(u, v)} is the column {@code u} along it and {@code v} to its left. */
	private record Road(Direction way, int length) {
		Direction left() {
			return this.way.getCounterClockWise();
		}

		int[] at(int u, int v) {
			Direction left = this.left();
			return new int[] {this.way.getStepX() * u + left.getStepX() * v, this.way.getStepZ() * u + left.getStepZ() * v};
		}
	}

	/** Four roads of beaten earth, north, south, east and west, three wide, rising and falling gently with the land. */
	private void roads() {
		for (Direction way : Direction.Plane.HORIZONTAL) {
			Road probe = new Road(way, 0);
			int length = 7;
			while (length < this.reach - 6) {
				boolean ok = true;
				for (int v = -2; v <= 2 && ok; v++) {
					int[] c = probe.at(length + 1, v);
					ok = this.island.has(c[0], c[1]) && this.island.inner(c[0], c[1]) >= ROAD_INNER;
				}
				if (!ok) {
					break;
				}
				length++;
			}
			Road road = new Road(way, length);
			this.roads.add(road);
			int y = this.floor;
			for (int u = 8; u <= length; u++) {
				int[] mid = road.at(u, 0);
				y = Mth.clamp(this.island.surface(mid[0], mid[1]), y - 1, y + 1);
				for (int v = -1; v <= 1; v++) {
					int[] c = road.at(u, v);
					float r = this.random.nextFloat();
					Block top = v == 0 || r < 0.8F ? Blocks.DIRT_PATH : r < 0.9F ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
					this.level(c[0], c[1], y, top.defaultBlockState());
					this.mark(c[0], c[1], ROAD);
				}
				for (int v = -2; v <= 2; v += 4) {
					int[] c = road.at(u, v);
					int verge = this.island.surface(c[0], c[1]);
					if (Math.abs(verge - y) > 1) {
						this.level(c[0], c[1], Mth.clamp(verge, y - 1, y + 1), Blocks.GRASS_BLOCK.defaultBlockState());
					}
					this.mark(c[0], c[1], CLEAR);
				}
			}
		}
	}

	/**
	 * A plank dock off the end of a road, out past the island's edge: railed, lit at the end, held up on posts, with a
	 * gap in the end rail to step aboard the airship moored there. Returns where the airship waits.
	 */
	private BlockPos dock(Road road) {
		int[] end = road.at(road.length(), 0);
		int y = this.island.surface(end[0], end[1]);
		int edge = road.length() + 1;
		while (edge < this.reach - 4) {
			int[] c = road.at(edge, 0);
			if (!this.island.has(c[0], c[1])) {
				break;
			}
			edge++;
		}
		int last = Math.min(edge + 6, this.reach - 4);
		Direction left = road.left();
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState rail = fence(Blocks.SPRUCE_FENCE, road.way(), road.way().getOpposite());
		BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int u = road.length() + 1; u <= last; u++) {
			for (int v = -2; v <= 2; v++) {
				int[] c = road.at(u, v);
				boolean ground = this.island.has(c[0], c[1]);
				if (ground && this.island.surface(c[0], c[1]) >= y) {
					for (int h = y + 1; h <= this.island.surface(c[0], c[1]); h++) {
						this.set(this.island.column(c[0], h, c[1]), Blocks.AIR);
					}
					this.island.setSurface(c[0], c[1], y);
				}
				BlockPos plank = this.island.column(c[0], y, c[1]);
				this.set(plank, deck);
				this.mark(c[0], c[1], ROAD);
				boolean overDrop = !ground || this.island.surface(c[0], c[1]) < y;
				if (Math.abs(v) == 2 && (overDrop || u >= edge - 2)) {
					this.set(plank.above(), rail);
				}
				if (Math.abs(v) == 2 && overDrop && (u - road.length()) % 3 == 0) {
					for (int h = 1; h <= 3; h++) {
						this.set(plank.below(h), post); // posts hanging under the deck
					}
				}
			}
		}
		// The end rail, with a gap in the middle to step aboard, and lanterns on its corners.
		for (int v = -2; v <= 2; v++) {
			int[] c = road.at(last, v);
			BlockPos top = this.island.column(c[0], y + 1, c[1]);
			if (v == 0) {
				this.set(top, Blocks.AIR);
				continue;
			}
			Direction toward = v < 0 ? left : left.getOpposite();
			this.set(top, fence(Blocks.SPRUCE_FENCE, Math.abs(v) == 1 ? new Direction[] {toward.getOpposite()} : new Direction[] {toward, road.way().getOpposite()}));
			if (Math.abs(v) == 2) {
				this.set(top.above(), Blocks.LANTERN);
			}
		}
		int[] moor = road.at(last + 4, 0);
		return this.island.column(moor[0], y + 1, moor[1]);
	}

	/** Lamp posts along each road, on alternate sides every nine blocks or so, keeping clear of doorsteps. */
	private void lamps() {
		for (Road road : this.roads) {
			int side = this.random.nextBoolean() ? 2 : -2;
			for (int u = 12; u <= road.length() - 2; u += 9) {
				for (int k = 0; k < 3; k++) {
					int[] c = road.at(u + k, side);
					if (this.island.has(c[0], c[1]) && this.use(c[0], c[1]) <= CLEAR) {
						this.lampPost(this.island.column(c[0], this.island.surface(c[0], c[1]) + 1, c[1]));
						this.mark(c[0], c[1], BUILT);
						break;
					}
				}
				side = -side;
			}
		}
	}

	// ---- Plots ----------------------------------------------------------------------------------------------

	private enum Plot {
		STABLE(17, 11, 7, 8, 9), SMITHY(7, 7, 2, 3, 4), LIBRARY(7, 9, 3), FARM(9, 9, 4), PEN(9, 9, 4), TOWER(5, 5, 2), HOUSE(7, 7, 3),
		COTTAGE(5, 5, 2);

		final int width;
		final int depth;
		/** Where along its front the way in is, for the path to the road. */
		final int[] doors;

		Plot(int width, int depth, int... doors) {
			this.width = width;
			this.depth = depth;
			this.doors = doors;
		}
	}

	/**
	 * A building's own frame: x across its front from the left (seen from inside, looking out of the door), z back
	 * from its front wall, y up from its floor. {@code front} is the way out of the door, toward the road.
	 */
	private record Frame(BlockPos origin, Direction front) {
		Direction right() {
			return this.front.getClockWise();
		}

		Direction back() {
			return this.front.getOpposite();
		}

		BlockPos at(int x, int y, int z) {
			return this.origin.relative(this.right(), x).relative(this.back(), z).above(y);
		}
	}

	private void put(Frame f, int x, int y, int z, BlockState state) {
		this.island.set(f.at(x, y, z), state);
	}

	private void put(Frame f, int x, int y, int z, Block block) {
		this.island.set(f.at(x, y, z), block.defaultBlockState());
	}

	/** Finds a spot beside a road for {@code plot} (out toward the edge if {@code far}, else nearer the plaza) and builds it. */
	private boolean place(Plot plot, boolean far) {
		List<int[]> spots = new ArrayList<>();
		for (int r = 0; r < this.roads.size(); r++) {
			Road road = this.roads.get(r);
			for (int sign = -1; sign <= 1; sign += 2) {
				for (int t = 9; t + plot.width - 1 <= road.length(); t++) {
					spots.add(new int[] {r, sign, t, (far ? -t : t) * 4 + this.random.nextInt(24)});
				}
			}
		}
		spots.sort(Comparator.comparingInt(spot -> spot[3]));
		for (int[] spot : spots) {
			Frame frame = this.fit(plot, this.roads.get(spot[0]), spot[1], spot[2]);
			if (frame != null) {
				this.build(plot, frame);
				return true;
			}
		}
		return false;
	}

	/** The frame for {@code plot} beside {@code road} ({@code sign} 1 its left side, -1 its right), {@code t} along it, if it fits. */
	private Frame fit(Plot plot, Road road, int sign, int t) {
		Direction side = sign > 0 ? road.left() : road.left().getOpposite();
		Direction front = side.getOpposite();
		Direction right = front.getClockWise();
		int[] origin = road.at(right == road.way() ? t : t + plot.width - 1, 3 * sign);
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, sum = 0, count = 0;
		for (int x = -1; x <= plot.width; x++) {
			for (int z = -1; z <= plot.depth; z++) {
				int dx = origin[0] + right.getStepX() * x + side.getStepX() * z;
				int dz = origin[1] + right.getStepZ() * x + side.getStepZ() * z;
				boolean core = x >= 0 && x < plot.width && z >= 0 && z < plot.depth;
				byte used = this.use(dx, dz);
				if (core) {
					if (!this.island.has(dx, dz) || this.island.inner(dx, dz) < PLOT_INNER || used != FREE) {
						return null;
					}
					int y = this.island.surface(dx, dz);
					low = Math.min(low, y);
					high = Math.max(high, y);
					sum += y;
					count++;
				} else if (this.inside(dx, dz) && used > CLEAR && !(used == ROAD && z == -1)) {
					return null; // keep a gap between buildings
				}
			}
		}
		if (high - low > (plot == Plot.STABLE || plot == Plot.FARM ? 5 : 4)) {
			return null;
		}
		int door = plot.doors[plot.doors.length / 2];
		int[] step = road.at(right == road.way() ? t + door : t + plot.width - 1 - door, 0);
		int roadY = this.island.surface(step[0], step[1]);
		int floor = Mth.clamp(Math.round((float) sum / count), roadY - 1, roadY + 1);
		return new Frame(this.island.column(origin[0], floor, origin[1]), front);
	}

	private int[] rel(BlockPos pos) {
		return new int[] {pos.getX() - this.island.center.getX(), pos.getZ() - this.island.center.getZ()};
	}

	private void build(Plot plot, Frame f) {
		int floor = f.origin().getY();
		for (int x = -1; x <= plot.width; x++) {
			for (int z = -1; z <= plot.depth; z++) {
				int[] c = this.rel(f.at(x, 0, z));
				if (!this.island.has(c[0], c[1])) {
					continue;
				}
				if (x >= 0 && x < plot.width && z >= 0 && z < plot.depth) {
					this.level(c[0], c[1], floor, (plot == Plot.PEN ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState());
					this.mark(c[0], c[1], BUILT);
				} else if (this.use(c[0], c[1]) <= CLEAR) {
					int y = this.island.surface(c[0], c[1]);
					if (Math.abs(y - floor) > 1) {
						this.level(c[0], c[1], Mth.clamp(y, floor - 1, floor + 1), Blocks.GRASS_BLOCK.defaultBlockState());
					}
					this.mark(c[0], c[1], CLEAR);
				}
			}
		}
		for (int door : plot.doors) {
			int[] c = this.rel(f.at(door, 0, -1));
			this.level(c[0], c[1], floor, Blocks.DIRT_PATH.defaultBlockState());
			this.mark(c[0], c[1], ROAD);
		}
		switch (plot) {
			case STABLE -> this.stable(f);
			case SMITHY -> this.smithy(f);
			case LIBRARY -> this.library(f);
			case FARM -> this.farm(f);
			case PEN -> this.pen(f);
			case TOWER -> this.tower(f);
			case HOUSE -> this.house(f);
			case COTTAGE -> SkyIslandFeature.cottage(this.island, f.at(2, 0, 2), f.front(), this.job(), this.cottages++ == 2, this.random);
		}
	}

	/** The next job site for a home: every trade once before any twice. */
	private Block job() {
		if (this.jobs.isEmpty()) {
			this.jobs.addAll(Arrays.asList(SkyIslandFeature.JOB_SITES));
		}
		return this.jobs.remove(this.random.nextInt(this.jobs.size()));
	}

	// ---- The buildings --------------------------------------------------------------------------------------

	private record Wood(Block planks, Block log, Block stairs, Block slab, Block door) {
		static final Wood[] WALLS = {
			new Wood(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_DOOR),
			new Wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR),
			new Wood(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_DOOR)};
		static final Wood[] ROOFS = {
			new Wood(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR),
			new Wood(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_DOOR)};
	}

	/**
	 * A pitched roof over a building {@code width} wide, its ridge running front to back: stairs up both sides from
	 * {@code eaves}, overhanging a block all round, the gable ends filled in, a slab along the ridge.
	 */
	private void roof(Frame f, int width, int depth, int eaves, Wood roof, Block gable) {
		BlockState up = roof.stairs().defaultBlockState().setValue(StairBlock.FACING, f.right());
		BlockState down = roof.stairs().defaultBlockState().setValue(StairBlock.FACING, f.right().getOpposite());
		int steps = width / 2;
		for (int i = 0; i <= steps; i++) {
			int y = eaves + i;
			for (int z = -1; z <= depth; z++) {
				if (i < steps || width % 2 == 0) {
					this.put(f, i - 1, y, z, up);
					this.put(f, width - i, y, z, down);
				}
				if (z == 0 || z == depth - 1) {
					for (int x = i; x <= width - 1 - i; x++) {
						this.put(f, x, y, z, gable);
					}
				}
			}
		}
		for (int z = -1; z <= depth; z++) {
			this.put(f, width / 2, eaves + steps, z, roof.slab());
		}
	}

	private void door(Frame f, int x, Block door) {
		BlockState state = door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.back());
		this.put(f, x, 1, 0, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		this.put(f, x, 2, 0, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** A bed along the {@code back} direction, its foot at (x, z). */
	private void bed(Frame f, int x, int z) {
		BlockState bed = Blocks.BED.pick(DyeColor.values()[this.random.nextInt(16)]).defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, f.back());
		this.put(f, x, 1, z, bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		this.put(f, x, 1, z + 1, bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	private void hangLantern(Frame f, int x, int y, int z) {
		this.put(f, x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** A glass pane set in a wall running along {@code along}. */
	private static BlockState pane(Direction along) {
		return fence(Blocks.GLASS_PANE, along, along.getOpposite());
	}

	/** A fence (or pane, or bars) joined on the given sides. */
	private static BlockState fence(Block block, Direction... joins) {
		BlockState state = block.defaultBlockState();
		for (Direction side : joins) {
			BooleanProperty property = switch (side) {
				case NORTH -> FenceBlock.NORTH;
				case SOUTH -> FenceBlock.SOUTH;
				case WEST -> FenceBlock.WEST;
				case EAST -> FenceBlock.EAST;
				default -> null;
			};
			if (property != null) {
				state = state.setValue(property, true);
			}
		}
		return state;
	}

	/**
	 * A house seven blocks square for a family of two: a cobblestone footing, a timber frame filled in with planks,
	 * windows all round, a pitched roof, and inside two beds, a job site, a crafting table, a chest and a lantern.
	 */
	private void house(Frame f) {
		Wood wood = Wood.WALLS[this.random.nextInt(Wood.WALLS.length)];
		Wood roof = Wood.ROOFS[this.random.nextInt(Wood.ROOFS.length)];
		BlockState log = wood.log().defaultBlockState();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean side = x == 0 || x == 6, end = z == 0 || z == 6;
				this.put(f, x, 0, z, side || end ? Blocks.COBBLESTONE : wood.planks());
				for (int y = 1; y <= 4; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (side && end) {
						state = log;
					} else if (side || end) {
						boolean window = (y == 2 || y == 3) && (side ? z == 2 || z == 4 : x == 1 || x == 5 || z == 6 && x == 3);
						state = y == 1 ? Blocks.COBBLESTONE.defaultBlockState() : window ? pane(side ? f.back() : f.right()) : wood.planks().defaultBlockState();
					}
					this.put(f, x, y, z, state);
				}
				this.put(f, x, 5, z, wood.planks());
			}
		}
		this.roof(f, 7, 7, 5, roof, wood.planks());
		this.door(f, 3, wood.door());
		this.bed(f, 1, 4);
		this.bed(f, 5, 4);
		this.put(f, 5, 1, 1, this.job());
		this.put(f, 1, 1, 1, Blocks.CRAFTING_TABLE);
		this.put(f, 3, 1, 5, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		SkyIslandFeature.fill(this.island, f.at(3, 1, 5), BuiltInLootTables.VILLAGE_PLAINS_HOUSE, this.random);
		this.put(f, 3, 1, 3, Blocks.CARPET.pick(DyeColor.values()[this.random.nextInt(16)]));
		this.hangLantern(f, 3, 4, 3);
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.VILLAGER, f.at(2, 1, 2), this.random);
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.VILLAGER, f.at(4, 1, 2), this.random);
	}

	/** The library: a long stone-footed hall lined with bookshelves, a lectern for its librarian, and a bed at the back. */
	private void library(Frame f) {
		Wood roof = Wood.ROOFS[this.random.nextInt(Wood.ROOFS.length)];
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 9; z++) {
				boolean side = x == 0 || x == 6, end = z == 0 || z == 8;
				this.put(f, x, 0, z, side || end ? Blocks.STONE_BRICKS : Blocks.DARK_OAK_PLANKS);
				for (int y = 1; y <= 4; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (side && (end || z == 4)) {
						state = log;
					} else if (end && (x == 0 || x == 6)) {
						state = log;
					} else if (side || end) {
						boolean window = (y == 2 || y == 3) && (side ? z == 2 || z == 6 : x == 1 || x == 5 || z == 8 && x == 3);
						state = y == 1 ? Blocks.STONE_BRICKS.defaultBlockState() : window ? pane(side ? f.back() : f.right()) : Blocks.SPRUCE_PLANKS.defaultBlockState();
					}
					this.put(f, x, y, z, state);
				}
				this.put(f, x, 5, z, Blocks.SPRUCE_PLANKS);
			}
		}
		this.roof(f, 7, 9, 5, roof, Blocks.SPRUCE_PLANKS);
		this.door(f, 3, Blocks.SPRUCE_DOOR);
		for (int z = 2; z <= 5; z++) {
			for (int y = 1; y <= 2; y++) {
				this.put(f, 1, y, z, Blocks.BOOKSHELF);
				this.put(f, 5, y, z, Blocks.BOOKSHELF);
			}
		}
		this.put(f, 3, 1, 6, Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		this.bed(f, 1, 6);
		this.put(f, 5, 1, 7, Blocks.POTTED_POPPY);
		this.put(f, 3, 1, 3, Blocks.CARPET.red());
		this.put(f, 3, 1, 4, Blocks.CARPET.red());
		this.hangLantern(f, 3, 4, 2);
		this.hangLantern(f, 3, 4, 6);
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.VILLAGER, f.at(3, 1, 2), this.random);
	}

	/** The smithy: open to the road, a forge of a blast furnace and a cauldron of lava, an anvil, a grindstone and its chest. */
	private void smithy(Frame f) {
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean side = x == 0 || x == 6, back = z == 6;
				this.put(f, x, 0, z, this.random.nextInt(3) == 0 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS);
				for (int y = 1; y <= 4; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (side && (z == 0 || back)) {
						state = log;
					} else if (side || back) {
						state = (y == 4 ? Blocks.STONE_BRICKS : this.random.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
					}
					this.put(f, x, y, z, state);
				}
			}
		}
		for (int x = -1; x <= 7; x++) {
			for (int z = -1; z <= 7; z++) {
				this.put(f, x, 5, z, Blocks.STONE_BRICK_SLAB);
			}
		}
		for (int y = 5; y <= 7; y++) {
			this.put(f, 5, y, 5, Blocks.COBBLESTONE); // the chimney
		}
		this.put(f, 5, 8, 5, Blocks.CAMPFIRE);
		this.put(f, 5, 1, 5, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		this.put(f, 4, 1, 5, Blocks.LAVA_CAULDRON);
		this.put(f, 3, 1, 3, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.right()));
		this.put(f, 1, 1, 4, Blocks.SMITHING_TABLE);
		this.put(f, 1, 1, 2, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		this.put(f, 2, 1, 5, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		SkyIslandFeature.fill(this.island, f.at(2, 1, 5), this.random.nextBoolean() ? BuiltInLootTables.VILLAGE_WEAPONSMITH
			: BuiltInLootTables.VILLAGE_TOOLSMITH, this.random);
		this.hangLantern(f, 2, 4, 2);
		this.hangLantern(f, 4, 4, 3);
	}

	/** A field of one crop round a channel of water, edged with logs, with a composter and a scarecrow. */
	private void farm(Frame f) {
		CropBlock crop = (CropBlock) SkyIslandFeature.VILLAGE_CROPS[this.random.nextInt(SkyIslandFeature.VILLAGE_CROPS.length)];
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean side = x == 0 || x == 8, end = z == 0 || z == 8;
				if (side || end) {
					Direction.Axis axis = side && end ? Direction.Axis.Y : side ? f.front().getAxis() : f.right().getAxis();
					this.put(f, x, 0, z, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
				} else if (x == 4) {
					this.put(f, x, 0, z, Blocks.WATER);
				} else {
					this.put(f, x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					this.put(f, x, 1, z, crop.getStateForAge(this.random.nextInt(crop.getMaxAge() + 1)));
				}
			}
		}
		this.put(f, 0, 1, 0, Blocks.COMPOSTER);
		this.put(f, 8, 1, 8, Blocks.OAK_FENCE);
		this.put(f, 8, 2, 8, Blocks.HAY_BLOCK);
		this.put(f, 8, 3, 8, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
	}

	/** A fenced pen of two kinds of farm animal, with a gate to the road, a trough, hay and a little shelter in the corner. */
	private void pen(Frame f) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean side = x == 0 || x == 8, end = z == 0 || z == 8;
				if (!side && !end) {
					continue;
				}
				if (x == 4 && z == 0) {
					this.put(f, x, 1, z, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
					continue;
				}
				List<Direction> joins = new ArrayList<>();
				if (side && z > 0) {
					joins.add(f.front());
				}
				if (side && z < 8) {
					joins.add(f.back());
				}
				if (end && x > 0) {
					joins.add(f.right().getOpposite());
				}
				if (end && x < 8) {
					joins.add(f.right());
				}
				this.put(f, x, 1, z, fence(Blocks.OAK_FENCE, joins.toArray(Direction[]::new)));
			}
		}
		for (int[] post : new int[][] {{6, 6}, {8, 6}, {6, 8}, {8, 8}}) {
			this.put(f, post[0], 1, post[1], Blocks.OAK_FENCE);
			this.put(f, post[0], 2, post[1], Blocks.OAK_FENCE);
		}
		for (int x = 6; x <= 8; x++) {
			for (int z = 6; z <= 8; z++) {
				this.put(f, x, 3, z, Blocks.SPRUCE_SLAB);
			}
		}
		this.put(f, 7, 1, 7, Blocks.HAY_BLOCK);
		this.put(f, 1, 1, 7, Blocks.HAY_BLOCK);
		this.put(f, 1, 0, 1, Blocks.WATER);
		this.put(f, 2, 0, 1, Blocks.WATER);
		List<EntityType<? extends Mob>> animals = new ArrayList<>(List.of(EntityTypes.SHEEP, EntityTypes.COW, EntityTypes.PIG, EntityTypes.CHICKEN));
		for (int kinds = 0; kinds < 2; kinds++) {
			EntityType<? extends Mob> type = animals.remove(this.random.nextInt(animals.size()));
			for (int i = 2 + this.random.nextInt(2); i > 0; i--) {
				SkyIslandFeature.spawnVillager(this.island, type, f.at(2 + this.random.nextInt(5), 1, 2 + this.random.nextInt(4)), this.random);
			}
		}
	}

	/** A lookout tower of stone with a ladder up the inside to a railed platform, lanterns at its corners. */
	private void tower(Frame f) {
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean side = x == 0 || x == 4, end = z == 0 || z == 4;
				this.put(f, x, 0, z, Blocks.STONE_BRICKS);
				for (int y = 1; y <= 10; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (side && end) {
						state = log;
					} else if (side || end) {
						boolean window = (y == 4 || y == 8) && (x == 2 || z == 2);
						state = window ? Blocks.AIR.defaultBlockState() : (y <= 2 ? Blocks.COBBLESTONE
							: this.random.nextInt(6) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
					}
					this.put(f, x, y, z, state);
				}
				if (!(x == 2 && z == 3)) {
					this.put(f, x, 11, z, Blocks.SPRUCE_PLANKS);
				}
			}
		}
		this.door(f, 2, Blocks.SPRUCE_DOOR);
		for (int y = 1; y <= 11; y++) {
			this.put(f, 2, y, 3, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if (x == 0 || x == 4 || z == 0 || z == 4) {
					boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
					this.put(f, x, 12, z, Blocks.SPRUCE_FENCE);
					if (corner) {
						this.put(f, x, 13, z, Blocks.LANTERN);
					}
				}
			}
		}
		this.hangLantern(f, 2, 10, 1);
	}

	/**
	 * A dragon stable: a great timber barn under a steep dark roof, seventeen blocks across. A stall either side of
	 * the aisle, each open to the road so its dragon can come and go, bedded with hay, with a trough of water; a
	 * hayloft over the stalls up a ladder; and at the back of the aisle the tack room: a chest of riding gear, a
	 * saddle and a lead on the wall, and the cauldron of the leatherworker who keeps the saddles.
	 */
	private void stable(Frame f) {
		BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState();
		BlockState lintel = beam.setValue(RotatedPillarBlock.AXIS, f.right().getAxis());
		Wood roof = Wood.ROOFS[1];
		for (int x = 0; x < 17; x++) {
			for (int z = 0; z < 11; z++) {
				boolean side = x == 0 || x == 16, back = z == 10, frontWall = z == 0;
				boolean stall = (x >= 1 && x <= 5 || x >= 11 && x <= 15) && z >= 1 && z <= 9;
				float r = this.random.nextFloat();
				this.put(f, x, 0, z, side || back || frontWall ? Blocks.COBBLESTONE : stall
					? (r < 0.6F ? Blocks.COARSE_DIRT : r < 0.8F ? Blocks.ROOTED_DIRT : Blocks.PODZOL) : Blocks.STONE_BRICKS);
				boolean post = (x == 0 || x == 6 || x == 10 || x == 16) && (frontWall || back) || side && z == 5;
				for (int y = 1; y <= 6; y++) {
					BlockState state = Blocks.AIR.defaultBlockState();
					if (post) {
						state = beam;
					} else if (side || back) {
						boolean window = (y == 3 || y == 4) && (side ? z == 2 || z == 3 || z == 7 || z == 8 : x == 3 || x == 13);
						state = y == 1 ? Blocks.COBBLESTONE.defaultBlockState() : window ? pane(side ? f.back() : f.right())
							: Blocks.SPRUCE_PLANKS.defaultBlockState();
					} else if (frontWall) {
						boolean stallMouth = x >= 1 && x <= 5 || x >= 11 && x <= 15;
						state = stallMouth ? (y == 6 ? lintel : Blocks.AIR.defaultBlockState())
							: y == 5 ? lintel : y == 6 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					}
					this.put(f, x, y, z, state);
				}
				// The hayloft over the stalls and the partitions, level with the top of the walls.
				if (!side && !back && !frontWall && (x <= 6 || x >= 10)) {
					this.put(f, x, 6, z, Blocks.SPRUCE_PLANKS);
				}
			}
		}
		// The partitions between the stalls and the aisle: a fence with a gate into each stall.
		for (int x : new int[] {6, 10}) {
			for (int z = 1; z <= 9; z++) {
				if (z == 4) {
					this.put(f, x, 1, z, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.right()));
					continue;
				}
				BlockState rail = fence(Blocks.SPRUCE_FENCE, z > 1 ? f.front() : f.back(), z < 9 ? f.back() : f.front());
				this.put(f, x, 1, z, rail);
				this.put(f, x, 2, z, rail);
				if (z == 1 || z == 9) {
					this.put(f, x, 3, z, Blocks.LANTERN);
				}
				// The loft's railing over the aisle, with a gap where the ladder comes up.
				if (!(x == 6 && z == 9)) {
					this.put(f, x, 7, z, fence(Blocks.SPRUCE_FENCE, f.front(), f.back()));
				}
			}
		}
		for (int y = 1; y <= 7; y++) {
			this.put(f, 7, y, 9, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		}
		this.roof(f, 17, 11, 7, roof, Blocks.SPRUCE_PLANKS);
		// The hay door high in the front gable.
		for (int x = 7; x <= 9; x++) {
			this.put(f, x, 9, 0, Blocks.AIR);
			this.put(f, x, 10, 0, Blocks.AIR);
			this.put(f, x, 11, 0, lintel);
		}
		// Hay in the loft and in the stalls, a trough of water in each stall, a lantern hanging over each.
		for (int i = 10 + this.random.nextInt(6); i > 0; i--) {
			int x = this.random.nextBoolean() ? 1 + this.random.nextInt(5) : 11 + this.random.nextInt(5);
			int z = 1 + this.random.nextInt(9);
			this.put(f, x, 7, z, Blocks.HAY_BLOCK);
			if (this.random.nextInt(3) == 0) {
				this.put(f, x, 8, z, Blocks.HAY_BLOCK);
			}
		}
		for (int stall : new int[] {3, 13}) {
			int out = stall < 8 ? -2 : 2;
			this.put(f, stall - out, 1, 9, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			this.put(f, stall, 1, 9, Blocks.HAY_BLOCK);
			this.put(f, stall + out / 2, 1, 9, Blocks.HAY_BLOCK);
			this.put(f, stall, 1, 8, Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.right().getAxis()));
			this.hangLantern(f, stall, 5, 5);
		}
		// The tack room at the back of the aisle.
		this.put(f, 8, 1, 9, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
		SkyIslandFeature.fill(this.island, f.at(8, 1, 9), STABLE_LOOT, this.random);
		this.put(f, 9, 1, 9, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		this.put(f, 9, 1, 8, Blocks.CAULDRON);
		this.frame(f.at(8, 3, 9), f.front(), Items.SADDLE);
		this.frame(f.at(9, 3, 9), f.front(), Items.LEAD);
		// And the dragons: most stalls have one, sometimes still young.
		for (int stall : new int[] {3, 13}) {
			BlockPos at = f.at(stall, 1, 5);
			this.stalls.add(at);
			if (this.random.nextInt(7) == 0) {
				continue; // out for the day
			}
			DragonElement kind = SkyIslandFeature.roostGuardian(this.overcloud, this.random);
			float growth = this.random.nextInt(10) < 3 ? 0.25F + this.random.nextFloat() * 0.3F : 1F;
			float yaw = this.random.nextFloat() * 360F;
			this.island.then(at, world -> {
				DragonEntity dragon = Dragons.TYPES.get(kind).create(world.getLevel(), EntitySpawnReason.STRUCTURE);
				if (dragon != null) {
					dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
					if (growth < 1F) {
						dragon.setGrowth(growth);
					}
					dragon.setStableHome(at);
					world.addFreshEntity(dragon);
				}
			});
		}
	}

	/** An item frame on the wall behind {@code pos}, facing {@code facing}, holding {@code item}. */
	private void frame(BlockPos pos, Direction facing, Item item) {
		this.island.then(pos, world -> {
			ItemFrame frame = new ItemFrame(world.getLevel(), pos, facing);
			frame.setItem(new ItemStack(item), false);
			world.addFreshEntity(frame);
		});
	}

	// ---- The edge, trees and residents ----------------------------------------------------------------------

	/**
	 * A fence round the whole edge of the island, so nobody (villager, golem or cat) wanders off it, lit with a lantern
	 * every so often. It's joined up corner to corner, leaving no gap to slip through, and stops for the dock.
	 */
	private void rimFence() {
		int size = 2 * this.reach + 1;
		boolean[] fenced = new boolean[size * size];
		for (int dx = -this.reach; dx <= this.reach; dx++) {
			for (int dz = -this.reach; dz <= this.reach; dz++) {
				fenced[(dx + this.reach) * size + dz + this.reach] = this.island.isRim(dx, dz) && this.use(dx, dz) != ROAD;
			}
		}
		// Where the edge steps diagonally, put a post in the corner too.
		for (int dx = -this.reach + 1; dx < this.reach; dx++) {
			for (int dz = -this.reach + 1; dz < this.reach; dz++) {
				if (!fenced[(dx + this.reach) * size + dz + this.reach]) {
					continue;
				}
				for (int a = -1; a <= 1; a += 2) {
					for (int b = -1; b <= 1; b += 2) {
						int ox = dx + a, oz = dz + b;
						if (Math.abs(ox) > this.reach || Math.abs(oz) > this.reach || !fenced[(ox + this.reach) * size + oz + this.reach]
								|| fenced[(dx + a + this.reach) * size + dz + this.reach] || fenced[(dx + this.reach) * size + dz + b + this.reach]) {
							continue;
						}
						int px = dx + a, pz = dz;
						if (!this.island.has(px, pz) || this.island.inner(dx, dz + b) > this.island.inner(px, pz)) {
							px = dx;
							pz = dz + b;
						}
						if (this.island.has(px, pz) && this.use(px, pz) != ROAD) {
							fenced[(px + this.reach) * size + pz + this.reach] = true;
						}
					}
				}
			}
		}
		int posts = 0;
		for (int dx = -this.reach; dx <= this.reach; dx++) {
			for (int dz = -this.reach; dz <= this.reach; dz++) {
				if (!fenced[(dx + this.reach) * size + dz + this.reach]) {
					continue;
				}
				int y = this.island.surface(dx, dz) + 1;
				List<Direction> joins = new ArrayList<>();
				for (Direction side : Direction.Plane.HORIZONTAL) {
					int nx = dx + side.getStepX(), nz = dz + side.getStepZ();
					if (Math.abs(nx) <= this.reach && Math.abs(nz) <= this.reach && fenced[(nx + this.reach) * size + nz + this.reach]
							&& this.island.surface(nx, nz) + 1 == y) {
						joins.add(side);
					}
				}
				BlockPos pos = this.island.column(dx, y, dz);
				if (!this.island.isAir(pos) && !this.island.get(pos).canBeReplaced()) {
					continue;
				}
				this.set(pos, fence(Blocks.OAK_FENCE, joins.toArray(Direction[]::new)));
				this.mark(dx, dz, BUILT);
				if (++posts % 11 == 0) {
					this.set(pos.above(), Blocks.LANTERN);
				}
			}
		}
	}

	/** Trees dotted about the open meadow, never crowding a road, a building or each other. */
	private void trees() {
		int wanted = 18 + this.random.nextInt(10);
		List<BlockPos> planted = new ArrayList<>();
		for (int attempt = 0; attempt < 600 && planted.size() < wanted; attempt++) {
			int dx = this.random.nextInt(2 * this.reach + 1) - this.reach;
			int dz = this.random.nextInt(2 * this.reach + 1) - this.reach;
			if (this.island.inner(dx, dz) < 0.15F || dx * dx + dz * dz < 100 || !this.island.isOpenGround(dx, dz)) {
				continue;
			}
			boolean roomy = true;
			for (int a = -3; a <= 3 && roomy; a++) {
				for (int b = -3; b <= 3 && roomy; b++) {
					roomy = this.use(dx + a, dz + b) <= (Math.abs(a) <= 1 && Math.abs(b) <= 1 ? FREE : CLEAR);
				}
			}
			BlockPos ground = this.island.ground(dx, dz);
			if (!roomy || planted.stream().anyMatch(other -> other.distSqr(ground) < 49)) {
				continue;
			}
			planted.add(ground);
			SkyIslandFeature.tree(this.island, ground.above(), this.random);
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					this.mark(dx + a, dz + b, CLEAR);
				}
			}
		}
	}

	/** The village's guardians and pets, and its airship at the dock (most villages have one to lend). */
	private void residents(BlockPos mooring) {
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.IRON_GOLEM, this.island.column(0, this.floor + 1, 4), this.random);
		if (!this.stalls.isEmpty()) {
			BlockPos stall = this.stalls.get(0);
			int[] c = this.rel(stall);
			// Out in front of the first stable, keeping an eye on its dragons.
			SkyIslandFeature.spawnVillager(this.island, EntityTypes.IRON_GOLEM, this.island.ground(c[0], c[1]).above(), this.random);
		}
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.CAT, this.island.column(-3, this.floor + 1, 0), this.random);
		SkyIslandFeature.spawnVillager(this.island, EntityTypes.CAT, this.island.column(3, this.floor + 1, 0), this.random);
		if (this.random.nextInt(4) > 0) {
			BlockPos at = mooring.immutable();
			this.island.then(at, world -> AirshipEntity.spawnVillageShip(world, at));
		}
	}
}
