package com.wonderrealms.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.firefly.FireflyEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Draws a firefly, its tail glowing full bright from the firefly_glowmask texture, whatever the light. */
public class FireflyRenderer extends GeoEntityRenderer<FireflyEntity, LivingEntityRenderState> {
	// GeckoLib's render states only gain GeoRenderState at run time (a mixin), so the glow layer is built raw.
	@SuppressWarnings({"unchecked", "rawtypes"})
	public FireflyRenderer(EntityRendererProvider.Context context) {
		super(context, new DefaultedEntityGeoModel<>(WonderRealms.id("firefly")));
		this.withRenderLayer((GeoRenderLayer) new AutoGlowingGeoLayer(this));
	}
}
