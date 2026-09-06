package me.docdrewskii.profitmultiplier.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ItemGroup {

    private final String name;
    private final Set<Material> materials;
    private final List<MultiplierTier> tiers;
    private final String icon;
    private final String displayName;
    private final GroupStackMode stackMode;
    private final String currency;
    private final MilestoneCommands milestones;
    private final Map<Material, Double> prices;

    public ItemGroup(String name, Set<Material> materials, List<MultiplierTier> tiers,
                     String icon, String displayName, GroupStackMode stackMode, String currency) {
        this(name, materials, tiers, icon, displayName, stackMode, currency, null, null);
    }

    public ItemGroup(String name, Set<Material> materials, List<MultiplierTier> tiers,
                     String icon, String displayName, GroupStackMode stackMode, String currency,
                     MilestoneCommands milestones) {
        this(name, materials, tiers, icon, displayName, stackMode, currency, milestones, null);
    }

    public ItemGroup(String name, Set<Material> materials, List<MultiplierTier> tiers,
                     String icon, String displayName, GroupStackMode stackMode, String currency,
                     MilestoneCommands milestones, Map<Material, Double> prices) {
        this.name = name;
        this.materials = materials;
        this.tiers = tiers;
        this.icon = icon;
        this.displayName = displayName;
        this.stackMode = stackMode;
        this.currency = currency;
        this.milestones = milestones;
        this.prices = prices == null ? Collections.<Material, Double>emptyMap() : prices;
    }

    public Double getPrice(Material material) {
        return prices.get(material);
    }

    public String getName() {
        return name;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public boolean contains(Material material) {
        return materials.contains(material);
    }

    public List<MultiplierTier> getTiers() {
        return tiers;
    }

    public String getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GroupStackMode getStackMode() {
        return stackMode;
    }

    public String getCurrency() {
        return currency;
    }

    public MilestoneCommands getMilestones() {
        return milestones;
    }

    public double getMaxMultiplier() {
        double best = 1.0;
        for (MultiplierTier tier : tiers) {
            if (tier.getMultiplier() > best) best = tier.getMultiplier();
        }
        return best;
    }
}
