package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.overcloud.Overcloud;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ways of the dragons added in 2.0: what each one's breath does, the gift it shares with its rider, and the power
 * it unleashes on the power key. {@link DragonEntity} hands these kinds over to this class.
 * <p>
 * Overworld wilds: Slime (bouncing goo; no fall damage), Mushroom (dizzying spores, mushrooms sprout; shrugs off poison
 * and hunger), Blossom (petals that calm foes and make animals fall in love; babies grow up fast), Iron (a heavy blast;
 * plated hide, a tougher rider, an iron golem ally), Bone (weakening shards; the undead leave its rider alone), Jungle
 * (vines that drag foes in; a stronger rider), Echo (a sonic pulse no armor stops; monsters glow) and Coral (bubbles that
 * float foes away; swims, and its rider swims like a dolphin).
 * <p>
 * Overcloud: Sun (a blinding beam that burns the undead and bakes sand into glass; golden hearts), Moon (moon dust
 * that sends foes to sleep; a gentle fall), Star (stardust and meteors; more experience), Aurora (light that heals
 * friends; extra hearts), Phoenix (fire that heals it; saves its rider from death), Nimbus (a cloudburst that builds
 * clouds; arrows can't touch its rider), Gold (dazzling gold; treasure sense) and Pearl (it blinks foes away, dodges by
 * blinking, and blinks its riders across the sky; endermen leave its rider alone).
 */
public final class DragonKinds {
	/** An Iron dragon's golem fights for this long before it goes. */
	private static final int GOLEM_LIFETIME = 20 * 30;
	/** A Phoenix dragon can save its rider from death this often. */
	static final int REBIRTH_COOLDOWN = 20 * 60 * 5;
	/** How far a Gold dragon's rider senses treasure, and an Echo dragon's rider hears monsters. */
	private static final int TREASURE_RANGE = 24, ECHO_RANGE = 24;
	/** How far a Pearl dragon blinks its riders, at most. */
	private static final double BLINK_RANGE = 32;
	/** Baby animals near a tamed Blossom dragon grow up this many ticks more every two seconds. */
	private static final int BLOSSOM_GROWTH = 600;
	private static final ParticleOptions SUN_DUST = new DustParticleOptions(0xFFE070, 1.8F);
	private static final ParticleOptions MOON_DUST = new DustParticleOptions(0xD8E0FF, 1.6F);
	private static final ParticleOptions[] AURORA_DUST = {new DustParticleOptions(0x40F080, 1.6F), new DustParticleOptions(0x30E0D0, 1.6F),
		new DustParticleOptions(0x50A0FF, 1.6F), new DustParticleOptions(0xA060F0, 1.6F)};
	private static final ParticleOptions GOLD_COIN = new ItemParticleOption(ParticleTypes.ITEM, Items.GOLD_NUGGET);
	private static final ParticleOptions VINES = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState());
	/** What a Sun dragon's breath bakes, as a furnace would. */
	private static final Map<Block, Block> SUN_BAKES = Map.of(Blocks.SAND, Blocks.GLASS, Blocks.RED_SAND, Blocks.GLASS, Blocks.CLAY, Blocks.TERRACOTTA,
		Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE, Blocks.SMOOTH_STONE, Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE,
		Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.WET_SPONGE, Blocks.SPONGE);

	private DragonKinds() {
	}

	/** Zombies, skeletons, phantoms and the rest of the undead. */
	static boolean undead(Entity entity) {
		return entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** The colour of an Aurora dragon's breath along its stream. */
	static ParticleOptions auroraDust(int stretch) {
		return AURORA_DUST[stretch % AURORA_DUST.length];
	}

	// ---- Breath ---------------------------------------------------------------------------------------------

	/** An Echo dragon's sonic pulse goes straight through armor. */
	static DamageSource breathSource(DragonEntity dragon) {
		return dragon.breathElement() == DragonElement.ECHO ? dragon.damageSources().sonicBoom(dragon) : dragon.damageSources().mobAttack(dragon);
	}

	/** An Iron dragon's blast hits harder than most. */
	static float breathDamageScale(DragonElement element) {
		return element == DragonElement.IRON ? 1.35F : 1F;
	}

	/**
	 * Before a breath hurts what it touches: a Blossom dragon's petals don't hurt animals (they fall in love instead),
	 * and an Aurora dragon's light heals its friends. True if that's all the breath does to {@code target}.
	 */
	static boolean breathGentle(DragonEntity dragon, ServerLevel level, LivingEntity target, boolean friend) {
		switch (dragon.breathElement()) {
			case BLOSSOM -> {
				if (target instanceof Animal animal && !(target instanceof Enemy) && !friend) {
					inLove(dragon, level, animal);
					return true;
				}
			}
			case AURORA -> {
				if (friend && target.getHealth() < target.getMaxHealth()) {
					target.heal(1.5F);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 2, 0.3, 0.2, 0.3, 0);
				}
			}
			default -> {
			}
		}
		return friend;
	}

	/** What the breath does to a foe it has just hurt. */
	static void breathHit(DragonEntity dragon, ServerLevel level, LivingEntity target, Vec3 dir) {
		switch (dragon.breathElement()) {
			case SLIME -> { // Boing: a glob of goo bounces them up and gums their feet.
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), dragon);
				target.push(dir.x * 0.3, 0.55, dir.z * 0.3);
				level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 0.3, target.getZ(), 6, 0.3, 0.1, 0.3, 0);
				dragon.awardBreath("boing");
			}
			case MUSHROOM -> { // Dizzy spores.
				target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), dragon);
			}
			case BLOSSOM -> calm(dragon, target, 100);
			case IRON -> target.push(dir.x * 0.35, 0.05, dir.z * 0.35); // and it hits hard: see breathDamageScale
			case BONE -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), dragon);
			}
			case JUNGLE -> { // A lash of vines drags them in and snares them.
				Vec3 in = dragon.position().subtract(target.position()).multiply(1, 0, 1);
				in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(0.6);
				target.push(in.x, 0.12, in.z);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), dragon);
			}
			case ECHO -> {
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), dragon);
				target.push(dir.x * 0.4, 0.1, dir.z * 0.4);
			}
			case CORAL -> { // Sealed in a bubble, they float away and can barely fight.
				target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), dragon);
				level.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.4, 0.4, 0.4, 0);
				dragon.awardBreath("bubble_trouble");
			}
			case SUN -> {
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), dragon);
				if (undead(target)) {
					target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 160));
					target.setInvulnerableTime(0);
					target.hurtServer(level, dragon.damageSources().magic(), 2F);
				}
			}
			case MOON -> {
				target.addEffect(new MobEffectInstance(DragonEffects.ASLEEP, 60, 0), dragon);
				if (target instanceof Mob) {
					dragon.awardBreath("sweet_dreams");
				}
			}
			case STAR -> {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), dragon);
				if (dragon.getRandom().nextFloat() < 0.25F) { // a starburst
					level.sendParticles(ParticleTypes.FIREWORK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.2, 0.2, 0.2, 0.15);
					target.setInvulnerableTime(0);
					target.hurtServer(level, dragon.damageSources().magic(), 2F);
				}
			}
			case AURORA -> {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), dragon);
			}
			case PHOENIX -> { // Fire that feeds the phoenix.
				target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
				dragon.heal(1F);
			}
			case NIMBUS -> { // A cloudburst: blinded and drenched.
				target.clearFire();
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), dragon);
			}
			case GOLD -> {
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0), dragon);
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0), dragon);
				target.push(dir.x * 0.5, 0.15, dir.z * 0.5);
			}
			case PEARL -> {
				if (target.getMaxHealth() <= 150F) { // bosses stand their ground
					blinkAway(level, target, 6);
					target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0), dragon);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * What the breath does to the world, every few ticks: a Phoenix dragon's fire melts snow and lights campfires as an
	 * Ember dragon's does. The rest only touch blocks when someone is riding and aiming them, as an Earth dragon only
	 * digs then: a Mushroom dragon's spores sprout mushrooms, a Sun dragon's beam bakes sand into glass (and more, as a
	 * furnace would), and a Nimbus dragon's cloudburst builds clouds at the end of its stream, even in midair.
	 */
	static void breathBlocks(DragonEntity dragon, ServerLevel level, BlockHitResult hit, Vec3 end) {
		DragonElement element = dragon.breathElement();
		boolean onBlock = hit.getType() == HitResult.Type.BLOCK;
		if (element == DragonElement.PHOENIX && onBlock) {
			dragon.affectBlocks(level, hit.getBlockPos());
			return;
		}
		if (!dragon.isBreathRidden() || !(dragon.getControllingPassenger() instanceof Player rider)) {
			return;
		}
		switch (element) {
			case MUSHROOM -> {
				if (onBlock) {
					sprout(dragon, level, rider, hit.getBlockPos());
				}
			}
			case SUN -> {
				if (onBlock) {
					bake(dragon, level, rider, hit.getBlockPos());
				}
			}
			case NIMBUS -> buildCloud(dragon, level, rider, onBlock ? hit.getBlockPos().relative(hit.getDirection()) : BlockPos.containing(end));
			default -> {
			}
		}
	}

	/** Spores make mushrooms grow (into huge ones) and sprout on shady ground. */
	private static void sprout(DragonEntity dragon, ServerLevel level, Player rider, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (dragon.getRandom().nextFloat() > 0.2F || !rider.mayInteract(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if ((state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)) && state.getBlock() instanceof BonemealableBlock plant
					&& plant.isValidBonemealTarget(level, pos, state, BonemealSource.MOB)) {
				plant.performBonemeal(level, dragon.getRandom(), pos.immutable(), state, BonemealSource.MOB);
				level.levelEvent(1505, pos, 15);
			} else if (state.is(BlockTags.DIRT) && level.getBlockState(pos.above()).isAir()) {
				BlockState mushroom = (dragon.getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
				if (mushroom.canSurvive(level, pos.above())) {
					level.setBlockAndUpdate(pos.above(), mushroom);
				}
			}
		}
	}

	/** A Sun dragon's beam bakes what it touches, as a furnace would: sand to glass, clay to terracotta, and so on. */
	private static void bake(DragonEntity dragon, ServerLevel level, Player rider, BlockPos center) {
		int baked = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (baked >= 3 || pos.distSqr(center) > 2 || dragon.getRandom().nextFloat() < 0.4F || !rider.mayInteract(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			Block into = SUN_BAKES.get(state.getBlock());
			if (into != null) {
				level.setBlockAndUpdate(pos, into.defaultBlockState());
				level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.01);
				baked++;
				if (into == Blocks.GLASS) {
					dragon.awardBreath("sunbaked");
				}
			} else if (state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
				level.removeBlock(pos, false); // and it melts snow and ice, as fire does
			} else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
				level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
			}
		}
		if (baked > 0) {
			level.playSound(null, center, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 1F, 1.2F);
		}
	}

	/** A Nimbus dragon's cloudburst gathers into clouds you can walk on: a few blocks each blast, only into thin air. */
	private static void buildCloud(DragonEntity dragon, ServerLevel level, Player rider, BlockPos at) {
		int built = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 0, 1))) {
			if (built >= 4 || (pos.distSqr(at) > 1 && dragon.getRandom().nextFloat() < 0.55F) || !level.getBlockState(pos).isAir()
					|| !rider.mayInteract(level, pos) || dragon.getBoundingBox().inflate(1).contains(Vec3.atCenterOf(pos))) {
				continue;
			}
			level.setBlockAndUpdate(pos.immutable(), Overcloud.CLOUD.defaultBlockState());
			level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
			built++;
		}
		if (built > 0) {
			level.playSound(null, at, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1F, 1.3F);
			dragon.awardBreath("cloud_builder");
		}
	}

	// ---- Gifts ----------------------------------------------------------------------------------------------

	/** What the dragon shares with whoever rides it, once a second. */
	static void gift(DragonEntity dragon, ServerLevel level, Player rider) {
		switch (dragon.element()) {
			case SLIME -> giftEffect(rider, dragon, DragonEffects.BOUNCY, 0);
			case MUSHROOM -> { // its spores keep its rider well
				for (Holder<MobEffect> ill : List.of(MobEffects.POISON, MobEffects.HUNGER, MobEffects.NAUSEA, MobEffects.WEAKNESS)) {
					rider.removeEffect(ill);
				}
			}
			case IRON -> giftEffect(rider, dragon, MobEffects.RESISTANCE, 0);
			case BONE -> { // the undead don't see its rider: any that were after them lose interest
				for (Mob undead : level.getEntitiesOfClass(Mob.class, rider.getBoundingBox().inflate(32),
						mob -> undead(mob) && mob.getTarget() == rider)) {
					undead.setTarget(null);
				}
			}
			case JUNGLE -> giftEffect(rider, dragon, MobEffects.STRENGTH, 0);
			case ECHO -> { // echolocation: every monster around lights up
				for (LivingEntity monster : level.getEntitiesOfClass(LivingEntity.class, rider.getBoundingBox().inflate(ECHO_RANGE),
						mob -> mob instanceof Enemy && mob.isAlive())) {
					monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false), dragon);
				}
			}
			case CORAL -> {
				giftEffect(rider, dragon, MobEffects.DOLPHINS_GRACE, 0);
				giftEffect(rider, dragon, MobEffects.CONDUIT_POWER, 0);
			}
			case SUN -> {
				giftEffect(rider, dragon, MobEffects.ABSORPTION, 0);
				// The sun tops its golden hearts back up now and then.
				if (dragon.tickCount % 200 < 20 && level.isBrightOutside() && level.canSeeSky(rider.blockPosition()) && rider.getAbsorptionAmount() < 4F) {
					rider.setAbsorptionAmount(4F);
				}
			}
			case MOON -> giftEffect(rider, dragon, MobEffects.SLOW_FALLING, 0);
			case STAR -> giftEffect(rider, dragon, MobEffects.LUCK, 0);
			case AURORA -> giftEffect(rider, dragon, MobEffects.HEALTH_BOOST, 0);
			case GOLD -> {
				if (rider instanceof ServerPlayer player && dragon.tickCount % 40 < 20) {
					senseTreasure(level, player);
				}
			}
			case PEARL -> { // endermen don't mind its rider
				for (Enderman enderman : level.getEntitiesOfClass(Enderman.class, rider.getBoundingBox().inflate(32), e -> e.getTarget() == rider)) {
					enderman.setTarget(null);
				}
			}
			default -> {
			}
		}
	}

	private static void giftEffect(Player rider, DragonEntity dragon, Holder<MobEffect> effect, int amplifier) {
		rider.addEffect(new MobEffectInstance(effect, 60, amplifier, true, false, true), dragon);
	}

	/** Unopened treasure chests and barrels near a Gold dragon's rider sparkle, for that rider's eyes only. */
	private static void senseTreasure(ServerLevel level, ServerPlayer rider) {
		int cx = rider.blockPosition().getX() >> 4, cz = rider.blockPosition().getZ() >> 4;
		int reach = (TREASURE_RANGE >> 4) + 1;
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				if (!level.hasChunk(x, z)) {
					continue;
				}
				for (BlockEntity block : level.getChunk(x, z).getBlockEntities().values()) {
					BlockPos pos = block.getBlockPos();
					if (block instanceof RandomizableContainerBlockEntity hoard && hoard.getLootTable() != null
							&& pos.distSqr(rider.blockPosition()) < TREASURE_RANGE * TREASURE_RANGE) {
						level.sendParticles(rider, ParticleTypes.WAX_ON, true, true, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
					}
				}
			}
		}
	}

	/** Whether {@code mob} should leave {@code target} alone: the undead don't see a Bone dragon's rider, nor endermen a Pearl's. */
	public static boolean ignores(Mob mob, LivingEntity target) {
		if (!(target instanceof Player) || !(target.getVehicle() instanceof DragonEntity dragon)) {
			return false;
		}
		return dragon.element() == DragonElement.BONE && undead(mob)
			|| dragon.element() == DragonElement.PEARL && mob instanceof Enderman;
	}

	// ---- Every tick -----------------------------------------------------------------------------------------

	static void tick(DragonEntity dragon, ServerLevel level) {
		if (!dragon.allies.isEmpty() && dragon.tickCount % 20 == 0) {
			dragon.allies.removeIf(ally -> {
				if (ally.isAlive() && ally.tickCount < GOLEM_LIFETIME) {
					if (ally.getTarget() == null) {
						ally.setTarget(dragon.getTarget());
					}
					return false;
				}
				if (ally.isAlive()) {
					level.sendParticles(ParticleTypes.POOF, ally.getX(), ally.getY() + 1, ally.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
					ally.discard();
				}
				return true;
			});
		}
		switch (dragon.element()) {
			case BLOSSOM -> { // baby animals near a tamed Blossom dragon grow up fast
				if (dragon.isTame() && dragon.tickCount % 40 == 0) {
					for (AgeableMob baby : level.getEntitiesOfClass(AgeableMob.class, dragon.getBoundingBox().inflate(8), AgeableMob::isBaby)) {
						if (!(baby instanceof DragonEntity)) {
							baby.ageUp(BLOSSOM_GROWTH, true);
						}
					}
				}
				if (dragon.tickCount % 30 == 0) {
					level.sendParticles(ParticleTypes.CHERRY_LEAVES, dragon.getX(), dragon.getY() + dragon.getBbHeight(), dragon.getZ(), 2,
						dragon.getBbWidth() * 0.4, 0.2, dragon.getBbWidth() * 0.4, 0);
				}
			}
			case PHOENIX -> {
				if (dragon.tickCount % 12 == 0) {
					level.sendParticles(ParticleTypes.FLAME, dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.7, dragon.getZ(), 1,
						dragon.getBbWidth() * 0.4, dragon.getBbHeight() * 0.3, dragon.getBbWidth() * 0.4, 0.01);
				}
			}
			case STAR, ECHO -> {
				if (dragon.tickCount % 25 == 0) {
					level.sendParticles(dragon.element() == DragonElement.STAR ? ParticleTypes.END_ROD : ParticleTypes.SCULK_SOUL,
						dragon.getX(), dragon.getY() + dragon.getBbHeight() * 0.6, dragon.getZ(), 1, dragon.getBbWidth() * 0.5, dragon.getBbHeight() * 0.3,
						dragon.getBbWidth() * 0.5, 0.01);
				}
			}
			default -> {
			}
		}
	}

	/** A Pearl dragon that gets hurt sometimes blinks out of reach (but never while it's carrying anyone). */
	static void dodge(DragonEntity dragon, ServerLevel level) {
		if (!dragon.isVehicle() && dragon.getRandom().nextFloat() < 0.3F) {
			blinkAway(level, dragon, 8);
		}
	}

	// ---- Powers ---------------------------------------------------------------------------------------------

	/** The power key, for the 2.0 kinds (see {@link DragonEntity#usePower}). {@code foes} are already chosen. */
	static void power(DragonEntity dragon, ServerLevel level, Player rider, List<LivingEntity> foes) {
		double x = dragon.getX(), y = dragon.getY() + dragon.getBbHeight() * 0.5, z = dragon.getZ();
		switch (dragon.element()) {
			case SLIME -> { // Boing!: every foe bounces sky-high, and the dragon and its riders turn bouncy
				level.sendParticles(ParticleTypes.ITEM_SLIME, x, dragon.getY(), z, 220, 5, 0.6, 5, 0.2);
				level.playSound(null, x, y, z, SoundEvents.SLIME_JUMP, SoundSource.NEUTRAL, 2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 2F, 0.6F);
				dragon.shield(new MobEffectInstance(DragonEffects.BOUNCY, 600, 0));
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 4F);
					foe.push(0, 1.4, 0);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), dragon);
				}
				if (!foes.isEmpty()) {
					dragon.awardBreath("boing");
				}
			}
			case MUSHROOM -> { // Fairy Ring: mushrooms spring up all around, foes reel, friends feast
				level.sendParticles(new DustParticleOptions(0xC8A888, 1.6F), x, y, z, 250, 5, 1.5, 5, 0);
				level.sendParticles(ParticleTypes.MYCELIUM, x, dragon.getY(), z, 150, 5, 0.3, 5, 0);
				level.playSound(null, x, y, z, SoundEvents.FUNGUS_PLACE, SoundSource.NEUTRAL, 2F, 0.6F);
				fairyRing(dragon, level, rider);
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 3F);
					foe.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), dragon);
				}
				for (Entity passenger : dragon.getPassengers()) {
					if (passenger instanceof Player friend) {
						friend.heal(6F);
						friend.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 1), dragon);
					}
				}
			}
			case BLOSSOM -> { // Petal Storm: friends heal, foes calm down, and the animals around fall in love
				level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y + 2, z, 400, 6, 2, 6, 0);
				level.playSound(null, x, y, z, SoundEvents.CHERRY_LEAVES_BREAK, SoundSource.NEUTRAL, 2F, 0.8F);
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2F, 1.4F);
				dragon.shield(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
				for (Entity passenger : dragon.getPassengers()) {
					if (passenger instanceof Player friend) {
						friend.heal(6F);
					}
				}
				for (LivingEntity foe : foes) {
					calm(dragon, foe, 200);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), dragon);
				}
				for (Animal animal : level.getEntitiesOfClass(Animal.class, dragon.getBoundingBox().inflate(DragonEntity.POWER_RADIUS),
						animal -> !(animal instanceof Enemy) && !(animal instanceof DragonEntity))) {
					inLove(dragon, level, animal);
				}
			}
			case IRON -> { // Iron Guard: an iron golem (two for an Elder) joins the fight for half a minute
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 150, 3, 1.5, 3, 0.4);
				level.playSound(null, x, y, z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 2F, 0.8F);
				level.playSound(null, x, y, z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1F, 0.6F);
				for (int i = 0; i < (dragon.isElder() ? 2 : 1); i++) {
					IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (golem == null) {
						break;
					}
					double angle = dragon.getRandom().nextDouble() * Math.PI * 2;
					golem.snapTo(x + Math.cos(angle) * 3, dragon.getY(), z + Math.sin(angle) * 3, dragon.getYRot(), 0F);
					golem.setPlayerCreated(true); // on our side: it never turns on players
					if (!foes.isEmpty()) {
						golem.setTarget(foes.get(i % foes.size()));
					}
					level.addFreshEntity(golem);
					level.sendParticles(ParticleTypes.POOF, golem.getX(), golem.getY() + 1, golem.getZ(), 20, 0.5, 1, 0.5, 0.02);
					dragon.allies.add(golem);
				}
				dragon.awardBreath("iron_ally");
			}
			case BONE -> { // Grave Howl: the undead crumble, and every foe is left weak and slow
				level.sendParticles(ParticleTypes.SOUL, x, y, z, 120, 5, 1.5, 5, 0.05);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BONE), x, y, z, 120, 5, 1.5, 5, 0.2);
				level.playSound(null, x, y, z, SoundEvents.SKELETON_HORSE_DEATH, SoundSource.NEUTRAL, 2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.NEUTRAL, 2F, 0.5F);
				for (LivingEntity foe : foes) {
					boolean undead = undead(foe);
					dragon.powerHit(level, foe, undead ? dragon.damageSources().magic() : dragon.damageSources().mobAttack(dragon), undead ? 12F : 4F);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), dragon);
				}
			}
			case JUNGLE -> { // Vine Snare: every foe is dragged in and wrapped up tight
				level.playSound(null, x, y, z, SoundEvents.VINE_STEP, SoundSource.NEUTRAL, 2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.NEUTRAL, 2F, 0.6F);
				for (LivingEntity foe : foes) {
					Vec3 in = dragon.position().subtract(foe.position()).multiply(1, 0, 1);
					double pull = Math.max(0, in.length() - 3) * 0.22;
					in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(Math.min(1.6, pull));
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 4F);
					foe.push(in.x, 0.3, in.z);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 4), dragon);
					level.sendParticles(VINES, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
				}
			}
			case ECHO -> { // Sonic Boom: the warden's own blast, at every foe
				level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 3F, 0.9F);
				Vec3 from = dragon.position().add(0, dragon.getBbHeight() * 0.7, 0);
				foes.stream().sorted((a, b) -> Double.compare(a.distanceToSqr(dragon), b.distanceToSqr(dragon))).limit(8).forEach(foe -> {
					Vec3 to = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
					Vec3 step = to.subtract(from);
					int steps = Math.max(1, (int) step.length());
					for (int i = 1; i <= steps; i++) {
						Vec3 at = from.add(step.scale((double) i / steps));
						level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
					}
					dragon.powerHit(level, foe, dragon.damageSources().sonicBoom(dragon), 10F);
					Vec3 away = step.multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(1.5);
					foe.push(away.x, 0.5, away.z);
				});
			}
			case CORAL -> { // Bubble Trap: every foe floats up in a bubble, helpless, until it pops
				level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 300, 5, 2, 5, 0.05);
				level.sendParticles(ParticleTypes.SPLASH, x, y, z, 200, 5, 1, 5, 0.2);
				level.playSound(null, x, y, z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.NEUTRAL, 2F, 0.8F);
				level.playSound(null, x, y, z, SoundEvents.BUBBLE_POP, SoundSource.NEUTRAL, 2F, 1F);
				for (LivingEntity foe : foes) {
					foe.clearFire();
					foe.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 80, 1), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2), dragon);
				}
				if (!foes.isEmpty()) {
					dragon.awardBreath("bubble_trouble");
				}
			}
			case SUN -> { // Solar Flare: a blinding flash that sets the undead ablaze; golden hearts for the dragon and its riders
				level.sendParticles(SUN_DUST, x, y, z, 300, 5, 2, 5, 0);
				level.sendParticles(ParticleTypes.END_ROD, x, y, z, 150, 4, 2, 4, 0.2);
				level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2F, 1.5F);
				level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 2F, 0.6F);
				dragon.shield(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1), new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				for (LivingEntity foe : foes) {
					boolean undead = undead(foe);
					dragon.powerHit(level, foe, dragon.damageSources().magic(), undead ? 12F : 6F);
					foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), dragon);
					if (undead) {
						foe.setRemainingFireTicks(Math.max(foe.getRemainingFireTicks(), 200));
					}
				}
			}
			case MOON -> { // Lullaby: every foe drifts off to sleep
				level.sendParticles(MOON_DUST, x, y, z, 300, 5, 2, 5, 0);
				level.sendParticles(ParticleTypes.NOTE, x, y + 1.5, z, 12, 3, 1, 3, 1);
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 2F, 0.6F);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 2F, 0.7F);
				for (LivingEntity foe : foes) {
					foe.addEffect(new MobEffectInstance(DragonEffects.ASLEEP, 160, 0), dragon);
				}
				if (foes.stream().anyMatch(foe -> foe instanceof Mob)) {
					dragon.awardBreath("sweet_dreams");
				}
			}
			case STAR -> { // Meteor Shower: falling stars strike every foe
				level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 2F, 0.6F);
				foes.stream().sorted((a, b) -> Double.compare(a.distanceToSqr(dragon), b.distanceToSqr(dragon))).limit(8).forEach(foe -> {
					for (int i = 0; i < 10; i++) {
						double up = 10 - i;
						level.sendParticles(i < 5 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, foe.getX() + up * 0.3, foe.getY() + up, foe.getZ(), 2, 0.1, 0.1, 0.1, 0);
					}
					level.sendParticles(ParticleTypes.EXPLOSION, foe.getX(), foe.getY() + 0.5, foe.getZ(), 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.FIREWORK, foe.getX(), foe.getY() + 0.5, foe.getZ(), 25, 0.3, 0.3, 0.3, 0.2);
					level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8F, 1.4F);
					dragon.powerHit(level, foe, dragon.damageSources().magic(), 8F);
					foe.setRemainingFireTicks(Math.max(foe.getRemainingFireTicks(), 60));
					foe.push(0, 0.5, 0);
				});
			}
			case AURORA -> { // Aurora Veil: curtains of light heal and shield every friend, and light up every foe
				for (int ring = 0; ring < AURORA_DUST.length; ring++) {
					double radius = 2.5 + ring * 1.5;
					for (int i = 0; i < 28; i++) {
						double angle = Math.PI * 2 * i / 28;
						level.sendParticles(AURORA_DUST[ring], x + Math.cos(angle) * radius, y + 1 + ring * 0.6, z + Math.sin(angle) * radius, 1, 0, 0.3, 0, 0);
					}
				}
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 2F, 1.2F);
				level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 2F, 1.4F);
				dragon.heal(20F);
				dragon.shield(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), new MobEffectInstance(MobEffects.RESISTANCE, 300, 0));
				LivingEntity owner = dragon.getOwner();
				for (LivingEntity friend : level.getEntitiesOfClass(LivingEntity.class, dragon.getBoundingBox().inflate(DragonEntity.POWER_RADIUS),
						living -> living != dragon && living.isAlive() && dragon.isFriend(living, owner))) {
					friend.heal(8F);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, friend.getX(), friend.getY() + friend.getBbHeight(), friend.getZ(), 6, 0.4, 0.3, 0.4, 0);
				}
				for (LivingEntity foe : foes) {
					foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), dragon);
				}
			}
			case PHOENIX -> { // Rebirth Blaze: a ring of fire, and the phoenix and its riders rise renewed from it
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 300, 5, 1.5, 5, 0.08);
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 50, 4, 1, 4, 0);
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1, z, 60, 1, 1, 1, 0.3);
				level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 2F, 0.7F);
				dragon.heal(20F);
				dragon.shield(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				for (Entity passenger : dragon.getPassengers()) {
					if (passenger instanceof Player friend) {
						friend.heal(8F);
					}
				}
				for (LivingEntity foe : foes) {
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 6F);
					foe.setRemainingFireTicks(Math.max(foe.getRemainingFireTicks(), 200));
				}
			}
			case NIMBUS -> { // Rain Cloud: a raincloud bursts over every foe
				level.playSound(null, x, y, z, SoundEvents.WEATHER_RAIN, SoundSource.NEUTRAL, 3F, 0.8F);
				for (LivingEntity foe : foes) {
					level.sendParticles(ParticleTypes.CLOUD, foe.getX(), foe.getY() + foe.getBbHeight() + 2.5, foe.getZ(), 30, 1, 0.3, 1, 0);
					level.sendParticles(ParticleTypes.FALLING_WATER, foe.getX(), foe.getY() + foe.getBbHeight() + 2, foe.getZ(), 80, 1, 0.5, 1, 0);
					foe.clearFire();
					boolean soaked = foe.isSensitiveToWater();
					dragon.powerHit(level, foe, soaked ? dragon.damageSources().drown() : dragon.damageSources().mobAttack(dragon), soaked ? 8F : 3F);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), dragon);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), dragon);
				}
				for (Entity passenger : dragon.getPassengers()) {
					passenger.clearFire();
				}
			}
			case GOLD -> { // Golden Hoard: the dragon and its riders gleam with golden armor, and a wave of gold hurls foes back
				level.sendParticles(GOLD_COIN, x, y, z, 160, 3, 1.5, 3, 0.3);
				level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 120, 4, 2, 4, 0.1);
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 2F, 0.8F);
				level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.NEUTRAL, 2F, 0.8F);
				dragon.shield(new MobEffectInstance(MobEffects.RESISTANCE, 400, 1), new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
				for (LivingEntity foe : foes) {
					Vec3 away = foe.position().subtract(dragon.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
					dragon.powerHit(level, foe, dragon.damageSources().mobAttack(dragon), 4F);
					foe.push(away.x * 1.6, 0.4, away.z * 1.6);
					foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), dragon);
				}
			}
			case PEARL -> blink(dragon, level, rider); // Blink: across the sky in the blink of an eye
			default -> {
			}
		}
	}

	/** A Pearl dragon blinks its riders up to {@value #BLINK_RANGE} blocks the way its rider looks, stopping short of walls. */
	private static void blink(DragonEntity dragon, ServerLevel level, Player rider) {
		Vec3 look = rider.getLookAngle();
		Vec3 from = dragon.position().add(0, dragon.getBbHeight() * 0.5, 0);
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(look.scale(BLINK_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
		double reach = (hit.getType() == HitResult.Type.MISS ? BLINK_RANGE : hit.getLocation().distanceTo(from)) - dragon.getBbWidth() - 1;
		if (reach < 3) {
			return;
		}
		Vec3 to = dragon.position().add(look.scale(reach));
		level.sendParticles(ParticleTypes.PORTAL, dragon.getX(), dragon.getY() + 1, dragon.getZ(), 120, 1.2, 1, 1.2, 0.4);
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.5F, 1.2F);
		dragon.teleportTo(to.x, to.y, to.z);
		dragon.resetFallDistance();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 120, 1.2, 1, 1.2, 0.2);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.5F, 1.4F);
		// Its rider's own game is steering it: tell it where it is now, or it would pull the dragon back.
		if (dragon.getControllingPassenger() instanceof ServerPlayer pilot) {
			pilot.connection.send(ClientboundMoveVehiclePacket.fromEntity(dragon));
		}
		dragon.awardBreath("blink");
	}

	/** Teleports {@code entity} a few blocks away at random, as chorus fruit does, in a puff of portal particles. */
	private static void blinkAway(ServerLevel level, LivingEntity entity, int spread) {
		double x = entity.getX(), y = entity.getY(), z = entity.getZ();
		for (int i = 0; i < 12; i++) {
			double tx = x + (entity.getRandom().nextDouble() - 0.5) * 2 * spread;
			double ty = y + entity.getRandom().nextInt(spread) - spread / 2.0;
			double tz = z + (entity.getRandom().nextDouble() - 0.5) * 2 * spread;
			if (entity.randomTeleport(tx, ty, tz, true, BlockTags.CONSUMABLE_DOES_NOT_TELEPORT_TO)) {
				level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.3);
				level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1F, 1.2F);
				return;
			}
		}
	}

	/** A Mushroom dragon's Fairy Ring: mushrooms sprout in a ring around it, wherever one could grow. */
	private static void fairyRing(DragonEntity dragon, ServerLevel level, Player rider) {
		BlockPos center = dragon.blockPosition();
		for (int i = 0; i < 24; i++) {
			double angle = Math.PI * 2 * i / 24;
			int dx = (int) Math.round(Math.cos(angle) * 5), dz = (int) Math.round(Math.sin(angle) * 5);
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos pos = center.offset(dx, dy, dz);
				BlockState mushroom = (i % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
				if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
					if (mushroom.canSurvive(level, pos) && rider.mayInteract(level, pos)) {
						level.setBlockAndUpdate(pos, mushroom);
					}
					break;
				}
			}
		}
	}

	/** Petals calm a foe: it forgets what it was after and can barely hurt anything for a while. */
	private static void calm(DragonEntity dragon, LivingEntity foe, int ticks) {
		if (foe instanceof Mob mob) {
			mob.setTarget(null);
		}
		foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1), dragon);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks / 2, 0), dragon);
	}

	/** Petals make a grown animal fall in love, ready to breed, as if its rider (or owner) had fed it. */
	private static void inLove(DragonEntity dragon, ServerLevel level, Animal animal) {
		if (animal.getAge() != 0 || !animal.canFallInLove()) {
			return;
		}
		Player lover = dragon.getControllingPassenger() instanceof Player rider ? rider : dragon.getOwner() instanceof Player owner ? owner : null;
		animal.setInLove(lover);
		level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight() + 0.3, animal.getZ(), 3, 0.3, 0.2, 0.3, 0);
		dragon.awardBreath("spring_fever");
	}

	// ---- Gifts that need to watch for harm -----------------------------------------------------------------

	/** A Phoenix dragon's rider cheats death; arrows pass a Nimbus dragon's rider by; a Star dragon's rider gets more experience. */
	static void registerEvents() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Player player && player.getVehicle() instanceof DragonEntity dragon && dragon.element() == DragonElement.PHOENIX
					&& dragon.isTame() && dragon.rebirthReady() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
					&& player.level() instanceof ServerLevel level) {
				dragon.useRebirth();
				player.setHealth(6F);
				player.removeAllEffects();
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 80, 0.6, 0.8, 0.6, 0.4);
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 80, 0.8, 0.8, 0.8, 0.1);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1F, 1.2F);
				if (player instanceof ServerPlayer serverPlayer) {
					WonderAdvancements.award(serverPlayer, "second_life");
				}
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.is(DamageTypeTags.IS_PROJECTILE) && entity.getVehicle() instanceof DragonEntity dragon && dragon.element() == DragonElement.NIMBUS
					&& entity.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
				return false; // lost in the mist
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Mob) || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			Entity killer = source.getEntity();
			boolean lucky = killer instanceof Player player && player.getVehicle() instanceof DragonEntity mount && mount.element() == DragonElement.STAR
				|| killer instanceof DragonEntity star && star.element() == DragonElement.STAR && star.isTame();
			if (lucky) {
				ExperienceOrb.award(level, entity.position(), 3 + level.getRandom().nextInt(6));
				level.sendParticles(ParticleTypes.FIREWORK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
			}
		});
	}
}
