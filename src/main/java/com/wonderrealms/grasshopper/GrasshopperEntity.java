package com.wonderrealms.grasshopper;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A grasshopper: a long-legged leaper of the tiny world. Saddle one and, while you're tiny, ride it. Hold jump to
 * wind up a leap, just like a horse's jump, and let go to spring high over the grass. Grasshoppers love wheat, and
 * two fed wheat make a nymph that grows into a grasshopper. Falls never hurt a grasshopper or whoever is riding it.
 */
public class GrasshopperEntity extends Animal implements GeoEntity, PlayerRideableJumping {
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(GrasshopperEntity.class, EntityDataSerializers.BOOLEAN);
	/** How small they are next to their model (about a block long): a mount for a shrunken player. */
	public static final double SCALE = 0.3;
	/** The saddle sits this high on its back, in model blocks. */
	private static final double SEAT_HEIGHT = 0.5;
	/** A fully wound-up leap: how fast it springs up and forward (blocks a tick, before its size). */
	private static final double LEAP_UP = 0.85, LEAP_FORWARD = 0.75;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	private static final RawAnimation HOP = RawAnimation.begin().thenLoop("move.hop");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** How hard the rider wound up the leap that's waiting to happen (0 = none). */
	private float pendingLeap;

	public GrasshopperEntity(EntityType<? extends GrasshopperEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.SCALE, SCALE)
			.add(Attributes.STEP_HEIGHT, 1.0)
			.add(Attributes.TEMPT_RANGE, 6.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SADDLED, false);
	}

	public boolean isSaddled() {
		return this.entityData.get(SADDLED);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("saddled", this.isSaddled());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SADDLED, input.getBooleanOr("saddled", false));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, stack -> stack.is(Items.WHEAT), false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 4F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		// Someone who grows back to full size is far too big to stay on.
		if (!this.level().isClientSide() && this.getFirstPassenger() instanceof LivingEntity rider && !TinyWorlds.isTiny(rider)) {
			this.ejectPassengers();
		}
	}

	/** Left to itself, a grasshopper gets about in little hops. */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isVehicle() && this.onGround() && this.getNavigation().isInProgress() && this.random.nextInt(12) == 0) {
			Vec3 forward = Vec3.directionFromRotation(0F, this.getYRot()).scale(0.12);
			this.setDeltaMovement(forward.x, 0.32, forward.z);
			this.needsSync = true;
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false; // springy legs, and a soft landing for its rider too
	}

	/** Wild ones wander off once nobody's near; a saddled or ridden one stays. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isSaddled() && !this.isVehicle();
	}

	// ---- Food and saddle ------------------------------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.WHEAT);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		GrasshopperEntity nymph = Grasshoppers.TYPE.create(level, EntitySpawnReason.BREEDING);
		if (nymph != null) {
			nymph.setPersistenceRequired();
		}
		return nymph;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean server = !this.level().isClientSide();
		if (this.isFood(stack) || this.isBaby()) {
			return super.mobInteract(player, hand);
		}
		if (stack.is(Items.SADDLE) && !this.isSaddled()) {
			if (server) {
				stack.consume(1, player);
				this.entityData.set(SADDLED, true);
				this.setPersistenceRequired();
				this.playSound(SoundEvents.HORSE_SADDLE.value(), 0.6F, 1.5F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.SHEARS) && this.isSaddled() && !this.isVehicle()) {
			if (server) {
				this.entityData.set(SADDLED, false);
				this.spawnAtLocation((ServerLevel) this.level(), new ItemStack(Items.SADDLE));
				this.playSound(SoundEvents.SHEEP_SHEAR, 0.6F, 1.5F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty() && this.isSaddled() && !this.isVehicle()) {
			if (server) {
				if (!TinyWorlds.isTiny(player)) {
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.grasshopper_too_big").withStyle(ChatFormatting.YELLOW));
				} else {
					player.startRiding(this);
				}
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

	// ---- Riding and leaping ---------------------------------------------------------------------------------

	@Override
	public LivingEntity getControllingPassenger() {
		return this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().isEmpty();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0, SEAT_HEIGHT * scale, 0);
	}

	@Override
	protected void tickRidden(Player rider, Vec3 input) {
		super.tickRidden(rider, input);
		this.setYRot(rider.getYRot());
		this.yRotO = this.getYRot();
		this.setXRot(rider.getXRot() * 0.5F);
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		if (this.isLocalInstanceAuthoritative() && this.onGround()) {
			if (this.pendingLeap > 0F) {
				this.leap(this.pendingLeap, input);
			}
			this.pendingLeap = 0F;
		}
	}

	/** Springs up, and forward if its rider is pressing forward, harder the longer the jump was held. */
	private void leap(float strength, Vec3 input) {
		double size = this.getScale() / SCALE; // a grasshopper shrunk smaller still leaps that much less
		Vec3 motion = this.getDeltaMovement();
		double up = LEAP_UP * strength * size;
		double forward = input.z > 0 ? LEAP_FORWARD * strength * size : 0.15 * strength * size;
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		this.setDeltaMovement(motion.x - Mth.sin(yaw) * forward, up, motion.z + Mth.cos(yaw) * forward);
		this.needsSync = true;
		this.playSound(SoundEvents.GOAT_LONG_JUMP, 0.3F, 1.8F);
	}

	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 input) {
		if (this.onGround() && this.pendingLeap == 0F && !this.isJumping()) {
			float forward = rider.zza;
			return new Vec3(rider.xxa * 0.5F, 0, forward < 0 ? forward * 0.4F : forward);
		}
		// In the air it can't steer much.
		return new Vec3(rider.xxa * 0.15F, 0, rider.zza * 0.3F);
	}

	@Override
	protected float getRiddenSpeed(Player rider) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	public void onPlayerJump(int power) {
		if (!this.isSaddled()) {
			return;
		}
		power = Math.max(0, power);
		this.pendingLeap = power >= 90 ? 1F : 0.35F + 0.55F * power / 90F;
	}

	@Override
	public boolean canJump() {
		return this.isSaddled();
	}

	@Override
	public void handleStartJump(int power) {
		// The leap itself happens where the rider steers from; the server just hears about it.
		if (this.getControllingPassenger() instanceof ServerPlayer rider && power > 0) {
			WonderAdvancements.award(rider, "grasshopper_rider");
		}
	}

	@Override
	public void handleStopJump() {
	}

	// ---- Sounds and animation -------------------------------------------------------------------------------

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.06F, 1.8F);
	}

	@Override
	public float getVoicePitch() {
		return Mth.randomBetween(this.random, 1.4F, 1.7F);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<GrasshopperEntity>("main", 2,
			test -> test.setAndContinue(!test.animatable().onGround() ? HOP : test.isMoving() ? WALK : IDLE)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
