package com.wonderrealms.dragon;

import com.wonderrealms.WonderRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by a rider pressing the dragon power key. */
public record PowerPayload() implements CustomPacketPayload {
	public static final PowerPayload INSTANCE = new PowerPayload();
	public static final Type<PowerPayload> TYPE = new Type<>(WonderRealms.id("dragon_power"));
	public static final StreamCodec<ByteBuf, PowerPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
