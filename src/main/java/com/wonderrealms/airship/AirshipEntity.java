package com.wonderrealms.airship;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.wonderrealms.WonderAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An airship: a wooden hull hung under a striped balloon, pushed along by a steam-driven propeller. The pilot
 * (first aboard) steers with WASD, climbs with jump and descends with sprint; up to three passengers ride along.
 * The engine burns coal, charcoal, blaze rods or coal blocks (use them on the ship). Out of fuel it can only drift
 * slowly and sinks. Left empty, it hovers where it was parked. Punch it to pack it back into an item.
 * Sneak and use it with an empty hand to open its cargo hold. Dye recolors the balloon. A cannon (use one on the
 * ship) fires from the bow when the pilot presses the cannon key; cannonballs hole a hull, and planks patch it.
 * Pillagers fly them too, under dark balloons: a pillager at the helm patrols its outpost and hunts players while
 * the crew shoots and the bow cannon fires, and their hold carries their plunder.
 */
public class AirshipEntity extends Entity implements GeoEntity {
	private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.INT);
	/** The balloon's dye color id, or -1 for the classic red and cream stripes. */
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> CANNON = SynchedEntityData.defineId(AirshipEntity.class, EntityDataSerializers.BOOLEAN);
	private static final int MAX_PASSENGERS = 4;
	private static final int MAX_FUEL = 20 * 60 * 30;
	private static final float BREAK_DAMAGE = 40F;
	private static final int CARGO_SLOTS = 27;

	private static final double ACCEL = 0.035, DRIFT_ACCEL = 0.01;
	private static final double MAX_SPEED = 0.65, DRIFT_SPEED = 0.15;
	private static final double CLIMB = 0.22, SINK = 0.03;
	private static final float TURN = 3F;
	/** How far a pillager crew spots players from. */
	private static final double RAIDER_SIGHT = 40;
	/** Where a pillager ship hovers over its target: close enough for crossbows, which reach 8 blocks. */
	private static final double ATTACK_RING = 3.5, ATTACK_HEIGHT = 3.5;
	/** Fuel left aboard a captured pillager ship. */
	private static final int LOOT_FUEL = 20 * 120;
	/** Cannonball hits wear the hull down (they don't mend by themselves); four sink a ship. Planks patch it. */
	private static final float HULL_STRENGTH = 100F, CANNONBALL_HULL_DAMAGE = 25F, PLANK_REPAIR = 25F;
	/** Ticks between shots: a player's cannon, a pillager's. Each player shot burns this much fuel. */
	private static final int CANNON_COOLDOWN = 30, RAIDER_CANNON_COOLDOWN = 80, SHOT_FUEL = 20 * 10;
	private static final double RAIDER_CANNON_RANGE = 28;
	private static final DyeColor[] PILLAGER_COLORS = {DyeColor.GRAY, DyeColor.BLACK, DyeColor.BROWN};

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("move.fly");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private boolean inputForward, inputBack, inputLeft, inputRight, inputUp, inputDown;
	private float damage;
	private float hullDamage;
	private int cannonCooldown;
	/** The outpost a pillager ship patrols around. Null for player-built ships. */
	private BlockPos home;
	/** A chest's worth of cargo, opened by sneaking and using the ship with an empty hand. */
	private final SimpleContainer cargo = new SimpleContainer(CARGO_SLOTS) {
		@Override
		public boolean stillValid(Player player) {
			return !AirshipEntity.this.isRemoved() && player.distanceToSqr(AirshipEntity.this) < 64;
		}
	};

	public AirshipEntity(EntityType<? extends AirshipEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.blocksBuilding = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FUEL, 0);
		builder.define(COLOR, -1);
		builder.define(CANNON, false);
	}

	public int fuel() {
		return this.entityData.get(FUEL);
	}

	/** The balloon's dye color id (see {@link DyeColor#getId()}), or -1 for the classic stripes. */
	public int balloonColor() {
		return this.entityData.get(COLOR);
	}

	public boolean hasCannon() {
		return this.entityData.get(CANNON);
	}

	private void setFuel(int fuel) {
		this.entityData.set(FUEL, Mth.clamp(fuel, 0, MAX_FUEL));
	}

	/** Called on the pilot's client every tick with their movement keys. */
	public void setInput(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
		this.inputForward = forward;
		this.inputBack = back;
		this.inputLeft = left;
		this.inputRight = right;
		this.inputUp = up;
		this.inputDown = down;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.damage > 0) {
			this.damage = Math.max(0, this.damage - 1F); // dents knock out over time; only a quick beating breaks it
		}
		if (this.cannonCooldown > 0) {
			this.cannonCooldown--;
		}
		if (this.hullDamage >= HULL_STRENGTH / 2 && this.tickCount % 8 == 0 && this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 1, 0.6, 0.2, 0.6, 0.01); // badly holed
		}
		if (this.isLocalInstanceAuthoritative()) {
			if (this.isRaiderCrewed()) {
				this.raiderFly();
			} else {
				this.fly();
			}
		} else {
			this.getInterpolation().interpolate();
			this.setDeltaMovement(Vec3.ZERO);
		}

		if (this.level() instanceof ServerLevel level) {
			LivingEntity pilot = this.getControllingPassenger();
			if (pilot != null && this.fuel() > 0) {
				this.setFuel(this.fuel() - 1);
			}
			if ((pilot != null && this.fuel() > 0) || this.isRaiderCrewed()) {
				if (this.tickCount % 6 == 0) {
					Vec3 stack = this.position().add(this.forward().scale(-1.0)).add(0, 1.2, 0);
					level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, stack.x, stack.y, stack.z, 1, 0.05, 0.05, 0.05, 0.01);
				}
			}
			if (pilot instanceof ServerPlayer player && this.tickCount % 20 == 0) {
				int seconds = this.fuel() / 20;
				player.sendOverlayMessage(seconds > 0
					? Component.translatable("message.wonderrealms.airship_fuel", seconds / 60, String.format("%02d", seconds % 60)).withStyle(ChatFormatting.GOLD)
					: Component.translatable("message.wonderrealms.airship_no_fuel").withStyle(ChatFormatting.RED));
			}
		}
	}

	// ---- Pillager crews ---------------------------------------------------------------------------------------

	/** A pillager at the helm: the ship flies itself and its crew shoots from the deck. */
	public boolean isRaiderCrewed() {
		return this.getFirstPassenger() instanceof Raider;
	}

	/**
	 * Pillager autopilot (server side): circle the outpost it belongs to, and when a player comes within range,
	 * close in and hover above them so the crew can shoot.
	 */
	private void raiderFly() {
		Vec3 home = this.home != null ? Vec3.atCenterOf(this.home) : this.position();
		Player target = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), RAIDER_SIGHT,
			e -> e instanceof Player p && !p.isCreative() && !p.isSpectator());
		double angle = this.tickCount * 0.015;
		Vec3 desired = target != null
			? target.position().add(Math.cos(angle) * ATTACK_RING, ATTACK_HEIGHT, Math.sin(angle) * ATTACK_RING)
			: home.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
		Vec3 to = desired.subtract(this.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flat > 0.5) {
			float wanted = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
			this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(wanted - this.getYRot()), -TURN, TURN));
		}
		double speed = Mth.clamp(flat * 0.03, 0, 0.35);
		Vec3 target3 = this.forward().scale(speed).add(0, Mth.clamp(to.y * 0.05, -0.15, 0.15), 0);
		Vec3 motion = this.getDeltaMovement().lerp(target3, 0.1);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);

		// A cannon at the bow: the pilot fires on players in range whenever it's loaded.
		if (target != null && this.hasCannon() && this.cannonCooldown <= 0 && this.distanceTo(target) < RAIDER_CANNON_RANGE
				&& this.getFirstPassenger() instanceof LivingEntity gunner && gunner.hasLineOfSight(target)) {
			this.fireCannon(gunner, target.getEyePosition().subtract(this.muzzle()));
		}
	}

	// ---- Cannon ---------------------------------------------------------------------------------------------

	private Vec3 muzzle() {
		return this.position().add(this.forward().scale(2.4)).add(0, 0.5, 0);
	}

	/**
	 * Fires the bow cannon along {@code direction} (server side). A player's shots each burn ten seconds of fuel;
	 * pillagers reload more slowly and aim less steadily. Returns whether it fired.
	 */
	public boolean fireCannon(LivingEntity gunner, Vec3 direction) {
		if (!(this.level() instanceof ServerLevel level) || !this.hasCannon() || this.cannonCooldown > 0 || direction.lengthSqr() < 1.0E-6) {
			return false;
		}
		boolean player = gunner instanceof Player;
		if (player && this.fuel() < SHOT_FUEL) {
			if (gunner instanceof ServerPlayer serverPlayer) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.wonderrealms.cannon_no_fuel").withStyle(ChatFormatting.RED));
			}
			return false;
		}
		Vec3 muzzle = this.muzzle();
		Cannonball ball = new Cannonball(level, gunner);
		ball.setPos(muzzle.x, muzzle.y, muzzle.z);
		ball.shoot(direction.x, direction.y, direction.z, 2.2F, player ? 0.5F : 3F);
		level.addFreshEntity(ball);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.15, 0.15, 0.15, 0.03);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.05);
		this.cannonCooldown = player ? CANNON_COOLDOWN : RAIDER_CANNON_COOLDOWN;
		if (player) {
			this.setFuel(this.fuel() - SHOT_FUEL);
			if (gunner instanceof ServerPlayer serverPlayer) {
				WonderAdvancements.award(serverPlayer, "broadside");
			}
		}
		return true;
	}

	/**
	 * Builds a pillager airship over {@code pos}: a pilot plus crew, with a couple of minutes' fuel left in the
	 * tanks for whoever takes it from them. Works during world generation too, where it must only use {@code level}'s
	 * own random: the world's is the server thread's alone, and worldgen runs on other threads.
	 */
	public static AirshipEntity spawnRaiderShip(ServerLevelAccessor level, BlockPos pos, int crew) {
		ServerLevel server = level.getLevel();
		AirshipEntity ship = Airships.TYPE.create(server, EntitySpawnReason.STRUCTURE);
		if (ship == null) {
			return null;
		}
		ship.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
		ship.home = pos.immutable();
		ship.setFuel(LOOT_FUEL);
		ship.stockPlunder(level.getRandom());
		// Pillagers fly dark balloons, and every one of their ships carries a cannon.
		ship.entityData.set(COLOR, PILLAGER_COLORS[level.getRandom().nextInt(PILLAGER_COLORS.length)].getId());
		ship.entityData.set(CANNON, true);
		for (int i = 0; i < crew; i++) {
			Pillager pillager = EntityTypes.PILLAGER.create(server, EntitySpawnReason.STRUCTURE);
			if (pillager == null) {
				continue;
			}
			pillager.snapTo(ship.getX(), ship.getY(), ship.getZ());
			pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
			pillager.setPersistenceRequired();
			pillager.startRiding(ship, true, false);
		}
		level.addFreshEntityWithPassengers(ship);
		return ship;
	}

	/** Bright balloons for the airships the sky villagers lend to travellers. */
	private static final DyeColor[] VILLAGE_COLORS = {DyeColor.RED, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.ORANGE, DyeColor.MAGENTA};

	/**
	 * A sky village's airship, moored in the village for anyone to borrow: a bright balloon, a few minutes' fuel and
	 * no cannon. Works during world generation too.
	 */
	public static AirshipEntity spawnVillageShip(ServerLevelAccessor level, BlockPos pos) {
		ServerLevel server = level.getLevel();
		AirshipEntity ship = Airships.TYPE.create(server, EntitySpawnReason.STRUCTURE);
		if (ship == null) {
			return null;
		}
		ship.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0F);
		ship.setFuel(LOOT_FUEL);
		ship.entityData.set(COLOR, VILLAGE_COLORS[level.getRandom().nextInt(VILLAGE_COLORS.length)].getId());
		level.addFreshEntity(ship);
		return ship;
	}

	/** A pillager ship's hold: their spoils, for whoever takes the ship. */
	private void stockPlunder(RandomSource random) {
		ItemStack[] spoils = {
			new ItemStack(Items.EMERALD, 2 + random.nextInt(5)), new ItemStack(Items.ARROW, 8 + random.nextInt(12)),
			new ItemStack(Items.COAL, 4 + random.nextInt(8)), new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)),
			new ItemStack(Items.WHEAT, 4 + random.nextInt(8)), new ItemStack(Items.STRING, 2 + random.nextInt(4)),
			new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3))};
		int stacks = 3 + random.nextInt(3);
		for (int i = 0; i < stacks; i++) {
			this.cargo.setItem(random.nextInt(CARGO_SLOTS), spoils[random.nextInt(spoils.length)].copy());
		}
	}

	private Vec3 forward() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Movement, run wherever this airship is simulated (the pilot's client, or the server when unpiloted). */
	private void fly() {
		boolean piloted = this.getControllingPassenger() instanceof Player;
		boolean fuelled = this.fuel() > 0;
		Vec3 v = this.getDeltaMovement();

		if (piloted) {
			if (this.inputLeft) {
				this.setYRot(this.getYRot() - TURN);
			}
			if (this.inputRight) {
				this.setYRot(this.getYRot() + TURN);
			}
		}
		double thrust = !piloted ? 0 : this.inputForward ? 1 : this.inputBack ? -0.5 : 0;
		Vec3 horizontal = new Vec3(v.x, 0, v.z).scale(0.96).add(this.forward().scale(thrust * (fuelled ? ACCEL : DRIFT_ACCEL)));
		double max = fuelled ? MAX_SPEED : DRIFT_SPEED;
		if (horizontal.length() > max) {
			horizontal = horizontal.normalize().scale(max);
		}
		double targetY;
		if (piloted && this.inputUp && fuelled) {
			targetY = CLIMB;
		} else if (piloted && this.inputDown) {
			targetY = -CLIMB;
		} else if (fuelled || !piloted) {
			targetY = 0; // the balloon holds it up
		} else {
			targetY = -SINK;
		}
		double vy = Mth.lerp(0.2, v.y, targetY);
		Vec3 motion = new Vec3(horizontal.x, vy, horizontal.z);
		this.setDeltaMovement(motion);
		this.move(MoverType.SELF, motion);
	}

	// ---- Riders ---------------------------------------------------------------------------------------------

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack stack = player.getItemInHand(hand);
		int burn = fuelValue(stack);
		if (burn > 0) {
			if (!this.level().isClientSide()) {
				if (this.fuel() + burn <= MAX_FUEL || this.fuel() == 0) {
					this.setFuel(this.fuel() + burn);
					stack.consume(1, player);
					this.playSound(SoundEvents.FIRECHARGE_USE, 0.6F, 1.2F);
				}
			}
			return InteractionResult.SUCCESS;
		}
		// Dye recolors the balloon.
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye != null && !this.isRaiderCrewed()) {
			if (!this.level().isClientSide() && this.balloonColor() != dye.getId()) {
				this.entityData.set(COLOR, dye.getId());
				stack.consume(1, player);
				this.playSound(SoundEvents.DYE_USE, 1F, 1F);
				if (player instanceof ServerPlayer serverPlayer) {
					WonderAdvancements.award(serverPlayer, "paint_the_sky");
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Airships.CANNON_ITEM) && !this.hasCannon()) {
			if (!this.level().isClientSide()) {
				this.entityData.set(CANNON, true);
				stack.consume(1, player);
				this.playSound(SoundEvents.ANVIL_USE, 0.5F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		}
		// Planks patch a hull holed by cannon fire.
		if (stack.is(ItemTags.PLANKS) && this.hullDamage > 0) {
			if (this.level() instanceof ServerLevel level) {
				this.hullDamage = Math.max(0, this.hullDamage - PLANK_REPAIR);
				stack.consume(1, player);
				this.playSound(SoundEvents.WOOD_PLACE, 1F, 0.9F);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.6, this.getZ(), 5, 0.8, 0.2, 0.8, 0.0);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (!stack.isEmpty() || this.isRaiderCrewed()) {
				return InteractionResult.PASS; // no rummaging through the hold while its crew is aboard
			}
			if (!this.level().isClientSide()) {
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, this.cargo),
					Component.translatable("container.wonderrealms.airship")));
				this.playSound(SoundEvents.BARREL_OPEN, 0.6F, 1F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.canAddPassenger(player) || this.isRaiderCrewed()) {
			return InteractionResult.PASS;
		}
		if (!this.level().isClientSide()) {
			player.startRiding(this);
			if (this.home != null && player instanceof ServerPlayer serverPlayer && this.getFirstPassenger() == player) {
				WonderAdvancements.award(serverPlayer, "sky_pirate");
				this.home = null; // it's theirs now
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static int fuelValue(ItemStack stack) {
		if (stack.is(Items.COAL_BLOCK)) {
			return 20 * 60 * 10;
		} else if (stack.is(Items.BLAZE_ROD)) {
			return 20 * 120;
		} else if (stack.is(ItemTags.COALS)) {
			return 20 * 80;
		}
		return 0;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < MAX_PASSENGERS;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : null;
	}

	/** Pilot at the wheel by the stern; passengers along the deck. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
		int seat = Math.max(0, this.getPassengers().indexOf(passenger));
		Vec3 offset = switch (seat) {
			case 0 -> new Vec3(0, 0.4, 0.9);
			case 1 -> new Vec3(0.55, 0.4, 0.1);
			case 2 -> new Vec3(-0.55, 0.4, 0.1);
			default -> new Vec3(0, 0.4, -0.8);
		};
		return offset.yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		// Turn riders with the ship, like a boat does.
		float turned = this.getYRot() - this.yRotO;
		passenger.setYRot(passenger.getYRot() + turned);
		passenger.setYHeadRot(passenger.getYHeadRot() + turned);
	}

	// ---- Physical presence ----------------------------------------------------------------------------------

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved() || this.isInvulnerableToBase(source)) {
			return false;
		}
		// Cannonballs hole the hull, and the holes stay until they're patched with planks.
		if (source.getDirectEntity() instanceof Cannonball) {
			this.hullDamage += CANNONBALL_HULL_DAMAGE;
			this.playSound(SoundEvents.WOOD_BREAK, 1F, 0.7F);
			if (this.hullDamage >= HULL_STRENGTH) {
				if (source.getEntity() instanceof ServerPlayer shooter && (this.isRaiderCrewed() || this.home != null)) {
					WonderAdvancements.award(shooter, "shot_down");
				}
				this.breakApart(level, true);
			}
			return true;
		}
		// Otherwise only a player's own fists or blade can knock an airship apart: stray arrows and bolts bounce off.
		if (!(source.getDirectEntity() instanceof Player)) {
			return false;
		}
		boolean creative = source.getEntity() instanceof Player player && player.isCreative();
		this.damage += amount * 10F;
		this.playSound(SoundEvents.WOOD_HIT, 1F, 0.8F);
		if (creative || this.damage > BREAK_DAMAGE) {
			this.breakApart(level, !creative);
		}
		return true;
	}

	/** Packs the ship up (or wrecks it): its cargo and cannon spill out, and everyone aboard falls off. */
	private void breakApart(ServerLevel level, boolean dropShip) {
		if (dropShip) {
			this.spawnAtLocation(level, new ItemStack(Airships.AIRSHIP_ITEM));
			if (this.hasCannon()) {
				this.spawnAtLocation(level, new ItemStack(Airships.CANNON_ITEM));
			}
		}
		Containers.dropContents(level, this, this.cargo);
		this.ejectPassengers();
		this.discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("fuel", this.fuel());
		output.putInt("balloon_color", this.balloonColor());
		output.putBoolean("cannon", this.hasCannon());
		output.putFloat("hull_damage", this.hullDamage);
		if (this.home != null) {
			output.putLong("home", this.home.asLong());
		}
		NonNullList<ItemStack> items = NonNullList.withSize(CARGO_SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < CARGO_SLOTS; i++) {
			items.set(i, this.cargo.getItem(i));
		}
		ContainerHelper.saveAllItems(output, items);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setFuel(input.getIntOr("fuel", 0));
		this.entityData.set(COLOR, Mth.clamp(input.getIntOr("balloon_color", -1), -1, 15));
		this.entityData.set(CANNON, input.getBooleanOr("cannon", false));
		this.hullDamage = input.getFloatOr("hull_damage", 0F);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		this.home = home == Long.MIN_VALUE ? null : BlockPos.of(home);
		NonNullList<ItemStack> items = NonNullList.withSize(CARGO_SLOTS, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
		for (int i = 0; i < CARGO_SLOTS; i++) {
			this.cargo.setItem(i, items.get(i));
		}
	}

	// ---- Animation ------------------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<AirshipEntity>("main", 8, test -> {
			AirshipEntity ship = test.animatable();
			boolean running = (ship.getControllingPassenger() != null && ship.fuel() > 0) || ship.isRaiderCrewed();
			return test.setAndContinue(running ? FLY : IDLE);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
