package com.verkku.hardcore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record KoStatePayload(UUID playerUuid, boolean isKo) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("verkku-hardcore", "ko_state");
    public static final CustomPacketPayload.Type<KoStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, KoStatePayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeLong(payload.playerUuid().getMostSignificantBits());
            buf.writeLong(payload.playerUuid().getLeastSignificantBits());
            buf.writeBoolean(payload.isKo());
        },
        buf -> {
            UUID uuid = new UUID(buf.readLong(), buf.readLong());
            boolean isKo = buf.readBoolean();
            return new KoStatePayload(uuid, isKo);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
