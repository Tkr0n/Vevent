package com.verkku.hardcore;

import com.verkku.hardcore.network.WorldRegenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class WorldRegenManager {
    private final HardcoreMod mod;
    private static final String FLAG_FILE = "regenerate_world.flag";
    private boolean regenerating = false;

    public WorldRegenManager(HardcoreMod mod) {
        this.mod = mod;
    }

    /**
     * Called during onInitialize() — BEFORE the world loads.
     * Checks for a regeneration flag and deletes the world directory if found.
     */
    public static void checkAndDeleteWorldOnStartup() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path flagPath = gameDir.resolve(FLAG_FILE);

            if (!Files.exists(flagPath)) {
                return;
            }

            HardcoreMod.LOGGER.warn("Found world regeneration flag! Deleting world directory...");

            // Read the seed from the flag file
            String seedStr = Files.readString(flagPath).trim();
            HardcoreMod.LOGGER.info("Flag file contains seed: {}", seedStr);

            // Delete the world directory
            Path worldPath = gameDir.resolve("world");
            if (Files.exists(worldPath)) {
                deleteRecursively(worldPath);
                HardcoreMod.LOGGER.info("World directory deleted successfully");
            }

            // Also delete level.dat if it exists outside world/
            Path levelDat = gameDir.resolve("level.dat");
            if (Files.exists(levelDat)) {
                Files.deleteIfExists(levelDat);
                HardcoreMod.LOGGER.info("level.dat deleted");
            }

            // Delete the flag file
            Files.deleteIfExists(flagPath);
            HardcoreMod.LOGGER.info("Regeneration flag removed. New world will be created on next start.");

        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to process world regeneration flag", e);
        }
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
            // 1. Save all player data
            server.getPlayerList().saveAll();
            HardcoreMod.LOGGER.info("Player data saved");

            // 2. Save lives to player-lives.nbt (already persists outside world/)
            mod.getLifeManager().save();
            HardcoreMod.LOGGER.info("Lives data saved");

            // 3. Clear downed and KO state for ALL players before disconnecting
            clearDownedStateForAllPlayers(server);
            mod.getKoHandler().clearAllKoStates();
            HardcoreMod.LOGGER.info("Cleared downed and KO state for all players");

            // 4. Generate new seed
            long newSeed = new java.util.Random().nextLong();
            HardcoreMod.LOGGER.info("New world seed: {}", newSeed);

            // 5. Write flag file — world deletion happens on NEXT startup
            Path gameDir = FabricLoader.getInstance().getGameDir();
            Path flagPath = gameDir.resolve(FLAG_FILE);
            Files.writeString(flagPath, String.valueOf(newSeed));
            HardcoreMod.LOGGER.info("Regeneration flag written to: {}", flagPath);

            // 6. Reset lives for all players
            mod.getLifeManager().resetAllLives();
            HardcoreMod.LOGGER.info("All lives reset");

            // 7. Send regen notification to all clients
            ArrayList<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
            WorldRegenPayload payload = new WorldRegenPayload(newSeed);
            for (ServerPlayer player : players) {
                ServerPlayNetworking.send(player, payload);
            }

            // 8. Disconnect all players
            for (ServerPlayer player : players) {
                player.connection.disconnect(Component.literal("§4§lMundo regenerado. Reconecta en unos segundos..."));
            }

            // 9. Halt immediately — no Thread.sleep, no shutdown hooks
            // Docker will restart the container, and on startup the flag file
            // will trigger world deletion BEFORE the world loads.
            HardcoreMod.LOGGER.info("Halting server for world regeneration...");
            Runtime.getRuntime().halt(0);

        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to regenerate world", e);
        } finally {
            regenerating = false;
        }
    }

    /**
     * Clear downed state for all online players.
     * This ensures no player reconnects in a downed state after world regeneration.
     */
    private void clearDownedStateForAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                // Clear Simplerevive downed tags
                player.removeTag(net.minecraft.resources.ResourceLocation.parse("simplerevive:downed"));
                player.removeTag(net.minecraft.resources.ResourceLocation.parse("simplerevive:downed.initiated"));

                // Also clear via commands as backup
                var dispatcher = server.getCommands().getDispatcher();
                var source = server.createCommandSourceStack()
                    .withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
                dispatcher.execute(
                    "tag " + player.getName().getString() + " remove simplerevive.downed",
                    source
                );
                dispatcher.execute(
                    "tag " + player.getName().getString() + " remove simplerevive.downed.initiated",
                    source
                );

                HardcoreMod.LOGGER.info("Cleared downed state for {}", player.getName().getString());
            } catch (Exception e) {
                HardcoreMod.LOGGER.warn("Failed to clear downed state for {}: {}", player.getName().getString(), e.getMessage());
            }
        }
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                // Skip datapacks directory — we need to keep our override datapacks
                if (path.getFileName().toString().equals("datapacks")) {
                    HardcoreMod.LOGGER.info("Skipping datapacks directory: {}", path);
                    return;
                }
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (Exception e) {
            HardcoreMod.LOGGER.error("Failed to delete: {}", path, e);
        }
    }

    public boolean isRegenerating() {
        return regenerating;
    }
}
