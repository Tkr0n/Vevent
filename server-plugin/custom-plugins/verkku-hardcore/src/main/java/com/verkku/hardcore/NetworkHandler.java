package com.verkku.hardcore;

import com.verkku.hardcore.network.KoStatePayload;
import com.verkku.hardcore.network.LivesSyncPayload;
import com.verkku.hardcore.network.WorldRegenPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class NetworkHandler {
    public NetworkHandler() {
        PayloadTypeRegistry.clientboundPlay().register(LivesSyncPayload.TYPE, LivesSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KoStatePayload.TYPE, KoStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldRegenPayload.TYPE, WorldRegenPayload.CODEC);
    }
}
