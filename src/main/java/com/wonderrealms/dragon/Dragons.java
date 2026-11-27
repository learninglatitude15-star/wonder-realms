package com.wonderrealms.dragon;

import net.minecraft.world.level.Level;
import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registers the dragons: one creature type per element and the Elder Dragon, eggs for the wild kinds, dragon armor,
 * the whistle, dragon scales and dragonscale armor, the Elder Dragon's scales and heart, and the breath and power
 * keys' network messages. The first wild dragons live on sky islands, guarding roosts or carrying pillagers (see
 * {@code SkyIslandFeature}); since 2.0, eight more live on the ground in the Overworld's biomes (the Echo dragon in
 * lairs in the Deep Dark), and eight in the Overcloud's (see its biomes' data). Eggs only come from dragons: kill a
 * wild adult for its egg, or tame one and it lays eggs. The End's Dragon Egg hatches an Ender Dragon when fed chorus
 * fruit.
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
	/** Shed by tamed dragons and dropped by wild ones; made into dragonscale armor. */
	public static final Item DRAGON_SCALE = item("dragon_scale", Item::new);
	/** A Clockwork dragon's scale: brass, with a tick to it. Eight round a clock make a Time Clock. */
	public static final Item CLOCKWORK_SCALE = item("clockwork_scale", Item::new);
	/** Stops time, and starts it again. */
	public static final Item TIME_CLOCK = item("time_clock", properties -> new TimeClockItem(properties.stacksTo(1).rarity(Rarity.EPIC)));
	/**
	 * Dragon Wings: a glider like an elytra, with wings of dragon membrane and scale. Made from dragon scales and phantom
	 * membranes, and mended with dragon scales.
	 */
	public static final ResourceKey<EquipmentAsset> DRAGON_WINGS_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, WonderRealms.id("dragon_wings"));
	public static final Item DRAGON_WINGS = item("dragon_wings", properties -> new Item(properties.durability(640).rarity(Rarity.RARE)
		.component(DataComponents.GLIDER, Unit.INSTANCE)
		.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
			.setAsset(DRAGON_WINGS_ASSET).setDamageOnHurt(false).build())
		.repairable(DRAGON_SCALE)));
	/** Dropped only by the Elder Dragon; made into Elder Dragon Armor. */
	public static final Item ELDER_SCALE = item("elder_scale", properties -> new Item(properties.fireResistant().rarity(Rarity.RARE)));
	/** The Elder Dragon's heart: give it to your own grown dragon to make it an Elder. */
	public static final Item DRAGON_HEART = item("dragon_heart", properties -> new Item(properties.stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

	/** Dragonscale armor for players: as strong as diamond, tougher, and a full set keeps off falls and fire. */
	public static final TagKey<Item> DRAGONSCALE_REPAIRS = TagKey.create(Registries.ITEM, WonderRealms.id("repairs_dragonscale_armor"));
	public static final ArmorMaterial DRAGONSCALE = new ArmorMaterial(35,
		Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
		15, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.5F, 0.05F, DRAGONSCALE_REPAIRS,
		ResourceKey.create(EquipmentAssets.ROOT_ID, WonderRealms.id("dragonscale")));
	public static final Item DRAGONSCALE_HELMET = item("dragonscale_helmet", properties -> new Item(properties.humanoidArmor(DRAGONSCALE, ArmorType.HELMET)));
	public static final Item DRAGONSCALE_CHESTPLATE = item("dragonscale_chestplate", properties -> new Item(properties.humanoidArmor(DRAGONSCALE, ArmorType.CHESTPLATE)));
	public static final Item DRAGONSCALE_LEGGINGS = item("dragonscale_leggings", properties -> new Item(properties.humanoidArmor(DRAGONSCALE, ArmorType.LEGGINGS)));
	public static final Item DRAGONSCALE_BOOTS = item("dragonscale_boots", properties -> new Item(properties.humanoidArmor(DRAGONSCALE, ArmorType.BOOTS)));

	/** The Elder Blade: a sword of Elder Scales that lets loose one of the Elder Dragon's breaths with every blow. */
	public static final TagKey<Item> ELDER_BLADE_REPAIRS = TagKey.create(Registries.ITEM, WonderRealms.id("repairs_elder_blade"));
	public static final ToolMaterial ELDER_TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 10F, 5F, 20, ELDER_BLADE_REPAIRS);
	public static final Item ELDER_BLADE = item("elder_blade",
		properties -> new ElderBladeItem(properties.sword(ELDER_TOOL, 3F, -2.4F).fireResistant().rarity(Rarity.EPIC)));

	/** The boss of the Sky Citadels. */
	private static final ResourceKey<EntityType<?>> ELDER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id("elder_dragon"));
	public static final EntityType<ElderDragonEntity> ELDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, ELDER_KEY,
		FabricEntityType.Builder.<ElderDragonEntity>createMob(ElderDragonEntity::new, MobCategory.MONSTER, mob -> mob)
			.sized(2.2F, 1.9F)
			.eyeHeight(1.6F)
			.fireImmune()
			.clientTrackingRange(16)
			.build(ELDER_KEY));
	public static final Item ELDER_SPAWN_EGG = item("elder_dragon_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(ELDER)));

	/** The Echo dragon's lairs in the Deep Dark (data/wonderrealms/worldgen/{feature,placed_feature}/echo_lair). */
	public static final ResourceKey<PlacedFeature> ECHO_LAIR = ResourceKey.create(Registries.PLACED_FEATURE, WonderRealms.id("echo_lair"));

	/** Chance each chorus fruit fed to the End's Dragon Egg hatches it. */
	private static final float CHORUS_HATCH_CHANCE = 0.25F;

	static {
		for (DragonElement element : DragonElement.values()) {
			ResourceKey<EntityType<?>> typeKey = ResourceKey.create(Registries.ENTITY_TYPE, WonderRealms.id(element.id + "_dragon"));
			EntityType.Builder<DragonEntity> builder = FabricEntityType.Builder.<DragonEntity>createMob((t, level) -> new DragonEntity(t, level, element),
					MobCategory.CREATURE, mob -> mob.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Dragons::canSpawn))
				.sized(2.2F, 1.9F)
				.eyeHeight(1.6F)
				.clientTrackingRange(10);
			if (element.fireproof()) {
				builder = builder.fireImmune(); // born of fire, or of the Nether
			}
			EntityType<DragonEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, typeKey, builder.build(typeKey));
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
				.mapColor(switch (element) {
					case EMBER -> MapColor.COLOR_RED;
					case FROST -> MapColor.ICE;
					case VERDANT -> MapColor.PLANT;
					case TIDE -> MapColor.WATER;
					case CRYSTAL -> MapColor.COLOR_MAGENTA;
					case SHADOW -> MapColor.COLOR_BLACK;
					case EARTH -> MapColor.TERRACOTTA_ORANGE;
					case HONEY -> MapColor.COLOR_YELLOW;
					case RAINBOW -> MapColor.COLOR_PINK;
					case AIR -> MapColor.SNOW;
					case SLIME -> MapColor.COLOR_LIGHT_GREEN;
					case MUSHROOM -> MapColor.COLOR_RED;
					case BLOSSOM -> MapColor.COLOR_PINK;
					case IRON -> MapColor.METAL;
					case BONE -> MapColor.QUARTZ;
					case JUNGLE -> MapColor.COLOR_GREEN;
					case ECHO -> MapColor.COLOR_CYAN;
					case CORAL -> MapColor.COLOR_ORANGE;
					case CHAMELEON -> MapColor.COLOR_LIGHT_GREEN;
					case SUN -> MapColor.GOLD;
					case MOON -> MapColor.COLOR_LIGHT_GRAY;
					case STAR -> MapColor.COLOR_BLUE;
					case AURORA -> MapColor.EMERALD;
					case PHOENIX -> MapColor.FIRE;
					case NIMBUS -> MapColor.COLOR_GRAY;
					case GOLD -> MapColor.GOLD;
					case PEARL -> MapColor.SNOW;
					default -> MapColor.COLOR_PURPLE;
				})
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
			ARMOR_ITEMS.put(armor, item(armor.id + "_dragon_armor", properties -> new Item(armor == DragonArmor.NETHERITE || armor == DragonArmor.ELDER
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
	 * Where a dragon may appear on its own: open, solid ground under the sky, one attempt in four. The first dragons
	 * only ever do through a dragon monster spawner; the 2.0 kinds spawn in their biomes as the world is made.
	 */
	private static boolean canSpawn(EntityType<DragonEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
			&& (level.canSeeSky(pos) || level.getLevel().dimension() == Level.NETHER) && random.nextInt(4) == 0; // the Nether has no sky
	}

	public static void init() {
		DragonEffects.init();
		DragonKinds.registerEvents();
		wildHomes();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> EGG_ITEMS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			SPAWN_EGGS.values().forEach(output::accept);
			output.accept(ELDER_SPAWN_EGG);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			ARMOR_ITEMS.values().forEach(output::accept);
			output.accept(DRAGONSCALE_HELMET);
			output.accept(DRAGONSCALE_CHESTPLATE);
			output.accept(DRAGONSCALE_LEGGINGS);
			output.accept(DRAGONSCALE_BOOTS);
			output.accept(ELDER_BLADE);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(WHISTLE);
			output.accept(DRAGON_WINGS);
			output.accept(TIME_CLOCK);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(DRAGON_SCALE);
			output.accept(CLOCKWORK_SCALE);
			output.accept(ELDER_SCALE);
			output.accept(DRAGON_HEART);
		});
		FabricDefaultAttributeRegistry.register(ELDER, ElderDragonEntity.createAttributes());

		PayloadTypeRegistry.serverboundPlay().register(BreathPayload.TYPE, BreathPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BreathPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof DragonEntity dragon && dragon.isOwnedBy(context.player())) {
				dragon.breathe(context.player().getLookAngle(), true);
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(PowerPayload.TYPE, PowerPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PowerPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof DragonEntity dragon && dragon.getControllingPassenger() == context.player()) {
				dragon.usePower(context.player());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Dragons::dragonscaleSets);

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

	/**
	 * Where the Overworld's own dragons live: each in its biomes, appearing (like horses or llamas) as new land is
	 * made, one at a time and none too often. The Echo dragon lives in lairs in the Deep Dark instead, out of the sky's
	 * reach. (The Overcloud's dragons are in its biomes' data.)
	 */
	private static void wildHomes() {
		home(DragonElement.SLIME, 5, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
		home(DragonElement.MUSHROOM, 6, Biomes.MUSHROOM_FIELDS);
		home(DragonElement.MUSHROOM, 3, Biomes.DARK_FOREST);
		home(DragonElement.BLOSSOM, 4, Biomes.CHERRY_GROVE);
		home(DragonElement.BLOSSOM, 2, Biomes.FLOWER_FOREST, Biomes.MEADOW);
		home(DragonElement.IRON, 4, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST, Biomes.STONY_PEAKS);
		home(DragonElement.BONE, 3, Biomes.DESERT, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS);
		home(DragonElement.BONE, 2, Biomes.PALE_GARDEN);
		home(DragonElement.JUNGLE, 5, Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE);
		home(DragonElement.CORAL, 4, Biomes.BEACH);
		home(DragonElement.CHAMELEON, 4, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		home(DragonElement.CHAMELEON, 3, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
		// Since 2.1: more of the Overworld's (the Abyss, Glow and Dripstone dragons have lairs: see MoreDragons)...
		home(DragonElement.ICE, 4, Biomes.ICE_SPIKES, Biomes.FROZEN_PEAKS);
		home(DragonElement.SNOW, 4, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.GROVE, Biomes.SNOWY_SLOPES);
		home(DragonElement.MUD, 5, Biomes.MANGROVE_SWAMP);
		home(DragonElement.VENOM, 3, Biomes.SWAMP, Biomes.SPARSE_JUNGLE);
		home(DragonElement.HARVEST, 3, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS);
		home(DragonElement.SAND, 3, Biomes.DESERT);
		home(DragonElement.TIGER, 4, Biomes.BAMBOO_JUNGLE, Biomes.JUNGLE);
		home(DragonElement.FANG, 4, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
		// ...the Nether's...
		home(DragonElement.MAGMA, 6, Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS);
		home(DragonElement.CRIMSON, 6, Biomes.CRIMSON_FOREST);
		home(DragonElement.WARPED, 6, Biomes.WARPED_FOREST);
		home(DragonElement.SOUL, 6, Biomes.SOUL_SAND_VALLEY);
		home(DragonElement.BASALT, 6, Biomes.BASALT_DELTAS);
		home(DragonElement.BLAZE, 5, Biomes.NETHER_WASTES);
		home(DragonElement.GHAST, 4, Biomes.SOUL_SAND_VALLEY, Biomes.NETHER_WASTES);
		// ...and the End's, out on its far islands.
		home(DragonElement.VOID, 4, Biomes.SMALL_END_ISLANDS, Biomes.END_BARRENS);
		home(DragonElement.CHORUS, 5, Biomes.END_HIGHLANDS);
		home(DragonElement.SHULKER, 4, Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS);
		home(DragonElement.NEBULA, 4, Biomes.END_MIDLANDS, Biomes.END_BARRENS);
		MoreDragons.lairs();
		Registry.register(BuiltInRegistries.FEATURE_TYPE, WonderRealms.id("echo_lair"), EchoLairFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.DEEP_DARK), GenerationStep.Decoration.UNDERGROUND_DECORATION, ECHO_LAIR);
	}

	@SafeVarargs
	private static void home(DragonElement element, int weight, ResourceKey<Biome>... biomes) {
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biomes), MobCategory.CREATURE, TYPES.get(element), weight, 1, 1);
	}

	/** A full set of dragonscale armor: falls don't hurt and fire can't burn. (And who's gliding on Dragon Wings.) */
	private static void dragonscaleSets(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (server.getTickCount() % 20 == 0 && player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).is(DRAGON_WINGS)) {
				WonderAdvancements.award(player, "dragon_wings");
			}
			if (player.getItemBySlot(EquipmentSlot.HEAD).is(DRAGONSCALE_HELMET) && player.getItemBySlot(EquipmentSlot.CHEST).is(DRAGONSCALE_CHESTPLATE)
					&& player.getItemBySlot(EquipmentSlot.LEGS).is(DRAGONSCALE_LEGGINGS) && player.getItemBySlot(EquipmentSlot.FEET).is(DRAGONSCALE_BOOTS)) {
				player.resetFallDistance();
				if (server.getTickCount() % 20 == 0) {
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
					WonderAdvancements.award(player, "dragonscale_armor");
				}
			}
		}
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
