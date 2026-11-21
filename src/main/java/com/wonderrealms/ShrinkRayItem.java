package com.wonderrealms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Shrink Ray: aim at a creature and zap it down to bug size for thirty seconds. A shrunken mob can only manage a
 * quarter of its usual damage against anything full-sized (a shrunken creeper's blast barely stings), and like a
 * tiny player it can climb walls. It doesn't work on players or bosses. Each zap costs a point of durability.
 */
public class ShrinkRayItem extends Item {
	private static final double RANGE = 24;
	private static final int SHRINK_TICKS = 20 * 30;
	private static final int COOLDOWN = 20;

	public ShrinkRayItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(RANGE));
		HitResult wall = server.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			entity -> entity instanceof LivingEntity living && living.isAlive() && entity.isPickable() && !(entity instanceof Player) && !isBoss(entity),
			eye.distanceToSqr(end));
		Vec3 to = hit != null ? hit.getLocation() : end;
		beam(server, eye.add(0, -0.15, 0), to);

		if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.8F);
			return InteractionResult.SUCCESS;
		}
		target.addEffect(new MobEffectInstance(TinyWorlds.SHRUNKEN, SHRINK_TICKS), player);
		target.ejectPassengers();
		target.stopRiding();
		server.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1F, 1.6F);
		server.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		stack.hurtAndBreak(1, player, hand);
		if (player instanceof ServerPlayer serverPlayer) {
			WonderAdvancements.award(serverPlayer, "shrink_mob");
			if (target.getType() == EntityTypes.CREEPER) {
				WonderAdvancements.award(serverPlayer, "tiny_terror");
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean isBoss(Entity entity) {
		return entity.getType() == EntityTypes.ENDER_DRAGON || entity.getType() == EntityTypes.WITHER
			|| entity instanceof com.wonderrealms.dragon.ElderDragonEntity;
	}

	/** A sparkling purple line from the ray to wherever it struck. */
	private static void beam(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 path = to.subtract(from);
		double length = path.length();
		Vec3 step = path.normalize();
		for (double d = 0.8; d < length; d += 0.5) {
			Vec3 p = from.add(step.scale(d));
			level.sendParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}
}
