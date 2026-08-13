package com.vevent.listeners;

import com.vevent.VEventPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Set;

public class GlobalMobListener implements Listener {

    private final VEventPlugin plugin;

    private static final Set<String> NO_SCALE_MOBS = Set.of(
            "WITHER", "GUARDIAN", "ELDER_GUARDIAN", "WARDEN", "EVOKER", "BREEZE",
            "COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "HORSE", "DONKEY", "MULE",
            "LLAMA", "VILLAGER", "IRON_GOLEM", "SNOW_GOLEM", "CAT", "OCELOT",
            "PARROT", "FOX", "BEE", "SQUID", "GLOW_SQUID", "DOLPHIN", "TURTLE",
            "AXOLOTL", "FROG", "TADPOLE", "PANDA", "POLAR_BEAR", "WOLF",
            "STRIDER", "CAMEL", "SNIFFER", "ALLAY", "GOAT", "BAT",
            "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH", "MOOSHROOM"
    );

    private static final Set<String> SIMPLE_MOBS = Set.of(
            "ZOMBIE", "SKELETON", "HUSK", "DROWNED", "STRAY", "ZOMBIFIED_PIGLIN",
            "ZOMBIE_VILLAGER", "SPIDER", "CAVE_SPIDER", "SILVERFISH", "CREEPER"
    );

    public GlobalMobListener(VEventPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (entity instanceof org.bukkit.entity.Player) return;

        String name = entity.getType().name();
        double healthMult = plugin.getConfig().getDouble("mob-difficulty.health-multiplier",
                plugin.getConfig().getDouble("event-mobs.health-multiplier", 4.0));
        double dmgMult = plugin.getConfig().getDouble("mob-difficulty.simple-damage-multiplier",
                plugin.getConfig().getDouble("event-mobs.simple-damage-multiplier", 1.2));

        if (!NO_SCALE_MOBS.contains(name)) {
            if (living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                double base = living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                living.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(base * healthMult);
            }
            double max = living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                    ? living.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 1.0;
            living.setHealth(max);
        }

        if (SIMPLE_MOBS.contains(name) && living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            double dmg = living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue();
            living.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(dmg * dmgMult);
        }
    }
}
