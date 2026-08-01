package com.vevent.commands;
import com.vevent.VEventPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {
    private final VEventPlugin plugin;
    public CommandManager(VEventPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return true;
        String subCommand = args[0].toLowerCase();

        if (sender instanceof Player player && sender.hasPermission("vevent.player")) {
            if (subCommand.equals("accept")) plugin.getActiveEventManager().acceptPlayer(player);
            if (subCommand.equals("reject")) plugin.getActiveEventManager().rejectPlayer(player);
        }

        if (sender.hasPermission("vevent.admin")) {
            if (subCommand.equals("schedule")) {
                plugin.getSchedulerManager().generateSchedule(8, 3, 120);
                sender.sendMessage("§aCalendario generado.");
            }
            if (subCommand.equals("start")) {
                String tier = args.length >= 2 ? args[1].toLowerCase() : null;
                if (tier != null && !tier.equals("easy") && !tier.equals("medium") && !tier.equals("hardcore")) {
                    sender.sendMessage("§cTier inválido. Usa: easy, medium o hardcore.");
                    return true;
                }
                plugin.getSchedulerManager().startEventNow(tier);
                sender.sendMessage("§aSolicitando botín a la IA e iniciando evento...");
            }
            if (subCommand.equals("cancel")) {
                plugin.getActiveEventManager().cancelInvitation();
                sender.sendMessage("§cEvento cancelado.");
            }
            if (subCommand.equals("stop")) {
                plugin.getActiveEventManager().endEvent("Detenido por administrador");
                sender.sendMessage("§cEvento detenido.");
            }
            if (subCommand.equals("reload")) {
                plugin.reloadPluginConfig();
                sender.sendMessage("§aConfiguración recargada.");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("vevent.player")) {
                completions.add("accept");
                completions.add("reject");
            }
            if (sender.hasPermission("vevent.admin")) {
                completions.add("start");
                completions.add("cancel");
                completions.add("stop");
                completions.add("schedule");
                completions.add("reload");
            }
            String partial = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(partial));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("vevent.admin")) {
            completions.add("easy");
            completions.add("medium");
            completions.add("hardcore");
            String partial = args[1].toLowerCase();
            completions.removeIf(s -> !s.startsWith(partial));
        }
        return completions;
    }
}