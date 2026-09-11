package com.verkku.hardcore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldRegenPayload(long newSeed) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("verkku-hardcore", "world_regen");
    public static final CustomPacketPayload.Type<WorldRegenPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, WorldRegenPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeLong(payload.newSeed()),
        buf -> new WorldRegenPayload(buf.readLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
