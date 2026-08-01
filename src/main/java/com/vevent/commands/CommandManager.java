package com.vevent.commands;
import com.vevent.VEventPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandManager implements CommandExecutor {
    private final VEventPlugin plugin;
    public CommandManager(VEventPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return true;
        String subCommand = args[0].toLowerCase();

        if (sender instanceof Player player && sender.hasPermission("vevent.player")) {
            if (subCommand.equals("accept")) plugin.getActiveEventManager().acceptPlayer(player);
        }

        if (sender.hasPermission("vevent.admin")) {
            if (subCommand.equals("schedule")) {
                plugin.getSchedulerManager().generateSchedule(8, 3, 120);
                sender.sendMessage("§aCalendario generado.");
            }
        }
        return true;
    }
}