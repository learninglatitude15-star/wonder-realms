package com.wonderrealms.overcloud;

import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The Overcloud: the realm above the sky. Its world is data (data/wonderrealms/dimension, dimension_type and
 * worldgen, written by tools/generate_overcloud.py): floating continents over a sea of clouds. This class holds its
 * blocks and items, and the way there and back.
 * <p>
 * Up: hold a Storm Eye high above the Overworld (y 300 and up) and the storm lifts you, and your dragon or airship
 * and everyone riding with you, into the Overcloud, where you come out on top of the clouds. Down: sneak to sink
 * through the cloud sea, and when you drop out of the bottom you fall back into the Overworld's sky, drifting.
 */
public final class Overcloud {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, WonderRealms.id("overcloud"));

	/** The top of the cloud sea (the dimension's sea level), where arrivals come out of the clouds. */
	public static final int CLOUD_TOP = 64;
	/** Hold a Storm Eye at least this high in the Overworld to rise, and from this lower height it starts to tug upward. */
	private static final int RISE_Y = 300, PULL_Y = 200;
	/** Below this in the Overcloud (under the cloud sea, which starts at the world's floor, y 48) you fall home. */
	private static final int FALL_Y = 40;
	/** Where in the Overworld's sky you come out, below the height that would lift you straight back up. */
	private static final int LAND_Y = 290;
	/** Long enough to drift from there all the way down into the deepest ravine (falling slowly runs about 10 blocks a second). */
	private static final int SLOW_FALL_TICKS = 20 * 45;

	private static final ResourceKey<Block> CLOUD_KEY = ResourceKey.create(Registries.BLOCK, WonderRealms.id("cloud"));
	public static final Block CLOUD = Registry.register(BuiltInRegistries.BLOCK, CLOUD_KEY, new CloudBlock(BlockBehaviour.Properties.of()
		.setId(CLOUD_KEY)
		.mapColor(MapColor.SNOW)
		.strength(0.2F)
		.sound(SoundType.WOOL)
		.isSuffocating((state, level, pos) -> false)
		.isRedstoneConductor((state, level, pos) -> false)));
	private static final ResourceKey<Item> CLOUD_ITEM_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("cloud"));
	public static final Item CLOUD_ITEM = Registry.register(BuiltInRegistries.ITEM, CLOUD_ITEM_KEY,
		new BlockItem(CLOUD, new Item.Properties().setId(CLOUD_ITEM_KEY).useBlockDescriptionPrefix()));

	/** The Storm Leviathan's eye (or one made from Elder Scales): the way up into the Overcloud. */
	private static final ResourceKey<Item> STORM_EYE_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("storm_eye"));
	public static final Item STORM_EYE = Registry.register(BuiltInRegistries.ITEM, STORM_EYE_KEY, new Item(new Item.Properties()
		.setId(STORM_EYE_KEY)
		.stacksTo(1)
		.rarity(Rarity.EPIC)
		.fireResistant()
		.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

	private Overcloud() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(CLOUD_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(STORM_EYE));
		ServerTickEvents.END_SERVER_TICK.register(Overcloud::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		ServerLevel overcloud = server.getLevel(LEVEL);
		if (overcloud == null) {
			return;
		}
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ResourceKey<Level> here = player.level().dimension();
			if (here == Level.OVERWORLD && player.getY() >= PULL_Y && holdsStormEye(player)) {
				if (player.getY() >= RISE_Y) {
					rise(player, overcloud);
				} else if (server.getTickCount() % 20 == 0) {
					// Getting close: tell them how much higher the storm is.
					int blocks = (int) Math.ceil(RISE_Y - player.getY());
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.overcloud_pull", blocks).withStyle(ChatFormatting.GRAY));
				}
			} else if (here == LEVEL && player.getY() < FALL_Y) {
				fall(player, server.overworld());
			}
		}
	}

	private static boolean holdsStormEye(ServerPlayer player) {
		return player.getMainHandItem().is(STORM_EYE) || player.getOffhandItem().is(STORM_EYE);
	}

	/** Through the storm and up: the player, or whatever they ride with everyone aboard, comes out on the clouds. */
	private static void rise(ServerPlayer player, ServerLevel overcloud) {
		Entity traveller = player.getRootVehicle();
		List<ServerPlayer> aboard = playersWith(traveller);
		Vec3 to = new Vec3(traveller.getX(), CLOUD_TOP + 0.1, traveller.getZ());
		traveller.teleport(new TeleportTransition(overcloud, to, Vec3.ZERO, traveller.getYRot(), traveller.getXRot(),
			TeleportTransition.PLACE_PORTAL_TICKET));
		overcloud.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.2F, 1.4F);
		overcloud.sendParticles(ParticleTypes.CLOUD, to.x, to.y + 1, to.z, 60, 1.5, 0.6, 1.5, 0.05);
		for (ServerPlayer rider : aboard) {
			rider.sendOverlayMessage(Component.translatable("message.wonderrealms.overcloud_rise").withStyle(ChatFormatting.AQUA));
			WonderAdvancements.award(rider, "overcloud");
		}
	}

	/**
	 * Out of the bottom of the cloud sea and down into the Overworld's sky, drifting so nobody has to land hard: the
	 * player, and whatever they ride (a mount that crosses worlds arrives as a new entity, with its riders back on it).
	 */
	private static void fall(ServerPlayer player, ServerLevel overworld) {
		Entity traveller = player.getRootVehicle();
		List<ServerPlayer> aboard = playersWith(traveller);
		Vec3 to = new Vec3(traveller.getX(), LAND_Y, traveller.getZ());
		Entity arrived = traveller.teleport(new TeleportTransition(overworld, to, Vec3.ZERO, traveller.getYRot(), traveller.getXRot(),
			TeleportTransition.PLACE_PORTAL_TICKET));
		if (arrived == null) {
			return;
		}
		List<Entity> everyone = new ArrayList<>();
		everyone.add(arrived);
		arrived.getIndirectPassengers().forEach(everyone::add);
		for (Entity entity : everyone) {
			if (entity instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, true));
			}
		}
		for (ServerPlayer rider : aboard) {
			rider.sendOverlayMessage(Component.translatable("message.wonderrealms.overcloud_fall").withStyle(ChatFormatting.AQUA));
		}
	}

	private static List<ServerPlayer> playersWith(Entity traveller) {
		List<ServerPlayer> players = new ArrayList<>();
		if (traveller instanceof ServerPlayer player) {
			players.add(player);
		}
		for (Entity passenger : traveller.getIndirectPassengers()) {
			if (passenger instanceof ServerPlayer player) {
				players.add(player);
			}
		}
		return players;
	}
}
