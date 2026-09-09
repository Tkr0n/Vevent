package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.LootProfile;
import com.vevent.utils.LLMClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private String cachedTier;
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
                String tier = selectTier();
                String displayName = getTierDisplayName(tier);
                Bukkit.broadcastMessage("§e¡Un evento " + displayName + " §ecaerá en 60 minutos!");
                prepareEventWithAI(tier);
            } else if (minutesLeft == 30 || minutesLeft == 15) {
                String displayName = getTierDisplayName(cachedTier);
                Bukkit.broadcastMessage("§eFaltan " + minutesLeft + " minutos para el evento " + displayName + ".");
            } else if (minutesLeft == 5) {
                String displayName = getTierDisplayName(cachedTier);
                Bukkit.broadcastMessage("§c¡Solo 5 minutos para el evento " + displayName + "§c! Preparen sus armas.");
            } else if (minutesLeft <= 0) {
                startEvent();
                toRemove.add(eventTime);
            }
        }
        scheduledEvents.removeAll(toRemove);
    }

    public void startEventNow() {
        startEventNow(null);
    }

    public void startEventNow(String forcedTier) {
        String tier = (forcedTier != null && !forcedTier.isEmpty()) ? forcedTier : selectTier();
        this.cachedTier = tier;
        plugin.getLogger().info("[Scheduler] Iniciando evento manualmente con tier '" + tier + "'...");
        String rules = plugin.getConfig().getString("llm-generation.tiers." + tier + ".prompt-rules", "");
        String displayName = getTierDisplayName(tier);
        llmClient.fetchEventDataAsync(tier, rules).thenAccept(lootProfile -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lootProfile != null) {
                    this.cachedLootProfile = lootProfile;
                    plugin.getLogger().info("[Scheduler] Botín generado por IA para evento manual: " + lootProfile.getTier());
                } else {
                    plugin.getLogger().warning("[Scheduler] La IA no generó botín. Iniciando evento sin perfil de botín.");
                }
                Bukkit.broadcastMessage("§a¡Evento " + displayName + " ha comenzado! Usen /vevent accept.");
                activeEventManager.openInvitations(cachedLootProfile, tier);
                this.cachedLootProfile = null;
            });
        });
    }

    private void prepareEventWithAI(String tier) {
        this.cachedTier = tier;
        String rules = plugin.getConfig().getString("llm-generation.tiers." + tier + ".prompt-rules", "");
        plugin.getLogger().info("[Scheduler] Preparando evento con IA para tier '" + tier + "'");
        llmClient.fetchEventDataAsync(tier, rules).thenAccept(lootProfile -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (lootProfile != null) {
                    this.cachedLootProfile = lootProfile;
                    plugin.getLogger().info("[Scheduler] Botín cacheado para tier '" + lootProfile.getTier() + "'");
                } else {
                    plugin.getLogger().warning("[Scheduler] La IA no devolvió botín para tier '" + tier + "'");
                }
            });
        });
    }

    private String selectTier() {
        double avgScore = calculateAveragePlayerPower();
        double easyChance, mediumChance, hardcoreChance;
        if (avgScore < 8) { easyChance = 0.80; mediumChance = 0.17; hardcoreChance = 0.03; }
        else if (avgScore < 14) { easyChance = 0.50; mediumChance = 0.40; hardcoreChance = 0.10; }
        else if (avgScore < 20) { easyChance = 0.20; mediumChance = 0.55; hardcoreChance = 0.25; }
        else if (avgScore < 28) { easyChance = 0.10; mediumChance = 0.45; hardcoreChance = 0.45; }
        else { easyChance = 0.05; mediumChance = 0.35; hardcoreChance = 0.60; }

        double roll = random.nextDouble();
        String tier;
        if (roll < easyChance) tier = "easy";
        else if (roll < easyChance + mediumChance) tier = "medium";
        else tier = "hardcore";

        plugin.getLogger().info("[Scheduler] Tier seleccionado: " + tier + " (score: " + String.format("%.1f", avgScore)
                + ", easy:" + String.format("%.0f", easyChance * 100) + "% medium:" + String.format("%.0f", mediumChance * 100) + "% hardcore:" + String.format("%.0f", hardcoreChance * 100) + "%)");
        return tier;
    }

    private double calculateAveragePlayerPower() {
        double totalScore = 0;
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            double score = 0;
            score += Math.min(p.getLevel(), 50) * 0.3;
            for (ItemStack armor : p.getInventory().getArmorContents()) {
                if (armor == null) continue;
                String typeName = armor.getType().name();
                if (typeName.contains("NETHERITE")) score += 7;
                else if (typeName.contains("DIAMOND")) score += 5;
                else if (typeName.contains("IRON")) score += 3;
                else if (typeName.contains("CHAINMAIL")) score += 2;
                else if (typeName.contains("LEATHER") || typeName.contains("GOLDEN")) score += 1;
            }
            totalScore += score;
            count++;
        }
        return count == 0 ? 5 : totalScore / count;
    }

    private void startEvent() {
        String displayName = getTierDisplayName(cachedTier);
        Bukkit.broadcastMessage("§a¡Evento " + displayName + " ha comenzado! Usen /vevent accept.");
        activeEventManager.openInvitations(cachedLootProfile, cachedTier);
        this.cachedLootProfile = null;
        this.cachedTier = null;
    }

    private String getTierDisplayName(String tier) {
        if (tier == null) return "";
        switch (tier) {
            case "easy": return "§a[Fácil]";
            case "medium": return "§e[Medio]";
            case "hardcore": return "§c[Hardcore]";
            default: return "[" + tier + "]";
        }
    }
}