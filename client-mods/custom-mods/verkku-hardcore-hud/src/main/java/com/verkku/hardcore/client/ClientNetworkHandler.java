package com.verkku.hardcore.client;

import com.verkku.hardcore.network.KoStatePayload;
import com.verkku.hardcore.network.LivesSyncPayload;
import com.verkku.hardcore.network.WorldRegenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.chat.Component;

public class ClientNetworkHandler {
    public ClientNetworkHandler() {
        PayloadTypeRegistry.clientboundPlay().register(LivesSyncPayload.TYPE, LivesSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KoStatePayload.TYPE, KoStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldRegenPayload.TYPE, WorldRegenPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(LivesSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                HardcoreHudMod mod = HardcoreHudMod.getInstance();
                if (mod != null) {
                    for (LivesSyncPayload.PlayerLivesData data : payload.players()) {
                        mod.getHeartRenderer().updateLives(data.uuid(), data.lives());
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(KoStatePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                HardcoreHudMod mod = HardcoreHudMod.getInstance();
                if (mod != null) {
                    mod.getHeartRenderer().updateKoState(payload.playerUuid(), payload.isKo());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(WorldRegenPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                HardcoreHudMod.LOGGER.info("World regeneration triggered! New seed: {}", payload.newSeed());

                if (context.client().player != null) {
                    context.client().player.sendSystemMessage(
                        Component.literal("\u00a74\u00a7l\u00a1Mundo regenerado! Nuevo seed: \u00a7e\u00a7l" + payload.newSeed())
                    );
                }
            });
        });
    }
}
