package com.wonderrealms.dragon;

import com.wonderrealms.WonderAdvancements;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A bone whistle on a string. Blow it to call every dragon of yours within earshot: they stop sitting and come,
 * flying to you if they have to. Sneak and blow it to tell them all to sit and wait.
 */
public class DragonWhistleItem extends Item {
	/** How far a whistle carries. */
	private static final double EARSHOT = 160;
	private static final int COOLDOWN = 30;

	public DragonWhistleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		boolean sit = player.isSecondaryUseActive();
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS,
			1.2F, sit ? 1.2F : 1.9F);
		server.sendParticles(ParticleTypes.NOTE, player.getX(), player.getEyeY() + 0.3, player.getZ(), 3, 0.3, 0.2, 0.3, 1.0);
		player.getCooldowns().addCooldown(player.getItemInHand(hand), COOLDOWN);

		List<DragonEntity> dragons = server.getEntitiesOfClass(DragonEntity.class, player.getBoundingBox().inflate(EARSHOT),
			dragon -> dragon.isOwnedBy(player) && dragon.isAlive());
		for (DragonEntity dragon : dragons) {
			if (sit) {
				dragon.orderToSit();
			} else {
				dragon.callTo();
			}
		}
		String message = dragons.isEmpty() ? "message.wonderrealms.whistle_unheard"
			: sit ? "message.wonderrealms.whistle_sit" : "message.wonderrealms.whistle_come";
		player.sendOverlayMessage(Component.translatable(message, dragons.size()).withStyle(dragons.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
		if (!dragons.isEmpty() && player instanceof ServerPlayer serverPlayer) {
			WonderAdvancements.award(serverPlayer, "whistle");
		}
		return InteractionResult.SUCCESS;
	}
}
