package com.verkku.hardcore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        this.dataFile = server.getServerDirectory().resolve("player-lives.nbt");
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
            HardcoreMod.LOGGER.info("No player-lives.nbt found, starting fresh");
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            var playersOpt = root.getList("players");
            if (playersOpt.isEmpty()) {
                return;
            }

            ListTag playersList = playersOpt.get();
            for (int i = 0; i < playersList.size(); i++) {
                var entryOpt = playersList.getCompound(i);
                if (entryOpt.isEmpty()) continue;
                CompoundTag entry = entryOpt.get();

                var uuidOpt = entry.getString("uuid");
                var nameOpt = entry.getString("name");
                var livesOpt = entry.getInt("lives");
                if (uuidOpt.isPresent() && nameOpt.isPresent() && livesOpt.isPresent()) {
                    UUID uuid = UUID.fromString(uuidOpt.get());
                    playerLives.put(uuid, new PlayerLife(nameOpt.get(), livesOpt.get()));
                }
            }

            HardcoreMod.LOGGER.info("Loaded lives for {} players", playerLives.size());
        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to load player-lives.nbt", e);
        }
    }

    public void save() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag playersList = new ListTag();

            for (Map.Entry<UUID, PlayerLife> entry : playerLives.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putString("uuid", entry.getKey().toString());
                playerTag.putString("name", entry.getValue().name);
                playerTag.putInt("lives", entry.getValue().lives);
                playersList.add(playerTag);
            }

            root.put("players", playersList);
            NbtIo.writeCompressed(root, dataFile);
        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to save player-lives.nbt", e);
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
