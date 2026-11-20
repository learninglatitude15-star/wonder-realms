package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;

/**
 * Draws an airship: the classic striped balloon or one of sixteen dyed ones, and the model with a cannon at the bow
 * once one is mounted. Its hitbox is just the hull, so keep drawing it while only the balloon overhead is in view.
 */
public class AirshipRenderer extends GeoEntityRenderer<AirshipEntity, EntityRenderState> {
	static final DataTicket<Integer> COLOR = DataTicket.create("wonderrealms_balloon_color", Integer.class);
	static final DataTicket<Boolean> CANNON = DataTicket.create("wonderrealms_cannon", Boolean.class);

	public AirshipRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(AirshipEntity ship, Void relatedObject, EntityRenderState state, float partialTick) {
		super.addRenderData(ship, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(COLOR, ship.balloonColor());
		((GeoRenderState) state).addGeckolibData(CANNON, ship.hasCannon());
	}

	@Override
	protected AABB getBoundingBoxForCulling(AirshipEntity ship, float partialTick) {
		return ship.getBoundingBox().inflate(1.5, 0, 1.5).expandTowards(0, 4.5, 0);
	}

	static class Model extends DefaultedEntityGeoModel<AirshipEntity> {
		private final Identifier plainModel;
		private final Identifier cannonModel;
		private final Identifier classicSkin;
		private final Identifier[] dyedSkins = new Identifier[DyeColor.values().length];

		Model() {
			super(WonderRealms.id("airship"));
			this.plainModel = this.buildFormattedModelPath(WonderRealms.id("airship"));
			this.cannonModel = this.buildFormattedModelPath(WonderRealms.id("airship_cannon"));
			this.classicSkin = this.buildFormattedTexturePath(WonderRealms.id("airship"));
			for (DyeColor color : DyeColor.values()) {
				this.dyedSkins[color.getId()] = this.buildFormattedTexturePath(WonderRealms.id("airship_" + color.getSerializedName()));
			}
		}

		@Override
		public Identifier getModelResource(GeoRenderState state) {
			return state.getOrDefaultGeckolibData(CANNON, false) ? this.cannonModel : this.plainModel;
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			int color = state.getOrDefaultGeckolibData(COLOR, -1);
			return color >= 0 && color < this.dyedSkins.length ? this.dyedSkins[color] : this.classicSkin;
		}
	}
}
