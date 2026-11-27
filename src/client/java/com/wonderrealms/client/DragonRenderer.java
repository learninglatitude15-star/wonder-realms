package com.wonderrealms.client;

import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.HashMap;
import com.wonderrealms.dragon.DragonElement;
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
 * Draws a dragon. Every kind, and the Elder Dragon, shares one model and set of animations. Each has a skin for
 * every combination of armor (none, iron, gold, diamond, netherite, elder) and tack (none, a saddle, or a saddle with
 * saddlebags). The saddlebags are cubes in the model that only the "bags" skins paint; the rest leave them clear.
 */
public class DragonRenderer extends GeoEntityRenderer<DragonEntity, LivingEntityRenderState> {
	static final DataTicket<Integer> TACK = DataTicket.create("wonderrealms_tack", Integer.class);
	static final DataTicket<Integer> ARMOR = DataTicket.create("wonderrealms_armor", Integer.class);
	/** A Chameleon dragon's disguise: the ordinal of the kind it looks like, or -1. */
	static final DataTicket<Integer> DISGUISE = DataTicket.create("wonderrealms_disguise", Integer.class);
	/** What a Chameleon dragon matches when there's nothing beneath it but sky. */
	private static final int SKY = 0x8EC0FF;

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
		((GeoRenderState) state).addGeckolibData(DISGUISE, dragon.disguise() == null ? -1 : dragon.disguise().ordinal());
	}

	/**
	 * A Chameleon dragon's scales (painted pale, to take any colour) show the colour of whatever it's standing on, or of
	 * the sky when it's up in the air, changing slowly as it goes. Its saddle and armor blend in too. In disguise it
	 * simply looks like the kind it's copying.
	 */
	@Override
	public int getRenderColor(DragonEntity dragon, Void relatedObject, float partialTick) {
		int color = super.getRenderColor(dragon, relatedObject, partialTick);
		if (dragon.element() != DragonElement.CHAMELEON || dragon.disguise() != null) {
			return color;
		}
		int target = SKY;
		Level level = dragon.level();
		BlockPos feet = dragon.blockPosition();
		for (int down = 0; down <= 3; down++) {
			BlockPos pos = feet.below(down);
			BlockState below = level.getBlockState(pos);
			MapColor map = below.isAir() ? MapColor.NONE : below.getMapColor(level, pos);
			if (map != MapColor.NONE) {
				target = map.col;
				break;
			}
		}
		float[] shown = dragon.shownColor;
		float r = Math.min(1F, (target >> 16 & 255) / 255F * 1.12F), g = Math.min(1F, (target >> 8 & 255) / 255F * 1.12F),
			b = Math.min(1F, (target & 255) / 255F * 1.12F);
		if (shown[0] < 0F) {
			shown[0] = r;
			shown[1] = g;
			shown[2] = b;
		} else {
			shown[0] += (r - shown[0]) * 0.035F;
			shown[1] += (g - shown[1]) * 0.035F;
			shown[2] += (b - shown[2]) * 0.035F;
		}
		int alpha = color >>> 24;
		int red = Math.round((color >> 16 & 255) * shown[0]), green = Math.round((color >> 8 & 255) * shown[1]),
			blue = Math.round((color & 255) * shown[2]);
		return alpha << 24 | red << 16 | green << 8 | blue;
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

		/** Other kinds' skins, for a Chameleon dragon in disguise. */
		private final Map<Integer, Identifier[][]> disguises = new HashMap<>();

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			int armor = state.getOrDefaultGeckolibData(ARMOR, 0);
			int tack = state.getOrDefaultGeckolibData(TACK, 0);
			int disguise = state.getOrDefaultGeckolibData(DISGUISE, -1);
			Identifier[][] table = disguise >= 0 && disguise < DragonElement.values().length
				? this.disguises.computeIfAbsent(disguise, kind -> this.table(DragonElement.values()[kind].id)) : this.skins;
			return table[armor >= 0 && armor < table.length ? armor : 0][tack >= 0 && tack < TACK_SUFFIXES.length ? tack : 0];
		}

		private Identifier[][] table(String skin) {
			Identifier[][] table = new Identifier[DragonArmor.values().length + 1][TACK_SUFFIXES.length];
			for (int armor = 0; armor < table.length; armor++) {
				String name = skin + "_dragon" + (armor == 0 ? "" : "_" + DragonArmor.byIndex(armor).id);
				for (int tack = 0; tack < TACK_SUFFIXES.length; tack++) {
					table[armor][tack] = this.buildFormattedTexturePath(WonderRealms.id(name + TACK_SUFFIXES[tack]));
				}
			}
			return table;
		}
	}
}
