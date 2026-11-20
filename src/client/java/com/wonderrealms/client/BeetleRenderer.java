package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.beetle.BeetleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a rhino beetle, with its saddle if it has one. */
public class BeetleRenderer extends GeoEntityRenderer<BeetleEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_beetle_saddled", Boolean.class);

	public BeetleRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(BeetleEntity beetle, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(beetle, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, beetle.isSaddled());
	}

	static class Model extends DefaultedEntityGeoModel<BeetleEntity> {
		private final Identifier skin = this.buildFormattedTexturePath(WonderRealms.id("rhino_beetle"));
		private final Identifier saddled = this.buildFormattedTexturePath(WonderRealms.id("rhino_beetle_saddled"));

		Model() {
			super(WonderRealms.id("rhino_beetle"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(SADDLED, false) ? this.saddled : this.skin;
		}
	}
}
