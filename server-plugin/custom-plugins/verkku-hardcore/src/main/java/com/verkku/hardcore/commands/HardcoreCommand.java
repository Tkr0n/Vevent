package com.verkku.hardcore.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.verkku.hardcore.HardcoreMod;
import com.verkku.hardcore.LifeManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Map;
import java.util.UUID;

public class HardcoreCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hardcore")
            .then(Commands.literal("status")
                .executes(ctx -> showStatus(ctx.getSource()))
            )
            .then(Commands.literal("lives")
                .executes(ctx -> showAllLives(ctx.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> showPlayerLives(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                )
            )
            .then(Commands.literal("admin")
                .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("reset")
                    .executes(ctx -> resetAllLives(ctx.getSource()))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> resetPlayerLives(ctx.getSource(), StringArgumentType.getString(ctx, "player")))
                    )
                )
            )
        );
    }

    private static int showStatus(CommandSourceStack source) {
        LifeManager lifeManager = HardcoreMod.getInstance().getLifeManager();
        Map<UUID, LifeManager.PlayerLife> allLives = lifeManager.getAllLives();
        
        source.sendSuccess(() -> Component.literal("§6=== Hardcore Status ==="), false);
        source.sendSuccess(() -> Component.literal("§7Max lives: §c" + lifeManager.getMaxLives()), false);
        source.sendSuccess(() -> Component.literal("§7Players tracked: §a" + allLives.size()), false);
        
        for (Map.Entry<UUID, LifeManager.PlayerLife> entry : allLives.entrySet()) {
            LifeManager.PlayerLife life = entry.getValue();
            String hearts = getHeartsString(life.lives, lifeManager.getMaxLives());
            source.sendSuccess(() -> Component.literal("§7" + life.name + ": " + hearts + " §c(" + life.lives + "/" + lifeManager.getMaxLives() + ")"), false);
        }
        
        return 1;
    }

    private static int showAllLives(CommandSourceStack source) {
        LifeManager lifeManager = HardcoreMod.getInstance().getLifeManager();
        Map<UUID, LifeManager.PlayerLife> allLives = lifeManager.getAllLives();
        
        source.sendSuccess(() -> Component.literal("§6=== Player Lives ==="), false);
        
        for (Map.Entry<UUID, LifeManager.PlayerLife> entry : allLives.entrySet()) {
            LifeManager.PlayerLife life = entry.getValue();
            String hearts = getHeartsString(life.lives, lifeManager.getMaxLives());
            source.sendSuccess(() -> Component.literal("§7" + life.name + ": " + hearts + " §c(" + life.lives + "/" + lifeManager.getMaxLives() + ")"), false);
        }
        
        return 1;
    }

    private static int showPlayerLives(CommandSourceStack source, ServerPlayer target) {
        LifeManager lifeManager = HardcoreMod.getInstance().getLifeManager();
        int lives = lifeManager.getLives(target);
        String hearts = getHeartsString(lives, lifeManager.getMaxLives());
        
        source.sendSuccess(() -> Component.literal("§6" + target.getName().getString() + " lives: " + hearts + " §c(" + lives + "/" + lifeManager.getMaxLives() + ")"), false);
        
        return 1;
    }

    private static int resetAllLives(CommandSourceStack source) {
        LifeManager lifeManager = HardcoreMod.getInstance().getLifeManager();
        lifeManager.resetAllLives();
        
        HardcoreMod.getInstance().getKoHandler().syncLivesToAllPlayers();
        
        source.sendSuccess(() -> Component.literal("§aAll player lives reset to " + lifeManager.getMaxLives()), true);
        
        return 1;
    }

    private static int resetPlayerLives(CommandSourceStack source, String playerName) {
        LifeManager lifeManager = HardcoreMod.getInstance().getLifeManager();
        
        UUID targetUuid = null;
        for (Map.Entry<UUID, LifeManager.PlayerLife> entry : lifeManager.getAllLives().entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(playerName)) {
                targetUuid = entry.getKey();
                break;
            }
        }
        
        if (targetUuid == null) {
            source.sendFailure(Component.literal("§cPlayer not found: " + playerName));
            return 0;
        }
        
        lifeManager.resetPlayerLives(targetUuid, playerName);
        
        HardcoreMod.getInstance().getKoHandler().syncLivesToAllPlayers();
        
        source.sendSuccess(() -> Component.literal("§aReset lives for " + playerName + " to " + lifeManager.getMaxLives()), true);
        
        return 1;
    }

    private static String getHeartsString(int current, int max) {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (i < current) {
                hearts.append("§c❤");
            } else {
                hearts.append("§7❤");
            }
        }
        return hearts.toString();
    }
}
