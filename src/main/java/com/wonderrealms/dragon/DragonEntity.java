package com.wonderrealms.dragon;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.airship.AirshipEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A dragon. Hatchlings come from eggs, bonded to whoever was nearest, and grow to full size over two days (faster
 * if fed), gaining health as they grow. Wild adults live only on sky islands: guarding a roost, or carrying a
 * pillager at a sky outpost. Tamed dragons follow and defend their owner, fly after them when they take to the air,
 * and lay an egg every couple of days.
 * <p>
 * Breath is a short stream of the dragon's element: fire sets targets alight and melts ice and snow, frost slows
 * and freezes (and turns water to ice you can walk on), storm crackles and sometimes calls lightning, ender breath
 * lifts targets off their feet.
 * <p>
 * A grown, saddled dragon can be ridden: jump (or look up while moving) to take off, steer by looking, hold jump to
 * climb, dive to gain speed, and press the breath key to breathe. Sneak and use it with an empty hand to make it
 * sit or follow.
 */
public class DragonEntity extends TamableAnimal implements GeoEntity {
	private static final EntityDataAccessor<Float> GROWTH = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);

	// Growth
	/** Ticks from hatchling to full size without feeding (two in-game days). */
	private static final int GROW_TICKS = 48000;
	private static final float FOOD_GROWTH = 0.08F;
	/** Hatchlings are this fraction of full size, and have this much of a grown dragon's health. */
	private static final float HATCHLING_SCALE = 0.3F;
	private static final double HATCHLING_HEALTH = 20, GROWN_HEALTH = 80;

	// Breath
	private static final float BREATH_RANGE = 10F;
	private static final double BREATH_CONE_COS = Math.cos(Math.toRadians(25));
	/** A breath lasts this long, and hurts what it touches every few ticks. */
	private static final int BREATH_TICKS = 14;
	private static final int BREATH_HIT_INTERVAL = 3;
	/** Rest after a breath before the next one: short when a rider calls for it, longer for the dragon's own fights. */
	private static final int RIDDEN_BREATH_REST = 16;
	private static final int AI_BREATH_REST = 50;

	// Wild behaviour
	/** How close a wild adult lets players come. */
	private static final double TERRITORY = 12;
	/** A pillager's dragon spots players this far off. */
	private static final double RAIDER_SIGHT = 32;
	/** How far around its outpost pillagers must be cleared before a captive dragon can be tamed. */
	private static final double OUTPOST_RADIUS = 48;
	/** A roost guardian wanders this far from its nest, and flies back if it ends up farther than the leash. */
	private static final int ROOST_RADIUS = 8;
	private static final double HOME_LEASH = 20;
	/** Taming chance per feeding: hatchlings and freed dragons take to you quickly, wild adults much less so. */
	private static final int EASY_TAME_ODDS = 3;
	private static final int ADULT_TAME_ODDS = 8;
	/** Ticks between eggs from a tamed, grown dragon (two to three in-game days). */
	private static final int EGG_INTERVAL = 48000;
	private static final int EGG_JITTER = 24000;

	// Flight paths. A pillager's crossbow reaches 8 blocks, so riders hover close enough for it to shoot.
	private static final double ATTACK_RING = 4, ATTACK_HEIGHT = 3.5;
	private static final double PATROL_RING = 16, PATROL_HEIGHT = 6;
	private static final double FOLLOW_RING = 5, FOLLOW_HEIGHT = 2.5;
	/** Players at least this high on a dragon earn "Touch the Sky". */
	private static final int SKY_HIGH_Y = 256;
	/** The saddle: on the middle of the back (full size), just ahead of the back spikes. */
	private static final double SEAT_HEIGHT = 1.4, SEAT_BACK = 0.06;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
	private static final RawAnimation SIT = RawAnimation.begin().thenLoop("misc.sit");
	private static final RawAnimation BREATH = RawAnimation.begin().thenPlay("attack.breath");

	private final DragonElement element;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int breathCooldown;
	private int breathTicks;
	private boolean breathRidden;
	private Vec3 breathDirection = Vec3.ZERO;
	private int eggTimer = EGG_INTERVAL;
	/** The island a wild dragon calls home: its roost, or the outpost it guards for the pillagers. */
	private BlockPos home;
	/** Raised by pillagers: can't be tamed until their outpost is conquered. */
	private boolean captive;
	private boolean conquered;
	private long conqueredCheckedAt = Long.MIN_VALUE;

	public DragonEntity(EntityType<? extends DragonEntity> type, Level level, DragonElement element) {
		super(type, level);
		this.element = element;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, GROWN_HEALTH)
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.FLYING_SPEED, 0.9)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.STEP_HEIGHT, 1.1)
			.add(Attributes.TEMPT_RANGE, 10.0);
	}

	public DragonElement element() {
		return this.element;
	}

	// ---- State ----------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GROWTH, 1F);
		builder.define(SADDLED, false);
		builder.define(FLYING, false);
	}

	public float growth() {
		return this.entityData.get(GROWTH);
	}

	/** Sets how grown the dragon is (0 hatchling, 1 adult). Size and health follow; it keeps its share of health. */
	public void setGrowth(float growth) {
		this.entityData.set(GROWTH, Mth.clamp(growth, 0F, 1F));
		this.applyGrowth(true);
	}

	private void applyGrowth(boolean keepHealthShare) {
		if (this.level().isClientSide()) {
			return;
		}
		float growth = this.growth();
		this.getAttribute(Attributes.SCALE).setBaseValue(HATCHLING_SCALE + (1F - HATCHLING_SCALE) * growth);
		double oldMax = this.getMaxHealth();
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Mth.lerp(growth, HATCHLING_HEALTH, GROWN_HEALTH));
		float max = this.getMaxHealth();
		float health = keepHealthShare && oldMax > 0 ? (float) (this.getHealth() * max / oldMax) : this.getHealth();
		this.setHealth(Math.min(health, max));
	}

	public boolean isGrown() {
		return this.growth() >= 1F;
	}

	public boolean isSaddled() {
		return this.entityData.get(SADDLED);
	}

	public boolean isFlying() {
		return this.entityData.get(FLYING);
	}

	private void setFlying(boolean flying) {
		if (flying != this.isFlying()) {
			this.entityData.set(FLYING, flying);
		}
		this.setNoGravity(flying);
	}

	/**
	 * Makes this a wild dragon of a sky island. Roost guardians stay near their nest; captive (pillager) dragons
	 * patrol the outpost and can't be tamed until it falls.
	 */
	public void setIslandHome(BlockPos center, boolean captive) {
		this.home = center.immutable();
		this.captive = captive;
		if (!captive) {
			this.setHomeTo(this.home, ROOST_RADIUS);
		}
		this.setPersistenceRequired();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putFloat("growth", this.growth());
		output.putBoolean("saddled", this.isSaddled());
		output.putInt("egg_timer", this.eggTimer);
		output.putBoolean("captive", this.captive);
		if (this.home != null) {
			output.putLong("island_home", this.home.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(GROWTH, Mth.clamp(input.getFloatOr("growth", 1F), 0F, 1F));
		this.applyGrowth(false);
		this.entityData.set(SADDLED, input.getBooleanOr("saddled", false));
		this.eggTimer = input.getIntOr("egg_timer", EGG_INTERVAL);
		this.captive = input.getBooleanOr("captive", false);
		long home = input.getLongOr("island_home", input.getLongOr("home", Long.MIN_VALUE));
		this.home = home == Long.MIN_VALUE ? null : BlockPos.of(home);
		if (this.home != null && !this.captive && !this.isTame()) {
			this.setHomeTo(this.home, ROOST_RADIUS);
		}
	}

	// ---- AI -------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new DragonBreathGoal(this));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 10F, 3F));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
		// Wild adults guard their island: come within a dozen blocks (or anywhere near a pillager's dragon) and they come for you.
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
			(target, level) -> !this.isTame() && this.isGrown() && !this.isFreed()
				&& this.distanceToSqr(target) < (this.isRaiderMounted() ? RAIDER_SIGHT * RAIDER_SIGHT : TERRITORY * TERRITORY)));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.breathCooldown > 0) {
			this.breathCooldown--;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.breathTicks > 0) {
			this.tickBreath(level);
		}
		if (!this.isGrown() && this.tickCount % 20 == 0) {
			this.setGrowth(this.growth() + 20F / GROW_TICKS);
		}
		// A tamed, grown dragon lays an egg every couple of days.
		if (this.isTame() && this.isGrown() && --this.eggTimer <= 0) {
			this.spawnAtLocation(level, Dragons.eggOf(this.element));
			this.playSound(SoundEvents.TURTLE_LAY_EGG, 1F, 0.7F);
			this.eggTimer = EGG_INTERVAL + this.random.nextInt(EGG_JITTER);
		}
		boolean airborne = this.isFlying() || (this.isVehicle() && !this.onGround());
		if (airborne && this.tickCount % 16 == 0) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, this.getSoundSource(),
				0.5F + 0.6F * this.growth(), 0.9F + 0.5F * (1F - this.growth()) + this.random.nextFloat() * 0.1F);
		}
		// Freed dragons let you know they'd welcome a friend.
		if (this.tickCount % 60 == 0 && this.isFreed()) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + this.getBbHeight(), this.getZ(), 4, 0.6, 0.3, 0.6, 0.0);
		}
		if (this.getControllingPassenger() instanceof ServerPlayer rider && this.getY() > SKY_HIGH_Y) {
			WonderAdvancements.award(rider, "touch_the_sky");
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false; // wings
	}

	// ---- Interaction ----------------------------------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return this.element.isFood(stack);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean server = !this.level().isClientSide();

		if (this.isRaiderMounted()) {
			return InteractionResult.PASS; // deal with its rider first
		}
		if (!this.isTame()) {
			if (!this.isFood(stack)) {
				return super.mobInteract(player, hand);
			}
			if (server && this.captive && !this.outpostConquered()) {
				// Pillagers' dragons won't be won over while their masters still hold the outpost.
				this.level().broadcastEntityEvent(this, (byte) 6);
				player.sendOverlayMessage(Component.translatable("message.wonderrealms.outpost_held").withStyle(ChatFormatting.RED));
				return InteractionResult.SUCCESS;
			}
			if (server) {
				stack.consume(1, player);
				// Hatchlings and freed dragons take to you quickly; a wild adult takes patience.
				int odds = this.captive || !this.isGrown() ? EASY_TAME_ODDS : ADULT_TAME_ODDS;
				if (this.random.nextInt(odds) == 0) {
					if (this.captive && player instanceof ServerPlayer serverPlayer) {
						WonderAdvancements.award(serverPlayer, "liberate_dragon");
					}
					this.setTarget(null);
					this.captive = false;
					this.home = null;
					this.clearHome();
					this.tame(player);
					this.level().broadcastEntityEvent(this, (byte) 7);
				} else {
					this.level().broadcastEntityEvent(this, (byte) 6);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.isOwnedBy(player)) {
			return InteractionResult.PASS;
		}

		if (this.isFood(stack) && (this.getHealth() < this.getMaxHealth() || !this.isGrown())) {
			if (server) {
				stack.consume(1, player);
				this.heal(8F);
				this.setGrowth(this.growth() + FOOD_GROWTH);
				this.level().broadcastEntityEvent(this, (byte) 7);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 1F, 0.8F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.SADDLE) && !this.isSaddled()) {
			if (server) {
				if (!this.isGrown()) {
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.too_young").withStyle(ChatFormatting.YELLOW));
				} else {
					stack.consume(1, player);
					this.entityData.set(SADDLED, true);
					this.playSound(SoundEvents.HORSE_SADDLE.value(), 1F, 0.8F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (server) {
				boolean sit = !this.isOrderedToSit();
				this.setOrderedToSit(sit);
				this.setInSittingPose(sit);
				this.getNavigation().stop();
				this.setTarget(null);
				player.sendOverlayMessage(Component.translatable(sit ? "message.wonderrealms.dragon_sits" : "message.wonderrealms.dragon_follows"));
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty() && this.isSaddled() && this.isGrown() && !this.isVehicle()) {
			if (server) {
				this.setOrderedToSit(false);
				this.setInSittingPose(false);
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** Killing a wild adult is the other way to get its egg. A saddle comes off too. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (!this.isTame() && this.isGrown()) {
			this.spawnAtLocation(level, Dragons.eggOf(this.element));
		}
		if (this.isSaddled()) {
			this.spawnAtLocation(level, new ItemStack(Items.SADDLE));
		}
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null; // dragons come from eggs
	}

	@Override
	public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
		return false;
	}

	// ---- Riding ---------------------------------------------------------------------------------------------

	@Override
	public LivingEntity getControllingPassenger() {
		if (this.isSaddled() && this.getFirstPassenger() instanceof Player player && this.isOwnedBy(player)) {
			return player;
		}
		return null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, SEAT_HEIGHT * scale, -SEAT_BACK * scale).yRot(-this.yBodyRot * Mth.DEG_TO_RAD);
	}

	@Override
	protected void tickRidden(Player rider, Vec3 input) {
		super.tickRidden(rider, input);
		this.setYRot(rider.getYRot());
		this.yRotO = this.getYRot();
		this.setXRot(rider.getXRot() * 0.5F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		// Jump, or look up while moving forward, to take off. Come down looking level or lower to land.
		if (!this.isFlying() && (rider.isJumping() || (input.z > 0 && rider.getXRot() < -25F))) {
			this.setFlying(true);
			this.setDeltaMovement(this.getDeltaMovement().add(0, 0.6, 0));
		} else if (this.isFlying() && this.onGround() && rider.getXRot() > 0F && !rider.isJumping()) {
			this.setFlying(false);
		}
	}

	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 input) {
		float forward = rider.zza;
		if (forward < 0) {
			forward *= 0.4F;
		}
		return new Vec3(rider.xxa * 0.5F, 0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player rider) {
		return (float) this.getAttributeValue(this.isFlying() ? Attributes.FLYING_SPEED : Attributes.MOVEMENT_SPEED);
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player rider) {
			if (this.isFlying()) {
				this.riddenFlight(rider, input);
				return;
			}
		} else if (!this.level().isClientSide()) {
			Vec3 goal = this.flightGoal();
			if (goal != null) {
				this.flyTowards(goal);
				return;
			}
			if (this.isFlying()) {
				this.glideDown();
				return;
			}
		}
		super.travel(input);
	}

	/** Fly where the rider looks. Diving builds speed, climbing costs some; hold jump to rise. */
	private void riddenFlight(Player rider, Vec3 input) {
		Vec3 look = rider.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
		double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * 0.6
			* (1 + Math.max(0, -look.y) * 0.7 - Math.max(0, look.y) * 0.25);
		Vec3 target = look.scale(input.z * speed).add(side.scale(-input.x * speed * 0.5));
		if (rider.isJumping()) {
			target = target.add(0, 0.35, 0);
		}
		Vec3 motion = this.getDeltaMovement().lerp(target, 0.12);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		this.calculateEntityAnimation(true);
	}

	// ---- Flying on its own ----------------------------------------------------------------------------------

	/**
	 * Where the dragon wants to fly right now, or null to stay on (or return to) the ground:
	 * a pillager's dragon patrols its outpost and dives at players; a tamed dragon follows its owner into the sky;
	 * any dragon chases a target it can't reach on foot; and an island dragon flies home if it ends up off its island.
	 */
	private Vec3 flightGoal() {
		if (this.isOrderedToSit() || this.isPassenger()) {
			return null;
		}
		double angle = this.tickCount * 0.02 + this.getId();
		Vec3 ring = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		LivingEntity target = this.getTarget();
		boolean hasTarget = target != null && target.isAlive() && target.level() == this.level();

		if (this.isRaiderMounted()) {
			if (hasTarget && this.distanceToSqr(target) < RAIDER_SIGHT * RAIDER_SIGHT * 2) {
				return target.position().add(ring.scale(ATTACK_RING)).add(0, ATTACK_HEIGHT, 0);
			}
			return this.homeCenter().add(ring.scale(PATROL_RING)).add(0, PATROL_HEIGHT, 0);
		}
		if (this.isTame() && this.getOwner() instanceof LivingEntity owner && owner.level() == this.level()
				&& isAloft(owner) && this.distanceToSqr(owner) > 36) {
			return owner.position().add(ring.scale(FOLLOW_RING)).add(0, FOLLOW_HEIGHT, 0);
		}
		if (hasTarget && this.growth() >= 0.3F && this.distanceToSqr(target) < 40 * 40
				&& (target.isPassenger() || target.getY() - this.getY() > 3.5 || this.isFlying())) {
			return target.position().add(ring.scale(ATTACK_RING)).add(0, ATTACK_HEIGHT, 0);
		}
		if (this.home != null && !this.isTame()) {
			Vec3 center = this.homeCenter();
			double dx = center.x - this.getX(), dz = center.z - this.getZ();
			if (dx * dx + dz * dz > HOME_LEASH * HOME_LEASH || this.getY() < this.home.getY() - 4) {
				return center.add(0, 2.5, 0);
			}
		}
		return null;
	}

	/** In the air on wings of their own: gliding, on an airship, or riding another dragon. */
	private static boolean isAloft(LivingEntity owner) {
		return owner.isFallFlying() || owner.getVehicle() instanceof AirshipEntity || owner.getVehicle() instanceof DragonEntity;
	}

	private Vec3 homeCenter() {
		return this.home != null ? Vec3.atBottomCenterOf(this.home) : this.position();
	}

	private void flyTowards(Vec3 goal) {
		this.setFlying(true);
		Vec3 to = goal.subtract(this.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		// Face the target while breathing on it; otherwise face where it's going.
		LivingEntity target = this.getTarget();
		Vec3 facing = this.breathTicks > 0 && target != null ? target.position().subtract(this.position()) : to;
		if (facing.horizontalDistanceSqr() > 0.25) {
			float wanted = (float) (Mth.atan2(-facing.x, facing.z) * Mth.RAD_TO_DEG);
			this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(wanted - this.getYRot()), -5F, 5F));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
		double speed = Mth.clamp(flat * 0.05, 0, this.getAttributeValue(Attributes.FLYING_SPEED) * 0.5);
		Vec3 heading = flat > 0.01 ? new Vec3(to.x / flat, 0, to.z / flat) : Vec3.ZERO;
		Vec3 wanted = heading.scale(speed).add(0, Mth.clamp(to.y * 0.08, -0.3, 0.3), 0);
		Vec3 motion = this.getDeltaMovement().lerp(wanted, 0.12);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		this.calculateEntityAnimation(true);
	}

	/** Nothing to fly for: glide down and land. */
	private void glideDown() {
		if (this.onGround() || this.isInWater()) {
			this.setFlying(false);
			return;
		}
		Vec3 v = this.getDeltaMovement();
		Vec3 motion = new Vec3(v.x * 0.9, Math.max(v.y - 0.04, -0.45), v.z * 0.9);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
		this.calculateEntityAnimation(true);
	}

	// ---- Pillager mounts ------------------------------------------------------------------------------------

	/** A pillager rides it: the dragon flies itself, guarding its island and diving at players. */
	public boolean isRaiderMounted() {
		return this.getFirstPassenger() instanceof Raider;
	}

	/**
	 * True once a pillager's dragon has been freed: its rider is gone and no pillagers remain around the outpost it
	 * guarded. Freed dragons stop guarding the island, fly back to it, and can be tamed.
	 */
	public boolean isFreed() {
		return this.captive && !this.isRaiderMounted() && this.outpostConquered();
	}

	/** No pillagers left around the outpost this dragon guarded (checked every couple of seconds at most). */
	private boolean outpostConquered() {
		long now = this.level().getGameTime();
		if (now - this.conqueredCheckedAt >= 40) {
			this.conqueredCheckedAt = now;
			BlockPos around = this.home != null ? this.home : this.blockPosition();
			this.conquered = this.level().getEntitiesOfClass(Raider.class, new AABB(around).inflate(OUTPOST_RADIUS), Raider::isAlive).isEmpty();
		}
		return this.conquered;
	}

	/** Pillagers' dragons are on the pillagers' side. */
	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		if (this.isRaiderMounted() && other instanceof Raider) {
			return true;
		}
		return super.considersEntityAsAlly(other);
	}

	/**
	 * Builds a pillager riding a wild dragon at {@code spawn}, patrolling the outpost at {@code outpost}. Works
	 * during world generation.
	 */
	public static DragonEntity spawnRaiderMount(ServerLevelAccessor level, BlockPos spawn, BlockPos outpost) {
		ServerLevel server = level.getLevel();
		DragonElement[] wild = {DragonElement.EMBER, DragonElement.FROST, DragonElement.STORM};
		DragonEntity dragon = Dragons.TYPES.get(wild[server.getRandom().nextInt(wild.length)]).create(server, EntitySpawnReason.STRUCTURE);
		if (dragon == null) {
			return null;
		}
		dragon.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, server.getRandom().nextFloat() * 360F, 0F);
		dragon.setIslandHome(outpost, true);
		Pillager rider = EntityTypes.PILLAGER.create(server, EntitySpawnReason.STRUCTURE);
		if (rider != null) {
			rider.snapTo(dragon.getX(), dragon.getY(), dragon.getZ());
			rider.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), EntitySpawnReason.STRUCTURE, null);
			rider.setPersistenceRequired();
			rider.startRiding(dragon, true, false);
		}
		level.addFreshEntityWithPassengers(dragon);
		return dragon;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof LivingEntity living) {
			living.yBodyRot = this.yBodyRot;
		}
	}

	// ---- Breath ---------------------------------------------------------------------------------------------

	public boolean canBreathe() {
		return this.breathCooldown <= 0 && this.breathTicks <= 0 && this.growth() >= 0.3F && this.isAlive();
	}

	public boolean isBreathing() {
		return this.breathTicks > 0;
	}

	/** Starts a breath along {@code direction}. Server only. A rider's breath follows where they look. */
	public void breathe(Vec3 direction, boolean ridden) {
		if (!(this.level() instanceof ServerLevel level) || !this.canBreathe() || direction.lengthSqr() < 1.0E-6) {
			return;
		}
		this.breathTicks = BREATH_TICKS;
		this.breathRidden = ridden;
		this.breathDirection = direction.normalize();
		this.breathCooldown = BREATH_TICKS + (ridden ? RIDDEN_BREATH_REST : AI_BREATH_REST);
		this.triggerAnim("breath", "breath");
		Vec3 mouth = this.mouth(this.breathDirection);
		level.playSound(null, mouth.x, mouth.y, mouth.z, this.element.breathSound, SoundSource.NEUTRAL, 1.2F, 0.7F + this.random.nextFloat() * 0.3F);
	}

	private Vec3 mouth(Vec3 direction) {
		float scale = this.getScale();
		return this.position().add(0, 1.3 * scale, 0).add(direction.scale(1.8 * scale));
	}

	private void tickBreath(ServerLevel level) {
		this.breathTicks--;
		if (this.breathRidden && this.getControllingPassenger() instanceof Player rider) {
			this.breathDirection = rider.getLookAngle();
		} else if (!this.breathRidden && this.getTarget() instanceof LivingEntity target && target.isAlive()) {
			Vec3 to = target.getEyePosition().subtract(this.mouth(this.breathDirection));
			if (to.lengthSqr() > 1.0E-4) {
				this.breathDirection = to.normalize();
			}
		}
		Vec3 dir = this.breathDirection;
		Vec3 mouth = this.mouth(dir);
		float range = BREATH_RANGE * (0.5F + 0.5F * this.getScale());
		// The stream stops at the first solid block (or water surface, for the frost dragon to freeze).
		BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(dir.scale(range)), ClipContext.Block.COLLIDER,
			this.element == DragonElement.FROST ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
		double reach = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(mouth);

		for (int i = 1; i <= 8; i++) {
			Vec3 at = mouth.add(dir.scale(reach * i / 8.0));
			double spread = 0.1 + i * 0.1;
			level.sendParticles(this.element.breathParticle, at.x, at.y, at.z, 3, spread, spread, spread, 0.02);
		}
		if (this.breathTicks % BREATH_HIT_INTERVAL != 0) {
			return;
		}
		this.burnEntities(level, mouth, dir, reach);
		if (hit.getType() == HitResult.Type.BLOCK) {
			this.affectBlocks(level, hit.getBlockPos());
		}
	}

	private void burnEntities(ServerLevel level, Vec3 mouth, Vec3 dir, double reach) {
		LivingEntity owner = this.getOwner();
		AABB area = new AABB(mouth, mouth.add(dir.scale(reach))).inflate(reach * 0.45);
		float damage = 1.2F + 1.2F * this.growth();
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !this.hasPassenger(e));
		for (LivingEntity target : hit) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mouth);
			if (to.length() > reach + 0.5 || to.normalize().dot(dir) < BREATH_CONE_COS || this.isFriend(target, owner)) {
				continue;
			}
			target.setInvulnerableTime(0); // a steady stream, not one blow
			this.creditOwner(target);
			if (!target.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
				continue;
			}
			switch (this.element) {
				case EMBER -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
				case FROST -> {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2), this);
					target.setTicksFrozen(Math.min(target.getTicksFrozen() + 40, target.getTicksRequiredToFreeze() + 40));
				}
				case ENDER -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), this);
				case STORM -> {
					if (this.random.nextFloat() < 0.12F) {
						LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
						if (bolt != null) {
							bolt.setVisualOnly(true);
							bolt.snapTo(target.getX(), target.getY(), target.getZ());
							level.addFreshEntity(bolt);
						}
						target.setInvulnerableTime(0);
						target.hurtServer(level, this.damageSources().lightningBolt(), 3F);
					}
				}
			}
		}
	}

	/** What the breath does to the world: frost freezes water (and lava), fire melts ice and snow and lights fires. */
	private void affectBlocks(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			switch (this.element) {
				case FROST -> {
					boolean openAbove = level.getBlockState(pos.above()).isAir();
					if (state.is(Blocks.WATER) && level.getFluidState(pos).isSource() && openAbove) {
						level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
						level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(this.random, 60, 120));
					} else if (state.is(Blocks.LAVA) && openAbove) {
						level.setBlockAndUpdate(pos, (level.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
						level.levelEvent(1501, pos, 0); // hiss and smoke
					}
				}
				case EMBER -> {
					if (state.getBlock() instanceof SnowLayerBlock || state.is(Blocks.POWDER_SNOW)) {
						level.removeBlock(pos, false);
					} else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
						level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
						level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
					} else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state)) {
						level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
					}
				}
				default -> {
				}
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (target instanceof LivingEntity living) {
			this.creditOwner(living);
		}
		return super.doHurtTarget(level, target);
	}

	/** What a tamed dragon kills counts as its owner's kill, experience and all, as a wolf's does. */
	private void creditOwner(LivingEntity target) {
		if (this.getOwner() instanceof Player owner) {
			target.setLastHurtByPlayer(owner, 100);
		}
	}

	/** Never breathe on the owner, the owner's pets, a pillager's allies, or other players unless they're the target. */
	private boolean isFriend(LivingEntity target, LivingEntity owner) {
		if (this.isRaiderMounted() && target instanceof Raider) {
			return true;
		}
		if (owner != null && (target == owner || (target instanceof TamableAnimal pet && pet.isOwnedBy(owner)))) {
			return true;
		}
		return target instanceof Player && target != this.getTarget();
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 6 || id == 7) {
			for (int i = 0; i < 7; i++) {
				this.level().addParticle(id == 7 ? ParticleTypes.HEART : ParticleTypes.SMOKE,
					this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0, 0.02, 0);
			}
			return;
		}
		super.handleEntityEvent(id);
	}

	// ---- Sound ----------------------------------------------------------------------------------------------

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENDER_DRAGON_GROWL;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 480;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENDER_DRAGON_GROWL;
	}

	/** Hatchlings chirp; grown dragons roar. */
	@Override
	protected float getSoundVolume() {
		return 0.35F + 0.65F * this.growth();
	}

	@Override
	public float getVoicePitch() {
		return (1.0F + (1F - this.growth()) * 0.9F) * (0.9F + this.random.nextFloat() * 0.2F);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (this.growth() > 0.6F) {
			this.playSound(SoundEvents.RAVAGER_STEP, 0.2F * this.growth(), 1.2F);
		} else {
			super.playStepSound(pos, state);
		}
	}

	// ---- Animation ------------------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<DragonEntity>("main", 6, test -> {
			DragonEntity dragon = test.animatable();
			if (dragon.isFlying() || (dragon.isVehicle() && !dragon.onGround())) {
				return test.setAndContinue(FLY);
			}
			if (dragon.isInSittingPose()) {
				return test.setAndContinue(SIT);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
		controllers.add(new AnimationController<DragonEntity>("breath", 2, test -> PlayState.STOP)
			.triggerableAnim("breath", BREATH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
