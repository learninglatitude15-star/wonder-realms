package com.wonderrealms.dragonfly;

import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderRealms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;

/**
 * Dragonflies: the fliers of the tiny world. Like the beetles, they're about when you're shrunk: one or two zip
 * around above the grass near every shrunken player who's out under the open sky.
 */
public final class Dragonflies {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("dragonfly"));
	public static final EntityType<DragonflyEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.of(DragonflyEntity::new, MobCategory.CREATURE)
			.sized(0.8F, 0.4F) // shrunk by the scale attribute to a mount for a shrunken player
			.eyeHeight(0.25F)
			.clientTrackingRange(6)
			.build(KEY));
	private static final ResourceKey<Item> SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("dragonfly_spawn_egg"));
	public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SPAWN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().setId(SPAWN_EGG_KEY).spawnEgg(TYPE)));

	/** Dragonflies kept around each shrunken player. */
	private static final int SWARM = 2;
	private static final int SPAWN_RADIUS = 8;
	private static final int SPAWN_INTERVAL = 400;

	private Dragonflies() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, DragonflyEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
		ServerTickEvents.END_SERVER_TICK.register(Dragonflies::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!TinyWorlds.isTiny(player) || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			if (level.getEntitiesOfClass(DragonflyEntity.class, new AABB(player.blockPosition()).inflate(24)).size() < SWARM) {
				spawnNear(level, player.blockPosition(), level.getRandom());
			}
		}
	}

	/** Finds open air a block or two over grass, dirt or moss, under the sky, within a few blocks of the player. */
	private static void spawnNear(ServerLevel level, BlockPos center, RandomSource random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			BlockPos column = center.offset(random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS, 0, random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);
			for (int dy = -3; dy <= 3; dy++) {
				BlockPos ground = column.above(dy);
				BlockPos pos = ground.above(1 + random.nextInt(2));
				if (level.getBlockState(ground).is(BlockTags.SUPPORTS_VEGETATION) && level.getBlockState(ground.above()).isAir()
						&& level.getBlockState(pos).isAir() && level.canSeeSky(pos)) {
					DragonflyEntity dragonfly = TYPE.create(level, EntitySpawnReason.EVENT);
					if (dragonfly != null) {
						dragonfly.snapTo(pos.getX() + random.nextDouble(), pos.getY() + 0.5, pos.getZ() + random.nextDouble(), random.nextFloat() * 360F, 0F);
						level.addFreshEntity(dragonfly);
					}
					return;
				}
			}
		}
	}
}
