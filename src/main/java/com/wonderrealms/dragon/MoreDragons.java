package com.wonderrealms.dragon;

import com.mojang.serialization.MapCodec;
import com.wonderrealms.WonderRealms;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * The thirty dragons of 2.1: eleven more of the Overworld's wilds, the first dragons of the Nether and of the End, five
 * more of the Overcloud, and three found only guarding sky islands. Their breaths, gifts to their riders and powers;
 * the Abyss, Glow and Dripstone dragons' lairs; and the gifts that work when blows land (or are taken).
 */
public final class MoreDragons {
	/** Monsters near enough to a Tiger dragon's rider to be scared off, and wolves near enough to a Fang's to be rallied. */
	private static final double PACK_RANGE = 16;

	private MoreDragons() {
	}

	// ---- Breath ---------------------------------------------------------------------------------------------

	/** How much harder than usual a kind's breath hits. */
	static float breathDamageScale(DragonElement element) {
		return switch (element) {
			case DRIPSTONE -> 1.3F;
			case HAIL, TIGER -> 1.2F;
			default -> 1F;
		};
	}

	static void breathHit(DragonEntity dragon, ServerLevel level, LivingEntity target, Vec3 dir) {
		switch (dragon.breathElement()) {
			case ICE -> {
				target.setTicksFrozen(Math.min(target.getTicksFrozen() + 60, target.getTicksRequiredToFreeze() + 100));
				effect(target, dragon, MobEffects.SLOWNESS, 60, 1);
			}
			case SNOW -> {
				target.push(dir.x * 0.6, 0.2, dir.z * 0.6);
				effect(target, dragon, MobEffects.SLOWNESS, 40, 0);
			}
			case ABYSS -> effect(target, dragon, MobEffects.BLINDNESS, 50, 0);
			case GLOW -> {
				effect(target, dragon, MobEffects.GLOWING, 100, 0);
				effect(target, dragon, MobEffects.WEAKNESS, 60, 0);
			}
			case DRIPSTONE, HAIL -> effect(target, dragon, MobEffects.SLOWNESS, 50, 0);
			case MUD -> {
				effect(target, dragon, MobEffects.SLOWNESS, 60, 2);
				effect(target, dragon, MobEffects.MINING_FATIGUE, 60, 0);
			}
			case VENOM -> {
				if (!DragonKinds.undead(target)) {
					effect(target, dragon, MobEffects.POISON, 80, 1);
				}
			}
			case HARVEST -> {
				effect(target, dragon, MobEffects.HUNGER, 100, 1);
				effect(target, dragon, MobEffects.WEAKNESS, 60, 0);
			}
			case SAND -> {
				effect(target, dragon, MobEffects.BLINDNESS, 40, 0);
				target.push(dir.x * 0.3, 0.05, dir.z * 0.3);
			}
			case TIGER -> { // a roar that rattles them
				effect(target, dragon, MobEffects.WEAKNESS, 60, 0);
				target.push(dir.x * 0.5, 0.1, dir.z * 0.5);
			}
			case FANG -> {
				effect(target, dragon, MobEffects.WEAKNESS, 80, 0);
				effect(target, dragon, MobEffects.SLOWNESS, 40, 0);
			}
			case MAGMA -> {
				target.igniteForSeconds(4);
				effect(target, dragon, MobEffects.SLOWNESS, 40, 0);
			}
			case CRIMSON -> {
				effect(target, dragon, MobEffects.WEAKNESS, 80, 0);
				effect(target, dragon, MobEffects.HUNGER, 80, 0);
			}
			case WARPED -> {
				effect(target, dragon, MobEffects.NAUSEA, 80, 0);
				if (dragon.getRandom().nextInt(4) == 0 && target.getMaxHealth() <= 150F) {
					DragonKinds.blinkAway(level, target, 3);
				}
			}
			case SOUL -> {
				target.igniteForSeconds(3);
				effect(target, dragon, MobEffects.SLOWNESS, 40, 0);
			}
			case BASALT -> {
				effect(target, dragon, MobEffects.BLINDNESS, 40, 0);
				effect(target, dragon, MobEffects.SLOWNESS, 40, 0);
			}
			case BLAZE -> target.igniteForSeconds(5);
			case GHAST -> {
				effect(target, dragon, MobEffects.WEAKNESS, 80, 0);
				effect(target, dragon, MobEffects.SLOWNESS, 60, 0);
			}
			case VOID -> { // the void draws them in
				Vec3 in = dragon.position().subtract(target.position()).multiply(1, 0, 1);
				in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(0.5);
				target.push(in.x, 0.05, in.z);
				effect(target, dragon, MobEffects.WEAKNESS, 60, 0);
			}
			case CHORUS -> {
				effect(target, dragon, MobEffects.NAUSEA, 40, 0);
				if (dragon.getRandom().nextInt(3) == 0 && target.getMaxHealth() <= 150F) {
					DragonKinds.blinkAway(level, target, 5);
				}
			}
			case SHULKER -> {
				effect(target, dragon, MobEffects.LEVITATION, 30, 0);
				effect(target, dragon, MobEffects.GLOWING, 60, 0);
			}
			case NEBULA -> {
				effect(target, dragon, MobEffects.NAUSEA, 60, 0);
				effect(target, dragon, MobEffects.GLOWING, 60, 0);
			}
			case ECLIPSE -> {
				effect(target, dragon, MobEffects.DARKNESS, 80, 0);
				effect(target, dragon, MobEffects.BLINDNESS, 30, 0);
			}
			case PRISM -> {
				effect(target, dragon, MobEffects.BLINDNESS, 30, 0);
				effect(target, dragon, MobEffects.GLOWING, 80, 0);
			}
			case ZEPHYR -> target.push(dir.x * 0.9, 0.35, dir.z * 0.9);
			case PHANTOM -> {
				effect(target, dragon, MobEffects.BLINDNESS, 30, 0);
				effect(target, dragon, MobEffects.WEAKNESS, 60, 0);
			}
			case REDSTONE -> {
				effect(target, dragon, MobEffects.SLOWNESS, 40, 1);
				effect(target, dragon, MobEffects.MINING_FATIGUE, 60, 0);
			}
			case CLOCKWORK -> effect(target, dragon, MobEffects.SLOWNESS, 60, 2); // time drags for them
			case MELODY -> { // they can't help dancing
				effect(target, dragon, MobEffects.NAUSEA, 80, 0);
				if (target instanceof Mob mob && dragon.getRandom().nextInt(5) == 0) {
					mob.setTarget(null);
				}
			}
			default -> {
			}
		}
	}

	private static void effect(LivingEntity target, Entity from, Holder<MobEffect> effect, int ticks, int amplifier) {
		target.addEffect(new MobEffectInstance(effect, ticks, amplifier), from);
	}

	// ---- Gifts ----------------------------------------------------------------------------------------------

	/** What riding each kind does for its rider (topped up every second). Some work when blows land: see {@link #registerEvents}. */
	static void gift(DragonEntity dragon, ServerLevel level, Player rider) {
		switch (dragon.element()) {
			case ICE, HAIL -> rider.setTicksFrozen(0); // never feels the cold
			case SNOW -> gift(rider, dragon, MobEffects.JUMP_BOOST, 0);
			case ABYSS -> {
				gift(rider, dragon, MobEffects.WATER_BREATHING, 0);
				if (rider.isInWater() || dragon.isInWater()) {
					gift(rider, dragon, MobEffects.NIGHT_VISION, 0, 300);
				}
			}
			case GLOW, PHANTOM -> gift(rider, dragon, MobEffects.NIGHT_VISION, 0, 300);
			case HARVEST -> gift(rider, dragon, MobEffects.HERO_OF_THE_VILLAGE, 0);
			case FANG -> { // its rider's own wolves fight like a pack
				for (Wolf wolf : level.getEntitiesOfClass(Wolf.class, rider.getBoundingBox().inflate(PACK_RANGE), wolf -> wolf.isOwnedBy(rider))) {
					wolf.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false), dragon);
					wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false), dragon);
				}
			}
			case MAGMA -> gift(rider, dragon, MobEffects.FIRE_RESISTANCE, 0);
			case WARPED -> gift(rider, dragon, MobEffects.SPEED, 0);
			case BASALT -> gift(rider, dragon, MobEffects.RESISTANCE, 0);
			case VOID -> gift(rider, dragon, MobEffects.SLOW_FALLING, 0);
			case CHORUS -> gift(rider, dragon, MobEffects.JUMP_BOOST, 1);
			case SHULKER -> gift(rider, dragon, MobEffects.ABSORPTION, 0);
			case NEBULA -> gift(rider, dragon, MobEffects.LUCK, 0);
			case ECLIPSE -> {
				if (!level.isBrightOutside()) {
					gift(rider, dragon, MobEffects.STRENGTH, 1); // strongest in the dark
				}
			}
			case ZEPHYR -> gift(rider, dragon, MobEffects.SPEED, 1);
			case REDSTONE -> gift(rider, dragon, MobEffects.HASTE, 1);
			case CLOCKWORK -> {
				gift(rider, dragon, MobEffects.SPEED, 0);
				gift(rider, dragon, MobEffects.HASTE, 0);
			}
			case MELODY -> gift(rider, dragon, MobEffects.REGENERATION, 0);
			default -> {
			}
		}
	}

	private static void gift(Player rider, DragonEntity dragon, Holder<MobEffect> effect, int amplifier) {
		gift(rider, dragon, effect, amplifier, 60);
	}

	private static void gift(Player rider, DragonEntity dragon, Holder<MobEffect> effect, int amplifier, int ticks) {
		rider.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true), dragon);
	}

	/** Monsters that leave a rider alone: creepers and phantoms a Tiger's, piglins and hoglins a Crimson's, ghasts and blazes a Ghast's. */
	static boolean ignores(Mob mob, DragonEntity mount) {
		EntityType<?> type = mob.getType();
		return switch (mount.element()) {
			case TIGER -> type == EntityTypes.CREEPER || type == EntityTypes.PHANTOM;
			case CRIMSON -> type == EntityTypes.PIGLIN || type == EntityTypes.PIGLIN_BRUTE || type == EntityTypes.HOGLIN;
			case GHAST -> type == EntityTypes.GHAST || type == EntityTypes.BLAZE;
			case PHANTOM -> type == EntityTypes.PHANTOM;
			default -> false;
		};
	}

	/**
	 * Gifts that work when blows land: a Soul dragon's rider heals a little with each, a Blaze's sets foes alight, a
	 * Venom's poisons them, a Mud's slows them, a Sand's blinds them. And when its rider is hit: a Dripstone dragon's
	 * spikes the attacker back, a Hail's chills it, a Prism's dazzles it.
	 */
	static void registerEvents() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (blocked || taken <= 0F || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getEntity() instanceof Player rider && rider != victim && rider.getVehicle() instanceof DragonEntity mount) {
				switch (mount.element()) {
					case SOUL -> rider.heal(1F);
					case BLAZE -> victim.igniteForSeconds(4);
					case VENOM -> {
						if (!DragonKinds.undead(victim)) {
							effect(victim, rider, MobEffects.POISON, 60, 0);
						}
					}
					case MUD -> effect(victim, rider, MobEffects.SLOWNESS, 60, 1);
					case SAND -> effect(victim, rider, MobEffects.BLINDNESS, 40, 0);
					default -> {
					}
				}
			}
			if (victim instanceof Player rider && rider.getVehicle() instanceof DragonEntity mount && source.getEntity() instanceof LivingEntity attacker
					&& attacker != rider && attacker != mount) {
				switch (mount.element()) {
					case DRIPSTONE -> attacker.hurtServer(level, rider.damageSources().thorns(rider), 2F);
					case HAIL -> effect(attacker, rider, MobEffects.SLOWNESS, 60, 2);
					case PRISM -> {
						effect(attacker, rider, MobEffects.BLINDNESS, 60, 0);
						effect(attacker, rider, MobEffects.GLOWING, 100, 0);
					}
					default -> {
					}
				}
			}
		});
	}

	// ---- Every tick -----------------------------------------------------------------------------------------

	static void tick(DragonEntity dragon, ServerLevel level) {
		if (dragon.tickCount % 30 != 0) {
			return;
		}
		ParticleOptions glow = switch (dragon.element()) {
			case GLOW -> ParticleTypes.GLOW;
			case MAGMA -> ParticleTypes.LAVA;
			case SOUL -> ParticleTypes.SOUL;
			case BLAZE -> ParticleTypes.SMALL_FLAME;
			case NEBULA, VOID -> ParticleTypes.REVERSE_PORTAL;
			case MELODY -> ParticleTypes.NOTE;
			case REDSTONE -> dragon.element().breathParticle;
			default -> null;
		};
		if (glow != null) {
			level.sendParticles(glow, dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.7, dragon.getZ(), 1,
				dragon.getBbWidth() * 0.4, dragon.getBbHeight() * 0.3, dragon.getBbWidth() * 0.4, 0.01);
		}
	}

	// ---- Powers ---------------------------------------------------------------------------------------------

	/** The power key, for the thirty (see {@link DragonEntity#usePower}). {@code foes} are already chosen. */
	static void power(DragonEntity dragon, ServerLevel level, Player rider, List<LivingEntity> foes) {
		double x = dragon.getX(), y = dragon.getY() + dragon.getBbHeight() * 0.5, z = dragon.getZ();
		RandomSource random = dragon.getRandom();
		switch (dragon.element()) {
			case ICE -> { // Deep Freeze: every foe frozen stiff
				burst(level, dragon, ParticleTypes.SNOWFLAKE, 250, SoundEvents.PLAYER_HURT_FREEZE, 0.6F);
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().freeze(), 3F);
					foe.setTicksFrozen(foe.getTicksRequiredToFreeze() + 200);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 5);
				}
			}
			case SNOW -> allies(dragon, level, rider, foes, EntityTypes.SNOW_GOLEM, 3, ParticleTypes.ITEM_SNOWBALL); // Snow Army
			case ABYSS -> { // Whirlpool: every foe dragged in and down
				burst(level, dragon, ParticleTypes.BUBBLE_POP, 200, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.8F);
				for (LivingEntity foe : foes) {
					Vec3 in = dragon.position().subtract(foe.position()).multiply(1, 0, 1);
					in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize();
					foe.push(in.x * 1.1 + in.z * 0.6, -0.6, in.z * 1.1 - in.x * 0.6);
					foe.needsSync = true;
					dragon.powerHit(level, foe, dragon.damageSources().drown(), 4F);
					effect(foe, dragon, MobEffects.BLINDNESS, 40, 0);
				}
			}
			case GLOW -> { // Glow Burst: a flash that lights every foe up, and heals every friend
				burst(level, dragon, ParticleTypes.GLOW, 300, SoundEvents.GLOW_SQUID_SQUIRT, 0.7F);
				for (LivingEntity foe : foes) {
					effect(foe, dragon, MobEffects.GLOWING, 200, 0);
					effect(foe, dragon, MobEffects.BLINDNESS, 60, 0);
				}
				friends(dragon, friend -> friend.heal(4F));
			}
			case DRIPSTONE -> { // Stalactite Rain: stone spikes drop on every foe
				level.playSound(null, x, y, z, SoundEvents.POINTED_DRIPSTONE_FALL, SoundSource.NEUTRAL, 2F, 0.8F);
				for (LivingEntity foe : foes) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DRIPSTONE_BLOCK.defaultBlockState()),
						foe.getX(), foe.getY() + foe.getBbHeight() + 3, foe.getZ(), 30, 0.4, 1.5, 0.4, 0);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.POINTED_DRIPSTONE.defaultBlockState()),
						foe.getX(), foe.getY() + foe.getBbHeight(), foe.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
					dragon.powerHit(level, foe, dragon.damageSources().fallingStalactite(dragon), 6F);
					effect(foe, dragon, MobEffects.SLOWNESS, 60, 1);
				}
			}
			case MUD -> { // Mud Slide: every foe swept away and bogged down
				burst(level, dragon, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), 250, SoundEvents.MUD_BREAK, 0.6F);
				for (LivingEntity foe : foes) {
					away(dragon, foe, 1.0, 0.2);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 3);
					effect(foe, dragon, MobEffects.BLINDNESS, 40, 0);
				}
			}
			case VENOM -> { // Venom Cloud: a cloud of poison hangs over every foe
				burst(level, dragon, dragon.element().breathParticle, 150, SoundEvents.SPIDER_AMBIENT, 0.5F);
				for (LivingEntity foe : foes) {
					AreaEffectCloud cloud = new AreaEffectCloud(level, foe.getX(), foe.getY(), foe.getZ());
					cloud.setOwner(dragon);
					cloud.setRadius(2.5F);
					cloud.setDuration(160);
					cloud.setRadiusPerTick(-0.01F);
					cloud.setWaitTime(0);
					cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
					level.addFreshEntity(cloud);
				}
			}
			case HARVEST -> { // Harvest Moon: crops ripen all around, friends feast, foes go hungry
				burst(level, dragon, ParticleTypes.HAPPY_VILLAGER, 150, SoundEvents.CROP_PLANTED, 0.8F);
				BlockPos center = dragon.blockPosition();
				for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
						level.setBlock(pos, crop.getStateForAge(crop.getMaxAge()), Block.UPDATE_CLIENTS);
						level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
					}
				}
				friends(dragon, friend -> friend.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0), dragon));
				for (LivingEntity foe : foes) {
					effect(foe, dragon, MobEffects.HUNGER, 200, 2);
					effect(foe, dragon, MobEffects.WEAKNESS, 160, 0);
				}
			}
			case SAND -> { // Sandstorm: a whirling storm of sand round every foe
				burst(level, dragon, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), 350, SoundEvents.SAND_FALL, 0.6F);
				for (LivingEntity foe : foes) {
					Vec3 out = foe.position().subtract(dragon.position()).multiply(1, 0, 1);
					out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
					foe.push(-out.z * 0.9, 0.2, out.x * 0.9); // round and round
					foe.needsSync = true;
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 3F);
					effect(foe, dragon, MobEffects.BLINDNESS, 80, 0);
					effect(foe, dragon, MobEffects.SLOWNESS, 60, 0);
				}
			}
			case TIGER -> { // Pounce: a terrible roar, and every foe in reach is mauled
				burst(level, dragon, ParticleTypes.CRIT, 120, SoundEvents.RAVAGER_ROAR, 0.8F);
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 7F);
					effect(foe, dragon, MobEffects.SLOWNESS, 60, 1);
				}
			}
			case FANG -> allies(dragon, level, rider, foes, EntityTypes.WOLF, 2, ParticleTypes.POOF); // Howl: wolves answer the call
			case MAGMA -> { // Eruption: magma bursts up under every foe
				burst(level, dragon, ParticleTypes.LAVA, 120, SoundEvents.LAVA_POP, 0.6F);
				for (LivingEntity foe : foes) {
					level.sendParticles(ParticleTypes.LAVA, foe.getX(), foe.getY(), foe.getZ(), 25, 0.4, 0.2, 0.4, 0);
					level.sendParticles(ParticleTypes.FLAME, foe.getX(), foe.getY(), foe.getZ(), 40, 0.3, 1.2, 0.3, 0.05);
					dragon.powerHit(level, foe, dragon.damageSources().hotFloor(), 5F);
					foe.igniteForSeconds(8);
					launch(foe, 0.9);
				}
			}
			case CRIMSON -> { // Crimson Snare: roots and vines snare every foe; the dragon and its riders mend
				burst(level, dragon, ParticleTypes.CRIMSON_SPORE, 300, SoundEvents.WEEPING_VINES_PLACE, 0.7F);
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 3F);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 3);
					effect(foe, dragon, MobEffects.WEAKNESS, 100, 0);
				}
				friends(dragon, friend -> friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), dragon));
			}
			case WARPED -> { // Warp Field: every foe flung somewhere else, reeling
				burst(level, dragon, ParticleTypes.WARPED_SPORE, 300, SoundEvents.ENDERMAN_TELEPORT, 0.6F);
				for (LivingEntity foe : foes) {
					if (foe.getMaxHealth() <= 150F) {
						DragonKinds.blinkAway(level, foe, 10);
					}
					effect(foe, dragon, MobEffects.NAUSEA, 100, 0);
				}
			}
			case SOUL -> { // Soul Harvest: every foe drained, and the dragon and its riders made whole by it
				burst(level, dragon, ParticleTypes.SOUL, 120, SoundEvents.SOUL_ESCAPE.value(), 0.7F);
				for (LivingEntity foe : foes) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 15, 0.3, 0.4, 0.3, 0.02);
					dragon.powerHit(level, foe, dragon.damageSources().magic(), 4F);
				}
				float healing = Math.min(10F, 1.5F * foes.size());
				friends(dragon, friend -> friend.heal(healing));
			}
			case BASALT -> { // Basalt Burst: columns of rock heave up under every foe
				burst(level, dragon, ParticleTypes.LARGE_SMOKE, 120, SoundEvents.BASALT_BREAK, 0.5F);
				for (LivingEntity foe : foes) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), foe.getX(), foe.getY(), foe.getZ(),
						40, 0.4, 0.6, 0.4, 0.2);
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 5F);
					effect(foe, dragon, MobEffects.BLINDNESS, 40, 0);
					launch(foe, 0.8);
				}
			}
			case BLAZE -> { // Fireball Volley: a fireball for every foe (up to eight)
				level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 2F, 0.8F);
				Vec3 mouth = dragon.position().add(0, dragon.getBbHeight() * 0.8, 0).add(dragon.getLookAngle().scale(dragon.getBbWidth() * 0.6));
				for (LivingEntity foe : foes.subList(0, Math.min(8, foes.size()))) {
					Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.5, 0).subtract(mouth).normalize();
					SmallFireball fireball = new SmallFireball(level, dragon, to);
					fireball.setPos(mouth.x, mouth.y, mouth.z);
					level.addFreshEntity(fireball);
				}
			}
			case GHAST -> { // Wail: a terrible cry that sends every foe reeling away
				burst(level, dragon, ParticleTypes.CLOUD, 150, SoundEvents.GHAST_SCREAM, 0.6F);
				for (LivingEntity foe : foes) {
					away(dragon, foe, 1.8, 0.4);
					effect(foe, dragon, MobEffects.WEAKNESS, 120, 1);
					effect(foe, dragon, MobEffects.NAUSEA, 80, 0);
				}
			}
			case VOID -> { // Black Hole: every foe sucked into one point ahead and crushed
				Vec3 hole = dragon.position().add(0, 1, 0).add(dragon.getLookAngle().multiply(1, 0, 1).scale(5));
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, hole.x, hole.y, hole.z, 300, 1.5, 1.5, 1.5, 0.2);
				level.playSound(null, hole.x, hole.y, hole.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.NEUTRAL, 2F, 0.5F);
				for (LivingEntity foe : foes) {
					Vec3 in = hole.subtract(foe.position());
					double distance = in.length();
					if (distance > 1.0E-3) {
						foe.setDeltaMovement(in.scale(Math.min(1.6, distance * 0.25) / distance));
						foe.needsSync = true;
					}
					dragon.powerHit(level, foe, dragon.damageSources().magic(), 5F);
				}
			}
			case CHORUS -> { // Chorus Launch: every foe flung high into the air (what goes up...)
				burst(level, dragon, dragon.element().breathParticle, 200, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.7F);
				for (LivingEntity foe : foes) {
					if (foe.getMaxHealth() <= 150F) {
						for (int up = 12; up >= 4; up -= 2) {
							BlockPos to = foe.blockPosition().above(up);
							if (level.isEmptyBlock(to) && level.isEmptyBlock(to.above())) {
								foe.teleportTo(foe.getX(), foe.getY() + up, foe.getZ());
								level.sendParticles(ParticleTypes.PORTAL, foe.getX(), foe.getY(), foe.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
								break;
							}
						}
					}
				}
			}
			case SHULKER -> { // Shell Up: the dragon and its riders shut tight; foes are thrown off and float away
				burst(level, dragon, ParticleTypes.END_ROD, 150, SoundEvents.SHULKER_CLOSE, 0.8F);
				friends(dragon, friend -> {
					friend.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 2), dragon);
					friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1), dragon);
				});
				for (LivingEntity foe : foes) {
					away(dragon, foe, 1.2, 0.3);
					effect(foe, dragon, MobEffects.LEVITATION, 40, 0);
				}
			}
			case NEBULA -> { // Supernova: a blinding burst of starlight
				burst(level, dragon, ParticleTypes.END_ROD, 400, SoundEvents.GENERIC_EXPLODE.value(), 1.3F);
				level.sendParticles(dragon.element().breathParticle, x, y, z, 300, 5, 2, 5, 0.1);
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().magic(), 6F);
					effect(foe, dragon, MobEffects.BLINDNESS, 60, 0);
					away(dragon, foe, 1.0, 0.3);
				}
			}
			case HAIL -> { // Hailstorm: hailstones pelt every foe
				level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 2F, 0.6F);
				for (LivingEntity foe : foes) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.PACKED_ICE.defaultBlockState()),
						foe.getX(), foe.getY() + foe.getBbHeight() + 3, foe.getZ(), 40, 0.8, 1.5, 0.8, 0);
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 5F);
					foe.setTicksFrozen(foe.getTicksRequiredToFreeze() + 60);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 1);
				}
			}
			case ECLIPSE -> { // Eclipse: darkness falls on every foe; its riders grow strong in it
				burst(level, dragon, dragon.element().breathParticle, 300, SoundEvents.BEACON_DEACTIVATE, 0.5F);
				for (LivingEntity foe : foes) {
					effect(foe, dragon, MobEffects.DARKNESS, 160, 0);
					effect(foe, dragon, MobEffects.WEAKNESS, 160, 0);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 0);
				}
				friends(dragon, friend -> {
					friend.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1), dragon);
					friend.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0), dragon);
				});
			}
			case PRISM -> { // Prism Beam: a beam of split light; foes in front take its full force
				Vec3 look = dragon.getLookAngle();
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 2F, 0.6F);
				for (int i = 1; i <= 16; i++) {
					level.sendParticles(dragon.element().breathParticle, x + look.x * i, y + look.y * i, z + look.z * i, 6, 0.15, 0.15, 0.15, 0);
				}
				for (LivingEntity foe : foes) {
					Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.5, 0).subtract(x, y, z).normalize();
					boolean inBeam = to.dot(look) > 0.75;
					dragon.powerHit(level, foe, dragon.damageSources().magic(), inBeam ? 9F : 3F);
					effect(foe, dragon, MobEffects.GLOWING, 160, 0);
				}
			}
			case ZEPHYR -> { // Tailwind: a gale behind the dragon and its riders, and in every foe's face
				burst(level, dragon, ParticleTypes.GUST, 20, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8F);
				friends(dragon, friend -> {
					friend.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2), dragon);
					friend.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1), dragon);
				});
				for (LivingEntity foe : foes) {
					away(dragon, foe, 1.4, 0.5);
				}
			}
			case PHANTOM -> { // Night Terror: every foe sees phantoms in the dark, and flees
				burst(level, dragon, dragon.element().breathParticle, 250, SoundEvents.PHANTOM_AMBIENT, 0.5F);
				for (LivingEntity foe : foes) {
					effect(foe, dragon, MobEffects.BLINDNESS, 100, 0);
					effect(foe, dragon, MobEffects.WEAKNESS, 100, 0);
					away(dragon, foe, 1.0, 0.2);
				}
			}
			case REDSTONE -> { // Overload: a surge of redstone through every foe
				burst(level, dragon, dragon.element().breathParticle, 300, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.5F);
				for (LivingEntity foe : foes) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
					dragon.powerHit(level, foe, dragon.damageSources().magic(), 5F);
					effect(foe, dragon, MobEffects.SLOWNESS, 80, 2);
					effect(foe, dragon, MobEffects.MINING_FATIGUE, 120, 1);
				}
			}
			case CLOCKWORK -> { // Time Stop: everything in the world stands still, until the power is used again
				level.sendParticles(dragon.element().breathParticle, x, y, z, 200, 4, 1.5, 4, 0.05);
				ClockworkTime.toggle(level, rider);
			}
			case MELODY -> { // Dance Party: every foe dances helplessly, and friends are soothed by the song
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.NEUTRAL, 2F, 1.2F);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 2F, 0.9F);
				for (LivingEntity foe : foes) {
					level.sendParticles(ParticleTypes.NOTE, foe.getX(), foe.getY() + foe.getBbHeight() + 0.3, foe.getZ(), 6, 0.5, 0.3, 0.5, 1);
					effect(foe, dragon, MobEffects.NAUSEA, 160, 0);
					effect(foe, dragon, MobEffects.SLOWNESS, 100, 2);
					foe.push(0, 0.35, 0); // hop
					foe.needsSync = true;
					if (foe instanceof Mob mob) {
						mob.setTarget(null);
					}
				}
				friends(dragon, friend -> friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1), dragon));
			}
			default -> {
			}
		}
	}

	/** Particles bursting out all round the dragon, and a sound. */
	private static void burst(ServerLevel level, DragonEntity dragon, ParticleOptions particle, int count, SoundEvent sound, float pitch) {
		double y = dragon.getY() + dragon.getBbHeight() * 0.5;
		level.sendParticles(particle, dragon.getX(), y, dragon.getZ(), count, 4, 1.5, 4, 0.05);
		level.playSound(null, dragon.getX(), y, dragon.getZ(), sound, SoundSource.NEUTRAL, 2F, pitch);
	}

	/** The dragon and everyone riding it. */
	private static void friends(DragonEntity dragon, java.util.function.Consumer<LivingEntity> action) {
		action.accept(dragon);
		for (Entity passenger : dragon.getPassengers()) {
			if (passenger instanceof LivingEntity living) {
				action.accept(living);
			}
		}
	}

	private static void away(DragonEntity dragon, LivingEntity foe, double strength, double lift) {
		Vec3 out = foe.position().subtract(dragon.position()).multiply(1, 0, 1);
		out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
		foe.push(out.x * strength, lift, out.z * strength);
		foe.needsSync = true;
	}

	private static void launch(LivingEntity foe, double up) {
		foe.push(0, up, 0);
		foe.needsSync = true;
	}

	/** Allies called to the fight for half a minute (more for an Elder): snow golems, or wolves that answer to the rider. */
	private static void allies(DragonEntity dragon, ServerLevel level, Player rider, List<LivingEntity> foes, EntityType<? extends Mob> type, int count,
			ParticleOptions puff) {
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), type == EntityTypes.WOLF ? SoundEvents.POLAR_BEAR_WARNING : SoundEvents.SNOW_GOLEM_SHOOT,
			SoundSource.NEUTRAL, 2F, 0.7F);
		for (int i = 0; i < count + (dragon.isElder() ? 1 : 0); i++) {
			Mob ally = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (ally == null) {
				break;
			}
			double angle = dragon.getRandom().nextDouble() * Math.PI * 2;
			ally.snapTo(dragon.getX() + Math.cos(angle) * 3, dragon.getY(), dragon.getZ() + Math.sin(angle) * 3, dragon.getYRot(), 0F);
			if (ally instanceof Wolf wolf && rider != null) {
				wolf.tame(rider);
			}
			if (!foes.isEmpty()) {
				ally.setTarget(foes.get(i % foes.size()));
			}
			level.addFreshEntity(ally);
			level.sendParticles(puff, ally.getX(), ally.getY() + 0.8, ally.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
			dragon.allies.add(ally);
		}
	}

	// ---- Lairs ----------------------------------------------------------------------------------------------

	public static final ResourceKey<PlacedFeature> ABYSS_LAIR = lairKey("abyss_lair");
	public static final ResourceKey<PlacedFeature> GLOW_LAIR = lairKey("glow_lair");
	public static final ResourceKey<PlacedFeature> DRIPSTONE_LAIR = lairKey("dripstone_lair");

	private static ResourceKey<PlacedFeature> lairKey(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, WonderRealms.id(name));
	}

	/** Where the Abyss, Glow and Dripstone dragons live: lairs on the deep ocean floor, in lush caves and in dripstone caves. */
	static void lairs() {
		for (DragonElement kind : new DragonElement[] {DragonElement.ABYSS, DragonElement.GLOW, DragonElement.DRIPSTONE}) {
			LairFeature feature = new LairFeature(kind);
			Registry.register(BuiltInRegistries.FEATURE_TYPE, WonderRealms.id(kind.id + "_lair"), feature.codec());
		}
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.DEEP_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.DEEP_FROZEN_OCEAN),
			GenerationStep.Decoration.LOCAL_MODIFICATIONS, ABYSS_LAIR);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.LUSH_CAVES), GenerationStep.Decoration.UNDERGROUND_DECORATION, GLOW_LAIR);
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.DRIPSTONE_CAVES), GenerationStep.Decoration.UNDERGROUND_DECORATION, DRIPSTONE_LAIR);
	}

	/**
	 * A dragon's lair: a nest on the floor (of the deep sea, a lush cave or a dripstone cave) in keeping with its kind,
	 * and the dragon guarding it.
	 */
	public static final class LairFeature implements Feature {
		private final DragonElement kind;
		private final MapCodec<LairFeature> codec;

		LairFeature(DragonElement kind) {
			this.kind = kind;
			this.codec = MapCodec.unit(this);
		}

		@Override
		public MapCodec<LairFeature> codec() {
			return this.codec;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			boolean wet = this.kind == DragonElement.ABYSS;
			BlockPos floor = origin.below();
			if (!level.getBlockState(floor).isSolid()) {
				return false;
			}
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 2, 1))) {
				BlockState state = level.getBlockState(pos);
				if (wet ? !state.getFluidState().isSourceOfType(Fluids.WATER) : !state.isAir()) {
					return false; // no room for a dragon
				}
			}
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > 3.4) {
						continue;
					}
					BlockPos ground = floor.offset(dx, 0, dz);
					if (!level.getBlockState(ground).isSolid()) {
						continue;
					}
					BlockPos above = ground.above();
					boolean open = wet ? level.getBlockState(above).getFluidState().isSourceOfType(Fluids.WATER) : level.getBlockState(above).isAir();
					switch (this.kind) {
						case ABYSS -> {
							level.setBlock(ground, (d < 1.5 ? Blocks.DARK_PRISMARINE : random.nextBoolean() ? Blocks.PRISMARINE : Blocks.PRISMARINE_BRICKS)
								.defaultBlockState(), Block.UPDATE_CLIENTS);
							if (d > 2.4 && open && random.nextInt(3) == 0) {
								level.setBlock(above, Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
							} else if (d > 1.5 && open && random.nextInt(3) == 0) {
								level.setBlock(above, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + random.nextInt(4)), Block.UPDATE_CLIENTS);
							}
						}
						case GLOW -> {
							level.setBlock(ground, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
							if (open && d > 2.4 && random.nextInt(3) == 0) {
								level.setBlock(above, Blocks.FLOWERING_AZALEA.defaultBlockState(), Block.UPDATE_CLIENTS);
							} else if (open && random.nextInt(3) == 0) {
								level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), Block.UPDATE_CLIENTS);
							}
						}
						default -> { // DRIPSTONE
							level.setBlock(ground, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
							if (open && d > 2.4 && random.nextInt(2) == 0) {
								level.setBlock(above, Blocks.POINTED_DRIPSTONE.defaultBlockState()
									.setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.UP)
									.setValue(BlockStateProperties.SPELEOTHEM_THICKNESS, SpeleothemThickness.TIP), Block.UPDATE_CLIENTS);
							}
						}
					}
				}
			}
			if (this.kind == DragonElement.GLOW) {
				// Glow lichen creeping over the cave round about.
				for (int i = 0; i < 30; i++) {
					BlockPos pos = origin.offset(random.nextInt(11) - 5, random.nextInt(5) - 1, random.nextInt(11) - 5);
					if (!level.getBlockState(pos).isAir()) {
						continue;
					}
					for (Direction face : Direction.values()) {
						BlockPos against = pos.relative(face);
						if (level.getBlockState(against).isFaceSturdy(level, against, face.getOpposite())) {
							level.setBlock(pos, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true), Block.UPDATE_CLIENTS);
							break;
						}
					}
				}
			}
			DragonEntity dragon = Dragons.TYPES.get(this.kind).create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (dragon != null) {
				dragon.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, random.nextFloat() * 360F, 0F);
				dragon.setIslandHome(origin, false); // it guards its lair as a roost's dragon guards its island
				level.addFreshEntity(dragon);
			}
			return true;
		}
	}
}
