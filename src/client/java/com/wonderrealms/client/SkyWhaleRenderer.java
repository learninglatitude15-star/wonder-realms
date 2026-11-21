package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.whale.SkyWhaleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** Draws a sky whale, with its saddle if it has one. */
public class SkyWhaleRenderer extends GeoEntityRenderer<SkyWhaleEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_sky_whale_saddled", Boolean.class);

	public SkyWhaleRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(SkyWhaleEntity creature, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(creature, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, creature.isSaddled());
	}

	/** Its tail reaches far beyond its hitbox, so keep drawing it until the whole whale is out of view. */
	@Override
	protected AABB getBoundingBoxForCulling(SkyWhaleEntity whale, float partialTick) {
		return super.getBoundingBoxForCulling(whale, partialTick).inflate(whale.getBbWidth() * 0.7, 0.5, whale.getBbWidth() * 0.7);
	}

	static class Model extends DefaultedEntityGeoModel<SkyWhaleEntity> {
		private final Identifier skin = this.buildFormattedTexturePath(WonderRealms.id("sky_whale"));
		private final Identifier saddled = this.buildFormattedTexturePath(WonderRealms.id("sky_whale_saddled"));

		Model() {
			super(WonderRealms.id("sky_whale"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(SADDLED, false) ? this.saddled : this.skin;
		}
	}
}
