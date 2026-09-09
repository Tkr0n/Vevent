package com.vevent.managers;

import com.vevent.VEventPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class LookInfoManager implements Listener {

    private final VEventPlugin plugin;
    private final BukkitRunnable task;
    private static final double RANGE = 10.0;
    private final Map<UUID, MiningState> mining = new HashMap<>();

    private static class MiningState {
        final Location loc;
        final long start;
        MiningState(Location loc, long start) { this.loc = loc; this.start = start; }
    }

    private static final Map<String, String> BLOCK_NAMES = new HashMap<>();
    private static final Map<String, String> ENTITY_NAMES = new HashMap<>();
    private static final Map<String, String> EFFECT_NAMES = new HashMap<>();
    static {
        BLOCK_NAMES.put("stone", "Piedra");
        BLOCK_NAMES.put("cobblestone", "Roca");
        BLOCK_NAMES.put("dirt", "Tierra");
        BLOCK_NAMES.put("grass_block", "Bloque de Cesped");
        BLOCK_NAMES.put("sand", "Arena");
        BLOCK_NAMES.put("gravel", "Grava");
        BLOCK_NAMES.put("bedrock", "Piedra Base");
        BLOCK_NAMES.put("obsidian", "Obsidiana");
        BLOCK_NAMES.put("planks", "Tablones");
        BLOCK_NAMES.put("log", "Tronco");
        BLOCK_NAMES.put("wood", "Madera");
        BLOCK_NAMES.put("leaves", "Hojas");
        BLOCK_NAMES.put("glass", "Cristal");
        BLOCK_NAMES.put("chest", "Cofre");
        BLOCK_NAMES.put("crafting_table", "Mesa de Crafteo");
        BLOCK_NAMES.put("furnace", "Horno");
        BLOCK_NAMES.put("enchanting_table", "Mesa de Encantamientos");
        BLOCK_NAMES.put("anvil", "Yunque");
        BLOCK_NAMES.put("bed", "Cama");
        BLOCK_NAMES.put("door", "Puerta");
        BLOCK_NAMES.put("trapdoor", "Trampilla");
        BLOCK_NAMES.put("torch", "Antorcha");
        BLOCK_NAMES.put("lantern", "Farol");
        BLOCK_NAMES.put("redstone_wire", "Polvo de Redstone");
        BLOCK_NAMES.put("redstone_block", "Bloque de Redstone");
        BLOCK_NAMES.put("repeater", "Repetidor");
        BLOCK_NAMES.put("comparator", "Comparador");
        BLOCK_NAMES.put("observer", "Observador");
        BLOCK_NAMES.put("piston", "Piston");
        BLOCK_NAMES.put("hopper", "Tolva");
        BLOCK_NAMES.put("dropper", "Lanzador");
        BLOCK_NAMES.put("dispenser", "Dispensador");
        BLOCK_NAMES.put("coal_ore", "Mineral de Carbon");
        BLOCK_NAMES.put("iron_ore", "Mineral de Hierro");
        BLOCK_NAMES.put("gold_ore", "Mineral de Oro");
        BLOCK_NAMES.put("diamond_ore", "Mineral de Diamante");
        BLOCK_NAMES.put("emerald_ore", "Mineral de Esmeralda");
        BLOCK_NAMES.put("copper_ore", "Mineral de Cobre");
        BLOCK_NAMES.put("water", "Agua");
        BLOCK_NAMES.put("lava", "Lava");
        BLOCK_NAMES.put("air", "Aire");

        ENTITY_NAMES.put("zombie", "Zombi");
        ENTITY_NAMES.put("skeleton", "Esqueleto");
        ENTITY_NAMES.put("creeper", "Creeper");
        ENTITY_NAMES.put("spider", "Arana");
        ENTITY_NAMES.put("cave_spider", "Arana de Cueva");
        ENTITY_NAMES.put("enderman", "Enderman");
        ENTITY_NAMES.put("witch", "Bruja");
        ENTITY_NAMES.put("phantom", "Fantasma");
        ENTITY_NAMES.put("slime", "Slime");
        ENTITY_NAMES.put("magma_cube", "Cubo de Magma");
        ENTITY_NAMES.put("blaze", "Blaze");
        ENTITY_NAMES.put("ghast", "Ghast");
        ENTITY_NAMES.put("wither_skeleton", "Esqueleto Wither");
        ENTITY_NAMES.put("drowned", "Ahogado");
        ENTITY_NAMES.put("husk", "Huesudo");
        ENTITY_NAMES.put("stray", "Esqueleto Vagante");
        ENTITY_NAMES.put("piglin", "Piglin");
        ENTITY_NAMES.put("piglin_brute", "Piglin Brutal");
        ENTITY_NAMES.put("zombified_piglin", "Piglin Zombificado");
        ENTITY_NAMES.put("cow", "Vaca");
        ENTITY_NAMES.put("pig", "Cerdo");
        ENTITY_NAMES.put("sheep", "Oveja");
        ENTITY_NAMES.put("chicken", "Gallina");
        ENTITY_NAMES.put("wolf", "Lobo");
        ENTITY_NAMES.put("cat", "Gato");
        ENTITY_NAMES.put("ocelot", "Ocelote");
        ENTITY_NAMES.put("horse", "Caballo");
        ENTITY_NAMES.put("donkey", "Burro");
        ENTITY_NAMES.put("mule", "Mula");
        ENTITY_NAMES.put("rabbit", "Conejo");
        ENTITY_NAMES.put("fox", "Zorro");
        ENTITY_NAMES.put("bear", "Oso");
        ENTITY_NAMES.put("polar_bear", "Oso Polar");
        ENTITY_NAMES.put("mooshroom", "Mooshroom");
        ENTITY_NAMES.put("goat", "Cabra");
        ENTITY_NAMES.put("llama", "Llama");
        ENTITY_NAMES.put("villager", "Aldeano");
        ENTITY_NAMES.put("iron_golem", "Golem de Hierro");
        ENTITY_NAMES.put("snow_golem", "Golem de Nieve");
        ENTITY_NAMES.put("panda", "Panda");
        ENTITY_NAMES.put("bee", "Abeja");
        ENTITY_NAMES.put("squid", "Calamar");
        ENTITY_NAMES.put("dolphin", "Delfin");
        ENTITY_NAMES.put("turtle", "Tortuga");
        ENTITY_NAMES.put("axolotl", "Ajolote");
        ENTITY_NAMES.put("frog", "Rana");
        ENTITY_NAMES.put("allay", "Allay");
        ENTITY_NAMES.put("warden", "Warden");
        ENTITY_NAMES.put("ender_dragon", "Dragon del Ender");
        ENTITY_NAMES.put("wither", "Wither");
        ENTITY_NAMES.put("elder_guardian", "Guardian Anciano");
        ENTITY_NAMES.put("guardian", "Guardian");
        ENTITY_NAMES.put("shulker", "Shulker");
        ENTITY_NAMES.put("vex", "Vex");
        ENTITY_NAMES.put("evoker", "Evocador");
        ENTITY_NAMES.put("ravager", "Devastador");
        ENTITY_NAMES.put("pillager", "Saqueador");
        ENTITY_NAMES.put("vindicator", "Vindicador");
        ENTITY_NAMES.put("illusioner", "Ilusionista");
        ENTITY_NAMES.put("armor_stand", "Soporte de Armadura");
        ENTITY_NAMES.put("item_frame", "Marco");
        ENTITY_NAMES.put("painting", "Cuadro");

        EFFECT_NAMES.put("speed", "Velocidad");
        EFFECT_NAMES.put("slowness", "Lentitud");
        EFFECT_NAMES.put("haste", "Prisa");
        EFFECT_NAMES.put("mining_fatigue", "Fatiga");
        EFFECT_NAMES.put("strength", "Fuerza");
        EFFECT_NAMES.put("instant_health", "Curacion");
        EFFECT_NAMES.put("instant_damage", "Dano");
        EFFECT_NAMES.put("jump_boost", "Salto");
        EFFECT_NAMES.put("nausea", "Nausea");
        EFFECT_NAMES.put("regeneration", "Regeneracion");
        EFFECT_NAMES.put("resistance", "Resistencia");
        EFFECT_NAMES.put("fire_resistance", "Res. al Fuego");
        EFFECT_NAMES.put("water_breathing", "Respirar Agua");
        EFFECT_NAMES.put("invisibility", "Invisibilidad");
        EFFECT_NAMES.put("blindness", "Ceguera");
        EFFECT_NAMES.put("night_vision", "Vision Nocturna");
        EFFECT_NAMES.put("hunger", "Hambre");
        EFFECT_NAMES.put("weakness", "Debilidad");
        EFFECT_NAMES.put("poison", "Veneno");
        EFFECT_NAMES.put("wither", "Wither");
        EFFECT_NAMES.put("health_boost", "Salud Extra");
        EFFECT_NAMES.put("absorption", "Absorcion");
        EFFECT_NAMES.put("saturation", "Saturacion");
        EFFECT_NAMES.put("glowing", "Resplandor");
        EFFECT_NAMES.put("levitation", "Levitacion");
        EFFECT_NAMES.put("luck", "Suerte");
        EFFECT_NAMES.put("unluck", "Mala Suerte");
        EFFECT_NAMES.put("slow_falling", "Caida Lenta");
        EFFECT_NAMES.put("conduit_power", "Poder del Conduit");
        EFFECT_NAMES.put("dolphins_grace", "Gracia del Delfin");
        EFFECT_NAMES.put("bad_omen", "Mal Presagio");
        EFFECT_NAMES.put("hero_of_the_village", "Heroe de la Aldea");
        EFFECT_NAMES.put("darkness", "Oscuridad");
    }

    public LookInfoManager(VEventPlugin plugin) {
        this.plugin = plugin;
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sendLookInfo(p);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("LookInfoManager inicializado.");
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        Player p = event.getPlayer();
        mining.put(p.getUniqueId(), new MiningState(event.getBlock().getLocation(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        mining.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        mining.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void sendLookInfo(Player player) {
        RayTraceResult blockResult = player.rayTraceBlocks(RANGE, org.bukkit.FluidCollisionMode.NEVER);
        double maxEntityDist = RANGE;
        Block targetBlock = null;
        if (blockResult != null && blockResult.getHitBlock() != null) {
            targetBlock = blockResult.getHitBlock();
            maxEntityDist = player.getEyeLocation().distance(blockResult.getHitPosition().toLocation(player.getWorld()));
        }

        RayTraceResult entityResult = player.rayTraceEntities((int) Math.ceil(maxEntityDist));
        if (entityResult != null && entityResult.getHitEntity() != null) {
            player.sendActionBar(entityInfo(entityResult.getHitEntity()));
        } else if (targetBlock != null && targetBlock.getType() != Material.AIR) {
            player.sendActionBar(blockInfo(targetBlock, player));
        } else {
            player.sendActionBar("");
        }
    }

    private String blockInfo(Block block, Player player) {
        Material mat = block.getType();
        String name = formatBlockName(mat);
        String extra = cropInfo(block);

        MiningState state = mining.get(player.getUniqueId());
        if (state != null && state.loc.equals(block.getLocation())) {
            double seconds = computeBreakSeconds(block, player);
            long elapsed = System.currentTimeMillis() - state.start;
            double progress = seconds > 0 ? Math.min(1.0, (elapsed / 1000.0) / seconds) : 1.0;
            return "§7→ §f" + name + " " + durabilityBar(progress) + extra;
        }
        return "§7→ §f" + name + extra;
    }

    private String cropInfo(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable age) {
            int current = age.getAge();
            int max = age.getMaximumAge();
            int pct = max > 0 ? (int) Math.round((double) current / max * 100) : 0;
            if (current >= max) {
                return " §7· §a§lLISTO para cosechar";
            }
            return " §7· §6Madurez: §f" + pct + "%";
        }
        return "";
    }

    private String durabilityBar(double progress) {
        int max = 10;
        int green = (int) Math.round(progress * max);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < green ? "§a▰" : "§8▰");
        }
        return sb.toString();
    }

    private double computeBreakSeconds(Block block, Player player) {
        double hardness = block.getType().getHardness();
        if (hardness < 0) return 999999;
        ItemStack tool = player.getInventory().getItemInMainHand();
        double speed = getToolSpeed(tool);

        if (tool.getType() != Material.AIR && block.getDrops(tool).isEmpty()
                && !block.getDrops(new ItemStack(Material.AIR)).isEmpty()) {
            speed = 1.0;
        }
        speed += getEfficiencyBonus(tool);

        for (PotionEffect pe : player.getActivePotionEffects()) {
            PotionEffectType type = pe.getType();
            if (type == getEffect("haste")) {
                speed *= 1.0 + 0.2 * (pe.getAmplifier() + 1);
            } else if (type == getEffect("mining_fatigue")) {
                speed /= 1.0 + 0.3 * (pe.getAmplifier() + 1);
            }
        }

        double seconds = hardness * 1.5 / speed;
        if (seconds < 0.05) seconds = 0.05;
        return seconds;
    }

    private PotionEffectType getEffect(String key) {
        PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
        return type != null ? type : PotionEffectType.getByName(key.toUpperCase().replace("_", " "));
    }

    private String formatBlockName(Material mat) {
        String key = mat.getKey().getKey();
        String translated = BLOCK_NAMES.get(key);
        if (translated != null) return translated;
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private double getToolSpeed(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return 1.0;
        String n = tool.getType().name();
        if (n.startsWith("WOODEN")) return 2.0;
        if (n.startsWith("STONE")) return 4.0;
        if (n.startsWith("IRON")) return 6.0;
        if (n.startsWith("DIAMOND")) return 8.0;
        if (n.startsWith("NETHERITE")) return 9.0;
        if (n.startsWith("GOLDEN")) return 12.0;
        return 1.0;
    }

    private int getEfficiencyBonus(ItemStack tool) {
        if (tool == null || tool.getItemMeta() == null) return 0;
        Enchantment eff = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        if (eff == null) return 0;
        int level = tool.getEnchantmentLevel(eff);
        return level > 0 ? level * level + 1 : 0;
    }

    private String entityInfo(Entity entity) {
        String name = getEntityName(entity);
        if (!(entity instanceof LivingEntity living)) {
            return "§f" + name;
        }
        double health = living.getHealth();
        double maxHealth = 20.0;
        if (living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHealth = living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }
        return "§f" + name + " " + heartsBar(health, maxHealth)
                + " §a" + formatHealth(health, maxHealth)
                + activeEffects(living);
    }

    private String getEntityName(Entity entity) {
        if (entity instanceof Player p) {
            return p.getDisplayName();
        }
        if (entity.getCustomName() != null) {
            return entity.getCustomName();
        }
        String key = entity.getType().getKey().getKey();
        String translated = ENTITY_NAMES.get(key);
        if (translated != null) return translated;
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String heartsBar(double health, double maxHealth) {
        int maxHearts = 10;
        int filled = (int) Math.round((health / maxHealth) * maxHearts);
        if (filled < 0) filled = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxHearts; i++) {
            sb.append(i < filled ? "§c❤" : "§8❤");
        }
        return sb.toString();
    }

    private String formatHealth(double health, double maxHealth) {
        return Math.round(health) + "/" + Math.round(maxHealth);
    }

    private String activeEffects(LivingEntity living) {
        Collection<PotionEffect> effects = living.getActivePotionEffects();
        if (effects.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" §7[");
        boolean first = true;
        for (PotionEffect pe : effects) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(formatEffect(pe.getType())).append(" ").append(roman(pe.getAmplifier() + 1));
        }
        return sb.append("]").toString();
    }

    private String formatEffect(PotionEffectType type) {
        String key = type.getKey().getKey();
        String translated = EFFECT_NAMES.get(key);
        return translated != null ? translated : key.replace("_", " ");
    }

    private String roman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}
