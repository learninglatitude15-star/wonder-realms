package com.wonderrealms.airship;

import com.wonderrealms.WonderRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by an airship's pilot pressing the cannon key: "fire where I'm looking". */
public record CannonPayload() implements CustomPacketPayload {
	public static final CannonPayload INSTANCE = new CannonPayload();
	public static final Type<CannonPayload> TYPE = new Type<>(WonderRealms.id("airship_cannon"));
	public static final StreamCodec<ByteBuf, CannonPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
