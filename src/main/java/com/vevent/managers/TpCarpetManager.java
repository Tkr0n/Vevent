package com.vevent.managers;

import com.vevent.VEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TpCarpetManager implements Listener {

    private final VEventPlugin plugin;
    private final NamespacedKey carpetTag;
    private final File saveFile;

    private final Map<String, StoredCarpet> unlinked = new HashMap<>();
    private final Map<String, CarpetPair> pairsById = new HashMap<>();
    private final Map<String, String> locToPairId = new HashMap<>();

    private final Map<UUID, PendingLink> pendingLinks = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> justTeleported = new HashMap<>();
    private final Map<UUID, Long> unlinkedHintThrottle = new HashMap<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    private static final long LINK_TIMEOUT_MS = 60_000L;
    private static final long COOLDOWN_MS = 3_000L;
    private static final long ANTILOOP_MS = 2_000L;
    private static final long HINT_THROTTLE_MS = 5_000L;

    private static final Material[] CARPET_COLORS = {
            Material.WHITE_CARPET, Material.ORANGE_CARPET, Material.MAGENTA_CARPET,
            Material.LIGHT_BLUE_CARPET, Material.YELLOW_CARPET, Material.LIME_CARPET,
            Material.PINK_CARPET, Material.GRAY_CARPET, Material.LIGHT_GRAY_CARPET,
            Material.CYAN_CARPET, Material.PURPLE_CARPET, Material.BLUE_CARPET,
            Material.BROWN_CARPET, Material.GREEN_CARPET, Material.RED_CARPET,
            Material.BLACK_CARPET
    };

    public TpCarpetManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.carpetTag = new NamespacedKey(plugin, "vevent_tp_carpet");
        this.saveFile = new File(plugin.getDataFolder(), "carpets.yml");
        load();
        registerAllRecipes();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("TpCarpetManager inicializado. " + pairsById.size() + " pares, " + unlinked.size() + " sin enlazar.");
    }

    public void stop() {
        save();
    }

    // --- Item factory ---

    public ItemStack createCarpetItem(Material carpetMat) {
        if (!isCarpet(carpetMat)) carpetMat = Material.WHITE_CARPET;
        ItemStack item = new ItemStack(carpetMat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§lAlfombra TP [" + spanishColorName(carpetMat) + "]");
        meta.setLore(List.of("§7Sin enlazar", "§7Shift+click para enlazar"));
        meta.getPersistentDataContainer().set(carpetTag, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTpCarpetItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(carpetTag, PersistentDataType.BOOLEAN);
    }

    public static boolean isCarpet(Material mat) {
        return mat != null && mat.name().endsWith("_CARPET");
    }

    public static Material parseCarpetColor(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase().replace(" ", "_").replace("-", "_");
        switch (s) {
            case "blanca": case "blanco": case "white": case "white_carpet": return Material.WHITE_CARPET;
            case "naranja": case "orange": case "orange_carpet": return Material.ORANGE_CARPET;
            case "magenta": case "magenta_carpet": return Material.MAGENTA_CARPET;
            case "celeste": case "light_blue": case "lightblue": case "light_blue_carpet": return Material.LIGHT_BLUE_CARPET;
            case "amarilla": case "amarillo": case "yellow": case "yellow_carpet": return Material.YELLOW_CARPET;
            case "lima": case "lime": case "lime_carpet": return Material.LIME_CARPET;
            case "rosa": case "pink": case "pink_carpet": return Material.PINK_CARPET;
            case "gris": case "gray": case "grey": case "gray_carpet": return Material.GRAY_CARPET;
            case "gris_claro": case "grisclaro": case "light_gray": case "light_gray_carpet": return Material.LIGHT_GRAY_CARPET;
            case "cian": case "cyan": case "cyan_carpet": return Material.CYAN_CARPET;
            case "morada": case "morado": case "purple": case "purple_carpet": return Material.PURPLE_CARPET;
            case "azul": case "blue": case "blue_carpet": return Material.BLUE_CARPET;
            case "marron": case "marrón": case "brown": case "brown_carpet": return Material.BROWN_CARPET;
            case "verde": case "green": case "green_carpet": return Material.GREEN_CARPET;
            case "roja": case "rojo": case "red": case "red_carpet": return Material.RED_CARPET;
            case "negra": case "negro": case "black": case "black_carpet": return Material.BLACK_CARPET;
            default: break;
        }
        Material direct = Material.getMaterial(s.toUpperCase());
        if (direct != null && isCarpet(direct)) return direct;
        return null;
    }

    public static String spanishColorName(Material carpet) {
        if (carpet == null) return "Blanco";
        switch (carpet.name()) {
            case "WHITE_CARPET": return "Blanco";
            case "ORANGE_CARPET": return "Naranja";
            case "MAGENTA_CARPET": return "Magenta";
            case "LIGHT_BLUE_CARPET": return "Celeste";
            case "YELLOW_CARPET": return "Amarillo";
            case "LIME_CARPET": return "Lima";
            case "PINK_CARPET": return "Rosa";
            case "GRAY_CARPET": return "Gris";
            case "LIGHT_GRAY_CARPET": return "Gris claro";
            case "CYAN_CARPET": return "Cian";
            case "PURPLE_CARPET": return "Morado";
            case "BLUE_CARPET": return "Azul";
            case "BROWN_CARPET": return "Marron";
            case "GREEN_CARPET": return "Verde";
            case "RED_CARPET": return "Rojo";
            case "BLACK_CARPET": return "Negro";
            default: return "Blanco";
        }
    }

    public static List<String> colorSuggestions() {
        return List.of("blanco", "naranja", "magenta", "celeste", "amarillo", "lima", "rosa",
                "gris", "gris_claro", "cian", "morado", "azul", "marron", "verde", "rojo", "negro");
    }

    public static Material woolForCarpet(Material carpet) {
        if (carpet == null) return Material.WHITE_WOOL;
        String woolName = carpet.name().replace("_CARPET", "_WOOL");
        Material wool = Material.getMaterial(woolName);
        return wool != null ? wool : Material.WHITE_WOOL;
    }

    public static String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public static String locKey(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    // --- Recipes ---

    private void registerAllRecipes() {
        for (Material carpet : CARPET_COLORS) {
            Material wool = woolForCarpet(carpet);
            ItemStack result = createCarpetItem(carpet);
            result.setAmount(2);
            String keyName = "tp_carpet_" + carpet.name().toLowerCase();
            NamespacedKey key = new NamespacedKey(plugin, keyName);
            recipeKeys.add(key);
            try {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape("LPL", "PAP", "LPL");
                recipe.setIngredient('L', wool);
                recipe.setIngredient('P', Material.ENDER_PEARL);
                recipe.setIngredient('A', carpet);
                Bukkit.addRecipe(recipe);
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo registrar receta " + keyName + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Recetas de Alfombra TP registradas: " + recipeKeys.size());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (NamespacedKey key : recipeKeys) {
            event.getPlayer().discoverRecipe(key);
        }
    }

    // --- Persistence ---

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        List<?> pairList = cfg.getList("pairs", new ArrayList<>());
        for (Object o : pairList) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            try {
                String id = String.valueOf(m.get("id"));
                Material color = Material.getMaterial(String.valueOf(m.get("color")));
                if (color == null || !isCarpet(color)) continue;
                String worldA = String.valueOf(m.get("worldA"));
                String worldB = String.valueOf(m.get("worldB"));
                int xA = ((Number) m.get("xA")).intValue();
                int yA = ((Number) m.get("yA")).intValue();
                int zA = ((Number) m.get("zA")).intValue();
                int xB = ((Number) m.get("xB")).intValue();
                int yB = ((Number) m.get("yB")).intValue();
                int zB = ((Number) m.get("zB")).intValue();
                String owner = m.get("owner") != null ? String.valueOf(m.get("owner")) : null;
                CarpetPair pair = new CarpetPair(id, color, worldA, xA, yA, zA, worldB, xB, yB, zB, owner);
                pairsById.put(id, pair);
                locToPairId.put(locKey(worldA, xA, yA, zA), id);
                locToPairId.put(locKey(worldB, xB, yB, zB), id);
            } catch (Exception ignored) {}
        }
        ConfigurationSection unlinkedSec = cfg.getConfigurationSection("unlinked");
        if (unlinkedSec != null) {
            for (String key : unlinkedSec.getKeys(false)) {
                try {
                    String colorName = unlinkedSec.getString(key + ".color");
                    Material color = Material.getMaterial(colorName);
                    if (color == null || !isCarpet(color)) continue;
                    String owner = unlinkedSec.getString(key + ".owner");
                    unlinked.put(key, new StoredCarpet(color, owner));
                } catch (Exception ignored) {}
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> pairList = new ArrayList<>();
        for (CarpetPair p : pairsById.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.id);
            m.put("color", p.color.name());
            m.put("worldA", p.worldA);
            m.put("xA", p.xA);
            m.put("yA", p.yA);
            m.put("zA", p.zA);
            m.put("worldB", p.worldB);
            m.put("xB", p.xB);
            m.put("yB", p.yB);
            m.put("zB", p.zB);
            m.put("owner", p.owner);
            pairList.add(m);
        }
        cfg.set("pairs", pairList);
        for (Map.Entry<String, StoredCarpet> e : unlinked.entrySet()) {
            cfg.set("unlinked." + e.getKey() + ".color", e.getValue().color.name());
            cfg.set("unlinked." + e.getKey() + ".owner", e.getValue().owner);
        }
        try {
            cfg.save(saveFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar carpets.yml: " + e.getMessage());
        }
    }

    // --- Placement / break ---

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isTpCarpetItem(item)) return;
        if (!event.getPlayer().hasPermission("vevent.tpcarpet.place")) {
            event.getPlayer().sendMessage("§cNo tienes permiso para colocar alfombras TP.");
            event.setCancelled(true);
            return;
        }
        Material placedType = event.getBlock().getType();
        if (!isCarpet(placedType)) return;
        Location loc = event.getBlock().getLocation();
        String key = locKey(loc);
        if (locToPairId.containsKey(key) || unlinked.containsKey(key)) return;
        unlinked.put(key, new StoredCarpet(placedType, event.getPlayer().getUniqueId().toString()));
        save();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isCarpet(block.getType())) return;
        String key = locKey(block.getLocation());
        boolean wasPair = locToPairId.containsKey(key);
        boolean wasUnlinked = unlinked.containsKey(key);
        if (!wasPair && !wasUnlinked) return;

        Material color = block.getType();
        String pairId = locToPairId.get(key);
        if (pairId != null) {
            CarpetPair pair = pairsById.remove(pairId);
            if (pair != null) {
                color = pair.color;
                locToPairId.remove(pair.keyA());
                locToPairId.remove(pair.keyB());
                String otherKey = pair.keyA().equals(key) ? pair.keyB() : pair.keyA();
                Material otherColor = pair.color;
                if (!locToPairId.containsKey(otherKey)) {
                    unlinked.put(otherKey, new StoredCarpet(otherColor, pair.owner));
                }
                event.getPlayer().sendMessage("§eEnlace de alfombra roto. La otra quedo sin enlazar.");
            }
        }
        unlinked.remove(key);
        save();
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), createCarpetItem(color));
    }

    // --- Linking ---

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !isCarpet(block.getType())) return;
        String key = locKey(block.getLocation());
        boolean tracked = locToPairId.containsKey(key) || unlinked.containsKey(key);
        if (!tracked) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (!player.hasPermission("vevent.tpcarpet.place")) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        PendingLink pending = pendingLinks.get(player.getUniqueId());
        if (pending != null && (now - pending.timestamp) > LINK_TIMEOUT_MS) {
            pending = null;
            pendingLinks.remove(player.getUniqueId());
        }

        if (pending == null) {
            pendingLinks.put(player.getUniqueId(), new PendingLink(block.getLocation().clone(), now));
            Material c = block.getType();
            player.sendMessage("§aOrigen marcado (" + spanishColorName(c) + "). Ahora shift+click en el destino del mismo color.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            player.spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.6, 0.5), 10, 0.3, 0.2, 0.3);
            return;
        }

        Location originLoc = pending.location;
        String originKey = locKey(originLoc);
        if (originKey.equals(key)) {
            player.sendMessage("§cEs la misma alfombra. Elige otra como destino.");
            return;
        }
        Block originBlock = originLoc.getBlock();
        if (!isCarpet(originBlock.getType())) {
            player.sendMessage("§cEl origen ya no existe. Marca otro origen.");
            pendingLinks.remove(player.getUniqueId());
            return;
        }
        if (originBlock.getType() != block.getType()) {
            player.sendMessage("§cDeben ser del mismo color.");
            return;
        }

        breakExistingPair(originKey, player, true);
        breakExistingPair(key, player, true);

        String id = UUID.randomUUID().toString().substring(0, 8);
        Material color = block.getType();
        CarpetPair pair = new CarpetPair(id, color,
                originLoc.getWorld().getName(), originLoc.getBlockX(), originLoc.getBlockY(), originLoc.getBlockZ(),
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                player.getUniqueId().toString());
        pairsById.put(id, pair);
        locToPairId.put(pair.keyA(), id);
        locToPairId.put(pair.keyB(), id);
        unlinked.remove(pair.keyA());
        unlinked.remove(pair.keyB());
        pendingLinks.remove(player.getUniqueId());
        save();

        player.sendMessage("§a¡Alfombras enlazadas! (" + spanishColorName(color) + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        player.spawnParticle(Particle.PORTAL, block.getLocation().add(0.5, 0.6, 0.5), 20, 0.3, 0.3, 0.3);
        originBlock.getWorld().spawnParticle(Particle.PORTAL, originLoc.clone().add(0.5, 0.6, 0.5), 20, 0.3, 0.3, 0.3);
    }

    private void breakExistingPair(String locKey, Player actor, boolean notify) {
        String pairId = locToPairId.get(locKey);
        if (pairId == null) return;
        CarpetPair old = pairsById.remove(pairId);
        if (old == null) return;
        locToPairId.remove(old.keyA());
        locToPairId.remove(old.keyB());
        if (!old.keyA().equals(locKey) && !locToPairId.containsKey(old.keyA())) {
            unlinked.put(old.keyA(), new StoredCarpet(old.color, old.owner));
        }
        if (!old.keyB().equals(locKey) && !locToPairId.containsKey(old.keyB())) {
            unlinked.put(old.keyB(), new StoredCarpet(old.color, old.owner));
        }
        if (notify && actor != null) {
            actor.sendMessage("§eEnlace anterior roto, nuevo enlace creado.");
        }
    }

    // --- Activation ---

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        tryTeleport(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        tryTeleport(event.getPlayer());
    }

    private Block getStandingCarpetBlock(Player player) {
        Block atFeet = player.getLocation().getBlock();
        if (isCarpet(atFeet.getType())) return atFeet;
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        if (isCarpet(below.getType())) return below;
        Block twoBelow = player.getLocation().subtract(0, 0.6, 0).getBlock();
        if (isCarpet(twoBelow.getType())) return twoBelow;
        return null;
    }

    private void tryTeleport(Player player) {
        long now = System.currentTimeMillis();
        Long immuneUntil = justTeleported.get(player.getUniqueId());
        if (immuneUntil != null && now < immuneUntil) return;
        Long cdUntil = cooldowns.get(player.getUniqueId());
        Block carpet = getStandingCarpetBlock(player);
        if (carpet == null) return;
        String key = locKey(carpet.getLocation());
        String pairId = locToPairId.get(key);
        if (pairId == null) {
            if (unlinked.containsKey(key)) {
                Long lastHint = unlinkedHintThrottle.get(player.getUniqueId());
                if (lastHint == null || (now - lastHint) > HINT_THROTTLE_MS) {
                    unlinkedHintThrottle.put(player.getUniqueId(), now);
                    player.sendMessage("§7Alfombra sin enlazar. Shift+click en otra del mismo color para enlazar.");
                }
            }
            return;
        }
        if (!player.hasPermission("vevent.tpcarpet.use")) {
            player.sendMessage("§cNo tienes permiso para usar alfombras TP.");
            return;
        }
        if (cdUntil != null && now < cdUntil) {
            long left = (cdUntil - now + 999) / 1000;
            player.sendMessage("§cEspera " + left + " s antes de volver a usarla.");
            return;
        }
        CarpetPair pair = pairsById.get(pairId);
        if (pair == null) return;
        Location destBlockLoc = pair.otherEnd(key);
        if (destBlockLoc == null) return;

        World destWorld = destBlockLoc.getWorld();
        if (destWorld == null) {
            player.sendMessage("§cDestino bloqueado o destruido. Revisa la otra alfombra.");
            return;
        }
        if (!destWorld.getChunkAt(destBlockLoc).isLoaded()) {
            destWorld.getChunkAt(destBlockLoc).load(true);
        }
        Block destBlock = destBlockLoc.getBlock();
        if (!isCarpet(destBlock.getType()) || destBlock.getType() != pair.color) {
            player.sendMessage("§cDestino bloqueado o destruido. Revisa la otra alfombra.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            return;
        }
        Block air1 = destBlock.getRelative(0, 1, 0);
        Block air2 = destBlock.getRelative(0, 2, 0);
        if (!air1.getType().isAir() || !air2.getType().isAir()) {
            player.sendMessage("§cDestino bloqueado o destruido. Revisa la otra alfombra.");
            return;
        }

        Location target = destBlockLoc.clone().add(0.5, 1.0, 0.5);
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

        Location from = player.getLocation().clone();
        cooldowns.put(player.getUniqueId(), now + COOLDOWN_MS);
        justTeleported.put(player.getUniqueId(), now + ANTILOOP_MS);
        player.teleport(target);
        from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 25, 0.4, 0.5, 0.4);
        target.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
    }

    // --- Protection ---

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            String k = locKey(b.getLocation());
            if (locToPairId.containsKey(k) || unlinked.containsKey(k)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            String k = locKey(b.getLocation());
            if (locToPairId.containsKey(k) || unlinked.containsKey(k)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> {
            String k = locKey(b.getLocation());
            return locToPairId.containsKey(k) || unlinked.containsKey(k);
        });
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> {
            String k = locKey(b.getLocation());
            return locToPairId.containsKey(k) || unlinked.containsKey(k);
        });
    }

    // --- Data classes ---

    private static class StoredCarpet {
        final Material color;
        final String owner;
        StoredCarpet(Material color, String owner) {
            this.color = color;
            this.owner = owner;
        }
    }

    private static class CarpetPair {
        final String id;
        final Material color;
        final String worldA;
        final int xA, yA, zA;
        final String worldB;
        final int xB, yB, zB;
        final String owner;

        CarpetPair(String id, Material color, String worldA, int xA, int yA, int zA,
                   String worldB, int xB, int yB, int zB, String owner) {
            this.id = id;
            this.color = color;
            this.worldA = worldA;
            this.xA = xA;
            this.yA = yA;
            this.zA = zA;
            this.worldB = worldB;
            this.xB = xB;
            this.yB = yB;
            this.zB = zB;
            this.owner = owner;
        }

        String keyA() { return locKey(worldA, xA, yA, zA); }
        String keyB() { return locKey(worldB, xB, yB, zB); }

        Location otherEnd(String fromKey) {
            String kA = keyA();
            String kB = keyB();
            String targetKey = kA.equals(fromKey) ? kB : kA;
            String[] parts = targetKey.split(";");
            if (parts.length < 4) return null;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) return null;
            try {
                return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static class PendingLink {
        final Location location;
        final long timestamp;
        PendingLink(Location location, long timestamp) {
            this.location = location;
            this.timestamp = timestamp;
        }
    }
}
