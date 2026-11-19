package com.wonderrealms.pixel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pixels left of a block a tiny player has started carving: the block it was, and which of its 8x8x8 pixels
 * are still there. Synced to clients so they can draw it and collide with it.
 */
public class PixelatedBlockEntity extends BlockEntity {
	public static final int N = 8;
	public static final int VOLUME = N * N * N;

	private BlockState source = Blocks.STONE.defaultBlockState();
	/** One bit per pixel, set while it's still there. Index = x + z*8 + y*64. */
	private final long[] bits = new long[VOLUME / 64];
	private int removed;
	private boolean dropped;
	private VoxelShape shape;

	public PixelatedBlockEntity(BlockPos pos, BlockState state) {
		super(PixelMining.BLOCK_ENTITY, pos, state);
		java.util.Arrays.fill(this.bits, -1L);
	}

	public BlockState source() {
		return this.source;
	}

	public void setSource(BlockState source) {
		this.source = source;
	}

	public static int index(int x, int y, int z) {
		return x + z * N + y * N * N;
	}

	public boolean has(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) {
			return false;
		}
		int i = index(x, y, z);
		return (this.bits[i >> 6] & (1L << (i & 63))) != 0;
	}

	/** Removes a pixel. Returns false if it was already gone. */
	public boolean remove(int x, int y, int z) {
		if (!this.has(x, y, z)) {
			return false;
		}
		int i = index(x, y, z);
		this.bits[i >> 6] &= ~(1L << (i & 63));
		this.removed++;
		this.shape = null;
		return true;
	}

	public boolean isEmpty() {
		for (long word : this.bits) {
			if (word != 0) {
				return false;
			}
		}
		return true;
	}

	public long[] bits() {
		return this.bits;
	}

	public int removed() {
		return this.removed;
	}

	/** True the first time this is asked once half the block has been carved away: time to drop its loot. */
	public boolean claimDrops() {
		if (!this.dropped && this.removed >= VOLUME / 2) {
			this.dropped = true;
			return true;
		}
		return false;
	}

	/** True if the block's loot hasn't been given yet (used when a full-size player breaks it outright). */
	public boolean claimAllDrops() {
		if (this.dropped) {
			return false;
		}
		this.dropped = true;
		return true;
	}

	/** Collision/outline shape: runs of pixels along X merged into boxes, then joined. */
	public VoxelShape shape() {
		if (this.shape == null) {
			VoxelShape out = Shapes.empty();
			for (int y = 0; y < N; y++) {
				for (int z = 0; z < N; z++) {
					int x = 0;
					while (x < N) {
						if (!this.has(x, y, z)) {
							x++;
							continue;
						}
						int start = x;
						while (x < N && this.has(x, y, z)) {
							x++;
						}
						out = Shapes.joinUnoptimized(out, Block.box(start * 2, y * 2, z * 2, x * 2, y * 2 + 2, z * 2 + 2), BooleanOp.OR);
					}
				}
			}
			this.shape = out.optimize();
		}
		return this.shape;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("source", BlockState.CODEC, this.source);
		int[] words = new int[this.bits.length * 2];
		for (int i = 0; i < this.bits.length; i++) {
			words[i * 2] = (int) (this.bits[i] >>> 32);
			words[i * 2 + 1] = (int) this.bits[i];
		}
		output.putIntArray("pixels", words);
		output.putInt("removed", this.removed);
		output.putBoolean("dropped", this.dropped);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.source = input.read("source", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState());
		input.getIntArray("pixels").ifPresent(words -> {
			for (int i = 0; i < this.bits.length && i * 2 + 1 < words.length; i++) {
				this.bits[i] = ((long) words[i * 2] << 32) | (words[i * 2 + 1] & 0xFFFFFFFFL);
			}
		});
		this.removed = input.getIntOr("removed", 0);
		this.dropped = input.getBooleanOr("dropped", false);
		this.shape = null;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}
}
