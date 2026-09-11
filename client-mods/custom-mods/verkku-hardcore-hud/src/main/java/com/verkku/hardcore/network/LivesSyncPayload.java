package com.verkku.hardcore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LivesSyncPayload(List<PlayerLivesData> players) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("verkku-hardcore", "lives_sync");
    public static final CustomPacketPayload.Type<LivesSyncPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, LivesSyncPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeInt(payload.players().size());
            for (PlayerLivesData data : payload.players()) {
                buf.writeLong(data.uuid().getMostSignificantBits());
                buf.writeLong(data.uuid().getLeastSignificantBits());
                buf.writeUtf(data.name());
                buf.writeInt(data.lives());
            }
        },
        buf -> {
            int count = buf.readInt();
            List<PlayerLivesData> players = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID uuid = new UUID(buf.readLong(), buf.readLong());
                String name = buf.readUtf();
                int lives = buf.readInt();
                players.add(new PlayerLivesData(uuid, name, lives));
            }
            return new LivesSyncPayload(players);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PlayerLivesData(UUID uuid, String name, int lives) {}
}
