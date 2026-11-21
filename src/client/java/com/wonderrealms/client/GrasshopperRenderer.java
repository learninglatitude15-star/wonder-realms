package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.grasshopper.GrasshopperEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a grasshopper, with its saddle if it has one. */
public class GrasshopperRenderer extends GeoEntityRenderer<GrasshopperEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_grasshopper_saddled", Boolean.class);

	public GrasshopperRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(GrasshopperEntity creature, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(creature, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, creature.isSaddled());
	}

	static class Model extends DefaultedEntityGeoModel<GrasshopperEntity> {
		private final Identifier skin = this.buildFormattedTexturePath(WonderRealms.id("grasshopper"));
		private final Identifier saddled = this.buildFormattedTexturePath(WonderRealms.id("grasshopper_saddled"));

		Model() {
			super(WonderRealms.id("grasshopper"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(SADDLED, false) ? this.saddled : this.skin;
		}
	}
}
