package com.verkku.hardcore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreHudMod implements ClientModInitializer {
    public static final String MOD_ID = "verkku-hardcore-hud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HardcoreHudMod instance;
    private HeartHudRenderer heartRenderer;
    private ClientNetworkHandler networkHandler;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Verkku Hardcore HUD initializing...");

        this.heartRenderer = new HeartHudRenderer();
        this.networkHandler = new ClientNetworkHandler();

        Identifier hudId = Identifier.fromNamespaceAndPath(MOD_ID, "hearts");
        HudElementRegistry.addLast(hudId, (extractor, deltaTracker) -> {
            heartRenderer.render(extractor);
        });

        LOGGER.info("Verkku Hardcore HUD initialized!");
    }

    public static HardcoreHudMod getInstance() {
        return instance;
    }

    public HeartHudRenderer getHeartRenderer() {
        return heartRenderer;
    }

    public ClientNetworkHandler getNetworkHandler() {
        return networkHandler;
    }
}
