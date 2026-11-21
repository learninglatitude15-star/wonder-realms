package com.wonderrealms.firefly;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

/**
 * Fireflies: on warm, dry nights a few drift over the ground near every player out under the open sky, and slip
 * away at dawn. Catch one in a glass bottle for a Firefly Jar, a little lamp that never needs fuel.
 */
public final class Fireflies {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("firefly"));
	public static final EntityType<FireflyEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.of(FireflyEntity::new, MobCategory.AMBIENT)
			.sized(0.5F, 0.4F) // shrunk by the scale attribute to a little bug
			.eyeHeight(0.2F)
			.clientTrackingRange(6)
			.build(KEY));
	private static final ResourceKey<Item> SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("firefly_spawn_egg"));
	public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SPAWN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().setId(SPAWN_EGG_KEY).spawnEgg(TYPE)));

	private static final ResourceKey<Block> JAR_KEY = ResourceKey.create(Registries.BLOCK, WonderRealms.id("firefly_jar"));
	public static final Block JAR = Registry.register(BuiltInRegistries.BLOCK, JAR_KEY, new FireflyJarBlock(BlockBehaviour.Properties.of()
		.setId(JAR_KEY)
		.mapColor(MapColor.COLOR_YELLOW)
		.strength(0.3F)
		.lightLevel(state -> 13)
		.noOcclusion()
		.sound(SoundType.GLASS)
		.pushReaction(PushReaction.POPPED)));
	private static final ResourceKey<Item> JAR_ITEM_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("firefly_jar"));
	public static final Item JAR_ITEM = Registry.register(BuiltInRegistries.ITEM, JAR_ITEM_KEY,
		new BlockItem(JAR, new Item.Properties().setId(JAR_ITEM_KEY).useBlockDescriptionPrefix()));

	/** Fireflies kept around each player on a warm night, how near they gather, and how often to look. */
	private static final int GLOW = 8;
	private static final int SPAWN_RADIUS = 16;
	private static final int SPAWN_INTERVAL = 200;

	private Fireflies() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, FireflyEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(JAR_ITEM));
		ServerTickEvents.END_SERVER_TICK.register(Fireflies::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (player.isSpectator() || level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || level.isRaining()) {
				continue;
			}
			if (level.getBiome(player.blockPosition()).value().getBaseTemperature() < 0.5F) {
				continue; // too cold for fireflies
			}
			if (level.getEntitiesOfClass(FireflyEntity.class, new AABB(player.blockPosition()).inflate(32)).size() < GLOW) {
				spawnCloud(level, player.blockPosition(), level.getRandom());
			}
		}
	}

	/** Two to four fireflies, a little over open ground somewhere near the player. */
	private static void spawnCloud(ServerLevel level, BlockPos center, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = center.getX() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
			int z = center.getZ() + random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(ground - center.getY()) > 16) {
				continue; // a cliff, or the player is down a cave
			}
			BlockPos below = new BlockPos(x, ground - 1, z);
			if (!level.getFluidState(below).isEmpty() && !level.getFluidState(below).is(net.minecraft.tags.FluidTags.WATER)) {
				continue;
			}
			for (int i = 2 + random.nextInt(3); i > 0; i--) {
				FireflyEntity firefly = TYPE.create(level, EntitySpawnReason.EVENT);
				if (firefly != null) {
					firefly.snapTo(x + random.nextDouble() * 3 - 1.5, ground + 0.8 + random.nextDouble() * 2, z + random.nextDouble() * 3 - 1.5,
						random.nextFloat() * 360F, 0F);
					if (level.noCollision(firefly)) {
						level.addFreshEntity(firefly);
					}
				}
			}
			return;
		}
	}
}
