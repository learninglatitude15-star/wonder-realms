package com.wonderrealms.dragonfly;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A dragonfly: a darting, shimmering flier, only really noticeable once you've shrunk to its size. Saddle one and,
 * while you're tiny, fly it: it goes where you look, climbs while you hold jump, and hovers when you let go.
 * Dragonflies love sweet berries, and two fed berries make a nymph that grows into a dragonfly.
 */
public class DragonflyEntity extends Animal implements GeoEntity {
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(DragonflyEntity.class, EntityDataSerializers.BOOLEAN);
	/** How small they are next to their model (a little over a block long): a mount for a shrunken player. */
	public static final double SCALE = 0.3;
	/** The saddle sits this high on its back, in model blocks. */
	private static final double SEAT_HEIGHT = 0.62;

	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public DragonflyEntity(EntityType<? extends DragonflyEntity> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl(this, 20, true);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.FLYING_SPEED, 0.5)
			.add(Attributes.SCALE, SCALE)
			.add(Attributes.TEMPT_RANGE, 6.0);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
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
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, stack -> stack.is(Items.SWEET_BERRIES), false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.2));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
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

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false; // it flies
	}

	// ---- Food, saddle and flying ----------------------------------------------------------------------------

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES);
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		DragonflyEntity nymph = Dragonflies.TYPE.create(level, EntitySpawnReason.BREEDING);
		if (nymph != null) {
			nymph.setPersistenceRequired(); // bred by a player, so it stays
		}
		return nymph;
	}

	/** Wild ones wander off once nobody's near; a saddled, ridden or bred one stays. */
	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isSaddled() && !this.isVehicle();
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
				this.playSound(SoundEvents.HORSE_SADDLE.value(), 0.6F, 1.6F);
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
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.dragonfly_too_big").withStyle(ChatFormatting.YELLOW));
				} else {
					player.startRiding(this);
					if (player instanceof ServerPlayer serverPlayer) {
						WonderAdvancements.award(serverPlayer, "dragonfly_rider");
					}
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
	}

	@Override
	protected Vec3 getRiddenInput(Player rider, Vec3 input) {
		float forward = rider.zza;
		return new Vec3(rider.xxa * 0.5F, 0, forward < 0 ? forward * 0.4F : forward);
	}

	@Override
	protected float getRiddenSpeed(Player rider) {
		return (float) this.getAttributeValue(Attributes.FLYING_SPEED);
	}

	/** Ridden, it flies where its rider looks, climbs while they hold jump, and hovers when they let go. */
	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player rider) {
			Vec3 look = rider.getLookAngle();
			Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
			double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * 0.45;
			Vec3 wanted = look.scale(input.z * speed).add(side.scale(-input.x * speed * 0.6));
			if (rider.isJumping()) {
				wanted = wanted.add(0, 0.15, 0);
			}
			Vec3 motion = this.getDeltaMovement().lerp(wanted, 0.25);
			this.setDeltaMovement(motion);
			this.move(MoverType.SELF, motion);
			this.calculateEntityAnimation(true);
			return;
		}
		super.travel(input);
	}

	// ---- Sounds and animation -------------------------------------------------------------------------------

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BEE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BEE_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<DragonflyEntity>("main", 0, test -> test.setAndContinue(FLY)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
