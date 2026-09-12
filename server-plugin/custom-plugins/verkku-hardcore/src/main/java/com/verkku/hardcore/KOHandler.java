package com.verkku.hardcore;

import com.verkku.hardcore.network.KoStatePayload;
import com.verkku.hardcore.network.LivesSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KOHandler {
    private final HardcoreMod mod;
    private final Map<UUID, Long> koTimestamps;
    private final Map<UUID, Boolean> koStates;

    public KOHandler(HardcoreMod mod) {
        this.mod = mod;
        this.koTimestamps = new ConcurrentHashMap<>();
        this.koStates = new ConcurrentHashMap<>();

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    public void startKO(ServerPlayer player) {
        UUID uuid = player.getUUID();
        koTimestamps.put(uuid, System.currentTimeMillis());
        koStates.put(uuid, true);

        String message = "§c§l" + player.getName().getString() + " está KO - revívelo";
        mod.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);

        sendKoState(player, true);

        HardcoreMod.LOGGER.info("Player {} is now in KO state", player.getName().getString());
    }

    public boolean isKO(ServerPlayer player) {
        return koStates.getOrDefault(player.getUUID(), false);
    }

    public boolean isKO(UUID playerUuid) {
        return koStates.getOrDefault(playerUuid, false);
    }

    public void revivePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        koTimestamps.remove(uuid);
        koStates.put(uuid, false);

        player.removeAllEffects();
        player.setHealth(20.0f);

        sendKoState(player, false);

        String message = "§a§l" + player.getName().getString() + " ha sido revivido!";
        mod.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);

        HardcoreMod.LOGGER.info("Player {} has been revived", player.getName().getString());
    }

    private void onServerTick(MinecraftServer server) {
        long currentTime = System.currentTimeMillis();
        long timeoutMs = 45 * 1000L;

        for (Map.Entry<UUID, Long> entry : koTimestamps.entrySet()) {
            UUID uuid = entry.getKey();
            long koTime = entry.getValue();

            if (currentTime - koTime >= timeoutMs) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    handleKoTimeout(player);
                }
                koTimestamps.remove(uuid);
                koStates.put(uuid, false);
            }
        }
    }

    private void handleKoTimeout(ServerPlayer player) {
        HardcoreMod.LOGGER.info("KO timeout for {}, decrementing life", player.getName().getString());

        boolean hasLives = mod.getLifeManager().decrementLife(player);
        syncLivesToAllPlayers();

        if (!hasLives || mod.getLifeManager().getLives(player) <= 0) {
            mod.getWorldRegenManager().triggerWorldRegeneration();
        } else {
            respawnPlayer(player);
        }
    }

    private void respawnPlayer(ServerPlayer player) {
        player.removeAllEffects();
        player.setHealth(20.0f);

        if (player.getRespawnConfig() != null && player.getRespawnConfig().respawnData() != null) {
            var respawnData = player.getRespawnConfig().respawnData();
            player.teleportTo(
                respawnData.pos().getX(),
                respawnData.pos().getY(),
                respawnData.pos().getZ()
            );
        } else {
            var spawnPos = mod.getServer().overworld().getRespawnData().pos();
            player.teleportTo(
                spawnPos.getX(),
                spawnPos.getY(),
                spawnPos.getZ()
            );
        }

        String message = "§e§l" + player.getName().getString() + " respawning... §c§l(" + mod.getLifeManager().getLives(player) + " vidas restantes)";
        mod.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    private void sendKoState(ServerPlayer player, boolean isKo) {
        KoStatePayload payload = new KoStatePayload(player.getUUID(), isKo);
        for (ServerPlayer onlinePlayer : mod.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(onlinePlayer, payload);
        }
    }

    public void syncLivesToAllPlayers() {
        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            syncLivesToPlayer(player);
        }
    }

    public void syncLivesToPlayer(ServerPlayer player) {
        Map<UUID, LifeManager.PlayerLife> allLives = mod.getLifeManager().getAllLives();
        List<LivesSyncPayload.PlayerLivesData> dataList = new ArrayList<>();

        for (Map.Entry<UUID, LifeManager.PlayerLife> entry : allLives.entrySet()) {
            dataList.add(new LivesSyncPayload.PlayerLivesData(
                entry.getKey(),
                entry.getValue().name,
                entry.getValue().lives
            ));
        }

        ServerPlayNetworking.send(player, new LivesSyncPayload(dataList));
    }

    /**
     * Clear KO state for a specific player.
     * Used when clearing downed state after world regeneration.
     */
    public void clearKoState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        koTimestamps.remove(uuid);
        koStates.put(uuid, false);
        sendKoState(player, false);
        HardcoreMod.LOGGER.info("Cleared KO state for {}", player.getName().getString());
    }

    /**
     * Clear KO state for all online players.
     * Used when clearing downed state after world regeneration.
     */
    public void clearAllKoStates() {
        for (ServerPlayer player : mod.getServer().getPlayerList().getPlayers()) {
            clearKoState(player);
        }
    }
}
