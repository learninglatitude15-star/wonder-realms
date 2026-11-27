package com.wonderrealms.whale;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.WonderAdvancements;
import java.util.Comparator;
import java.util.UUID;
import com.wonderrealms.WonderRealms;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A sky whale: an enormous, gentle creature that swims through the air high over the world, singing to itself. Up
 * to four players can sit on its back and ride along wherever it drifts. Put a saddle on it and whoever sits at the
 * front steers: it goes where they look, climbs while they hold jump, and simply floats when they let go. Whales
 * love fish, and will come down to anyone holding one. Climb off high up and it lets you down gently.
 */
public class SkyWhaleEntity extends PathfinderMob implements GeoEntity {
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(SkyWhaleEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> CALF = SynchedEntityData.defineId(SkyWhaleEntity.class, EntityDataSerializers.BOOLEAN);
	/** A calf is this much of a grown whale's size and health, and grows up after this long (a Minecraft day). */
	private static final double CALF_SIZE = 0.4;
	private static final int GROW_UP = 24000;
	/** Each fish fed to a calf brings growing up this much closer. */
	private static final int FISH_GROWTH = 2400;
	/** How big it is next to its model. */
	public static final double SCALE = 2.5;
	/** How many can ride on its back, and where they sit: how far forward of its middle, and how high, in model blocks. */
	private static final int SEATS = 4;
	private static final double[] SEAT_FORWARD = {0.75, 0.3, -0.15, -0.6};
	private static final double SEAT_HEIGHT = 1.1;
	/** Cruising speed on its own (blocks a tick) and how gently it changes course. */
	private static final double CRUISE = 0.12, EASE = 0.02;
	/** It likes to swim at least this far over the ground, and no lower than this, unless someone's holding a fish. */
	private static final int CLEARANCE = 24, MIN_Y = 130;
	/** Within this many blocks of a player, a wild whale never swims off. */
	private static final double STAY_NEAR = 96;
	/** Turning, in degrees a tick: slow, as befits a whale. */
	private static final float TURN = 2.5F;

	private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("move.swim");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private Vec3 drift;
	/** How long a calf has been growing, and the whale it swims beside. */
	private int growth;
	private UUID mother;

	public SkyWhaleEntity(EntityType<? extends SkyWhaleEntity> type, Level level) {
		super(type, level);
		// Size its hitbox by its scale attribute as its model is sized (a fresh one isn't, until something changes it).
		this.refreshDimensions();
		this.setNoGravity(true);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 120.0)
			.add(Attributes.ARMOR, 4.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.12)
			.add(Attributes.FLYING_SPEED, 0.32)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SADDLED, false);
		builder.define(CALF, false);
	}

	public boolean isCalf() {
		return this.entityData.get(CALF);
	}

	/** Makes it a calf (smaller, and too young to ride) swimming beside {@code mother}, or grows it up. */
	public void setCalf(boolean calf, UUID mother) {
		this.entityData.set(CALF, calf);
		this.mother = calf ? mother : null;
		Identifier id = WonderRealms.id("calf");
		for (AttributeInstance attribute : new AttributeInstance[] {this.getAttribute(Attributes.SCALE), this.getAttribute(Attributes.MAX_HEALTH)}) {
			if (attribute != null) {
				attribute.removeModifier(id);
				if (calf) {
					attribute.addTransientModifier(new AttributeModifier(id, CALF_SIZE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
		this.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
	}

	public boolean isSaddled() {
		return this.entityData.get(SADDLED);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("saddled", this.isSaddled());
		output.putBoolean("calf", this.isCalf());
		output.putInt("growth", this.growth);
		if (this.mother != null) {
			output.putLong("mother_most", this.mother.getMostSignificantBits());
			output.putLong("mother_least", this.mother.getLeastSignificantBits());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SADDLED, input.getBooleanOr("saddled", false));
		this.growth = input.getIntOr("growth", 0);
		long most = input.getLongOr("mother_most", 0L), least = input.getLongOr("mother_least", 0L);
		this.setCalf(input.getBooleanOr("calf", false), most == 0L && least == 0L ? null : new UUID(most, least));
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** A wild whale swims off once nobody's within a hundred blocks or so; a saddled or ridden one stays. */
	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return !this.isSaddled() && !this.isVehicle() && distanceSqr > STAY_NEAR * STAY_NEAR;
	}

	// ---- Swimming through the sky ---------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.isCalf()) {
			if (++this.growth >= GROW_UP) {
				this.growUp(level);
			} else if (this.mother != null && level.getEntity(this.mother) instanceof SkyWhaleEntity mom && mom.isAlive() && mom.distanceToSqr(this) < 96 * 96) {
				// A calf keeps close beside its mother.
				Vec3 beside = Vec3.directionFromRotation(0F, mom.getYRot() + 90F).scale(mom.getBbWidth() * 0.9);
				this.drift = mom.position().add(beside).add(0, 1, 0);
				return;
			}
		}
		Player fish = this.fishHolder(level);
		if (fish != null && !this.isVehicle()) {
			// Someone's holding a fish: come and hang just over their head, facing them.
			Vec3 over = fish.position().add(0, 3 + this.getBbHeight() * 0.3, 0);
			Vec3 away = this.position().subtract(fish.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(4 + this.getBbWidth() * 0.5);
			this.drift = over.add(away);
		} else if (this.drift == null || this.drift.distanceToSqr(this.position()) < 16 || this.random.nextInt(400) == 0 || this.horizontalCollision) {
			this.drift = this.wander(level);
		}
		if (this.tickCount % 20 == 0 && this.getControllingPassenger() instanceof ServerPlayer captain
				&& this.getDeltaMovement().lengthSqr() > 0.01) {
			WonderAdvancements.award(captain, "whale_captain");
		}
		if (this.tickCount % 200 == 0 && this.getHealth() < this.getMaxHealth()) {
			this.heal(2F);
		}
	}

	/** The nearest player holding a fish within its follow range, if any. */
	private Player fishHolder(ServerLevel level) {
		double range = this.getAttributeValue(Attributes.FOLLOW_RANGE);
		return level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(range), player -> !player.isSpectator()
				&& (player.getMainHandItem().is(ItemTags.FISHES) || player.getOffhandItem().is(ItemTags.FISHES)))
			.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/** Somewhere to swim to: a few dozen blocks off, well clear of the ground, and high in the sky. */
	private Vec3 wander(ServerLevel level) {
		double angle = this.random.nextDouble() * Math.PI * 2;
		double distance = 24 + this.random.nextDouble() * 40;
		double x = this.getX() + Math.cos(angle) * distance;
		double z = this.getZ() + Math.sin(angle) * distance;
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
		double floor = Math.max(ground + CLEARANCE, MIN_Y);
		double y = Mth.clamp(this.getY() + this.random.nextGaussian() * 6, floor, Math.max(floor, level.getMaxY() - 24));
		return new Vec3(x, y, z);
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player captain) {
			this.steered(captain, input);
		} else if (!this.level().isClientSide()) {
			Vec3 to = this.drift == null ? Vec3.ZERO : this.drift.subtract(this.position());
			double length = to.length();
			Vec3 wanted = length < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(CRUISE, length * 0.05) / length);
			Vec3 motion = this.getDeltaMovement().lerp(wanted, EASE);
			this.setDeltaMovement(motion);
			this.move(MoverType.SELF, motion);
			if (motion.horizontalDistanceSqr() > 1.0E-4) {
				this.turnTowards((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90F);
			}
		}
		this.calculateEntityAnimation(true);
	}

	/** A saddled whale goes where its captain looks, climbs while they hold jump, and floats when they let go. */
	private void steered(Player captain, Vec3 input) {
		Vec3 look = captain.getLookAngle();
		double speed = this.getAttributeValue(Attributes.FLYING_SPEED);
		Vec3 wanted = look.scale(input.z * speed);
		if (captain.isJumping()) {
			wanted = wanted.add(0, 0.18, 0);
		}
		Vec3 motion = this.getDeltaMovement().lerp(wanted, 0.04);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		this.turnTowards(captain.getYRot());
	}

	private void turnTowards(float yaw) {
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, TURN));
		this.yRotO = this.getYRot();
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 input) {
		float forward = rider.zza;
		return new Vec3(0, 0, forward < 0 ? forward * 0.3F : forward);
	}

	@Override
	protected float getRiddenSpeed(Player rider) {
		return (float) this.getAttributeValue(Attributes.FLYING_SPEED);
	}

	// ---- Riding ---------------------------------------------------------------------------------------------

	@Override
	public LivingEntity getControllingPassenger() {
		return this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return !this.isCalf() && this.getPassengers().size() < SEATS;
	}

	private void growUp(ServerLevel level) {
		this.setCalf(false, null);
		this.setHealth(this.getMaxHealth());
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), 20, 2, 1, 2, 0);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int seat = Math.max(0, Math.min(SEATS - 1, this.getPassengers().indexOf(passenger)));
		return new Vec3(0, SEAT_HEIGHT * scale, SEAT_FORWARD[seat] * scale).yRot(-this.yBodyRot * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof LivingEntity living) {
			living.yBodyRot = this.yBodyRot;
		}
	}

	/** Climb off high in the sky and the whale lets you drift down gently. */
	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (passenger instanceof Player player && !this.level().isClientSide()) {
			int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
			if (this.getY() - ground > 4) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false, true), this);
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean server = !this.level().isClientSide();
		if (this.isCalf()) {
			// Calves are too young to ride or saddle, but they love a fish, and every one helps them grow.
			if (!stack.is(ItemTags.FISHES)) {
				return InteractionResult.PASS;
			}
			if (server) {
				stack.consume(1, player);
				this.growth += FISH_GROWTH;
				this.heal(5F);
				((ServerLevel) this.level()).sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), 4, 0.6, 0.3, 0.6, 0);
				this.playSound(SoundEvents.HAPPY_GHAST_AMBIENT, 1F, 1.1F);
				if (player instanceof ServerPlayer serverPlayer) {
					WonderAdvancements.award(serverPlayer, "whale_calf");
				}
				if (this.growth >= GROW_UP) {
					this.growUp((ServerLevel) this.level());
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ItemTags.FISHES)) {
			if (this.getHealth() >= this.getMaxHealth()) {
				return InteractionResult.PASS;
			}
			if (server) {
				stack.consume(1, player);
				this.heal(15F);
				((ServerLevel) this.level()).sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight(), this.getZ(),
					5, 1.5, 0.5, 1.5, 0);
				this.playSound(SoundEvents.HAPPY_GHAST_AMBIENT, 2F, 0.6F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.SADDLE) && !this.isSaddled()) {
			if (server) {
				stack.consume(1, player);
				this.entityData.set(SADDLED, true);
				this.setPersistenceRequired();
				this.playSound(SoundEvents.HORSE_SADDLE.value(), 1F, 0.6F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.SHEARS) && this.isSaddled() && !this.isVehicle()) {
			if (server) {
				this.entityData.set(SADDLED, false);
				this.spawnAtLocation((ServerLevel) this.level(), new ItemStack(Items.SADDLE));
				this.playSound(SoundEvents.SHEEP_SHEAR, 1F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty() && this.canAddPassenger(player) && !player.isPassenger()) {
			if (server && player.startRiding(this) && player instanceof ServerPlayer serverPlayer) {
				WonderAdvancements.award(serverPlayer, "sky_whale");
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (this.isSaddled()) {
			this.spawnAtLocation(level, new ItemStack(Items.SADDLE));
		}
	}

	// ---- Sounds and animation -------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HAPPY_GHAST_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HAPPY_GHAST_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HAPPY_GHAST_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3F;
	}

	@Override
	public float getVoicePitch() {
		return this.isCalf() ? Mth.randomBetween(this.random, 0.9F, 1.1F) // a calf's song is higher
			: Mth.randomBetween(this.random, 0.45F, 0.55F); // a deep, slow song
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<SkyWhaleEntity>("main", 0, test -> test.setAndContinue(SWIM)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
