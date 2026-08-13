package com.vevent.models;
import java.util.List;

public class MissionProfile {
    private String type;
    private String objective;
    private int quantity;
    private int xpReward;
    private List<String> rewardItems;
    private String description;

    public String getType() { return type; }
    public String getObjective() { return objective; }
    public int getQuantity() { return quantity; }
    public int getXpReward() { return xpReward; }
    public List<String> getRewardItems() { return rewardItems; }
    public String getDescription() { return description; }

    public void setType(String type) { this.type = type; }
    public void setObjective(String objective) { this.objective = objective; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setXpReward(int xpReward) { this.xpReward = xpReward; }
    public void setRewardItems(List<String> rewardItems) { this.rewardItems = rewardItems; }
    public void setDescription(String description) { this.description = description; }
}
