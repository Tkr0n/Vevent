package com.vevent;

import com.vevent.commands.CommandManager;
import com.vevent.listeners.ArenaListener;
import com.vevent.managers.ActiveEventManager;
import com.vevent.managers.EventSchedulerManager;
import com.vevent.utils.LLMClient;
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

        getLogger().info("vEvent Drops Plugin activado correctamente.");
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