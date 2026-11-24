package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Elder Dragon: the ancient guardian of a Sky Citadel, more than twice the size of any other dragon, with a
 * boss bar to match. It can't be tamed or ridden. It breathes fire, then frost, then lightning, then shadow as it
 * weakens, and each time it changes it roars and throws back everyone near it. Beaten, it leaves its Dragon Heart
 * and a pile of Elder Scales.
 */
public class ElderDragonEntity extends DragonEntity {
	private static final DragonElement[] PHASES = {DragonElement.EMBER, DragonElement.FROST, DragonElement.STORM, DragonElement.SHADOW};
	private static final BossEvent.BossBarColor[] PHASE_COLORS = {
		BossEvent.BossBarColor.RED, BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.PURPLE};
	private static final double BOSS_HEALTH = 400;
	private static final float BOSS_SCALE = 2.2F;
	/** It comes for any player this close, and shows its health to any player this close. */
	private static final double SIGHT = 40, BAR_RANGE = 64;
	/** The roar when it changes element throws back everyone this close. */
	private static final double ROAR_RADIUS = 14;

	private final ServerBossEvent bossBar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.wonderrealms.elder_dragon"),
		PHASE_COLORS[0], BossEvent.BossBarOverlay.NOTCHED_10);
	private int phase;

	public ElderDragonEntity(EntityType<? extends DragonEntity> type, Level level) {
		super(type, level, DragonElement.EMBER);
		this.xpReward = 500;
		this.setGrowth(1F); // full size from the start: its size and health come with its growth
	}

	public static AttributeSupplier.Builder createAttributes() {
		return DragonEntity.createAttributes()
			.add(Attributes.MAX_HEALTH, BOSS_HEALTH)
			.add(Attributes.ARMOR, 12)
			.add(Attributes.ATTACK_DAMAGE, 14)
			.add(Attributes.FOLLOW_RANGE, 64)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1)
			.add(Attributes.FLYING_SPEED, 1.1);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> this.distanceToSqr(target) < SIGHT * SIGHT));
	}

	@Override
	protected float adultScale() {
		return BOSS_SCALE;
	}

	@Override
	protected double adultHealth() {
		return BOSS_HEALTH;
	}

	@Override
	protected DragonElement breathElement() {
		return PHASES[this.phase];
	}

	@Override
	protected float breathDamage() {
		return 5F;
	}

	/** Not an egg, but the heart of the oldest dragon in the sky. */
	@Override
	protected ItemStack trophy() {
		return new ItemStack(Dragons.DRAGON_HEART);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS; // no taming, no riding
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// ---- The fight ------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		float health = this.getHealth() / this.getMaxHealth();
		this.bossBar.setProgress(health);
		int wanted = Math.min(PHASES.length - 1, (int) ((1F - health) * PHASES.length));
		if (wanted > this.phase) {
			this.phase = wanted;
			this.roar(level);
		}
		if (this.tickCount % 10 == 0) {
			this.updateWatchers(level);
		}
	}

	/** It changes element: a roar that throws back and hurts everyone close by, and its scales flare with the new element. */
	private void roar(ServerLevel level) {
		this.triggerAnim("power", this.onGround() ? "rear" : "rear_in_flight"); // it rears up as it roars
		this.bossBar.setColor(PHASE_COLORS[this.phase]);
		Vec3 middle = this.position().add(0, this.getBbHeight() * 0.5, 0);
		level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4F, 0.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y, middle.z, 1, 0, 0, 0, 0);
		level.sendParticles(this.breathElement().breathParticle, middle.x, middle.y, middle.z, 300, 4, 3, 4, 0.1);
		for (ServerPlayer player : level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < ROAR_RADIUS * ROAR_RADIUS)) {
			Vec3 away = player.position().subtract(this.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
			player.hurtServer(level, this.damageSources().mobAttack(this), 4F);
			player.push(away.x * 2.0, 0.8, away.z * 2.0); // sent along with the hurt
		}
	}

	/** Shows the boss bar to everyone near enough to see the fight, and hides it from everyone else. */
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
				WonderAdvancements.award(player, "elder_awakens");
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
			WonderAdvancements.award(player, "elder_slayer");
		}
		this.bossBar.removeAllPlayers();
	}

	/** Its heart (see {@link #trophy}), a hoard of Elder Scales, and more dragon scales besides. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(Dragons.ELDER_SCALE, 8 + this.random.nextInt(5)));
		this.spawnAtLocation(level, new ItemStack(Dragons.DRAGON_SCALE, 3 + this.random.nextInt(4)));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("phase", this.phase);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.phase = Math.clamp(input.getIntOr("phase", 0), 0, PHASES.length - 1);
		this.bossBar.setColor(PHASE_COLORS[this.phase]);
	}
}
