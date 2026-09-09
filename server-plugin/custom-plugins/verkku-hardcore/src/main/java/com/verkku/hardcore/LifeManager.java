package com.verkku.hardcore;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeManager {
    private final MinecraftServer server;
    private final Path dataFile;
    private final Map<UUID, PlayerLife> playerLives;
    private int maxLives;

    public LifeManager(MinecraftServer server) {
        this.server = server;
        this.dataFile = server.getServerDirectory().resolve("player-lives.yml");
        this.playerLives = new HashMap<>();
        this.maxLives = 3;
        load();
    }

    public int getMaxLives() {
        return maxLives;
    }

    public void setMaxLives(int maxLives) {
        this.maxLives = maxLives;
    }

    public int getLives(UUID playerUuid) {
        PlayerLife life = playerLives.get(playerUuid);
        return life != null ? life.lives : maxLives;
    }

    public int getLives(ServerPlayer player) {
        return getLives(player.getUUID());
    }

    public void setLives(UUID playerUuid, String playerName, int lives) {
        playerLives.put(playerUuid, new PlayerLife(playerName, Math.max(0, Math.min(lives, maxLives))));
        save();
    }

    public void setLives(ServerPlayer player, int lives) {
        setLives(player.getUUID(), player.getName().getString(), lives);
    }

    public boolean decrementLife(ServerPlayer player) {
        int current = getLives(player);
        if (current <= 0) {
            return false;
        }
        setLives(player, current - 1);
        return true;
    }

    public boolean hasLivesRemaining(UUID playerUuid) {
        return getLives(playerUuid) > 0;
    }

    public boolean hasLivesRemaining(ServerPlayer player) {
        return hasLivesRemaining(player.getUUID());
    }

    public void resetAllLives() {
        for (Map.Entry<UUID, PlayerLife> entry : playerLives.entrySet()) {
            entry.getValue().lives = maxLives;
        }
        save();
    }

    public void resetPlayerLives(UUID playerUuid, String playerName) {
        setLives(playerUuid, playerName, maxLives);
    }

    public Map<UUID, PlayerLife> getAllLives() {
        return new HashMap<>(playerLives);
    }

    public void load() {
        if (!Files.exists(dataFile)) {
            HardcoreMod.LOGGER.info("No player-lives.yml found, starting fresh");
            return;
        }

        try {
            Yaml yaml = new Yaml();
            try (InputStream inputStream = Files.newInputStream(dataFile)) {
                Map<String, Object> data = yaml.load(inputStream);

                if (data == null || !data.containsKey("players")) {
                    return;
                }

                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> players = (Map<String, Map<String, Object>>) data.get("players");
                
                for (Map.Entry<String, Map<String, Object>> entry : players.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Map<String, Object> playerData = entry.getValue();
                    String name = (String) playerData.get("name");
                    int lives = (int) playerData.get("lives");
                    playerLives.put(uuid, new PlayerLife(name, lives));
                }

                HardcoreMod.LOGGER.info("Loaded lives for {} players", playerLives.size());
            }
        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to load player-lives.yml", e);
        }
    }

    public void save() {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data = new HashMap<>();
            Map<String, Map<String, Object>> players = new HashMap<>();

            for (Map.Entry<UUID, PlayerLife> entry : playerLives.entrySet()) {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("name", entry.getValue().name);
                playerData.put("lives", entry.getValue().lives);
                players.put(entry.getKey().toString(), playerData);
            }

            data.put("players", players);

            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(dataFile))) {
                yaml.dump(data, writer);
            }
        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to save player-lives.yml", e);
        }
    }

    public static class PlayerLife {
        public final String name;
        public int lives;

        public PlayerLife(String name, int lives) {
            this.name = name;
            this.lives = lives;
        }
    }
}
