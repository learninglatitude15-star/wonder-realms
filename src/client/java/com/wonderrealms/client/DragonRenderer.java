package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a dragon. All four kinds share one model and set of animations; each has its own skin, plus a second skin
 * with a saddle painted on that's used while it's wearing one.
 */
public class DragonRenderer extends GeoEntityRenderer<DragonEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_saddled", Boolean.class);

	public DragonRenderer(EntityRendererProvider.Context context, DragonElement element) {
		super(context, new Model(element));
	}

	@Override
	public void addRenderData(DragonEntity dragon, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, dragon.isSaddled());
	}

	static class Model extends DefaultedEntityGeoModel<DragonEntity> {
		private final Identifier skin;
		private final Identifier saddledSkin;

		Model(DragonElement element) {
			super(WonderRealms.id("dragon"));
			this.skin = this.buildFormattedTexturePath(WonderRealms.id(element.id + "_dragon"));
			this.saddledSkin = this.buildFormattedTexturePath(WonderRealms.id(element.id + "_dragon_saddled"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(SADDLED, false) ? this.saddledSkin : this.skin;
		}
	}
}
