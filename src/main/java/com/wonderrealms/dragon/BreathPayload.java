package com.wonderrealms.dragon;

import com.wonderrealms.WonderRealms;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by a rider pressing the breath key: "breathe where I'm looking". */
public record BreathPayload() implements CustomPacketPayload {
	public static final BreathPayload INSTANCE = new BreathPayload();
	public static final Type<BreathPayload> TYPE = new Type<>(WonderRealms.id("dragon_breath"));
	public static final StreamCodec<ByteBuf, BreathPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
