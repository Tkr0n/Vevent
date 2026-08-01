package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;
import org.bukkit.*;
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
    private BukkitTask beamTask;
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
        for (Player p : participants) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dx = Math.cos(angle) * 50;
            double dz = Math.sin(angle) * 50;
            Location tpLoc = eventLocation.clone().add(dx, 0, dz);
            tpLoc.setY(tpLoc.getWorld().getHighestBlockYAt(tpLoc) + 1);
            p.teleport(tpLoc);
            p.sendMessage("§eEl epicentro está a 50 bloques. ¡Busca el haz de luz!");
        }
        startBeam();
        if (activeProfile != null) {
            spawnEventMobs();
            spawnEventChests();
        }
        eventLoopTask = new BukkitRunnable() {
            @Override public void run() { checkEndConditions(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private Location findSafeLocation(World world) {
        Location center = world.getSpawnLocation();
        for (int attempt = 0; attempt < 50; attempt++) {
            int x = center.getBlockX() + random.nextInt(3000) - 1500;
            int z = center.getBlockZ() + random.nextInt(3000) - 1500;
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (loc.getBlock().getType() != Material.AIR) continue;
            if (!loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            if (isTooCloseToAnyBed(loc)) continue;
            return loc;
        }
        return center;
    }

    private boolean isTooCloseToAnyBed(Location loc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location bed = player.getBedSpawnLocation();
            if (bed != null && bed.getWorld() != null && bed.getWorld().equals(loc.getWorld())) {
                if (loc.distanceSquared(bed) < 1000 * 1000) return true;
            }
        }
        return false;
    }

    private void spawnEventMobs() {
        if (activeProfile == null || activeProfile.getMobs() == null) return;
        World world = eventLocation.getWorld();
        for (Map.Entry<String, Integer> entry : activeProfile.getMobs().entrySet()) {
            String mobTypeName = entry.getKey().toUpperCase();
            int count = Math.min(entry.getValue(), 50);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(mobTypeName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Evento] Tipo de mob desconocido: " + mobTypeName);
                continue;
            }
            for (int i = 0; i < count; i++) {
                Location spawnLoc = eventLocation.clone().add(
                        random.nextInt(20) - 10, 1, random.nextInt(20) - 10);
                Entity entity = world.spawnEntity(spawnLoc, entityType);
                entity.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                activeMobs.add(entity);
            }
        }
        Bukkit.broadcastMessage("§c¡" + activeMobs.size() + " criaturas han aparecido!");
    }

    private void spawnEventChests() {
        if (activeProfile == null) return;
        World world = eventLocation.getWorld();
        int chestCount = Math.min(participants.size() * 2, 10);
        for (int i = 0; i < chestCount; i++) {
            Location chestLoc = eventLocation.clone().add(
                    random.nextInt(16) - 8, 0, random.nextInt(16) - 8);
            chestLoc.setY(world.getHighestBlockYAt(chestLoc) + 1);
            if (chestLoc.getBlock().getType() != Material.AIR) continue;
            chestLoc.getBlock().setType(Material.CHEST);
            activeChests.add(chestLoc.getBlock());
            if (chestLoc.getBlock().getState() instanceof Chest chest) {
                fillChestWithLoot(chest);
            }
        }
        Bukkit.broadcastMessage("§6¡" + chestCount + " cofres han aparecido!");
    }

    private void fillChestWithLoot(Chest chest) {
        if (activeProfile == null) return;
        List<ItemStack> loot = new ArrayList<>();
        if (activeProfile.getRareItems() != null) {
            for (String itemName : activeProfile.getRareItems()) {
                Material mat = Material.matchMaterial(itemName.toUpperCase());
                if (mat != null) loot.add(new ItemStack(mat, 1));
            }
        }
        if (activeProfile.getFarmingItems() != null) {
            for (String itemName : activeProfile.getFarmingItems()) {
                Material mat = Material.matchMaterial(itemName.toUpperCase());
                if (mat != null) loot.add(new ItemStack(mat, random.nextInt(16) + 1));
            }
        }
        if (activeProfile.getEnchantments() != null) {
            for (String enchString : activeProfile.getEnchantments()) {
                String[] parts = enchString.split("-");
                Material mat = Material.DIAMOND_SWORD;
                if (parts.length >= 1) {
                    Material maybeMat = Material.matchMaterial(parts[0].toUpperCase());
                    if (maybeMat != null && maybeMat != Material.AIR) mat = maybeMat;
                }
                ItemStack item = new ItemStack(mat);
                if (parts.length >= 2) {
                    try {
                        int level = Integer.parseInt(parts[parts.length - 1]);
                        String enchName = enchString.substring(0, enchString.lastIndexOf("-")).toLowerCase();
                        for (org.bukkit.enchantments.Enchantment ench : org.bukkit.enchantments.Enchantment.values()) {
                            if (ench.getKey().getKey().equalsIgnoreCase(enchName)) {
                                item.addUnsafeEnchantment(ench, level);
                                break;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
                loot.add(item);
            }
        }
        Collections.shuffle(loot);
        for (ItemStack item : loot) {
            int slot = random.nextInt(chest.getInventory().getSize());
            chest.getInventory().setItem(slot, item);
        }
    }

    private void checkEndConditions() {
        secondsElapsed++;
        int maxMinutes = plugin.getConfig().getInt("event-rules.duration-minutes", 15);
        activeMobs.removeIf(Entity::isDead);

        boolean timedOut = secondsElapsed >= (maxMinutes * 60);
        boolean objectivesComplete = secondsElapsed > 5 && (activeChests.isEmpty() && activeMobs.isEmpty());

        if (timedOut) {
            endEvent("Tiempo agotado");
        } else if (objectivesComplete) {
            endEvent("Todos los objetivos completados");
        }
    }

    public void endEvent(String reason) {
        if (!eventInProgress) return;
        eventInProgress = false;
        if (eventLoopTask != null) eventLoopTask.cancel();
        stopBeam();
        Bukkit.broadcastMessage("§aEvento concluido: " + reason);
        for (Player player : participants) if (player.isOnline()) giveReturnScroll(player);
        for (Entity mob : activeMobs) if (!mob.isDead()) mob.remove();
        for (Block chest : activeChests) chest.setType(Material.AIR);
        activeMobs.clear(); activeChests.clear(); participants.clear();
    }

    private void startBeam() {
        stopBeam();
        beamTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventInProgress || eventLocation == null) { cancel(); return; }
                World world = eventLocation.getWorld();
                Location base = eventLocation.clone();
                base.setY(eventLocation.getBlockY());
                for (int y = 0; y < 120; y += 3) {
                    world.spawnParticle(Particle.END_ROD, base.getX(), base.getY() + y, base.getZ(),
                            1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void stopBeam() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }
    }

    private void giveReturnScroll(Player player) {
        Location bedLoc = player.getBedSpawnLocation();
        if (bedLoc == null) return;

        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();
        meta.setDisplayName("§6§lPergamino de Retorno");
        meta.setLore(List.of(
                "§7Click derecho para volver a tu cama",
                "§7Expira en 5 minutos"
        ));
        NamespacedKey locationKey = new NamespacedKey(plugin, "vevent_scroll_loc");
        String locString = bedLoc.getWorld().getName() + ";" + bedLoc.getBlockX() + ";" + bedLoc.getBlockY() + ";" + bedLoc.getBlockZ();
        meta.getPersistentDataContainer().set(scrollKey, PersistentDataType.LONG, System.currentTimeMillis());
        meta.getPersistentDataContainer().set(locationKey, PersistentDataType.STRING, locString);
        scroll.setItemMeta(meta);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), scroll);
            player.sendMessage("§cTu inventario estaba lleno. ¡El pergamino cayó a tus pies!");
        } else {
            player.getInventory().addItem(scroll);
        }
    }
    
    public boolean isEventInProgress() { return eventInProgress; }
    public List<Block> getActiveChests() { return activeChests; }
    public NamespacedKey getScrollKey() { return scrollKey; }
}