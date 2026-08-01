package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public class ActiveEventManager {
    private final VEventPlugin plugin;
    private final Random random;
    private final NamespacedKey mobKey;
    private final NamespacedKey scrollKey;
    private boolean isAcceptingPlayers = false;
    private boolean eventInProgress = false;
    private Location eventLocation;
    private LootProfile activeProfile;
    private final List<Player> participants = new ArrayList<>();
    private final List<Block> activeChests = new ArrayList<>();
    private final List<Entity> activeMobs = new ArrayList<>();
    private BukkitTask eventLoopTask;
    private int secondsElapsed = 0;

    public ActiveEventManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.mobKey = new NamespacedKey(plugin, "vevent_mob");
        this.scrollKey = new NamespacedKey(plugin, "vevent_scroll_time");
    }

    public void openInvitations(LootProfile profile) {
        this.activeProfile = profile;
        this.participants.clear();
        this.isAcceptingPlayers = true;
        this.eventLocation = findSafeLocation(Bukkit.getWorlds().get(0));
        
        int timeout = plugin.getConfig().getInt("event-rules.invite-timeout-seconds", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isAcceptingPlayers = false;
            if (participants.isEmpty()) {
                Bukkit.broadcastMessage("§cNadie aceptó la invitación.");
                return;
            }
            startCombatPhase();
        }, timeout * 20L);
    }

    public boolean acceptPlayer(Player player) {
        if (!isAcceptingPlayers) return false;
        if (player.getBedSpawnLocation() == null) {
            player.sendMessage("§cDebes dormir en una cama primero.");
            return false;
        }
        if (!participants.contains(player)) {
            participants.add(player);
            player.sendMessage("§a¡Registrado para el evento!");
        }
        return true;
    }

    private void startCombatPhase() {
        this.eventInProgress = true;
        this.secondsElapsed = 0;
        for (Player p : participants) p.teleport(eventLocation.clone().add(0, 2, 0));
        spawnEventMobs();
        spawnEventChests();
        eventLoopTask = new BukkitRunnable() {
            @Override public void run() { checkEndConditions(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private Location findSafeLocation(World world) {
        return world.getSpawnLocation(); // Simplificado para compilar
    }

    private void spawnEventMobs() { }
    private void spawnEventChests() { }

    private void checkEndConditions() {
        secondsElapsed++;
        int maxMinutes = plugin.getConfig().getInt("event-rules.duration-minutes", 15);
        activeMobs.removeIf(Entity::isDead);
        if (secondsElapsed >= (maxMinutes * 60) || activeChests.isEmpty() || activeMobs.isEmpty()) {
            endEvent("Objetivos completados o tiempo agotado");
        }
    }

    public void endEvent(String reason) {
        if (!eventInProgress) return;
        eventInProgress = false;
        if (eventLoopTask != null) eventLoopTask.cancel();
        Bukkit.broadcastMessage("§aEvento concluido: " + reason);
        for (Player player : participants) if (player.isOnline()) giveReturnScroll(player);
        for (Entity mob : activeMobs) if (!mob.isDead()) mob.remove();
        for (Block chest : activeChests) chest.setType(Material.AIR);
        activeMobs.clear(); activeChests.clear(); participants.clear();
    }

    private void giveReturnScroll(Player player) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();
        meta.setDisplayName("§6§lPergamino de Retorno");
        meta.getPersistentDataContainer().set(scrollKey, PersistentDataType.LONG, System.currentTimeMillis());
        scroll.setItemMeta(meta);
        player.getInventory().addItem(scroll);
    }
    
    public boolean isEventInProgress() { return eventInProgress; }
    public List<Block> getActiveChests() { return activeChests; }
    public NamespacedKey getScrollKey() { return scrollKey; }
}