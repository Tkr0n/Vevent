package com.vevent.listeners;
import com.vevent.VEventPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

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
}