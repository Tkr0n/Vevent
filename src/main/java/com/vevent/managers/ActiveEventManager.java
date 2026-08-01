package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
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
    private String eventTier;
    private final List<Player> participants = new ArrayList<>();
    private final List<Block> activeChests = new ArrayList<>();
    private final List<Entity> activeMobs = new ArrayList<>();
    private final List<Block> activeSpawners = new ArrayList<>();
    private BukkitTask eventLoopTask;
    private BukkitTask beamTask;
    private int secondsElapsed = 0;

    public ActiveEventManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.mobKey = new NamespacedKey(plugin, "vevent_mob");
        this.scrollKey = new NamespacedKey(plugin, "vevent_scroll_time");
    }

    public void openInvitations(LootProfile profile, String tier) {
        this.activeProfile = profile;
        this.eventTier = tier;
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
        if (!isAcceptingPlayers) {
            player.sendMessage("§cNo hay inscripciones abiertas en este momento.");
            return false;
        }
        if (player.getBedSpawnLocation() == null) {
            player.sendMessage("§cDebes dormir en una cama primero.");
            return false;
        }
        if (!participants.contains(player)) {
            participants.add(player);
            player.sendMessage("§a¡Registrado para el evento " + getTierDisplay() + "§a!");
        }
        return true;
    }

    public void rejectPlayer(Player player) {
        if (!isAcceptingPlayers) {
            player.sendMessage("§cNo hay inscripciones abiertas en este momento.");
            return;
        }
        if (participants.remove(player)) {
            player.sendMessage("§cTe has desinscrito del evento. Puedes volver con /vevent accept.");
        } else {
            player.sendMessage("§cNo estás inscrito en ningún evento.");
        }
    }

    private String getTierDisplay() {
        if (eventTier == null) return "";
        switch (eventTier) {
            case "easy": return "§a[Fácil]";
            case "medium": return "§e[Medio]";
            case "hardcore": return "§c[Hardcore]";
            default: return "[" + eventTier + "]";
        }
    }

    private void startCombatPhase() {
        this.eventInProgress = true;
        this.secondsElapsed = 0;
        World world = eventLocation.getWorld();
        world.setTime(13000);
        world.setStorm(false);
        world.setThundering(false);
        for (Player p : participants) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dx = Math.cos(angle) * 50;
            double dz = Math.sin(angle) * 50;
            Location tpLoc = eventLocation.clone().add(dx, 0, dz);
            tpLoc.setY(world.getHighestBlockYAt(tpLoc) + 1);
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
        double scaleFactor = getMobScaleFactor();
        for (Map.Entry<String, Integer> entry : activeProfile.getMobs().entrySet()) {
            String mobTypeName = entry.getKey().toUpperCase();
            int count = (int) Math.round(entry.getValue() * scaleFactor);
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(mobTypeName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Evento] Tipo de mob desconocido: " + mobTypeName);
                continue;
            }
            for (int i = 0; i < count; i++) {
                Location spawnLoc = eventLocation.clone().add(
                        random.nextInt(30) - 15, 1, random.nextInt(30) - 15);
                Entity entity = world.spawnEntity(spawnLoc, entityType);
                entity.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                activeMobs.add(entity);
            }
        }
        spawnSpawners();
        Bukkit.broadcastMessage("§c¡" + activeMobs.size() + " criaturas han aparecido!");
    }

    private double getMobScaleFactor() {
        int n = participants.size();
        if (n <= 2) return 1.0;
        if (n == 3) return 1.20;
        if (n == 4) return 1.40;
        return 1.60;
    }

    private void spawnSpawners() {
        if (activeProfile == null || activeProfile.getMobs() == null) return;
        World world = eventLocation.getWorld();
        List<String> mobTypes = new ArrayList<>(activeProfile.getMobs().keySet());
        if (mobTypes.isEmpty()) return;

        int spawnerCount = Math.min(mobTypes.size() * 2, 8);
        for (int i = 0; i < spawnerCount; i++) {
            Location spawnerLoc = eventLocation.clone().add(
                    random.nextInt(24) - 12, -1, random.nextInt(24) - 12);
            spawnerLoc.setY(world.getHighestBlockYAt(spawnerLoc));
            if (spawnerLoc.getBlock().getType() == Material.AIR) {
                spawnerLoc.getBlock().setType(Material.SPAWNER);
                activeSpawners.add(spawnerLoc.getBlock());
                if (spawnerLoc.getBlock().getState() instanceof CreatureSpawner spawner) {
                    String mobTypeName = mobTypes.get(random.nextInt(mobTypes.size())).toUpperCase();
                    try {
                        spawner.setSpawnedType(EntityType.valueOf(mobTypeName));
                        spawner.setDelay(200);
                        spawner.setMaxNearbyEntities(6);
                        spawner.setSpawnRange(4);
                        spawner.update();
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[Evento] Tipo de spawner inválido: " + mobTypeName);
                    }
                }
            }
        }
        Bukkit.broadcastMessage("§5¡" + spawnerCount + " spawners han aparecido!");
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
        int maxSeconds = maxMinutes * 60;
        activeMobs.removeIf(Entity::isDead);

        int secondsLeft = maxSeconds - secondsElapsed;

        if (secondsLeft == 300) {
            Bukkit.broadcastMessage("§eQuedan 5 minutos de evento " + getTierDisplay() + "§e.");
        } else if (secondsLeft == 60) {
            Bukkit.broadcastMessage("§c¡Último minuto del evento " + getTierDisplay() + "§c!");
        } else if (secondsLeft <= 10 && secondsLeft > 0) {
            Bukkit.broadcastMessage("§c§l" + secondsLeft + " segundos restantes!");
        } else if (secondsLeft <= 0) {
            endEvent("Tiempo agotado");
            return;
        }

        if (secondsElapsed > 30 && activeChests.isEmpty() && activeMobs.isEmpty()) {
            endEvent("Todos los objetivos completados");
        }
    }

    public void endEvent(String reason) {
        if (!eventInProgress) return;
        eventInProgress = false;
        if (eventLoopTask != null) eventLoopTask.cancel();
        stopBeam();
        eventLocation.getWorld().setTime(0);
        Bukkit.broadcastMessage("§aEvento concluido: " + reason);
        for (Player player : participants) if (player.isOnline()) giveReturnScroll(player);
        for (Entity mob : activeMobs) if (!mob.isDead()) mob.remove();
        for (Block chest : activeChests) chest.setType(Material.AIR);
        for (Block spawner : activeSpawners) spawner.setType(Material.AIR);
        activeMobs.clear(); activeChests.clear(); activeSpawners.clear(); participants.clear();
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

    public void cancelInvitation() {
        if (!isAcceptingPlayers) return;
        isAcceptingPlayers = false;
        participants.clear();
        activeProfile = null;
        eventLocation = null;
        Bukkit.broadcastMessage("§cEl evento fue cancelado por un administrador.");
    }
}