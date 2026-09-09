package com.verkku.hardcore;

import com.verkku.hardcore.commands.HardcoreCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreMod implements ModInitializer {
    public static final String MOD_ID = "verkku-hardcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static HardcoreMod instance;
    private MinecraftServer server;
    private LifeManager lifeManager;
    private KOHandler koHandler;
    private WorldRegenManager worldRegenManager;

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Verkku Hardcore initializing...");

        new NetworkHandler();

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HardcoreCommand.register(dispatcher);
        });

        LOGGER.info("Verkku Hardcore initialized!");
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;
        this.lifeManager = new LifeManager(server);
        this.koHandler = new KOHandler(this);
        this.worldRegenManager = new WorldRegenManager(this);

        LOGGER.info("Hardcore system loaded with {} max lives", lifeManager.getMaxLives());
    }

    private void onServerStopping(MinecraftServer server) {
        if (lifeManager != null) {
            lifeManager.save();
        }
    }

    public static HardcoreMod getInstance() {
        return instance;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public LifeManager getLifeManager() {
        return lifeManager;
    }

    public KOHandler getKoHandler() {
        return koHandler;
    }

    public WorldRegenManager getWorldRegenManager() {
        return worldRegenManager;
    }
}
