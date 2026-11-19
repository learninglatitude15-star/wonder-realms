package com.wonderrealms;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards the Wonder Realms advancements that have no vanilla trigger (hatching an egg, carving a pixel, capturing a
 * pillager airship and so on). Each lives in data/wonderrealms/advancement with a single "minecraft:impossible"
 * criterion named "done", which only this code can grant.
 */
public final class WonderAdvancements {
	private WonderAdvancements() {
	}

	public static void award(ServerPlayer player, String path) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(WonderRealms.id(path));
		if (advancement != null) {
			player.getAdvancements().award(advancement, "done");
		}
	}
}
