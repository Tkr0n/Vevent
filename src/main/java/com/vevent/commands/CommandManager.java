package com.vevent.commands;
import com.vevent.VEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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
            if (subCommand.equals("miningmission") || subCommand.equals("craftingmission")) {
                String tier = args.length >= 2 ? args[1].toLowerCase() : null;
                if (tier == null || (!tier.equals("easy") && !tier.equals("medium") && !tier.equals("hardcore"))) {
                    sender.sendMessage("§cUso: /vevent " + subCommand + " <easy|medium|hardcore>");
                    return true;
                }
                String type = subCommand.equals("miningmission") ? "MINING" : "CRAFTING";
                plugin.getMissionManager().requestMission(type, tier);
                sender.sendMessage("§aSolicitando mision a la IA...");
                return true;
            }
            if (subCommand.equals("acceptmission")) {
                plugin.getMissionManager().acceptMission();
                return true;
            }
            if (subCommand.equals("rejectmission")) {
                plugin.getMissionManager().rejectMission();
                return true;
            }
            if (subCommand.equals("stopmission")) {
                plugin.getMissionManager().stopMission();
                return true;
            }
            if (subCommand.equals("debugpenalty")) {
                if (!(sender instanceof Player debugPlayer)) {
                    sender.sendMessage("§cEste comando solo lo puede usar un jugador.");
                    return true;
                }
                String tier = args.length >= 2 ? args[1].toLowerCase() : "medium";
                if (!tier.equals("easy") && !tier.equals("medium") && !tier.equals("hardcore")) {
                    sender.sendMessage("§cUso: /vevent debugpenalty <easy|medium|hardcore>");
                    return true;
                }
                plugin.getMissionManager().debugPenalty(debugPlayer, tier);
                return true;
            }
            if (subCommand.equals("schedule")) {
                plugin.getSchedulerManager().generateSchedule(8, 3, 120);
                sender.sendMessage("§aCalendario generado.");
            }
            if (subCommand.equals("start")) {
                if (plugin.getActiveEventManager().isAcceptingOrInProgress()) {
                    sender.sendMessage("§cYa hay un evento en curso. Usa /vevent cancel o /vevent stop primero.");
                    return true;
                }
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
                plugin.getActiveEventManager().forceCleanup("Detenido por administrador");
                sender.sendMessage("§cEvento detenido y limpiado.");
            }
            if (subCommand.equals("reload")) {
                plugin.reloadPluginConfig();
                sender.sendMessage("§aConfiguración recargada.");
            }
            if (subCommand.equals("givescroll")) {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado.");
                    return true;
                }
                ItemStack scroll = new ItemStack(Material.PAPER);
                ItemMeta meta = scroll.getItemMeta();
                meta.setDisplayName("§d§lPergamino de Retorno");
                meta.setLore(List.of("§7Click derecho para volver a tu cama", "§7Un solo uso"));
                NamespacedKey key = new NamespacedKey(plugin, "vevent_crafted_scroll");
                meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
                scroll.setItemMeta(meta);
                target.getInventory().addItem(scroll);
                sender.sendMessage("§aPergamino entregado a " + target.getName() + ".");
            }
            if (subCommand.equals("givetable")) {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado.");
                    return true;
                }
                ItemStack table = new ItemStack(Material.ENCHANTING_TABLE);
                ItemMeta meta = table.getItemMeta();
                meta.setDisplayName("§5§lMesa de Desvinculacion");
                meta.setLore(List.of("§7Colocala para desvincular encantamientos",
                        "§7Item encantado + libro + lapislazuli"));
                NamespacedKey key = new NamespacedKey(plugin, "vevent_disenchant_table");
                meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
                table.setItemMeta(meta);
                target.getInventory().addItem(table);
                sender.sendMessage("§aMesa de Desvinculacion entregada a " + target.getName() + ".");
            }
            if (subCommand.equals("givechest")) {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado.");
                    return true;
                }
                Material copperMat = Material.getMaterial("WAXED_COPPER_CHEST");
                if (copperMat == null) copperMat = Material.getMaterial("COPPER_CHEST");
                if (copperMat == null) copperMat = Material.CHEST;
                ItemStack chest = new ItemStack(copperMat);
                ItemMeta meta = chest.getItemMeta();
                meta.setDisplayName("§6§lCofre de Redstone");
                meta.setLore(List.of("§7Hub central de almacenamiento",
                        "§7Conecta cofres con comparadores + repetidores"));
                NamespacedKey key = new NamespacedKey(plugin, "vevent_redstone_chest");
                meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
                chest.setItemMeta(meta);
                target.getInventory().addItem(chest);
                sender.sendMessage("§aCofre de Redstone entregado a " + target.getName() + ".");
            }
            if (subCommand.equals("givecarpet")) {
                Player target = null;
                Material color = null;
                if (args.length == 1) {
                    if (!(sender instanceof Player self)) {
                        sender.sendMessage("§cEspecifica jugador. Uso: /vevent givecarpet [jugador] [color]");
                        return true;
                    }
                    target = self;
                    color = Material.WHITE_CARPET;
                } else if (args.length == 2) {
                    Material asColor = com.vevent.managers.TpCarpetManager.parseCarpetColor(args[1]);
                    if (asColor != null) {
                        if (!(sender instanceof Player self)) {
                            sender.sendMessage("§cEspecifica jugador. Uso: /vevent givecarpet [jugador] [color]");
                            return true;
                        }
                        target = self;
                        color = asColor;
                    } else {
                        target = Bukkit.getPlayer(args[1]);
                        color = Material.WHITE_CARPET;
                        if (target == null) {
                            sender.sendMessage("§cJugador no encontrado. Uso: /vevent givecarpet [jugador] [color]");
                            return true;
                        }
                    }
                } else {
                    target = Bukkit.getPlayer(args[1]);
                    color = com.vevent.managers.TpCarpetManager.parseCarpetColor(args[2]);
                    if (target == null) {
                        sender.sendMessage("§cJugador no encontrado.");
                        return true;
                    }
                    if (color == null) {
                        sender.sendMessage("§cColor inválido. Usa: blanco, rojo, azul, ...");
                        return true;
                    }
                }
                ItemStack carpet = plugin.getTpCarpetManager().createCarpetItem(color);
                carpet.setAmount(2);
                target.getInventory().addItem(carpet);
                sender.sendMessage("§aAlfombra TP [" + com.vevent.managers.TpCarpetManager.spanishColorName(color) + "] x2 entregada a " + target.getName() + ".");
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
                completions.add("givescroll");
                completions.add("givetable");
                completions.add("givechest");
                completions.add("givecarpet");
                completions.add("miningmission");
                completions.add("craftingmission");
                completions.add("acceptmission");
                completions.add("rejectmission");
                completions.add("stopmission");
                completions.add("debugpenalty");
            }
            String partial = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(partial));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("miningmission") || args[0].equalsIgnoreCase("craftingmission") || args[0].equalsIgnoreCase("debugpenalty")) && sender.hasPermission("vevent.admin")) {
            completions.add("easy");
            completions.add("medium");
            completions.add("hardcore");
            String partial = args[1].toLowerCase();
            completions.removeIf(s -> !s.startsWith(partial));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("givescroll") || args[0].equalsIgnoreCase("givetable") || args[0].equalsIgnoreCase("givechest")) && sender.hasPermission("vevent.admin")) {
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) completions.add(p.getName());
            }
            for (String c : com.vevent.managers.TpCarpetManager.colorSuggestions()) {
                if (c.startsWith(partial)) completions.add(c);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("givecarpet") && sender.hasPermission("vevent.admin")) {
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) completions.add(p.getName());
            }
            for (String c : com.vevent.managers.TpCarpetManager.colorSuggestions()) {
                if (c.startsWith(partial)) completions.add(c);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("givecarpet") && sender.hasPermission("vevent.admin")) {
            String partial = args[2].toLowerCase();
            for (String c : com.vevent.managers.TpCarpetManager.colorSuggestions()) {
                if (c.startsWith(partial)) completions.add(c);
            }
        }
        return completions;
    }
}