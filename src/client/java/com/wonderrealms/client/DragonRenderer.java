package com.wonderrealms.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.dragon.DragonArmor;
import com.wonderrealms.dragon.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws a dragon. All twelve kinds, and the Elder Dragon, share one model and set of animations. Each has a skin for
 * every combination of armor (none, iron, gold, diamond, netherite, elder) and tack (none, a saddle, or a saddle with
 * saddlebags). The saddlebags are cubes in the model that only the "bags" skins paint; the rest leave them clear.
 */
public class DragonRenderer extends GeoEntityRenderer<DragonEntity, LivingEntityRenderState> {
	static final DataTicket<Integer> TACK = DataTicket.create("wonderrealms_tack", Integer.class);
	static final DataTicket<Integer> ARMOR = DataTicket.create("wonderrealms_armor", Integer.class);

	/** {@code skin} names the skins: the element's id, or "elder" for the Elder Dragon. */
	public DragonRenderer(EntityRendererProvider.Context context, String skin) {
		super(context, new Model(skin));
	}

	/**
	 * Spread, the wings reach almost three blocks either side of a grown dragon and beat up to three blocks high, well
	 * past its hitbox, so it must still be drawn when the hitbox is out of view.
	 */
	@Override
	protected AABB getBoundingBoxForCulling(DragonEntity dragon, float partialTick) {
		float out = dragon.getBbWidth() * 0.95F;
		return super.getBoundingBoxForCulling(dragon, partialTick).inflate(out, dragon.getBbHeight() * 0.55F, out);
	}

	@Override
	public void addRenderData(DragonEntity dragon, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, relatedObject, state, partialTick);
		((GeoRenderState) state).addGeckolibData(TACK, dragon.hasSaddlebags() ? 2 : dragon.isSaddled() ? 1 : 0);
		((GeoRenderState) state).addGeckolibData(ARMOR, dragon.armor() == null ? 0 : dragon.armor().index());
	}

	static class Model extends DefaultedEntityGeoModel<DragonEntity> {
		private static final String[] TACK_SUFFIXES = {"", "_saddled", "_saddled_bags"};
		/** [armor index (0 = none)][tack: 0 none, 1 saddle, 2 saddle and bags] */
		private final Identifier[][] skins = new Identifier[DragonArmor.values().length + 1][TACK_SUFFIXES.length];

		Model(String skin) {
			super(WonderRealms.id("dragon"));
			for (int armor = 0; armor < this.skins.length; armor++) {
				String name = skin + "_dragon" + (armor == 0 ? "" : "_" + DragonArmor.byIndex(armor).id);
				for (int tack = 0; tack < TACK_SUFFIXES.length; tack++) {
					this.skins[armor][tack] = this.buildFormattedTexturePath(WonderRealms.id(name + TACK_SUFFIXES[tack]));
				}
			}
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			int armor = state.getOrDefaultGeckolibData(ARMOR, 0);
			int tack = state.getOrDefaultGeckolibData(TACK, 0);
			return this.skins[armor >= 0 && armor < this.skins.length ? armor : 0][tack >= 0 && tack < TACK_SUFFIXES.length ? tack : 0];
		}
	}
}
