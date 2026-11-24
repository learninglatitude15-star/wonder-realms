package com.wonderrealms.whale;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.overcloud.Overcloud;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Storm Leviathan: a vast, dark whale that rides thunderstorms high over the world, and the second Wonder Realms
 * boss. It circles its prey, then charges to ram it, and calls lightning down where it stands (a crackle of sparks
 * gives a moment to dodge). Lightning can't hurt it. When the storm passes with no one to hunt, it swims off into the
 * clouds. Beat it for a trident of channeling and loyalty, a heart of the sea, nautilus shells and diamonds.
 */
public class StormLeviathanEntity extends Monster implements GeoEntity {
	/** How big it is next to the sky whale's model: far bigger than any whale. */
	public static final double SCALE = 4.0;
	/** How far off it spots prey, and how far its boss bar shows. */
	private static final int SIGHT = 64, BAR_RANGE = 96;
	/** Circling its prey: how far out, how high above, and how fast. */
	private static final double ORBIT_RADIUS = 22, ORBIT_HEIGHT = 10, CRUISE = 0.35, ORBIT_STEP = 0.012;
	/** Its charge: how fast, for how long, and how often. */
	private static final double CHARGE_SPEED = 1.1;
	private static final int CHARGE_TICKS = 60, CHARGE_EVERY = 200;
	/** Its lightning: how often, how long the warning sparks crackle first, and how far the strike reaches. */
	private static final int BOLT_EVERY = 100, BOLT_DELAY = 30;
	private static final double BOLT_RADIUS = 3;
	private static final float RAM_DAMAGE = 14F, BOLT_DAMAGE = 8F;
	/** Once the storm's over, it swims off after this long with nobody to hunt. */
	private static final int CALM_TICKS = 1200;

	private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("move.swim");

	private record Strike(Vec3 at, int when) {
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.wonderrealms.storm_leviathan"),
		BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final List<Strike> strikes = new ArrayList<>();
	private int chargeTimer = CHARGE_EVERY, charging, boltTimer = BOLT_EVERY / 2, calm;
	private Vec3 chargeDirection, drift;
	private double orbit;

	public StormLeviathanEntity(EntityType<? extends StormLeviathanEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 300;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 300.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ATTACK_DAMAGE, RAM_DAMAGE)
			.add(Attributes.FOLLOW_RANGE, SIGHT)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FLYING_SPEED, 0.4)
			.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
			(target, level) -> this.distanceToSqr(target) < SIGHT * SIGHT));
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** It rides the storm: lightning can't hurt it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return !source.is(DamageTypes.LIGHTNING_BOLT) && super.hurtServer(level, source, amount);
	}

	// ---- The hunt -------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.bossBar.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.tickCount % 10 == 0) {
			this.updateWatchers(level);
		}
		// Lightning it called down lands now.
		this.strikes.removeIf(strike -> {
			if (this.tickCount < strike.when()) {
				return false;
			}
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.snapTo(strike.at().x, strike.at().y, strike.at().z);
				level.addFreshEntity(bolt);
			}
			for (Player player : level.getEntitiesOfClass(Player.class, new AABB(strike.at(), strike.at()).inflate(BOLT_RADIUS))) {
				if (!player.isCreative() && !player.isSpectator()) {
					player.hurtServer(level, this.damageSources().lightningBolt(), BOLT_DAMAGE);
				}
			}
			return true;
		});
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive() || target instanceof Player player && (player.isCreative() || player.isSpectator())) {
			this.charging = 0;
			if (!level.isThundering() && ++this.calm > CALM_TICKS) {
				// The storm's over, and it swims away into the clouds.
				level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 80, 4, 2, 4, 0.05);
				this.discard();
			}
			return;
		}
		this.calm = 0;
		if (this.charging > 0) {
			this.charging--;
			if (this.getBoundingBox().inflate(1).intersects(target.getBoundingBox())) {
				// Rammed!
				target.hurtServer(level, this.damageSources().mobAttack(this), RAM_DAMAGE);
				Vec3 push = this.chargeDirection.multiply(1, 0, 1);
				push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(2);
				target.push(push.x, 0.8, push.z);
				this.charging = 0;
			}
		} else if (--this.chargeTimer <= 0) {
			Vec3 to = target.getEyePosition().subtract(this.position().add(0, this.getBbHeight() * 0.5, 0));
			if (to.lengthSqr() > 1.0E-4) {
				this.chargeDirection = to.normalize();
				this.charging = CHARGE_TICKS;
				this.playSound(SoundEvents.RAVAGER_ROAR, 4F, 0.5F);
			}
			this.chargeTimer = CHARGE_EVERY + this.random.nextInt(80);
		}
		if (--this.boltTimer <= 0) {
			// Lightning where its prey stands, after a crackle of sparks.
			Vec3 at = target.position();
			this.strikes.add(new Strike(at, this.tickCount + BOLT_DELAY));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 60, BOLT_RADIUS * 0.5, 0.1, BOLT_RADIUS * 0.5, 0.05);
			this.boltTimer = BOLT_EVERY + this.random.nextInt(40);
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (this.level().isClientSide()) {
			this.calculateEntityAnimation(true);
			return;
		}
		LivingEntity target = this.getTarget();
		Vec3 wanted;
		double ease = 0.03;
		if (this.charging > 0 && this.chargeDirection != null) {
			wanted = this.chargeDirection.scale(CHARGE_SPEED);
			ease = 0.2;
		} else if (target != null) {
			this.orbit += ORBIT_STEP;
			wanted = this.toward(target.position().add(Math.cos(this.orbit) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(this.orbit) * ORBIT_RADIUS), CRUISE);
		} else {
			if (this.drift == null || this.drift.distanceToSqr(this.position()) < 16 || this.random.nextInt(300) == 0 || this.horizontalCollision) {
				this.drift = this.wander();
			}
			wanted = this.toward(this.drift, CRUISE * 0.5);
		}
		Vec3 motion = this.getDeltaMovement().lerp(wanted, ease);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		if (motion.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, this.charging > 0 ? 8F : 3F));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
		this.calculateEntityAnimation(true);
	}

	private Vec3 toward(Vec3 point, double speed) {
		Vec3 to = point.subtract(this.position());
		double length = to.length();
		return length < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(speed, length * 0.05) / length);
	}

	/** Somewhere to swim to in the storm: off in some direction, high in the sky. */
	private Vec3 wander() {
		double angle = this.random.nextDouble() * Math.PI * 2;
		double x = this.getX() + Math.cos(angle) * 40, z = this.getZ() + Math.sin(angle) * 40;
		int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		double floor = Math.max(ground + 30, 140);
		return new Vec3(x, Mth.clamp(this.getY() + this.random.nextGaussian() * 6, floor, Math.max(floor, this.level().getMaxY() - 24)), z);
	}

	// ---- Boss bar and spoils --------------------------------------------------------------------------------

	private void updateWatchers(ServerLevel level) {
		Set<ServerPlayer> near = new HashSet<>(level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE));
		for (ServerPlayer player : List.copyOf(this.bossBar.getPlayers())) {
			if (!near.contains(player)) {
				this.bossBar.removePlayer(player);
			}
		}
		for (ServerPlayer player : near) {
			if (!this.bossBar.getPlayers().contains(player)) {
				this.bossBar.addPlayer(player);
				WonderAdvancements.award(player, "leviathan_sighted");
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		for (ServerPlayer player : List.copyOf(this.bossBar.getPlayers())) {
			WonderAdvancements.award(player, "storm_leviathan");
		}
		this.bossBar.removeAllPlayers();
	}

	/** A trident of channeling and loyalty, a heart of the sea, nautilus shells, diamonds and prismarine crystals. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		Registry<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack trident = new ItemStack(Items.TRIDENT);
		trident.enchant(enchantments.getOrThrow(Enchantments.CHANNELING), 1);
		trident.enchant(enchantments.getOrThrow(Enchantments.LOYALTY), 2);
		this.spawnAtLocation(level, trident);
		this.spawnAtLocation(level, new ItemStack(Items.HEART_OF_THE_SEA));
		this.spawnAtLocation(level, new ItemStack(Items.NAUTILUS_SHELL, 3 + this.random.nextInt(4)));
		this.spawnAtLocation(level, new ItemStack(Items.DIAMOND, 2 + this.random.nextInt(4)));
		this.spawnAtLocation(level, new ItemStack(Items.PRISMARINE_CRYSTALS, 4 + this.random.nextInt(5)));
		this.spawnAtLocation(level, new ItemStack(Overcloud.STORM_EYE)); // the way up into the Overcloud
	}

	// ---- Sounds and animation -------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ELDER_GUARDIAN_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 5F;
	}

	@Override
	public float getVoicePitch() {
		return Mth.randomBetween(this.random, 0.4F, 0.5F);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<StormLeviathanEntity>("main", 0, test -> test.setAndContinue(SWIM)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
