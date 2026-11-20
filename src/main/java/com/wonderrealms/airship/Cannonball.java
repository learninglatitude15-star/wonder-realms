package com.wonderrealms.airship;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A shot from an airship's cannon. It flies fast with a trail of smoke and bursts on impact with a bang, hurting
 * whatever it hits and anyone right beside it. It never breaks blocks. Airships it hits get holed (see
 * {@link AirshipEntity}). It won't hit the ship it was fired from, or anyone aboard her.
 */
public class Cannonball extends ThrowableItemProjectile {
	private static final float DIRECT_DAMAGE = 8F;
	private static final float SPLASH_DAMAGE = 4F;
	private static final double SPLASH_RADIUS = 2.0;
	private static final int LIFETIME = 200;

	public Cannonball(EntityType<? extends Cannonball> type, Level level) {
		super(type, level);
	}

	public Cannonball(Level level, LivingEntity gunner) {
		super(Airships.CANNONBALL, gunner, level, new ItemStack(Items.FIRE_CHARGE));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIRE_CHARGE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.02;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level) {
			if (this.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (this.tickCount > LIFETIME) {
				this.discard();
			}
		}
	}

	/** Not the gunner's own ship, nor anyone sailing on it. */
	@Override
	protected boolean canHitEntity(Entity target) {
		Entity ship = this.getOwner() == null ? null : this.getOwner().getVehicle();
		if (ship != null && (target == ship || target.getVehicle() == ship)) {
			return false;
		}
		return super.canHitEntity(target);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel level) {
			hit.getEntity().hurtServer(level, this.damageSources().explosion(this, this.getOwner()), DIRECT_DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = result.getLocation();
		Entity struck = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
		for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH_RADIUS),
				living -> living != struck && living.isAlive() && this.canHitEntity(living) && living != this.getOwner())) {
			nearby.hurtServer(level, this.damageSources().explosion(this, this.getOwner()), SPLASH_DAMAGE);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.2F, 1.1F);
		this.discard();
	}
}
