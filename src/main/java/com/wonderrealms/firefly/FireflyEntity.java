package com.wonderrealms.firefly;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.WonderAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A firefly: a little beetle with a glowing tail that drifts over the grass on warm nights, and slips away again
 * at dawn. Use a glass bottle on one to catch it in a Firefly Jar. To a shrunken player, it's the size of a dog.
 */
public class FireflyEntity extends AmbientCreature implements GeoEntity {
	/** How small they are next to their model. */
	public static final double SCALE = 0.5;
	/** How far it drifts at most in a tick, and how high over the ground it likes to stay. */
	private static final double SPEED = 0.05, MIN_HEIGHT = 0.6, MAX_HEIGHT = 3.2;

	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private Vec3 drift;

	public FireflyEntity(EntityType<? extends FireflyEntity> type, Level level) {
		super(type, level);
		// Size its hitbox by its scale attribute as its model is sized (a fresh one isn't, until something changes it).
		this.refreshDimensions();
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AmbientCreature.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 2.0)
			.add(Attributes.SCALE, SCALE);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(net.minecraft.world.entity.Entity entity) {
	}

	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() && this.random.nextInt(30) == 0) {
			this.level().addParticle(ParticleTypes.FIREFLY, this.getX(), this.getY() + 0.1, this.getZ(), 0, 0, 0);
		}
	}

	/** Drifts lazily from spot to spot a little over the ground, and slips away when the sun comes up. */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isPersistenceRequired() && level.isBrightOutside() && this.random.nextInt(200) == 0) {
			level.sendParticles(ParticleTypes.FIREFLY, this.getX(), this.getY(), this.getZ(), 3, 0.2, 0.2, 0.2, 0);
			this.discard();
			return;
		}
		if (this.drift == null || this.random.nextInt(50) == 0 || this.drift.distanceToSqr(this.position()) < 0.3) {
			double x = this.getX() + this.random.nextGaussian() * 2.5;
			double z = this.getZ() + this.random.nextGaussian() * 2.5;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			double y = Mth.clamp(this.getY() + this.random.nextGaussian(), ground + MIN_HEIGHT, ground + MAX_HEIGHT);
			if (Math.abs(ground - this.getY()) > 12) {
				y = this.getY(); // over a cliff or a cave: keep to its own height
			}
			this.drift = new Vec3(x, y, z);
		}
		Vec3 to = this.drift.subtract(this.position());
		Vec3 wanted = to.lengthSqr() > 1.0E-4 ? to.normalize().scale(SPEED) : Vec3.ZERO;
		Vec3 motion = this.getDeltaMovement().lerp(wanted, 0.08);
		this.setDeltaMovement(motion);
		if (motion.horizontalDistanceSqr() > 1.0E-5) {
			float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 10F));
			this.yBodyRot = this.getYRot();
		}
	}

	/** A glass bottle catches it in a jar, to light up a room. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.GLASS_BOTTLE)) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Fireflies.JAR_ITEM)));
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1F, 1.4F);
			level.sendParticles(ParticleTypes.FIREFLY, this.getX(), this.getY(), this.getZ(), 5, 0.2, 0.2, 0.2, 0);
			if (player instanceof ServerPlayer serverPlayer) {
				WonderAdvancements.award(serverPlayer, "firefly_jar");
			}
			this.discard();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

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
		return 0.2F;
	}

	@Override
	public float getVoicePitch() {
		return 1.8F;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<FireflyEntity>("main", 0, test -> test.setAndContinue(FLY)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
