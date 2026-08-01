package com.vevent;

import com.vevent.commands.CommandManager;
import com.vevent.listeners.ArenaListener;
import com.vevent.managers.ActiveEventManager;
import com.vevent.managers.EventSchedulerManager;
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

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        String llmEndpoint = getConfig().getString("llm-generation.endpoint");
        String llmModel = getConfig().getString("llm-generation.model");
        String llmApiKey = getConfig().getString("llm-generation.api-key");

        if (llmModel == null || llmModel.isEmpty()) {
            getLogger().warning("llm-generation.model no configurado en config.yml. Las peticiones a la IA fallarán.");
        }
        if (llmApiKey == null || llmApiKey.isEmpty() || llmApiKey.equalsIgnoreCase("PON_TU_API_KEY_AQUI")) {
            getLogger().warning("llm-generation.api-key no configurada o usa el valor por defecto. Las peticiones a Gemini fallarán.");
        }

        this.llmClient = new LLMClient(this, llmEndpoint, llmModel, llmApiKey);
        this.activeEventManager = new ActiveEventManager(this);
        this.schedulerManager = new EventSchedulerManager(this, llmClient, activeEventManager);

        CommandManager cmdManager = new CommandManager(this);
        getCommand("vevent").setExecutor(cmdManager);
        getCommand("vevent").setTabCompleter(cmdManager);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        registerReturnScrollRecipe();

        getLogger().info("vEvent Drops Plugin activado correctamente.");
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
        recipe.shape("EPE", "PCP", "EPE");
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
    }

    public LLMClient getLlmClient() { return llmClient; }
    public EventSchedulerManager getSchedulerManager() { return schedulerManager; }
    public ActiveEventManager getActiveEventManager() { return activeEventManager; }
}