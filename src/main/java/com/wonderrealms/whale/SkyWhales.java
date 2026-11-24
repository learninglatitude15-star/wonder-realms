package com.wonderrealms.whale;

import com.wonderrealms.WonderRealms;
import com.wonderrealms.overcloud.Overcloud;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Sky whales: they swim high over the Overworld, up where the sky islands float. Fly up there (on a dragon, an
 * airship or elytra, or climb a sky island) and now and then one comes swimming by.
 */
public final class SkyWhales {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("sky_whale"));
	public static final EntityType<SkyWhaleEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.of(SkyWhaleEntity::new, MobCategory.CREATURE)
			.sized(2.0F, 1.2F) // grown by the scale attribute to five blocks across
			.eyeHeight(0.7F)
			.clientTrackingRange(16)
			.build(KEY));
	private static final ResourceKey<Item> SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("sky_whale_spawn_egg"));
	public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SPAWN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().setId(SPAWN_EGG_KEY).spawnEgg(TYPE)));

	/** The boss of the thunderstorms. */
	private static final ResourceKey<EntityType<?>> LEVIATHAN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("storm_leviathan"));
	public static final EntityType<StormLeviathanEntity> LEVIATHAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, LEVIATHAN_KEY,
		EntityType.Builder.of(StormLeviathanEntity::new, MobCategory.MONSTER)
			.sized(2.0F, 1.2F) // grown by the scale attribute to eight blocks across
			.eyeHeight(0.7F)
			.fireImmune()
			.clientTrackingRange(20)
			.build(LEVIATHAN_KEY));
	private static final ResourceKey<Item> LEVIATHAN_EGG_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("storm_leviathan_spawn_egg"));
	public static final Item LEVIATHAN_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, LEVIATHAN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().setId(LEVIATHAN_EGG_KEY).spawnEgg(LEVIATHAN)));
	/** In a thunderstorm, the chance (each look) that the Leviathan comes hunting a player up in the sky, and how far off. */
	private static final int LEVIATHAN_CHANCE = 3, LEVIATHAN_NEAR = 256, LEVIATHAN_DISTANCE = 80;

	/** Players this high up are where the whales swim. */
	private static final int SKY_Y = 110;
	/** How often to look, the chance one turns up when none is near, and how far off they appear. */
	private static final int SPAWN_INTERVAL = 600;
	private static final float SPAWN_CHANCE = 0.35F;
	private static final int NEAR = 160, MIN_DISTANCE = 48, MAX_DISTANCE = 72;

	private SkyWhales() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, SkyWhaleEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LEVIATHAN, StormLeviathanEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.accept(SPAWN_EGG);
			output.accept(LEVIATHAN_SPAWN_EGG);
		});
		ServerTickEvents.END_SERVER_TICK.register(SkyWhales::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			boolean overcloud = level.dimension() == Overcloud.LEVEL; // the whales' own sky: they swim at every height there
			if (player.isSpectator() || !overcloud && (level.dimension() != Level.OVERWORLD || player.getY() < SKY_Y)) {
				continue;
			}
			RandomSource random = level.getRandom();
			if (level.isThundering() && level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(LEVIATHAN_CHANCE) == 0
					&& level.getEntitiesOfClass(StormLeviathanEntity.class, new AABB(player.blockPosition()).inflate(LEVIATHAN_NEAR)).isEmpty()) {
				spawnLeviathan(level, player, random);
			}
			if (random.nextFloat() < SPAWN_CHANCE
					&& level.getEntitiesOfClass(SkyWhaleEntity.class, new AABB(player.blockPosition()).inflate(NEAR)).isEmpty()) {
				spawnNear(level, player, random);
			}
		}
	}

	/** The Storm Leviathan, swimming in out of the storm a long way off and a little above the player. */
	private static void spawnLeviathan(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double x = player.getX() + Math.cos(angle) * LEVIATHAN_DISTANCE;
			double z = player.getZ() + Math.sin(angle) * LEVIATHAN_DISTANCE;
			double y = Mth.clamp(player.getY() + 12, SKY_Y, level.getMaxY() - 24);
			StormLeviathanEntity leviathan = LEVIATHAN.create(level, EntitySpawnReason.EVENT);
			if (leviathan == null) {
				return;
			}
			leviathan.snapTo(x, y, z, random.nextFloat() * 360F, 0F);
			if (level.noCollision(leviathan, leviathan.getBoundingBox().inflate(2))) {
				level.addFreshEntity(leviathan);
				return;
			}
		}
	}

	/** A whale out in open sky a few dozen blocks from the player, at about their height. */
	private static void spawnNear(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Mth.nextDouble(random, MIN_DISTANCE, MAX_DISTANCE);
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double y = Mth.clamp(player.getY() + random.nextInt(25) - 8, SKY_Y, level.getMaxY() - 24);
			SkyWhaleEntity whale = TYPE.create(level, EntitySpawnReason.EVENT);
			if (whale == null) {
				return;
			}
			whale.snapTo(x, y, z, random.nextFloat() * 360F, 0F);
			if (level.noCollision(whale, whale.getBoundingBox().inflate(2)) && !level.containsAnyLiquid(whale.getBoundingBox())) {
				level.addFreshEntity(whale);
				// Two in five whales have a calf swimming beside them.
				if (random.nextInt(5) < 2) {
					SkyWhaleEntity calf = TYPE.create(level, EntitySpawnReason.EVENT);
					if (calf != null) {
						calf.setCalf(true, whale.getUUID());
						calf.snapTo(x + 6, y + 1, z, whale.getYRot(), 0F);
						level.addFreshEntity(calf);
					}
				}
				return;
			}
		}
	}
}
