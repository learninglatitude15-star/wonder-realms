package com.wonderrealms.airship;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.wonderrealms.WonderRealms;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/** The airship: its entity type, the item that places it, its cannon and cannonballs, and the airships pillager outposts send up. */
public final class Airships {
	private static final ResourceKey<EntityType<?>> TYPE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("airship"));
	public static final EntityType<AirshipEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, TYPE_KEY,
		EntityType.Builder.<AirshipEntity>of(AirshipEntity::new, MobCategory.MISC)
			.sized(2.6F, 1.0F)
			.clientTrackingRange(10)
			.build(TYPE_KEY));

	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("airship"));
	public static final Item AIRSHIP_ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new AirshipItem(new Item.Properties().setId(ITEM_KEY).stacksTo(1)));

	private static final ResourceKey<Item> CANNON_KEY = ResourceKey.create(Registries.ITEM, WonderRealms.id("airship_cannon"));
	/** Use on an airship to mount a cannon at its bow. */
	public static final Item CANNON_ITEM = Registry.register(BuiltInRegistries.ITEM, CANNON_KEY, new Item(new Item.Properties().setId(CANNON_KEY).stacksTo(1)));

	private static final ResourceKey<EntityType<?>> CANNONBALL_KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("cannonball"));
	public static final EntityType<Cannonball> CANNONBALL = Registry.register(BuiltInRegistries.ENTITY_TYPE, CANNONBALL_KEY,
		EntityType.Builder.<Cannonball>of(Cannonball::new, MobCategory.MISC)
			.sized(0.4F, 0.4F)
			.clientTrackingRange(8)
			.updateInterval(10)
			.build(CANNONBALL_KEY));

	private Airships() {
	}

	/** When each pillager outpost last launched an airship, so they restock every couple of days, not constantly. */
	private static final AttachmentType<Map<String, Long>> OUTPOST_LAUNCHES = AttachmentRegistry.create(WonderRealms.id("outpost_airships"),
		builder -> builder.initializer(HashMap::new).persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	private static final int OUTPOST_CHECK_TICKS = 600;
	private static final int OUTPOST_SEARCH_CHUNKS = 7;
	private static final double OUTPOST_RANGE = 112;
	private static final long OUTPOST_RESTOCK_TICKS = 48000;

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(AIRSHIP_ITEM);
			output.accept(CANNON_ITEM);
		});
		PayloadTypeRegistry.serverboundPlay().register(CannonPayload.TYPE, CannonPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CannonPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof AirshipEntity ship && ship.getControllingPassenger() == context.player()) {
				ship.fireCannon(context.player(), context.player().getLookAngle());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % OUTPOST_CHECK_TICKS == 0) {
				launchOutpostAirships(server.overworld());
			}
		});
	}

	/** A pillager outpost near a player sends up an airship (one per outpost every couple of days). */
	private static void launchOutpostAirships(ServerLevel level) {
		Holder<Structure> outpost = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.PILLAGER_OUTPOST);
		Map<String, Long> launches = new HashMap<>(level.getAttachedOrCreate(OUTPOST_LAUNCHES));
		boolean changed = false;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(outpost), player.blockPosition(), OUTPOST_SEARCH_CHUNKS, false);
			if (found == null) {
				continue;
			}
			BlockPos at = found.getFirst();
			if (Math.hypot(at.getX() - player.getX(), at.getZ() - player.getZ()) > OUTPOST_RANGE) {
				continue;
			}
			String key = at.getX() + "," + at.getZ();
			long now = level.getGameTime();
			if (now - launches.getOrDefault(key, Long.MIN_VALUE / 2) < OUTPOST_RESTOCK_TICKS) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at);
			if (AirshipEntity.spawnRaiderShip(level, ground.above(22), 3) != null) {
				launches.put(key, now);
				changed = true;
			}
		}
		if (changed) {
			level.setAttached(OUTPOST_LAUNCHES, launches);
		}
	}

	/** Places an airship on top of the clicked block, facing the same way as the player. */
	public static class AirshipItem extends Item {
		public AirshipItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos above = context.getClickedPos().relative(context.getClickedFace());
			AirshipEntity ship = TYPE.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
			if (ship == null) {
				return InteractionResult.FAIL;
			}
			float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0F;
			ship.snapTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, yaw, 0F);
			if (!level.noCollision(ship, ship.getBoundingBox())) {
				return InteractionResult.FAIL;
			}
			level.addFreshEntity(ship);
			context.getItemInHand().consume(1, context.getPlayer());
			return InteractionResult.SUCCESS;
		}
	}
}
