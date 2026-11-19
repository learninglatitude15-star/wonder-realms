package com.wonderrealms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wonderrealms.pixel.PixelatedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a pixelated block: every remaining pixel's exposed faces, textured with the matching patch of the original
 * block's texture so carved blocks still read as stone, dirt, wood and so on.
 */
public class PixelatedBlockRenderer implements BlockEntityRenderer<PixelatedBlockEntity, PixelatedBlockRenderer.State> {
	private static final int N = PixelatedBlockEntity.N;

	public static class State extends BlockEntityRenderState {
		BlockState source;
		long[] bits = new long[PixelatedBlockEntity.VOLUME / 64];
		int light;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PixelatedBlockEntity entity, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(entity, state, crumbling);
		state.source = entity.source();
		System.arraycopy(entity.bits(), 0, state.bits, 0, state.bits.length);
		state.light = brightestAround(entity.getLevel(), entity.getBlockPos());
	}

	/** Carved pixels are open to the air, so light them like the brightest open spot around the block. */
	private static int brightestAround(Level level, BlockPos pos) {
		if (level == null) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = level.getBrightness(LightLayer.SKY, pos);
		for (Direction dir : Direction.values()) {
			BlockPos side = pos.relative(dir);
			block = Math.max(block, level.getBrightness(LightLayer.BLOCK, side));
			sky = Math.max(sky, level.getBrightness(LightLayer.SKY, side));
		}
		return LightCoordsUtil.pack(block, sky);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.source == null) {
			return;
		}
		TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state.source).sprite();
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (pose, out) -> {
			for (int y = 0; y < N; y++) {
				for (int z = 0; z < N; z++) {
					for (int x = 0; x < N; x++) {
						if (!has(state.bits, x, y, z)) {
							continue;
						}
						for (Direction face : Direction.values()) {
							if (!has(state.bits, x + face.getStepX(), y + face.getStepY(), z + face.getStepZ())) {
								quad(out, pose, sprite, state.light, x, y, z, face);
							}
						}
					}
				}
			}
		});
	}

	private static boolean has(long[] bits, int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) {
			return false;
		}
		int i = PixelatedBlockEntity.index(x, y, z);
		return (bits[i >> 6] & (1L << (i & 63))) != 0;
	}

	/** One pixel face. Texture coordinates come from the face's position on the block, like a world-aligned texture. */
	private static void quad(VertexConsumer out, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, int x, int y, int z, Direction face) {
		float s = 1F / N;
		float x0 = x * s, y0 = y * s, z0 = z * s, x1 = x0 + s, y1 = y0 + s, z1 = z0 + s;
		float[][] corners = switch (face) {
			case DOWN -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
			case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
			case NORTH -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
			case SOUTH -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
			case WEST -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
			case EAST -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
		};
		float du = sprite.getU1() - sprite.getU0();
		float dv = sprite.getV1() - sprite.getV0();
		for (float[] c : corners) {
			// Pick the two in-plane axes for this face and map them onto the sprite.
			float a, b;
			switch (face.getAxis()) {
				case X -> { a = c[2]; b = 1F - c[1]; }
				case Y -> { a = c[0]; b = c[2]; }
				default -> { a = c[0]; b = 1F - c[1]; }
			}
			out.addVertex(pose, c[0], c[1], c[2])
				.setColor(0xFFFFFFFF)
				.setUv(sprite.getU0() + du * a, sprite.getV0() + dv * b)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
		}
	}
}
