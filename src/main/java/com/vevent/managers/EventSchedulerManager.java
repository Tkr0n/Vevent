package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;
import com.vevent.utils.LLMClient;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class EventSchedulerManager {
    private final VEventPlugin plugin;
    private final LLMClient llmClient;
    private final ActiveEventManager activeEventManager;
    private final List<Instant> scheduledEvents;
    private BukkitTask clockTask;
    private LootProfile cachedLootProfile;
    private final Random random;

    public EventSchedulerManager(VEventPlugin plugin, LLMClient llmClient, ActiveEventManager activeEventManager) {
        this.plugin = plugin;
        this.llmClient = llmClient;
        this.activeEventManager = activeEventManager;
        this.scheduledEvents = new ArrayList<>();
        this.random = new Random();
        startInternalClock();
    }

    public void generateSchedule(int windowHours, int eventCount, int cooldownMins) {
        scheduledEvents.clear();
        int windowMins = windowHours * 60;
        int totalCooldownNeeded = (eventCount - 1) * cooldownMins;
        
        if (totalCooldownNeeded > windowMins) return;
        
        int slackMins = windowMins - totalCooldownNeeded;
        List<Integer> randomSlacks = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) randomSlacks.add(random.nextInt(slackMins + 1));
        Collections.sort(randomSlacks);

        Instant now = Instant.now();
        int accumulatedCooldown = 0;

        for (int i = 0; i < eventCount; i++) {
            int eventMinute = randomSlacks.get(i) + accumulatedCooldown;
            Instant eventTime = now.plus(eventMinute, ChronoUnit.MINUTES);
            scheduledEvents.add(eventTime);
            accumulatedCooldown += cooldownMins;
            plugin.getLogger().info("Evento agendado para: " + eventTime);
        }
    }

    private void startInternalClock() {
        if (clockTask != null) clockTask.cancel();
        clockTask = new BukkitRunnable() {
            @Override
            public void run() { tickClock(); }
        }.runTaskTimer(plugin, 1200L, 1200L); // 1 minuto
    }

    private void tickClock() {
        if (scheduledEvents.isEmpty()) return;
        Instant now = Instant.now();
        List<Instant> toRemove = new ArrayList<>();

        for (Instant eventTime : scheduledEvents) {
            long minutesLeft = ChronoUnit.MINUTES.between(now, eventTime);
            if (minutesLeft == 60) {
                Bukkit.broadcastMessage("§e¡Un evento inusual caerá en 60 minutos!");
                prepareEventWithAI("titan");
            } else if (minutesLeft == 30 || minutesLeft == 15) {
                Bukkit.broadcastMessage("§eFaltan " + minutesLeft + " minutos para el botín.");
            } else if (minutesLeft == 5) {
                Bukkit.broadcastMessage("§c¡Solo 5 minutos! Preparen sus armas.");
            } else if (minutesLeft <= 0) {
                startEvent();
                toRemove.add(eventTime);
            }
        }
        scheduledEvents.removeAll(toRemove);
    }

    private void prepareEventWithAI(String tier) {
        String rules = plugin.getConfig().getString("llm-generation.tiers." + tier + ".prompt-rules", "");
        llmClient.fetchEventDataAsync(tier, rules).thenAccept(lootProfile -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lootProfile != null) this.cachedLootProfile = lootProfile;
            });
        });
    }

    private void startEvent() {
        Bukkit.broadcastMessage("§a¡El evento ha comenzado! Usen /vevent accept.");
        activeEventManager.openInvitations(cachedLootProfile);
        this.cachedLootProfile = null;
    }
}