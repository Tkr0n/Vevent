package com.verkku.hardcore;

import com.verkku.hardcore.network.WorldRegenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class WorldRegenManager {
    private final HardcoreMod mod;
    private boolean regenerating = false;

    public WorldRegenManager(HardcoreMod mod) {
        this.mod = mod;
    }

    public void triggerWorldRegeneration() {
        if (regenerating) {
            HardcoreMod.LOGGER.warn("World regeneration already in progress!");
            return;
        }

        regenerating = true;

        String warning = "§4§l¡ATENCIÓN! §c§lUn jugador ha perdido todas sus vidas. Regenerando mundo...";
        mod.getServer().getPlayerList().broadcastSystemMessage(Component.literal(warning), false);

        HardcoreMod.LOGGER.warn("WORLD REGENERATION TRIGGERED - All players will lose progress!");

        mod.getServer().execute(this::performWorldRegeneration);
    }

    private void performWorldRegeneration() {
        MinecraftServer server = mod.getServer();

        try {
            server.getPlayerList().saveAll();

            BlockPos spawnPos = new BlockPos(0, 100, 0);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                player.sendSystemMessage(Component.literal("§e§lRegenerando mundo... Por favor espera."));
            }

            long newSeed = new java.util.Random().nextLong();
            HardcoreMod.LOGGER.info("New world seed: {}", newSeed);

            Path worldPath = server.getServerDirectory().resolve("world");
            deleteDirectory(worldPath);

            String adminMessage = "§4§lMundo regenerado! Nuevo seed: §e§l" + newSeed + "\n§4§lReinicia el servidor para aplicar los cambios.";
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.literal(adminMessage));
            }

            mod.getLifeManager().resetAllLives();
            mod.getKoHandler().syncLivesToAllPlayers();

            WorldRegenPayload payload = new WorldRegenPayload(newSeed);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, payload);
            }

            HardcoreMod.LOGGER.info("World regeneration complete. New seed: {}", newSeed);

        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to regenerate world", e);
        } finally {
            regenerating = false;
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public boolean isRegenerating() {
        return regenerating;
    }
}
