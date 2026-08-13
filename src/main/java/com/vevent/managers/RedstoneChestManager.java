package com.vevent.managers;

import com.vevent.VEventPlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RedstoneChestManager implements Listener {

    private final VEventPlugin plugin;
    private final NamespacedKey chestTag;
    private final NamespacedKey recipeKey;
    private final Material copperChestMat;
    private final Set<Location> hubs = new HashSet<>();
    private final File saveFile;
    private final Set<UUID> searchMode = new HashSet<>();

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_START = 9;
    private static final int ITEMS_PER_PAGE = 36;
    private static final int SEARCH_SLOT = 8;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int INFO_SLOT = 46;
    private static final int CLEAR_SLOT = 47;
    private static final int DEPOSIT_SLOT = 50;

    private static final String[] CATEGORIES = {
            "Todos", "Construccion", "Natural", "Funcional",
            "Herramientas", "Combate", "Comida", "Ingredientes"
    };
    private static final Material[] CATEGORY_ICONS = {
            Material.CRAFTING_TABLE, Material.OAK_PLANKS, Material.GRASS_BLOCK, Material.FURNACE,
            Material.IRON_PICKAXE, Material.IRON_SWORD, Material.APPLE, Material.DIAMOND
    };

    private static final Map<String, String> WORD_TRANSLATIONS = new HashMap<>();
    static {
        WORD_TRANSLATIONS.put("diamond", "diamante");
        WORD_TRANSLATIONS.put("tnt", "dinamita");
        WORD_TRANSLATIONS.put("golden", "dorado");
        WORD_TRANSLATIONS.put("gold", "oro");
        WORD_TRANSLATIONS.put("iron", "hierro");
        WORD_TRANSLATIONS.put("copper", "cobre");
        WORD_TRANSLATIONS.put("netherite", "netherita");
        WORD_TRANSLATIONS.put("emerald", "esmeralda");
        WORD_TRANSLATIONS.put("lapis_lazuli", "lapislazuli");
        WORD_TRANSLATIONS.put("quartz", "cuarzo");
        WORD_TRANSLATIONS.put("redstone", "redstone");
        WORD_TRANSLATIONS.put("sword", "espada");
        WORD_TRANSLATIONS.put("pickaxe", "pico");
        WORD_TRANSLATIONS.put("axe", "hacha");
        WORD_TRANSLATIONS.put("shovel", "pala");
        WORD_TRANSLATIONS.put("hoe", "azada");
        WORD_TRANSLATIONS.put("helmet", "casco");
        WORD_TRANSLATIONS.put("chestplate", "peto");
        WORD_TRANSLATIONS.put("leggings", "mallas");
        WORD_TRANSLATIONS.put("boots", "botas");
        WORD_TRANSLATIONS.put("ingot", "lingote");
        WORD_TRANSLATIONS.put("nugget", "pepita");
        WORD_TRANSLATIONS.put("block", "bloque");
        WORD_TRANSLATIONS.put("ore", "mineral");
        WORD_TRANSLATIONS.put("raw", "crudo");
        WORD_TRANSLATIONS.put("stone", "piedra");
        WORD_TRANSLATIONS.put("wooden", "madera");
        WORD_TRANSLATIONS.put("oak", "roble");
        WORD_TRANSLATIONS.put("spruce", "abeto");
        WORD_TRANSLATIONS.put("birch", "abedul");
        WORD_TRANSLATIONS.put("jungle", "jungla");
        WORD_TRANSLATIONS.put("cherry", "cerezo");
        WORD_TRANSLATIONS.put("apple", "manzana");
        WORD_TRANSLATIONS.put("bread", "pan");
        WORD_TRANSLATIONS.put("porkchop", "chuleta");
        WORD_TRANSLATIONS.put("chicken", "pollo");
        WORD_TRANSLATIONS.put("mutton", "cordero");
        WORD_TRANSLATIONS.put("carrot", "zanahoria");
        WORD_TRANSLATIONS.put("potato", "patata");
        WORD_TRANSLATIONS.put("sugar", "azucar");
        WORD_TRANSLATIONS.put("cookie", "galleta");
        WORD_TRANSLATIONS.put("cake", "pastel");
        WORD_TRANSLATIONS.put("potion", "pocion");
        WORD_TRANSLATIONS.put("arrow", "flecha");
        WORD_TRANSLATIONS.put("bow", "arco");
        WORD_TRANSLATIONS.put("crossbow", "ballesta");
        WORD_TRANSLATIONS.put("shield", "escudo");
        WORD_TRANSLATIONS.put("armor", "armadura");
        WORD_TRANSLATIONS.put("wood", "madera");
        WORD_TRANSLATIONS.put("planks", "tablones");
        WORD_TRANSLATIONS.put("log", "tronco");
        WORD_TRANSLATIONS.put("leaves", "hojas");
        WORD_TRANSLATIONS.put("sand", "arena");
        WORD_TRANSLATIONS.put("dirt", "tierra");
        WORD_TRANSLATIONS.put("gravel", "grava");
        WORD_TRANSLATIONS.put("cobblestone", "piedra");
        WORD_TRANSLATIONS.put("obsidian", "obsidiana");
        WORD_TRANSLATIONS.put("grass", "cesped");
        WORD_TRANSLATIONS.put("flower", "flor");
        WORD_TRANSLATIONS.put("book", "libro");
        WORD_TRANSLATIONS.put("paper", "papel");
        WORD_TRANSLATIONS.put("ender_pearl", "perla del end");
        WORD_TRANSLATIONS.put("blaze_rod", "vara de blaze");
        WORD_TRANSLATIONS.put("ghast_tear", "lagrima de ghast");
        WORD_TRANSLATIONS.put("slime_ball", "bola de slime");
        WORD_TRANSLATIONS.put("bone", "hueso");
        WORD_TRANSLATIONS.put("feather", "pluma");
        WORD_TRANSLATIONS.put("leather", "cuero");
        WORD_TRANSLATIONS.put("string", "hilo");
        WORD_TRANSLATIONS.put("gunpowder", "polvora");
        WORD_TRANSLATIONS.put("flint", "pedernal");
        WORD_TRANSLATIONS.put("stick", "palo");
        WORD_TRANSLATIONS.put("coal", "carbon");
        WORD_TRANSLATIONS.put("torch", "antorcha");
        WORD_TRANSLATIONS.put("lantern", "farol");
        WORD_TRANSLATIONS.put("glass", "cristal");
        WORD_TRANSLATIONS.put("wool", "lana");
        WORD_TRANSLATIONS.put("carpet", "alfombra");
        WORD_TRANSLATIONS.put("bed", "cama");
        WORD_TRANSLATIONS.put("door", "puerta");
        WORD_TRANSLATIONS.put("trapdoor", "trampilla");
        WORD_TRANSLATIONS.put("fence", "valla");
        WORD_TRANSLATIONS.put("stairs", "escaleras");
        WORD_TRANSLATIONS.put("slab", "losa");
        WORD_TRANSLATIONS.put("wall", "muro");
        WORD_TRANSLATIONS.put("chest", "cofre");
        WORD_TRANSLATIONS.put("barrel", "barril");
        WORD_TRANSLATIONS.put("hopper", "tolva");
        WORD_TRANSLATIONS.put("furnace", "horno");
        WORD_TRANSLATIONS.put("anvil", "yunque");
        WORD_TRANSLATIONS.put("beacon", "faro");
        WORD_TRANSLATIONS.put("shulker_box", "caja de shulker");
        WORD_TRANSLATIONS.put("totem_of_undying", "totem");
        WORD_TRANSLATIONS.put("mace", "maza");
        WORD_TRANSLATIONS.put("trident", "tridente");
        WORD_TRANSLATIONS.put("elytra", "elitra");
        WORD_TRANSLATIONS.put("saddle", "silla");
        WORD_TRANSLATIONS.put("name_tag", "etiqueta");
        WORD_TRANSLATIONS.put("lead", "cuerda");
        WORD_TRANSLATIONS.put("compass", "brujula");
        WORD_TRANSLATIONS.put("clock", "reloj");
        WORD_TRANSLATIONS.put("map", "mapa");
        WORD_TRANSLATIONS.put("bucket", "cubo");
        WORD_TRANSLATIONS.put("milk", "leche");
        WORD_TRANSLATIONS.put("egg", "huevo");
        WORD_TRANSLATIONS.put("snow", "nieve");
        WORD_TRANSLATIONS.put("ice", "hielo");
        WORD_TRANSLATIONS.put("pumpkin", "calabaza");
        WORD_TRANSLATIONS.put("melon", "sandia");
        WORD_TRANSLATIONS.put("cactus", "cactus");
        WORD_TRANSLATIONS.put("bamboo", "bambu");
        WORD_TRANSLATIONS.put("vines", "enredaderas");
        WORD_TRANSLATIONS.put("wheat", "trigo");
        WORD_TRANSLATIONS.put("seeds", "semillas");
        WORD_TRANSLATIONS.put("beetroot", "remolacha");
        WORD_TRANSLATIONS.put("spider_eye", "ojo de arana");
        WORD_TRANSLATIONS.put("rotten_flesh", "carne podrida");
        WORD_TRANSLATIONS.put("experience_bottle", "frasco de experiencia");
        WORD_TRANSLATIONS.put("firework", "fuegos artificiales");
        WORD_TRANSLATIONS.put("banner", "estandarte");
        WORD_TRANSLATIONS.put("dye", "tinte");
        WORD_TRANSLATIONS.put("candle", "vela");
        WORD_TRANSLATIONS.put("crystal", "cristal");
        WORD_TRANSLATIONS.put("shard", "fragmento");
        WORD_TRANSLATIONS.put("pearl", "perla");
        WORD_TRANSLATIONS.put("rod", "vara");
        WORD_TRANSLATIONS.put("powder", "polvo");
        WORD_TRANSLATIONS.put("bone_meal", "polvo de hueso");
        WORD_TRANSLATIONS.put("sugar_cane", "caña de azucar");
        WORD_TRANSLATIONS.put("steak", "filete");
        WORD_TRANSLATIONS.put("cod", "bacalao");
        WORD_TRANSLATIONS.put("salmon", "salmon");
        WORD_TRANSLATIONS.put("potato", "patata");
        WORD_TRANSLATIONS.put("cocoa_beans", "cacao");
        WORD_TRANSLATIONS.put("golden_apple", "manzana de oro");
        WORD_TRANSLATIONS.put("enchanted_golden_apple", "manzana dorada encantada");
    }

    private String toSpanish(String materialName) {
        String result = materialName.toLowerCase().replace("_", " ");
        String translated = result;
        for (Map.Entry<String, String> e : WORD_TRANSLATIONS.entrySet()) {
            translated = translated.replace(e.getKey().replace("_", " "), e.getValue());
        }
        return translated;
    }

    private boolean matchesFilter(Material mat, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        String english = mat.name().toLowerCase().replace("_", " ");
        String spanish = toSpanish(mat.name());
        return english.contains(filter) || spanish.contains(filter)
                || english.replace(" ", "").contains(filter.replace(" ", ""))
                || spanish.replace(" ", "").contains(filter.replace(" ", ""));
    }

    private final Map<UUID, String> playerFilters = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerCategories = new HashMap<>();
    private final Map<UUID, List<GUIEntry>> pageEntries = new HashMap<>();

    private static class GUIEntry {
        final ItemStack item;
        final Material material;
        final String category;
        final String sortKey;
        final boolean stackable;
        final int total;
        final ItemStack withdrawTarget;

        GUIEntry(ItemStack item, Material material, String category, String sortKey, boolean stackable, int total, ItemStack withdrawTarget) {
            this.item = item;
            this.material = material;
            this.category = category;
            this.sortKey = sortKey;
            this.stackable = stackable;
            this.total = total;
            this.withdrawTarget = withdrawTarget;
        }
    }

    public RedstoneChestManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.chestTag = new NamespacedKey(plugin, "vevent_redstone_chest");
        this.recipeKey = new NamespacedKey(plugin, "redstone_chest_craft");
        this.saveFile = new File(plugin.getDataFolder(), "redstone_chests.yml");

        Material mat = Material.getMaterial("WAXED_COPPER_CHEST");
        if (mat == null) mat = Material.getMaterial("COPPER_CHEST");
        if (mat == null) {
            mat = Material.CHEST;
            plugin.getLogger().warning("WAXED_COPPER_CHEST y COPPER_CHEST no encontrados. Usando CHEST como fallback.");
        }
        this.copperChestMat = mat;

        loadHubs();
        registerRecipe();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("RedstoneChestManager inicializado. " + hubs.size() + " hubs trackeados.");
    }

    private void loadHubs() {
        if (!saveFile.exists()) {
            plugin.getLogger().info("[RedstoneChest] No hay archivo de hubs. 0 trackeados.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        List<String> entries = new ArrayList<>();
        if (cfg.isList("hubs")) {
            entries.addAll(cfg.getStringList("hubs"));
        } else {
            for (String key : cfg.getKeys(false)) entries.add(key);
        }
        List<String> unresolved = new ArrayList<>();
        for (String key : entries) {
            try {
                String[] parts = key.split(";");
                if (parts.length < 4) continue;
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) {
                    unresolved.add(key);
                    continue;
                }
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                hubs.add(new Location(world, x, y, z));
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[RedstoneChest] Hubs cargados: " + hubs.size()
                + (unresolved.isEmpty() ? "" : " | mundos sin resolver: " + unresolved.size()));
        if (!unresolved.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::loadHubs, 100L);
        }
    }

    public void stop() {
        saveHubs();
    }

    private void saveHubs() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> entries = new ArrayList<>();
        for (Location loc : hubs) {
            if (loc.getWorld() == null) continue;
            entries.add(loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ());
        }
        cfg.set("hubs", entries);
        try { cfg.save(saveFile); } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar redstone_chests.yml: " + e.getMessage());
        }
    }

    private ItemStack createHubItem() {
        ItemStack item = new ItemStack(copperChestMat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lCofre de Redstone");
        meta.setLore(List.of("§7Hub central de almacenamiento",
                "§7Conecta cofres con comparadores + repetidores"));
        meta.getPersistentDataContainer().set(chestTag, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private void registerRecipe() {
        ItemStack result = createHubItem();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("RBR", "CEC", "RBR");
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('B', Material.REDSTONE_BLOCK);
        recipe.setIngredient('C', Material.COMPARATOR);
        recipe.setIngredient('E', copperChestMat);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Receta de Cofre de Redstone registrada.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != copperChestMat) return;
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(chestTag, PersistentDataType.BOOLEAN)) {
            hubs.add(event.getBlock().getLocation());
            saveHubs();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != copperChestMat) return;
        Location loc = event.getBlock().getLocation();
        if (hubs.remove(loc)) {
            saveHubs();
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(loc, createHubItem());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != copperChestMat) return;
        if (!hubs.contains(block.getLocation())) return;
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand.getType().isBlock() && hand.getType() != Material.AIR) return;
        event.setCancelled(true);

        if (findLinkedChests(block.getLocation()).isEmpty()) {
            event.getPlayer().sendMessage("§cEl circuito de redstone no esta energizado. Conecta cofres con comparadores + repetidores.");
            return;
        }
        openGUI(event.getPlayer(), block.getLocation());
    }

    // --- Redstone Network Detection ---

    private List<Block> findLinkedChests(Location hubLoc) {
        List<Block> chests = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Block hub = hubLoc.getBlock();
        visited.add(hubLoc);

        for (org.bukkit.block.BlockFace face : org.bukkit.block.BlockFace.values()) {
            Block neighbor = hub.getRelative(face);
            traceFromHub(neighbor, face, visited, chests, 0);
        }
        return chests;
    }

    private void traceFromHub(Block block, org.bukkit.block.BlockFace fromFace, Set<Location> visited, List<Block> found, int depth) {
        if (depth > 60 || !visited.add(block.getLocation())) return;
        if (block.getBlockPower() == 0) return;

        Material mat = block.getType();

        if (mat == Material.REDSTONE_WIRE) {
            for (org.bukkit.block.BlockFace face : org.bukkit.block.BlockFace.values()) {
                Block neighbor = block.getRelative(face);
                if (neighbor.getType() == Material.REDSTONE_WIRE && neighbor.getBlockPower() > 0) {
                    traceFromHub(neighbor, face, visited, found, depth + 1);
                } else if (neighbor.getType() == Material.REPEATER && face != org.bukkit.block.BlockFace.UP && face != org.bukkit.block.BlockFace.DOWN) {
                    Repeater rep = (Repeater) neighbor.getBlockData();
                    if (rep.getFacing() == face.getOppositeFace() && neighbor.getBlockPower() > 0) {
                        Block input = neighbor.getRelative(rep.getFacing().getOppositeFace());
                        traceFromHub(input, rep.getFacing().getOppositeFace(), visited, found, depth + 1);
                    }
                } else if (neighbor.getType() == Material.COMPARATOR && face != org.bukkit.block.BlockFace.UP && face != org.bukkit.block.BlockFace.DOWN) {
                    Comparator comp = (Comparator) neighbor.getBlockData();
                    if (comp.getFacing() == face.getOppositeFace() && neighbor.getBlockPower() > 0) {
                        Block input = neighbor.getRelative(comp.getFacing().getOppositeFace());
                        if (input.getType() == Material.CHEST || input.getType() == Material.TRAPPED_CHEST) {
                            addChestUnique(found, input);
                        }
                        traceFromHub(input, comp.getFacing().getOppositeFace(), visited, found, depth + 1);
                    }
                }
            }

            for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                    org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
                Block sameLevel = block.getRelative(face);
                Block above = sameLevel.getRelative(org.bukkit.block.BlockFace.UP);
                Block below = sameLevel.getRelative(org.bukkit.block.BlockFace.DOWN);
                if (above.getType() == Material.REDSTONE_WIRE && above.getBlockPower() > 0) {
                    traceFromHub(above, org.bukkit.block.BlockFace.DOWN, visited, found, depth + 1);
                }
                if (below.getType() == Material.REDSTONE_WIRE && below.getBlockPower() > 0) {
                    traceFromHub(below, org.bukkit.block.BlockFace.UP, visited, found, depth + 1);
                }
            }
        } else if (mat == Material.REPEATER) {
            Repeater rep = (Repeater) block.getBlockData();
            if (rep.getFacing() != fromFace.getOppositeFace()) return;
            Block input = block.getRelative(rep.getFacing().getOppositeFace());
            traceFromHub(input, rep.getFacing().getOppositeFace(), visited, found, depth + 1);
        } else if (mat == Material.COMPARATOR) {
            Comparator comp = (Comparator) block.getBlockData();
            if (comp.getFacing() != fromFace.getOppositeFace()) return;
            Block input = block.getRelative(comp.getFacing().getOppositeFace());
            if (input.getType() == Material.CHEST || input.getType() == Material.TRAPPED_CHEST) {
                addChestUnique(found, input);
            }
            traceFromHub(input, comp.getFacing().getOppositeFace(), visited, found, depth + 1);
        } else if (mat == Material.REDSTONE_BLOCK) {
            for (org.bukkit.block.BlockFace face : org.bukkit.block.BlockFace.values()) {
                if (face == org.bukkit.block.BlockFace.DOWN || face == org.bukkit.block.BlockFace.UP) continue;
                Block neighbor = block.getRelative(face);
                traceFromHub(neighbor, face, visited, found, depth + 1);
            }
        }
    }

    private void addChestUnique(List<Block> found, Block chest) {
        for (Block b : found) {
            if (b.getLocation().equals(chest.getLocation())) return;
        }
        found.add(chest);
    }

    // --- GUI ---

    private static class RedstoneChestGUI implements InventoryHolder {
        private final Inventory inventory;
        RedstoneChestGUI(String title) { this.inventory = Bukkit.createInventory(this, GUI_SIZE, title); }
        @Override public Inventory getInventory() { return inventory; }
    }

    private void openGUI(Player player, Location hubLoc) {
        playerPages.computeIfAbsent(player.getUniqueId(), k -> 0);
        playerCategories.computeIfAbsent(player.getUniqueId(), k -> "Todos");
        player.openInventory(buildGUI(player, hubLoc));
    }

    private Inventory buildGUI(Player player, Location hubLoc) {
        RedstoneChestGUI gui = new RedstoneChestGUI("Cofre de Redstone");
        Inventory inv = gui.getInventory();
        List<Block> linked = findLinkedChests(hubLoc);
        List<GUIEntry> allEntries = collectEntries(linked);
        String filter = playerFilters.getOrDefault(player.getUniqueId(), "").toLowerCase();
        String category = playerCategories.getOrDefault(player.getUniqueId(), "Todos");
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);

        List<GUIEntry> filtered = new ArrayList<>();
        for (GUIEntry e : allEntries) {
            if (!filter.isEmpty() && !matchesFilter(e.material, filter)) continue;
            if (!category.equals("Todos") && !e.category.equals(category)) continue;
            filtered.add(e);
        }
        filtered.sort(java.util.Comparator.comparing(e -> e.sortKey));

        int totalPages = Math.max(1, (filtered.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        if (page >= totalPages) page = totalPages - 1;
        playerPages.put(player.getUniqueId(), page);

        for (int i = 0; i < CATEGORIES.length; i++) {
            ItemStack icon = new ItemStack(CATEGORY_ICONS[i]);
            ItemMeta meta = icon.getItemMeta();
            if (CATEGORIES[i].equals(category)) {
                meta.setDisplayName("§a§l" + CATEGORIES[i] + " §a✔");
                meta.setLore(List.of("§7Click para filtrar", "§a§lCategoria activa"));
            } else {
                meta.setDisplayName("§7§l" + CATEGORIES[i]);
                meta.setLore(List.of("§7Click para filtrar por categoria"));
            }
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        ItemStack searchIcon = new ItemStack(Material.NAME_TAG);
        ItemMeta sMeta = searchIcon.getItemMeta();
        sMeta.setDisplayName("§bBuscar");
        sMeta.setLore(List.of("§7Click para buscar por nombre",
                filter.isEmpty() ? "§7Sin filtro activo" : "§7Filtro: §f" + filter));
        searchIcon.setItemMeta(sMeta);
        inv.setItem(SEARCH_SLOT, searchIcon);

        List<GUIEntry> pageList = new ArrayList<>();
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < filtered.size(); i++) {
            GUIEntry entry = filtered.get(start + i);
            pageList.add(entry);
            ItemStack display = entry.item.clone();
            if (entry.stackable) {
                display.setAmount(Math.min(display.getMaxStackSize(), entry.total));
            } else {
                display.setAmount(1);
            }
            inv.setItem(ITEMS_START + i, display);
        }
        pageEntries.put(player.getUniqueId(), pageList);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta(); gMeta.setDisplayName(" "); glass.setItemMeta(gMeta);
        for (int i = ITEMS_START + ITEMS_PER_PAGE; i < 54; i++) {
            if (i != PREV_SLOT && i != NEXT_SLOT && i != INFO_SLOT && i != CLEAR_SLOT && i != DEPOSIT_SLOT)
                inv.setItem(i, glass.clone());
        }

        ItemStack deposit = new ItemStack(Material.HOPPER);
        ItemMeta dMeta = deposit.getItemMeta();
        dMeta.setDisplayName("§a§lGuardar items");
        dMeta.setLore(List.of("§7Coloca items aqui para enviarlos a los cofres",
                "§7Se organizan automaticamente por tipo"));
        deposit.setItemMeta(dMeta);
        inv.setItem(DEPOSIT_SLOT, deposit);

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pMeta = prev.getItemMeta(); pMeta.setDisplayName("§eAnterior"); prev.setItemMeta(pMeta);
        inv.setItem(PREV_SLOT, prev);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nMeta = next.getItemMeta(); nMeta.setDisplayName("§eSiguiente"); next.setItemMeta(nMeta);
        inv.setItem(NEXT_SLOT, next);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta iMeta = info.getItemMeta();
        iMeta.setDisplayName("§6Pagina " + (page + 1) + " / " + totalPages);
        iMeta.setLore(List.of("§7" + filtered.size() + " tipos en " + linked.size() + " cofres"));
        info.setItemMeta(iMeta);
        inv.setItem(INFO_SLOT, info);

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = clear.getItemMeta();
        cMeta.setDisplayName("§cLimpiar filtros");
        clear.setItemMeta(cMeta);
        inv.setItem(CLEAR_SLOT, clear);

        return inv;
    }

    private List<GUIEntry> collectEntries(List<Block> chests) {
        Map<String, Integer> stackableCounts = new HashMap<>();
        List<ItemStack> nonStackable = new ArrayList<>();
        Set<String> seenChests = new HashSet<>();

        for (Block block : chests) {
            if (!(block.getState() instanceof Chest chestInv)) continue;
            String sig = chestSignature(chestInv);
            if (!seenChests.add(sig)) continue;

            Inventory inv = chestInv.getInventory();
            for (ItemStack stack : inv.getContents()) {
                if (stack == null || stack.getType() == Material.AIR) continue;
                if (stack.getType().getMaxStackSize() > 1) {
                    stackableCounts.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
                } else {
                    nonStackable.add(stack.clone());
                }
            }
        }

        plugin.getLogger().info("[RedstoneChest] Cofres procesados: " + seenChests.size() + " | Tipos stackeables: " + stackableCounts.size() + " | Items no-stackeables: " + nonStackable.size());

        List<GUIEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : stackableCounts.entrySet()) {
            Material mat = Material.getMaterial(e.getKey());
            if (mat == null) continue;
            ItemStack display = new ItemStack(mat);
            ItemMeta meta = display.getItemMeta();
            meta.setLore(List.of("§7Total: §f" + e.getValue(),
                    "§7Click izq: 1 stack §7| §7Click der: mitad"));
            display.setItemMeta(meta);
            entries.add(new GUIEntry(display, mat, getCategory(mat), getSortKey(mat), true, e.getValue(), null));
        }
        for (ItemStack stack : nonStackable) {
            Material mat = stack.getType();
            ItemStack display = stack.clone();
            display.setAmount(1);
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§7Cantidad: 1");
            meta.setLore(lore);
            display.setItemMeta(meta);
            entries.add(new GUIEntry(display, mat, getCategory(mat), getSortKey(mat), false, 1, stack.clone()));
        }
        return entries;
    }

    private String chestSignature(Chest chest) {
        Inventory inv = chest.getInventory();
        if (inv.getHolder() instanceof DoubleChest dc) {
            InventoryHolder left = dc.getLeftSide();
            InventoryHolder right = dc.getRightSide();
            String ls = left instanceof Chest c ? locKey(c.getBlock().getLocation()) : "";
            String rs = right instanceof Chest c2 ? locKey(c2.getBlock().getLocation()) : "";
            if (ls.compareTo(rs) <= 0) return "D:" + ls + "|" + rs;
            return "D:" + rs + "|" + ls;
        }
        return "S:" + locKey(chest.getBlock().getLocation());
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    // --- Categories & Sorting ---

    private String getCategory(Material mat) {
        String n = mat.name();

        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") ||
            n.equals("SHEARS") || n.equals("FLINT_AND_STEEL") || n.equals("BRUSH") || n.equals("FISHING_ROD") ||
            n.equals("SPYGLASS") || n.equals("COMPASS") || n.equals("RECOVERY_COMPASS") || n.equals("CLOCK") ||
            n.equals("LEAD") || n.equals("NAME_TAG") || n.equals("SADDLE") || n.equals("GOAT_HORN") ||
            n.equals("MAP") || n.equals("FILLED_MAP") || n.equals("BUNDLE") || n.equals("CARROT_ON_A_STICK") ||
            n.equals("WARPED_FUNGUS_ON_A_STICK"))
            return "Herramientas";

        if (n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") ||
            n.equals("MACE") || n.equals("SHIELD") || n.equals("ARROW") || n.equals("SPECTRAL_ARROW") ||
            n.equals("TIPPED_ARROW") || n.equals("TNT") || n.equals("TOTEM_OF_UNDYING") ||
            n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") ||
            n.equals("TURTLE_HELMET") || n.equals("ELYTRA"))
            return "Combate";

        if (mat.isEdible() || n.equals("CAKE") || n.equals("HONEY_BOTTLE") || n.equals("PUMPKIN_PIE") ||
            n.equals("GOLDEN_APPLE") || n.equals("ENCHANTED_GOLDEN_APPLE") || n.equals("SUSPICIOUS_STEW") ||
            n.equals("MUSHROOM_STEW") || n.equals("RABBIT_STEW") || n.equals("BEETROOT_SOUP") ||
            n.equals("MILK_BUCKET") || n.equals("GLOW_BERRIES") || n.equals("SWEET_BERRIES") ||
            n.equals("CHORUS_FRUIT") || n.contains("POTION") || n.contains("SPLASH") || n.contains("LINGERING") ||
            n.equals("DRAGON_BREATH"))
            return "Comida";

        if (n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("QUARTZ") ||
            n.equals("IRON_INGOT") || n.equals("GOLD_INGOT") || n.equals("COPPER_INGOT") ||
            n.equals("NETHERITE_INGOT") || n.equals("NETHERITE_SCRAP") ||
            n.equals("RAW_IRON") || n.equals("RAW_GOLD") || n.equals("RAW_COPPER") ||
            n.equals("REDSTONE") || n.equals("GLOWSTONE_DUST") || n.equals("LAPIS_LAZULI") ||
            n.equals("AMETHYST_SHARD") || n.equals("ECHO_SHARD") || n.equals("PRISMARINE_CRYSTALS") ||
            n.equals("PRISMARINE_SHARD") || n.equals("EMERALD") || n.equals("COAL") || n.equals("CHARCOAL") ||
            n.equals("DIAMOND") || n.equals("FLINT") || n.equals("IRON_NUGGET") || n.equals("GOLD_NUGGET") ||
            n.equals("STICK") || n.equals("STRING") || n.equals("LEATHER") || n.equals("RABBIT_HIDE") ||
            n.equals("FEATHER") || n.equals("BONE") || n.equals("BONE_MEAL") || n.equals("GUNPOWDER") ||
            n.equals("SLIME_BALL") || n.equals("SLIME_BLOCK") || n.equals("GHAST_TEAR") ||
            n.equals("BLAZE_ROD") || n.equals("BLAZE_POWDER") || n.equals("MAGMA_CREAM") ||
            n.equals("ENDER_PEARL") || n.equals("ENDER_EYE") || n.equals("SHULKER_SHELL") ||
            n.equals("NETHER_WART") || n.equals("PAPER") || n.equals("BOOK") || n.equals("ENCHANTED_BOOK") ||
            n.equals("WRITABLE_BOOK") || n.equals("WRITTEN_BOOK") || n.equals("KNOWLEDGE_BOOK") ||
            n.equals("EXPERIENCE_BOTTLE") || n.equals("FIRE_CHARGE") || n.equals("SUGAR") ||
            n.equals("EGG") || n.equals("GLASS_BOTTLE") || n.equals("SPIDER_EYE") ||
            n.equals("FERMENTED_SPIDER_EYE") || n.equals("GOLDEN_CARROT") || n.equals("GLISTERING_MELON_SLICE") ||
            n.equals("GHAST_TEAR") || n.equals("NAUTILUS_SHELL") || n.equals("HEART_OF_THE_SEA") ||
            n.equals("SCUTE") || n.equals("PHANTOM_MEMBRANE") || n.equals("HONEYCOMB") ||
            n.equals("HONEY_BOTTLE") || n.equals("WHEAT") || n.equals("WHEAT_SEEDS") ||
            n.equals("BEETROOT_SEEDS") || n.equals("MELON_SEEDS") || n.equals("PUMPKIN_SEEDS") ||
            n.equals("TORCHFLOWER_SEEDS") || n.equals("PITCHER_POD") || n.equals("POTATO") ||
            n.equals("CARROT") || n.equals("BEETROOT") || n.equals("SWEET_BERRIES") || n.equals("GLOW_BERRIES") ||
            n.equals("COCOA_BEANS") || n.equals("NETHER_STAR") || n.equals("NETHERITE_SCRAP") ||
            n.contains("INGOT") || n.contains("NUGGET") || n.contains("SHARD") || n.contains("DUST") ||
            n.contains("BANNER_PATTERN") || n.contains("MUSIC_DISC") || n.contains("DISC_FRAGMENT") ||
            n.contains("ARMOR_TRIM") || n.contains("SMITHING_TEMPLATE") || n.contains("UPGRADE") ||
            n.contains("WIND_CHARGE") || n.contains("BREEZE_ROD") || n.contains("GUSTER"))
            return "Ingredientes";

        if (n.contains("FURNACE") || n.contains("CHEST") || n.contains("BARREL") || n.contains("HOPPER") ||
            n.contains("CRAFTING") || n.contains("ENCHANTING") || n.contains("BREWING") || n.contains("CAULDRON") ||
            n.contains("ANVIL") || n.contains("BEACON") || n.contains("CONDUIT") || n.contains("LECTERN") ||
            n.contains("LOOM") || n.contains("STONECUTTER") || n.contains("GRINDSTONE") || n.contains("SMITHING") ||
            n.contains("CARTOGRAPHY") || n.contains("COMPOSTER") || n.contains("BELL") || n.contains("LANTERN") ||
            n.contains("SHULKER_BOX") || n.contains("REDSTONE") || n.contains("REPEATER") || n.contains("COMPARATOR") ||
            n.contains("OBSERVER") || n.contains("PISTON") || n.contains("DROPPER") || n.contains("DISPENSER") ||
            n.contains("TRIPWIRE") || n.contains("TARGET") || n.contains("DAYLIGHT") || n.contains("NOTE_BLOCK") ||
            n.contains("JUKEBOX") || n.contains("LEVER") || n.contains("RAIL") || n.contains("MINECART") ||
            n.contains("LIGHTNING_ROD") || n.contains("SCULK") || n.contains("CRAFTER") || n.contains("TRIAL_SPAWNER") ||
            n.contains("VAULT") || n.contains("HEAVY_CORE") || n.contains("LODESTONE") || n.contains("RESPAWN_ANCHOR") ||
            n.contains("TINTED_GLASS") || n.contains("COPPER_BULB") || n.contains("COPPER_GRATE") ||
            n.contains("COPPER_DOOR") || n.contains("COPPER_TRAPDOOR") || n.contains("COPPER_CHAIN") ||
            n.contains("COPPER_BARS") || n.contains("COPPER_LANTERN") || n.contains("COPPER_TORCH") ||
            n.contains("COPPER_GOLEM_STATUE") || n.contains("DECORATED_POT") || n.contains("SUSPICIOUS_SAND") ||
            n.contains("SUSPICIOUS_GRAVEL") || n.contains("REINFORCED_DEEPSLATE") || n.contains("SPAWNER") ||
            n.contains("CAMPFIRE") || n.contains("LODESTONE"))
            return "Funcional";

        if (n.contains("ORE") || n.contains("DEEPSLATE") || n.equals("STONE") || n.equals("GRANITE") ||
            n.equals("DIORITE") || n.equals("ANDESITE") || n.equals("BEDROCK") || n.equals("SAND") ||
            n.equals("RED_SAND") || n.equals("GRAVEL") || n.equals("DIRT") || n.equals("COARSE_DIRT") ||
            n.equals("ROOTED_DIRT") || n.equals("MUD") || n.equals("CLAY") || n.equals("PODZOL") ||
            n.equals("MYCELIUM") || n.equals("SOUL_SAND") || n.equals("SOUL_SOIL") || n.equals("GRASS_BLOCK") ||
            n.equals("DIRT_PATH") || n.equals("FARMLAND") || n.equals("SNOW_BLOCK") || n.equals("ICE") ||
            n.equals("PACKED_ICE") || n.equals("BLUE_ICE") || n.equals("POWDER_SNOW") || n.contains("NETHERRACK") ||
            n.equals("END_STONE") || n.equals("BASALT") || n.equals("BLACKSTONE") || n.equals("CALCITE") ||
            n.equals("AMETHYST_BLOCK") || n.equals("BUDDING_AMETHYST") || n.equals("TUFF") ||
            n.equals("DRIPSTONE_BLOCK") || n.equals("POINTED_DRIPSTONE") || n.equals("MOSS_BLOCK") ||
            n.equals("FROGLIGHT") || n.equals("MAGMA_BLOCK") || n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN") ||
            n.equals("GLOWSTONE") || n.contains("GRASS") || n.contains("FERN") || n.contains("FLOWER") ||
            n.contains("MUSHROOM") || n.contains("SEAGRASS") || n.contains("KELP") || n.contains("VINE") ||
            n.contains("CORAL") || n.contains("DEAD_BUSH") || n.contains("COCOA") || n.contains("CACTUS") ||
            n.contains("BAMBOO") || n.contains("NETHER_SPROUTS") || n.contains("WEEPING_VINES") ||
            n.contains("TWISTING_VINES") || n.contains("GLOW_LICHEN") || n.contains("MOSS_CARPET") ||
            n.contains("BIG_DRIPLEAF") || n.contains("SMALL_DRIPLEAF") || n.contains("AZALEA") ||
            n.contains("HANGING_ROOTS") || n.contains("PITCHER") || n.contains("TORCHFLOWER") ||
            n.contains("PINK_PETALS") || n.contains("WEB") || n.contains("COBWEB") || n.contains("FIRE") ||
            n.contains("WHEAT") || n.contains("CARROTS") || n.contains("POTATOES") || n.contains("BEETROOTS"))
            return "Natural";

        if (mat.isBlock()) return "Construccion";

        return "Ingredientes";
    }

    private int getCategoryOrder(String cat) {
        switch (cat) {
            case "Construccion": return 0;
            case "Natural": return 1;
            case "Funcional": return 2;
            case "Herramientas": return 3;
            case "Combate": return 4;
            case "Comida": return 5;
            case "Ingredientes": return 6;
            default: return 7;
        }
    }

    private int getMaterialTier(Material mat) {
        String n = mat.name();
        if (n.startsWith("WOODEN") || n.startsWith("LEATHER") || n.startsWith("OAK_") ||
            n.startsWith("SPRUCE_") || n.startsWith("BIRCH_") || n.startsWith("JUNGLE_") ||
            n.startsWith("ACACIA_") || n.startsWith("DARK_OAK_") || n.startsWith("MANGROVE_") ||
            n.startsWith("CHERRY_") || n.startsWith("CRIMSON_") || n.startsWith("WARPED_") ||
            n.startsWith("BAMBOO_")) return 0;
        if (n.startsWith("STONE") || n.startsWith("COPPER") || n.startsWith("IRON") || n.startsWith("CHAIN")) return 1;
        if (n.startsWith("GOLD") || n.startsWith("GOLDEN")) return 2;
        if (n.startsWith("DIAMOND")) return 3;
        if (n.startsWith("NETHERITE")) return 4;
        return 5;
    }

    private String getSortKey(Material mat) {
        return String.format("%02d-%02d-%s",
                getCategoryOrder(getCategory(mat)), getMaterialTier(mat), mat.name());
    }

    private void refreshGUI(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof RedstoneChestGUI) {
            Location hubLoc = findHubForPlayer(player);
            if (hubLoc != null) {
                Inventory newInv = buildGUI(player, hubLoc);
                player.getOpenInventory().getTopInventory().setContents(newInv.getContents());
            }
        }
    }

    private Location findHubForPlayer(Player player) {
        for (Location loc : hubs) {
            if (loc.getWorld() != player.getWorld()) continue;
            if (loc.distanceSquared(player.getLocation()) < 100) return loc;
        }
        return null;
    }

    // --- Inventory Events ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RedstoneChestGUI)) return;

        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != event.getInventory()) {
            if (event.isShiftClick() && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                handleDeposit(player, event.getCurrentItem(), event.getClickedInventory(), event.getSlot());
            }
            return;
        }

        int slot = event.getSlot();

        for (int i = 0; i < CATEGORIES.length; i++) {
            if (slot == i) {
                event.setCancelled(true);
                playerCategories.put(player.getUniqueId(), CATEGORIES[i]);
                playerPages.put(player.getUniqueId(), 0);
                refreshGUI(player);
                return;
            }
        }

        if (slot == SEARCH_SLOT) {
            event.setCancelled(true);
            searchMode.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§bEscribe en el chat el termino de busqueda. Escribe 'cancelar' para salir.");
            return;
        }
        if (slot == CLEAR_SLOT) {
            event.setCancelled(true);
            playerFilters.put(player.getUniqueId(), "");
            playerCategories.put(player.getUniqueId(), "Todos");
            playerPages.put(player.getUniqueId(), 0);
            refreshGUI(player);
            return;
        }
        if (slot == PREV_SLOT) {
            event.setCancelled(true);
            int page = playerPages.getOrDefault(player.getUniqueId(), 0);
            playerPages.put(player.getUniqueId(), Math.max(0, page - 1));
            refreshGUI(player);
            return;
        }
        if (slot == NEXT_SLOT) {
            event.setCancelled(true);
            int page = playerPages.getOrDefault(player.getUniqueId(), 0);
            playerPages.put(player.getUniqueId(), page + 1);
            refreshGUI(player);
            return;
        }

        if (slot == DEPOSIT_SLOT) {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                ItemStack toDeposit = cursor.clone();
                Location hubLoc = findHubForPlayer(player);
                if (hubLoc != null && depositItem(hubLoc, toDeposit)) {
                    if (toDeposit.getAmount() <= 0) {
                        player.setItemOnCursor(null);
                    } else {
                        cursor.setAmount(toDeposit.getAmount());
                    }
                    player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1.0f, 1.2f);
                } else {
                    player.sendMessage("§cNo hay espacio en los cofres. Amplia el mecanismo.");
                }
                refreshGUI(player);
            }
            return;
        }

        if (slot >= ITEMS_START && slot < ITEMS_START + ITEMS_PER_PAGE) {
            event.setCancelled(true);
            List<GUIEntry> entries = pageEntries.get(player.getUniqueId());
            if (entries == null) return;
            int index = slot - ITEMS_START;
            if (index >= entries.size()) return;
            GUIEntry entry = entries.get(index);

            int amount;
            if (event.isLeftClick()) {
                amount = Math.min(entry.material.getMaxStackSize(), entry.total);
            } else if (event.isRightClick()) {
                amount = Math.max(1, Math.min(entry.material.getMaxStackSize(), (entry.total + 1) / 2));
            } else {
                return;
            }

            Location hubLoc = findHubForPlayer(player);
            if (hubLoc == null) return;
            int withdrawn = withdrawEntry(hubLoc, entry, amount, player);
            if (withdrawn > 0) {
                ItemStack toGive;
                if (entry.stackable) {
                    toGive = new ItemStack(entry.material, withdrawn);
                } else {
                    toGive = entry.withdrawTarget.clone();
                    toGive.setAmount(1);
                }
                returnItemToPlayer(player, toGive);
                refreshGUI(player);
            } else {
                player.sendMessage("§cNo se pudo extraer el item.");
            }
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RedstoneChestGUI) event.setCancelled(true);
    }

    // --- Search via Chat ---

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!searchMode.remove(player.getUniqueId())) return;
        event.setCancelled(true);

        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancelar")) {
            player.sendMessage("§7Busqueda cancelada.");
            return;
        }
        playerFilters.put(player.getUniqueId(), msg);
        playerPages.put(player.getUniqueId(), 0);
        player.sendMessage("§aFiltro aplicado: §f" + msg + "§a. Abre el cofre de redstone para ver resultados.");
    }

    // --- Storage Logic ---

    private int withdrawEntry(Location hubLoc, GUIEntry entry, int amount, Player player) {
        List<Block> chests = findLinkedChests(hubLoc);
        int remaining = amount;

        for (Block block : chests) {
            if (block.getState() instanceof Chest chestInv) {
                Inventory inv = chestInv.getInventory();
                for (int slot = 0; slot < inv.getSize() && remaining > 0; slot++) {
                    ItemStack stack = inv.getItem(slot);
                    if (stack == null || stack.getType() == Material.AIR) continue;
                    if (entry.stackable) {
                        if (stack.getType() == entry.material) {
                            int take = Math.min(remaining, stack.getAmount());
                            stack.setAmount(stack.getAmount() - take);
                            remaining -= take;
                            if (stack.getAmount() <= 0) inv.setItem(slot, null);
                        }
                    } else {
                        if (stack.isSimilar(entry.withdrawTarget)) {
                            inv.setItem(slot, null);
                            remaining -= 1;
                            if (remaining <= 0) break;
                        }
                    }
                }
            }
        }
        return amount - remaining;
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private void handleDeposit(Player player, ItemStack source, Inventory clickedInv, int slot) {
        ItemStack toDeposit = source.clone();
        Location hubLoc = findHubForPlayer(player);
        if (hubLoc == null) return;

        if (depositItem(hubLoc, toDeposit)) {
            if (toDeposit.getAmount() <= 0) {
                clickedInv.setItem(slot, null);
            } else {
                clickedInv.setItem(slot, toDeposit);
            }
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1.0f, 1.2f);
        } else {
            player.sendMessage("§cNo hay espacio en los cofres. Amplia el mecanismo.");
        }
        refreshGUI(player);
    }

    private boolean depositItem(Location hubLoc, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        List<Block> chests = findLinkedChests(hubLoc);
        Set<String> seen = new HashSet<>();

        for (Block block : chests) {
            if (!(block.getState() instanceof Chest chestInv)) continue;
            String sig = chestSignature(chestInv);
            if (!seen.add(sig)) continue;
            Inventory inv = chestInv.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) continue;
                if (stack.getType() == item.getType() && stack.isSimilar(item)
                        && stack.getAmount() < stack.getMaxStackSize()) {
                    int room = stack.getMaxStackSize() - stack.getAmount();
                    int add = Math.min(room, item.getAmount());
                    stack.setAmount(stack.getAmount() + add);
                    item.setAmount(item.getAmount() - add);
                    if (item.getAmount() <= 0) return true;
                }
            }
        }

        Set<String> seen2 = new HashSet<>();
        for (Block block : chests) {
            if (!(block.getState() instanceof Chest chestInv)) continue;
            String sig = chestSignature(chestInv);
            if (!seen2.add(sig)) continue;
            Inventory inv = chestInv.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) {
                    inv.setItem(slot, item.clone());
                    item.setAmount(0);
                    return true;
                }
            }
        }
        return false;
    }
}
