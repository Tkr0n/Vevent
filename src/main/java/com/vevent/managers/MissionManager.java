package com.vevent.managers;

import com.vevent.VEventPlugin;
import com.vevent.models.MissionProfile;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MissionManager implements Listener {

    private final VEventPlugin plugin;
    private final File saveFile;
    private static final long MISSION_DURATION_MS = 24L * 60 * 60 * 1000;

    private MissionProfile pendingProposal;
    private String pendingTier;
    private MissionProfile activeMission;
    private String activeTier;
    private long expiryTime;

    private final Map<UUID, Integer> playerProgress = new ConcurrentHashMap<>();
    private final Set<UUID> completedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> touchedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> penaltyPending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> penaltyServed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> countdownLeft = new ConcurrentHashMap<>();
    private final Map<Player, Scoreboard> previousScoreboards = new ConcurrentHashMap<>();

    private BukkitRunnable tickTask;

    public MissionManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "missions.yml");
        loadState();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTickTask();
        plugin.getLogger().info("MissionManager inicializado. Mision activa: " + (activeMission != null));
    }

    // --- Public API ---

    public void requestMission(String type, String tier) {
        if (activeMission != null) {
            Bukkit.broadcastMessage("§cYa hay una mision activa. Espera a que expire para pedir otra.");
            return;
        }
        if (pendingProposal != null) {
            Bukkit.broadcastMessage("§cYa hay una propuesta pendiente. Usa /vevent acceptmission o rejectmission.");
            return;
        }
        String rules = getTierRules(tier);
        plugin.getLlmClient().fetchMissionAsync(type, tier, rules).thenAccept(profile -> {
            if (profile == null || profile.getObjective() == null) {
                Bukkit.broadcastMessage("§cLa IA no pudo generar la mision. Intenta de nuevo.");
                return;
            }
            pendingProposal = profile;
            pendingTier = tier;
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("§6§l════════ PROPUESTA DE MISION ════════");
            Bukkit.broadcastMessage("§7Tipo: §f" + profile.getType() + " §7| §7Tier: §f" + tier);
            Bukkit.broadcastMessage("§7Objetivo: §f" + formatMaterial(profile.getObjective()));
            Bukkit.broadcastMessage("§7Cuota por jugador: §f" + profile.getQuantity());
            if (profile.getDescription() != null) {
                Bukkit.broadcastMessage("§7Descripcion: §f" + profile.getDescription());
            }
            Bukkit.broadcastMessage("§7Recompensa: §f" + formatReward(profile));
            Bukkit.broadcastMessage("§6§l══════════════════════════════════════");
            Bukkit.broadcastMessage("§ePara aceptar: §f/vevent acceptmission §e| §cPara rechazar: §f/vevent rejectmission");
        });
    }

    public void acceptMission() {
        if (pendingProposal == null) {
            Bukkit.broadcastMessage("§cNo hay ninguna propuesta pendiente.");
            return;
        }
        MissionProfile profile = pendingProposal;
        Material objective = Material.matchMaterial(profile.getObjective().toUpperCase());
        if (objective == null || objective == Material.AIR) {
            Bukkit.broadcastMessage("§cEl objetivo '" + profile.getObjective() + "' no es un material valido. Rechaza y pide otra.");
            return;
        }

        int maxXp;
        switch (pendingTier == null ? "" : pendingTier.toLowerCase()) {
            case "easy": maxXp = 3; break;
            case "medium": maxXp = 10; break;
            case "hardcore": maxXp = 15; break;
            default: maxXp = 10;
        }
        if (profile.getXpReward() > maxXp) {
            profile.setXpReward(maxXp);
        }

        activeMission = profile;
        activeTier = pendingTier;
        pendingProposal = null;
        expiryTime = System.currentTimeMillis() + MISSION_DURATION_MS;
        playerProgress.clear();
        completedPlayers.clear();
        touchedPlayers.clear();
        penaltyServed.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            touchedPlayers.add(p.getUniqueId());
            assignScoreboard(p);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l¡MISION ACTIVA! (" + pendingTier + ")");
        Bukkit.broadcastMessage("§7Objetivo: §f" + formatMaterial(activeMission.getObjective()));
        Bukkit.broadcastMessage("§7Cuota: §f" + activeMission.getQuantity() + " §7por jugador");
        Bukkit.broadcastMessage("§7Recompensa: §f" + formatReward(activeMission));
        Bukkit.broadcastMessage("§7Tiempo: §f24 horas §7(expira en 24h tiempo real)");
        Bukkit.broadcastMessage("§aCada jugador debe completar su cuota para recibir la recompensa.");
        Bukkit.broadcastMessage("§cSi no la completas a tiempo, recibiras una penitencia al conectarte.");
        saveState();
    }

    public void rejectMission() {
        if (pendingProposal == null) {
            Bukkit.broadcastMessage("§cNo hay ninguna propuesta pendiente.");
            return;
        }
        pendingProposal = null;
        Bukkit.broadcastMessage("§cMision rechazada.");
    }

    public void stopMission() {
        if (activeMission == null) {
            Bukkit.broadcastMessage("§cNo hay ninguna mision activa.");
            return;
        }
        Bukkit.broadcastMessage("§e§lMISION DETENIDA por un administrador.");
        Bukkit.broadcastMessage("§7Sin castigo para los jugadores.");
        restoreAllScoreboards();
        activeMission = null;
        pendingProposal = null;
        playerProgress.clear();
        saveState();
    }

    // --- Tick task: scoreboard, expiry, countdown ---

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeMission != null) {
                    updateScoreboards();
                    if (System.currentTimeMillis() > expiryTime) {
                        expireMission();
                    }
                }
                processCountdown();
            }
        };
        tickTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void expireMission() {
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§c§l¡MISION EXPIRADA!");
        Bukkit.broadcastMessage("§7La mision de " + formatMaterial(activeMission.getObjective()) + " no se completo a tiempo.");

        for (UUID uuid : touchedPlayers) {
            if (!completedPlayers.contains(uuid) && !penaltyServed.contains(uuid)) {
                penaltyPending.add(uuid);
            }
        }
        restoreAllScoreboards();
        activeMission = null;
        playerProgress.clear();
        saveState();
        Bukkit.broadcastMessage("§cLos jugadores que se conectaron y no completaron la mision recibiran una penitencia al conectarse.");
    }

    // --- Countdown & penalty ---

    private void processCountdown() {
        if (countdownLeft.isEmpty()) return;
        for (Map.Entry<UUID, Integer> e : countdownLeft.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;
            int left = e.getValue();
            if (left <= 0) {
                countdownLeft.remove(e.getKey());
                applyPenalty(p, activeTier);
            } else {
                e.setValue(left - 1);
                p.sendActionBar("§c§lPENITENCIA EN " + left + "s §7- §e" + (30 - left) + "s");
            }
        }
    }

    public void debugPenalty(Player player, String tier) {
        String normalized = tier != null ? tier.toLowerCase() : "medium";
        player.sendMessage("§eEjecutando castigo de prueba (tier: " + normalized + ")...");
        player.sendMessage("§7Perderas toda tu XP, recibiras debuffs y te perseguiran mobs.");
        applyPenalty(player, normalized);
    }

    private double tierMultiplier(String tier) {
        String key = tier == null ? "medium" : tier.toLowerCase();
        return plugin.getConfig().getDouble("mission.tier-multiplier." + key, 1.0);
    }

    private void applyPenalty(Player player, String tier) {
        penaltyPending.remove(player.getUniqueId());
        penaltyServed.add(player.getUniqueId());

        Location tp = findSafeTeleport();
        if (tp != null) {
            player.teleport(tp);
        }

        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);

        double mult = tierMultiplier(tier);
        int debuffSeconds = (int) Math.round(plugin.getConfig().getInt("mission.debuff-seconds", 300) * mult);
        int ticks = debuffSeconds * 20;
        player.addPotionEffect(new PotionEffect(getEffect("slowness"), ticks, 1));
        player.addPotionEffect(new PotionEffect(getEffect("weakness"), ticks, 1));
        player.addPotionEffect(new PotionEffect(getEffect("mining_fatigue"), ticks, 2));

        spawnPenaltyMobs(player, tier);

        player.sendMessage("§c§l¡HAS FALLADO LA MISION!");
        player.sendMessage("§cHas perdido toda tu experiencia.");
        player.sendMessage("§cHas recibido debuffs temporales y los mobs te persiguen.");
        Bukkit.broadcastMessage("§c" + player.getName() + " ha recibido la penitencia por no completar la mision.");
        saveState();
    }

    private PotionEffectType getEffect(String key) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        return type != null ? type : PotionEffectType.getByName(key.toUpperCase().replace("_", " "));
    }

    private void spawnPenaltyMobs(Player player, String tier) {
        double mult = tierMultiplier(tier);
        int count = Math.max(1, (int) Math.round(plugin.getConfig().getInt("mission.penalty-mob-count", 3) * mult));
        double health = plugin.getConfig().getDouble("mission.penalty-mob-health", 100.0) * mult;
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.ZOMBIFIED_PIGLIN};
        for (int i = 0; i < count; i++) {
            Location loc = player.getLocation().clone().add(Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
            loc.setY(player.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()));
            EntityType type = types[i % types.length];
            LivingEntity mob = (LivingEntity) player.getWorld().spawnEntity(loc, type);
            if (mob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            }
            mob.setHealth(health);
            mob.setCustomName("§c§lMision fallida");
            mob.setCustomNameVisible(true);
        }
    }

    private Location findSafeTeleport() {
        World world = Bukkit.getWorlds().get(0);
        if (world == null) return null;
        Location spawn = world.getSpawnLocation();
        int dist = plugin.getConfig().getInt("mission.teleport-distance", 500);

        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = Math.random() * Math.PI * 2;
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            Block ground = world.getHighestBlockAt(x, z);
            if (ground.getType() == Material.WATER || ground.getType() == Material.LAVA
                    || ground.getType() == Material.KELP_PLANT) continue;
            Block feet = ground.getRelative(BlockFace.UP);
            Block head = ground.getRelative(BlockFace.UP, 2);
            if (!feet.getType().isSolid() && !head.getType().isSolid()
                    && feet.getType() != Material.WATER && feet.getType() != Material.LAVA
                    && head.getType() != Material.WATER && head.getType() != Material.LAVA) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return spawn;
    }

    // --- Progress tracking ---

    private static final Map<Material, Material> ORE_TO_RESOURCE = new HashMap<>();
    static {
        ORE_TO_RESOURCE.put(Material.IRON_ORE, Material.RAW_IRON);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON);
        ORE_TO_RESOURCE.put(Material.GOLD_ORE, Material.RAW_GOLD);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD);
        ORE_TO_RESOURCE.put(Material.COPPER_ORE, Material.RAW_COPPER);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER);
        ORE_TO_RESOURCE.put(Material.DIAMOND_ORE, Material.DIAMOND);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND);
        ORE_TO_RESOURCE.put(Material.EMERALD_ORE, Material.EMERALD);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD);
        ORE_TO_RESOURCE.put(Material.REDSTONE_ORE, Material.REDSTONE);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE);
        ORE_TO_RESOURCE.put(Material.LAPIS_ORE, Material.LAPIS_LAZULI);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI);
        ORE_TO_RESOURCE.put(Material.COAL_ORE, Material.COAL);
        ORE_TO_RESOURCE.put(Material.DEEPSLATE_COAL_ORE, Material.COAL);
        ORE_TO_RESOURCE.put(Material.NETHER_QUARTZ_ORE, Material.QUARTZ);
        ORE_TO_RESOURCE.put(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET);
    }

    private boolean blockProducesObjective(Material blockType, Material objective) {
        if (blockType == objective) return true;
        if (blockType.name().startsWith("DEEPSLATE_")
                && blockType.name().substring("DEEPSLATE_".length()).equals(objective.name())) {
            return true;
        }
        Material resource = ORE_TO_RESOURCE.get(blockType);
        return resource == objective;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeMission == null) return;
        if (!"MINING".equalsIgnoreCase(activeMission.getType())) return;
        Player player = event.getPlayer();
        if (completedPlayers.contains(player.getUniqueId())) return;

        Material objective = Material.matchMaterial(activeMission.getObjective().toUpperCase());
        if (objective == null) return;

        Block block = event.getBlock();
        int gained = 0;
        if (blockProducesObjective(block.getType(), objective)) {
            gained += 1;
            for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand())) {
                if (drop.getType() == objective) {
                    gained += Math.max(0, drop.getAmount() - 1);
                }
            }
        }
        if (gained > 0) {
            addProgress(player, gained);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (activeMission == null) return;
        if (!"CRAFTING".equalsIgnoreCase(activeMission.getType())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (completedPlayers.contains(player.getUniqueId())) return;

        Material objective = Material.matchMaterial(activeMission.getObjective().toUpperCase());
        if (objective == null) return;

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() == objective) {
            addProgress(player, result.getAmount());
        }
    }

    private void addProgress(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = playerProgress.getOrDefault(uuid, 0);
        int quota = activeMission.getQuantity();
        int newVal = current + amount;
        playerProgress.put(uuid, newVal);

        if (newVal >= quota && !completedPlayers.contains(uuid)) {
            completedPlayers.add(uuid);
            giveReward(player);
        }
    }

    private void giveReward(Player player) {
        player.setLevel(player.getLevel() + activeMission.getXpReward());
        if (activeMission.getRewardItems() != null) {
            for (String s : activeMission.getRewardItems()) {
                ItemStack item = parseRewardItem(s);
                if (item == null) continue;
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack stack : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), stack);
                    }
                }
            }
        }
        player.sendMessage("§a§l¡MISION COMPLETADA!");
        player.sendMessage("§6Recompensa: §f" + formatReward(activeMission));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        Bukkit.broadcastMessage("§a" + player.getName() + " ha completado la mision de " + formatMaterial(activeMission.getObjective()) + "!");
    }

    // --- Scoreboard ---

    private void assignScoreboard(Player p) {
        if (plugin.getActiveEventManager().isParticipant(p)) return;
        previousScoreboards.put(p, p.getScoreboard());
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("vmission", "dummy", "§6§l⚒ Mision");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        p.setScoreboard(board);
    }

    private void updateScoreboards() {
        long left = expiryTime - System.currentTimeMillis();
        int hours = (int) (left / 3600000);
        int mins = (int) ((left % 3600000) / 60000);
        String timeStr = hours + "h " + mins + "m";

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getActiveEventManager().isParticipant(p)) continue;
            Scoreboard board = p.getScoreboard();
            Objective obj = board.getObjective("vmission");
            if (obj == null) {
                assignScoreboard(p);
                board = p.getScoreboard();
                obj = board.getObjective("vmission");
            }
            if (obj == null) continue;

            for (String entry : board.getEntries()) {
                board.resetScores(entry);
            }

            int progress = playerProgress.getOrDefault(p.getUniqueId(), 0);
            int quota = activeMission.getQuantity();
            boolean done = completedPlayers.contains(p.getUniqueId());
            String status = done ? "§a§lCOMPLETADA" : "§e" + progress + "§7/" + quota;

            obj.getScore("§7§m                      ").setScore(4);
            obj.getScore("§eObjetivo: §f" + formatMaterial(activeMission.getObjective())).setScore(3);
            obj.getScore("§eProgreso: §f" + status).setScore(2);
            obj.getScore("§eTiempo: §f" + timeStr).setScore(1);
            obj.getScore("§7§m                      §r").setScore(0);
        }
    }

    private void restoreAllScoreboards() {
        for (Map.Entry<Player, Scoreboard> e : previousScoreboards.entrySet()) {
            Player p = e.getKey();
            if (p.isOnline()) p.setScoreboard(e.getValue());
        }
        previousScoreboards.clear();
    }

    // --- Join/Quit ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (penaltyPending.contains(p.getUniqueId()) && !penaltyServed.contains(p.getUniqueId())
                && !countdownLeft.containsKey(p.getUniqueId())) {
            countdownLeft.put(p.getUniqueId(), 30);
            p.sendMessage("§c§l¡MISION FALLIDA!");
            p.sendMessage("§cEn 30 segundos recibiras tu penitencia por no completar la mision.");
            return;
        }
        if (activeMission != null && !touchedPlayers.contains(p.getUniqueId())) {
            touchedPlayers.add(p.getUniqueId());
        }
        if (activeMission != null && !plugin.getActiveEventManager().isParticipant(p)) {
            assignScoreboard(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        countdownLeft.remove(p.getUniqueId());
        previousScoreboards.remove(p);
        if (activeMission != null && !touchedPlayers.contains(p.getUniqueId())) {
            touchedPlayers.add(p.getUniqueId());
        }
    }

    // --- Rewards parsing ---

    private ItemStack parseRewardItem(String s) {
        String[] parts = s.split("-");
        if (parts.length == 0) return null;
        String matName = parts[0].toUpperCase();
        Material mat = Material.matchMaterial(matName);
        if (mat == null) return null;

        if (mat == Material.ENCHANTED_BOOK && parts.length >= 3) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            try {
                int level = Integer.parseInt(parts[parts.length - 1]);
                String enchName = parts[parts.length - 2].toLowerCase();
                for (Enchantment ench : Enchantment.values()) {
                    if (ench.getKey().getKey().equalsIgnoreCase(enchName)) {
                        meta.addStoredEnchant(ench, level, true);
                        break;
                    }
                }
            } catch (Exception ignored) {}
            book.setItemMeta(meta);
            return book;
        }

        int amount = 1;
        if (parts.length >= 2) {
            try { amount = Integer.parseInt(parts[1]); } catch (Exception ignored) {}
        }
        return new ItemStack(mat, Math.min(amount, mat.getMaxStackSize()));
    }

    private String formatReward(MissionProfile m) {
        StringBuilder sb = new StringBuilder();
        if (m.getXpReward() > 0) sb.append(m.getXpReward()).append(" XP");
        if (m.getRewardItems() != null && !m.getRewardItems().isEmpty()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(m.getRewardItems().size()).append(" items");
        }
        return sb.toString();
    }

    private String formatMaterial(String matName) {
        String[] words = matName.toLowerCase().replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String getTierRules(String tier) {
        switch (tier.toLowerCase()) {
            case "easy":
                return "Tier facil: jugadores nuevos. Objetivos alcanzables y recompensas modestas.";
            case "medium":
                return "Tier medio: jugadores con buen equipo. Objetivos de valor medio y buenas recompensas.";
            case "hardcore":
                return "Tier hardcore: jugadores avanzados. Objetivos dificiles y recompensas excelentes.";
            default:
                return "Tier balanceado.";
        }
    }

    // --- Persistence ---

    private void loadState() {
        if (!saveFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
            long savedExpiry = cfg.getLong("expiry-time", 0);
            if (savedExpiry > System.currentTimeMillis()) {
                MissionProfile m = new MissionProfile();
                m.setType(cfg.getString("mission.type"));
                m.setObjective(cfg.getString("mission.objective"));
                m.setQuantity(cfg.getInt("mission.quantity"));
                m.setXpReward(cfg.getInt("mission.xp-reward"));
                m.setRewardItems(cfg.getStringList("mission.reward-items"));
                m.setDescription(cfg.getString("mission.description"));
                activeMission = m;
                expiryTime = savedExpiry;
            }
            for (String key : cfg.getKeys(false)) {
                if (!key.startsWith("progress.")) continue;
                String uuidStr = key.substring("progress.".length());
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    playerProgress.put(uuid, cfg.getInt(key));
                } catch (Exception ignored) {}
            }
            for (String s : cfg.getStringList("completed")) {
                try { completedPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
            for (String s : cfg.getStringList("touched")) {
                try { touchedPlayers.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
            for (String s : cfg.getStringList("penalty-pending")) {
                try { penaltyPending.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
            for (String s : cfg.getStringList("penalty-served")) {
                try { penaltyServed.add(UUID.fromString(s)); } catch (Exception ignored) {}
            }
            plugin.getLogger().info("[MissionManager] Estado cargado. Mision activa: " + (activeMission != null));
        } catch (Exception e) {
            plugin.getLogger().warning("[MissionManager] Error al cargar missions.yml: " + e.getMessage());
        }
    }

    private void saveState() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (activeMission != null) {
            cfg.set("expiry-time", expiryTime);
            cfg.set("mission.type", activeMission.getType());
            cfg.set("mission.objective", activeMission.getObjective());
            cfg.set("mission.quantity", activeMission.getQuantity());
            cfg.set("mission.xp-reward", activeMission.getXpReward());
            cfg.set("mission.reward-items", activeMission.getRewardItems() != null ? activeMission.getRewardItems() : new ArrayList<>());
            cfg.set("mission.description", activeMission.getDescription());
        }
        for (Map.Entry<UUID, Integer> e : playerProgress.entrySet()) {
            cfg.set("progress." + e.getKey().toString(), e.getValue());
        }
        List<String> completed = new ArrayList<>();
        for (UUID u : completedPlayers) completed.add(u.toString());
        cfg.set("completed", completed);
        List<String> touched = new ArrayList<>();
        for (UUID u : touchedPlayers) touched.add(u.toString());
        cfg.set("touched", touched);
        List<String> pending = new ArrayList<>();
        for (UUID u : penaltyPending) pending.add(u.toString());
        cfg.set("penalty-pending", pending);
        List<String> served = new ArrayList<>();
        for (UUID u : penaltyServed) served.add(u.toString());
        cfg.set("penalty-served", served);
        try {
            cfg.save(saveFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[MissionManager] No se pudo guardar missions.yml: " + e.getMessage());
        }
    }

    public boolean hasActiveMission() {
        return activeMission != null;
    }

    public boolean hasPendingProposal() {
        return pendingProposal != null;
    }
}
