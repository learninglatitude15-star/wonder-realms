package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Stopping time, and starting it again: what a Clockwork dragon's power does, and a Time Clock. Stopped, everything
 * stands still (mobs, arrows in flight, falling sand, the sun in the sky) except the players, and whatever they're
 * riding. The same again sets it all going.
 */
public final class ClockworkTime {
	private ClockworkTime() {
	}

	/** Stops time if it's running, or starts it again if it's stopped. */
	public static void toggle(ServerLevel level, Player who) {
		ServerTickRateManager ticks = level.getServer().tickRateManager();
		boolean stop = !ticks.isFrozen();
		ticks.setFrozen(stop);
		level.sendParticles(new DustParticleOptions(0xD8A848, 1.6F), who.getX(), who.getY() + 1, who.getZ(), 80, 2, 1, 2, 0);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), stop ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE,
			SoundSource.PLAYERS, 1.5F, stop ? 0.5F : 1.5F);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 1.5F, stop ? 0.5F : 1.2F);
		who.sendOverlayMessage(Component.translatable(stop ? "message.wonderrealms.time_stopped" : "message.wonderrealms.time_flows")
			.withStyle(ChatFormatting.GOLD));
		if (stop && who instanceof ServerPlayer player) {
			WonderAdvancements.award(player, "time_lord");
		}
	}
}
