package com.verkku.hardcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeartHudRenderer {
    private static final Identifier HEART_FULL = Identifier.fromNamespaceAndPath(HardcoreHudMod.MOD_ID, "textures/heart_full.png");
    private static final Identifier HEART_EMPTY = Identifier.fromNamespaceAndPath(HardcoreHudMod.MOD_ID, "textures/heart_empty.png");
    private static final Identifier HEART_KO = Identifier.fromNamespaceAndPath(HardcoreHudMod.MOD_ID, "textures/heart_ko.png");

    private static final int HEART_SIZE = 12;
    private static final int HEART_SPACING = 2;
    private static final int MAX_LIVES = 3;

    private final Map<UUID, Integer> playerLives;
    private final Map<UUID, Boolean> koStates;
    private int localPlayerLives = 3;
    private boolean localPlayerKo = false;

    private long lastBlinkTime = 0;
    private boolean blinkState = false;

    public HeartHudRenderer() {
        this.playerLives = new ConcurrentHashMap<>();
        this.koStates = new ConcurrentHashMap<>();
    }

    public void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.gui.hud.isHidden()) {
            return;
        }

        if (client.player.isSpectator()) {
            return;
        }

        UUID myUuid = client.player.getUUID();
        Integer storedLives = playerLives.get(myUuid);
        if (storedLives != null) {
            localPlayerLives = storedLives;
        }
        Boolean storedKo = koStates.get(myUuid);
        if (storedKo != null) {
            localPlayerKo = storedKo;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime > 500) {
            blinkState = !blinkState;
            lastBlinkTime = currentTime;
        }

        int x = 10;
        int y = 35;

        context.text(
            client.font,
            "§6§lVIDAS:",
            x,
            y,
            0xFFFFFF
        );
        y += 12;

        for (int i = 0; i < MAX_LIVES; i++) {
            Identifier texture;

            if (localPlayerKo && blinkState) {
                texture = HEART_KO;
            } else if (i < localPlayerLives) {
                texture = HEART_FULL;
            } else {
                texture = HEART_EMPTY;
            }

            context.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + (i * (HEART_SIZE + HEART_SPACING)),
                y,
                0.0f,
                0.0f,
                HEART_SIZE,
                HEART_SIZE,
                HEART_SIZE,
                HEART_SIZE
            );
        }

        y += HEART_SIZE + 4;
        String livesText = "§c" + localPlayerLives + "§7/" + MAX_LIVES;
        context.text(
            client.font,
            livesText,
            x,
            y,
            0xFFFFFF
        );
    }

    public void updateLives(UUID playerUuid, int lives) {
        playerLives.put(playerUuid, lives);

        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getUUID().equals(playerUuid)) {
            localPlayerLives = lives;
        }
    }

    public void updateKoState(UUID playerUuid, boolean isKo) {
        koStates.put(playerUuid, isKo);

        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getUUID().equals(playerUuid)) {
            localPlayerKo = isKo;
        }
    }

    public int getLocalPlayerLives() {
        return localPlayerLives;
    }

    public boolean isLocalPlayerKo() {
        return localPlayerKo;
    }

    public Map<UUID, Integer> getAllLives() {
        return new ConcurrentHashMap<>(playerLives);
    }
}
