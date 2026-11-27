package com.wonderrealms.dragon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A clock set round with Clockwork dragon scales. Use it to stop time, and again to start it (see {@link ClockworkTime}). */
public class TimeClockItem extends Item {
	public TimeClockItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel server) {
			ClockworkTime.toggle(server, player);
		}
		return InteractionResult.SUCCESS;
	}
}
