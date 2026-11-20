package com.wonderrealms.mixin.client;

import com.wonderrealms.dragon.DragonEntity;
import com.wonderrealms.pixel.PixelMining;
import com.wonderrealms.pixel.PixelatedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pixel mining on the player's own screen: instead of removing the whole block and waiting for the server to put
 * the carved block back (a visible flicker on every chip), predict the chip locally. The server still decides; its
 * update follows a moment later.
 * <p>
 * Also: riding a dragon, the inventory key opens its saddlebags if it has some, and your own inventory if not.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
	private void wonderrealms$predictPixelChip(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Player player = this.minecraft.player;
		Level level = this.minecraft.level;
		if (player == null || level == null || !PixelMining.canCarve(player)) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof PixelatedBlockEntity pixels) {
			int[] pixel = PixelMining.findPixel(player, pos, pixels);
			if (pixel != null) {
				pixels.remove(pixel[0], pixel[1], pixel[2]);
			}
			cir.setReturnValue(true);
		} else if (PixelMining.canPixelate(level, pos, level.getBlockState(pos))) {
			cir.setReturnValue(true); // the server turns it into pixels and chips the first one
		}
	}

	@Inject(method = "isServerControlledInventory", at = @At("HEAD"), cancellable = true)
	private void wonderrealms$saddlebagsOnly(CallbackInfoReturnable<Boolean> cir) {
		Player player = this.minecraft.player;
		if (player != null && player.getVehicle() instanceof DragonEntity dragon && !dragon.hasSaddlebags()) {
			cir.setReturnValue(false);
		}
	}
}
