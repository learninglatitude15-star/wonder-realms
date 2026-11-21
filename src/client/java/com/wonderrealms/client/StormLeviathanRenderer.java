package com.wonderrealms.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.whale.StormLeviathanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws the Storm Leviathan: the sky whale's model and swim, in its own storm-dark skin, with its eyes and the
 * lightning scars along its back glowing full bright from storm_leviathan_glowmask.
 */
public class StormLeviathanRenderer extends GeoEntityRenderer<StormLeviathanEntity, LivingEntityRenderState> {
	// GeckoLib's render states only gain GeoRenderState at run time (a mixin), so the glow layer is built raw.
	@SuppressWarnings({"unchecked", "rawtypes"})
	public StormLeviathanRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.withRenderLayer((GeoRenderLayer) new AutoGlowingGeoLayer(this));
	}

	/** Its tail reaches far beyond its hitbox, so keep drawing it until the whole beast is out of view. */
	@Override
	protected AABB getBoundingBoxForCulling(StormLeviathanEntity leviathan, float partialTick) {
		return super.getBoundingBoxForCulling(leviathan, partialTick).inflate(leviathan.getBbWidth() * 0.7, 0.5, leviathan.getBbWidth() * 0.7);
	}

	static class Model extends DefaultedEntityGeoModel<StormLeviathanEntity> {
		private final Identifier skin = this.buildFormattedTexturePath(WonderRealms.id("storm_leviathan"));

		Model() {
			super(WonderRealms.id("sky_whale"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return this.skin;
		}
	}
}
