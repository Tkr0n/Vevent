package com.verkku.hardcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record WorldRegenPayload(long newSeed) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("verkku-hardcore", "world_regen");
    public static final Type<WorldRegenPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldRegenPayload> CODEC = new StreamCodec<>() {
        @Override
        public WorldRegenPayload decode(RegistryFriendlyByteBuf buf) {
            long seed = buf.readLong();
            return new WorldRegenPayload(seed);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, WorldRegenPayload payload) {
            buf.writeLong(payload.newSeed);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
