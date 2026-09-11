package com.verkku.hardcore;

import com.verkku.hardcore.commands.HardcoreCommand;
import com.verkku.hardcore.network.LivesSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
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

        // Check for world regeneration flag BEFORE the world loads
        WorldRegenManager.checkAndDeleteWorldOnStartup();

        new NetworkHandler();

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HardcoreCommand.register(dispatcher);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                LOGGER.info("Player {} died from {}, scheduling life decrement", player.getName().getString(), damageSource.getMsgId());
                if (lifeManager != null && koHandler != null) {
                    server.execute(() -> {
                        try {
                            boolean hadLives = lifeManager.decrementLife(player);
                            int remaining = lifeManager.getLives(player);
                            LOGGER.info("Player {} now has {} lives remaining", player.getName().getString(), remaining);

                            koHandler.syncLivesToAllPlayers();

                            if (!hadLives || remaining <= 0) {
                                LOGGER.info("Player {} has no lives left! Triggering world regeneration", player.getName().getString());
                                worldRegenManager.triggerWorldRegeneration();
                            } else {
                                String message = "§e§l" + player.getName().getString() + " respawning... §c§l(" + remaining + " vidas restantes)";
                                server.getPlayerList().broadcastSystemMessage(
                                    net.minecraft.network.chat.Component.literal(message), false
                                );
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error handling death for {}: {}", player.getName().getString(), e.getMessage(), e);
                        }
                    });
                }
            }
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, force) -> {
            if (lifeManager != null && server != null) {
                server.execute(() -> {
                    try {
                        newPlayer.setGameMode(GameType.SURVIVAL);
                        LOGGER.info("Forced {} to SURVIVAL after respawn", newPlayer.getName().getString());
                    } catch (Exception e) {
                        LOGGER.error("Failed to set gamemode for {}: {}", newPlayer.getName().getString(), e.getMessage());
                    }
                });
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, serverInstance) -> {
            if (lifeManager != null) {
                ServerPlayer player = handler.getPlayer();
                int lives = lifeManager.getLives(player);
                ServerPlayNetworking.send(player, new LivesSyncPayload(
                    java.util.List.of(new LivesSyncPayload.PlayerLivesData(player.getUUID(), player.getName().getString(), lives))
                ));
                LOGGER.info("Sent {} lives to {} on join", lives, player.getName().getString());
            }
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
