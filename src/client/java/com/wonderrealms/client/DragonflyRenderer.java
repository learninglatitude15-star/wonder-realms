package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.dragonfly.DragonflyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a dragonfly, with its saddle if it has one. */
public class DragonflyRenderer extends GeoEntityRenderer<DragonflyEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_dragonfly_saddled", Boolean.class);

	public DragonflyRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(DragonflyEntity dragonfly, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragonfly, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, dragonfly.isSaddled());
	}

	static class Model extends DefaultedEntityGeoModel<DragonflyEntity> {
		private final Identifier skin = this.buildFormattedTexturePath(WonderRealms.id("dragonfly"));
		private final Identifier saddled = this.buildFormattedTexturePath(WonderRealms.id("dragonfly_saddled"));

		Model() {
			super(WonderRealms.id("dragonfly"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(SADDLED, false) ? this.saddled : this.skin;
		}
	}
}
