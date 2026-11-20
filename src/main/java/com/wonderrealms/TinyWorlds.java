package com.wonderrealms;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tiny Worlds: shrink to a tenth of your size and explore your own world at insect scale.
 * <ul>
 *   <li>Shrinking Draught: drink to become tiny for five minutes. Growth Draught ends it early.</li>
 *   <li>While tiny you climb walls by walking into them, and falls barely hurt.</li>
 *   <li>Bugs are bosses now: spiders, silverfish, bees and endermites hit you twice as hard, and beating one while
 *   tiny drops a hoard of loot.</li>
 *   <li>Grass hides dewdrops you can only find when you're small enough to see them.</li>
 *   <li>A Shrink Ray shrinks mobs for thirty seconds; a Pixel Chisel carves pixels at full size.</li>
 * </ul>
 */
public final class TinyWorlds {
	private static final int SHRINK_TICKS = 20 * 60 * 5;
	/** Arthropods hit a shrunken player this much harder. */
	public static final float BUG_DAMAGE_MULTIPLIER = 2.0F;
	private static final float DEWDROP_CHANCE = 0.4F;
	/** A shrunken mob hits anything full-sized this much more weakly. */
	public static final float SHRUNKEN_MOB_DAMAGE_MULTIPLIER = 0.25F;

	public static final Holder<MobEffect> SHRUNKEN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, WonderRealms.id("shrunken"), new ShrunkenEffect());

	public static final Item SHRINKING_DRAUGHT = item("shrinking_draught", p -> new Item(p
		.component(DataComponents.CONSUMABLE, Consumables.defaultDrink()
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(SHRUNKEN, SHRINK_TICKS)))
			.build())
		.usingConvertsTo(Items.GLASS_BOTTLE)
		.stacksTo(16)));
	public static final Item GROWTH_DRAUGHT = item("growth_draught", p -> new Item(p
		.component(DataComponents.CONSUMABLE, Consumables.defaultDrink()
			.onConsume(new RemoveStatusEffectsConsumeEffect(SHRUNKEN))
			.build())
		.usingConvertsTo(Items.GLASS_BOTTLE)
		.stacksTo(16)));
	public static final Item DEWDROP = item("dewdrop", p -> new Item(p
		.food(new FoodProperties(1, 0.1F, true), Consumable.builder()
			.consumeSeconds(0.6F)
			.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0)))
			.build())));
	/** Zaps a mob down to bug size for thirty seconds. */
	public static final Item SHRINK_RAY = item("shrink_ray", p -> new ShrinkRayItem(p.durability(64)));
	/** Carves pixels at full size. */
	public static final Item PIXEL_CHISEL = item("pixel_chisel", p -> new Item(p.durability(250)));

	private TinyWorlds() {
	}

	private static Item item(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, WonderRealms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static boolean isTiny(LivingEntity entity) {
		return entity.hasEffect(SHRUNKEN);
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(SHRINKING_DRAUGHT);
			output.accept(GROWTH_DRAUGHT);
			output.accept(DEWDROP);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(SHRINK_RAY);
			output.accept(PIXEL_CHISEL);
		});

		// Dewdrops cling to grass, too small to notice at normal size.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && isTiny(player) && isGrass(state) && server.getRandom().nextFloat() < DEWDROP_CHANCE) {
				drop(server, pos, new ItemStack(DEWDROP));
			}
		});

		// A bug beaten by a tiny player was a boss fight: it drops a hoard.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel server && entity.is(EntityTypeTags.ARTHROPOD)
					&& source.getEntity() instanceof ServerPlayer player && isTiny(player)) {
				bossLoot(server, entity.blockPosition(), server.getRandom());
				WonderAdvancements.award(player, "giant_slayer");
			}
		});
	}

	/**
	 * How much harder (or softer) a hit lands, given who's tiny. Full-sized bugs tower over a tiny target and hit it
	 * twice as hard; a mob shrunk by a Shrink Ray can only nibble at anything full-sized.
	 */
	public static float damageMultiplier(LivingEntity target, DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return 1F;
		}
		boolean targetTiny = isTiny(target);
		boolean attackerTiny = isTiny(attacker);
		if (attackerTiny && !targetTiny && !(attacker instanceof Player)) {
			return SHRUNKEN_MOB_DAMAGE_MULTIPLIER;
		}
		if (targetTiny && !attackerTiny && attacker.is(EntityTypeTags.ARTHROPOD)) {
			return BUG_DAMAGE_MULTIPLIER;
		}
		return 1F;
	}

	private static boolean isGrass(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
	}

	private static void bossLoot(ServerLevel level, BlockPos pos, RandomSource random) {
		drop(level, pos, new ItemStack(Items.STRING, 4 + random.nextInt(5)));
		drop(level, pos, new ItemStack(Items.SPIDER_EYE, 1 + random.nextInt(2)));
		drop(level, pos, new ItemStack(Items.COBWEB, 1 + random.nextInt(2)));
		if (random.nextFloat() < 0.35F) {
			drop(level, pos, new ItemStack(SHRINKING_DRAUGHT));
		}
		if (random.nextFloat() < 0.15F) {
			drop(level, pos, new ItemStack(Items.EMERALD, 1 + random.nextInt(3)));
		}
	}

	private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
		level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
	}
}
