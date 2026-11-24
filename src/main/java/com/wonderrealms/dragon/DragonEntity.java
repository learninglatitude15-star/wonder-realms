package com.wonderrealms.dragon;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.TinyWorlds;
import com.wonderrealms.WonderAdvancements;
import com.wonderrealms.WonderRealms;
import com.wonderrealms.airship.AirshipEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * and freezes (and turns water to ice you can walk on), storm crackles and sometimes calls lightning, verdant spores
 * poison and make plants grow, a tide jet bowls foes over and puts out fires, crystal shards make foes glow, shadow
 * withers and weakens, and ender breath lifts targets off their feet.
 * <p>
 * Each kind has a gift too: an Ember dragon's rider can't burn, a Frost dragon freezes water as it walks, a Storm
 * dragon is the fastest flier, a Verdant dragon heals its rider, a Tide dragon swims as fast as it flies (and its
 * rider breathes underwater), crystal scales armor a Crystal dragon, and a Shadow dragon's rider sees in the dark.
 * <p>
 * A grown, saddled dragon can be ridden: jump (or look up while moving) to take off, steer by looking, hold jump to
 * climb, dive to gain speed, and press the breath key to breathe. Sneak and use it with an empty hand to make it
 * sit or follow. A chest gives a saddled dragon saddlebags, opened with the inventory key while riding.
 */
public class DragonEntity extends TamableAnimal implements GeoEntity, HasCustomInventoryScreen {
	private static final EntityDataAccessor<Float> GROWTH = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
	/** The armor worn: 0 for none, otherwise {@link DragonArmor#index()}. */
	private static final EntityDataAccessor<Integer> ARMOR = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BAGS = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ELDER = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);

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
	/** The saddle sits on the middle of the back (full size); a second rider sits just behind it. */
	private static final double SEAT_HEIGHT = 1.4, SEAT_BACK = 0.06, PILLION_BACK = 0.62;
	/** A whistle's call lasts this long; the dragon counts as arrived this close to its owner. */
	private static final int CALL_TICKS = 20 * 30;
	private static final double CALL_ARRIVE = 6;
	/** A chest's worth of saddlebags. */
	private static final int BAG_SLOTS = 27;
	/** Crystal scales count as this much armor. */
	private static final double CRYSTAL_ARMOR = 6;
	/** An Earth dragon's rocky hide: a little armor, and it's hard to knock about. */
	private static final double EARTH_ARMOR = 4, EARTH_KNOCKBACK_RESISTANCE = 0.5;
	/** An Iron dragon's plated hide: more armor than any other dragon's, and very hard to knock about. */
	private static final double IRON_ARMOR = 8, IRON_KNOCKBACK_RESISTANCE = 0.6;
	/** How many bees a Honey dragon's Swarm calls up, and how long they stay (ticks). */
	private static final int SWARM_SIZE = 6, SWARM_LIFETIME = 600;
	/** The colours of a Rainbow dragon's breath, red to violet, one for each stretch of the stream. */
	private static final ParticleOptions[] RAINBOW_DUST = {new DustParticleOptions(0xFF3030, 1.6F), new DustParticleOptions(0xFF8C1A, 1.6F),
		new DustParticleOptions(0xFFE030, 1.6F), new DustParticleOptions(0x40D040, 1.6F), new DustParticleOptions(0x30A0FF, 1.6F),
		new DustParticleOptions(0x5050E0, 1.6F), new DustParticleOptions(0xB050E0, 1.6F)};
	/** What a Rainbow dragon's breath can repaint, when ridden: each family of coloured blocks. */
	private static final List<ColorCollection<Block>> PAINTABLE = List.of(Blocks.WOOL, Blocks.CONCRETE, Blocks.DYED_TERRACOTTA,
		Blocks.STAINED_GLASS, Blocks.STAINED_GLASS_PANE, Blocks.CARPET);
	/** How much faster an Air dragon climbs while its rider holds jump. */
	private static final double AIR_CLIMB = 0.55;
	/** At most this many blocks an Earth dragon's breath grinds away with each blast. */
	private static final int DIG_PER_BLAST = 4;
	/** How much faster a Storm dragon flies than the others. */
	private static final double STORM_SPEED_BONUS = 0.25;
	/** A wing stroke underwater only gets this share of its speed in the air (except for a Tide dragon). */
	private static final double WATER_DRAG = 0.4;
	/** A Verdant dragon heals its rider by half a heart this often. */
	private static final int VERDANT_HEAL_TICKS = 60;
	/** A dragon given a Dragon Heart becomes an Elder: this much bigger, with this much health, when grown. */
	private static final float ELDER_PET_SCALE = 1.35F;
	private static final double ELDER_PET_HEALTH = 120;
	/** How long a dragon's power takes to recharge (an Elder's recharges faster), and how far it reaches. */
	private static final int POWER_COOLDOWN = 20 * 30, ELDER_POWER_COOLDOWN = 20 * 20;
	static final double POWER_RADIUS = 10, STORM_POWER_RADIUS = 16;
	/** A tamed, grown dragon sheds a scale every so often (between this and twice this). */
	private static final int SHED_INTERVAL = 20 * 60 * 6;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");
	private static final RawAnimation SIT = RawAnimation.begin().thenLoop("misc.sit");
	private static final RawAnimation BREATH = RawAnimation.begin().thenPlay("attack.breath");
	/** Rearing back to unleash its power (in the air, it ends with its wings still spread). */
	private static final RawAnimation REAR = RawAnimation.begin().thenPlay("attack.power");
	private static final RawAnimation REAR_IN_FLIGHT = RawAnimation.begin().thenPlay("attack.power_air");

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
	/** Ticks left on a whistle's call to come. */
	private int callTicks;
	private int powerCooldown;
	private int shedTimer = SHED_INTERVAL;
	/** The bees a Honey dragon's Swarm called up; they buzz off again after half a minute. */
	private final List<Bee> swarm = new ArrayList<>();
	/** Helpers a power called up (an Iron dragon's golem), which go again after a while (see {@link DragonKinds}). */
	final List<Mob> allies = new ArrayList<>();
	/** Ticks until a Phoenix dragon can save its rider from death again. */
	private int rebirthCooldown;
	private final SimpleContainer saddlebags = new SimpleContainer(BAG_SLOTS) {
		@Override
		public boolean stillValid(Player player) {
			return !DragonEntity.this.isRemoved() && player.distanceToSqr(DragonEntity.this) < 64;
		}
	};

	public DragonEntity(EntityType<? extends DragonEntity> type, Level level, DragonElement element) {
		super(type, level);
		this.element = element;
		this.applyKindTraits();
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
		builder.define(ARMOR, 0);
		builder.define(BAGS, false);
		builder.define(ELDER, false);
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
		this.getAttribute(Attributes.SCALE).setBaseValue(HATCHLING_SCALE + (this.adultScale() - HATCHLING_SCALE) * growth);
		double oldMax = this.getMaxHealth();
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Mth.lerp(growth, HATCHLING_HEALTH, this.adultHealth()));
		float max = this.getMaxHealth();
		float health = keepHealthShare && oldMax > 0 ? (float) (this.getHealth() * max / oldMax) : this.getHealth();
		this.setHealth(Math.min(health, max));
	}

	public boolean isGrown() {
		return this.growth() >= 1F;
	}

	/** How big it is when fully grown: 1, or bigger for an Elder. */
	protected float adultScale() {
		return this.isElder() ? ELDER_PET_SCALE : 1F;
	}

	protected double adultHealth() {
		return this.isElder() ? ELDER_PET_HEALTH : GROWN_HEALTH;
	}

	/** Given a Dragon Heart: bigger and tougher, with stronger breath and a power that recharges faster. */
	public boolean isElder() {
		return this.entityData.get(ELDER);
	}

	public boolean isSaddled() {
		return this.entityData.get(SADDLED);
	}

	/** The armor this dragon wears, or null. */
	public DragonArmor armor() {
		return DragonArmor.byIndex(this.entityData.get(ARMOR));
	}

	private void setArmor(DragonArmor armor) {
		this.entityData.set(ARMOR, armor == null ? 0 : armor.index());
		this.applyArmor();
	}

	/** Worn armor counts toward the dragon's own armor, toughness and knockback resistance. */
	private void applyArmor() {
		if (this.level().isClientSide()) {
			return;
		}
		DragonArmor armor = this.armor();
		Identifier id = WonderRealms.id("dragon_armor");
		AttributeInstance protection = this.getAttribute(Attributes.ARMOR);
		AttributeInstance toughness = this.getAttribute(Attributes.ARMOR_TOUGHNESS);
		AttributeInstance knockback = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		for (AttributeInstance attribute : new AttributeInstance[] {protection, toughness, knockback}) {
			if (attribute != null) {
				attribute.removeModifier(id);
			}
		}
		if (armor == null) {
			return;
		}
		if (protection != null) {
			protection.addTransientModifier(new AttributeModifier(id, armor.armor, AttributeModifier.Operation.ADD_VALUE));
		}
		if (toughness != null && armor.toughness > 0) {
			toughness.addTransientModifier(new AttributeModifier(id, armor.toughness, AttributeModifier.Operation.ADD_VALUE));
		}
		if (knockback != null && armor.knockbackResistance > 0) {
			knockback.addTransientModifier(new AttributeModifier(id, armor.knockbackResistance, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** What comes with the kind: crystal scales armor a Crystal dragon, and a Storm dragon outflies the rest. */
	private void applyKindTraits() {
		Identifier id = WonderRealms.id("dragon_kind");
		AttributeInstance protection = this.getAttribute(Attributes.ARMOR);
		AttributeInstance flying = this.getAttribute(Attributes.FLYING_SPEED);
		if (this.element == DragonElement.CRYSTAL && protection != null) {
			protection.removeModifier(id);
			protection.addTransientModifier(new AttributeModifier(id, CRYSTAL_ARMOR, AttributeModifier.Operation.ADD_VALUE));
		}
		if (this.element == DragonElement.STORM && flying != null) {
			flying.removeModifier(id);
			flying.addTransientModifier(new AttributeModifier(id, STORM_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		AttributeInstance steady = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if ((this.element == DragonElement.EARTH || this.element == DragonElement.IRON) && protection != null && steady != null) {
			boolean iron = this.element == DragonElement.IRON;
			protection.removeModifier(id);
			protection.addTransientModifier(new AttributeModifier(id, iron ? IRON_ARMOR : EARTH_ARMOR, AttributeModifier.Operation.ADD_VALUE));
			steady.removeModifier(id);
			steady.addTransientModifier(new AttributeModifier(id, iron ? IRON_KNOCKBACK_RESISTANCE : EARTH_KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** A tamed Air dragon fades from sight: everyone else sees nothing, its owner a faint shimmer of a dragon. */
	public boolean isAirCloaked() {
		return this.element == DragonElement.AIR && this.isTame();
	}

	@Override
	public boolean isInvisible() {
		return super.isInvisible() || this.isAirCloaked();
	}

	@Override
	public boolean isInvisibleTo(Player player) {
		if (this.isAirCloaked() && (this.isOwnedBy(player) || this.hasPassenger(player))) {
			return false; // drawn see-through for its owner and riders, so they can find it
		}
		return super.isInvisibleTo(player);
	}

	public boolean hasSaddlebags() {
		return this.entityData.get(BAGS);
	}

	/** The bags come off: the chest and everything in it spill out. */
	private void dropSaddlebags(ServerLevel level) {
		Containers.dropContents(level, this, this.saddlebags);
		this.saddlebags.clearContent();
		this.spawnAtLocation(level, new ItemStack(Items.CHEST));
		this.entityData.set(BAGS, false);
	}

	/** The inventory key opens the saddlebags while riding. (The client only asks when the dragon has bags.) */
	@Override
	public void openCustomInventoryScreen(Player player) {
		if (this.level().isClientSide() || !this.hasSaddlebags() || !this.hasPassenger(player)) {
			return;
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, this.saddlebags),
			Component.translatable("container.wonderrealms.saddlebags")));
		this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6F, 1.1F);
	}

	/** A whistle's call: stop sitting and come, flying if need be. */
	public void callTo() {
		this.setOrderedToSit(false);
		this.setInSittingPose(false);
		this.setTarget(null);
		this.callTicks = CALL_TICKS;
	}

	/** A whistle's order to sit and wait (landing first if it's flying). */
	public void orderToSit() {
		this.callTicks = 0;
		this.setOrderedToSit(true);
		this.setInSittingPose(true);
		this.setTarget(null);
		this.getNavigation().stop();
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
		if (this.armor() != null) {
			output.putString("armor", this.armor().id);
		}
		if (this.home != null) {
			output.putLong("island_home", this.home.asLong());
		}
		output.putBoolean("elder", this.isElder());
		if (this.rebirthCooldown > 0) {
			output.putInt("rebirth_cooldown", this.rebirthCooldown);
		}
		output.putBoolean("saddlebags", this.hasSaddlebags());
		if (this.hasSaddlebags()) {
			NonNullList<ItemStack> items = NonNullList.withSize(BAG_SLOTS, ItemStack.EMPTY);
			for (int i = 0; i < BAG_SLOTS; i++) {
				items.set(i, this.saddlebags.getItem(i));
			}
			ContainerHelper.saveAllItems(output.child("saddlebag_items"), items);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(ELDER, input.getBooleanOr("elder", false));
		this.entityData.set(GROWTH, Mth.clamp(input.getFloatOr("growth", 1F), 0F, 1F));
		this.applyGrowth(false);
		this.entityData.set(SADDLED, input.getBooleanOr("saddled", false));
		this.eggTimer = input.getIntOr("egg_timer", EGG_INTERVAL);
		this.captive = input.getBooleanOr("captive", false);
		this.setArmor(DragonArmor.byId(input.getStringOr("armor", "")));
		long home = input.getLongOr("island_home", input.getLongOr("home", Long.MIN_VALUE));
		this.home = home == Long.MIN_VALUE ? null : BlockPos.of(home);
		if (this.home != null && !this.captive && !this.isTame()) {
			this.setHomeTo(this.home, ROOST_RADIUS);
		}
		this.rebirthCooldown = input.getIntOr("rebirth_cooldown", 0);
		this.entityData.set(BAGS, input.getBooleanOr("saddlebags", false));
		input.child("saddlebag_items").ifPresent(bags -> {
			NonNullList<ItemStack> items = NonNullList.withSize(BAG_SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(bags, items);
			for (int i = 0; i < BAG_SLOTS; i++) {
				this.saddlebags.setItem(i, items.get(i));
			}
		});
		this.applyKindTraits();
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
		// Wild adults guard their island: come within a dozen blocks (or anywhere near a pillager's dragon) and they come for
		// you. So do the Overcloud's. But the dragons wandering the Overworld's own lands only fight back if attacked.
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
			(target, level) -> !this.isTame() && this.isGrown() && !this.isFreed() && (this.home != null || level.dimension() != Level.OVERWORLD)
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
		if (this.callTicks > 0) {
			this.callTicks--;
		}
		if (this.powerCooldown > 0) {
			this.powerCooldown--;
		}
		if (this.isAirCloaked() && !this.isVehicle() && this.tickCount % 40 == 0) {
			level.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ(), 4,
				this.getBbWidth() * 0.4, this.getBbHeight() * 0.3, this.getBbWidth() * 0.4, 0); // a faint shimmer where it waits
		}
		if (!this.swarm.isEmpty() && this.tickCount % 20 == 0) {
			this.swarm.removeIf(bee -> {
				if (bee.isAlive() && bee.tickCount < SWARM_LIFETIME) {
					return false;
				}
				if (bee.isAlive()) {
					level.sendParticles(ParticleTypes.POOF, bee.getX(), bee.getY() + 0.2, bee.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
					bee.discard();
				}
				return true;
			});
		}
		if (this.isVehicle() && TinyWorlds.isTiny(this)) {
			this.ejectPassengers(); // shrunk by a Shrink Ray: far too small to carry anyone
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
		// And now and then it sheds a scale.
		if (this.isTame() && this.isGrown() && --this.shedTimer <= 0) {
			this.spawnAtLocation(level, new ItemStack(Dragons.DRAGON_SCALE));
			this.shedTimer = SHED_INTERVAL + this.random.nextInt(SHED_INTERVAL);
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
		if (this.getControllingPassenger() instanceof ServerPlayer rider) {
			if (this.getY() > SKY_HIGH_Y) {
				WonderAdvancements.award(rider, "touch_the_sky");
			}
			if (this.element == DragonElement.TIDE && rider.isUnderWater()) {
				WonderAdvancements.award(rider, "deep_dive");
			}
		}
		if (this.tickCount % 20 == 0 && this.isVehicle()) {
			this.shareGift();
		}
		if (this.element == DragonElement.FROST && this.tickCount % 4 == 0 && this.onGround() && !this.isFlying()) {
			this.freezeWaterUnderfoot(level);
		}
		if (this.rebirthCooldown > 0) {
			this.rebirthCooldown--;
		}
		if (this.element.isNewKind()) {
			DragonKinds.tick(this, level);
		}
	}

	/** Whether a Phoenix dragon is ready to save its rider from death. */
	boolean rebirthReady() {
		return this.rebirthCooldown <= 0;
	}

	void useRebirth() {
		this.rebirthCooldown = DragonKinds.REBIRTH_COOLDOWN;
	}

	/** Each kind shares a gift with whoever rides it: no burning, healing, breathing underwater, or seeing in the dark. */
	private void shareGift() {
		Holder<MobEffect> gift = switch (this.element) {
			case EMBER -> MobEffects.FIRE_RESISTANCE;
			case TIDE -> MobEffects.WATER_BREATHING;
			case SHADOW -> MobEffects.NIGHT_VISION;
			case EARTH -> MobEffects.HASTE;
			case AIR -> MobEffects.INVISIBILITY;
			case RAINBOW -> MobEffects.HERO_OF_THE_VILLAGE;
			default -> null;
		};
		// Night vision flickers as it runs out, so it lasts a little longer than the others once you climb off.
		int duration = this.element == DragonElement.SHADOW ? 260 : this.element == DragonElement.RAINBOW ? 1200 : 60;
		for (Entity passenger : this.getPassengers()) {
			if (!(passenger instanceof Player rider)) {
				continue;
			}
			if (gift != null) {
				// Haste II for an Earth dragon's rider: the stone gives way easily.
				rider.addEffect(new MobEffectInstance(gift, duration, this.element == DragonElement.EARTH ? 1 : 0, true, false, true), this);
			}
			if (this.element == DragonElement.HONEY && this.tickCount % 60 == 0 && rider.getFoodData().needsFood()) {
				rider.getFoodData().eat(1, 0.5F); // a little honey now and then: its rider never goes hungry
				((ServerLevel) this.level()).sendParticles(ParticleTypes.FALLING_HONEY, rider.getX(), rider.getY() + 1.6, rider.getZ(), 3, 0.2, 0.1, 0.2, 0);
			}
			if (this.element == DragonElement.VERDANT && this.tickCount % VERDANT_HEAL_TICKS == 0 && rider.getHealth() < rider.getMaxHealth()) {
				rider.heal(1F);
				((ServerLevel) this.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, rider.getX(), rider.getY() + 1, rider.getZ(), 3, 0.3, 0.3, 0.3, 0);
			}
			if (this.element.isNewKind()) {
				DragonKinds.gift(this, (ServerLevel) this.level(), rider);
			}
		}
	}

	/** A Frost dragon on the move freezes the water around its feet, as frost walker boots do, so it can walk across. */
	private void freezeWaterUnderfoot(ServerLevel level) {
		int radius = 1 + Math.round(2 * this.growth());
		BlockPos feet = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -1, -radius), feet.offset(radius, -1, radius))) {
			int dx = pos.getX() - feet.getX();
			int dz = pos.getZ() - feet.getZ();
			if (dx * dx + dz * dz <= radius * radius + 1 && level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()
					&& level.getBlockState(pos.above()).isAir()) {
				level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
				level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(this.random, 60, 120));
			}
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false; // wings
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.element == DragonElement.PEARL && this.isAlive()) {
			DragonKinds.dodge(this, level); // a Pearl dragon sometimes blinks out of reach
		}
		return hurt;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return this.element.swims() || super.canBreatheUnderwater();
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
			// A friend can climb on behind the owner once the owner is in the saddle.
			if (stack.isEmpty() && this.getControllingPassenger() instanceof Player pilot && this.getPassengers().size() < 2) {
				if (server) {
					player.startRiding(this);
					if (pilot instanceof ServerPlayer serverPilot) {
						WonderAdvancements.award(serverPilot, "two_riders");
					}
					if (player instanceof ServerPlayer serverPlayer) {
						WonderAdvancements.award(serverPlayer, "two_riders");
					}
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		if (stack.is(Dragons.DRAGON_HEART)) {
			if (server) {
				if (this.isElder()) {
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.already_elder").withStyle(ChatFormatting.YELLOW));
				} else if (!this.isGrown()) {
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.heart_too_young").withStyle(ChatFormatting.YELLOW));
				} else {
					stack.consume(1, player);
					this.entityData.set(ELDER, true);
					this.applyGrowth(true);
					this.setHealth(this.getMaxHealth());
					((ServerLevel) this.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + this.getBbHeight() * 0.6,
						this.getZ(), 80, 1.0, 1.0, 1.0, 0.4);
					this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 1.2F);
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.became_elder").withStyle(ChatFormatting.GOLD));
					if (player instanceof ServerPlayer serverPlayer) {
						WonderAdvancements.award(serverPlayer, "dragon_heart");
					}
				}
			}
			return InteractionResult.SUCCESS;
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
		// A chest hangs a pair of saddlebags from the saddle.
		if (stack.is(Items.CHEST) && !this.hasSaddlebags()) {
			if (server) {
				if (!this.isSaddled()) {
					player.sendOverlayMessage(Component.translatable("message.wonderrealms.saddle_first").withStyle(ChatFormatting.YELLOW));
				} else {
					stack.consume(1, player);
					this.entityData.set(BAGS, true);
					this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1F, 0.9F);
					if (player instanceof ServerPlayer serverPlayer) {
						WonderAdvancements.award(serverPlayer, "saddlebags");
					}
				}
			}
			return InteractionResult.SUCCESS;
		}
		DragonArmor armor = DragonArmor.of(stack);
		if (armor != null) {
			if (server) {
				ServerLevel level = (ServerLevel) this.level();
				if (this.armor() != null) {
					this.spawnAtLocation(level, new ItemStack(Dragons.ARMOR_ITEMS.get(this.armor())));
				}
				this.setArmor(armor);
				stack.consume(1, player);
				this.playSound(SoundEvents.HORSE_ARMOR.value(), 1F, 0.8F);
				if (player instanceof ServerPlayer serverPlayer) {
					WonderAdvancements.award(serverPlayer, "dragon_armor");
					if (armor == DragonArmor.NETHERITE) {
						WonderAdvancements.award(serverPlayer, "netherite_dragon");
					}
					if (armor == DragonArmor.ELDER) {
						WonderAdvancements.award(serverPlayer, "elder_armor");
					}
				}
			}
			return InteractionResult.SUCCESS;
		}
		// Shears take the armor off, then the saddlebags, and then the saddle.
		if (stack.is(Items.SHEARS) && (this.armor() != null || this.isSaddled())) {
			if (server) {
				ServerLevel level = (ServerLevel) this.level();
				if (this.armor() != null) {
					this.spawnAtLocation(level, new ItemStack(Dragons.ARMOR_ITEMS.get(this.armor())));
					this.setArmor(null);
				} else if (this.hasSaddlebags()) {
					this.dropSaddlebags(level);
				} else {
					this.ejectPassengers();
					this.entityData.set(SADDLED, false);
					this.spawnAtLocation(level, new ItemStack(Items.SADDLE));
				}
				this.playSound(SoundEvents.SHEEP_SHEAR, 1F, 0.9F);
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
		if (stack.isEmpty() && this.isSaddled() && this.isGrown() && this.getControllingPassenger() == null && !TinyWorlds.isTiny(this)) {
			if (server) {
				this.ejectPassengers(); // the owner always takes the front seat
				this.setOrderedToSit(false);
				this.setInSittingPose(false);
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** Killing a wild adult is the other way to get its egg, and it drops a few scales. A saddle comes off too. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (!this.isTame() && this.isGrown()) {
			this.spawnAtLocation(level, this.trophy());
			this.spawnAtLocation(level, new ItemStack(Dragons.DRAGON_SCALE, 1 + this.random.nextInt(3)));
		}
		if (this.hasSaddlebags()) {
			this.dropSaddlebags(level);
		}
		if (this.isSaddled()) {
			this.spawnAtLocation(level, new ItemStack(Items.SADDLE));
		}
		if (this.armor() != null) {
			this.spawnAtLocation(level, new ItemStack(Dragons.ARMOR_ITEMS.get(this.armor())));
		}
	}

	/** What a wild one leaves behind when it's beaten: its egg. */
	protected ItemStack trophy() {
		return Dragons.eggOf(this.element);
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
		double back = this.getPassengers().indexOf(passenger) > 0 ? PILLION_BACK : SEAT_BACK;
		return new Vec3(0, SEAT_HEIGHT * scale, -back * scale).yRot(-this.yBodyRot * Mth.DEG_TO_RAD);
	}

	/** The pilot in the saddle, and one friend behind. */
	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < 2;
	}

	/** When the pilot climbs off, whoever rode behind them has to get down too. */
	@Override
	protected void removePassenger(Entity passenger) {
		boolean pilot = passenger instanceof Player && passenger == this.getFirstPassenger();
		super.removePassenger(passenger);
		if (pilot && !this.level().isClientSide()) {
			this.ejectPassengers();
		}
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
		// In water, a Tide or Coral dragon swims off wherever its rider looks.
		boolean swim = this.element.swims() && this.isInWater() && input.z > 0;
		if (!this.isFlying() && (rider.isJumping() || (input.z > 0 && rider.getXRot() < -25F) || swim)) {
			this.setFlying(true);
			if (!swim) {
				this.setDeltaMovement(this.getDeltaMovement().add(0, 0.6, 0));
			}
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
		if (this.isInWater() && !this.element.swims()) {
			speed *= WATER_DRAG; // wings are slow strokes underwater: only Tide and Coral dragons swim as fast as they fly
		}
		Vec3 target = look.scale(input.z * speed).add(side.scale(-input.x * speed * 0.5));
		if (rider.isJumping()) {
			target = target.add(0, this.element == DragonElement.AIR ? AIR_CLIMB : 0.35, 0);
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
		// Called by a whistle: fly to the owner, then land beside them.
		if (this.callTicks > 0 && this.getOwner() instanceof LivingEntity caller && caller.level() == this.level()) {
			if (this.distanceToSqr(caller) > CALL_ARRIVE * CALL_ARRIVE) {
				return caller.position().add(ring.scale(3)).add(0, 3, 0);
			}
			this.callTicks = 0;
		}
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
	 * during world generation (using only {@code level}'s random: the world's own belongs to the server thread).
	 */
	public static DragonEntity spawnRaiderMount(ServerLevelAccessor level, BlockPos spawn, BlockPos outpost) {
		ServerLevel server = level.getLevel();
		DragonElement[] wild = DragonElement.WILD;
		DragonEntity dragon = Dragons.TYPES.get(wild[level.getRandom().nextInt(wild.length)]).create(server, EntitySpawnReason.STRUCTURE);
		if (dragon == null) {
			return null;
		}
		dragon.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
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

	// ---- Powers ---------------------------------------------------------------------------------------------

	/**
	 * The power key: each kind of dragon has a power of its own, which then takes half a minute to recharge (twenty
	 * seconds for an Elder). Powers only strike monsters and whatever the dragon or its rider is fighting.
	 */
	public void usePower(Player rider) {
		if (!(this.level() instanceof ServerLevel level) || !this.isGrown()) {
			return;
		}
		if (this.powerCooldown > 0) {
			rider.sendOverlayMessage(Component.translatable("message.wonderrealms.power_recharging", (this.powerCooldown + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		this.powerCooldown = this.isElder() ? ELDER_POWER_COOLDOWN : POWER_COOLDOWN;
		this.rearBack(level);
		List<LivingEntity> foes = this.powerTargets(level, rider, this.element == DragonElement.STORM ? STORM_POWER_RADIUS : POWER_RADIUS);
		double x = this.getX(), y = this.getY() + this.getBbHeight() * 0.5, z = this.getZ();
		switch (this.element) {
			case EMBER -> { // Inferno: a ring of flame sets everything around alight
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 250, 5, 1.5, 5, 0.05);
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 40, 4, 1, 4, 0);
				level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 2F, 0.6F);
				for (LivingEntity foe : foes) {
					this.powerHit(level, foe, this.damageSources().mobAttack(this), 6F);
					foe.setRemainingFireTicks(Math.max(foe.getRemainingFireTicks(), 160));
				}
			}
			case FROST -> { // Blizzard: everything around is frozen in its tracks
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 350, 5, 2, 5, 0.05);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 2F, 0.5F);
				for (LivingEntity foe : foes) {
					this.powerHit(level, foe, this.damageSources().freeze(), 4F);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 3), this);
					foe.setTicksFrozen(foe.getTicksRequiredToFreeze() + 100);
				}
			}
			case STORM -> { // Thunderstorm: lightning strikes the six nearest foes
				foes.stream().sorted((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).limit(6).forEach(foe -> {
					LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setVisualOnly(true);
						bolt.snapTo(foe.getX(), foe.getY(), foe.getZ());
						level.addFreshEntity(bolt);
					}
					this.powerHit(level, foe, this.damageSources().lightningBolt(), 8F);
				});
			}
			case VERDANT -> { // Bloom: heals its riders and itself, poisons foes, and the ground bursts into growth
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 80, 4, 1.5, 4, 0);
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 150, 5, 2, 5, 0);
				level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 2F, 0.7F);
				this.heal(20F);
				for (Entity passenger : this.getPassengers()) {
					if (passenger instanceof Player friend) {
						friend.heal(8F);
						friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), this);
					}
				}
				for (LivingEntity foe : foes) {
					foe.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
				}
				this.bloom(level);
			}
			case TIDE -> { // Tidal Wave: a surge of water throws every foe back and puts out the fires around
				level.sendParticles(ParticleTypes.SPLASH, x, y, z, 400, 5, 1.5, 5, 0.3);
				level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 120, 5, 1, 5, 0.1);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 2F, 0.6F);
				for (LivingEntity foe : foes) {
					Vec3 away = foe.position().subtract(this.position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
					this.powerHit(level, foe, foe.isSensitiveToWater() ? this.damageSources().drown() : this.damageSources().mobAttack(this),
						foe.isSensitiveToWater() ? 8F : 4F);
					foe.clearFire();
					foe.push(away.x * 2.2, 0.7, away.z * 2.2);
				}
				for (BlockPos pos : BlockPos.betweenClosed(this.blockPosition().offset(-6, -3, -6), this.blockPosition().offset(6, 3, 6))) {
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof BaseFireBlock) {
						level.removeBlock(pos, false);
					} else if (CampfireBlock.isLitCampfire(state)) {
						CampfireBlock.douse(this, level, pos, state);
						level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
					}
				}
			}
			case CRYSTAL -> { // Crystal Shield: it and its riders are wrapped in crystal, and every foe around lights up
				level.sendParticles(ParticleTypes.END_ROD, x, y, z, 120, 2.5, 2, 2.5, 0.05);
				level.sendParticles(this.element.breathParticle, x, y, z, 120, 3, 2, 3, 0);
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 2F, 1F);
				this.shield(new MobEffectInstance(MobEffects.RESISTANCE, 300, 1), new MobEffectInstance(MobEffects.ABSORPTION, 300, 1));
				for (LivingEntity foe : this.powerTargets(level, rider, STORM_POWER_RADIUS)) {
					foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0), this);
				}
			}
			case SHADOW -> { // Shadow Cloak: it and its riders vanish, and its foes are left blind and weak
				level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 200, 3, 2, 3, 0.05);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 80, 3, 2, 3, 0.02);
				level.playSound(null, x, y, z, SoundEvents.WITHER_SHOOT, SoundSource.NEUTRAL, 1.5F, 0.5F);
				this.shield(new MobEffectInstance(MobEffects.INVISIBILITY, 300, 0, false, false, true));
				for (LivingEntity foe : foes) {
					foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), this);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1), this);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0), this);
				}
			}
			case EARTH -> { // Earthquake: the ground heaves, flinging every foe around into the air and leaving them reeling
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), x, this.getY(), z, 400, 6, 0.4, 6, 0.4);
				level.sendParticles(ParticleTypes.EXPLOSION, x, this.getY(), z, 14, 5, 0.3, 5, 0);
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 2F, 0.5F);
				level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1F, 0.5F);
				for (LivingEntity foe : foes) {
					BlockState under = level.getBlockState(foe.blockPosition().below());
					if (!under.isAir()) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), foe.getX(), foe.getY(), foe.getZ(), 30, 0.5, 0.2, 0.5, 0.2);
					}
					this.powerHit(level, foe, this.damageSources().mobAttack(this), 8F);
					foe.push(0, 0.9, 0);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 2), this);
					foe.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0), this);
				}
			}
			case HONEY -> { // Swarm: a cloud of angry bees pours out to sting every foe around
				level.sendParticles(ParticleTypes.FALLING_HONEY, x, y, z, 60, 3, 1.5, 3, 0);
				level.playSound(null, x, y, z, SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.NEUTRAL, 2F, 1F);
				List<LivingEntity> nearest = foes.stream().sorted((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).toList();
				for (int i = 0; i < SWARM_SIZE; i++) {
					Bee bee = EntityTypes.BEE.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (bee == null) {
						break;
					}
					bee.snapTo(x + this.random.nextGaussian(), y + 0.5 + this.random.nextDouble(), z + this.random.nextGaussian(), this.random.nextFloat() * 360F, 0F);
					if (!nearest.isEmpty()) {
						bee.setTarget(nearest.get(i % nearest.size()));
						bee.startPersistentAngerTimer();
					}
					level.addFreshEntity(bee);
					this.swarm.add(bee);
				}
			}
			case RAINBOW -> { // Rainbow Burst: rings of colour dazzle every foe, sheep turn every colour, and its riders are lifted up
				for (int ring = 0; ring < RAINBOW_DUST.length; ring++) {
					double radius = 2 + ring * 1.2;
					for (int i = 0; i < 24; i++) {
						double angle = Math.PI * 2 * i / 24;
						level.sendParticles(RAINBOW_DUST[ring], x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1, 0, 0.1, 0, 0);
					}
				}
				level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.NEUTRAL, 2F, 1.2F);
				level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 2F, 1.5F);
				this.shield(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), new MobEffectInstance(MobEffects.SPEED, 600, 1));
				for (LivingEntity foe : foes) {
					foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0), this);
					foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), this);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1), this);
				}
				for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, this.getBoundingBox().inflate(POWER_RADIUS))) {
					sheep.setColor(DyeColor.values()[this.random.nextInt(DyeColor.values().length)]);
				}
			}
			case AIR -> { // Tornado: a whirlwind sweeps every foe around together and flings them sky-high
				level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 3, 2, 1, 2, 0);
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 200, 5, 2, 5, 0.15);
				level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 2F, 0.6F);
				level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 2F, 0.5F);
				for (LivingEntity foe : foes) {
					Vec3 in = this.position().subtract(foe.position()).multiply(1, 0, 1);
					in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(0.5);
					this.powerHit(level, foe, this.damageSources().mobAttack(this), 4F);
					foe.clearFire();
					foe.push(in.x, 1.4, in.z);
					level.sendParticles(ParticleTypes.GUST, foe.getX(), foe.getY() + 0.5, foe.getZ(), 1, 0, 0, 0, 0);
				}
				if (!foes.isEmpty() && rider instanceof ServerPlayer serverRider) {
					WonderAdvancements.award(serverRider, "gone_with_the_wind");
				}
			}
			case ENDER -> { // Void Rift: every foe around is hurled into the air, to fall back down hard
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 300, 5, 2, 5, 0.1);
				level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 2F, 0.5F);
				for (LivingEntity foe : foes) {
					this.powerHit(level, foe, this.damageSources().mobAttack(this), 4F);
					foe.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 3), this);
				}
			}
			default -> DragonKinds.power(this, level, rider, foes);
		}
		rider.sendOverlayMessage(Component.translatable("power.wonderrealms." + this.element.id).withStyle(ChatFormatting.GOLD));
		if (rider instanceof ServerPlayer serverRider) {
			WonderAdvancements.award(serverRider, "dragon_power");
		}
	}

	/** It rears back on its hind legs, wings flung wide, and roars: every power begins with this. */
	protected void rearBack(ServerLevel level) {
		boolean airborne = this.isFlying() || (this.isVehicle() && !this.onGround());
		this.triggerAnim("power", airborne ? "rear_in_flight" : "rear");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, this.getSoundSource(),
			0.7F, (this.isElder() ? 0.9F : 1.2F) + this.random.nextFloat() * 0.1F);
	}

	/** Monsters near the dragon, and anything the dragon or its rider is fighting. Never friends. */
	List<LivingEntity> powerTargets(ServerLevel level, Player rider, double radius) {
		LivingEntity owner = this.getOwner();
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius), foe -> foe != this && foe.isAlive()
			&& !this.hasPassenger(foe) && !this.isFriend(foe, owner) && foe.distanceToSqr(this) < radius * radius
			&& (foe instanceof Enemy || foe == this.getTarget() || foe == rider.getLastHurtMob()
				|| foe instanceof Mob mob && (mob.getTarget() == rider || mob.getTarget() == this)));
	}

	void powerHit(ServerLevel level, LivingEntity foe, DamageSource source, float amount) {
		foe.setInvulnerableTime(0);
		this.creditOwner(foe);
		foe.hurtServer(level, source, amount);
	}

	/** Gives the dragon and everyone riding it these effects. */
	void shield(MobEffectInstance... effects) {
		for (MobEffectInstance effect : effects) {
			this.addEffect(new MobEffectInstance(effect), this);
			for (Entity passenger : this.getPassengers()) {
				if (passenger instanceof Player friend) {
					friend.addEffect(new MobEffectInstance(effect), this);
				}
			}
		}
	}

	/** Bone meal on a few dozen spots of the ground around the dragon. */
	private void bloom(ServerLevel level) {
		BlockPos center = this.blockPosition();
		for (int i = 0; i < 40; i++) {
			BlockPos pos = center.offset(this.random.nextInt(13) - 6, this.random.nextInt(7) - 3, this.random.nextInt(13) - 6);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BonemealableBlock plant && plant.isValidBonemealTarget(level, pos, state, BonemealSource.MOB)) {
				plant.performBonemeal(level, this.random, pos, state, BonemealSource.MOB);
				level.levelEvent(1505, pos, 15);
			}
		}
	}

	// ---- Breath ---------------------------------------------------------------------------------------------

	public boolean canBreathe() {
		return this.breathCooldown <= 0 && this.breathTicks <= 0 && this.growth() >= 0.3F && this.isAlive();
	}

	public boolean isBreathing() {
		return this.breathTicks > 0;
	}

	/** Whether the breath under way is its rider's, aimed where they look (rather than the dragon's own). */
	boolean isBreathRidden() {
		return this.breathRidden;
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
		level.playSound(null, mouth.x, mouth.y, mouth.z, this.breathElement().breathSound, SoundSource.NEUTRAL, 1.2F, 0.7F + this.random.nextFloat() * 0.3F);
	}

	/** The element of its breath: its own, though the Elder Dragon's changes as the fight goes on. */
	protected DragonElement breathElement() {
		return this.element;
	}

	/** Damage for each blast of a breath's stream. Grown dragons hit harder, and Elders harder still. */
	protected float breathDamage() {
		return (1.2F + 1.2F * this.growth()) * (this.isElder() ? 1.5F : 1F);
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
		// The stream stops at the first solid block (or a pool's surface, for frost to freeze or a tide jet to cool lava).
		boolean wetBreath = this.breathElement() == DragonElement.FROST || this.breathElement() == DragonElement.TIDE;
		BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(dir.scale(range)), ClipContext.Block.COLLIDER,
			wetBreath ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
		double reach = hit.getType() == HitResult.Type.MISS ? range : hit.getLocation().distanceTo(mouth);

		for (int i = 1; i <= 8; i++) {
			Vec3 at = mouth.add(dir.scale(reach * i / 8.0));
			double spread = 0.1 + i * 0.1;
			ParticleOptions particle = this.breathElement() == DragonElement.RAINBOW ? RAINBOW_DUST[(i - 1) % RAINBOW_DUST.length]
				: this.breathElement() == DragonElement.AURORA ? DragonKinds.auroraDust(i - 1) : this.breathElement().breathParticle;
			level.sendParticles(particle, at.x, at.y, at.z, 3, spread, spread, spread, 0.02);
		}
		if (this.breathTicks % BREATH_HIT_INTERVAL != 0) {
			return;
		}
		this.burnEntities(level, mouth, dir, reach);
		if (this.breathElement().isNewKind()) {
			DragonKinds.breathBlocks(this, level, hit, mouth.add(dir.scale(reach)));
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			this.affectBlocks(level, hit.getBlockPos());
		}
	}

	private void burnEntities(ServerLevel level, Vec3 mouth, Vec3 dir, double reach) {
		LivingEntity owner = this.getOwner();
		AABB area = new AABB(mouth, mouth.add(dir.scale(reach))).inflate(reach * 0.45);
		float damage = this.breathDamage() * DragonKinds.breathDamageScale(this.breathElement());
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !this.hasPassenger(e));
		for (LivingEntity target : hit) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(mouth);
			if (to.length() > reach + 0.5 || to.normalize().dot(dir) < BREATH_CONE_COS) {
				continue;
			}
			boolean friend = this.isFriend(target, owner);
			if (this.breathElement().isNewKind() ? DragonKinds.breathGentle(this, level, target, friend) : friend) {
				continue; // never hurts friends (an Aurora dragon's light heals them), nor a Blossom dragon's petals animals
			}
			if (this.breathElement() == DragonElement.RAINBOW && target instanceof Sheep sheep) {
				// Sheep aren't hurt by a rainbow: they just come out a new colour.
				sheep.setColor(DyeColor.values()[this.random.nextInt(DyeColor.values().length)]);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sheep.getX(), sheep.getY() + sheep.getBbHeight(), sheep.getZ(), 4, 0.3, 0.2, 0.3, 0);
				this.awardBreath("rainbow_sheep");
				continue;
			}
			target.setInvulnerableTime(0); // a steady stream, not one blow
			this.creditOwner(target);
			if (!target.hurtServer(level, DragonKinds.breathSource(this), damage)) {
				continue;
			}
			switch (this.breathElement()) {
				case EMBER -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));
				case FROST -> {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2), this);
					target.setTicksFrozen(Math.min(target.getTicksFrozen() + 40, target.getTicksRequiredToFreeze() + 40));
				}
				case ENDER -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), this);
				case VERDANT -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1), this);
				case TIDE -> {
					// The jet bowls them over and puts them out; endermen, blazes and the like can't stand water at all.
					target.clearFire();
					double push = 0.5 + 0.5 * this.growth();
					target.push(dir.x * push, 0.12, dir.z * push);
					if (target.isSensitiveToWater()) {
						target.setInvulnerableTime(0);
						target.hurtServer(level, this.damageSources().drown(), 3F);
					}
				}
				case CRYSTAL -> {
					target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), this);
					this.awardBreath("crystal_glow");
				}
				case SHADOW -> {
					target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), this);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
				}
				case EARTH -> {
					// A blast of grit staggers them back.
					target.push(dir.x * 0.4, 0.08, dir.z * 0.4);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), this);
				}
				case RAINBOW -> {
					// Dazzled: lit up in every colour and stumbling about.
					target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), this);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), this);
					target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), this);
				}
				case AIR -> {
					// A gust that blows them clean away, and blows out any fire on them.
					target.clearFire();
					target.push(dir.x * 1.1, 0.35, dir.z * 1.1);
				}
				case HONEY -> {
					// Gummed up with honey, they can barely move.
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3), this);
					level.sendParticles(ParticleTypes.LANDING_HONEY, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
						4, 0.3, 0.3, 0.3, 0);
				}
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
				default -> DragonKinds.breathHit(this, level, target, dir);
			}
		}
	}

	/** What the breath does to the world: frost freezes water (and lava), fire melts ice and snow and lights fires. */
	void affectBlocks(ServerLevel level, BlockPos center) {
		if (this.breathElement() == DragonElement.EARTH) {
			this.dig(level, center);
			return;
		}
		if (this.breathElement() == DragonElement.RAINBOW) {
			this.paint(level, center);
			return;
		}
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState state = level.getBlockState(pos);
			switch (this.breathElement()) {
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
				case EMBER, PHOENIX -> {
					if (state.getBlock() instanceof SnowLayerBlock || state.is(Blocks.POWDER_SNOW)) {
						level.removeBlock(pos, false);
					} else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
						level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
						level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
					} else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state)) {
						level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
					}
				}
				case VERDANT -> {
					// Plants it touches burst into growth, and bare dirt sprouts grass.
					if (state.getBlock() instanceof BonemealableBlock plant && this.random.nextFloat() < 0.12F
							&& plant.isValidBonemealTarget(level, pos, state, BonemealSource.MOB)) {
						if (plant.isBonemealSuccess(level, this.random, pos, state, BonemealSource.MOB)) {
							plant.performBonemeal(level, this.random, pos.immutable(), state, BonemealSource.MOB);
						}
						level.levelEvent(1505, pos, 15); // bone meal sparkles
						this.awardBreath("green_thumb");
					} else if (state.is(Blocks.DIRT) && level.getBlockState(pos.above()).isAir() && this.random.nextFloat() < 0.1F) {
						level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
					}
				}
				case TIDE -> {
					// Water puts out fires, wets farmland, sets concrete, fills cauldrons and cools lava.
					if (state.getBlock() instanceof BaseFireBlock) {
						level.removeBlock(pos, false);
						level.levelEvent(1009, pos, 0); // fizz
					} else if (CampfireBlock.isLitCampfire(state)) {
						level.levelEvent(1009, pos, 0);
						CampfireBlock.douse(this, level, pos, state);
						level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
					} else if (AbstractCandleBlock.isLit(state)) {
						AbstractCandleBlock.extinguish(null, state, level, pos);
					} else if (state.is(Blocks.FARMLAND)) {
						level.setBlock(pos, state.setValue(BlockStateProperties.MOISTURE, 7), Block.UPDATE_CLIENTS);
					} else if (state.getBlock() instanceof ConcretePowderBlock) {
						Block concrete = hardened(state.getBlock());
						if (concrete != null) {
							level.setBlockAndUpdate(pos, concrete.defaultBlockState());
						}
					} else if (state.is(Blocks.CAULDRON)) {
						level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
					} else if (state.is(Blocks.LAVA) && level.getBlockState(pos.above()).isAir()) {
						level.setBlockAndUpdate(pos, (level.getFluidState(pos).isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState());
						level.levelEvent(1501, pos, 0); // hiss and smoke
					}
				}
				case AIR -> {
					// A gust blows out fires, campfires and candles.
					if (state.getBlock() instanceof BaseFireBlock) {
						level.removeBlock(pos, false);
					} else if (CampfireBlock.isLitCampfire(state)) {
						CampfireBlock.douse(this, level, pos, state);
						level.setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, false));
					} else if (AbstractCandleBlock.isLit(state)) {
						AbstractCandleBlock.extinguish(null, state, level, pos);
					}
				}
				case HONEY -> {
					// Beehives and bee nests it touches brim with honey, ready to harvest.
					if (state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) {
						level.setBlockAndUpdate(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS));
						level.playSound(null, pos, SoundEvents.BEEHIVE_DRIP, SoundSource.BLOCKS, 1F, 1F);
						this.awardBreath("liquid_gold");
					}
				}
				case CRYSTAL -> {
					// Budding amethyst sings back, and its crystals grow.
					if (state.is(Blocks.BUDDING_AMETHYST) && this.random.nextFloat() < 0.3F) {
						state.randomTick(level, pos.immutable(), this.random);
						level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1F, 0.8F + this.random.nextFloat() * 0.4F);
					}
				}
				default -> {
				}
			}
		}
	}

	/**
	 * An Earth dragon's breath grinds away the ground it hits, a few blocks each blast, dropping what it digs as a
	 * pickaxe would. Only when someone's riding and aiming it: on its own it never digs, so it can't wreck builds or
	 * its own island. And only natural ground: stone, earth, sand, gravel, terracotta and ore.
	 */
	private void dig(ServerLevel level, BlockPos center) {
		if (!this.breathRidden || !(this.getControllingPassenger() instanceof Player rider)) {
			return;
		}
		int dug = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (dug >= DIG_PER_BLAST || pos.distSqr(center) > 2 || this.random.nextFloat() < 0.35F) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (isDiggable(state, level, pos) && rider.mayInteract(level, pos) && level.destroyBlock(pos.immutable(), true, this)) {
				dug++;
			}
		}
		if (dug > 0) {
			this.awardBreath("tunnel_vision");
		}
	}

	/**
	 * A Rainbow dragon's breath repaints coloured blocks it hits (wool, concrete, terracotta, glass and carpet) in
	 * random colours, and colours plain glass and terracotta. Only when someone's riding it and aiming, like digging.
	 */
	private void paint(ServerLevel level, BlockPos center) {
		if (!this.breathRidden || !(this.getControllingPassenger() instanceof Player rider)) {
			return;
		}
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			if (this.random.nextFloat() < 0.5F) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			ColorCollection<Block> family = state.is(Blocks.GLASS) ? Blocks.STAINED_GLASS : state.is(Blocks.GLASS_PANE) ? Blocks.STAINED_GLASS_PANE
				: state.is(Blocks.TERRACOTTA) ? Blocks.DYED_TERRACOTTA : null;
			for (ColorCollection<Block> colors : PAINTABLE) {
				if (family == null && colors.asList().contains(state.getBlock())) {
					family = colors;
				}
			}
			if (family != null && rider.mayInteract(level, pos)) {
				Block colored = family.pick(DyeColor.values()[this.random.nextInt(DyeColor.values().length)]);
				level.setBlockAndUpdate(pos.immutable(), colored.withPropertiesOf(state));
			}
		}
	}

	private static boolean isDiggable(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 || hardness > 4.5F) {
			return false; // bedrock, obsidian and the like are too hard
		}
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
			|| state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.ORES)
			|| state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MUD)
			|| state.is(Blocks.CLAY) || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
			|| state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)
			|| state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.SNOW_BLOCK);
	}

	/** The concrete a powder sets into: white concrete powder becomes white concrete, and so on. */
	private static Block hardened(Block powder) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(powder);
		return BuiltInRegistries.BLOCK.getOptional(id.withPath(id.getPath().replace("_powder", ""))).orElse(null);
	}

	/** Whoever's riding (or else the owner) earned an advancement with this dragon's breath. */
	void awardBreath(String advancement) {
		if (this.getControllingPassenger() instanceof ServerPlayer rider) {
			WonderAdvancements.award(rider, advancement);
		} else if (this.getOwner() instanceof ServerPlayer owner) {
			WonderAdvancements.award(owner, advancement);
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
	void creditOwner(LivingEntity target) {
		if (this.getOwner() instanceof Player owner) {
			target.setLastHurtByPlayer(owner, 100);
		}
	}

	/** Never breathe on the owner, the owner's pets, a pillager's allies, or other players unless they're the target. */
	boolean isFriend(LivingEntity target, LivingEntity owner) {
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
		controllers.add(new AnimationController<DragonEntity>("power", 4, test -> PlayState.STOP)
			.triggerableAnim("rear", REAR)
			.triggerableAnim("rear_in_flight", REAR_IN_FLIGHT));
		controllers.add(new AnimationController<DragonEntity>("breath", 2, test -> PlayState.STOP)
			.triggerableAnim("breath", BREATH));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
