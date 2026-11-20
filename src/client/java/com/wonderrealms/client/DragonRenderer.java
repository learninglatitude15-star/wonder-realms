package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.dragon.DragonArmor;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Draws a dragon. All five kinds share one model and set of animations. Each kind has a skin for every
 * combination of armor (none, iron, gold, diamond, netherite) and saddle (on or off).
 */
public class DragonRenderer extends GeoEntityRenderer<DragonEntity, LivingEntityRenderState> {
	static final DataTicket<Boolean> SADDLED = DataTicket.create("wonderrealms_saddled", Boolean.class);
	static final DataTicket<Integer> ARMOR = DataTicket.create("wonderrealms_armor", Integer.class);

	public DragonRenderer(EntityRendererProvider.Context context, DragonElement element) {
		super(context, new Model(element));
	}

	@Override
	public void addRenderData(DragonEntity dragon, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(SADDLED, dragon.isSaddled());
		((GeoRenderState) state).addGeckolibData(ARMOR, dragon.armor() == null ? 0 : dragon.armor().index());
	}

	static class Model extends DefaultedEntityGeoModel<DragonEntity> {
		/** [armor index (0 = none)][saddled ? 1 : 0] */
		private final Identifier[][] skins = new Identifier[DragonArmor.values().length + 1][2];

		Model(DragonElement element) {
			super(WonderRealms.id("dragon"));
			for (int armor = 0; armor < this.skins.length; armor++) {
				String name = element.id + "_dragon" + (armor == 0 ? "" : "_" + DragonArmor.byIndex(armor).id);
				this.skins[armor][0] = this.buildFormattedTexturePath(WonderRealms.id(name));
				this.skins[armor][1] = this.buildFormattedTexturePath(WonderRealms.id(name + "_saddled"));
			}
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			int armor = state.getOrDefaultGeckolibData(ARMOR, 0);
			boolean saddled = state.getOrDefaultGeckolibData(SADDLED, false);
			return this.skins[armor >= 0 && armor < this.skins.length ? armor : 0][saddled ? 1 : 0];
		}
	}
}
