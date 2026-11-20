package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registers the dragons: one creature type per element, eggs for the wild kinds, dragon armor, the whistle, and
 * the breath key's network message. Wild dragons live only on sky islands, guarding roosts or carrying pillagers (see
 * {@code SkyIslandFeature}). Eggs only come from dragons: kill a wild adult for its egg, or tame one and it lays
 * eggs. The End's Dragon Egg hatches an Ender Dragon when fed chorus fruit.
 */
public final class Dragons {
	public static final Map<DragonElement, EntityType<DragonEntity>> TYPES = new EnumMap<>(DragonElement.class);
	public static final Map<DragonElement, DragonEggBlock> EGGS = new EnumMap<>(DragonElement.class);
	public static final Map<DragonElement, Item> EGG_ITEMS = new EnumMap<>(DragonElement.class);
	/** Creative-mode spawn eggs: a wild, grown dragon of each kind. */
	public static final Map<DragonElement, Item> SPAWN_EGGS = new EnumMap<>(DragonElement.class);
	/** Dragon armor, one item per material. */
	public static final Map<DragonArmor, Item> ARMOR_ITEMS = new EnumMap<>(DragonArmor.class);
	/** Calls your dragons to you (sneak to make them sit). */
	public static final Item WHISTLE = item("dragon_whistle", properties -> new DragonWhistleItem(properties.stacksTo(1)));

	/** Chance each chorus fruit fed to the End's Dragon Egg hatches it. */
	private static final float CHORUS_HATCH_CHANCE = 0.25F;

	static {
		for (DragonElement element : DragonElement.values()) {
			ResourceKey<EntityType<?>> typeKey = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id(element.id + "_dragon"));
			EntityType<DragonEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, typeKey,
				FabricEntityType.Builder.<DragonEntity>createMob((t, level) -> new DragonEntity(t, level, element), MobCategory.CREATURE,
						mob -> mob.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Dragons::canSpawn))
					.sized(2.2F, 1.9F)
					.eyeHeight(1.6F)
					.clientTrackingRange(10)
					.build(typeKey));
			TYPES.put(element, type);
			FabricDefaultAttributeRegistry.register(type, DragonEntity.createAttributes());
			ResourceKey<Item> spawnEggKey = ResourceKey.create(Registries.ITEM, WonderRealms.id(element.id + "_dragon_spawn_egg"));
			SPAWN_EGGS.put(element, Registry.register(BuiltInRegistries.ITEM, spawnEggKey,
				new SpawnEggItem(new Item.Properties().setId(spawnEggKey).spawnEgg(type))));

			if (!element.hasOwnEgg()) {
				continue;
			}
			ResourceKey<Block> eggKey = ResourceKey.create(Registries.BLOCK, WonderRealms.id(element.id + "_egg"));
			DragonEggBlock egg = Registry.register(BuiltInRegistries.BLOCK, eggKey, new DragonEggBlock(element, BlockBehaviour.Properties.of()
				.setId(eggKey)
				.mapColor(element == DragonElement.EMBER ? MapColor.COLOR_RED : element == DragonElement.FROST ? MapColor.ICE
					: element == DragonElement.VERDANT ? MapColor.PLANT : MapColor.COLOR_PURPLE)
				.strength(3F, 9F)
				.lightLevel(state -> element.eggLight)
				.noOcclusion()
				.sound(SoundType.STONE)
				.pushReaction(PushReaction.IMMOVEABLE)));
			EGGS.put(element, egg);
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, WonderRealms.id(element.id + "_egg"));
			EGG_ITEMS.put(element, Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(egg, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1))));
		}
		for (DragonArmor armor : DragonArmor.values()) {
			ARMOR_ITEMS.put(armor, item(armor.id + "_dragon_armor", properties -> new Item(armor == DragonArmor.NETHERITE
				? properties.stacksTo(1).fireResistant() : properties.stacksTo(1))));
		}
	}

	private Dragons() {
	}

	private static <T extends Item> T item(String name, Function<Item.Properties, T> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, WonderRealms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** The egg a dragon of this element drops or lays: its own, or for an ender dragon, the End's Dragon Egg. */
	public static ItemStack eggOf(DragonElement element) {
		return new ItemStack(element.hasOwnEgg() ? EGG_ITEMS.get(element) : Items.DRAGON_EGG);
	}

	/**
	 * Dragons don't spawn naturally (they come from sky islands, eggs and spawn eggs). This only matters to things
	 * like a dragon monster spawner: open, solid ground under the sky, and one attempt in four.
	 */
	private static boolean canSpawn(EntityType<DragonEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& level.canSeeSky(pos) && random.nextInt(4) == 0;
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> EGG_ITEMS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> SPAWN_EGGS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> ARMOR_ITEMS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(WHISTLE));

		PayloadTypeRegistry.serverboundPlay().register(BreathPayload.TYPE, BreathPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BreathPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof DragonEntity dragon && dragon.isOwnedBy(context.player())) {
				dragon.breathe(context.player().getLookAngle(), true);
			}
		});

		// Feed the End's Dragon Egg chorus fruit to hatch an Ender Dragon. (Using it also stops the egg teleporting.)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			ItemStack stack = player.getItemInHand(hand);
			if (!level.getBlockState(pos).is(Blocks.DRAGON_EGG) || !stack.is(Items.CHORUS_FRUIT)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				stack.consume(1, player);
				server.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.3);
				server.playSound(null, pos, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 1F, 0.7F);
				if (server.getRandom().nextFloat() < CHORUS_HATCH_CHANCE) {
					hatchEnder(server, pos, player);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void hatchEnder(ServerLevel level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.6F, 1.8F);
		level.sendParticles(DragonElement.ENDER.breathParticle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.02);
		DragonEntity dragon = TYPES.get(DragonElement.ENDER).create(level, EntitySpawnReason.BREEDING);
		if (dragon == null) {
			return;
		}
		dragon.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180F, 0F);
		dragon.setGrowth(0F);
		dragon.setPersistenceRequired();
		dragon.tame(player);
		level.addFreshEntity(dragon);
		if (player instanceof ServerPlayer serverPlayer) {
			WonderAdvancements.award(serverPlayer, "hatch_ender_dragon");
		}
	}
}
