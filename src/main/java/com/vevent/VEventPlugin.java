package com.vevent;

import com.vevent.commands.CommandManager;
import com.vevent.listeners.ArenaListener;
import com.vevent.listeners.GlobalMobListener;
import com.vevent.managers.ActiveEventManager;
import com.vevent.managers.DisenchantManager;
import com.vevent.managers.EventSchedulerManager;
import com.vevent.managers.LookInfoManager;
import com.vevent.managers.MissionManager;
import com.vevent.managers.RedstoneChestManager;
import com.vevent.managers.TpCarpetManager;
import com.vevent.utils.LLMClient;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class VEventPlugin extends JavaPlugin {
    private LLMClient llmClient;
    private EventSchedulerManager schedulerManager;
    private ActiveEventManager activeEventManager;
    private DisenchantManager disenchantManager;
    private RedstoneChestManager redstoneChestManager;
    private LookInfoManager lookInfoManager;
    private MissionManager missionManager;
    private TpCarpetManager tpCarpetManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        getLogger().info("Data folder: " + getDataFolder().getAbsolutePath());
        getLogger().info("Config file: " + new java.io.File(getDataFolder(), "config.yml").getAbsolutePath());

        String llmEndpoint = getConfig().getString("llm-generation.endpoint");
        String llmModel = getConfig().getString("llm-generation.model");
        String llmApiKey = getConfig().getString("llm-generation.api-key");

        getLogger().info("Config leído — model=" + llmModel + ", api-key=" + maskKey(llmApiKey)
                + ", tiers=" + getConfig().getConfigurationSection("llm-generation.tiers").getKeys(false));

        if (llmModel == null || llmModel.isEmpty()) {
            getLogger().warning("llm-generation.model no configurado en config.yml. Las peticiones a la IA fallarán.");
        }
        if (llmApiKey == null || llmApiKey.isEmpty() || llmApiKey.equalsIgnoreCase("PON_TU_API_KEY_AQUI")) {
            getLogger().warning("llm-generation.api-key no configurada o usa el valor por defecto. Las peticiones a Gemini fallarán.");
        }

        this.llmClient = new LLMClient(this, llmEndpoint, llmModel, llmApiKey);
        this.activeEventManager = new ActiveEventManager(this);
        this.schedulerManager = new EventSchedulerManager(this, llmClient, activeEventManager);
        this.disenchantManager = new DisenchantManager(this);
        this.redstoneChestManager = new RedstoneChestManager(this);
        this.lookInfoManager = new LookInfoManager(this);
        this.missionManager = new MissionManager(this);
        this.tpCarpetManager = new TpCarpetManager(this);

        CommandManager cmdManager = new CommandManager(this);
        getCommand("vevent").setExecutor(cmdManager);
        getCommand("vevent").setTabCompleter(cmdManager);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        new GlobalMobListener(this);
        registerReturnScrollRecipe();
        createReturnScrollDatapack();

        getLogger().info("vEvent Drops Plugin activado correctamente.");
    }

    private void createReturnScrollDatapack() {
        java.io.File worldFolder = getServer().getWorlds().get(0).getWorldFolder();
        java.io.File datapackFolder = new java.io.File(worldFolder, "datapacks/vevent_scroll");
        java.io.File dataFolder = new java.io.File(datapackFolder, "data/vevent/item");

        if (dataFolder.exists()) return;

        dataFolder.mkdirs();

        String itemJson = "{\n"
                + "  \"model\": {\n"
                + "    \"type\": \"minecraft:model\",\n"
                + "    \"model\": \"minecraft:item/paper\"\n"
                + "  },\n"
                + "  \"components\": {\n"
                + "    \"minecraft:item_name\": \"{\\\"text\\\":\\\"Pergamino de Retorno\\\",\\\"color\\\":\\\"light_purple\\\",\\\"bold\\\":true}\",\n"
                + "    \"minecraft:lore\": [\n"
                + "      \"{\\\"text\\\":\\\"Click derecho para volver a tu cama\\\",\\\"color\\\":\\\"gray\\\"}\",\n"
                + "      \"{\\\"text\\\":\\\"Un solo uso\\\",\\\"color\\\":\\\"gray\\\"}\"\n"
                + "    ],\n"
                + "    \"minecraft:custom_data\": \"{vevent_crafted_scroll:true}\"\n"
                + "  }\n"
                + "}";

        String mcmeta = "{\"pack\":{\"description\":\"vEvent Drops - Return Scroll\",\"pack_format\":46}}";

        try {
            java.nio.file.Files.writeString(new java.io.File(dataFolder, "return_scroll.json").toPath(), itemJson);
            java.nio.file.Files.writeString(new java.io.File(datapackFolder, "pack.mcmeta").toPath(), mcmeta);
            getLogger().info("Datapack 'vevent_scroll' creado en " + datapackFolder.getAbsolutePath());
            getLogger().info("Ejecuta /minecraft:reload para activarlo. Luego: /give @p vevent:return_scroll");
        } catch (Exception e) {
            getLogger().warning("No se pudo crear el datapack: " + e.getMessage());
        }
    }

    private void registerReturnScrollRecipe() {
        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName("§d§lPergamino de Retorno");
        meta.setLore(java.util.List.of("§7Click derecho para volver a tu cama", "§7Un solo uso"));
        NamespacedKey craftedKey = new NamespacedKey(this, "vevent_crafted_scroll");
        meta.getPersistentDataContainer().set(craftedKey, PersistentDataType.BOOLEAN, true);
        result.setItemMeta(meta);

        NamespacedKey recipeKey = new NamespacedKey(this, "return_scroll_craft");

        // Horizontal cross + ender pearls in corners
        // E P E
        // P C P
        // E P E
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape("CPC", "PEP", "CPC");
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('P', Material.PAPER);
        recipe.setIngredient('C', new RecipeChoice.MaterialChoice(
                Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED, Material.LIGHT_BLUE_BED,
                Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
                Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED,
                Material.BROWN_BED, Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED
        ));

        Bukkit.addRecipe(recipe);
        getLogger().info("Receta de Pergamino de Retorno registrada.");
    }

    @Override
    public void onDisable() {
        if (activeEventManager != null && activeEventManager.isEventInProgress()) {
            activeEventManager.endEvent("Apagado del servidor");
        }
        if (lookInfoManager != null) {
            lookInfoManager.stop();
        }
        if (disenchantManager != null) {
            disenchantManager.stop();
        }
        if (redstoneChestManager != null) {
            redstoneChestManager.stop();
        }
        if (tpCarpetManager != null) {
            tpCarpetManager.stop();
        }
    }

    public LLMClient getLlmClient() { return llmClient; }
    public EventSchedulerManager getSchedulerManager() { return schedulerManager; }
    public ActiveEventManager getActiveEventManager() { return activeEventManager; }
    public MissionManager getMissionManager() { return missionManager; }
    public TpCarpetManager getTpCarpetManager() { return tpCarpetManager; }

    public void reloadPluginConfig() {
        reloadConfig();
        String model = getConfig().getString("llm-generation.model");
        String apiKey = getConfig().getString("llm-generation.api-key");
        getLogger().info("Config recargado — model=" + model + ", api-key=" + maskKey(apiKey));
        if (llmClient != null) {
            llmClient.updateConfig(model, apiKey);
        }
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "VACIA";
        if (key.length() <= 8) return key.substring(0, 3) + "***";
        return key.substring(0, 6) + "..." + key.substring(key.length() - 4);
    }
}