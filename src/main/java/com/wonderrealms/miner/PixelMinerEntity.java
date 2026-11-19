package com.wonderrealms.miner;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderAdvancements;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A Pixel Miner: one of the tiny folk who live in the grass and stone, only really noticeable once you've shrunk
 * to their size. They wander, chip away at rocks, run from bugs, and trade what they've dug up for dewdrops.
 * They show up around shrunken players and wander off home a while after there are none about.
 */
public class PixelMinerEntity extends PathfinderMob implements GeoEntity {
	private static final EntityDataAccessor<Boolean> MINING = SynchedEntityData.defineId(PixelMinerEntity.class, EntityDataSerializers.BOOLEAN);
	/** How tiny they are compared to their 1-block-tall model. A shrunken player is 0.18 blocks tall. */
	public static final double SCALE = 0.16;
	/** Ticks without a shrunken player nearby before a miner heads home. */
	private static final int LONELY_TICKS = 20 * 120;
	private static final double COMPANY_RANGE = 40;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	private static final RawAnimation MINE = RawAnimation.begin().thenLoop("misc.mine");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int lonelyTicks;

	public PixelMinerEntity(EntityType<? extends PixelMinerEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.12)
			.add(Attributes.SCALE, SCALE)
			.add(Attributes.STEP_HEIGHT, 0.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MINING, false);
	}

	public boolean isMining() {
		return this.entityData.get(MINING);
	}

	void setMining(boolean mining) {
		this.entityData.set(MINING, mining);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Spider.class, 3F, 1.2, 1.6));
		this.goalSelector.addGoal(4, new MineGoal(this));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 2F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 40 == 0) {
			Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), COMPANY_RANGE,
				p -> p instanceof Player player && TinyWorlds.isTiny(player));
			this.lonelyTicks = near == null ? this.lonelyTicks + 40 : 0;
			if (this.lonelyTicks > LONELY_TICKS) {
				level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.05, this.getZ(), 3, 0.02, 0.02, 0.02, 0.0);
				this.discard();
			}
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(TinyWorlds.DEWDROP)) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			stack.consume(1, player);
			ItemStack pay = PixelMiners.payment(this.random);
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.1, this.getZ(), pay));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.05, 0.05, 0.05, 0.0);
			this.playSound(SoundEvents.VILLAGER_YES, 0.4F, 2.0F);
			if (player instanceof ServerPlayer serverPlayer) {
				WonderAdvancements.award(serverPlayer, "pixel_trade");
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<PixelMinerEntity>("main", 3, test -> {
			if (test.animatable().isMining()) {
				return test.setAndContinue(MINE);
			}
			return test.setAndContinue(test.isMoving() ? WALK : IDLE);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
