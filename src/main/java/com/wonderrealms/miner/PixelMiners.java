package com.wonderrealms.miner;

import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderRealms;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Pixel Miners: the tiny folk of the grass and stone. They appear around shrunken players (on the ground nearby, or
 * in the same cave) and trade dewdrops for what they dig: nuggets, redstone, and now and then a Diamond or Emerald
 * Pixel. Nine pixels make a whole gem.
 */
public final class PixelMiners {
	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("pixel_miner"));
	public static final EntityType<PixelMinerEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
		EntityType.Builder.of(PixelMinerEntity::new, MobCategory.CREATURE)
			.sized(0.5F, 1.0F) // shrunk by the scale attribute to about a shrunken player's size
			.eyeHeight(0.85F)
			.clientTrackingRange(4)
			.build(KEY));

	public static final Item DIAMOND_PIXEL = item("diamond_pixel", new Item.Properties());
	public static final Item EMERALD_PIXEL = item("emerald_pixel", new Item.Properties());
	public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, itemKey("pixel_miner_spawn_egg"),
		new SpawnEggItem(new Item.Properties().setId(itemKey("pixel_miner_spawn_egg")).spawnEgg(TYPE)));

	/** Miners kept around each shrunken player. */
	private static final int CROWD = 4;
	private static final int SPAWN_RADIUS = 6;
	private static final int SPAWN_INTERVAL = 200;

	private PixelMiners() {
	}

	private static ResourceKey<Item> itemKey(String name) {
		return ResourceKey.create(Registries.ITEM, WonderRealms.id(name));
	}

	private static Item item(String name, Item.Properties properties) {
		return Registry.register(BuiltInRegistries.ITEM, itemKey(name), new Item(properties.setId(itemKey(name))));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, PixelMinerEntity.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(DIAMOND_PIXEL);
			output.accept(EMERALD_PIXEL);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(SPAWN_EGG));
		ServerTickEvents.END_SERVER_TICK.register(PixelMiners::tick);
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
			int nearby = level.getEntitiesOfClass(PixelMinerEntity.class, new AABB(player.blockPosition()).inflate(16)).size();
			if (nearby < CROWD) {
				spawnNear(level, player.blockPosition(), level.getRandom());
			}
		}
	}

	/** Finds open footing within a few blocks at the player's level (works on the surface and in caves). */
	private static void spawnNear(ServerLevel level, BlockPos center, RandomSource random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			BlockPos column = center.offset(random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS, 0, random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);
			for (int dy = -2; dy <= 2; dy++) {
				BlockPos pos = column.above(dy);
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
					PixelMinerEntity miner = TYPE.create(level, EntitySpawnReason.EVENT);
					if (miner != null) {
						miner.snapTo(pos.getX() + random.nextDouble(), pos.getY(), pos.getZ() + random.nextDouble(), random.nextFloat() * 360F, 0F);
						level.addFreshEntity(miner);
					}
					return;
				}
			}
		}
	}

	/** What a miner pays for a dewdrop. */
	static ItemStack payment(RandomSource random) {
		int roll = random.nextInt(100);
		if (roll < 45) {
			return new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(4));
		} else if (roll < 70) {
			return new ItemStack(Items.GOLD_NUGGET, 1 + random.nextInt(3));
		} else if (roll < 85) {
			return new ItemStack(Items.REDSTONE, 2 + random.nextInt(3));
		} else if (roll < 97) {
			return new ItemStack(DIAMOND_PIXEL, 1 + random.nextInt(2));
		}
		return new ItemStack(EMERALD_PIXEL, 1 + random.nextInt(2));
	}
}
