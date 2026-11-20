package com.wonderrealms.pixel;

import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pixel mining: when you're tiny, a block is a cliff ten times your height, so you don't break it in one go. The
 * first swing turns it into a pixelated block (8x8x8 pixels, each about half your height), and every break after
 * that chips out just the pixel you're aimed at. Carve tunnels, rooms and stairs inside a single block. Dig out
 * half a block and it drops what it normally would.
 */
public final class PixelMining {
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, WonderRealms.id("pixelated_block"));
	public static final PixelatedBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY, new PixelatedBlock(BlockBehaviour.Properties.of()
		.setId(BLOCK_KEY)
		.strength(1.5F, 6F)
		.noOcclusion()
		.dynamicShape()
		.noLootTable()
		.sound(SoundType.STONE)));
	public static final BlockEntityType<PixelatedBlockEntity> BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
		WonderRealms.id("pixelated_block"), FabricBlockEntityTypeBuilder.create(PixelatedBlockEntity::new, BLOCK).build());

	/** How far along the look a tiny player's chip can reach, in blocks. */
	private static final double REACH = 2.0;
	private static final double STEP = 0.01;
	/** Pixels chipped per point of tool durability used. */
	private static final int PIXELS_PER_DURABILITY = 32;

	/** At full size, a Pixel Chisel carves at least this fast (a pixel every few ticks), whatever the block. */
	private static final float CHISEL_MIN_PROGRESS = 0.25F;
	/** Pixels carve this many times faster than the whole block would break. */
	private static final float PIXEL_SPEEDUP = 8F;

	private PixelMining() {
	}

	/** Tiny players carve pixels with anything (even their fists); at full size you need a Pixel Chisel in hand. */
	public static boolean canCarve(Player player) {
		return TinyWorlds.isTiny(player) || player.getMainHandItem().is(TinyWorlds.PIXEL_CHISEL);
	}

	/** How fast a carver chips one pixel out of a block that would break whole at {@code wholeProgress} per tick. */
	public static float pixelProgress(Player player, float wholeProgress) {
		if (!canCarve(player)) {
			return wholeProgress;
		}
		float progress = wholeProgress * PIXEL_SPEEDUP;
		if (!TinyWorlds.isTiny(player)) {
			progress = Math.max(progress, CHISEL_MIN_PROGRESS);
		}
		return Math.min(1F, progress);
	}

	public static void init() {
		// Tiny players (and anyone holding a Pixel Chisel) turn a whole block into pixels the moment they start swinging at it.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level instanceof ServerLevel server && canCarve(player) && !player.isSpectator()) {
				BlockState state = server.getBlockState(pos);
				if (canPixelate(server, pos, state)) {
					pixelate(server, pos, state);
				}
			}
			return InteractionResult.PASS;
		});

		// Breaking a pixelated block only chips out the pixel being looked at.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server)) {
				return true;
			}
			if (blockEntity instanceof PixelatedBlockEntity pixels) {
				if (canCarve(player)) {
					chip(server, player, pos, pixels);
				} else {
					// Full-size players just break the whole carved block, with its drops if not already given.
					if (!player.isCreative() && pixels.claimAllDrops()) {
						Block.dropResources(pixels.source(), server, pos, null, player, player.getMainHandItem());
					}
					server.destroyBlock(pos, false, player);
				}
				return false;
			}
			// A tiny player who somehow breaks a whole block (say, a block that was placed mid-swing) carves it too.
			if (canCarve(player) && canPixelate(server, pos, state)) {
				PixelatedBlockEntity pixels = pixelate(server, pos, state);
				if (pixels != null) {
					chip(server, player, pos, pixels);
				}
				return false;
			}
			return true;
		});
	}

	/** Plain full blocks only: no containers or other block entities, nothing unbreakable, no liquids or plants. */
	public static boolean canPixelate(Level level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.is(BLOCK) && !state.hasBlockEntity() && state.getFluidState().isEmpty()
			&& state.getDestroySpeed(level, pos) >= 0 && state.isCollisionShapeFullBlock(level, pos)
			&& !state.is(Blocks.BEDROCK);
	}

	private static PixelatedBlockEntity pixelate(ServerLevel level, BlockPos pos, BlockState source) {
		level.setBlock(pos, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof PixelatedBlockEntity pixels) {
			pixels.setSource(source);
			pixels.setChanged();
			level.sendBlockUpdated(pos, source, BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			return pixels;
		}
		return null;
	}

	/**
	 * The first remaining pixel of {@code pixels} along the player's line of sight, as {x, y, z}, or null. Shared by
	 * the server (which chips it) and the player's client (which predicts the chip so it shows up instantly).
	 */
	public static int[] findPixel(Player player, BlockPos pos, PixelatedBlockEntity pixels) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		int n = PixelatedBlockEntity.N;
		double reach = (TinyWorlds.isTiny(player) ? REACH : player.blockInteractionRange()) + 1.5;
		for (double d = 0; d <= reach; d += STEP) {
			Vec3 p = eye.add(look.scale(d));
			double lx = p.x - pos.getX(), ly = p.y - pos.getY(), lz = p.z - pos.getZ();
			if (lx < 0 || ly < 0 || lz < 0 || lx >= 1 || ly >= 1 || lz >= 1) {
				continue;
			}
			int x = (int) (lx * n), y = (int) (ly * n), z = (int) (lz * n);
			if (pixels.has(x, y, z)) {
				return new int[] {x, y, z};
			}
		}
		return null;
	}

	/** Chips out the first remaining pixel along the player's line of sight. */
	private static void chip(ServerLevel level, Player player, BlockPos pos, PixelatedBlockEntity pixels) {
		int[] pixel = findPixel(player, pos, pixels);
		if (pixel == null) {
			// Nothing in the line of sight (the client predicted a chip the server didn't see): resend the true state.
			BlockState state = level.getBlockState(pos);
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
			return;
		}
		int n = PixelatedBlockEntity.N;
		pixels.remove(pixel[0], pixel[1], pixel[2]);
		afterChip(level, player, pos, pixels,
			new Vec3(pos.getX() + (pixel[0] + 0.5) / n, pos.getY() + (pixel[1] + 0.5) / n, pos.getZ() + (pixel[2] + 0.5) / n));
	}

	private static void afterChip(ServerLevel level, Player player, BlockPos pos, PixelatedBlockEntity pixels, Vec3 at) {
		BlockState source = pixels.source();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, source), at.x, at.y, at.z, 4, 0.03, 0.03, 0.03, 0.02);
		SoundType sound = source.getSoundType();
		level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.4F, sound.getPitch() * 1.6F);

		if (player instanceof ServerPlayer serverPlayer) {
			WonderAdvancements.award(serverPlayer, TinyWorlds.isTiny(player) ? "carve_pixel" : "sculptor");
		}
		if (!player.isCreative() && pixels.removed() % PIXELS_PER_DURABILITY == 0) {
			player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
		if (!player.isCreative() && pixels.claimDrops()) {
			Block.dropResources(source, level, pos, null, player, player.getMainHandItem());
		}

		if (pixels.isEmpty()) {
			level.removeBlock(pos, false);
			level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.6F, sound.getPitch() * 1.4F);
			return;
		}
		pixels.setChanged();
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
	}
}
