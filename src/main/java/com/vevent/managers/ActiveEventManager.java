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
import org.bukkit.scoreboard.*;
import java.util.*;

public class ActiveEventManager {
    private final VEventPlugin plugin;
    private final Random random;
    private final NamespacedKey mobKey;
    private final NamespacedKey scrollKey;
    private final NamespacedKey craftedScrollKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey locationKey;
    private boolean isAcceptingPlayers = false;
    private boolean eventInProgress = false;
    private Location eventLocation;
    private LootProfile activeProfile;
    private String eventTier;
    private final List<Player> participants = new ArrayList<>();
    private final List<Block> activeChests = new ArrayList<>();
    private final List<Entity> activeMobs = new ArrayList<>();
    private final List<Block> activeSpawners = new ArrayList<>();
    private final List<Block> beaconBlocks = new ArrayList<>();
    private final Map<Player, Scoreboard> previousScoreboards = new HashMap<>();
    private Location oldBorderCenter;
    private double oldBorderSize;
    private boolean borderWasSaved;
    private BukkitTask eventLoopTask;
    private int secondsElapsed = 0;

    public ActiveEventManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.mobKey = new NamespacedKey(plugin, "vevent_mob");
        this.scrollKey = new NamespacedKey(plugin, "vevent_scroll_time");
        this.craftedScrollKey = new NamespacedKey(plugin, "vevent_crafted_scroll");
        this.uuidKey = new NamespacedKey(plugin, "vevent_scroll_uuid");
        this.locationKey = new NamespacedKey(plugin, "vevent_scroll_loc");
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

        preloadChunks(world, eventLocation, 1);
        setWorldBorder(world);

        assignScoreboards();
        startBeam();

        List<Location> tpLocations = new ArrayList<>();
        for (Player p : participants) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dx = Math.cos(angle) * 25;
            double dz = Math.sin(angle) * 25;
            Location tpLoc = eventLocation.clone().add(dx, 0, dz);
            int groundY = world.getHighestBlockYAt(tpLoc);
            int eventY = eventLocation.getBlockY();
            if (Math.abs(groundY - eventY) > 15) {
                groundY = eventY;
                while (groundY > world.getMinHeight() && !world.getBlockAt(tpLoc.getBlockX(), groundY, tpLoc.getBlockZ()).getType().isSolid()) {
                    groundY--;
                }
            }
            tpLoc.setY(groundY + 1);
            preloadPlayerChunk(world, tpLoc);
            tpLocations.add(tpLoc);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < participants.size(); i++) {
                Player p = participants.get(i);
                if (!p.isOnline()) continue;
                p.teleport(tpLocations.get(i));
                p.setFallDistance(0);
                p.sendMessage("§eEl epicentro está a 25 bloques. ¡Busca el haz de luz!");
            }
            if (activeProfile != null) {
                spawnEventMobs();
                spawnEventChests();
            }
            eventLoopTask = new BukkitRunnable() {
                @Override public void run() { checkEndConditions(); }
            }.runTaskTimer(plugin, 20L, 20L);
        }, 40L);
    }

    private Location findSafeLocation(World world) {
        Location center = world.getSpawnLocation();
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = center.getBlockX() + random.nextInt(3000) - 1500;
            int z = center.getBlockZ() + random.nextInt(3000) - 1500;
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (loc.getBlock().getType() != Material.AIR) continue;
            if (!loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            if (isTooCloseToAnyBed(loc)) continue;
            if (!isFlatEnough(world, x, z)) continue;
            return loc;
        }
        return center;
    }

    private boolean isFlatEnough(World world, int cx, int cz) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int y = world.getHighestBlockYAt(cx + dx * 5, cz + dz * 5);
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return (maxY - minY) <= 3;
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

    private void preloadChunks(World world, Location center, int radius) {
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.getChunkAt(cx + dx, cz + dz);
            }
        }
        plugin.getLogger().info("[Evento] Chunks precargados en radio " + radius + " alrededor del epicentro.");
    }

    private void preloadPlayerChunk(World world, Location loc) {
        world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void setWorldBorder(World world) {
        WorldBorder border = world.getWorldBorder();
        oldBorderCenter = border.getCenter();
        oldBorderSize = border.getSize();
        borderWasSaved = true;
        border.setCenter(eventLocation);
        border.setSize(200);
        border.setDamageAmount(2.0);
        border.setDamageBuffer(2.0);
        border.setWarningDistance(10);
        border.setWarningTime(5);
    }

    private void restoreWorldBorder() {
        if (!borderWasSaved) return;
        WorldBorder border = eventLocation.getWorld().getWorldBorder();
        border.setCenter(oldBorderCenter);
        border.setSize(oldBorderSize);
        border.setDamageAmount(0.2);
        border.setDamageBuffer(5.0);
        border.setWarningDistance(5);
        border.setWarningTime(15);
        borderWasSaved = false;
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
                int spawnX = eventLocation.getBlockX() + random.nextInt(30) - 15;
                int spawnZ = eventLocation.getBlockZ() + random.nextInt(30) - 15;
                int groundY = world.getHighestBlockYAt(spawnX, spawnZ);
                Location spawnLoc = new Location(world, spawnX + 0.5, groundY + 1, spawnZ + 0.5);
                Entity entity = world.spawnEntity(spawnLoc, entityType);
                entity.getPersistentDataContainer().set(mobKey, PersistentDataType.BYTE, (byte) 1);
                entity.setCustomName("§c" + formatMobName(mobTypeName) + " de evento");
                entity.setCustomNameVisible(true);
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

    private String formatMobName(String typeName) {
        String lower = typeName.toLowerCase().replace("_", " ");
        String[] words = lower.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.length() > 0) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
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
        int chestCount = Math.min(participants.size() * 2, 15);
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
        updateScoreboards(secondsLeft);

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

        if (secondsElapsed > 30 && activeMobs.isEmpty()) {
            endEvent("Todos los mobs derrotados");
        }
    }

    public void endEvent(String reason) {
        if (!eventInProgress) return;
        eventInProgress = false;
        if (eventLoopTask != null) eventLoopTask.cancel();
        stopBeam();
        restoreScoreboards();
        restoreWorldBorder();
        eventLocation.getWorld().setTime(0);
        Bukkit.broadcastMessage("§aEvento concluido: " + reason);
        for (Player player : participants) if (player.isOnline()) giveReturnScroll(player);
        for (Entity mob : activeMobs) if (!mob.isDead()) mob.remove();
        for (Block chest : activeChests) chest.setType(Material.AIR);
        for (Block spawner : activeSpawners) spawner.setType(Material.AIR);
        activeMobs.clear(); activeChests.clear(); activeSpawners.clear(); beaconBlocks.clear(); participants.clear();
    }

    private void assignScoreboards() {
        previousScoreboards.clear();
        for (Player p : participants) {
            previousScoreboards.put(p, p.getScoreboard());
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("vevent", "dummy", "§6§l⚔ Evento " + getTierDisplay());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            p.setScoreboard(board);
        }
    }

    private void updateScoreboards(int secondsLeft) {
        int mobsAlive = activeMobs.size();
        long chestsRemaining = activeChests.stream().filter(b -> b.getType() == Material.CHEST).count();
        int mins = secondsLeft / 60;
        int secs = secondsLeft % 60;
        String timeStr = String.format("%d:%02d", mins, secs);

        for (Player p : participants) {
            if (!p.isOnline()) continue;
            Scoreboard board = p.getScoreboard();
            Objective obj = board.getObjective("vevent");
            if (obj == null) continue;

            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }

            obj.getScore("§7§m                    ").setScore(4);
            obj.getScore("§cMobs restantes: §f" + mobsAlive).setScore(3);
            obj.getScore("§6Cofres por abrir: §f" + chestsRemaining).setScore(2);
            obj.getScore("§eTiempo: §f" + timeStr).setScore(1);
            obj.getScore("§7§m                    §r").setScore(0);
        }
    }

    private void restoreScoreboards() {
        for (Map.Entry<Player, Scoreboard> entry : previousScoreboards.entrySet()) {
            Player p = entry.getKey();
            if (p.isOnline()) {
                p.setScoreboard(entry.getValue());
            }
        }
        previousScoreboards.clear();
    }

    private void startBeam() {
        stopBeam();
        World world = eventLocation.getWorld();
        int bx = eventLocation.getBlockX();
        int bz = eventLocation.getBlockZ();
        int by = eventLocation.getBlockY() - 1;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Location ironLoc = new Location(world, bx + dx, by, bz + dz);
                Material existing = ironLoc.getBlock().getType();
                if (existing == Material.AIR || existing.isSolid()) {
                    ironLoc.getBlock().setType(Material.IRON_BLOCK);
                    beaconBlocks.add(ironLoc.getBlock());
                }
            }
        }

        Location beaconLoc = new Location(world, bx + 0.5, by + 1, bz + 0.5);
        beaconLoc.getBlock().setType(Material.BEACON);
        beaconBlocks.add(beaconLoc.getBlock());
    }

    private void stopBeam() {
        for (Block block : beaconBlocks) {
            block.setType(Material.AIR);
        }
        beaconBlocks.clear();
    }

    private void giveReturnScroll(Player player) {
        Location bedLoc = player.getBedSpawnLocation();
        if (bedLoc == null) return;

        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();
        meta.setDisplayName("§6§lPergamino de Retorno");
        meta.setLore(List.of(
                "§7Propietario: §f" + player.getName(),
                "§7Click derecho para volver a tu cama",
                "§7Expira en 5 minutos"
        ));
        String locString = bedLoc.getWorld().getName() + ";" + bedLoc.getBlockX() + ";" + bedLoc.getBlockY() + ";" + bedLoc.getBlockZ();
        meta.getPersistentDataContainer().set(scrollKey, PersistentDataType.LONG, System.currentTimeMillis());
        meta.getPersistentDataContainer().set(locationKey, PersistentDataType.STRING, locString);
        meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
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
    public List<Block> getBeaconBlocks() { return beaconBlocks; }
    public boolean areAllMobsDead() { return activeMobs.isEmpty(); }
    public NamespacedKey getScrollKey() { return scrollKey; }
    public NamespacedKey getCraftedScrollKey() { return craftedScrollKey; }
    public NamespacedKey getUuidKey() { return uuidKey; }
    public NamespacedKey getLocationKey() { return locationKey; }

    public void cancelInvitation() {
        if (!isAcceptingPlayers) return;
        isAcceptingPlayers = false;
        participants.clear();
        activeProfile = null;
        eventLocation = null;
        Bukkit.broadcastMessage("§cEl evento fue cancelado por un administrador.");
    }
}