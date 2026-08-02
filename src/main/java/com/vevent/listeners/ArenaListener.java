package com.vevent.listeners;
import com.vevent.VEventPlugin;
import com.vevent.managers.ActiveEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ArenaListener implements Listener {
    private final VEventPlugin plugin;
    public ArenaListener(VEventPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(new NamespacedKey(plugin, "return_scroll_craft"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ActiveEventManager mgr = plugin.getActiveEventManager();

        if (mgr.getActiveChests().contains(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c¡No puedes romper los cofres del evento!");
            return;
        }
        if (mgr.getBeaconBlocks().contains(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c¡No puedes romper el beacon del evento!");
        }
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        ActiveEventManager mgr = plugin.getActiveEventManager();
        if (!mgr.getActiveChests().contains(block)) return;

        if (!mgr.areAllMobsDead()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c¡Los cofres están sellados! Derrota a todos los mobs primero.");
        }
    }

    @EventHandler
    public void onScrollUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;

        var meta = item.getItemMeta();
        if (meta == null) return;

        ActiveEventManager mgr = plugin.getActiveEventManager();

        var craftedKey = mgr.getCraftedScrollKey();
        if (meta.getPersistentDataContainer().has(craftedKey, PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
            Location bedLoc = event.getPlayer().getBedSpawnLocation();
            if (bedLoc == null) {
                event.getPlayer().sendMessage("§cNo tienes una cama asignada. Duerme en una primero.");
                return;
            }
            event.getPlayer().teleport(bedLoc);
            event.getPlayer().sendMessage("§a¡Has vuelto a tu cama!");
            consumeOneItem(event.getPlayer(), item);
            return;
        }

        var scrollKey = mgr.getScrollKey();
        if (!meta.getPersistentDataContainer().has(scrollKey, PersistentDataType.LONG)) return;

        var uuidKey = mgr.getUuidKey();
        String ownerUuid = meta.getPersistentDataContainer().get(uuidKey, PersistentDataType.STRING);
        if (ownerUuid == null || !ownerUuid.equals(event.getPlayer().getUniqueId().toString())) {
            event.getPlayer().sendMessage("§cEste pergamino pertenece a otro jugador.");
            return;
        }

        Long timestamp = meta.getPersistentDataContainer().get(scrollKey, PersistentDataType.LONG);
        if (timestamp == null) return;

        int expirationMinutes = plugin.getConfig().getInt("return-scroll.expiration-minutes", 5);
        long expirationTime = timestamp + (expirationMinutes * 60 * 1000L);
        if (System.currentTimeMillis() > expirationTime) {
            event.getPlayer().sendMessage("§cEl pergamino ha expirado.");
            consumeOneItem(event.getPlayer(), item);
            return;
        }

        var locationKey = mgr.getLocationKey();
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
            consumeOneItem(event.getPlayer(), item);
        } catch (NumberFormatException ignored) {}
    }

    private void consumeOneItem(org.bukkit.entity.Player player, ItemStack item) {
        int newAmount = item.getAmount() - 1;
        item.setAmount(0);
        if (newAmount > 0) {
            ItemStack remainder = item.clone();
            remainder.setAmount(newAmount);
            player.getInventory().setItemInMainHand(remainder);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}