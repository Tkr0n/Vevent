package com.vevent.models;
import java.util.List;
import java.util.Map;

public class LootProfile {
    private String tier;
    private String environmentDescription;
    private List<String> enchantments;
    private List<String> rareItems;
    private List<String> farmingItems;
    private Map<String, Integer> mobs;

    public String getTier() { return tier; }
    public String getEnvironmentDescription() { return environmentDescription; }
    public List<String> getEnchantments() { return enchantments; }
    public List<String> getRareItems() { return rareItems; }
    public List<String> getFarmingItems() { return farmingItems; }
    public Map<String, Integer> getMobs() { return mobs; }
}