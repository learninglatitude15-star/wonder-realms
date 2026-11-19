package com.wonderrealms.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;

/** The airship's hitbox is just the hull; keep drawing it while only the balloon overhead is in view. */
public class AirshipRenderer extends GeoEntityRenderer<AirshipEntity, EntityRenderState> {
	public AirshipRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<AirshipEntity>(WonderRealms.id("airship")));
	}

	@Override
	protected AABB getBoundingBoxForCulling(AirshipEntity ship, float partialTick) {
		return ship.getBoundingBox().inflate(1.5, 0, 1.5).expandTowards(0, 4.5, 0);
	}
}
