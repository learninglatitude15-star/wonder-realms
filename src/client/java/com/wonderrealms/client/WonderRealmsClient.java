package com.wonderrealms.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import com.wonderrealms.airship.Airships;
import com.wonderrealms.airship.CannonPayload;
import com.wonderrealms.beetle.Beetles;
import com.wonderrealms.dragon.BreathPayload;
import com.wonderrealms.dragon.DragonElement;
import com.wonderrealms.dragon.DragonEntity;
import com.wonderrealms.dragon.Dragons;
import com.wonderrealms.dragon.PowerPayload;
import com.wonderrealms.dragonfly.Dragonflies;
import com.wonderrealms.firefly.Fireflies;
import com.wonderrealms.grasshopper.Grasshoppers;
import com.wonderrealms.whale.SkyWhales;
import com.wonderrealms.miner.PixelMinerEntity;
import com.wonderrealms.miner.PixelMiners;
import com.wonderrealms.pixel.PixelMining;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class WonderRealmsClient implements ClientModInitializer {
	/** Breathe while riding a dragon; fire the cannon at an airship's helm. */
	private static final KeyMapping BREATH_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.wonderrealms.dragon_breath", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY));
	/** Unleash your dragon's special power while riding it. */
	private static final KeyMapping POWER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.wonderrealms.dragon_power", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY));

	@Override
	public void onInitializeClient() {
		for (DragonElement element : DragonElement.values()) {
			EntityRendererRegistry.register(Dragons.TYPES.get(element), context -> new DragonRenderer(context, element.id));
		}
		EntityRendererRegistry.register(Dragons.ELDER, context -> new DragonRenderer(context, "elder"));
		EntityRendererRegistry.register(Dragonflies.TYPE, DragonflyRenderer::new);
		EntityRendererRegistry.register(Grasshoppers.TYPE, GrasshopperRenderer::new);
		EntityRendererRegistry.register(Fireflies.TYPE, FireflyRenderer::new);
		EntityRendererRegistry.register(SkyWhales.TYPE, SkyWhaleRenderer::new);
		EntityRendererRegistry.register(SkyWhales.LEVIATHAN, StormLeviathanRenderer::new);
		EntityRendererRegistry.register(PixelMiners.TYPE, context -> new GeoEntityRenderer<PixelMinerEntity, LivingEntityRenderState>(context,
			new DefaultedEntityGeoModel<PixelMinerEntity>(WonderRealms.id("pixel_miner"))));
		EntityRendererRegistry.register(Beetles.TYPE, BeetleRenderer::new);
		EntityRendererRegistry.register(Airships.TYPE, AirshipRenderer::new);
		EntityRendererRegistry.register(Airships.CANNONBALL, ThrownItemRenderer::new);
		BlockEntityRenderers.register(PixelMining.BLOCK_ENTITY, context -> new PixelatedBlockRenderer());

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			// The pilot's keys steer the airship on their own client, which moves it and tells the server.
			if (mc.player != null && mc.player.getVehicle() instanceof AirshipEntity ship && ship.getControllingPassenger() == mc.player) {
				Input keys = mc.player.input.keyPresses;
				ship.setInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.sprint());
			}
			while (POWER_KEY.consumeClick()) {
				if (mc.player != null && mc.player.getVehicle() instanceof DragonEntity dragon && dragon.getControllingPassenger() == mc.player) {
					ClientPlayNetworking.send(PowerPayload.INSTANCE);
				}
			}
			while (BREATH_KEY.consumeClick()) {
				if (mc.player == null) {
					continue;
				}
				if (mc.player.getVehicle() instanceof DragonEntity) {
					ClientPlayNetworking.send(BreathPayload.INSTANCE);
				} else if (mc.player.getVehicle() instanceof AirshipEntity ship && ship.hasCannon() && ship.getControllingPassenger() == mc.player) {
					ClientPlayNetworking.send(CannonPayload.INSTANCE);
				}
			}
		});

		registerTooltips();
	}

	/** A line or two of help under each Wonder Realms item, from the "tooltip.wonderrealms.*" lang keys. */
	private static void registerTooltips() {
		Map<Item, String> tips = new IdentityHashMap<>();
		tips.put(TinyWorlds.SHRINKING_DRAUGHT, "shrinking_draught");
		tips.put(TinyWorlds.GROWTH_DRAUGHT, "growth_draught");
		tips.put(TinyWorlds.DEWDROP, "dewdrop");
		tips.put(PixelMiners.DIAMOND_PIXEL, "gem_pixel");
		tips.put(PixelMiners.EMERALD_PIXEL, "gem_pixel");
		tips.put(Airships.AIRSHIP_ITEM, "airship");
		tips.put(Airships.CANNON_ITEM, "airship_cannon");
		tips.put(TinyWorlds.SHRINK_RAY, "shrink_ray");
		tips.put(TinyWorlds.PIXEL_CHISEL, "pixel_chisel");
		tips.put(Dragons.WHISTLE, "dragon_whistle");
		tips.put(Dragons.DRAGON_SCALE, "dragon_scale");
		tips.put(Dragons.ELDER_SCALE, "elder_scale");
		tips.put(Dragons.DRAGON_HEART, "dragon_heart");
		tips.put(Dragons.ELDER_BLADE, "elder_blade");
		tips.put(Fireflies.JAR_ITEM, "firefly_jar");
		tips.put(Dragons.DRAGON_WINGS, "dragon_wings");
		for (Item piece : List.of(Dragons.DRAGONSCALE_HELMET, Dragons.DRAGONSCALE_CHESTPLATE, Dragons.DRAGONSCALE_LEGGINGS, Dragons.DRAGONSCALE_BOOTS)) {
			tips.put(piece, "dragonscale_armor");
		}
		Dragons.ARMOR_ITEMS.values().forEach(armor -> tips.put(armor, "dragon_armor"));
		Dragons.EGG_ITEMS.values().forEach(egg -> tips.put(egg, "dragon_egg"));
		tips.put(Items.DRAGON_EGG, "ender_egg"); // a hint: it hatches on chorus fruit
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			String key = tips.get(stack.getItem());
			if (key != null) {
				addLines(lines, key);
			}
		});
	}

	private static void addLines(List<Component> lines, String key) {
		for (int i = 1; i <= 4; i++) {
			String line = "tooltip.wonderrealms." + key + "." + i;
			Component text = Component.translatable(line);
			if (text.getString().equals(line)) {
				return; // no more lines for this item
			}
			lines.add(text.copy().withStyle(ChatFormatting.GRAY));
		}
	}
}
