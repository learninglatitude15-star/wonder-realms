package com.wonderrealms.sky;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A whole sky village, planned block by block before any of it is built: the world builds it a chunk at a time, each
 * chunk copying out its own part (see {@link #apply}), so the village comes out the same whichever chunk is made first.
 * Anything that needs the real world (chests' loot, flowing water, villagers, dragons...) waits in a list until the
 * chunk it belongs to is built.
 */
final class VillageBuffer implements IslandCanvas {
	/** Cell value for a block the plan never touched: whatever's already there stays. */
	private static final short UNTOUCHED = 0;

	private final BoundingBox box;
	private final int sizeX;
	private final int sizeZ;
	private final short[] cells;
	private final List<BlockState> palette = new ArrayList<>();
	private final Object2IntOpenHashMap<BlockState> ids = new Object2IntOpenHashMap<>();
	private final List<Deferred> deferred = new ArrayList<>();

	private record Deferred(BlockPos pos, Consumer<WorldGenLevel> action) {
	}

	VillageBuffer(BoundingBox box) {
		this.box = box;
		this.sizeX = box.getXSpan();
		this.sizeZ = box.getZSpan();
		this.cells = new short[this.sizeX * box.getYSpan() * this.sizeZ];
		this.palette.add(null); // UNTOUCHED
		this.ids.defaultReturnValue(-1);
	}

	BoundingBox box() {
		return this.box;
	}

	private int index(int x, int y, int z) {
		return ((y - this.box.minY()) * this.sizeZ + (z - this.box.minZ())) * this.sizeX + (x - this.box.minX());
	}

	@Override
	public void set(BlockPos pos, BlockState state) {
		if (!this.box.isInside(pos)) {
			return;
		}
		int id = this.ids.getInt(state);
		if (id < 0) {
			id = this.palette.size();
			this.palette.add(state);
			this.ids.put(state, id);
		}
		this.cells[this.index(pos.getX(), pos.getY(), pos.getZ())] = (short) id;
	}

	@Override
	public BlockState get(BlockPos pos) {
		if (!this.box.isInside(pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		BlockState state = this.palette.get(this.cells[this.index(pos.getX(), pos.getY(), pos.getZ())]);
		return state != null ? state : Blocks.AIR.defaultBlockState(); // it's built in the open sky
	}

	@Override
	public void then(BlockPos pos, Consumer<WorldGenLevel> action) {
		this.deferred.add(new Deferred(pos.immutable(), action));
	}

	/** Builds the part of the village inside {@code area} (one chunk's worth), then everything waiting on it. */
	void apply(WorldGenLevel level, BoundingBox area) {
		if (!this.box.intersects(area)) {
			return;
		}
		int minX = Math.max(this.box.minX(), area.minX()), maxX = Math.min(this.box.maxX(), area.maxX());
		int minY = Math.max(this.box.minY(), area.minY()), maxY = Math.min(this.box.maxY(), area.maxY());
		int minZ = Math.max(this.box.minZ(), area.minZ()), maxZ = Math.min(this.box.maxZ(), area.maxZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					short id = this.cells[this.index(x, y, z)];
					if (id != UNTOUCHED) {
						level.setBlock(pos.set(x, y, z), this.palette.get(id), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		for (Deferred job : this.deferred) {
			if (area.isInside(job.pos()) && this.box.isInside(job.pos())) {
				job.action().accept(level);
			}
		}
	}
}
