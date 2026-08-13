package com.vevent.managers;

import com.vevent.VEventPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DisenchantManager implements Listener {

    private final VEventPlugin plugin;
    private final NamespacedKey tableTag;
    private final NamespacedKey recipeKey;
    private final NamespacedKey optionEnchKey;
    private final NamespacedKey optionLevelKey;
    private final Set<Location> tables = new HashSet<>();
    private final File saveFile;

    private static final String GUI_TITLE = "Mesa de Desvinculacion";
    private static final int INPUT_SLOT = 10;
    private static final int BOOK_SLOT = 15;
    private static final int LAPIS_SLOT = 16;
    private static final int[] OPTION_SLOTS = {20, 22, 24};
    private static final int XP_SLOT = 25;
    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            11, 12, 13, 14, 17, 18, 19, 21, 23, 26};

    public DisenchantManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.tableTag = new NamespacedKey(plugin, "vevent_disenchant_table");
        this.recipeKey = new NamespacedKey(plugin, "disenchant_table_craft");
        this.optionEnchKey = new NamespacedKey(plugin, "vevent_de_ench");
        this.optionLevelKey = new NamespacedKey(plugin, "vevent_de_level");
        this.saveFile = new File(plugin.getDataFolder(), "disenchant_tables.yml");
        loadTables();
        registerRecipe();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("DisenchantManager inicializado. " + tables.size() + " mesas trackeadas.");
    }

    // --- Persistence ---

    private void loadTables() {
        if (!saveFile.exists()) {
            plugin.getLogger().info("[Disenchant] No hay archivo de mesas. 0 trackeadas.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        List<String> entries = new ArrayList<>();
        if (cfg.isList("tables")) {
            entries.addAll(cfg.getStringList("tables"));
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
                tables.add(new Location(world, x, y, z));
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Disenchant] Mesas cargadas: " + tables.size()
                + (unresolved.isEmpty() ? "" : " | mundos sin resolver: " + unresolved.size()));
        if (!unresolved.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::loadTables, 100L);
        }
    }

    public void stop() {
        saveTables();
    }

    private void saveTables() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<String> entries = new ArrayList<>();
        for (Location loc : tables) {
            if (loc.getWorld() == null) continue;
            entries.add(loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ());
        }
        cfg.set("tables", entries);
        try {
            cfg.save(saveFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar disenchant_tables.yml: " + e.getMessage());
        }
    }

    // --- Recipe ---

    private ItemStack createTableItem() {
        ItemStack table = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = table.getItemMeta();
        meta.setDisplayName("§5§lMesa de Desvinculacion");
        meta.setLore(List.of("§7Colocala para desvincular encantamientos",
                "§7Item encantado + libro + lapislazuli"));
        meta.getPersistentDataContainer().set(tableTag, PersistentDataType.BOOLEAN, true);
        table.setItemMeta(meta);
        return table;
    }

    private void registerRecipe() {
        ItemStack result = createTableItem();
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("BOB", "OEO", "BOB");
        recipe.setIngredient('B', Material.BOOK);
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('E', Material.ENCHANTING_TABLE);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Receta de Mesa de Desvinculacion registrada.");
    }

    // --- Block events ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(recipeKey);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.ENCHANTING_TABLE) return;
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(tableTag, PersistentDataType.BOOLEAN)) {
            tables.add(event.getBlock().getLocation());
            saveTables();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.ENCHANTING_TABLE) return;
        Location loc = event.getBlock().getLocation();
        if (tables.remove(loc)) {
            saveTables();
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(loc, createTableItem());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) return;
        if (!tables.contains(block.getLocation())) return;

        if (playerHasItemInHandThatOpensGui(event.getPlayer())) return;

        event.setCancelled(true);
        openGUI(event.getPlayer());
    }

    private boolean playerHasItemInHandThatOpensGui(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isBlock() && item.getType() != Material.AIR) {
            return true;
        }
        return false;
    }

    // --- GUI ---

    private static class DisenchantGUI implements InventoryHolder {
        private final Inventory inventory;

        DisenchantGUI() {
            this.inventory = Bukkit.createInventory(this, 27, GUI_TITLE);
            ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
            for (int slot : BORDER_SLOTS) {
                inventory.setItem(slot, glass.clone());
            }

            ItemStack info = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta infoMeta = info.getItemMeta();
            infoMeta.setDisplayName("§eCoste de Experiencia");
            infoMeta.setLore(List.of("§7El coste es §b3x §7el coste vanilla",
                    "§7+ §9Lapislazuli §7por extraccion"));
            info.setItemMeta(infoMeta);
            inventory.setItem(XP_SLOT, info);
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private void openGUI(Player player) {
        DisenchantGUI gui = new DisenchantGUI();
        player.openInventory(gui.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DisenchantGUI)) return;

        if (event.getClickedInventory() != event.getInventory()) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        Inventory inv = event.getInventory();
        int slot = event.getSlot();

        if (slot == INPUT_SLOT || slot == BOOK_SLOT || slot == LAPIS_SLOT) {
            ItemStack cursor = event.getCursor();
            ItemStack current = inv.getItem(slot);

            if (current != null) {
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (!isValidForSlot(slot, cursor)) return;
                    player.setItemOnCursor(current);
                } else {
                    player.setItemOnCursor(current);
                    inv.setItem(slot, null);
                    updateOffers(inv);
                    return;
                }
            }

            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!isValidForSlot(slot, cursor)) return;
                inv.setItem(slot, cursor.clone());
                player.setItemOnCursor(null);
            } else {
                inv.setItem(slot, null);
            }

            updateOffers(inv);
            return;
        }

        for (int i = 0; i < OPTION_SLOTS.length; i++) {
            if (slot == OPTION_SLOTS[i]) {
                handleDisenchant(player, inv, i);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof DisenchantGUI)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DisenchantGUI)) return;

        Inventory inv = event.getInventory();
        Player player = (Player) event.getPlayer();
        Location loc = player.getLocation();

        ItemStack input = inv.getItem(INPUT_SLOT);
        ItemStack book = inv.getItem(BOOK_SLOT);
        ItemStack lapis = inv.getItem(LAPIS_SLOT);

        if (input != null) returnItem(player, input, loc);
        if (book != null) returnItem(player, book, loc);
        if (lapis != null) returnItem(player, lapis, loc);
    }

    private void returnItem(Player player, ItemStack item, Location loc) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(loc, stack);
            }
        }
    }

    private boolean isValidForSlot(int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        if (slot == BOOK_SLOT) return item.getType() == Material.BOOK;
        if (slot == LAPIS_SLOT) return item.getType() == Material.LAPIS_LAZULI;
        if (slot == INPUT_SLOT) return hasAnyEnchantments(item);
        return false;
    }

    // --- Disenchant logic ---

    private void updateOffers(Inventory inv) {
        for (int i : OPTION_SLOTS) inv.setItem(i, null);

        ItemStack input = inv.getItem(INPUT_SLOT);
        if (input == null) return;

        Map<Enchantment, Integer> enchants = getAllEnchantments(input);
        enchants.entrySet().removeIf(e -> e.getKey().isCursed());
        if (enchants.isEmpty()) return;

        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(enchants.entrySet());
        Collections.shuffle(entries);

        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            Enchantment ench = entries.get(i).getKey();
            int level = entries.get(i).getValue();
            int cost = level * getEnchantWeight(ench) * 3;

            ItemStack option = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = option.getItemMeta();
            meta.setDisplayName("§e" + formatEnchantName(ench) + " " + toRoman(level));
            meta.setLore(List.of("§7Coste: §b" + cost + " niveles §7+ §91 lapislazuli",
                    "§7§oClick para extraer"));
            meta.getPersistentDataContainer().set(optionEnchKey, PersistentDataType.STRING, ench.getKey().toString());
            meta.getPersistentDataContainer().set(optionLevelKey, PersistentDataType.INTEGER, level);
            option.setItemMeta(meta);
            inv.setItem(OPTION_SLOTS[i], option);
        }
    }

    private void handleDisenchant(Player player, Inventory inv, int optionIndex) {
        ItemStack optionItem = inv.getItem(OPTION_SLOTS[optionIndex]);
        if (optionItem == null) {
            player.sendMessage("§cEsa opcion no esta disponible.");
            return;
        }

        ItemStack input = inv.getItem(INPUT_SLOT);
        ItemStack bookSlot = inv.getItem(BOOK_SLOT);
        ItemStack lapisSlot = inv.getItem(LAPIS_SLOT);
        if (input == null || bookSlot == null || bookSlot.getType() != Material.BOOK
                || lapisSlot == null || lapisSlot.getType() != Material.LAPIS_LAZULI) {
            player.sendMessage("§cColoca un item encantado, un libro normal y lapislazuli.");
            return;
        }

        ItemMeta optMeta = optionItem.getItemMeta();
        String enchKeyStr = optMeta.getPersistentDataContainer().get(optionEnchKey, PersistentDataType.STRING);
        Integer enchLevel = optMeta.getPersistentDataContainer().get(optionLevelKey, PersistentDataType.INTEGER);
        if (enchKeyStr == null || enchLevel == null) return;

        NamespacedKey nk = NamespacedKey.fromString(enchKeyStr);
        if (nk == null) return;
        Enchantment ench = Registry.ENCHANTMENT.get(nk);
        if (ench == null) return;

        int cost = enchLevel * getEnchantWeight(ench) * 3;
        if (player.getLevel() < cost) {
            player.sendMessage("§cNo tienes suficiente experiencia. Necesitas §b" + cost + " §cniveles.");
            return;
        }

        ItemMeta inputMeta = input.getItemMeta();
        if (inputMeta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storage = (EnchantmentStorageMeta) inputMeta;
            if (!storage.hasStoredEnchant(ench)) {
                player.sendMessage("§cEl encantamiento ya no esta en el item.");
                updateOffers(inv);
                return;
            }
            storage.removeStoredEnchant(ench);
        } else {
            if (!inputMeta.hasEnchant(ench)) {
                player.sendMessage("§cEl encantamiento ya no esta en el item.");
                updateOffers(inv);
                return;
            }
            inputMeta.removeEnchant(ench);
        }
        input.setItemMeta(inputMeta);

        if (!hasAnyEnchantments(input)) {
            inv.setItem(INPUT_SLOT, null);
            player.sendMessage("§aTodos los encantamientos han sido extraidos. El item vuelve limpio.");
        }

        ItemStack result = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) result.getItemMeta();
        bookMeta.addStoredEnchant(ench, enchLevel, true);
        result.setItemMeta(bookMeta);

        int bookAmount = bookSlot.getAmount();
        if (bookAmount <= 1) {
            inv.setItem(BOOK_SLOT, null);
        } else {
            bookSlot.setAmount(bookAmount - 1);
        }

        int lapisAmount = lapisSlot.getAmount();
        if (lapisAmount <= 1) {
            inv.setItem(LAPIS_SLOT, null);
        } else {
            lapisSlot.setAmount(lapisAmount - 1);
        }

        returnItem(player, result, player.getLocation());

        player.setLevel(player.getLevel() - cost);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.3f);

        updateOffers(inv);
    }

    // --- Utility methods ---

    private Map<Enchantment, Integer> getAllEnchantments(ItemStack item) {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        if (item == null) return enchants;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return enchants;
        if (meta instanceof EnchantmentStorageMeta) {
            enchants.putAll(((EnchantmentStorageMeta) meta).getStoredEnchants());
        } else {
            enchants.putAll(meta.getEnchants());
        }
        return enchants;
    }

    private boolean hasAnyEnchantments(ItemStack item) {
        return !getAllEnchantments(item).isEmpty();
    }

    private int getEnchantWeight(Enchantment ench) {
        return switch (ench.getKey().getKey()) {
            case "mending", "frost_walker", "soul_speed", "swift_sneak",
                 "wind_burst", "breach", "density" -> 4;
            case "silk_touch", "infinity", "channeling", "multishot",
                 "piercing", "loyalty" -> 3;
            case "fortune", "looting", "knockback", "punch", "flame",
                 "fire_aspect", "aqua_affinity", "respiration",
                 "depth_strider", "thorns", "impaling", "riptide",
                 "quick_charge" -> 2;
            default -> 1;
        };
    }

    private String formatEnchantName(Enchantment ench) {
        String key = ench.getKey().getKey();
        StringBuilder sb = new StringBuilder();
        for (String part : key.split("_")) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

}
