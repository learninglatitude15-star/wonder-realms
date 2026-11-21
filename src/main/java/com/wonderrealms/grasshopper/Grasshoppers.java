package com.wonderrealms.grasshopper;

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
 * Grasshoppers: the leapers of the tiny world. Like the beetles, they're about when you're shrunk: one or two hop
 * through the grass near every shrunken player who's out under the open sky.
 */
public final class Grasshoppers {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("grasshopper"));
	public static final EntityType<GrasshopperEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.of(GrasshopperEntity::new, MobCategory.CREATURE)
			.sized(0.75F, 0.55F) // shrunk by the scale attribute to a mount for a shrunken player
			.eyeHeight(0.4F)
			.clientTrackingRange(6)
			.build(KEY));
	private static final ResourceKey<Item> SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("grasshopper_spawn_egg"));
	public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SPAWN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().setId(SPAWN_EGG_KEY).spawnEgg(TYPE)));

	/** Grasshoppers kept around each shrunken player. */
	private static final int SWARM = 2;
	private static final int SPAWN_RADIUS = 8;
	private static final int SPAWN_INTERVAL = 350;

	private Grasshoppers() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, GrasshopperEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
		ServerTickEvents.END_SERVER_TICK.register(Grasshoppers::tick);
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
			if (level.getEntitiesOfClass(GrasshopperEntity.class, new AABB(player.blockPosition()).inflate(24)).size() < SWARM) {
				spawnNear(level, player.blockPosition(), level.getRandom());
			}
		}
	}

	/** Finds open grass, dirt or moss under the sky within a few blocks of the player. */
	private static void spawnNear(ServerLevel level, BlockPos center, RandomSource random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			BlockPos column = center.offset(random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS, 0, random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);
			for (int dy = -3; dy <= 3; dy++) {
				BlockPos ground = column.above(dy);
				BlockPos pos = ground.above();
				if (level.getBlockState(ground).is(BlockTags.SUPPORTS_VEGETATION) && level.getBlockState(pos).canBeReplaced()
						&& level.getBlockState(pos.above()).isAir() && level.canSeeSky(pos)) {
					GrasshopperEntity grasshopper = TYPE.create(level, EntitySpawnReason.EVENT);
					if (grasshopper != null) {
						grasshopper.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0F);
						level.addFreshEntity(grasshopper);
					}
					return;
				}
			}
		}
	}
}
