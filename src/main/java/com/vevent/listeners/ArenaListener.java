package com.vevent.listeners;
import com.vevent.VEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ArenaListener implements Listener {
    private final VEventPlugin plugin;
    public ArenaListener(VEventPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.CHEST) {
            if (plugin.getActiveEventManager().getActiveChests().contains(event.getBlock())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c¡No puedes romper los cofres!");
            }
        }
    }

    @EventHandler
    public void onScrollUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;

        var meta = item.getItemMeta();
        if (meta == null) return;

        var scrollKey = plugin.getActiveEventManager().getScrollKey();
        if (!meta.getPersistentDataContainer().has(scrollKey, PersistentDataType.LONG)) return;

        Long timestamp = meta.getPersistentDataContainer().get(scrollKey, PersistentDataType.LONG);
        if (timestamp == null) return;

        int expirationMinutes = plugin.getConfig().getInt("return-scroll.expiration-minutes", 5);
        long expirationTime = timestamp + (expirationMinutes * 60 * 1000L);
        if (System.currentTimeMillis() > expirationTime) {
            event.getPlayer().sendMessage("§cEl pergamino ha expirado.");
            item.setAmount(item.getAmount() - 1);
            return;
        }

        NamespacedKey locationKey = new NamespacedKey(plugin, "vevent_scroll_loc");
        String locString = meta.getPersistentDataContainer().get(locationKey, PersistentDataType.STRING);
        if (locString == null) return;

        String[] parts = locString.split(";");
        if (parts.length != 4) return;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return;

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            Location bedLoc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
            event.getPlayer().teleport(bedLoc);
            event.getPlayer().sendMessage("§a¡Has vuelto a tu cama!");
            item.setAmount(item.getAmount() - 1);
        } catch (NumberFormatException ignored) {}
    }
}